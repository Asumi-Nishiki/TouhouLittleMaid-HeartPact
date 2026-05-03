package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.ChildInteractionStateSyncPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 小女仆互动会话管理器。
 *
 * <p>这条链路刻意仿照成年女仆的拥抱 UI 唤起方式：
 * 1. 客户端只发起“我要和这只小女仆互动”的请求；
 * 2. 服务端校验目标、创建站立锁定会话、每 tick 维持面对面站位；
 * 3. 服务端把会话状态同步回客户端；
 * 4. 客户端收到同步后才自动打开小女仆互动 UI。
 *
 * <p>这样小女仆 UI 不再走“按键后客户端本地直接开屏”的临时路线，
 * 行为会和拥抱 UI 一样稳定：目标固定、位置固定、服务端权威。
 */
@Mod.EventBusSubscriber(modid = com.example.maidmarriage.MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChildInteractionManager {
    private static final double AUTO_SELECT_RANGE_SQR = 16 * 16;
    private static final double START_DISTANCE_SQR = 2.25D * 2.25D;
    private static final double BREAK_DISTANCE_SQR = 3.0D * 3.0D;
    private static final double PLAYER_POSITION_EPSILON_SQR = 0.0004D;
    private static final double MAID_POSITION_EPSILON_SQR = 0.0025D;

    /**
     * 服务端：一个玩家当前最多只维护一份小女仆互动会话。
     */
    private static final Map<UUID, ChildInteractionSession> PLAYER_TO_SESSION = new ConcurrentHashMap<>();

    /**
     * 服务端：反查某只小女仆是否已经被某个玩家锁定。
     */
    private static final Map<UUID, UUID> MAID_TO_PLAYER = new ConcurrentHashMap<>();

    /**
     * 客户端：同步下来的“玩家 -> 小女仆互动目标”映射。
     *
     * <p>目前主要给本地 UI 和后续渲染判断用；即使同步给旁观玩家，
     * 本地 UI 状态类也只会处理当前玩家自己的同步。
     */
    private static final Map<UUID, UUID> CLIENT_PLAYER_TO_MAID = new ConcurrentHashMap<>();

    /**
     * 客户端：同步下来的“小女仆 -> 互动玩家”反查映射。
     */
    private static final Map<UUID, UUID> CLIENT_MAID_TO_PLAYER = new ConcurrentHashMap<>();

    private ChildInteractionManager() {
    }

    /**
     * Alt+O 的服务端入口。
     *
     * <p>语义和成年女仆互动入口一致：
     * - 已经有小女仆互动会话：结束会话；
     * - 没有会话：尝试对目标小女仆建立站立锁定会话。
     */
    public static void handleInteractionToggle(ServerPlayer player, @Nullable UUID maidUuid) {
        ChildInteractionSession existing = PLAYER_TO_SESSION.get(player.getUUID());
        if (existing != null) {
            stopInteraction(player, findMaidByUuid(player.serverLevel(), existing.maidUuid()));
            return;
        }

        EntityMaid maid = resolveTargetMaid(player, maidUuid);
        if (maid == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_interaction.no_target"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_interaction.need_owner", maid.getDisplayName()));
            return;
        }
        if (!MaidChildEntity.shouldStayChild(maid)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_interaction.need_child", maid.getDisplayName()));
            return;
        }
        if (maid.isMaidInSittingPose()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_interaction.need_standing", maid.getDisplayName()));
            return;
        }
        if (maid.isPassenger() || MAID_TO_PLAYER.containsKey(maid.getUUID())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_interaction.already_busy", maid.getDisplayName()));
            return;
        }
        if (maid.distanceToSqr(player) > START_DISTANCE_SQR) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_interaction.no_target"));
            return;
        }

        startInteraction(player, maid);
    }

    /**
     * 判断指定玩家和小女仆是否处于小女仆互动会话中。
     */
    public static boolean isInteractionState(EntityMaid maid, @Nullable Player player) {
        if (maid == null || player == null) {
            return false;
        }
        if (maid.level().isClientSide()) {
            return maid.getUUID().equals(CLIENT_PLAYER_TO_MAID.get(player.getUUID()));
        }
        return player.getUUID().equals(MAID_TO_PLAYER.get(maid.getUUID()));
    }

    @Nullable
    public static Player getInteractionPlayer(EntityMaid maid) {
        if (maid == null) {
            return null;
        }
        UUID playerUuid = maid.level().isClientSide()
                ? CLIENT_MAID_TO_PLAYER.get(maid.getUUID())
                : MAID_TO_PLAYER.get(maid.getUUID());
        if (playerUuid == null) {
            return null;
        }
        if (maid.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().getPlayerList().getPlayer(playerUuid);
        }
        return com.example.maidmarriage.client.ClientEntityLookup.findPlayer(playerUuid);
    }

    /**
     * 客户端收到服务端同步后更新运行时映射。
     */
    public static void handleClientInteractionStateSync(UUID playerUuid, @Nullable UUID maidUuid) {
        if (maidUuid == null) {
            UUID oldMaid = CLIENT_PLAYER_TO_MAID.remove(playerUuid);
            if (oldMaid != null) {
                CLIENT_MAID_TO_PLAYER.remove(oldMaid);
            }
            return;
        }

        UUID oldMaid = CLIENT_PLAYER_TO_MAID.put(playerUuid, maidUuid);
        if (oldMaid != null && !oldMaid.equals(maidUuid)) {
            CLIENT_MAID_TO_PLAYER.remove(oldMaid);
        }
        CLIENT_MAID_TO_PLAYER.put(maidUuid, playerUuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        ChildInteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        if (session == null) {
            return;
        }

        EntityMaid maid = findMaidByUuid(player.serverLevel(), session.maidUuid());
        if (!isValidInteractionPair(player, maid, session)) {
            stopInteraction(player, maid);
            return;
        }

        maintainInteractionPose(player, maid, session);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopInteraction(player, getInteractingMaid(player));
            return;
        }
        if (event.getEntity() instanceof EntityMaid maid) {
            UUID playerUuid = MAID_TO_PLAYER.get(maid.getUUID());
            if (playerUuid == null || !(maid.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                stopInteraction(player, maid);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopInteraction(player, getInteractingMaid(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopInteraction(player, getInteractingMaid(player));
        }
    }

    private static void startInteraction(ServerPlayer player, EntityMaid maid) {
        MaidMoodManager.ensureDailyMood(maid);
        ChildInteractionSession session = createSession(player, maid);
        PLAYER_TO_SESSION.put(player.getUUID(), session);
        MAID_TO_PLAYER.put(maid.getUUID(), player.getUUID());
        maintainInteractionPose(player, maid, session);
        syncInteractionState(player, maid);
    }

    private static void stopInteraction(ServerPlayer player, @Nullable EntityMaid maid) {
        ChildInteractionSession session = PLAYER_TO_SESSION.remove(player.getUUID());
        if (session != null) {
            MAID_TO_PLAYER.remove(session.maidUuid());
        }
        if (maid == null && session != null) {
            maid = findMaidByUuid(player.serverLevel(), session.maidUuid());
        }

        if (maid != null) {
            maid.setPose(Pose.STANDING);
            maid.setInSittingPose(false);
            maid.setDeltaMovement(Vec3.ZERO);
            maid.setTarget(null);
            maid.getNavigation().stop();
            maid.setYBodyRot(maid.getYRot());
            maid.setYHeadRot(maid.getYRot());
        }

        syncInteractionState(player, null);
    }

    private static ChildInteractionSession createSession(ServerPlayer player, EntityMaid maid) {
        float playerYaw = player.getYRot();
        Vec3 lockedPlayerPos = player.position();
        Vec3 lockedMaidPos = resolveGroundedMaidPosition(
                maid.level(),
                computeLockedMaidPos(lockedPlayerPos, playerYaw, MaidHugManager.resolveHugDistance(player))
        );
        float maidYaw = computeYawTowards(lockedMaidPos, lockedPlayerPos);
        return new ChildInteractionSession(player.getUUID(), maid.getUUID(), lockedPlayerPos, playerYaw, lockedMaidPos, maidYaw);
    }

    /**
     * 每 tick 维持“小女仆站在玩家面前”的锁定姿态。
     *
     * <p>这里不做拥抱姿态、不触发 YSM 改骨，只负责 UI 会话的空间稳定。
     */
    private static void maintainInteractionPose(ServerPlayer player, EntityMaid maid, ChildInteractionSession session) {
        lockPlayer(player, session);
        lockMaid(player, maid, session.withGroundedMaidPos(resolveGroundedMaidPosition(maid.level(), session.lockedMaidPos())));
    }

    private static void lockPlayer(ServerPlayer player, ChildInteractionSession session) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        if (player.position().distanceToSqr(session.lockedPlayerPos()) > PLAYER_POSITION_EPSILON_SQR) {
            Vec3 pos = session.lockedPlayerPos();
            player.teleportTo(pos.x, pos.y, pos.z);
        }
        player.setYRot(session.lockedPlayerYaw());
        player.setYHeadRot(session.lockedPlayerYaw());
        player.setYBodyRot(session.lockedPlayerYaw());
        player.setXRot(0.0F);
    }

    private static void lockMaid(ServerPlayer player, EntityMaid maid, ChildInteractionSession session) {
        maid.getNavigation().stop();
        maid.setTarget(null);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
        maid.setPose(Pose.STANDING);
        maid.setInSittingPose(false);
        if (maid.position().distanceToSqr(session.lockedMaidPos()) > MAID_POSITION_EPSILON_SQR) {
            Vec3 pos = session.lockedMaidPos();
            maid.moveTo(pos.x, pos.y, pos.z, session.lockedMaidYaw(), 0.0F);
        }
        stabilizeGroundState(maid, session.lockedMaidPos());
        maid.setYRot(session.lockedMaidYaw());
        maid.setYBodyRot(session.lockedMaidYaw());

        if (!PetHeadManager.isPetHeadAnimating(maid)) {
            maid.setYHeadRot(session.lockedMaidYaw());
            /*
             * 小女仆比玩家矮很多，站立锁定时如果继续强制 pitch=0，
             * 视觉上就会像“只把身体转向玩家，但眼睛仍然平视前方”。
             *
             * 这里按双方眼睛高度动态计算头部俯仰：
             * - 玩家眼睛高于小女仆眼睛时，pitch 为负值，表现为抬头；
             * - 夹角做了轻微限制，避免距离很近或地形高度差导致头部过度折叠。
             */
            maid.setXRot(computePitchTowardsEyes(maid, player));
        }
    }

    private static void stabilizeGroundState(EntityMaid maid, Vec3 lockedPos) {
        maid.setOnGround(true);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
        maid.xo = lockedPos.x;
        maid.yo = lockedPos.y;
        maid.zo = lockedPos.z;
        maid.xOld = lockedPos.x;
        maid.yOld = lockedPos.y;
        maid.zOld = lockedPos.z;
    }

    private static float computeYawTowards(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
    }

    private static float computePitchTowardsEyes(EntityMaid maid, ServerPlayer player) {
        Vec3 maidEye = maid.getEyePosition();
        Vec3 playerEye = player.getEyePosition();
        double dx = playerEye.x - maidEye.x;
        double dz = playerEye.z - maidEye.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double dy = playerEye.y - maidEye.y;
        float pitch = (float) (-(Mth.atan2(dy, horizontalDistance) * Mth.RAD_TO_DEG));
        return Mth.clamp(pitch, -45.0F, 25.0F);
    }

    private static Vec3 computeLockedMaidPos(Vec3 playerPos, float playerYaw, double hugDistance) {
        float yawRad = playerYaw * Mth.DEG_TO_RAD;
        double offsetX = -Mth.sin(yawRad) * hugDistance;
        double offsetZ = Mth.cos(yawRad) * hugDistance;
        return new Vec3(playerPos.x + offsetX, playerPos.y, playerPos.z + offsetZ);
    }

    private static Vec3 resolveGroundedMaidPosition(Level level, Vec3 originalPos) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(originalPos).mutable();
        for (int i = 0; i < 6; i++) {
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                double topY = cursor.getY() + shape.max(Direction.Axis.Y);
                return new Vec3(originalPos.x, topY, originalPos.z);
            }
            cursor.move(Direction.DOWN);
        }
        return originalPos;
    }

    private static boolean isValidInteractionPair(ServerPlayer player, @Nullable EntityMaid maid, ChildInteractionSession session) {
        if (maid == null || !maid.isAlive() || !player.isAlive()) {
            return false;
        }
        if (!maid.getUUID().equals(session.maidUuid()) || !player.getUUID().equals(session.playerUuid())) {
            return false;
        }
        if (!maid.isOwnedBy(player) || !MaidChildEntity.shouldStayChild(maid) || maid.isMaidInSittingPose()) {
            return false;
        }
        if (player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isSpectator()) {
            return false;
        }
        if (maid.isPassenger()) {
            return false;
        }
        return maid.distanceToSqr(player) <= BREAK_DISTANCE_SQR;
    }

    private static void syncInteractionState(ServerPlayer player, @Nullable EntityMaid maid) {
        ModNetworking.sendChildInteractionState(
                player,
                new ChildInteractionStateSyncPayload(player.getUUID(), maid == null ? null : maid.getUUID())
        );
    }

    @Nullable
    public static EntityMaid getInteractingMaid(ServerPlayer player) {
        ChildInteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        return session == null ? null : findMaidByUuid(player.serverLevel(), session.maidUuid());
    }

    @Nullable
    private static EntityMaid resolveTargetMaid(ServerPlayer player, @Nullable UUID maidUuid) {
        if (maidUuid != null) {
            Entity entity = player.serverLevel().getEntity(maidUuid);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }

        EntityMaid best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (!(entity instanceof EntityMaid maid)) {
                continue;
            }
            if (!maid.isOwnedBy(player)
                    || !MaidChildEntity.shouldStayChild(maid)
                    || maid.isMaidInSittingPose()
                    || maid.isPassenger()) {
                continue;
            }
            double dist = maid.distanceToSqr(player);
            if (dist > AUTO_SELECT_RANGE_SQR || dist >= bestDist) {
                continue;
            }
            best = maid;
            bestDist = dist;
        }
        return best;
    }

    @Nullable
    private static EntityMaid findMaidByUuid(ServerLevel level, UUID maidUuid) {
        Entity entity = level.getEntity(maidUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    /**
     * 小女仆站立锁定会话。
     *
     * <p>这里没有 hugging 字段，因为小女仆面板只需要“站立锁定 + UI”，
     * 举高高、摸头、让大女仆抱起等动作都从面板选项单独触发。
     */
    private record ChildInteractionSession(
            UUID playerUuid,
            UUID maidUuid,
            Vec3 lockedPlayerPos,
            float lockedPlayerYaw,
            Vec3 lockedMaidPos,
            float lockedMaidYaw
    ) {
        private ChildInteractionSession withGroundedMaidPos(Vec3 groundedPos) {
            return new ChildInteractionSession(playerUuid, maidUuid, lockedPlayerPos, lockedPlayerYaw, groundedPos, lockedMaidYaw);
        }
    }
}
