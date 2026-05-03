package com.example.maidmarriage.compat;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 小女仆工作系统：
 * 1. 复用女仆任务模式承载学习/探索；
 * 2. 每次行动自动消耗背包材料；
 * 3. 统一处理好感消耗、行动锁定与空闲恢复。
 */
public final class MaidWorkManager {
    private static final double GLOBAL_DURATION_SCALE = 0.8D;
    private static final double FAVOR_DURATION_BASE = 50.0D;
    private static final double MIN_FAVOR_DURATION_COEFFICIENT = 0.2D;
    private static final int MIN_ACTION_DURATION_TICKS = 100;

    private static final int LEARN_DURATION_TICKS = 3000;
    private static final int EXPLORE_DURATION_TICKS = 6000;
    private static final int IDLE_RECOVER_TICKS = 24000;
    private static final int IDLE_RECOVER_FAVOR = 5;

    private static final int FAVOR_DEFAULT = RelationshipThresholds.HUG_UNLOCK;
    private static final int FAVOR_MAX = RelationshipThresholds.FAVORABILITY_MAX;
    private static final int FAVOR_ACTION_COST = 2;
    private static final int FAVOR_BLOCK = 10;
    private static final int FAVOR_UNLOCK = 15;
    private static final int LEARN_MOOD_COST = 2;
    private static final int EXHAUSTED_MOOD_VALUE = 0;
    private static final float EXPLORE_MIN_HEALTH_RATIO = 0.30F;

    private static final String TAG_ACTION_MODE = "maidmarriage_child_action_mode";
    private static final String TAG_ACTION_END = "maidmarriage_child_action_end";
    private static final String TAG_ACTION_OWNER = "maidmarriage_child_action_owner";
    private static final String TAG_ACTION_LOCKED = "maidmarriage_child_action_locked";
    private static final String TAG_IDLE_START = "maidmarriage_child_idle_start";
    private static final String TAG_FAVOR_INITIALIZED = "maidmarriage_child_favor_initialized";
    private static final String TAG_LAST_HEALTH_HINT = "maidmarriage_child_last_health_hint";
    private static final String TAG_MISSING_MATERIAL_MODE = "maidmarriage_child_missing_material_mode";
    private static final String TAG_LAST_COUNTDOWN_SECOND = "maidmarriage_child_last_countdown_second";
    private static final String TAG_LAST_WORK_TIME_HINT = "maidmarriage_child_last_work_time_hint";
    private static final String TAG_GENERATED_REWARD = "maidmarriage_generated_reward";

    private static final long HINT_COOLDOWN_TICKS = 200L;

    private static final List<Potion> HIGH_TIER_POTIONS = List.of(
            Potions.STRONG_HEALING, Potions.STRONG_REGENERATION, Potions.STRONG_STRENGTH,
            Potions.STRONG_SWIFTNESS, Potions.LONG_INVISIBILITY, Potions.LONG_FIRE_RESISTANCE
    );
    private static final List<Potion> NORMAL_TIER_POTIONS = List.of(
            Potions.HEALING, Potions.REGENERATION, Potions.STRENGTH,
            Potions.SWIFTNESS, Potions.FIRE_RESISTANCE, Potions.NIGHT_VISION
    );

    private MaidWorkManager() {
    }

    /**
     * Register child work modes into the maid task panel.
     */
    public static void addChildWorkTasks(TaskManager manager) {
        for (WorkMode mode : WorkMode.values()) {
            manager.add(new ChildWorkTask(mode));
        }
    }

    /**
     * Keep only favorability recovery on right click.
     * Study/exploration is now task-mode driven.
     */
    public static boolean tryHandleFavorRecovery(Player player, EntityMaid maid, ItemStack stack) {
        if (player.level().isClientSide() || !maid.isOwnedBy(player)) {
            return false;
        }
        if (isBornMaid(maid)) {
            ensureDefaultFavorability(maid);
        }
        return tryRecoverFavorability(player, maid, stack);
    }

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide() || !isBornMaid(maid)) {
            return;
        }
        /*
         * 魂符/胶片恢复有时会把子代女仆还原成原版 EntityMaid。
         * 这种情况下她不再走 MaidChildEntity.tick()，所以这里补上成长推进，
         * 避免“数据已经成年、模型却永远停在儿童体型”的状态残留。
         */
        MaidChildEntity.tickExternalChildLifecycle(maid);
        if (maid.tickCount % 20 != 0) {
            return;
        }
        if (!isChildWorkMaid(maid)) {
            clearActionStateWhenAdult(maid);
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        // 成长与实体还原由专用链路处理：
        // - 成长推进：MaidChildEntity.tick()
        // - 子代还原/修复：SoulSlabChildBridge
        // 本管理器仅负责子代工作系统，不再改写成长状态。

        ensureDefaultFavorability(maid);
        sanitizeLegacyGeneratedRewardTags(maid, getOwner(maid));
        CompoundTag tag = maid.getPersistentData();
        long now = level.getGameTime();

        if (isActionBusy(maid)) {
            WorkMode runningMode = WorkMode.fromKey(tag.getString(TAG_ACTION_MODE)).orElse(null);
            WorkMode selectedMode = WorkMode.fromTask(maid.getTask()).orElse(null);
            if (runningMode == null || selectedMode != runningMode) {
                clearAction(tag);
                return;
            }
            if (runningMode.actionType == ActionType.EXPLORE && isExhausted(maid)) {
                maybeSendCooldownHint(level, resolveOwner(level, maid, tag), tag,
                        "message.maidmarriage.child.explore.need_mood", TAG_LAST_WORK_TIME_HINT);
                clearAction(tag);
                return;
            }
            tag.remove(TAG_IDLE_START);
            if (!canProgressCurrentAction(maid)) {
                freezeActionProgress(tag, now);
                return;
            }
            pushActionCountdown(level, maid, tag);
            if (now >= tag.getLong(TAG_ACTION_END)) {
                finishCurrentAction(level, maid, tag);
                if (tryStartActionByTask(level, maid, tag)) {
                    return;
                }
            }
            return;
        }

        if (tryStartActionByTask(level, maid, tag)) {
            tag.remove(TAG_IDLE_START);
            return;
        }

        tickIdleFavorRecovery(level, maid, tag);
    }

    private static void clearActionStateWhenAdult(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        if (!tag.contains(TAG_ACTION_END)
                && !tag.contains(TAG_ACTION_MODE)
                && !tag.contains(TAG_ACTION_OWNER)
                && !tag.contains(TAG_LAST_COUNTDOWN_SECOND)
                && !tag.contains(TAG_MISSING_MATERIAL_MODE)) {
            return;
        }
        clearAction(tag);
        tag.remove(TAG_MISSING_MATERIAL_MODE);
        tag.remove(TAG_LAST_HEALTH_HINT);
        tag.remove(TAG_LAST_WORK_TIME_HINT);
    }

    private static void finishCurrentAction(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        WorkMode mode = WorkMode.fromKey(tag.getString(TAG_ACTION_MODE)).orElse(null);
        if (mode == null) {
            clearAction(tag);
            return;
        }

        ServerPlayer owner = resolveOwner(level, maid, tag);
        if (mode.actionType == ActionType.LEARN) {
            completeLearning(level, maid, owner, mode.learnType);
        } else {
            completeExploration(level, maid, owner);
        }
        clearAction(tag);
    }

    private static boolean tryStartActionByTask(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        WorkMode mode = WorkMode.fromTask(maid.getTask()).orElse(null);
        if (mode == null) {
            return false;
        }

        ServerPlayer owner = getOwner(maid);
        WorkBlockReason blockReason = resolveWorkBlockReason(maid);
        if (blockReason != WorkBlockReason.NONE) {
            maybeSendCooldownHint(level, owner, tag, blockReason.messageKey, TAG_LAST_WORK_TIME_HINT);
            return false;
        }
        if (!checkFavorabilityGate(owner, maid)) {
            return false;
        }
        if (maid.getScheduleDetail() != Activity.WORK) {
            maybeSendCooldownHint(level, owner, tag, "message.maidmarriage.child.work.need_work_time", TAG_LAST_WORK_TIME_HINT);
            return false;
        }

        if (mode.actionType == ActionType.EXPLORE && maid.getHealth() < maid.getMaxHealth() * EXPLORE_MIN_HEALTH_RATIO) {
            maybeSendCooldownHint(level, owner, tag, "message.maidmarriage.child.explore.need_health", TAG_LAST_HEALTH_HINT);
            return false;
        }
        if (mode.actionType == ActionType.EXPLORE && isExhausted(maid)) {
            maybeSendCooldownHint(level, owner, tag, "message.maidmarriage.child.explore.need_mood", TAG_LAST_WORK_TIME_HINT);
            return false;
        }

        if (!consumeInputForMode(maid, mode)) {
            notifyMissingMaterialOnce(owner, maid, tag, mode);
            return false;
        }

        tag.remove(TAG_MISSING_MATERIAL_MODE);
        tag.putString(TAG_ACTION_MODE, mode.key);
        int actionDurationTicks = calculateActionDurationTicks(mode.durationTicks, maid.getFavorability());
        tag.putLong(TAG_ACTION_END, level.getGameTime() + actionDurationTicks);
        UUID ownerUuid = maid.getOwnerUUID();
        if (ownerUuid != null) {
            tag.putUUID(TAG_ACTION_OWNER, ownerUuid);
        } else {
            tag.remove(TAG_ACTION_OWNER);
        }

        if (owner != null) {
            if (mode.actionType == ActionType.LEARN) {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.learn.start", maid.getDisplayName(), mode.learnType.display()));
            } else {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.explore.start", maid.getDisplayName(), mode.display()));
            }
        }

        level.playSound(null, maid.blockPosition(),
                mode.actionType == ActionType.LEARN ? SoundEvents.ENCHANTMENT_TABLE_USE : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.7F, 1.15F);
        return true;
    }

    private static void notifyMissingMaterialOnce(ServerPlayer owner, EntityMaid maid, CompoundTag tag, WorkMode mode) {
        if (owner == null) {
            return;
        }
        String markedMode = tag.getString(TAG_MISSING_MATERIAL_MODE);
        if (mode.key.equals(markedMode)) {
            return;
        }
        tag.putString(TAG_MISSING_MATERIAL_MODE, mode.key);
        owner.sendSystemMessage(Component.translatable(
                "message.maidmarriage.child.material.missing",
                maid.getDisplayName(),
                mode.display(),
                mode.requirementDisplay()));
    }

    private static void tickIdleFavorRecovery(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        long now = level.getGameTime();
        long idleStart = tag.contains(TAG_IDLE_START) ? tag.getLong(TAG_IDLE_START) : now;
        if (!tag.contains(TAG_IDLE_START)) {
            tag.putLong(TAG_IDLE_START, now);
            return;
        }

        if (now - idleStart < IDLE_RECOVER_TICKS) {
            return;
        }

        int oldFavor = maid.getFavorability();
        int newFavor = oldFavor
                + MaidMoodManager.applyFavorabilityDeltaWithRefresh(maid, IDLE_RECOVER_FAVOR, FAVOR_MAX);
        tag.putLong(TAG_IDLE_START, now);

        if (tag.getBoolean(TAG_ACTION_LOCKED) && newFavor >= FAVOR_UNLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, false);
        }

        ServerPlayer owner = getOwner(maid);
        if (owner != null) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.favor.rested", maid.getDisplayName(), newFavor));
        }
    }

    private static boolean tryRecoverFavorability(Player player, EntityMaid maid, ItemStack stack) {
        int restore = 0;
        ItemStack extraReturn = ItemStack.EMPTY;

        if (stack.is(Items.SUGAR)) {
            restore = 1;
        } else if (stack.is(Items.MILK_BUCKET)) {
            restore = 2;
            extraReturn = new ItemStack(Items.BUCKET);
        } else if (stack.is(Items.GOLDEN_APPLE)) {
            restore = 3;
        }

        if (restore <= 0) {
            return false;
        }

        int oldFavor = maid.getFavorability();
        int favor = oldFavor
                + MaidMoodManager.applyFavorabilityDeltaWithRefresh(maid, restore, FAVOR_MAX);

        CompoundTag tag = maid.getPersistentData();
        if (tag.getBoolean(TAG_ACTION_LOCKED) && favor >= FAVOR_UNLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, false);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child.favor.unlocked", maid.getDisplayName(), favor));
        } else {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child.favor.recover", maid.getDisplayName(), favor));
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!extraReturn.isEmpty() && !player.getInventory().add(extraReturn)) {
                player.drop(extraReturn, false);
            }
        }
        return true;
    }

    private static boolean checkFavorabilityGate(ServerPlayer owner, EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        int favor = maid.getFavorability();
        boolean locked = tag.getBoolean(TAG_ACTION_LOCKED);

        if (locked && favor >= FAVOR_UNLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, false);
            if (owner != null) {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.favor.unlocked", maid.getDisplayName(), favor));
            }
            return true;
        }
        if (locked) {
            return false;
        }
        if (favor < FAVOR_BLOCK) {
            tag.putBoolean(TAG_ACTION_LOCKED, true);
            if (owner != null) {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.favor.blocked", maid.getDisplayName(), favor));
            }
            return false;
        }
        return true;
    }

    private static void spendActionCost(EntityMaid maid) {
        int oldFavor = maid.getFavorability();
        int favor = oldFavor
                + MaidMoodManager.applyFavorabilityDeltaWithRefresh(maid, -FAVOR_ACTION_COST, FAVOR_MAX);
        if (favor < FAVOR_BLOCK) {
            maid.getPersistentData().putBoolean(TAG_ACTION_LOCKED, true);
        }
    }

    private static boolean isActionBusy(EntityMaid maid) {
        return maid.getPersistentData().contains(TAG_ACTION_END);
    }

    private static void clearAction(CompoundTag tag) {
        tag.remove(TAG_ACTION_MODE);
        tag.remove(TAG_ACTION_END);
        tag.remove(TAG_ACTION_OWNER);
        tag.remove(TAG_LAST_COUNTDOWN_SECOND);
    }

    private static ServerPlayer getOwner(EntityMaid maid) {
        if (maid.getOwner() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        return null;
    }

    private static ServerPlayer resolveOwner(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        if (tag.hasUUID(TAG_ACTION_OWNER)) {
            UUID ownerUuid = tag.getUUID(TAG_ACTION_OWNER);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (player != null) {
                return player;
            }
        }
        return getOwner(maid);
    }

    private static boolean consumeInputForMode(EntityMaid maid, WorkMode mode) {
        if (consumeInputFromMainHand(maid, mode.inputMatcher)) {
            return true;
        }
        if (mode.allowBackpackForStackable) {
            return consumeInputFromBackpack(maid, mode.inputMatcher);
        }
        if (!mode.allowBackpackForUnstackable) {
            return false;
        }
        return consumeUnstackableInputFromBackpack(maid, mode.inputMatcher);
    }

    private static boolean consumeInputFromMainHand(EntityMaid maid, Predicate<ItemStack> matcher) {
        ItemStack mainHand = maid.getMainHandItem();
        if (!isPlayerInputMaterial(mainHand, matcher)) {
            return false;
        }
        mainHand.shrink(1);
        if (mainHand.isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        return true;
    }

    private static boolean consumeUnstackableInputFromBackpack(EntityMaid maid, Predicate<ItemStack> matcher) {
        var inventory = maid.getAvailableInv(false);
        if (inventory == null) {
            return false;
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!isPlayerInputMaterial(stack, matcher) || stack.getMaxStackSize() > 1) {
                continue;
            }
            ItemStack extracted = inventory.extractItem(i, 1, false);
            if (!extracted.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeInputFromBackpack(EntityMaid maid, Predicate<ItemStack> matcher) {
        var inventory = maid.getAvailableInv(false);
        if (inventory == null) {
            return false;
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!isPlayerInputMaterial(stack, matcher)) {
                continue;
            }
            ItemStack extracted = inventory.extractItem(i, 1, false);
            if (!extracted.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void maybeSendCooldownHint(ServerLevel level, ServerPlayer owner, CompoundTag tag, String key, String hintTag) {
        if (owner == null) {
            return;
        }
        long now = level.getGameTime();
        long last = tag.getLong(hintTag);
        if (now - last < HINT_COOLDOWN_TICKS) {
            return;
        }
        tag.putLong(hintTag, now);
        owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, key));
    }

    private static void pushActionCountdown(ServerLevel level, EntityMaid maid, CompoundTag tag) {
        ServerPlayer owner = resolveOwner(level, maid, tag);
        if (owner == null) {
            return;
        }
        WorkMode mode = WorkMode.fromKey(tag.getString(TAG_ACTION_MODE)).orElse(null);
        if (mode == null) {
            return;
        }
        long remainTicks = Math.max(0L, tag.getLong(TAG_ACTION_END) - level.getGameTime());
        int remainSeconds = (int) ((remainTicks + 19L) / 20L);
        if (tag.getInt(TAG_LAST_COUNTDOWN_SECOND) == remainSeconds) {
            return;
        }
        tag.putInt(TAG_LAST_COUNTDOWN_SECOND, remainSeconds);
        owner.displayClientMessage(DialogueScriptManager.componentForPlayer(owner,
                "message.maidmarriage.child.action.countdown",
                maid.getDisplayName(),
                formatRemainTime(remainSeconds)), true);
    }

    private static String formatRemainTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static void completeLearning(ServerLevel level, EntityMaid maid, ServerPlayer owner, LearnType learnType) {
        boolean exhaustedOutcome = MaidMoodManager.value(maid) - LEARN_MOOD_COST <= EXHAUSTED_MOOD_VALUE;
        List<ItemStack> rewards = switch (learnType) {
            case ENCHANTMENT -> createEnchantmentRewards(level, maid, exhaustedOutcome);
            case ALCHEMY -> createAlchemyRewards(level, maid, exhaustedOutcome);
            case TACTICS -> createTacticsRewards(level, maid, exhaustedOutcome);
        };

        deliverRewards(maid, owner, rewards);
        applyLearningFatigue(maid, owner);

        if (owner != null) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.learn.finish", maid.getDisplayName(), learnType.display()));
        }
        level.playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.35F);
    }

    private static void completeExploration(ServerLevel level, EntityMaid maid, ServerPlayer owner) {
        ExploreResult result = createExploreRewards(maid);

        if (result.injured) {
            float hurtValue = (float) (maid.getMaxHealth() * result.hurtRatio);
            maid.setHealth(Math.max(1.0F, maid.getHealth() - hurtValue));
        }

        if (!result.rewards.isEmpty()) {
            deliverRewards(maid, owner, result.rewards);
        }

        if (owner != null) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner,
                    "message.maidmarriage.child.explore.finish",
                    maid.getDisplayName(),
                    DialogueScriptManager.component("message.maidmarriage.child.explore.type.adventure")));
            if (result.emptyHanded) {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.explore.empty", maid.getDisplayName()));
            }
            if (result.injured) {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.explore.injured", maid.getDisplayName()));
            }
        }

        level.playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.1F);
    }

    private static List<ItemStack> createEnchantmentRewards(ServerLevel level, EntityMaid maid, boolean lowQuality) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);
        List<ItemStack> result = new ArrayList<>();

        if (lowQuality && random.nextDouble() < 0.55D) {
            result.add(new ItemStack(Items.BOOK));
        } else {
            result.add(createRandomEnchantedBook(level, random, favorRate, lowQuality));
        }
        return result;
    }

    private static ItemStack createRandomEnchantedBook(ServerLevel level, RandomSource random, double favorRate, boolean lowQuality) {
        if (lowQuality) {
            ItemStack book = new ItemStack(Items.BOOK);
            ItemStack enchanted = EnchantmentHelper.enchantItem(random, book, 4 + random.nextInt(7), false);
            return enchanted.is(Items.ENCHANTED_BOOK) ? enchanted : new ItemStack(Items.BOOK);
        }
        boolean highTier = random.nextDouble() < 0.20D + favorRate * 0.45D;
        int enchantLevel = highTier ? 26 + random.nextInt(12) : 10 + random.nextInt(12);
        ItemStack book = new ItemStack(Items.BOOK);
        ItemStack enchanted = EnchantmentHelper.enchantItem(random, book, enchantLevel, false);
        return enchanted.is(Items.ENCHANTED_BOOK) ? enchanted : new ItemStack(Items.ENCHANTED_BOOK);
    }

    private static List<ItemStack> createAlchemyRewards(ServerLevel level, EntityMaid maid, boolean lowQuality) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);
        List<ItemStack> result = new ArrayList<>();
        if (lowQuality) {
            ItemStack awkward = new ItemStack(Items.POTION);
            PotionUtils.setPotion(awkward, random.nextBoolean() ? Potions.AWKWARD : Potions.WATER);
            result.add(awkward);
            return result;
        }
        boolean highTier = random.nextDouble() < 0.15D + favorRate * 0.40D;
        List<Potion> pool = highTier ? HIGH_TIER_POTIONS : NORMAL_TIER_POTIONS;
        Potion potion = pool.get(random.nextInt(pool.size()));
        Item potionItem = highTier && random.nextDouble() < 0.35D ? Items.LINGERING_POTION : Items.POTION;
        ItemStack potionStack = new ItemStack(potionItem);
        PotionUtils.setPotion(potionStack, potion);
        result.add(potionStack);

        if (random.nextDouble() < 0.10D + favorRate * 0.35D) {
            result.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + random.nextInt(2)));
        }
        return result;
    }

    private static List<ItemStack> createTacticsRewards(ServerLevel level, EntityMaid maid, boolean lowQuality) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);
        List<ItemStack> result = new ArrayList<>();

        boolean highTier = !lowQuality && random.nextDouble() < 0.18D + favorRate * 0.38D;
        ItemStack weapon = createRandomWeapon(level, random, highTier, lowQuality);
        result.add(weapon);

        if (!lowQuality && (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))) {
            result.add(new ItemStack(Items.ARROW, 8 + random.nextInt(17)));
        }
        return result;
    }

    private static ItemStack createRandomWeapon(ServerLevel level, RandomSource random, boolean highTier, boolean lowQuality) {
        Item item;
        if (lowQuality) {
            Item[] lowPool = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.STONE_AXE, Items.BOW};
            item = lowPool[random.nextInt(lowPool.length)];
        } else if (highTier) {
            Item[] highPool = {Items.DIAMOND_SWORD, Items.TRIDENT, Items.CROSSBOW, Items.NETHERITE_SWORD};
            item = highPool[random.nextInt(highPool.length)];
        } else {
            Item[] normalPool = {Items.IRON_SWORD, Items.BOW, Items.CROSSBOW, Items.SHIELD, Items.IRON_AXE};
            item = normalPool[random.nextInt(normalPool.length)];
        }

        ItemStack stack = new ItemStack(item);
        if (!lowQuality) {
            maybeEnchantWeapon(level, random, stack, highTier);
        }
        return stack;
    }

    private static void maybeEnchantWeapon(ServerLevel level, RandomSource random, ItemStack weapon, boolean highTier) {
        if (random.nextDouble() > (highTier ? 0.80D : 0.45D)) {
            return;
        }
        int enchantLevel = highTier ? 24 + random.nextInt(10) : 8 + random.nextInt(8);
        ItemStack enchanted = EnchantmentHelper.enchantItem(random, weapon.copy(), enchantLevel, true);
        weapon.setTag(enchanted.getTag());
    }

    private static ExploreResult createExploreRewards(EntityMaid maid) {
        RandomSource random = maid.getRandom();
        double favorRate = favorRate(maid);

        // 探险不再分近郊/遗迹/深渊，而是把“走多远、敢不敢深入”交给好感度影响。
        // 低好感时更像在附近转转，容易空手或受伤；高好感时更信任玩家，回报更稳定，也更敢带回稀有发现。
        double emptyChance = Math.max(0.05D, 0.30D - favorRate * 0.25D);
        double injuryChance = Math.max(0.04D, 0.24D - favorRate * 0.18D);
        boolean empty = random.nextDouble() < emptyChance;
        boolean injured = random.nextDouble() < injuryChance;

        List<ItemStack> rewards = new ArrayList<>();
        if (!empty) {
            int favor = clampFavorability(maid.getFavorability());
            int rolls = 2
                    + (favor >= RelationshipThresholds.HUG_UNLOCK ? 1 : 0)
                    + (favor >= RelationshipThresholds.DATING_UNLOCK && random.nextDouble() < 0.50D ? 1 : 0)
                    + (favor >= RelationshipThresholds.MARRIAGE_UNLOCK ? 1 : 0);
            for (int i = 0; i < rolls; i++) {
                double rareChance = 0.05D + favorRate * 0.22D;
                double treasureChance = Math.max(0.0D, favorRate - 0.55D) * 0.08D;
                double roll = random.nextDouble();
                if (roll < treasureChance) {
                    rewards.add(rollReward(random, ExploreRewardPool.TREASURE));
                } else if (roll < treasureChance + rareChance) {
                    rewards.add(rollReward(random, ExploreRewardPool.RARE));
                } else {
                    rewards.add(rollReward(random, ExploreRewardPool.COMMON));
                }
            }
        }

        double hurtRatio = 0.08D + random.nextDouble() * (0.18D - favorRate * 0.08D);
        return new ExploreResult(rewards, empty, injured, hurtRatio);
    }

    private static ItemStack rollReward(RandomSource random, ExploreRewardPool pool) {
        RewardEntry entry = pool.entries.get(random.nextInt(pool.entries.size()));
        int count = entry.min + (entry.max > entry.min ? random.nextInt(entry.max - entry.min + 1) : 0);
        return new ItemStack(entry.item, Math.max(1, count));
    }

    private static void deliverRewards(EntityMaid maid, ServerPlayer owner, List<ItemStack> rewards) {
        var backpack = maid.getAvailableInv(false);
        for (ItemStack reward : rewards) {
            if (reward.isEmpty()) {
                continue;
            }
            ItemStack generatedReward = reward.copy();
            ItemStack remaining = backpack != null
                    ? ItemHandlerHelper.insertItemStacked(backpack, generatedReward, false)
                    : generatedReward;
            if (!remaining.isEmpty() && owner != null && owner.getInventory().add(remaining)) {
                continue;
            }
            if (!remaining.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5D, maid.getZ(), remaining);
                maid.level().addFreshEntity(itemEntity);
            }
        }
    }

    private static void ensureDefaultFavorability(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        if (!tag.getBoolean(TAG_FAVOR_INITIALIZED)) {
            MaidMoodManager.setFavorabilityWithRefresh(maid, FAVOR_DEFAULT, FAVOR_MAX);
            tag.putBoolean(TAG_FAVOR_INITIALIZED, true);
        }
    }

    private static boolean isChildWorkMaid(EntityMaid maid) {
        return MaidChildEntity.shouldStayChild(maid);
    }

    private static boolean isBornMaid(EntityMaid maid) {
        /*
         * “出生女仆”是长期身份，不再依赖当前实体类型。
         * 这里只看血统标签或当前是否仍处于 child 生命周期。
         */
        return maid.getTags().contains(MaidChildEntity.BORN_MAID_TAG)
                || maid.getPersistentData().hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)
                || maid.getPersistentData().hasUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY)
                || maid.getPersistentData().hasUUID(MaidChildEntity.PERSISTENT_GRAND_PARENT_UUID_KEY)
                || MaidChildEntity.shouldStayChild(maid);
    }

    private static int clampFavorability(int favorability) {
        return Math.max(0, Math.min(FAVOR_MAX, favorability));
    }

    private static double favorRate(EntityMaid maid) {
        return Math.max(0.0D, Math.min(1.0D, maid.getFavorability() / (double) FAVOR_MAX));
    }

    private static boolean canProgressCurrentAction(EntityMaid maid) {
        return resolveWorkBlockReason(maid) == WorkBlockReason.NONE
                // 工作任务只在原版女仆的 WORK 日程中推进；进入休息/睡眠时间后暂停，下一段工作时间继续。
                && maid.getScheduleDetail() == Activity.WORK;
    }

    private static WorkBlockReason resolveWorkBlockReason(EntityMaid maid) {
        if (!hasUsableBackpack(maid)) {
            return WorkBlockReason.NO_BACKPACK;
        }
        if (maid.isSleeping()) {
            return WorkBlockReason.SLEEPING;
        }
        if (maid.isInSittingPose()) {
            return WorkBlockReason.SITTING;
        }
        return WorkBlockReason.NONE;
    }

    private static boolean hasUsableBackpack(EntityMaid maid) {
        return maid.getAvailableInv(false) != null;
    }

    private static void freezeActionProgress(CompoundTag tag, long now) {
        if (tag.contains(TAG_ACTION_END)) {
            long currentEnd = tag.getLong(TAG_ACTION_END);
            // Pause by extending end time one second per blocked tick,
            // preserving remaining duration after maid stands up again.
            tag.putLong(TAG_ACTION_END, Math.max(currentEnd, now) + 20L);
        }
        tag.remove(TAG_LAST_COUNTDOWN_SECOND);
    }

    private static boolean isPlayerInputMaterial(ItemStack stack, Predicate<ItemStack> matcher) {
        return !stack.isEmpty() && matcher.test(stack);
    }

    private static boolean isExhausted(EntityMaid maid) {
        return MaidMoodManager.value(maid) <= EXHAUSTED_MOOD_VALUE;
    }

    private static void applyLearningFatigue(EntityMaid maid, ServerPlayer owner) {
        int beforeMood = MaidMoodManager.value(maid);
        MaidMoodManager.addMood(maid, -LEARN_MOOD_COST);
        int afterMood = MaidMoodManager.value(maid);
        if (afterMood <= EXHAUSTED_MOOD_VALUE) {
            spendActionCost(maid);
            playLearningExhaustedDialogue(maid);
            if (owner != null) {
                owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.work.exhausted", maid.getDisplayName()));
            }
            return;
        }
        if (owner == null || afterMood >= MaidMoodData.DEFAULT_MOOD || afterMood >= beforeMood) {
            return;
        }
        owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.child.work.tired", maid.getDisplayName()));
    }

    private static void playLearningExhaustedDialogue(EntityMaid maid) {
        int index = maid.getRandom().nextInt(5) + 1;
        RomanceSleepManager.speakSingleLineWithChat(maid, "dialogue.maidmarriage.child.learn.exhausted." + index);
    }

    /**
     * 清理旧版本给奖励物品留下的标记。
     *
     * <p>早期版本会给生成奖励写入 `maidmarriage_generated_reward`，这会让玩家拿到的物品
     * 带着没有实际用途的 NBT。正式版不再依赖这个标记，所以这里顺手清掉旧存档遗留数据。
     */
    private static void sanitizeLegacyGeneratedRewardTags(EntityMaid maid, ServerPlayer owner) {
        var backpack = maid.getAvailableInv(false);
        if (backpack != null) {
            for (int slot = 0; slot < backpack.getSlots(); slot++) {
                clearGeneratedRewardTag(backpack.getStackInSlot(slot));
            }
        }
        if (owner == null) {
            return;
        }
        for (ItemStack stack : owner.getInventory().items) {
            clearGeneratedRewardTag(stack);
        }
        for (ItemStack stack : owner.getInventory().armor) {
            clearGeneratedRewardTag(stack);
        }
        for (ItemStack stack : owner.getInventory().offhand) {
            clearGeneratedRewardTag(stack);
        }
    }

    private static void clearGeneratedRewardTag(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_GENERATED_REWARD)) {
            return;
        }
        tag.remove(TAG_GENERATED_REWARD);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    private static int calculateActionDurationTicks(int baseDurationTicks, int currentFavorability) {
        double favorCoefficient = Math.max(MIN_FAVOR_DURATION_COEFFICIENT, currentFavorability / FAVOR_DURATION_BASE);
        double scaledDuration = baseDurationTicks * GLOBAL_DURATION_SCALE / favorCoefficient;
        return Math.max(MIN_ACTION_DURATION_TICKS, (int) Math.round(scaledDuration));
    }

    private record RewardEntry(Item item, int min, int max) {
    }

    private record ExploreResult(List<ItemStack> rewards, boolean emptyHanded, boolean injured, double hurtRatio) {
    }

    private enum ActionType {
        LEARN,
        EXPLORE
    }

    private enum WorkBlockReason {
        NONE(""),
        NO_BACKPACK("message.maidmarriage.child.work.need_backpack"),
        SLEEPING("message.maidmarriage.child.work.sleeping"),
        SITTING("message.maidmarriage.child.work.sitting");

        private final String messageKey;

        WorkBlockReason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private enum LearnType {
        ENCHANTMENT("enchantment"),
        ALCHEMY("alchemy"),
        TACTICS("tactics");

        private final String key;

        LearnType(String key) {
            this.key = key;
        }

        private Component display() {
            return DialogueScriptManager.component("message.maidmarriage.child.learn.type." + this.key);
        }
    }

    private enum ExploreRewardPool {
        COMMON(List.of(
                new RewardEntry(Items.BREAD, 2, 6),
                new RewardEntry(Items.COOKED_CHICKEN, 1, 4),
                new RewardEntry(Items.COOKED_SALMON, 1, 4),
                new RewardEntry(Items.APPLE, 2, 5),
                new RewardEntry(Items.WHEAT, 2, 6),
                new RewardEntry(Items.WHEAT_SEEDS, 3, 10),
                new RewardEntry(Items.BEETROOT_SEEDS, 2, 8),
                new RewardEntry(Items.MELON_SEEDS, 2, 6),
                new RewardEntry(Items.PUMPKIN_SEEDS, 2, 6),
                new RewardEntry(Items.SUGAR_CANE, 2, 6),
                new RewardEntry(Items.BAMBOO, 2, 6),
                new RewardEntry(Items.SWEET_BERRIES, 2, 6),
                new RewardEntry(Items.GLOW_BERRIES, 2, 5),
                new RewardEntry(Items.COAL, 4, 10),
                new RewardEntry(Items.COPPER_INGOT, 3, 8),
                new RewardEntry(Items.IRON_INGOT, 1, 4),
                new RewardEntry(Items.REDSTONE, 3, 8),
                new RewardEntry(Items.LAPIS_LAZULI, 2, 6),
                new RewardEntry(Items.POPPY, 1, 4),
                new RewardEntry(Items.DANDELION, 1, 4),
                new RewardEntry(Items.CORNFLOWER, 1, 3),
                new RewardEntry(Items.OXEYE_DAISY, 1, 3)
        )),
        RARE(List.of(
                new RewardEntry(Items.GOLD_INGOT, 1, 4),
                new RewardEntry(Items.EMERALD, 1, 3),
                new RewardEntry(Items.DIAMOND, 1, 2),
                new RewardEntry(Items.AMETHYST_SHARD, 2, 6),
                new RewardEntry(Items.EXPERIENCE_BOTTLE, 1, 3),
                new RewardEntry(Items.ENDER_PEARL, 1, 2),
                new RewardEntry(Items.NAME_TAG, 1, 1),
                new RewardEntry(Items.SADDLE, 1, 1)
        )),
        TREASURE(List.of(
                new RewardEntry(Items.ANCIENT_DEBRIS, 1, 1),
                new RewardEntry(Items.NETHERITE_SCRAP, 1, 1),
                new RewardEntry(Items.TOTEM_OF_UNDYING, 1, 1),
                new RewardEntry(Items.ENCHANTED_GOLDEN_APPLE, 1, 1),
                new RewardEntry(Items.HEART_OF_THE_SEA, 1, 1)
        ));

        private final List<RewardEntry> entries;

        ExploreRewardPool(List<RewardEntry> entries) {
            this.entries = entries;
        }
    }

    private enum WorkMode {
        STUDY_ENCHANTMENT("child_study_enchantment", Items.BOOK, LEARN_DURATION_TICKS, ActionType.LEARN,
                stack -> stack.is(Items.BOOK), LearnType.ENCHANTMENT,
                "message.maidmarriage.child.requirement.book", false, true),
        STUDY_ALCHEMY("child_study_alchemy", Items.GLASS_BOTTLE, LEARN_DURATION_TICKS, ActionType.LEARN,
                stack -> stack.is(Items.GLASS_BOTTLE) || stack.is(Items.POTION), LearnType.ALCHEMY,
                "message.maidmarriage.child.requirement.alchemy", true, true),
        STUDY_TACTICS("child_study_tactics", Items.IRON_SWORD, LEARN_DURATION_TICKS, ActionType.LEARN,
                stack -> stack.is(ItemTags.SWORDS)
                        || stack.is(ItemTags.AXES)
                        || stack.is(Items.BOW)
                        || stack.is(Items.CROSSBOW)
                        || stack.is(Items.TRIDENT), LearnType.TACTICS, "message.maidmarriage.child.requirement.weapon", true),
        EXPLORE("child_explore", Items.STICK, EXPLORE_DURATION_TICKS, ActionType.EXPLORE,
                stack -> stack.is(Items.STICK),
                null, "message.maidmarriage.child.requirement.explore", false, true);

        private static final Map<ResourceLocation, WorkMode> TASK_MAP = Map.of(
                id(STUDY_ENCHANTMENT.key), STUDY_ENCHANTMENT,
                id(STUDY_ALCHEMY.key), STUDY_ALCHEMY,
                id(STUDY_TACTICS.key), STUDY_TACTICS,
                id(EXPLORE.key), EXPLORE
        );

        private final String key;
        private final Item icon;
        private final int durationTicks;
        private final ActionType actionType;
        private final Predicate<ItemStack> inputMatcher;
        private final LearnType learnType;
        private final String requirementKey;
        private final boolean allowBackpackForUnstackable;
        private final boolean allowBackpackForStackable;

        WorkMode(String key, Item icon, int durationTicks, ActionType actionType,
                 Predicate<ItemStack> inputMatcher, LearnType learnType,
                 String requirementKey, boolean allowBackpackForUnstackable) {
            this(key, icon, durationTicks, actionType, inputMatcher, learnType, requirementKey, allowBackpackForUnstackable, false);
        }

        WorkMode(String key, Item icon, int durationTicks, ActionType actionType,
                 Predicate<ItemStack> inputMatcher, LearnType learnType,
                 String requirementKey, boolean allowBackpackForUnstackable, boolean allowBackpackForStackable) {
            this.key = key;
            this.icon = icon;
            this.durationTicks = durationTicks;
            this.actionType = actionType;
            this.inputMatcher = inputMatcher;
            this.learnType = learnType;
            this.requirementKey = requirementKey;
            this.allowBackpackForUnstackable = allowBackpackForUnstackable;
            this.allowBackpackForStackable = allowBackpackForStackable;
        }

        private ResourceLocation uid() {
            return id(this.key);
        }

        private static ResourceLocation id(String path) {
            return new ResourceLocation(MaidMarriageMod.MOD_ID, path);
        }

        private static Optional<WorkMode> fromTask(IMaidTask task) {
            if (task == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(TASK_MAP.get(task.getUid()));
        }

        private static Optional<WorkMode> fromKey(String key) {
            for (WorkMode mode : values()) {
                if (mode.key.equals(key)) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }

        private Component display() {
            return actionType == ActionType.LEARN
                    ? learnType.display()
                    : DialogueScriptManager.component("message.maidmarriage.child.explore.type.adventure");
        }

        private Component requirementDisplay() {
            return DialogueScriptManager.component(requirementKey);
        }
    }

    private static final class ChildWorkTask implements IMaidTask {
        private final WorkMode mode;

        private ChildWorkTask(WorkMode mode) {
            this.mode = mode;
        }

        @Override
        public ResourceLocation getUid() {
            return mode.uid();
        }

        @Override
        public ItemStack getIcon() {
            return new ItemStack(mode.icon);
        }

        @Override
        public net.minecraft.sounds.SoundEvent getAmbientSound(EntityMaid maid) {
            return null;
        }

        @Override
        public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
            return Collections.emptyList();
        }

        @Override
        public boolean isEnable(EntityMaid maid) {
            return isChildWorkMaid(maid);
        }

        @Override
        public boolean isHidden(EntityMaid maid) {
            return !isChildWorkMaid(maid);
        }

        @Override
        public List<String> getDescription(EntityMaid maid) {
            return List.of(String.format("task.%s.%s.desc", MaidMarriageMod.MOD_ID, mode.key));
        }
    }
}
