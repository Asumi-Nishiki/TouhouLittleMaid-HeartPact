package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ChildLineageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModItems;
import com.example.maidmarriage.item.MarriageConsentFormItem;
import com.mojang.authlib.GameProfile;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.IItemHandler;

public final class MarriageEventHandler {
    private static final String TAG_PLAYER_PRIMARY_MAID = "maidmarriage_primary_maid";
    private static final String TAG_RING_USED = "maidmarriage_ring_used";
    private static final String TAG_RING_PLAYER = "maidmarriage_ring_player";
    private static final String TAG_RING_MAID = "maidmarriage_ring_maid";
    private static final String TAG_FLOWER_GIFT_MASK = "maidmarriage_flower_gift_mask";
    private static final int NORMAL_FLOWER_FAVORABILITY_GAIN = 5;
    private static final int RAINBOW_BOUQUET_FAVORABILITY_GAIN = 5;
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;
    private static final long TRANSFER_CONFIRM_TICKS = 20L * 30L;
    private static final long DAY_TICKS = 24000L;
    private static final double POSTPARTUM_SAUCE_DUCK_REDUCE_PERCENT = 0.10D;
    private static final int PROPOSAL_PUNISH_NAUSEA_TICKS = 20 * 60;
    private static final int PROPOSAL_PUNISH_STRIKE_INTERVAL_TICKS = 20;
    private static final int PROPOSAL_PUNISH_FREEZE_EXTRA_TICKS = 20 * 4;
    private static final float PROPOSAL_PUNISH_TARGET_HEALTH = 1.0F;
    private static final float PROPOSAL_PUNISH_HEALTH_THRESHOLD = 10.0F;
    private static final float PROPOSAL_PUNISH_DAMAGE_RATIO = 0.5F;
    private static final float PROPOSAL_PUNISH_DAMAGE_LOW_HEALTH = 1.0F;
    private static final String TAG_CONSENT_APPROVED_PLAYER = "maidmarriage_consent_approved_player";
    private static final Map<UUID, PendingTransferRequest> PENDING_TRANSFERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PROPOSAL_PUNISH_ACTIVE = new ConcurrentHashMap<>();

    private MarriageEventHandler() {
    }

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        if (ModTaskData.MARRIAGE_DATA == null) {
            return;
        }

        ItemStack stack = event.getStack();
        if (stack.isEmpty()) {
            return;
        }

        if (stack.is(ModItems.PROPOSAL_RING.get())) {
            handleProposal(event.getPlayer(), event.getMaid(), stack, InteractionHand.MAIN_HAND);
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.YES_PILLOW.get())) {
            handleBreedingTest(event.getPlayer(), event.getMaid());
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.LONGING_TESTER.get())) {
            handleLongingTest(event.getPlayer(), event.getMaid());
            event.setCanceled(true);
            return;
        }
        if (stack.is(ModItems.SAUCE_DUCK.get())) {
            if (handleSauceDuck(event.getPlayer(), event.getMaid(), stack)) {
                event.setCanceled(true);
                return;
            }
        }
        if (stack.is(ModItems.GROWTH_TOOL.get())) {
            if (handleGrowthTool(event.getPlayer(), event.getMaid())) {
                event.setCanceled(true);
                return;
            }
        }
        if (stack.is(ModItems.BIRTH_TOOL.get())) {
            if (handleBirthTool(event.getPlayer(), event.getMaid())) {
                event.setCanceled(true);
                return;
            }
        }
        if (stack.is(ModItems.PREGNANCY_TEST_TOOL.get())) {
            if (handlePregnancyTestTool(event.getPlayer(), event.getMaid(), stack)) {
                event.setCanceled(true);
                return;
            }
        }
        if (stack.is(ModItems.PREGNANCY_SETTLEMENT_TOOL.get())) {
            if (handlePregnancySettlementTool(event.getPlayer(), event.getMaid(), stack)) {
                event.setCanceled(true);
                return;
            }
        }
        if (stack.is(ModItems.FAMILY_TREE_TOOL.get())) {
            if (handleFamilyTreeTool(event.getPlayer(), event.getMaid())) {
                event.setCanceled(true);
                return;
            }
        }
        if (stack.is(ModItems.MARRIAGE_CONSENT_FORM.get())) {
            if (handleMarriageConsentMaidSelection(event.getPlayer(), event.getMaid(), stack)) {
                event.setCanceled(true);
                return;
            }
        }
        if (tryHandleFlowerGift(event.getPlayer(), event.getMaid(), stack)) {
            event.setCanceled(true);
            return;
        }
        if (MaidWorkManager.tryHandleFavorRecovery(event.getPlayer(), event.getMaid(), stack)) {
            event.setCanceled(true);
        }
    }

    /**
     * Fallback for interaction paths not covered by InteractMaidEvent
     * (for example off-hand or non-owned maid paths in upstream interaction flow).
     */
    @SubscribeEvent
    public static void onEntityInteractFallback(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        if (stack.is(ModItems.MARRIAGE_CONSENT_FORM.get()) && event.getTarget() instanceof net.minecraft.world.entity.player.Player targetPlayer) {
            if (handleMarriageConsentPlayerSelection(event.getEntity(), targetPlayer, stack)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (stack.is(ModItems.MARRIAGE_CONSENT_FORM.get()) && !(event.getTarget() instanceof EntityMaid)) {
            event.getEntity().sendSystemMessage(DialogueScriptManager.componentForPlayer(event.getEntity(), "message.maidmarriage.consent.invalid_target"));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }

        // Main-hand + owned maid is already handled by InteractMaidEvent.
        if (event.getHand() == InteractionHand.MAIN_HAND && maid.isOwnedBy(event.getEntity())) {
            return;
        }

        if (stack.is(ModItems.PROPOSAL_RING.get())) {
            handleProposal(event.getEntity(), maid, stack, event.getHand());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (stack.is(ModItems.YES_PILLOW.get())) {
            handleBreedingTest(event.getEntity(), maid);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (stack.is(ModItems.FAMILY_TREE_TOOL.get())) {
            handleFamilyTreeTool(event.getEntity(), maid);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (stack.is(ModItems.MARRIAGE_CONSENT_FORM.get())) {
            if (handleMarriageConsentMaidSelection(event.getEntity(), maid, stack)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    /**
     * 处理玩家手持求婚戒指直接右键女仆的旧交互。
     *
     * <p>正式流程已经收敛到互动 UI 的“结婚”选项里；这里仍保留两件事：
     * 1. 对小女仆或直系子代求婚时立即惩罚；
     * 2. 对普通成年女仆只提示玩家去互动面板完成誓约。
     */
    private static void handleProposal(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack, InteractionHand interactionHand) {
        if (MaidChildEntity.shouldStayChild(maid)
                || (MaidChildEntity.isParentOfMaid(maid, player.getUUID()) && !isConsentApprovedPlayer(maid, player.getUUID()))) {
            if (maid.level() instanceof ServerLevel level) {
                summonProposalPunishLightning(level, player, true);
            }
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.own_child_cannot_marry", maid.getDisplayName()));
            return;
        }

        player.sendSystemMessage(Component.literal("请在互动面板中选择“结婚”完成婚礼誓约。"));
        return;
        /*
        if (MaidChildEntity.isParentOfMaid(maid, player.getUUID()) && !isConsentApprovedPlayer(maid, player.getUUID())) {
            if (maid.level() instanceof ServerLevel level) {
                summonProposalPunishLightning(level, player, true);
            }
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.own_child_cannot_marry", maid.getDisplayName()));
            return;
        }

        MarriageData currentData = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        clearExpiredTransfer(maid);
        if (maid.isOwnedBy(player) && tryConfirmTransfer(player, maid, currentData)) {
            return;
        }

        if (!maid.isOwnedBy(player)) {
            if (isBornLineageMaid(maid)) {
                player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.need_owner", maid.getDisplayName()));
                return;
            }
            if (!canRequestOwnershipTransfer(maid, currentData)) {
                player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.need_owner", maid.getDisplayName()));
                return;
            }
            requestOwnershipTransfer(player, maid, stack, currentData);
            return;
        }

        int requiredFavorability = RomanceSleepManager.resolveRequiredFavorability(player);
        if (maid.getFavorability() < requiredFavorability) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, 
                    "message.maidmarriage.proposal.need_favorability", maid.getDisplayName(), requiredFavorability));
            return;
        }
        if (!RomanceSleepManager.resolveHaremMode(player) && hasOtherMarriage(player, maid)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.harem_disabled"));
            return;
        }

        if (currentData.isMarriedWith(player.getUUID())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.already_married_with_you", maid.getDisplayName()));
            return;
        }
        if (currentData.married()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.already_married", maid.getDisplayName()));
            return;
        }

        ItemStack mainHandRing = player.getMainHandItem();
        if (!mainHandRing.is(ModItems.PROPOSAL_RING.get())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.need_mainhand_ring"));
            return;
        }

        ItemStack offhandRing = player.getOffhandItem();
        if (!offhandRing.is(ModItems.PROPOSAL_RING.get())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.need_offhand_ring"));
            return;
        }
        if (mainHandRing == offhandRing || isRingUsed(mainHandRing) || isRingUsed(offhandRing)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.ring_used"));
            return;
        }

        ItemStack maidRing = mainHandRing.copy();
        maidRing.setCount(1);

        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, currentData.marry(player.getUUID(), maid.level().getGameTime()));
        engraveRing(offhandRing, player, maid);
        engraveRing(maidRing, player, maid);
        giveRingToMaid(maid, maidRing);
        maid.getPersistentData().remove(TAG_CONSENT_APPROVED_PLAYER);

        consumeMainHandProposalRing(player, stack, interactionHand);
        giveMarriagePillows(player, maid);
        if (!RomanceSleepManager.resolveHaremMode(player)) {
            player.getPersistentData().putUUID(TAG_PLAYER_PRIMARY_MAID, maid.getUUID());
        }

        if (maid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                    10, 0.25, 0.25, 0.25, 0.01);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.success", maid.getDisplayName()));
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModAdvancements.grantMarriage(serverPlayer);
            RomanceSleepManager.startProposalDialogue(serverPlayer, maid);
        }
        */
    }

    private static boolean canRequestOwnershipTransfer(EntityMaid maid, MarriageData currentData) {
        if (!isBornLineageMaid(maid) || MaidChildEntity.shouldStayChild(maid)) {
            return false;
        }
        if (!currentData.married()) {
            return true;
        }
        UUID owner = maid.getOwnerUUID();
        return owner != null && currentData.isMarriedWith(owner);
    }

    private static boolean isBornLineageMaid(EntityMaid maid) {
        if (maid.getTags().contains(MaidChildEntity.BORN_MAID_TAG)) {
            return true;
        }
        CompoundTag data = maid.getPersistentData();
        if (data.hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)
                || data.hasUUID(MaidChildEntity.PERSISTENT_FATHER_UUID_KEY)) {
            return true;
        }
        var lineage = maid.getData(ModTaskData.CHILD_LINEAGE_DATA);
        if (lineage != null && lineage.bornMaid()) {
            return true;
        }
        var state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        return state != null && (state.mother().isPresent() || state.father().isPresent());
    }

    private static void requestOwnershipTransfer(net.minecraft.world.entity.player.Player proposer, EntityMaid maid, ItemStack interactionStack, MarriageData currentData) {
        int requiredFavorability = RomanceSleepManager.resolveRequiredFavorability(proposer);
        if (maid.getFavorability() < requiredFavorability) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, 
                    "message.maidmarriage.proposal.need_favorability", maid.getDisplayName(), requiredFavorability));
            return;
        }
        if (!RomanceSleepManager.resolveHaremMode(proposer) && hasOtherMarriage(proposer, maid)) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.harem_disabled"));
            return;
        }
        if (currentData.isMarriedWith(proposer.getUUID())) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.already_married_with_you", maid.getDisplayName()));
            return;
        }

        ItemStack mainHandRing = proposer.getMainHandItem();
        if (!mainHandRing.is(ModItems.PROPOSAL_RING.get())) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.need_mainhand_ring"));
            return;
        }

        ItemStack offhandRing = proposer.getOffhandItem();
        if (!offhandRing.is(ModItems.PROPOSAL_RING.get())) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.need_offhand_ring"));
            return;
        }
        if (mainHandRing == offhandRing || isRingUsed(mainHandRing) || isRingUsed(offhandRing)) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.ring_used"));
            return;
        }

        UUID ownerUuid = maid.getOwnerUUID();
        if (ownerUuid == null || !(maid.level() instanceof ServerLevel level)) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.need_owner", maid.getDisplayName()));
            return;
        }
        net.minecraft.server.level.ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) {
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.transfer.owner_offline"));
            return;
        }

        long expireTick = level.getGameTime() + TRANSFER_CONFIRM_TICKS;
        PENDING_TRANSFERS.put(maid.getUUID(), new PendingTransferRequest(ownerUuid, proposer.getUUID(), expireTick));
        proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, 
                "message.maidmarriage.proposal.transfer.requested",
                maid.getDisplayName(),
                owner.getDisplayName()));
        owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, 
                "message.maidmarriage.proposal.transfer.need_confirm",
                proposer.getDisplayName(),
                maid.getDisplayName()));
    }

    private static boolean tryConfirmTransfer(net.minecraft.world.entity.player.Player owner, EntityMaid maid, MarriageData currentData) {
        PendingTransferRequest request = PENDING_TRANSFERS.get(maid.getUUID());
        if (request == null) {
            return false;
        }
        if (!owner.getUUID().equals(request.ownerUuid)) {
            return false;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.getGameTime() > request.expireTick) {
            PENDING_TRANSFERS.remove(maid.getUUID());
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.proposal.transfer.expired"));
            return true;
        }
        net.minecraft.server.level.ServerPlayer proposer = level.getServer().getPlayerList().getPlayer(request.proposerUuid);
        if (proposer == null) {
            PENDING_TRANSFERS.remove(maid.getUUID());
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.proposal.transfer.proposer_offline"));
            return true;
        }
        if (!maid.isOwnedBy(owner)) {
            PENDING_TRANSFERS.remove(maid.getUUID());
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.proposal.transfer.invalid"));
            return true;
        }
        if (!consumeAnyProposalRing(proposer)) {
            PENDING_TRANSFERS.remove(maid.getUUID());
            proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.transfer.need_ring"));
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.proposal.transfer.need_ring"));
            return true;
        }

        maid.tame(proposer);
        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, currentData.marry(proposer.getUUID(), level.getGameTime()));
        maid.getPersistentData().remove(TAG_CONSENT_APPROVED_PLAYER);
        if (!RomanceSleepManager.resolveHaremMode(proposer)) {
            proposer.getPersistentData().putUUID(TAG_PLAYER_PRIMARY_MAID, maid.getUUID());
        }
        if (owner.getPersistentData().hasUUID(TAG_PLAYER_PRIMARY_MAID)
                && owner.getPersistentData().getUUID(TAG_PLAYER_PRIMARY_MAID).equals(maid.getUUID())) {
            owner.getPersistentData().remove(TAG_PLAYER_PRIMARY_MAID);
        }

        PENDING_TRANSFERS.remove(maid.getUUID());
        proposer.sendSystemMessage(DialogueScriptManager.componentForPlayer(proposer, "message.maidmarriage.proposal.transfer.success", maid.getDisplayName()));
        owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.proposal.transfer.success_owner", maid.getDisplayName()));

        level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(), 10, 0.25, 0.25, 0.25, 0.01);
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        ModAdvancements.grantMarriage(proposer);
        RomanceSleepManager.startProposalDialogue(proposer, maid);
        return true;
    }

    private static void clearExpiredTransfer(EntityMaid maid) {
        PendingTransferRequest request = PENDING_TRANSFERS.get(maid.getUUID());
        if (request == null || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() > request.expireTick) {
            PENDING_TRANSFERS.remove(maid.getUUID());
        }
    }

    private static void handleBreedingTest(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.need_owner", maid.getDisplayName()));
            return;
        }
        if (!isMarriedWithPlayer(maid, player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.not_married", maid.getDisplayName()));
            return;
        }
        if (player.level().isDay()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.not_time_yet"));
            RomanceSleepManager.speakSingleLine(maid, "dialogue.maidmarriage.daytime");
            return;
        }

        RomanceSleepManager.tryStartRomanceRhythmThenSleep(serverPlayer, maid);
    }

    private static void handleLongingTest(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.need_owner", maid.getDisplayName()));
            return;
        }
        MaidMoodManager.setMood(maid, MaidMoodManager.LOVE_TEST_VALUE);
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.debug.longing_applied"));
    }

    private static boolean handleSauceDuck(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!maid.isOwnedBy(player)) {
            return false;
        }

        boolean consumed = false;
        long now = maid.level().getGameTime();
        PregnancyData pregnancyData = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        if (ModConfigs.postpartumRecoveryEnabled() && pregnancyData.isInPostpartumRecovery(now)) {
            PregnancyData updated = pregnancyData.reducePostpartumRemainingByPercent(now, POSTPARTUM_SAUCE_DUCK_REDUCE_PERCENT);
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.postpartum.reduce", maid.getDisplayName(), "10%"));
            consumed = true;
        } else if (MaidChildEntity.shouldStayChild(maid)) {
            MaidChildEntity.shortenGrowthByDays(maid, 1);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child.growth.reduce", maid.getDisplayName(), 1));
            consumed = true;
        }

        if (!consumed) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (maid.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, maid.getX(), maid.getY(1.0D), maid.getZ(),
                    6, 0.25D, 0.2D, 0.25D, 0.01D);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.0F);
        return true;
    }

    private static boolean handleGrowthTool(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!maid.isOwnedBy(player)) {
            return false;
        }
        if (!MaidChildEntity.shouldStayChild(maid)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child.growth_tool.not_child", maid.getDisplayName()));
            return true;
        }
        if (!MaidChildEntity.setGrowthRemainingSeconds(maid, 5)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child.growth_tool.fail", maid.getDisplayName()));
            return true;
        }
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child.growth_tool.countdown_set", maid.getDisplayName(), 5));
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide()) {
            return;
        }
        UUID playerId = event.player.getUUID();
        Integer elapsed = PROPOSAL_PUNISH_ACTIVE.get(playerId);
        if (elapsed == null) {
            return;
        }
        if (!event.player.isAlive()) {
            PROPOSAL_PUNISH_ACTIVE.remove(playerId);
            return;
        }
        int nextElapsed = elapsed + 1;
        PROPOSAL_PUNISH_ACTIVE.put(playerId, nextElapsed);
        if (nextElapsed % PROPOSAL_PUNISH_STRIKE_INTERVAL_TICKS != 0) {
            return;
        }
        float currentHealth = event.player.getHealth();
        if (currentHealth <= PROPOSAL_PUNISH_TARGET_HEALTH) {
            PROPOSAL_PUNISH_ACTIVE.remove(playerId);
            return;
        }
        if (event.player.level() instanceof ServerLevel level) {
            summonProposalPunishLightning(level, event.player, false);
        }
        applyProposalFreezeEffect(event.player);
        float damage = currentHealth > PROPOSAL_PUNISH_HEALTH_THRESHOLD
                ? currentHealth * PROPOSAL_PUNISH_DAMAGE_RATIO
                : PROPOSAL_PUNISH_DAMAGE_LOW_HEALTH;
        float nextHealth = Math.max(PROPOSAL_PUNISH_TARGET_HEALTH, currentHealth - damage);
        event.player.setHealth(nextHealth);
        if (nextHealth <= PROPOSAL_PUNISH_TARGET_HEALTH) {
            PROPOSAL_PUNISH_ACTIVE.remove(playerId);
        }
    }

    private static boolean handleBirthTool(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return true;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.need_owner", maid.getDisplayName()));
            return true;
        }
        RomanceSleepManager.forceBirthNow(serverPlayer, maid);
        return true;
    }

    private static boolean handlePregnancyTestTool(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return true;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pregnancy_test_tool.need_owner", maid.getDisplayName()));
            return true;
        }
        if (!isMarriedWithPlayer(maid, player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.not_married", maid.getDisplayName()));
            return true;
        }

        long now = maid.level().getGameTime();
        PregnancyData pregnancy = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        if (pregnancy.pregnant()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pregnancy_test_tool.already_pregnant", maid.getDisplayName()));
            return true;
        }
        if (pregnancy.isInPostpartumRecovery(now)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.postpartum_blocked", maid.getDisplayName()));
            return true;
        }

        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, pregnancy.conceive(serverPlayer.getUUID(), now));
        RomanceSleepManager.speakSingleLine(maid, "dialogue.maidmarriage.pregnant");
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pregnancy_test_tool.success", maid.getDisplayName()));

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (maid.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                    10, 0.3D, 0.2D, 0.3D, 0.02D);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75F, 1.2F);
        return true;
    }

    private static boolean handlePregnancySettlementTool(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return true;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pregnancy_test_tool.need_owner", maid.getDisplayName()));
            return true;
        }
        if (!isMarriedWithPlayer(maid, player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.not_married", maid.getDisplayName()));
            return true;
        }

        long now = maid.level().getGameTime();
        PregnancyData pregnancy = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        if (pregnancy.pregnant()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pregnancy_test_tool.already_pregnant", maid.getDisplayName()));
            return true;
        }
        if (pregnancy.isInPostpartumRecovery(now)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.breed.postpartum_blocked", maid.getDisplayName()));
            return true;
        }

        RomanceSleepManager.DebugConceptionResult result = RomanceSleepManager.debugSettleConception(serverPlayer, maid, pregnancy);
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                player,
                "message.maidmarriage.pregnancy_settlement_tool.result",
                maid.getDisplayName(),
                String.format(java.util.Locale.ROOT, "%.2f%%", result.chance() * 100.0D),
                DialogueScriptManager.component(result.conceived()
                        ? "message.maidmarriage.common.yes"
                        : "message.maidmarriage.common.no"),
                DialogueScriptManager.component(result.twins()
                        ? "message.maidmarriage.common.yes"
                        : "message.maidmarriage.common.no")
        ));
        return true;
    }

    private static boolean handleFamilyTreeTool(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        ChildLineageData lineage = maid.getData(ModTaskData.CHILD_LINEAGE_DATA);
        MarriageData marriageData = maid.getData(ModTaskData.MARRIAGE_DATA);
        List<EntityMaid> children = collectDirectChildrenEntities(maid);

        String selfName = maid.getDisplayName().getString();
        String spouseName = resolveLineageName(maid, marriageData == null ? null : marriageData.spouse().orElse(null)).getString();
        String motherName = resolveLineageName(maid, lineage == null ? null : lineage.mother().orElse(null)).getString();
        String fatherName = resolveLineageName(maid, lineage == null ? null : lineage.father().orElse(null)).getString();
        String grandParentName = resolveLineageName(maid, lineage == null ? null : lineage.grandParent().orElse(null)).getString();

        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.header", selfName));
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.self", selfName));
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.mother", motherName));
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.father", fatherName));
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.grand_parent", grandParentName));
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.spouse", spouseName));

        if (children.isEmpty()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.children.none"));
            return true;
        }

        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.children.count", children.size()));
        int childLimit = Math.min(children.size(), 8);
        for (int index = 0; index < childLimit; index++) {
            EntityMaid child = children.get(index);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                    player,
                    "message.maidmarriage.family_tree.children.entry",
                    index + 1,
                    child.getDisplayName().getString()));
        }
        if (children.size() > 8) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.family_tree.children.more", children.size() - 8));
        }
        return true;
    }
    /**
     * 闁汇垹鐤囬顒佺▕閿旂虎鍎戝☉鎾亾婵縿鍎荤槐鐗堢▔鐠佽櫕鐪介悗闈涙贡濞蹭即寮介崶褏鎽嶅ù鐙呯到閵堣櫕绂掗崱娆愮０缂佹梻濯寸槐婵堟媼閺夎法绉跨€垫澘鎳愪簺濞存嚎鍊曢顔炬寬鎺抽埀?     *
     * @param player 鐟滅増鎸告晶鐘虫媴鐠恒劍鏆忛柣銏犵枃椤曨剚绋婇敂鐐暠闁绘壕鏅涢宥夋晬閸綆娲ｆ慨鐟板€瑰Σ鍛婄附閸忓懐鐭庡☉鎾诡唺濮瑰鏁?     * @param maid   閻炴凹鍋婇埀顒€顦懙鎴︽儍閸曨偆鎽嶅ù鐙呯到閵堣櫕绂?     * @return 濠殿喖顑囩划鎾存交閺傛寧绀€ true闁挎稑鐭侀妴鍐矆妤﹁法绠规繛鍡忊偓鍙夊攭濞存粍甯掗崙锟犳偨鏉堚晜鏆ら悹鍥腹閸旂喐寰勯崟顓熷€?     */
    private static boolean handleMarriageConsentMaidSelection(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.consent.need_owner", maid.getDisplayName()));
            return true;
        }
        if (!isBornLineageMaid(maid)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.consent.need_child", maid.getDisplayName()));
            return true;
        }
        if (MaidChildEntity.shouldStayChild(maid)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.consent.need_adult", maid.getDisplayName()));
            return true;
        }
        MarriageConsentFormItem.bindMaid(stack, maid.getUUID(), maid.getDisplayName());
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.consent.maid_selected", maid.getDisplayName()));
        return true;
    }

    /**
     * 闁汇垹鐤囬顒佺▕閿旂虎鍎戝ù婊冩湰椤掔偤鏁嶅顐㈢槣濞存粌鎼顕€鎯勯鐣屽灱闁绘壕鏅涢宥囨兜椤旀鍚囬柟鍝勭墛濞煎牓鏁嶇仦鍊熷珯缂佹柨顑呭畵鍡欑矓鐠佽櫕鍞夊┑鍌氬帠缁繘骞嶉埀顒勫嫉婢跺缍€闁?     * 缂佸顔婂锕傚籍閺堢數绐楅梺鎻掔Ф閻ゅ棙绺介崗鍛煄濠靛倽濮ら崝鍛▔鎼粹槅鐓煎┑顔垮吹婵悂骞€娓氬﹦绀夌痪顓у枙缁绘岸宕ユ惔锝囨暰闊洤鎳橀妴蹇涙煂瀹ュ棙鐓€闁糕晝鎳撻崥鍥礃瀹ュ棛婀村┑鐘冲搸閳?     *
     * @param owner  闁告鍠嶇€靛本绂?     * @param target 闁哄倿顣︾€靛本绂嶉崫鍕ㄥ亾濞嗘挴鍋撴径灞借礋閻?     * @param stack  濞戞挾绮晶婊堟偨鐎圭媭鍤炲☉?     * @return 闁哄嫷鍨伴幆浣衡偓鐟版湰閸ㄦ碍寰勯崟顓熷€?     */
    private static boolean handleMarriageConsentPlayerSelection(net.minecraft.world.entity.player.Player owner,
                                                                net.minecraft.world.entity.player.Player target,
                                                                ItemStack stack) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return true;
        }
        if (owner.getUUID().equals(target.getUUID())) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.target_self"));
            return true;
        }
        Optional<UUID> selected = MarriageConsentFormItem.getBoundMaidUuid(stack);
        if (selected.isEmpty()) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.select_maid_first"));
            return true;
        }
        UUID maidUuid = selected.get();
        EntityMaid maid = findMaidByUuid(level.getServer(), maidUuid);
        if (maid == null || !maid.isAlive()) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.maid_missing"));
            return true;
        }
        if (!maid.isOwnedBy(owner)) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.not_owner_anymore", maid.getDisplayName()));
            return true;
        }
        if (!isBornLineageMaid(maid)) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.need_child", maid.getDisplayName()));
            return true;
        }
        if (MaidChildEntity.shouldStayChild(maid)) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.need_adult", maid.getDisplayName()));
            return true;
        }
        if (MaidChildEntity.isParentOfMaid(maid, target.getUUID())) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.target_blocked_parent", target.getDisplayName()));
            return true;
        }
        if (!(target instanceof ServerPlayer targetPlayer)) {
            owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.target_offline"));
            return true;
        }

        maid.tame(targetPlayer);
        MaidMoodManager.setFavorabilityWithRefresh(maid, 0, FAVORABILITY_CAP);
        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        maid.getPersistentData().putUUID(TAG_CONSENT_APPROVED_PLAYER, targetPlayer.getUUID());
        MarriageConsentFormItem.bindTargetPlayer(stack, targetPlayer.getUUID(), targetPlayer.getDisplayName());
        clearExpiredTransfer(maid);
        PENDING_TRANSFERS.remove(maid.getUUID());

        if (!RomanceSleepManager.resolveHaremMode(targetPlayer)) {
            targetPlayer.getPersistentData().putUUID(TAG_PLAYER_PRIMARY_MAID, maid.getUUID());
        }
        if (owner.getPersistentData().hasUUID(TAG_PLAYER_PRIMARY_MAID)
                && owner.getPersistentData().getUUID(TAG_PLAYER_PRIMARY_MAID).equals(maid.getUUID())) {
            owner.getPersistentData().remove(TAG_PLAYER_PRIMARY_MAID);
        }
        level.sendParticles(ParticleTypes.ENCHANT, maid.getX(), maid.getY(1.0D), maid.getZ(),
                10, 0.2D, 0.2D, 0.2D, 0.01D);
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.0F);
        owner.sendSystemMessage(DialogueScriptManager.componentForPlayer(owner, "message.maidmarriage.consent.transfer_success_owner",
                maid.getDisplayName(), targetPlayer.getDisplayName()));
        targetPlayer.sendSystemMessage(DialogueScriptManager.componentForPlayer(targetPlayer, "message.maidmarriage.consent.transfer_success_target",
                maid.getDisplayName()));
        if (owner instanceof ServerPlayer ownerServerPlayer) {
            RomanceSleepManager.startTransferFarewellDialogue(ownerServerPlayer, maid);
        }
        return true;
    }

    /**
     * 闁告帇鍊栭弻鍥蓟閹邦喖璐熼悗纭呭煐濡叉悂宕ラ敂鑺バ﹂悹鍥ュ劚閵堣櫕绂掗崱姘兼蕉闁汇垹鐤囬顒佺▕閿旇棄鎴块柡澶婂暢缁诲啴鎯冮崟顓熺獥闁哄秴娲ㄧ敮铏光偓纭呯堪閳?     * 閻犲洢鍎版穱濠囧箒椤栨瑧绋婂☉鎾跺劋閻増绻濆顒€顤呴悶娑掑亾濞存粍褰冮崹鐣屸偓瑙勬皑濞堟垿宕楀鍐亢闁谎嗘閹洟宕￠弴顏嗙濞戞挸绉甸弫濂稿矗濡吋绾紒顖涙椤㈠懏绂嶉懠棰濇綈闁告帗鐟﹀﹢浼寸叕椤愮姭鍋?     */
    private static boolean isConsentApprovedPlayer(EntityMaid maid, UUID playerUuid) {
        CompoundTag data = maid.getPersistentData();
        return data.hasUUID(TAG_CONSENT_APPROVED_PLAYER)
                && playerUuid.equals(data.getUUID(TAG_CONSENT_APPROVED_PLAYER));
    }

    /**
     * 閻犳亽鍔庡ǎ顔芥償閿旂晫鍙€闁归潧澧藉ú浼村冀閸パ佸仢濞寸姴妫庨埀?     * 闁汇垹鐤囬顒佺▕閿旂虎鍎戝ù婊冩湰椤掔偤鎮欓崷顓炶礋閻庣鍩栧鍌炴晬瀹€鍕粯閻熸洑妞掔划鐘诲礂閵婏附绠涚紓浣规綑鐎硅櫕绋夐鐐垫毎濞达絽绉堕鍥ㄧ▔閳ь剙顫㈤妷鈹惧亾婢跺鍘柣銊ュ閵堣櫕绂掗崱妤冩澖濞达絾鎸堕埀?     */
    private static EntityMaid findMaidByUuid(MinecraftServer server, UUID maidUuid) {
        for (ServerLevel serverLevel : server.getAllLevels()) {
            Entity entity = serverLevel.getEntity(maidUuid);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }

    private static Component resolveLineageName(EntityMaid maid, UUID uuid) {
        if (uuid == null) {
            return DialogueScriptManager.component("message.maidmarriage.family_tree.unknown");
        }
        if (maid.level() instanceof ServerLevel serverLevel) {
            Component anyLevelName = resolveEntityDisplayNameFromAllLevels(serverLevel.getServer(), uuid);
            if (anyLevelName != null) {
                return anyLevelName;
            }
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                return player.getDisplayName();
            }
            Optional<GameProfile> profile = serverLevel.getServer().getProfileCache().get(uuid);
            if (profile.isPresent() && profile.get().getName() != null && !profile.get().getName().isBlank()) {
                return Component.literal(profile.get().getName());
            }
        }
        String shortUuid = uuid.toString();
        if (shortUuid.length() > 8) {
            shortUuid = shortUuid.substring(0, 8);
        }
        return DialogueScriptManager.component("message.maidmarriage.family_tree.uuid_fallback", shortUuid);
    }

    /**
     * 闁革负鍔岄崣蹇曠磼閺夋垵顔婂☉鎿冨幗閻擄繝骞嶉幆褏鏉藉ù锝嗘尭閼荤喐娼婚弬鎸庣闁哄嫬澧介妵姘跺触瀹ュ啠鍋?     * 闁哄啫绻楀銊╁蓟閵夘煈鍤勯柡鍐啇缁辨繈鎮ラ懜鐢垫Г/缂佷焦鐗炵欢銏ゅ矗椤栨繂鍘村☉鎾崇Т濠€顏囥亹閹惧啿顤呯紓浣规綑鐎规娊鏁嶇仦鐓庘枏闁活潿鍔忓▔鏇犵磼閺夋垵顔婃俊顐熷亾缂佷究鍨硅ぐ鍙夊濡搫甯ラ柡鍕⒔閵囨岸鎯囬悢椋庢澖闁告艾绉撮悺褔鎳撳畝鍕 UUID闁?     */
    @Nullable
    private static Component resolveEntityDisplayNameFromAllLevels(MinecraftServer server, UUID entityUuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityUuid);
            if (entity != null) {
                return entity.getDisplayName();
            }
        }
        return null;
    }

    private static List<Component> collectChildrenNames(EntityMaid maid) {
        List<Component> results = new ArrayList<>();
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return results;
        }
        UUID motherId = maid.getUUID();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (!(entity instanceof EntityMaid child) || child.getUUID().equals(motherId)) {
                continue;
            }
            if (!isChildOfMaid(child, motherId)) {
                continue;
            }
            results.add(child.getDisplayName());
        }
        return results;
    }

    private static List<EntityMaid> collectDirectChildrenEntities(EntityMaid maid) {
        List<EntityMaid> results = new ArrayList<>();
        if (!(maid.level() instanceof ServerLevel serverLevel)) {
            return results;
        }
        UUID motherId = maid.getUUID();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (!(entity instanceof EntityMaid child) || child.getUUID().equals(motherId)) {
                continue;
            }
            if (isChildOfMaid(child, motherId)) {
                results.add(child);
            }
        }
        return results;
    }

    private static boolean isChildOfMaid(EntityMaid maid, UUID motherId) {
        CompoundTag tag = maid.getPersistentData();
        if (tag.hasUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY)
                && motherId.equals(tag.getUUID(MaidChildEntity.PERSISTENT_MOTHER_UUID_KEY))) {
            return true;
        }
        ChildLineageData lineage = maid.getData(ModTaskData.CHILD_LINEAGE_DATA);
        if (lineage != null && lineage.mother().filter(motherId::equals).isPresent()) {
            return true;
        }
        var state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        return state != null && state.mother().filter(motherId::equals).isPresent();
    }

    private static boolean tryHandleFlowerGift(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        if (!maid.isOwnedBy(player)) {
            return false;
        }
        if (stack.is(ModItems.RAINBOW_BOUQUET.get())) {
            applyFlowerGiftResult(player, maid, stack, "message.maidmarriage.flower.color.rainbow",
                    "dialogue.maidmarriage.flower.rainbow", "dialogue.maidmarriage.child.flower.rainbow",
                    RAINBOW_BOUQUET_FAVORABILITY_GAIN);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                ModAdvancements.grantRainbowBouquet(serverPlayer);
            }
            return true;
        }

        FlowerGift gift = FlowerGift.from(stack);
        if (gift == null) {
            return false;
        }

        if (hasGiftedFlowerColor(maid, gift)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, 
                    "message.maidmarriage.flower.already_gifted",
                    maid.getDisplayName(),
                    Component.translatable(gift.colorNameKey)));
            return true;
        }
        setGiftedFlowerColor(maid, gift);

        applyFlowerGiftResult(player, maid, stack, gift.colorNameKey, gift.dialogueKey, gift.childDialogueKey,
                NORMAL_FLOWER_FAVORABILITY_GAIN);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ModAdvancements.grantFlowerGift(serverPlayer, gift.advancementSuffix);
        }
        return true;
    }

    /**
     * 送礼面板专用的花束处理入口。
     *
     * <p>普通右键送花和送礼面板共用同一套花束规则与成就逻辑，
     * 这样不会因为多加了一个 UI 就出现两套花束结算。
     */
    public static boolean handleFlowerGiftFromUi(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack) {
        return tryHandleFlowerGift(player, maid, stack);
    }

    /**
     * 判断花束是否会被真正收下。
     *
     * <p>重复颜色只显示提示，不消耗物品，也不占用送礼面板的每日次数。
     */
    public static boolean canAcceptFlowerGiftFromUi(EntityMaid maid, ItemStack stack) {
        if (maid == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.RAINBOW_BOUQUET.get())) {
            return true;
        }
        FlowerGift gift = FlowerGift.from(stack);
        return gift != null && !hasGiftedFlowerColor(maid, gift);
    }

    private static boolean hasGiftedFlowerColor(EntityMaid maid, FlowerGift gift) {
        int giftedMask = maid.getPersistentData().getInt(TAG_FLOWER_GIFT_MASK);
        return (giftedMask & gift.bit) != 0;
    }

    private static void setGiftedFlowerColor(EntityMaid maid, FlowerGift gift) {
        int giftedMask = maid.getPersistentData().getInt(TAG_FLOWER_GIFT_MASK);
        maid.getPersistentData().putInt(TAG_FLOWER_GIFT_MASK, giftedMask | gift.bit);
    }

    private static void applyFlowerGiftResult(net.minecraft.world.entity.player.Player player, EntityMaid maid, ItemStack stack,
                                              String colorNameKey, String dialogueKey, String childDialogueKey,
                                              int favorabilityGain) {
        MaidMoodManager.applyLimitedInteractionMoodGain(maid, MaidMoodManager.EVENT_FLOWER);
        int actualFavorabilityGain = MaidMoodManager.applyInteractionFavorabilityGain(maid, favorabilityGain, FAVORABILITY_CAP);
        int updatedFavorability = maid.getFavorability();
        String resolvedDialogueKey = MaidChildEntity.shouldStayChild(maid) ? childDialogueKey : dialogueKey;
        RomanceSleepManager.speakSingleLine(maid, resolvedDialogueKey);
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, 
                "message.maidmarriage.flower.gift",
                maid.getDisplayName(),
                Component.translatable(colorNameKey),
                actualFavorabilityGain,
                updatedFavorability));

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (maid.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.1), maid.getZ(),
                    6, 0.25, 0.2, 0.25, 0.02);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.45F, 1.45F);
    }

    private static boolean isMarriedWithPlayer(EntityMaid maid, net.minecraft.world.entity.player.Player player) {
        MarriageData data = maid.getData(ModTaskData.MARRIAGE_DATA);
        return data != null && data.isMarriedWith(player.getUUID());
    }

    private static boolean hasOtherMarriage(net.minecraft.world.entity.player.Player player, EntityMaid currentMaid) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.hasUUID(TAG_PLAYER_PRIMARY_MAID)) {
            return false;
        }
        return !tag.getUUID(TAG_PLAYER_PRIMARY_MAID).equals(currentMaid.getUUID());
    }

    static boolean isRingUsed(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_RING_USED);
    }

    static void engraveRing(ItemStack ring, net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        CompoundTag tag = ring.getOrCreateTag();
        tag.putBoolean(TAG_RING_USED, true);
        tag.putUUID(TAG_RING_PLAYER, player.getUUID());
        tag.putUUID(TAG_RING_MAID, maid.getUUID());

        ring.setHoverName(Component.translatable("item.maidmarriage.vow_ring"));
        CompoundTag display = ring.getOrCreateTagElement("display");
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("item.maidmarriage.vow_ring.pair", player.getName(), maid.getName()))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("item.maidmarriage.vow_ring.desc"))));
        display.put("Lore", lore);
    }

    static void giveRingToMaid(EntityMaid maid, ItemStack ring) {
        if (ring.isEmpty()) {
            return;
        }
        // 婚礼剧情有可能因为客户端重复发包被再次请求；女仆身上已有同一对誓约戒指时不再补发。
        if (hasSameVowRingInMaidInventory(maid, ring)) {
            return;
        }
        if (maid.getMainHandItem().isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, ring);
            return;
        }
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), ring, false);
        if (!remaining.isEmpty()) {
            ItemEntity drop = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), remaining);
            maid.level().addFreshEntity(drop);
        }
    }

    private static boolean hasSameVowRingInMaidInventory(EntityMaid maid, ItemStack ring) {
        if (isSameVowRing(maid.getMainHandItem(), ring) || isSameVowRing(maid.getOffhandItem(), ring)) {
            return true;
        }
        IItemHandler inventory = maid.getAvailableInv(false);
        if (inventory == null) {
            return false;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (isSameVowRing(inventory.getStackInSlot(slot), ring)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSameVowRing(ItemStack existing, ItemStack expected) {
        if (existing.isEmpty() || expected.isEmpty()
                || !existing.is(ModItems.PROPOSAL_RING.get()) || !expected.is(ModItems.PROPOSAL_RING.get())) {
            return false;
        }
        CompoundTag existingTag = existing.getTag();
        CompoundTag expectedTag = expected.getTag();
        if (existingTag == null || expectedTag == null
                || !existingTag.getBoolean(TAG_RING_USED) || !expectedTag.getBoolean(TAG_RING_USED)
                || !existingTag.hasUUID(TAG_RING_PLAYER) || !expectedTag.hasUUID(TAG_RING_PLAYER)
                || !existingTag.hasUUID(TAG_RING_MAID) || !expectedTag.hasUUID(TAG_RING_MAID)) {
            return false;
        }
        return existingTag.getUUID(TAG_RING_PLAYER).equals(expectedTag.getUUID(TAG_RING_PLAYER))
                && existingTag.getUUID(TAG_RING_MAID).equals(expectedTag.getUUID(TAG_RING_MAID));
    }

    static void giveMarriagePillows(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        ItemStack pillowForMaid = new ItemStack(ModItems.YES_PILLOW.get());
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), pillowForMaid, false);
        if (!remaining.isEmpty()) {
            ItemEntity drop = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), remaining);
            maid.level().addFreshEntity(drop);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.proposal.pillow_maid_inventory_full", maid.getDisplayName()));
        }
    }

    static void clearConsentApproval(EntityMaid maid) {
        maid.getPersistentData().remove(TAG_CONSENT_APPROVED_PLAYER);
    }

    static void markPrimaryMaidIfNeeded(net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        if (!RomanceSleepManager.resolveHaremMode(player)) {
            player.getPersistentData().putUUID(TAG_PLAYER_PRIMARY_MAID, maid.getUUID());
        }
    }

    private static void consumeMainHandProposalRing(net.minecraft.world.entity.player.Player player, ItemStack interactionStack, InteractionHand interactionHand) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.PROPOSAL_RING.get())) {
            consumePlayerHandItem(player, InteractionHand.MAIN_HAND);
            if (interactionHand == InteractionHand.MAIN_HAND
                    && !interactionStack.isEmpty()
                    && interactionStack.is(ModItems.PROPOSAL_RING.get())) {
                interactionStack.shrink(1);
            }
        } else if (interactionHand == InteractionHand.MAIN_HAND
                && !interactionStack.isEmpty()
                && interactionStack.is(ModItems.PROPOSAL_RING.get())) {
            interactionStack.shrink(1);
        } else {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack invStack = inventory.items.get(i);
                if (invStack.is(ModItems.PROPOSAL_RING.get())) {
                    invStack.shrink(1);
                    break;
                }
            }
        }
        player.getInventory().setChanged();
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
            syncPlayerInventorySlot(serverPlayer, InteractionHand.MAIN_HAND);
        }
    }

    private static boolean consumeAnyProposalRing(net.minecraft.server.level.ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.PROPOSAL_RING.get())) {
            consumePlayerHandItem(player, InteractionHand.MAIN_HAND);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            syncPlayerInventorySlot(player, InteractionHand.MAIN_HAND);
            return true;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(ModItems.PROPOSAL_RING.get())) {
            consumePlayerHandItem(player, InteractionHand.OFF_HAND);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            syncPlayerInventorySlot(player, InteractionHand.OFF_HAND);
            return true;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack invStack = inventory.items.get(i);
            if (!invStack.is(ModItems.PROPOSAL_RING.get())) {
                continue;
            }
            invStack.shrink(1);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static void consumePlayerHandItem(net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack handStack = player.getItemInHand(hand);
        if (handStack.isEmpty()) {
            return;
        }
        if (handStack.getCount() <= 1) {
            handStack.setCount(0);
            if (hand == InteractionHand.MAIN_HAND) {
                player.getInventory().items.set(player.getInventory().selected, ItemStack.EMPTY);
            } else {
                player.getInventory().offhand.set(0, ItemStack.EMPTY);
            }
            return;
        }
        handStack.setCount(handStack.getCount() - 1);
    }

    private static void syncPlayerInventorySlot(ServerPlayer player, InteractionHand hand) {
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
        ItemStack stack = hand == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, stack.copy()));
    }

    private static void summonProposalPunishLightning(ServerLevel level, net.minecraft.world.entity.player.Player player, boolean startPunish) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return;
        }
        lightning.moveTo(player.getX(), player.getY(), player.getZ());
        lightning.setVisualOnly(true);
        if (startPunish && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            lightning.setCause(serverPlayer);
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, PROPOSAL_PUNISH_NAUSEA_TICKS, 0));
        }
        level.addFreshEntity(lightning);
        if (startPunish) {
            applyProposalFreezeEffect(player);
            PROPOSAL_PUNISH_ACTIVE.put(player.getUUID(), 0);
        }
    }

    private static void applyProposalFreezeEffect(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return;
        }
        /*
         * 这里复用原版细雪冻结条，让惩罚有“从骨头里冷一下”的反馈。
         * 但不要超过冻伤阈值，避免原版冻结伤害和求婚惩罚伤害叠加造成不可控死亡。
         */
        int freezeLimit = Math.max(1, player.getTicksRequiredToFreeze() - 1);
        int targetFrozenTicks = Math.min(freezeLimit, player.getTicksFrozen() + PROPOSAL_PUNISH_FREEZE_EXTRA_TICKS);
        player.setTicksFrozen(Math.max(player.getTicksFrozen(), targetFrozenTicks));
    }

    private record PendingTransferRequest(UUID ownerUuid, UUID proposerUuid, long expireTick) {
    }

    private enum FlowerGift {
        RED("red", 1 << 0, "message.maidmarriage.flower.color.red", "dialogue.maidmarriage.flower.red", "dialogue.maidmarriage.child.flower.red",
                Items.POPPY, Items.RED_TULIP, Items.ROSE_BUSH),
        YELLOW("yellow", 1 << 1, "message.maidmarriage.flower.color.yellow", "dialogue.maidmarriage.flower.yellow", "dialogue.maidmarriage.child.flower.yellow",
                Items.DANDELION, Items.SUNFLOWER),
        BLUE("blue", 1 << 2, "message.maidmarriage.flower.color.blue", "dialogue.maidmarriage.flower.blue", "dialogue.maidmarriage.child.flower.blue",
                Items.BLUE_ORCHID, Items.CORNFLOWER),
        WHITE("white", 1 << 3, "message.maidmarriage.flower.color.white", "dialogue.maidmarriage.flower.white", "dialogue.maidmarriage.child.flower.white",
                Items.AZURE_BLUET, Items.WHITE_TULIP, Items.OXEYE_DAISY, Items.LILY_OF_THE_VALLEY),
        ORANGE("orange", 1 << 4, "message.maidmarriage.flower.color.orange", "dialogue.maidmarriage.flower.orange", "dialogue.maidmarriage.child.flower.orange",
                Items.ORANGE_TULIP, Items.TORCHFLOWER),
        PINK("pink", 1 << 5, "message.maidmarriage.flower.color.pink", "dialogue.maidmarriage.flower.pink", "dialogue.maidmarriage.child.flower.pink",
                Items.PINK_TULIP, Items.PEONY),
        PURPLE("purple", 1 << 6, "message.maidmarriage.flower.color.purple", "dialogue.maidmarriage.flower.purple", "dialogue.maidmarriage.child.flower.purple",
                Items.ALLIUM, Items.LILAC),
        BLACK("black", 1 << 7, "message.maidmarriage.flower.color.black", "dialogue.maidmarriage.flower.black", "dialogue.maidmarriage.child.flower.black",
                Items.WITHER_ROSE);

        private final String advancementSuffix;
        private final int bit;
        private final String colorNameKey;
        private final String dialogueKey;
        private final String childDialogueKey;
        private final List<Item> flowers;

        FlowerGift(String advancementSuffix, int bit, String colorNameKey, String dialogueKey, String childDialogueKey, Item... flowers) {
            this.advancementSuffix = advancementSuffix;
            this.bit = bit;
            this.colorNameKey = colorNameKey;
            this.dialogueKey = dialogueKey;
            this.childDialogueKey = childDialogueKey;
            this.flowers = Arrays.asList(flowers);
        }

        private static FlowerGift from(ItemStack stack) {
            for (FlowerGift gift : values()) {
                if (gift.flowers.stream().anyMatch(stack::is)) {
                    return gift;
                }
            }
            return null;
        }
    }
}
