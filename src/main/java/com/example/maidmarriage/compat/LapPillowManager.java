package com.example.maidmarriage.compat;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.entity.LapPillowAnchorEntity;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.LapPillowStateSyncPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 膝枕状态管理器。
 *
 * <p>服务端负责真实校验、锁位和同步；客户端只根据同步结果做 UI 条件与动作表现。
 * 这里不使用玩家骑乘锚点的方案，而是用锚点实体记录姿态中心，再每 tick 将玩家锁到锚点位置。
 * 这样可以继续保留互动面板，不会因为骑乘链和亲密交互会话互相抢控制权。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LapPillowManager {
    private static final double START_DISTANCE_SQR = 2.75D * 2.75D;
    private static final int PET_PLAYER_HEAD_TICKS = 96;
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;
    private static final float DEFAULT_SLEEP_YAW_OFFSET = -90.0F;

    private static final Map<UUID, Session> PLAYER_TO_SESSION = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> MAID_TO_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_ANCHOR = new ConcurrentHashMap<>();
    private static final Map<UUID, DebugPose> PLAYER_TO_DEBUG_POSE = new ConcurrentHashMap<>();

    private LapPillowManager() {
    }

    public static void handleStart(ServerPlayer player, @Nullable UUID maidUuid) {
        if (PLAYER_TO_SESSION.containsKey(player.getUUID())) {
            stop(player, true);
            return;
        }

        EntityMaid maid = resolveTargetMaid(player, maidUuid);
        if (maid == null) {
            player.sendSystemMessage(Component.literal("没有找到可以膝枕的女仆。"));
            return;
        }
        String rejectReason = validateStart(player, maid);
        if (rejectReason != null) {
            player.sendSystemMessage(Component.literal(rejectReason));
            return;
        }

        MaidHugManager.forceStopHugPose(player, maid);
        start(player, maid);
    }

    public static void handleExit(ServerPlayer player) {
        stop(player, true);
    }

    public static void handlePetPlayerHead(ServerPlayer player, @Nullable UUID maidUuid) {
        Session session = PLAYER_TO_SESSION.get(player.getUUID());
        if (session == null) {
            return;
        }
        /*
         * 膝枕中后续动作以服务端 session 为准。
         * 客户端 UI 可能还保留着进入膝枕前的互动目标，不能因为 UUID 参数短暂不同就吞掉动作。
         */
        EntityMaid maid = findMaidByUuid(player.serverLevel(), session.maidUuid());
        if (maid == null || !maid.isAlive()) {
            stop(player, false);
            return;
        }

        MaidMoodManager.markMeaningfulInteraction(maid);
        MaidMoodManager.applyLimitedInteractionMoodGain(maid, MaidMoodManager.EVENT_LAP_PILLOW);
        player.serverLevel().sendParticles(
                ParticleTypes.HEART,
                player.getX(), player.getY(0.75D), player.getZ(),
                5, 0.22D, 0.14D, 0.22D, 0.01D
        );
        player.serverLevel().playSound(
                null,
                maid.blockPosition(),
                SoundEvents.ALLAY_ITEM_GIVEN,
                SoundSource.PLAYERS,
                0.55F,
                1.2F
        );
        sync(player, maid, findAnchor(player), PET_PLAYER_HEAD_TICKS);
    }

    public static void handleDebugPose(ServerPlayer player,
                                       double sideOffset,
                                       double heightOffset,
                                       double forwardOffset,
                                       float yawOffset) {
        if (!isPlayerInLapPillow(player)) {
            return;
        }
        PLAYER_TO_DEBUG_POSE.put(player.getUUID(), new DebugPose(
                Mth.clamp(sideOffset, -2.0D, 2.0D),
                Mth.clamp(heightOffset, -0.5D, 2.0D),
                Mth.clamp(forwardOffset, -2.0D, 2.0D),
                Mth.wrapDegrees(yawOffset)
        ));
        Session session = PLAYER_TO_SESSION.get(player.getUUID());
        EntityMaid maid = session == null ? null : findMaidByUuid(player.serverLevel(), session.maidUuid());
        sync(player, maid, findAnchor(player), 0);
    }

    public static boolean isPlayerInLapPillow(@Nullable Player player) {
        return player != null && PLAYER_TO_SESSION.containsKey(player.getUUID());
    }

    public static boolean isLapPillowMaid(@Nullable EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        if (maid.level().isClientSide()) {
            return com.example.maidmarriage.client.LapPillowClientState.isLapPillowMaid(maid.getUUID());
        }
        return MAID_TO_PLAYER.containsKey(maid.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        Session session = PLAYER_TO_SESSION.get(player.getUUID());
        if (session == null) {
            return;
        }

        EntityMaid maid = findMaidByUuid(player.serverLevel(), session.maidUuid());
        if (!isValidActivePair(player, maid, session)) {
            stop(player, false);
            return;
        }

        LapPillowAnchorEntity anchor = findAnchor(player);
        if (anchor == null || !anchor.isAlive()) {
            anchor = createAnchor(player, maid);
            PLAYER_TO_ANCHOR.put(player.getUUID(), anchor.getUUID());
            sync(player, maid, anchor, 0);
        }
        maintainPose(player, maid, anchor, session);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
            return;
        }
        if (event.getEntity() instanceof EntityMaid maid && maid.level() instanceof ServerLevel level) {
            UUID playerUuid = MAID_TO_PLAYER.get(maid.getUUID());
            ServerPlayer player = playerUuid == null ? null : level.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                stop(player, false);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        }
    }

    private static void start(ServerPlayer player, EntityMaid maid) {
        MaidMoodManager.ensureDailyMood(maid);

        LapPillowAnchorEntity anchor = createAnchor(player, maid);
        /*
         * 膝枕开始时就把整套姿态朝向固定下来。
         *
         * 这里不能每 tick 用“女仆 -> 玩家”的实时向量重新算 yaw：
         * 玩家位置本身又是根据女仆 yaw 推出来的，一旦两者互相追，就会形成反馈环，
         * 表现成玩家围着女仆头顶不停旋转。
         */
        float lockedMaidYaw = maid.getYRot();
        Session session = new Session(player.getUUID(), maid.getUUID(), lockedMaidYaw);
        PLAYER_TO_SESSION.put(player.getUUID(), session);
        MAID_TO_PLAYER.put(maid.getUUID(), player.getUUID());
        PLAYER_TO_ANCHOR.put(player.getUUID(), anchor.getUUID());

        maintainPose(player, maid, anchor, session);
        MaidMoodManager.markMeaningfulInteraction(maid);
        MaidMoodManager.applyLimitedInteractionMoodGain(maid, MaidMoodManager.EVENT_LAP_PILLOW);
        MaidMoodManager.applyInteractionFavorabilityGain(maid, 1, FAVORABILITY_CAP);

        player.serverLevel().sendParticles(
                ParticleTypes.HEART,
                maid.getX(), maid.getY(1.0D), maid.getZ(),
                8, 0.2D, 0.22D, 0.2D, 0.01D
        );
        player.serverLevel().playSound(
                null,
                maid.blockPosition(),
                SoundEvents.ALLAY_AMBIENT_WITH_ITEM,
                SoundSource.PLAYERS,
                0.6F,
                1.08F
        );
        sync(player, maid, anchor, 0);
    }

    private static void stop(ServerPlayer player, boolean manual) {
        Session session = PLAYER_TO_SESSION.remove(player.getUUID());
        UUID anchorUuid = PLAYER_TO_ANCHOR.remove(player.getUUID());
        PLAYER_TO_DEBUG_POSE.remove(player.getUUID());
        if (session != null) {
            MAID_TO_PLAYER.remove(session.maidUuid());
        }

        LapPillowAnchorEntity anchor = LapPillowAnchorEntity.find(player.serverLevel(), anchorUuid);
        if (anchor != null) {
            anchor.discard();
        }

        EntityMaid maid = session == null ? null : findMaidByUuid(player.serverLevel(), session.maidUuid());
        if (maid != null) {
            maid.setInSittingPose(false);
            maid.setPose(Pose.STANDING);
            maid.setDeltaMovement(Vec3.ZERO);
            maid.getNavigation().stop();
            maid.setTarget(null);
        }

        player.setNoGravity(false);
        player.setForcedPose(null);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        if (manual && maid != null) {
            player.sendSystemMessage(Component.literal(maid.getDisplayName().getString() + "轻轻扶你坐了起来。"));
        }
        sync(player, null, null, 0);
    }

    private static void maintainPose(ServerPlayer player, EntityMaid maid, LapPillowAnchorEntity anchor, Session session) {
        float yawToPlayer = session.lockedMaidYaw();
        maid.getNavigation().stop();
        maid.setTarget(null);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
        maid.setInSittingPose(true);
        maid.setPose(Pose.SITTING);
        maid.setYRot(yawToPlayer);
        maid.setYBodyRot(yawToPlayer);
        maid.setYHeadRot(yawToPlayer);
        maid.setXRot(0.0F);

        anchor.moveToMaidLap(maid);
        DebugPose debugPose = PLAYER_TO_DEBUG_POSE.get(player.getUUID());
        Vec3 restPos = debugPose == null
                ? LapPillowAnchorEntity.computePlayerRestPosition(maid)
                : LapPillowAnchorEntity.computePlayerRestPosition(
                        maid,
                        debugPose.sideOffset(),
                        debugPose.heightOffset(),
                        debugPose.forwardOffset()
                );
        if (debugPose != null) {
            anchor.setPos(restPos.x, restPos.y, restPos.z);
        }
        player.teleportTo(restPos.x, restPos.y, restPos.z);
        /*
         * 这里只写 forcedPose，不写 player.setPose(Pose.SLEEPING)。
         *
         * forcedPose 只告诉客户端“这个实体渲染时应该按睡姿显示”，不会把玩家真正塞进床的交互流程；
         * setPose 才更容易触发原版睡觉 UI/输入语义，导致我们的互动面板被隐藏。
         */
        player.setForcedPose(Pose.SLEEPING);
        player.setNoGravity(true);
        player.setOnGround(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        float playerYaw = currentSleepYaw(player, session);
        player.setYRot(playerYaw);
        player.setYHeadRot(playerYaw);
        player.setYBodyRot(playerYaw);
        player.setXRot(0.0F);
    }

    @Nullable
    private static String validateStart(ServerPlayer player, EntityMaid maid) {
        if (!maid.isOwnedBy(player)) {
            return "只有自己的女仆才可以膝枕。";
        }
        if (MaidChildEntity.shouldStayChild(maid)) {
            return "小女仆还不适合做膝枕互动。";
        }
        if (maid.isPassenger() || MAID_TO_PLAYER.containsKey(maid.getUUID())) {
            return "她现在正在忙，等一下再靠过去吧。";
        }
        if (player.isPassenger() || player.isFallFlying() || player.isSpectator()) {
            return "你现在的姿势不太适合膝枕。";
        }
        if (maid.distanceToSqr(player) > START_DISTANCE_SQR) {
            return "离她近一点，再试着靠过去吧。";
        }
        RelationStage stage = MaidRelationshipManager.resolveStage(maid);
        if (stage != RelationStage.DATING && stage != RelationStage.MARRIAGE) {
            return "关系还没有到可以膝枕的时候。";
        }
        MaidMoodData.MoodState mood = MaidMoodManager.state(maid);
        if (mood == MaidMoodData.MoodState.DEPRESSED || mood == MaidMoodData.MoodState.GENERAL) {
            return "她今天心情不太好，还是先哄哄她吧。";
        }
        return null;
    }

    private static boolean isValidActivePair(ServerPlayer player, @Nullable EntityMaid maid, Session session) {
        return maid != null
                && maid.isAlive()
                && player.isAlive()
                && maid.getUUID().equals(session.maidUuid())
                && player.getUUID().equals(session.playerUuid())
                && maid.isOwnedBy(player)
                && !MaidChildEntity.shouldStayChild(maid)
                && !maid.isPassenger()
                && !player.isFallFlying()
                && !player.isSpectator()
                && player.level() == maid.level();
    }

    private static LapPillowAnchorEntity createAnchor(ServerPlayer player, EntityMaid maid) {
        LapPillowAnchorEntity anchor = ModEntities.LAP_PILLOW_ANCHOR.get().create(player.level());
        if (anchor == null) {
            throw new IllegalStateException("Failed to create lap pillow anchor entity");
        }
        anchor.setTargets(player.getUUID(), maid.getUUID());
        anchor.moveToMaidLap(maid);
        player.level().addFreshEntity(anchor);
        return anchor;
    }

    @Nullable
    private static LapPillowAnchorEntity findAnchor(ServerPlayer player) {
        return LapPillowAnchorEntity.find(player.serverLevel(), PLAYER_TO_ANCHOR.get(player.getUUID()));
    }

    private static void sync(ServerPlayer player, @Nullable EntityMaid maid,
                             @Nullable LapPillowAnchorEntity anchor, int petTicks) {
        Session session = PLAYER_TO_SESSION.get(player.getUUID());
        float sleepYaw = session == null ? player.getYRot() : currentSleepYaw(player, session);
        ModNetworking.sendLapPillowState(player, new LapPillowStateSyncPayload(
                player.getUUID(),
                maid == null ? null : maid.getUUID(),
                anchor == null ? null : anchor.getUUID(),
                maid != null,
                sleepYaw,
                petTicks
        ));
    }

    @Nullable
    private static EntityMaid resolveTargetMaid(ServerPlayer player, @Nullable UUID maidUuid) {
        if (maidUuid != null) {
            Entity entity = player.serverLevel().getEntity(maidUuid);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        EntityMaid interacting = MaidHugManager.getInteractingMaid(player);
        if (interacting != null) {
            return interacting;
        }
        return null;
    }

    @Nullable
    private static EntityMaid findMaidByUuid(ServerLevel level, @Nullable UUID maidUuid) {
        if (maidUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(maidUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private static float computeYawTowards(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
    }

    private record Session(UUID playerUuid, UUID maidUuid, float lockedMaidYaw) {
    }

    private static float currentSleepYaw(ServerPlayer player, Session session) {
        DebugPose debugPose = PLAYER_TO_DEBUG_POSE.get(player.getUUID());
        float yawOffset = debugPose == null ? DEFAULT_SLEEP_YAW_OFFSET : debugPose.yawOffset();
        return Mth.wrapDegrees(session.lockedMaidYaw() + 180.0F + yawOffset);
    }

    private record DebugPose(double sideOffset, double heightOffset, double forwardOffset, float yawOffset) {
    }
}
