package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 摸头交互管理器（服务端逻辑）。
 * <p>
 * 负责处理客户端发来的摸头请求，校验女仆状态，并在成功时执行：
 * 坐姿校验、气泡台词、粒子音效、轻微好感奖励等反馈。
 */
public final class PetHeadManager {
    private static final String TAG_PET_HEAD_COOLDOWN_UNTIL = "maidmarriage_pet_head_cooldown_until";
    private static final String TAG_PET_HEAD_HUG_VARIANT = "maidmarriage_pet_head_hug_variant";
    private static final String TAG_PET_HEAD_FAVOR_DAY = "maidmarriage_pet_head_favor_day";
    private static final String TAG_PET_HEAD_FAVOR_GAIN_TODAY = "maidmarriage_pet_head_favor_gain_today";
    private static final String TAG_PET_HEAD_FAVOR_COUNT_TODAY = "maidmarriage_pet_head_favor_count_today";
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;
    private static final int FAVORABILITY_GAIN = 1;
    private static final int OVER_PET_FAVORABILITY_PENALTY = -1;
    private static final int PET_HEAD_MAX_DISTANCE = 5;
    private static final double PET_HEAD_MAX_DISTANCE_SQR = PET_HEAD_MAX_DISTANCE * PET_HEAD_MAX_DISTANCE;
    private static final int PET_HEAD_DAILY_FAVOR_LIMIT = 4;
    private static final int PET_HEAD_WARM_STAGE_GAIN_COUNT_LIMIT = 2;
    private static final long PET_HEAD_COOLDOWN_TICKS = 20L;
    private static final long PET_HEAD_ANIM_TICKS = 52L;
    private static final long HUG_PET_HEAD_ANIM_TICKS = 26L;
    private static final String TAG_PET_HEAD_ANIM_ACTIVE = "maidmarriage_pet_head_anim_active";
    private static final float PET_HEAD_SWAY_YAW_DEGREES = 18.0F;
    private static final float PET_HEAD_SWAY_PITCH_BASE = 6.0F;
    private static final float PET_HEAD_SWAY_PITCH_WAVE = 2.2F;
    private static final float HUG_PET_HEAD_SWAY_YAW_DEGREES = 7.0F;
    private static final float HUG_PET_HEAD_SWAY_PITCH_BASE = 3.2F;
    private static final float HUG_PET_HEAD_SWAY_PITCH_WAVE = 0.9F;
    private static final Map<UUID, PetHeadAnimState> PET_HEAD_ANIM_STATES = new ConcurrentHashMap<>();
    private static final List<String> PET_HEAD_DIALOGUES = List.of(
            "dialogue.maidmarriage.pet_head.1",
            "dialogue.maidmarriage.pet_head.2",
            "dialogue.maidmarriage.pet_head.3",
            "dialogue.maidmarriage.pet_head.4"
    );

    private PetHeadManager() {
    }

    /**
     * 查询女仆是否正处于摸头表现窗口。
     * 拥抱系统会用这个状态判断是否需要暂时放开头部朝向锁定，
     * 否则身体虽然被摸头状态带动了，头部却会被拥抱锁定每 tick 拉回去。
     */
    public static boolean isPetHeadAnimating(EntityMaid maid) {
        return maid != null
                && (PET_HEAD_ANIM_STATES.containsKey(maid.getUUID())
                || maid.getPersistentData().getBoolean(TAG_PET_HEAD_ANIM_ACTIVE));
    }

    /**
     * 拥抱中的摸头会走单独的轻动作版本：
     * 时长更短、幅度更小，而且不再复用原本会带动身体/尾巴的 begging 动作。
     */
    public static boolean isHugPetHeadAnimating(EntityMaid maid) {
        return maid != null
                && (maid.getPersistentData().getBoolean(TAG_PET_HEAD_ANIM_ACTIVE)
                && maid.getPersistentData().getBoolean(TAG_PET_HEAD_HUG_VARIANT));
    }

    /**
     * 客户端本地预测开启一次摸头动画窗口。
     *
     * <p>因为我们现在不再把 `maid.isBegging()` 当成摸头动作判定本体，
     * 所以客户端在发包后，需要先给本地实体打一层短时预测标签，
     * 等服务端真实状态回收时再自然结束。
     */
    public static void startClientPredictedPetHead(EntityMaid maid, boolean hugVariant) {
        if (maid == null) {
            return;
        }
        maid.getPersistentData().putBoolean(TAG_PET_HEAD_ANIM_ACTIVE, true);
        maid.getPersistentData().putBoolean(TAG_PET_HEAD_HUG_VARIANT, hugVariant);
    }

    /**
     * 客户端本地预测结束摸头动画窗口。
     */
    public static void stopClientPredictedPetHead(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        maid.getPersistentData().remove(TAG_PET_HEAD_ANIM_ACTIVE);
        maid.getPersistentData().remove(TAG_PET_HEAD_HUG_VARIANT);
    }

    public static long clientPredictedAnimTicks(boolean hugVariant) {
        return hugVariant ? HUG_PET_HEAD_ANIM_TICKS : PET_HEAD_ANIM_TICKS;
    }

    /**
     * 处理一次摸头请求。
     *
     * @param player  发起请求的玩家
     * @param maidUuid 客户端可选传入的目标女仆 UUID（未传时自动查找）
     */
    public static void handlePetHeadRequest(ServerPlayer player, @Nullable UUID maidUuid) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(TAG_PET_HEAD_COOLDOWN_UNTIL) > now) {
            return;
        }

        EntityMaid maid = resolveTargetMaid(player, maidUuid);
        if (maid == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pet_head.no_target"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pet_head.need_owner", maid.getDisplayName()));
            return;
        }
        if (!MaidHugManager.isInteractionState(maid, player)
                && !ChildInteractionManager.isInteractionState(maid, player)
                && maid.distanceToSqr(player) > PET_HEAD_MAX_DISTANCE_SQR) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                    player,
                    "message.maidmarriage.pet_head.too_far",
                    maid.getDisplayName(),
                    PET_HEAD_MAX_DISTANCE));
            return;
        }
        boolean allowDuringInteraction = MaidHugManager.isInteractionState(maid, player)
                || ChildInteractionManager.isInteractionState(maid, player);
        boolean hugVariant = MaidHugManager.isHugState(maid, player);
        if (MaidChildEntity.shouldStayChild(maid) && !allowDuringInteraction && !maid.isMaidInSittingPose()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pet_head.need_sitting", maid.getDisplayName()));
            return;
        }
        if (!MaidChildEntity.shouldStayChild(maid) && !allowDuringInteraction && !maid.isMaidInSittingPose()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pet_head.adult_need_sitting", maid.getDisplayName()));
            return;
        }
        if (!allowDuringInteraction && !maid.isMaidInSittingPose()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.pet_head.need_sitting", maid.getDisplayName()));
            return;
        }

        player.getPersistentData().putLong(TAG_PET_HEAD_COOLDOWN_UNTIL, now + PET_HEAD_COOLDOWN_TICKS);
        maid.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (!hugVariant) {
            maid.swing(InteractionHand.MAIN_HAND);
        }
        /*
         * 这里统一打开 begging，原因不是复用旧动作，
         * 而是借用这个原版/模组已同步到客户端的状态位，给渲染层一个稳定的“轻摸头进行中”信号。
         * 真正的身体姿势仍由我们的拥抱骨骼覆盖控制，不再直接沿用旧摸头整套摆身动作。
         */
        maid.setBegging(true);
        maid.getPersistentData().putBoolean(TAG_PET_HEAD_ANIM_ACTIVE, true);
        maid.getPersistentData().putBoolean(TAG_PET_HEAD_HUG_VARIANT, hugVariant);
        long endTick = now + (hugVariant ? HUG_PET_HEAD_ANIM_TICKS : PET_HEAD_ANIM_TICKS);
        PET_HEAD_ANIM_STATES.put(maid.getUUID(), new PetHeadAnimState(player.getUUID(), now, endTick, hugVariant));

        MaidMoodManager.applyLimitedInteractionMoodGain(maid, MaidMoodManager.EVENT_PET_HEAD);
        int actualFavorabilityGain = applyDailyLimitedPetHeadFavorabilityGain(maid);
        MaidMoodManager.markMeaningfulInteraction(maid);
        boolean preHugStage = maid.getFavorability() < RelationshipThresholds.HUG_UNLOCK;
        boolean overPetWarmStage = actualFavorabilityGain <= 0 && preHugStage;
        if (overPetWarmStage) {
            MaidMoodManager.applyFavorabilityDeltaWithRefresh(maid, OVER_PET_FAVORABILITY_PENALTY, FAVORABILITY_CAP);
        }
        String dialogue = overPetWarmStage
                ? (level.getRandom().nextBoolean()
                ? "dialogue.maidmarriage.pet_head.limit_warm.1"
                : "dialogue.maidmarriage.pet_head.limit_warm.2")
                : PET_HEAD_DIALOGUES.get(level.getRandom().nextInt(PET_HEAD_DIALOGUES.size()));
        /*
         * 摸头是一个很轻的交互动作，但用户希望不只在头顶气泡里看见反馈，
         * 还要在聊天框同步看到女仆的回应。
         * 因此这里统一走“气泡 + 聊天框”入口。
         */
        RomanceSleepManager.speakSingleLineWithChat(maid, dialogue);
        level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1.05D), maid.getZ(),
                5, 0.25D, 0.15D, 0.25D, 0.01D);
        level.playSound(null, maid.blockPosition(), SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 0.7F, 1.05F);
        if (actualFavorabilityGain > 0) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                    player,
                    "message.maidmarriage.pet_head.success",
                    maid.getDisplayName(),
                    actualFavorabilityGain));
        } else if (overPetWarmStage) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                    player,
                    "message.maidmarriage.pet_head.over_pet_penalty",
                    maid.getDisplayName(),
                    Math.abs(OVER_PET_FAVORABILITY_PENALTY)));
        }
    }

    /**
     * 解析本次请求要操作的女仆目标。
     * 优先使用客户端传入 UUID；若未传或无效，则自动选择玩家所在维度里第一只“属于玩家且坐姿”的女仆。
     */
    @Nullable
    private static EntityMaid resolveTargetMaid(ServerPlayer player, @Nullable UUID maidUuid) {
        ServerLevel level = player.serverLevel();
        if (maidUuid != null) {
            Entity entity = level.getEntity(maidUuid);
            if (entity instanceof EntityMaid maid
                    && (MaidHugManager.isInteractionState(maid, player)
                    || ChildInteractionManager.isInteractionState(maid, player)
                    || maid.distanceToSqr(player) <= PET_HEAD_MAX_DISTANCE_SQR)) {
                return maid;
            }
        }
        EntityMaid interactingMaid = MaidHugManager.getInteractingMaid(player);
        if (interactingMaid != null) {
            return interactingMaid;
        }
        EntityMaid childInteractingMaid = ChildInteractionManager.getInteractingMaid(player);
        if (childInteractingMaid != null) {
            return childInteractingMaid;
        }
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityMaid maid
                    && maid.isOwnedBy(player)
                    && maid.isMaidInSittingPose()
                    && maid.distanceToSqr(player) <= PET_HEAD_MAX_DISTANCE_SQR) {
                return maid;
            }
        }
        return null;
    }

    /**
     * 摸头的每日好感上限：
     * - 每天最多通过摸头增加 5 点实际好感；
     * - 这里按“最终真正加到女仆身上的值”统计，而不是按基础值统计，
     *   这样可以避免心情倍率把日上限绕过去。
     */
    private static int applyDailyLimitedPetHeadFavorabilityGain(EntityMaid maid) {
        int requestedGain = MaidMoodManager.resolvePositiveInteractionFavorabilityGain(maid, FAVORABILITY_GAIN);
        if (requestedGain <= 0) {
            return 0;
        }

        CompoundTag tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        long recordedDay = tag.getLong(TAG_PET_HEAD_FAVOR_DAY);
        if (recordedDay != currentDay) {
            tag.putLong(TAG_PET_HEAD_FAVOR_DAY, currentDay);
            tag.putInt(TAG_PET_HEAD_FAVOR_GAIN_TODAY, 0);
            tag.putInt(TAG_PET_HEAD_FAVOR_COUNT_TODAY, 0);
        }

        int gainedToday = Math.max(0, tag.getInt(TAG_PET_HEAD_FAVOR_GAIN_TODAY));
        int gainCountToday = Math.max(0, tag.getInt(TAG_PET_HEAD_FAVOR_COUNT_TODAY));
        int favorability = maid.getFavorability();

        if (favorability < RelationshipThresholds.HUG_UNLOCK) {
            if (gainCountToday >= PET_HEAD_WARM_STAGE_GAIN_COUNT_LIMIT) {
                return 0;
            }
            int appliedGain = MaidMoodManager.applyDailyLimitedInteractionFavorabilityDelta(maid, requestedGain, FAVORABILITY_CAP);
            if (appliedGain > 0) {
                tag.putInt(TAG_PET_HEAD_FAVOR_COUNT_TODAY, gainCountToday + 1);
                tag.putInt(TAG_PET_HEAD_FAVOR_GAIN_TODAY, gainedToday + appliedGain);
            }
            return appliedGain;
        }

        int remainingToday = Math.max(0, PET_HEAD_DAILY_FAVOR_LIMIT - gainedToday);
        if (remainingToday <= 0) {
            return 0;
        }

        int appliedGain = MaidMoodManager.applyDailyLimitedInteractionFavorabilityDelta(
                maid,
                Math.min(requestedGain, remainingToday),
                FAVORABILITY_CAP
        );
        if (appliedGain > 0) {
            tag.putInt(TAG_PET_HEAD_FAVOR_GAIN_TODAY, gainedToday + appliedGain);
        }
        return appliedGain;
    }

    /**
     * 服务端逐 tick 回收“摸头享受动作”状态。
     * 到时后自动关闭 begging，避免女仆一直停留在享受动作。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PET_HEAD_ANIM_STATES.isEmpty()) {
            return;
        }
        var it = PET_HEAD_ANIM_STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PetHeadAnimState> entry = it.next();
            UUID maidUuid = entry.getKey();
            PetHeadAnimState state = entry.getValue();
            EntityMaid maidEntity = null;
            ServerLevel maidLevel = null;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                Entity entity = level.getEntity(maidUuid);
                if (entity instanceof EntityMaid maid) {
                    maidEntity = maid;
                    maidLevel = level;
                    break;
                }
            }
            if (maidEntity == null || maidLevel == null) {
                it.remove();
                continue;
            }
            long now = maidLevel.getGameTime();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(state.ownerUuid);
            boolean keepAnim = maidEntity.isMaidInSittingPose()
                    || (owner != null && (MaidHugManager.isInteractionState(maidEntity, owner)
                    || ChildInteractionManager.isInteractionState(maidEntity, owner)));
            if (now >= state.endTick || !keepAnim) {
                maidEntity.setBegging(false);
                maidEntity.getPersistentData().remove(TAG_PET_HEAD_ANIM_ACTIVE);
                maidEntity.getPersistentData().remove(TAG_PET_HEAD_HUG_VARIANT);
                it.remove();
                continue;
            }
            if (owner != null) {
                driveSwayLookAt(maidEntity, owner, now - state.startTick, state.hugVariant);
            }
        }
    }

    /**
     * 驱动“左右摇头 + 微微前倾感”的可见动作效果。
     * 通过每 tick 更新 LookControl 的目标点，让头部持续左右摆动而不是单次偏转。
     */
    private static void driveSwayLookAt(EntityMaid maid, ServerPlayer owner, long elapsedTick, boolean hugVariant) {
        float phase = elapsedTick * (hugVariant ? 0.68F : 0.52F);
        float yawOffset = Mth.sin(phase) * (hugVariant ? HUG_PET_HEAD_SWAY_YAW_DEGREES : PET_HEAD_SWAY_YAW_DEGREES);
        float pitchOffset = (hugVariant ? HUG_PET_HEAD_SWAY_PITCH_BASE : PET_HEAD_SWAY_PITCH_BASE)
                + Mth.sin(phase * 0.75F) * (hugVariant ? HUG_PET_HEAD_SWAY_PITCH_WAVE : PET_HEAD_SWAY_PITCH_WAVE);

        double dx = owner.getX() - maid.getX();
        double dz = owner.getZ() - maid.getZ();
        float baseYaw = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI));
        float finalYaw = baseYaw + yawOffset;
        double rad = Math.toRadians(finalYaw);

        if (!hugVariant) {
            double targetX = maid.getX() + Mth.cos((float) rad) * 2.0D;
            double targetZ = maid.getZ() + Mth.sin((float) rad) * 2.0D;
            double targetY = maid.getEyeY() + pitchOffset * 0.03D;
            maid.getLookControl().setLookAt(targetX, targetY, targetZ, 50.0F, 50.0F);
        }

        /*
         * LookControl 在下一次实体 AI tick 才会真正落到头部旋转。
         * 但拥抱锁定会在每 tick 直接写死头部角度，所以这里同步写入头部旋转，
         * 让“被摸头时左右轻摆头”的动作能立刻覆盖拥抱的面向锁。
         */
        double ownerDx = owner.getX() - maid.getX();
        double ownerDz = owner.getZ() - maid.getZ();
        float ownerYaw = (float) (Mth.atan2(-ownerDx, ownerDz) * Mth.RAD_TO_DEG);
        maid.setYHeadRot(ownerYaw + yawOffset);
        if (!hugVariant) {
            maid.setXRot(pitchOffset);
        }
    }

    /**
     * 摸头动画窗口状态。
     *
     * @param ownerUuid  触发摸头的主人 UUID
     * @param startTick  动画开始 tick
     * @param endTick    动画结束 tick
     */
    private record PetHeadAnimState(UUID ownerUuid, long startTick, long endTick, boolean hugVariant) {
    }
}
