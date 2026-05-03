package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.example.maidmarriage.rhythm.RomanceRhythmSync;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 同眠与生育流程管理：处理剧情、心情循环、怀孕与分娩。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class RomanceSleepManager {
    private static final double SHARED_TWINS_CHANCE = 0.02D;
    private static final double RHYTHM_MAX_CONCEPTION_CHANCE = 0.60D;
    private static final int SCENE_INTERVAL_TICKS = 24;
    private static final int MIN_VALID_SLEEP_TICKS = 20;
    private static final int SINGLE_LINE_BUBBLE_TICKS = 100;
    private static final int MULTI_LINE_BUBBLE_TICKS = 60;
    private static final int MULTI_LINE_GAP_TICKS = 30;
    private static final int MULTI_LINE_STEP_TICKS = MULTI_LINE_BUBBLE_TICKS + MULTI_LINE_GAP_TICKS;
    private static final long LONGING_SADDLE_COOLDOWN_TICKS = 200L;
    private static final double LONGING_HEART_RANGE_SQR = 25.0D;
    private static final String TAG_ROMANCE_COUNT = "maidmarriage_romance_count";
    private static final String TAG_LONGING_NEXT_TALK_TICK = "maidmarriage_longing_next_talk_tick";
    private static final String TAG_LONGING_LINE_INDEX = "maidmarriage_longing_line_index";
    private static final String TAG_LONGING_SADDLE_NEXT_TALK_TICK = "maidmarriage_longing_saddle_next_talk_tick";
    private static final String TAG_ROMANCE_COOLDOWN_UNTIL_TICK = "maidmarriage_romance_cooldown_until_tick";
    private static final String TAG_PENDING_WAKE_DIALOGUE = "maidmarriage_pending_wake_dialogue";
    private static final String TAG_PENDING_WAKE_DIALOGUE_DUE_TICK = "maidmarriage_pending_wake_dialogue_due_tick";
    private static final String TAG_REVIVE_DIALOGUE_NEXT_TICK = "maidmarriage_revive_dialogue_next_tick";
    private static final String TAG_REVIVE_DIALOGUE_INDEX = "maidmarriage_revive_dialogue_index";
    private static final String TAG_PLAYER_MAID_ADDRESSING = "maidmarriage_player_maid_addressing";
    private static final String TAG_PLAYER_CHILD_MAID_ADDRESSING = "maidmarriage_player_child_maid_addressing";
    private static final String TAG_PLAYER_HAREM_MODE = "maidmarriage_player_harem_mode";
    private static final String TAG_PLAYER_PRIMARY_MAID = "maidmarriage_primary_maid";
    private static final int POSTPARTUM_RECOVERY_MIN_DAYS = 2;
    private static final int POSTPARTUM_RECOVERY_MAX_DAYS = 3;
    private static final long ROMANCE_COOLDOWN_TICKS = 15L * 20L;
    private static final int ROMANCE_FAVORABILITY_GAIN = 1;
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;
    /**
     * 第一次同寝专属文案，强调“初次体验”的仪式感。
     */
    private static final List<String> FIRST_SCENE_LINES = List.of(
            "scene.maidmarriage.sleep.1",
            "scene.maidmarriage.sleep.2",
            "scene.maidmarriage.sleep.3",
            "scene.maidmarriage.sleep.4"
    );
    /**
     * 非首次时随机抽取的剧情组：温柔/热烈/日常甜蜜三种风格。
     */
    private static final List<List<String>> RANDOM_SCENE_VARIANTS = List.of(
            List.of(
                    "scene.maidmarriage.sleep.gentle.1",
                    "scene.maidmarriage.sleep.gentle.2",
                    "scene.maidmarriage.sleep.gentle.3",
                    "scene.maidmarriage.sleep.gentle.4"
            ),
            List.of(
                    "scene.maidmarriage.sleep.passion.1",
                    "scene.maidmarriage.sleep.passion.2",
                    "scene.maidmarriage.sleep.passion.3",
                    "scene.maidmarriage.sleep.passion.4"
            ),
            List.of(
                    "scene.maidmarriage.sleep.sweet.1",
                    "scene.maidmarriage.sleep.sweet.2",
                    "scene.maidmarriage.sleep.sweet.3",
                    "scene.maidmarriage.sleep.sweet.4"
            )
    );
    private static final List<String> PROPOSAL_LINES = List.of(
            "dialogue.maidmarriage.proposal.1",
            "dialogue.maidmarriage.proposal.2",
            "dialogue.maidmarriage.proposal.3"
    );
    private static final List<String> TRANSFER_FAREWELL_LINES = List.of(
            "dialogue.maidmarriage.transfer_farewell.1",
            "dialogue.maidmarriage.transfer_farewell.2",
            "dialogue.maidmarriage.transfer_farewell.3",
            "dialogue.maidmarriage.transfer_farewell.4"
    );
    private static final List<String> LONGING_DATING_LINES = List.of(
            "dialogue.maidmarriage.longing.dating.1",
            "dialogue.maidmarriage.longing.dating.2",
            "dialogue.maidmarriage.longing.dating.3",
            "dialogue.maidmarriage.longing.dating.4",
            "dialogue.maidmarriage.longing.dating.5"
    );
    private static final List<String> LONGING_MARRIAGE_LINES = List.of(
            "dialogue.maidmarriage.longing.marriage.1",
            "dialogue.maidmarriage.longing.marriage.2",
            "dialogue.maidmarriage.longing.marriage.3",
            "dialogue.maidmarriage.longing.marriage.4",
            "dialogue.maidmarriage.longing.marriage.5"
    );
    private static final List<String> LONGING_LOW_MOOD_LINES = List.of(
            "dialogue.maidmarriage.longing.low_mood.1",
            "dialogue.maidmarriage.longing.low_mood.2",
            "dialogue.maidmarriage.longing.low_mood.3",
            "dialogue.maidmarriage.longing.low_mood.4",
            "dialogue.maidmarriage.longing.low_mood.5"
    );
    private static final List<String> REVIVE_CHILD_LINES = List.of(
            "dialogue.maidmarriage.revive.child1",
            "dialogue.maidmarriage.revive.child2"
    );
    private static final Map<UUID, RomanceSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ProposalDialogueSession> PROPOSAL_DIALOGUES = new ConcurrentHashMap<>();

    private RomanceSleepManager() {
    }

    public static boolean tryStartRomanceRhythmThenSleep(ServerPlayer player, EntityMaid maid) {
        RomancePrerequisite prerequisite = checkRomancePrerequisite(player, maid);
        if (prerequisite == null) {
            return false;
        }
        RomanceRhythmSync.requestDecision(player, maid, player.level().getGameTime(), prerequisite.playerBedPos());
        return true;
    }

    public static boolean tryStartRomanceSleep(ServerPlayer player, EntityMaid maid) {
        RomancePrerequisite prerequisite = checkRomancePrerequisite(player, maid);
        if (prerequisite == null) {
            return false;
        }
        return beginSleepSession(player, maid, prerequisite.playerBedPos(), false);
    }

    private static RomancePrerequisite checkRomancePrerequisite(ServerPlayer player, EntityMaid maid) {
        if (!maid.isSleeping()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.need_sleeping_maid", maid.getDisplayName()));
            return null;
        }

        Optional<BlockPos> maidBedPos = maid.getSleepingPos();
        if (maidBedPos.isEmpty()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.cannot_find_maid_bed", maid.getDisplayName()));
            return null;
        }

        Optional<BlockPos> playerBedPos = findAdjacentPlayerBed(player.serverLevel(), player.blockPosition(), maidBedPos.get());
        if (playerBedPos.isEmpty()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.need_adjacent_bed"));
            return null;
        }

        PregnancyData pregnancy = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        long now = maid.level().getGameTime();
        long cooldownUntil = maid.getPersistentData().getLong(TAG_ROMANCE_COOLDOWN_UNTIL_TICK);
        if (cooldownUntil > now) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.exhausted_today", maid.getDisplayName()));
            return null;
        }
        if (pregnancy.pregnant()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.pregnant_blocked", maid.getDisplayName()));
            return null;
        }
        if (pregnancy.isInPostpartumRecovery(maid.level().getGameTime())) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.postpartum_blocked", maid.getDisplayName()));
            return null;
        }
        return new RomancePrerequisite(playerBedPos.get(), pregnancy);
    }

    private static boolean beginSleepSession(ServerPlayer player, EntityMaid maid, BlockPos playerBedPos, boolean conceptionSettledAfterRhythm) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, maid.position().add(0, 0.3, 0));
        var sleepResult = player.startSleepInBed(playerBedPos);
        if (sleepResult.left().isPresent()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.sleep_failed"));
            return false;
        }

        // Refresh romance day as soon as sleeping starts so longing hearts are cleared
        // even if the scene is interrupted before full completion.
        long now = maid.level().getGameTime();
        PregnancyData latest = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, latest.markRomance(now));
        maid.getPersistentData().putLong(TAG_ROMANCE_COOLDOWN_UNTIL_TICK, now + ROMANCE_COOLDOWN_TICKS);
        resetLongingLoopDialogue(maid);

        RomanceSession session = new RomanceSession(maid.getUUID(), pickSceneLines(player, maid), conceptionSettledAfterRhythm);
        SESSIONS.put(player.getUUID(), session);
        sendNextSceneLine(player, session);
        return true;
    }

    public static void onRhythmPanelResult(ServerPlayer player, UUID maidUuid, float rhythmScore) {
        RomanceRhythmSync.PendingDecision pending = RomanceRhythmSync.consume(player.getUUID());
        if (pending == null || !pending.maidUuid().equals(maidUuid)) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(maidUuid);
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.mother_missing", Component.translatable("entity.maidmarriage.maid_child")));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.need_owner", maid.getDisplayName()));
            return;
        }

        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        PregnancyAttemptResult attempt = settleConceptionImmediatelyAfterRhythm(player, maid, current, rhythmScore);
        beginSleepSession(player, maid, pending.playerBedPos(), true);
        ensureConceptionPersistence(player, maid, attempt);
    }

    public static boolean forceBirthNow(ServerPlayer player, EntityMaid maid) {
        PregnancyData pregnancy = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        if (!pregnancy.pregnant()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.birth_tool.not_pregnant", maid.getDisplayName()));
            return false;
        }
        if (!pregnancy.isPregnantWith(player.getUUID())) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.pregnant_with_other", maid.getDisplayName()));
            return false;
        }
        int childCount = pregnancy.twinsPregnancy() ? 2 : 1;
        if (!spawnChildren(player, maid, childCount)) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.spawn_fail", maid.getDisplayName()));
            return false;
        }
        long now = maid.level().getGameTime();
        long recoveryTicks = createPostpartumRecoveryTicks(maid);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, pregnancy.completeBirth(now, recoveryTicks));
        applyPostpartumRecoveryEffects(maid, recoveryTicks);
        speakSingleLine(maid, "dialogue.maidmarriage.after_birth");
        player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.birth_success", maid.getDisplayName()));
        ModAdvancements.grantChildbirth(player);
        return true;
    }

    public static void startProposalDialogue(ServerPlayer player, EntityMaid maid) {
        ProposalDialogueSession session = new ProposalDialogueSession(
                maid.getUUID(),
                player.serverLevel().getGameTime(),
                PROPOSAL_LINES);
        PROPOSAL_DIALOGUES.put(player.getUUID(), session);
    }

    /**
     * 启动“监护移交”离别台词。
     * 该流程复用求婚台词的逐句气泡调度器，按固定间隔依次播放四句离别文案。
     *
     * @param player 原主人（通常为父亲玩家）
     * @param maid   正在移交的女仆
     */
    public static void startTransferFarewellDialogue(ServerPlayer player, EntityMaid maid) {
        ProposalDialogueSession session = new ProposalDialogueSession(
                maid.getUUID(),
                player.serverLevel().getGameTime(),
                TRANSFER_FAREWELL_LINES);
        PROPOSAL_DIALOGUES.put(player.getUUID(), session);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        tickProposalDialogue(player);
        trySettleRhythmTimeout(player);

        RomanceSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }

        if (!player.isSleeping()) {
            finishSession(player, session);
            SESSIONS.remove(player.getUUID());
            return;
        }

        session.sleepTicks++;
        if (session.sleepTicks % SCENE_INTERVAL_TICKS == 0) {
            sendNextSceneLine(player, session);
        }
    }

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide() || maid.tickCount % 20 != 0) {
            return;
        }
        tickTemporaryReviveDialogue(maid);
        flushPendingWakeDialogue(maid);
        tryAutoBirthOnTick(maid);
        tickPostpartumRecovery(maid);
        if (!MaidMoodManager.isLongingForInteraction(maid)) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(maid.getOwner() instanceof ServerPlayer owner) || owner.level() != maid.level()) {
            return;
        }
        double distanceSqr = maid.distanceToSqr(owner);
        boolean saddleTriggered = tryTriggerSaddleDialogue(level, maid, owner);

        if (distanceSqr <= LONGING_HEART_RANGE_SQR) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.0D), maid.getZ(),
                    2, 0.25D, 0.15D, 0.25D, 0.01D);
            if (!saddleTriggered) {
                tryTriggerLongingLoopDialogue(level, maid);
            }
        } else {
            resetLongingLoopDialogue(maid);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        SESSIONS.remove(player.getUUID());
        PROPOSAL_DIALOGUES.remove(player.getUUID());
        RomanceRhythmSync.clear(player.getUUID());
    }

    @SubscribeEvent
    public static void onMaidDeath(MaidDeathEvent event) {
        EntityMaid maid = event.getMaid();
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        maid.removeEffect(MobEffects.WEAKNESS);
        maid.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private static void finishSession(ServerPlayer player, RomanceSession session) {
        if (!session.sceneFinished) {
            if (player.serverLevel().isDay() || session.sleepTicks >= MIN_VALID_SLEEP_TICKS) {
                session.sceneFinished = true;
            } else {
            Entity maidEntity = player.serverLevel().getEntity(session.maidUuid);
            if (maidEntity instanceof EntityMaid maid && maid.isAlive() && maid.isOwnedBy(player)) {
                long now = maid.level().getGameTime();
                PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
                maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, current.markRomance(now));
                resetLongingLoopDialogue(maid);
            }
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.scene_interrupted"));
            return;
            }
        }

        Entity maidEntity = player.serverLevel().getEntity(session.maidUuid);
        if (!(maidEntity instanceof EntityMaid maid) || !maid.isAlive()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.mother_missing", Component.translatable("entity.maidmarriage.maid_child")));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.need_owner", maid.getDisplayName()));
            return;
        }
        int romanceCount = increaseRomanceCount(player);
        if (romanceCount == 1) {
            ModAdvancements.grantFirstRomance(player);
        }
        if (romanceCount >= 10) {
            ModAdvancements.grantRomanceTen(player);
        }

        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        long gameTime = maid.level().getGameTime();
        PregnancyData updated = current.markRomance(gameTime);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated);
        MaidMoodManager.applyLimitedInteractionMoodGain(maid, MaidMoodManager.EVENT_ROMANCE);
        MaidMoodManager.applyInteractionFavorabilityGain(maid, ROMANCE_FAVORABILITY_GAIN, FAVORABILITY_CAP);
        MaidMoodManager.markMeaningfulInteraction(maid);
        speakSingleLine(maid, "dialogue.maidmarriage.after_romance");

        if (session.conceptionSettledAfterRhythm) {
            return;
        }

        if (!updated.pregnant()) {
            PregnancyAttemptResult attempt = settleConceptionAttempt(player, maid, updated, false);
            if (!attempt.conceived()) {
                player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.no_conception", maid.getDisplayName()));
            }
            return;
        }

        if (!updated.isPregnantWith(player.getUUID())) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.pregnant_with_other", maid.getDisplayName()));
            return;
        }
        if (!isBirthDue(updated, gameTime)) {
            long needTicks = Math.max(1L, ModConfigs.pregnancyBirthDays()) * 24000L;
            long passedTicks = Math.max(0L, gameTime - updated.conceivedGameTime());
            long leftTicks = Math.max(0L, needTicks - passedTicks);
            long leftDays = (long) Math.ceil(leftTicks / 24000.0D);
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.birth_not_due_days", maid.getDisplayName(), leftDays));
            return;
        }

        int childCount = updated.twinsPregnancy() ? 2 : 1;
        if (!spawnChildren(player, maid, childCount)) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.spawn_fail", maid.getDisplayName()));
            return;
        }
        long recoveryTicks = createPostpartumRecoveryTicks(maid);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, updated.completeBirth(gameTime, recoveryTicks));
        applyPostpartumRecoveryEffects(maid, recoveryTicks);
        player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.birth_success", maid.getDisplayName()));
        ModAdvancements.grantChildbirth(player);
    }

    private static boolean spawnChildren(ServerPlayer player, EntityMaid mother, int childCount) {
        if (!(mother.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        int safeChildCount = Math.max(1, childCount);
        MaidChildEntity.markAsAdult(mother);
        Component plannedName = readPlannedChildName(player);

        double spawnX = mother.getX();
        double spawnY = mother.getY() + mother.getBbHeight() + 0.15D;
        double spawnZ = mother.getZ();
        boolean success = true;
        int spawned = 0;
        for (int index = 0; index < safeChildCount; index++) {
            MaidChildEntity child = ModEntities.MAID_CHILD.get().create(serverLevel);
            if (child == null) {
                success = false;
                break;
            }

            double sideOffset = safeChildCount == 1 ? 0.0D : (index == 0 ? -0.35D : 0.35D);
            child.moveTo(spawnX + sideOffset, spawnY, spawnZ, player.getYRot(), 0);
            child.tame(player);
            child.setCustomName(plannedName.copy());
            child.setParents(mother.getUUID(), player.getUUID());
            child.applyBornMaidTraits();
            child.prepareNewbornInfant(serverLevel.getGameTime());
            child.inheritModelFromMother(mother);

            if (!serverLevel.addFreshEntity(child)) {
                success = false;
                break;
            }
            /*
             * 新生小女仆出生后默认切到“不跟随”模式。
             *
             * 这里把 home mode 的初始化放到实体真正进世界之后再做一次，
             * 避免被驯服初始化或出生瞬间的默认 AI 状态覆盖掉。
             *
             * 对 TLM 来说：
             * - homeMode = true  -> 不跟随，记住当前位置；
             * - homeMode = false -> 跟随主人。
             */
            child.getSchedulePos().setHomeModeEnable(child, child.blockPosition());
            child.setHomeModeEnable(true);
            if (index == 0) {
                child.startRiding(mother, true);
            }
            child.syncChildStateToClient();
            spawned++;
        }

        if (spawned > 0) {
            serverLevel.sendParticles(ParticleTypes.HEART, spawnX, spawnY + 0.75, spawnZ, 16 + spawned * 4, 0.45, 0.25, 0.45, 0.02);
            serverLevel.playSound(null, mother.blockPosition(), net.minecraft.sounds.SoundEvents.CHICKEN_EGG,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.1F);
        }
        return success && spawned == safeChildCount;
    }

    private static void tryAutoBirthOnTick(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        PregnancyData pregnancy = maid.getData(ModTaskData.PREGNANCY_DATA);
        if (pregnancy == null || !pregnancy.pregnant()) {
            return;
        }
        if (!isBirthDue(pregnancy, level.getGameTime())) {
            return;
        }

        ServerPlayer father = pregnancy.father()
                .map(uuid -> level.getServer().getPlayerList().getPlayer(uuid))
                .orElse(null);
        if (father == null && maid.getOwner() instanceof ServerPlayer owner && owner.level() == maid.level()) {
            father = owner;
        }
        if (father == null) {
            return;
        }

        int childCount = pregnancy.twinsPregnancy() ? 2 : 1;
        if (!spawnChildren(father, maid, childCount)) {
            return;
        }
        long recoveryTicks = createPostpartumRecoveryTicks(maid);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, pregnancy.completeBirth(level.getGameTime(), recoveryTicks));
        applyPostpartumRecoveryEffects(maid, recoveryTicks);
        speakSingleLine(maid, "dialogue.maidmarriage.after_birth");
        father.sendSystemMessage(scriptForPlayer(father, "message.maidmarriage.breed.birth_success", maid.getDisplayName()));
        ModAdvancements.grantChildbirth(father);
    }

    private static void tickPostpartumRecovery(EntityMaid maid) {
        PregnancyData pregnancy = maid.getData(ModTaskData.PREGNANCY_DATA);
        if (pregnancy == null) {
            return;
        }
        long now = maid.level().getGameTime();
        if (!ModConfigs.postpartumRecoveryEnabled()) {
            if (pregnancy.postpartumEndGameTime() > 0L) {
                maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, pregnancy.clearPostpartum());
            }
            return;
        }
        if (pregnancy.isInPostpartumRecovery(now)) {
            return;
        }
        if (pregnancy.postpartumEndGameTime() > 0L) {
            maid.removeEffect(MobEffects.WEAKNESS);
            maid.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, pregnancy.clearPostpartum());
        }
    }

    private static void applyPostpartumRecoveryEffects(EntityMaid maid, long recoveryTicks) {
        if (recoveryTicks <= 0L || !ModConfigs.postpartumRecoveryEnabled()) {
            return;
        }
        int duration = (int) Math.min(Integer.MAX_VALUE, recoveryTicks);
        // One-shot long debuffs, hidden particles/icons to reduce render/network overhead.
        maid.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, false, false));
        maid.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0, false, false, false));
    }

    private static long createPostpartumRecoveryTicks(EntityMaid maid) {
        if (!ModConfigs.postpartumRecoveryEnabled()) {
            return 0L;
        }
        int days = POSTPARTUM_RECOVERY_MIN_DAYS
                + maid.getRandom().nextInt(POSTPARTUM_RECOVERY_MAX_DAYS - POSTPARTUM_RECOVERY_MIN_DAYS + 1);
        return days * 24000L;
    }

    private static Component readPlannedChildName(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.NAME_TAG) && offhand.hasCustomHoverName()) {
            Component name = offhand.getHoverName().copy();
            if (!player.getAbilities().instabuild) {
                offhand.shrink(1);
            }
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.use_name_tag", name));
            return name;
        }
        return Component.translatable("entity.maidmarriage.maid_child");
    }

    private static int increaseRomanceCount(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int count = data.getInt(TAG_ROMANCE_COUNT) + 1;
        data.putInt(TAG_ROMANCE_COUNT, count);
        return count;
    }

    private static boolean isBirthDue(PregnancyData data, long gameTime) {
        if (!data.pregnant()) {
            return false;
        }
        long needTicks = Math.max(1L, ModConfigs.pregnancyBirthDays()) * 24000L;
        long passedTicks = Math.max(0L, gameTime - data.conceivedGameTime());
        return passedTicks >= needTicks;
    }

    private static void tryTriggerLongingLoopDialogue(ServerLevel level, EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        long now = level.getGameTime();
        long nextTalkTick = data.getLong(TAG_LONGING_NEXT_TALK_TICK);
        if (nextTalkTick > now) {
            return;
        }
        List<String> lines = resolveLongingLoopLines(maid);
        int index = Math.floorMod(data.getInt(TAG_LONGING_LINE_INDEX), lines.size());
        speakMultiLine(maid, lines.get(index));
        data.putInt(TAG_LONGING_LINE_INDEX, (index + 1) % lines.size());
        data.putLong(TAG_LONGING_NEXT_TALK_TICK, now + MULTI_LINE_STEP_TICKS);
    }

    private static List<String> resolveLongingLoopLines(EntityMaid maid) {
        MaidMoodData.MoodState mood = MaidMoodManager.state(maid);
        if (mood == MaidMoodData.MoodState.DEPRESSED || mood == MaidMoodData.MoodState.GENERAL) {
            return LONGING_LOW_MOOD_LINES;
        }
        return MaidRelationshipManager.resolveStage(maid) == RelationStage.MARRIAGE
                ? LONGING_MARRIAGE_LINES
                : LONGING_DATING_LINES;
    }

    private static void resetLongingLoopDialogue(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.remove(TAG_LONGING_NEXT_TALK_TICK);
        data.remove(TAG_LONGING_LINE_INDEX);
    }

    private static boolean tryTriggerSaddleDialogue(ServerLevel level, EntityMaid maid, ServerPlayer owner) {
        if (!maid.isPassenger() || maid.getVehicle() != owner) {
            return false;
        }
        long now = level.getGameTime();
        CompoundTag data = maid.getPersistentData();
        if (data.getLong(TAG_LONGING_SADDLE_NEXT_TALK_TICK) > now) {
            return false;
        }
        speakSingleLine(maid, "dialogue.maidmarriage.longing_saddle");
        level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.0D), maid.getZ(),
                6, 0.3D, 0.15D, 0.3D, 0.01D);
        data.putLong(TAG_LONGING_SADDLE_NEXT_TALK_TICK, now + LONGING_SADDLE_COOLDOWN_TICKS);
        return true;
    }

    private static void tickProposalDialogue(ServerPlayer player) {
        ProposalDialogueSession session = PROPOSAL_DIALOGUES.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (player.serverLevel().getGameTime() < session.nextSpeakTick) {
            return;
        }
        Entity maidEntity = player.serverLevel().getEntity(session.maidUuid);
        if (!(maidEntity instanceof EntityMaid maid) || !maid.isAlive()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
            return;
        }
        if (session.lineIndex >= session.lines.size()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
            return;
        }

        speakMultiLine(maid, session.lines.get(session.lineIndex));
        session.lineIndex++;
        session.nextSpeakTick = player.serverLevel().getGameTime() + MULTI_LINE_STEP_TICKS;

        if (session.lineIndex >= session.lines.size()) {
            PROPOSAL_DIALOGUES.remove(player.getUUID());
        }
    }

    private static void sendNextSceneLine(ServerPlayer player, RomanceSession session) {
        if (session.lineIndex >= session.sceneLines.size()) {
            session.sceneFinished = true;
            return;
        }
        player.displayClientMessage(scriptForPlayer(player, session.sceneLines.get(session.lineIndex)), true);
        session.lineIndex++;
        if (session.lineIndex >= session.sceneLines.size()) {
            session.sceneFinished = true;
        }
    }

    /**
     * 根据玩家是否“首次同寝”选择剧情：
     * - 首次：固定播放首次剧情；
     * - 非首次：在三组随机剧情中抽一组。
     */
    private static List<String> pickSceneLines(ServerPlayer player, EntityMaid maid) {
        if (player.getPersistentData().getInt(TAG_ROMANCE_COUNT) <= 0) {
            return FIRST_SCENE_LINES;
        }
        return RANDOM_SCENE_VARIANTS.get(maid.getRandom().nextInt(RANDOM_SCENE_VARIANTS.size()));
    }

    private static Optional<BlockPos> findAdjacentPlayerBed(ServerLevel level, BlockPos playerPos, BlockPos maidBedPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos bedPos = maidBedPos.relative(direction);
            BlockState state = level.getBlockState(bedPos);
            if (isPlayerBed(state) && playerPos.distManhattan(bedPos) <= 3) {
                return Optional.of(bedPos);
            }
        }
        return Optional.empty();
    }

    private static boolean isPlayerBed(BlockState state) {
        return state.getBlock() instanceof BedBlock && !(state.getBlock() instanceof BlockMaidBed);
    }

    public static void speakSingleLine(EntityMaid maid, String langKey) {
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(
                SINGLE_LINE_BUBBLE_TICKS,
                scriptForMaid(maid, langKey, resolveDialogueArgs(maid)),
                IChatBubbleData.TYPE_2,
                IChatBubbleData.DEFAULT_PRIORITY));
    }

    /**
     * 单句台词同时同步到头顶气泡与聊天框。
     *
     * <p>拥抱、摸头、亲吻这一类近距离交互中，
     * 玩家注意力经常在第一人称镜头或角色脸部上，
     * 仅靠气泡有时会被遮挡、错过或者来不及看清。
     * 因此这里额外把同一句台词同步到拥有者聊天框，
     * 让反馈既保留“角色开口说话”的感觉，也不会漏掉文本。
     */
    public static void speakSingleLineWithChat(EntityMaid maid, String langKey) {
        Component dialogue = scriptForMaid(maid, langKey, resolveDialogueArgs(maid));
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(
                SINGLE_LINE_BUBBLE_TICKS,
                dialogue,
                IChatBubbleData.TYPE_2,
                IChatBubbleData.DEFAULT_PRIORITY));
        sendDialogueToOwnerChat(maid, dialogue);
    }

    private static void speakMultiLine(EntityMaid maid, String langKey) {
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(
                MULTI_LINE_BUBBLE_TICKS,
                scriptForMaid(maid, langKey, resolveDialogueArgs(maid)),
                IChatBubbleData.TYPE_2,
                IChatBubbleData.DEFAULT_PRIORITY));
    }

    private static Object[] resolveDialogueArgs(EntityMaid maid) {
        Component maidName = maid.getDisplayName();
        String ownerRawName = maid.getOwner() != null
                ? maid.getOwner().getName().getString()
                : script("name.maidmarriage.master_default").getString();
        String ownerName = resolveAddressingFromOwner(maid, ownerRawName);
        return new Object[]{maidName, ownerName};
    }

    private static Component scriptForPlayer(@Nullable Player player, String key, Object... args) {
        return DialogueScriptManager.componentForPlayer(player, key, args);
    }

    private static Component scriptForMaid(EntityMaid maid, String key, Object... args) {
        return DialogueScriptManager.componentForMaid(maid, key, args);
    }

    private static Component script(String key, Object... args) {
        return DialogueScriptManager.component(key, args);
    }

    private static String resolveAddressingFromOwner(EntityMaid maid, String fallbackPlayerName) {
        Entity owner = maid.getOwner();
        if (owner instanceof ServerPlayer serverPlayer) {
            CompoundTag data = serverPlayer.getPersistentData();
            String tagKey = MaidChildEntity.shouldStayChild(maid)
                    ? TAG_PLAYER_CHILD_MAID_ADDRESSING
                    : TAG_PLAYER_MAID_ADDRESSING;
            if (data.contains(tagKey)) {
                String custom = data.getString(tagKey);
                if (!custom.isBlank()) {
                    return custom;
                }
            }
        }
        return MaidChildEntity.shouldStayChild(maid)
                ? ModConfigs.resolveChildMaidAddressing(fallbackPlayerName)
                : ModConfigs.resolveMaidAddressing(fallbackPlayerName);
    }

    /**
     * 把女仆台词投递到拥有者聊天框。
     *
     * <p>这里只发给拥有者本人，不做范围广播，
     * 这样既能保证交互私密感，也能避免多人环境里刷屏。
     */
    private static void sendDialogueToOwnerChat(EntityMaid maid, Component dialogue) {
        Entity owner = maid.getOwner();
        if (owner instanceof ServerPlayer serverPlayer) {
            Component chatLine = Component.literal("<")
                    .append(maid.getDisplayName().copy())
                    .append("> ")
                    .append(dialogue.copy());
            serverPlayer.sendSystemMessage(chatLine);
        }
    }

    public static void updatePlayerMaidAddressing(ServerPlayer player, String addressing) {
        String sanitized = addressing == null ? "" : addressing.trim();
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        player.getPersistentData().putString(TAG_PLAYER_MAID_ADDRESSING, sanitized);
    }

    public static void updatePlayerMaidAddressing(ServerPlayer player, String addressing, String childAddressing) {
        updatePlayerMaidAddressing(player, addressing);
        String sanitizedChild = childAddressing == null ? "" : childAddressing.trim();
        if (sanitizedChild.length() > 32) {
            sanitizedChild = sanitizedChild.substring(0, 32);
        }
        player.getPersistentData().putString(TAG_PLAYER_CHILD_MAID_ADDRESSING, sanitizedChild);
    }

    public static void updatePlayerRuntimeSettings(ServerPlayer player, boolean haremMode, int requiredFavorability, double pregnancyChance) {
        updatePlayerHaremMode(player, haremMode);
    }

    public static void updatePlayerHaremMode(ServerPlayer player, boolean haremMode) {
        if (player == null) {
            return;
        }
        // 后宫模式是玩家自己的玩法偏好：客户端设置同步到服务端后，只影响该玩家的表白/结婚判定。
        player.getPersistentData().putBoolean(TAG_PLAYER_HAREM_MODE, haremMode);
        if (!haremMode) {
            rememberLoadedPrimaryMaid(player);
        }
    }

    public static boolean resolveHaremMode(Player player) {
        if (player != null && player.getPersistentData().contains(TAG_PLAYER_HAREM_MODE)) {
            return player.getPersistentData().getBoolean(TAG_PLAYER_HAREM_MODE);
        }
        return ModConfigs.haremMode();
    }

    public static int resolveRequiredFavorability(Player player) {
        return RelationshipThresholds.MARRIAGE_UNLOCK;
    }

    public static double resolvePregnancyChance(Player player) {
        return ModConfigs.pregnancyChance();
    }

    private static void rememberLoadedPrimaryMaid(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        if (tag.hasUUID(TAG_PLAYER_PRIMARY_MAID) || ModTaskData.MARRIAGE_DATA == null) {
            return;
        }
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
                continue;
            }
            MarriageData data = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
            if (data != null && data.isMarriedWith(player.getUUID())) {
                tag.putUUID(TAG_PLAYER_PRIMARY_MAID, maid.getUUID());
                return;
            }
        }
    }

    private static final class RomanceSession {
        private final UUID maidUuid;
        private final List<String> sceneLines;
        private final boolean conceptionSettledAfterRhythm;
        private int sleepTicks = 0;
        private int lineIndex = 0;
        private boolean sceneFinished = false;

        private RomanceSession(UUID maidUuid, List<String> sceneLines, boolean conceptionSettledAfterRhythm) {
            this.maidUuid = maidUuid;
            this.sceneLines = sceneLines;
            this.conceptionSettledAfterRhythm = conceptionSettledAfterRhythm;
        }
    }

    private static final class ProposalDialogueSession {
        private final UUID maidUuid;
        private final List<String> lines;
        private long nextSpeakTick;
        private int lineIndex = 0;

        private ProposalDialogueSession(UUID maidUuid, long nextSpeakTick, List<String> lines) {
            this.maidUuid = maidUuid;
            this.nextSpeakTick = nextSpeakTick;
            this.lines = lines;
        }
    }

    private record RomancePrerequisite(BlockPos playerBedPos, PregnancyData pregnancy) {
    }

    private record PregnancyAttemptResult(boolean conceived, boolean twins, PregnancyData data) {
        private static PregnancyAttemptResult noChange(PregnancyData data) {
            return new PregnancyAttemptResult(false, false, data);
        }
    }

    /**
     * 结算调试结果，用于把本次真实结算的关键值回显到聊天栏。
     */
    public record DebugConceptionResult(double chance, boolean conceived, boolean twins, PregnancyData data) {
    }

    private static void trySettleRhythmTimeout(ServerPlayer player) {
        RomanceRhythmSync.PendingDecision pending = RomanceRhythmSync.peek(player.getUUID());
        if (pending == null || !RomanceRhythmSync.isTimedOut(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        pending = RomanceRhythmSync.consume(player.getUUID());
        if (pending == null) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(pending.maidUuid());
        if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !maid.isOwnedBy(player)) {
            return;
        }
        PregnancyData current = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        PregnancyAttemptResult attempt = settleConceptionImmediatelyAfterRhythm(player, maid, current, 0.0F);
        beginSleepSession(player, maid, pending.playerBedPos(), true);
        ensureConceptionPersistence(player, maid, attempt);
    }

    private static PregnancyAttemptResult settleConceptionImmediatelyAfterRhythm(ServerPlayer player, EntityMaid maid, PregnancyData current, float rhythmScore) {
        if (current.pregnant()) {
            if (!current.isPregnantWith(player.getUUID())) {
                player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.pregnant_with_other", maid.getDisplayName()));
            }
            return PregnancyAttemptResult.noChange(current);
        }
        /*
         * 音游与普通同眠拆分：
         * 普通同眠走 20% 起步、失败 +5%、8 次保底；
         * 音游完全按成绩结算，最高只给到 60% 怀孕率。
         */
        PregnancyAttemptResult attempt = settleRhythmConceptionAttempt(player, maid, current, rhythmScore);
        if (!attempt.conceived()) {
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.no_conception", maid.getDisplayName()));
        }
        return attempt;
    }

    private static void ensureConceptionPersistence(ServerPlayer player, EntityMaid maid, PregnancyAttemptResult attempt) {
        if (!attempt.conceived() || !maid.isAlive()) {
            return;
        }
        PregnancyData latest = maid.getOrCreateData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        if (latest.pregnant()) {
            return;
        }
        long now = maid.level().getGameTime();
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, latest.conceive(player.getUUID(), now, attempt.twins()));
    }

    /**
     * 统一怀孕结算：
     * 首次 60%，失败后逐次 +10%，最高 90%；
     * 第一次 90% 失败后重置；
     * 第二次 90% 再失败后，下次直接保底双胞胎；
     * 另外所有成功怀孕共享 1% 双胞胎概率。
     */
    private static PregnancyAttemptResult settleConceptionAttempt(ServerPlayer player, EntityMaid maid, PregnancyData current, boolean speakAfterWake) {
        long gameTime = maid.level().getGameTime();
        if (current.guaranteedConceptionNextAttempt()) {
            PregnancyData conceived = current.conceive(player.getUUID(), gameTime, true);
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, conceived);
            playConceptionEffects(maid);
            if (speakAfterWake) {
                speakSingleLineAfterWake(maid, "dialogue.maidmarriage.pregnant");
            }
            player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.pregnant_success", maid.getDisplayName()));
            return new PregnancyAttemptResult(true, true, conceived);
        }

        double chance = current.currentConceptionChance();
        if (maid.getRandom().nextDouble() >= chance) {
            PregnancyData failed = current.onConceptionFailed();
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, failed);
            return new PregnancyAttemptResult(false, false, failed);
        }

        boolean twins = maid.getRandom().nextDouble() < SHARED_TWINS_CHANCE;
        PregnancyData conceived = current.conceive(player.getUUID(), gameTime, twins);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, conceived);
        playConceptionEffects(maid);
        if (speakAfterWake) {
            speakSingleLineAfterWake(maid, "dialogue.maidmarriage.pregnant");
        }
        player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.pregnant_success", maid.getDisplayName()));
        return new PregnancyAttemptResult(true, twins, conceived);
    }

    /**
     * 调试用：直接执行一次正式的普通怀孕结算，但不依赖睡觉流程，也不额外发送原本的成功/失败提示。
     *
     * <p>这样测试工具既能复现线上真实概率，又不会被剧情消息干扰。
     */
    public static DebugConceptionResult debugSettleConception(ServerPlayer player, EntityMaid maid, PregnancyData current) {
        long gameTime = maid.level().getGameTime();

        if (current.guaranteedConceptionNextAttempt()) {
            PregnancyData conceived = current.conceive(player.getUUID(), gameTime, true);
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, conceived);
            playConceptionEffects(maid);
            return new DebugConceptionResult(1.0D, true, true, conceived);
        }

        double chance = current.currentConceptionChance();
        if (maid.getRandom().nextDouble() >= chance) {
            PregnancyData failed = current.onConceptionFailed();
            maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, failed);
            return new DebugConceptionResult(chance, false, false, failed);
        }

        boolean twins = maid.getRandom().nextDouble() < SHARED_TWINS_CHANCE;
        PregnancyData conceived = current.conceive(player.getUUID(), gameTime, twins);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, conceived);
        playConceptionEffects(maid);
        return new DebugConceptionResult(chance, true, twins, conceived);
    }

    /**
     * 音游分数换算规则：
     * 分数范围 0~1，最终怀孕率范围 0~60%。
     * 这里不吃普通同眠保底，避免两套系统互相叠太快。
     */
    private static PregnancyAttemptResult settleRhythmConceptionAttempt(ServerPlayer player, EntityMaid maid, PregnancyData current, float rhythmScore) {
        long gameTime = maid.level().getGameTime();
        float clampedScore = (float) Math.max(0.0D, Math.min(1.0D, rhythmScore));
        double chance = RHYTHM_MAX_CONCEPTION_CHANCE * clampedScore;
        if (chance <= 0.0D || maid.getRandom().nextDouble() >= chance) {
            return new PregnancyAttemptResult(false, false, current);
        }

        boolean twins = maid.getRandom().nextDouble() < SHARED_TWINS_CHANCE;
        PregnancyData conceived = current.conceive(player.getUUID(), gameTime, twins);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, conceived);
        playConceptionEffects(maid);
        speakSingleLineAfterWake(maid, "dialogue.maidmarriage.pregnant");
        player.sendSystemMessage(scriptForPlayer(player, "message.maidmarriage.breed.pregnant_success", maid.getDisplayName()));
        return new PregnancyAttemptResult(true, twins, conceived);
    }

    private static void playConceptionEffects(EntityMaid maid) {
        maid.level().playSound(null, maid.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.75F, 1.2F);
        if (maid.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                    10, 0.3, 0.2, 0.3, 0.02);
        }
    }

    private static void speakSingleLineAfterWake(EntityMaid maid, String langKey) {
        CompoundTag data = maid.getPersistentData();
        data.putString(TAG_PENDING_WAKE_DIALOGUE, langKey);
        data.putLong(TAG_PENDING_WAKE_DIALOGUE_DUE_TICK, maid.level().getGameTime() + 1L);
    }

    private static void flushPendingWakeDialogue(EntityMaid maid) {
        if (maid.isSleeping()) {
            return;
        }
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(TAG_PENDING_WAKE_DIALOGUE)) {
            return;
        }
        long dueTick = data.getLong(TAG_PENDING_WAKE_DIALOGUE_DUE_TICK);
        if (maid.level().getGameTime() < dueTick) {
            return;
        }
        String langKey = data.getString(TAG_PENDING_WAKE_DIALOGUE);
        data.remove(TAG_PENDING_WAKE_DIALOGUE);
        data.remove(TAG_PENDING_WAKE_DIALOGUE_DUE_TICK);
        if (!langKey.isBlank()) {
            speakSingleLine(maid, langKey);
        }
    }

    private static void tickTemporaryReviveDialogue(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        if (!data.getBoolean(SoulSlabChildBridge.PERSISTENT_TEMP_REVIVE_CHILD_KEY)) {
            return;
        }
        if (!data.getBoolean(SoulSlabChildBridge.PERSISTENT_TEMP_REVIVE_FROM_PHOTO_KEY)) {
            data.remove(SoulSlabChildBridge.PERSISTENT_TEMP_REVIVE_CHILD_KEY);
            data.remove(TAG_REVIVE_DIALOGUE_NEXT_TICK);
            data.remove(TAG_REVIVE_DIALOGUE_INDEX);
            return;
        }
        if (maid.isSleeping()) {
            return;
        }
        long now = maid.level().getGameTime();
        long nextTick = data.contains(TAG_REVIVE_DIALOGUE_NEXT_TICK) ? data.getLong(TAG_REVIVE_DIALOGUE_NEXT_TICK) : now;
        if (now < nextTick) {
            return;
        }
        int index = data.getInt(TAG_REVIVE_DIALOGUE_INDEX);
        if (index < 0 || index >= REVIVE_CHILD_LINES.size()) {
            data.remove(SoulSlabChildBridge.PERSISTENT_TEMP_REVIVE_CHILD_KEY);
            data.remove(SoulSlabChildBridge.PERSISTENT_TEMP_REVIVE_FROM_PHOTO_KEY);
            data.remove(TAG_REVIVE_DIALOGUE_NEXT_TICK);
            data.remove(TAG_REVIVE_DIALOGUE_INDEX);
            return;
        }
        speakSingleLine(maid, REVIVE_CHILD_LINES.get(index));
        index++;
        if (index >= REVIVE_CHILD_LINES.size()) {
            data.remove(SoulSlabChildBridge.PERSISTENT_TEMP_REVIVE_CHILD_KEY);
            data.remove(SoulSlabChildBridge.PERSISTENT_TEMP_REVIVE_FROM_PHOTO_KEY);
            data.remove(TAG_REVIVE_DIALOGUE_NEXT_TICK);
            data.remove(TAG_REVIVE_DIALOGUE_INDEX);
        } else {
            data.putInt(TAG_REVIVE_DIALOGUE_INDEX, index);
            data.putLong(TAG_REVIVE_DIALOGUE_NEXT_TICK, now + MULTI_LINE_STEP_TICKS);
        }
    }
}
