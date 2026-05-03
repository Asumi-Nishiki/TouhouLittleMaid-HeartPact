package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.HugStateSyncPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 女仆亲密交互会话管理器。
 *
 * <p>这次的核心重构，就是把旧版“只有拥抱态”拆成两层：
 * 1. 交互会话：玩家与女仆站立锁定、面对面、UI 打开、目标固定；
 * 2. 拥抱姿态：在同一个交互会话内部，额外切到拥抱动作与拥抱相机。
 *
 * <p>这样做之后，整条链路会清晰很多：
 * - 进交互时先进入站立锁定，不再一上来就强制拥抱；
 * - 面板里的“拥抱 / 放开女仆”只切换姿态，不会把整个会话直接关掉；
 * - YSM / GeckoLib / 玩家手臂姿态这些渲染桥，继续只认“真正的拥抱态”；
 * - UI、目标锁定、剧情分支，则认“交互会话是否存在”。
 */
@Mod.EventBusSubscriber(modid = com.example.maidmarriage.MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidHugManager {
    private static final double AUTO_SELECT_RANGE_SQR = 16 * 16;
    private static final double START_DISTANCE_SQR = 2.25D * 2.25D;
    private static final double BREAK_DISTANCE_SQR = 3.0D * 3.0D;
    private static final String TAG_PLAYER_HUG_DISTANCE = "maidmarriage_player_hug_distance";
    private static final double PLAYER_POSITION_EPSILON_SQR = 0.0004D;
    private static final double MAID_POSITION_EPSILON_SQR = 0.0025D;
    private static final int HUG_FAVORABILITY_GAIN = 1;
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;

    /**
     * 服务端：一个玩家当前最多只维护一份亲密交互会话。
     */
    private static final Map<UUID, InteractionSession> PLAYER_TO_SESSION = new ConcurrentHashMap<>();

    /**
     * 服务端：反查一只女仆当前被哪个玩家锁定。
     */
    private static final Map<UUID, UUID> MAID_TO_PLAYER = new ConcurrentHashMap<>();

    /**
     * 客户端：当前同步到本地的“玩家 -> 交互女仆”映射。
     */
    private static final Map<UUID, UUID> CLIENT_PLAYER_TO_MAID = new ConcurrentHashMap<>();

    /**
     * 客户端：当前同步到本地的“女仆 -> 交互玩家”映射。
     */
    private static final Map<UUID, UUID> CLIENT_MAID_TO_PLAYER = new ConcurrentHashMap<>();

    /**
     * 客户端拥抱入场动画时长。
     *
     * <p>这个值只影响渲染层：服务端锁定与动作状态仍然是立即生效的，
     * 但模型手臂会用这段时间从普通站姿慢慢抬起、张开并进入拥抱姿态。
     */
    private static final long CLIENT_HUG_ENTER_ANIMATION_MILLIS = 650L;

    /**
     * 客户端：哪些玩家当前处于“交互中且已切到拥抱姿态”。
     *
     * <p>这里故意单独维护一份集合，而不是把“是否拥抱”塞进 UUID map 的值里，
     * 因为渲染层最常问的问题就是：“这个玩家/女仆现在是不是拥抱态？”
     * 用集合判断会更直接，调用点也更清楚。
     */
    private static final Set<UUID> CLIENT_HUGGING_PLAYERS = ConcurrentHashMap.newKeySet();

    /**
     * 客户端：记录每个玩家最近一次进入拥抱姿态的时间。
     *
     * <p>渲染 mixin 会读取这份时间戳，计算 0~1 的入场进度。
     * 这样我们不用额外加同步包字段，也不会影响服务端逻辑。
     */
    private static final Map<UUID, Long> CLIENT_HUGGING_START_MILLIS = new ConcurrentHashMap<>();

    /**
     * 客户端：记录每个玩家最近一次从拥抱姿态退回站立锁定的时间。
     *
     * <p>放开女仆时服务端会立即把 hugging=false 同步下来，
     * 但客户端不能因此立刻停止渲染拥抱骨骼，否则手臂会瞬间弹回去。
     * 这里保留一小段退出动画时间，让姿态按入场动画反向收回。
     */
    private static final Map<UUID, Long> CLIENT_HUGGING_EXIT_START_MILLIS = new ConcurrentHashMap<>();

    private MaidHugManager() {
    }

    public static double resolveHugDistance(Player player) {
        if (player == null || player.level().isClientSide()) {
            return ModConfigs.hugDistance();
        }
        return player.getPersistentData().contains(TAG_PLAYER_HUG_DISTANCE)
                ? player.getPersistentData().getDouble(TAG_PLAYER_HUG_DISTANCE)
                : ModConfigs.hugDistance();
    }

    public static void updatePlayerHugSettings(ServerPlayer player, double hugDistance) {
        player.getPersistentData().putDouble(TAG_PLAYER_HUG_DISTANCE, Math.max(0.10D, Math.min(2.00D, hugDistance)));
    }

    /**
     * Ctrl+拥抱键的入口。
     *
     * <p>现在它不再直接切换“拥抱姿态”，而是负责整个会话的开/关：
     * - 当前没有会话 -> 进入站立锁定；
     * - 当前已有会话 -> 结束交互。
     *
     * <p>真正的“拥抱 / 放开女仆”动作，改由面板里的专用按钮通过
     * {@link #handleHugPoseToggle(ServerPlayer, UUID)} 触发。
     */
    public static void handleInteractionToggle(ServerPlayer player, @Nullable UUID maidUuid) {
        InteractionSession existing = PLAYER_TO_SESSION.get(player.getUUID());
        if (existing != null) {
            stopInteraction(player, findMaidByUuid(player.serverLevel(), existing.maidUuid()), true);
            return;
        }

        EntityMaid maid = resolveTargetMaid(player, maidUuid);
        if (maid == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.interaction.no_target"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.interaction.need_owner", maid.getDisplayName()));
            return;
        }
        if (MaidChildEntity.shouldStayChild(maid)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.interaction.need_adult", maid.getDisplayName()));
            return;
        }
        if (maid.isMaidInSittingPose()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.interaction.need_standing", maid.getDisplayName()));
            return;
        }
        if (maid.isPassenger() || MAID_TO_PLAYER.containsKey(maid.getUUID())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.interaction.already_busy", maid.getDisplayName()));
            return;
        }
        if (maid.distanceToSqr(player) > START_DISTANCE_SQR) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.interaction.no_target"));
            return;
        }

        startInteraction(player, maid);
    }

    /**
     * 面板里的“拥抱 / 放开女仆”入口。
     *
     * <p>这里不会打开或关闭整个交互会话，只在当前会话内部切换 hugging 标记。
     * 也正因为这样，UI 才能在同一个界面里持续显示，只是按钮文案与动作分支发生切换。
     */
    public static void handleHugPoseToggle(ServerPlayer player, @Nullable UUID maidUuid) {
        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        if (session == null) {
            return;
        }

        EntityMaid maid = findMaidByUuid(player.serverLevel(), session.maidUuid());
        if (!isValidInteractionPair(player, maid, session)) {
            stopInteraction(player, maid, false);
            return;
        }

        /*
         * UI 传进来的 maidUuid 只作为安全校验：
         * 当前面板理论上应该始终操作会话里的这只女仆，
         * 如果客户端传了别的 UUID，就直接忽略，避免错误切到别的目标。
         */
        if (maidUuid != null && !maidUuid.equals(session.maidUuid())) {
            return;
        }

        boolean nextHugging = !session.hugging();
        setHugging(player, maid, nextHugging, true);
        if (nextHugging) {
            MaidMoodManager.markMeaningfulInteraction(maid);
        }
    }

    /**
     * 判断“这只女仆与这个玩家是否正处于交互会话中”。
     *
     * <p>这层是新的基础状态：
     * UI 打开、目标固定、站立亲吻、站立摸头，都应优先认这层。
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

    /**
     * 判断“这只女仆与这个玩家是否正处于真正的拥抱姿态”。
     *
     * <p>渲染桥、玩家手臂姿态、YSM 强制改骨、拥抱内专用动作，都应该继续只认这一层。
     */
    public static boolean isHugState(EntityMaid maid, @Nullable Player player) {
        if (!isInteractionState(maid, player) || player == null) {
            return false;
        }
        if (maid.level().isClientSide()) {
            return CLIENT_HUGGING_PLAYERS.contains(player.getUUID());
        }
        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        return session != null
                && session.hugging()
                && maid.getUUID().equals(session.maidUuid());
    }

    public static boolean isClientPlayerInteracting(Player player) {
        return player != null && CLIENT_PLAYER_TO_MAID.containsKey(player.getUUID());
    }

    public static boolean isClientPlayerHugging(Player player) {
        return player != null
                && CLIENT_PLAYER_TO_MAID.containsKey(player.getUUID())
                && CLIENT_HUGGING_PLAYERS.contains(player.getUUID());
    }

    /**
     * 当前客户端渲染帧里，某个玩家进入拥抱姿态的平滑进度。
     *
     * <p>返回值范围：
     * - 0：刚开始进入拥抱；
     * - 1：已经完全进入拥抱姿态。
     */
    public static float clientHugEnterProgress(@Nullable Player player) {
        if (player == null || !isClientPlayerHugging(player)) {
            return 0.0F;
        }
        long startMillis = CLIENT_HUGGING_START_MILLIS.getOrDefault(player.getUUID(), System.currentTimeMillis());
        float rawProgress = (System.currentTimeMillis() - startMillis) / (float) CLIENT_HUG_ENTER_ANIMATION_MILLIS;
        rawProgress = Mth.clamp(rawProgress, 0.0F, 1.0F);
        return rawProgress * rawProgress * (3.0F - 2.0F * rawProgress);
    }

    /**
     * 当前客户端渲染帧里，拥抱姿态整体还应该保留多少。
     *
     * <p>进入拥抱时：0 -> 1；
     * 放开女仆时：1 -> 0。
     * 渲染层统一读这个值，就能天然获得“进入”和“退出”的双向动画。
     */
    public static float clientHugPoseProgress(@Nullable Player player) {
        if (player == null) {
            return 0.0F;
        }
        if (isClientPlayerHugging(player)) {
            return clientHugEnterProgress(player);
        }

        Long exitStartMillis = CLIENT_HUGGING_EXIT_START_MILLIS.get(player.getUUID());
        if (exitStartMillis == null) {
            return 0.0F;
        }
        float rawProgress = 1.0F - (System.currentTimeMillis() - exitStartMillis) / (float) CLIENT_HUG_ENTER_ANIMATION_MILLIS;
        rawProgress = Mth.clamp(rawProgress, 0.0F, 1.0F);
        if (rawProgress <= 0.0F) {
            CLIENT_HUGGING_EXIT_START_MILLIS.remove(player.getUUID());
            return 0.0F;
        }
        return rawProgress * rawProgress * (3.0F - 2.0F * rawProgress);
    }

    public static boolean isClientPlayerHugPoseVisible(@Nullable Player player) {
        return clientHugPoseProgress(player) > 0.0F;
    }

    /**
     * 取得当前交互会话里的玩家。
     *
     * <p>这里不要求当前一定是拥抱态，因为不少逻辑只是想知道“这只女仆当前正和谁在互动”。
     */
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
     * 保留旧方法名给现有拥抱渲染桥复用。
     *
     * <p>它现在返回的是“交互玩家”，至于是不是拥抱态，调用方再用
     * {@link #isHugState(EntityMaid, Player)} 判断。
     */
    @Nullable
    public static Player getHugPlayer(EntityMaid maid) {
        return getInteractionPlayer(maid);
    }

    /**
     * 处理服务端发来的客户端同步。
     *
     * <p>同步包现在包含两层信息：
     * - 当前有没有交互目标 maidUuid；
     * - 当前这份交互是否处于拥抱姿态 hugging。
     */
    public static void handleClientHugStateSync(UUID playerUuid, @Nullable UUID maidUuid, boolean hugging) {
        if (maidUuid == null) {
            UUID oldMaid = CLIENT_PLAYER_TO_MAID.remove(playerUuid);
            if (oldMaid != null) {
                CLIENT_MAID_TO_PLAYER.remove(oldMaid);
            }
            CLIENT_HUGGING_PLAYERS.remove(playerUuid);
            CLIENT_HUGGING_START_MILLIS.remove(playerUuid);
            CLIENT_HUGGING_EXIT_START_MILLIS.remove(playerUuid);
            return;
        }

        boolean wasHugging = CLIENT_HUGGING_PLAYERS.contains(playerUuid);
        UUID oldMaid = CLIENT_PLAYER_TO_MAID.put(playerUuid, maidUuid);
        if (oldMaid != null && !oldMaid.equals(maidUuid)) {
            CLIENT_MAID_TO_PLAYER.remove(oldMaid);
        }
        CLIENT_MAID_TO_PLAYER.put(maidUuid, playerUuid);
        if (hugging) {
            CLIENT_HUGGING_PLAYERS.add(playerUuid);
            CLIENT_HUGGING_EXIT_START_MILLIS.remove(playerUuid);
            if (!wasHugging || oldMaid == null || !oldMaid.equals(maidUuid)) {
                CLIENT_HUGGING_START_MILLIS.put(playerUuid, System.currentTimeMillis());
            }
        } else {
            CLIENT_HUGGING_PLAYERS.remove(playerUuid);
            CLIENT_HUGGING_START_MILLIS.remove(playerUuid);
            if (wasHugging) {
                CLIENT_HUGGING_EXIT_START_MILLIS.put(playerUuid, System.currentTimeMillis());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        if (session == null) {
            return;
        }

        EntityMaid maid = findMaidByUuid(player.serverLevel(), session.maidUuid());
        if (!isValidInteractionPair(player, maid, session)) {
            stopInteraction(player, maid, false);
            return;
        }

        maintainInteractionPose(player, maid, session);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopInteraction(player, getInteractingMaid(player), false);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopInteraction(player, getInteractingMaid(player), false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopInteraction(player, getInteractingMaid(player), false);
        }
    }

    /**
     * 创建新的站立交互会话。
     *
     * <p>注意这里故意不再发送“拥抱开始成功”的那套提示，
     * 因为此时我们只是进入站立锁定，不应该把站立交互误报成已经拥抱。
     */
    private static void startInteraction(ServerPlayer player, EntityMaid maid) {
        MaidMoodManager.ensureDailyMood(maid);
        InteractionSession session = createSession(player, maid, false);
        PLAYER_TO_SESSION.put(player.getUUID(), session);
        MAID_TO_PLAYER.put(maid.getUUID(), player.getUUID());
        maintainInteractionPose(player, maid, session);
        syncInteractionState(player, maid, false);
    }

    /**
     * 结束整份交互会话。
     *
     * <p>无论当前是站立锁定还是拥抱姿态，只要会话结束，就统一清空：
     * - 位置锁定；
     * - 客户端同步；
     * - YSM 拥抱姿态桥；
     * - 女仆的静止/朝向强控。
     */
    private static void stopInteraction(ServerPlayer player, @Nullable EntityMaid maid, boolean manual) {
        InteractionSession session = PLAYER_TO_SESSION.remove(player.getUUID());
        if (session != null) {
            MAID_TO_PLAYER.remove(session.maidUuid());
        }
        if (maid == null && session != null) {
            maid = findMaidByUuid(player.serverLevel(), session.maidUuid());
        }

        if (maid != null) {
            YsmHugAnimationBridge.stopIfAvailable(maid);
            maid.setPose(Pose.STANDING);
            maid.setInSittingPose(false);
            maid.setDeltaMovement(Vec3.ZERO);
            maid.setTarget(null);
            maid.getNavigation().stop();
            maid.setYBodyRot(maid.getYRot());
            maid.setYHeadRot(maid.getYRot());
        }

        syncInteractionState(player, null, false);
    }

    /**
     * 在现有交互会话内部切换 hugging 标记。
     *
     * <p>这里只改“姿态层”，不改“会话层”，所以 UI 不会被关掉。
     */
    private static void setHugging(ServerPlayer player, EntityMaid maid, boolean hugging, boolean manual) {
        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        if (session == null || !session.maidUuid().equals(maid.getUUID())) {
            return;
        }
        if (session.hugging() == hugging) {
            return;
        }

        InteractionSession updated = session.withHugging(hugging);
        PLAYER_TO_SESSION.put(player.getUUID(), updated);
        maintainInteractionPose(player, maid, updated);

        if (hugging) {
            YsmHugAnimationBridge.playHugIfAvailable(maid);
            player.serverLevel().sendParticles(
                    ParticleTypes.HEART,
                    maid.getX(), maid.getY(1.0D), maid.getZ(),
                    8, 0.18D, 0.25D, 0.18D, 0.01D
            );
            player.serverLevel().playSound(
                    null, player.blockPosition(),
                    SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
                    SoundSource.PLAYERS, 0.7F, 0.95F
            );
            MaidMoodManager.applyLimitedInteractionMoodGain(maid, MaidMoodManager.EVENT_HUG);
            MaidMoodManager.applyInteractionFavorabilityGain(maid, HUG_FAVORABILITY_GAIN, FAVORABILITY_CAP);
            if (manual) {
                RomanceSleepManager.speakSingleLineWithChat(maid, "dialogue.maidmarriage.hug.start");
                player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.hug.start_success", maid.getDisplayName()));
            }
        } else {
            YsmHugAnimationBridge.stopIfAvailable(maid);
        }

        syncInteractionState(player, maid, hugging);
    }

    private static InteractionSession createSession(ServerPlayer player, EntityMaid maid, boolean hugging) {
        float playerYaw = player.getYRot();
        Vec3 lockedPlayerPos = player.position();
        Vec3 lockedMaidPos = resolveGroundedMaidPosition(
                maid.level(),
                computeLockedMaidPos(lockedPlayerPos, playerYaw, resolveHugDistance(player))
        );
        float maidYaw = computeYawTowards(lockedMaidPos, lockedPlayerPos);
        return new InteractionSession(player.getUUID(), maid.getUUID(), lockedPlayerPos, playerYaw, lockedMaidPos, maidYaw, hugging);
    }

    /**
     * 服务端每 tick 维持会话的“面对面站立锁定”。
     *
     * <p>这里无论是否拥抱都要执行，因为“站立锁定”本来就是新的基础层。
     * 拥抱姿态只是渲染层和动作层叠上去的额外状态，不会改变这层的锁位职责。
     */
    private static void maintainInteractionPose(ServerPlayer player, EntityMaid maid, InteractionSession session) {
        lockPlayer(player, session);
        lockMaid(maid, session.withGroundedMaidPos(resolveGroundedMaidPosition(maid.level(), session.lockedMaidPos())));
    }

    private static void lockPlayer(ServerPlayer player, InteractionSession session) {
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

    private static void lockMaid(EntityMaid maid, InteractionSession session) {
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

        /*
         * 这里仍然保留“摸头时暂时让出头部控制权”的逻辑。
         * 站立锁定与拥抱锁定都属于同一个交互会话，因此摸头动画在两种模式下都可能发生。
         */
        if (!PetHeadManager.isPetHeadAnimating(maid)) {
            maid.setYHeadRot(session.lockedMaidYaw());
            maid.setXRot(0.0F);
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

    private static boolean isValidInteractionPair(ServerPlayer player, @Nullable EntityMaid maid, InteractionSession session) {
        if (maid == null || !maid.isAlive() || !player.isAlive()) {
            return false;
        }
        if (!maid.getUUID().equals(session.maidUuid()) || !player.getUUID().equals(session.playerUuid())) {
            return false;
        }
        if (!maid.isOwnedBy(player) || MaidChildEntity.shouldStayChild(maid) || maid.isMaidInSittingPose()) {
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

    /**
     * 同步整份交互会话状态到客户端。
     *
     * <p>虽然包名还叫 HugStateSync，但语义已经升级成：
     * - maidUuid：当前交互对象；
     * - hugging：当前是否切到拥抱姿态。
     */
    private static void syncInteractionState(ServerPlayer player, @Nullable EntityMaid maid, boolean hugging) {
        ModNetworking.sendHugState(player, new HugStateSyncPayload(
                player.getUUID(),
                maid == null ? null : maid.getUUID(),
                hugging,
                requiresChildNameBeforeNormalInteraction(maid)
        ));
    }

    /**
     * 服务端权威判断：这位妈妈怀里的小女仆是否还没经过正式命名 UI。
     *
     * <p>这个结果会随互动会话同步给客户端，避免客户端因为乘客/TaskData 尚未同步而漏掉入口剧情。
     */
    private static boolean requiresChildNameBeforeNormalInteraction(@Nullable EntityMaid maid) {
        if (maid == null || MaidChildEntity.shouldStayChild(maid)) {
            return false;
        }
        for (Entity passenger : maid.getPassengers()) {
            if (passenger instanceof EntityMaid child
                    && MaidChildEntity.shouldStayChild(child)
                    && MaidChildEntity.isMotherOfChild(child, maid)
                    && !MaidChildEntity.hasConfirmedChildName(child)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static EntityMaid getInteractingMaid(ServerPlayer player) {
        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        return session == null ? null : findMaidByUuid(player.serverLevel(), session.maidUuid());
    }

    @Nullable
    public static UUID getInteractingMaidUuid(ServerPlayer player) {
        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        return session == null ? null : session.maidUuid();
    }

    /**
     * 保留旧接口给拥抱专用逻辑使用。
     *
     * <p>只有会话存在且 hugging=true 时，才返回这只女仆。
     */
    @Nullable
    public static EntityMaid getHuggedMaid(ServerPlayer player) {
        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        if (session == null || !session.hugging()) {
            return null;
        }
        return findMaidByUuid(player.serverLevel(), session.maidUuid());
    }

    @Nullable
    public static UUID getHuggedMaidUuid(ServerPlayer player) {
        InteractionSession session = PLAYER_TO_SESSION.get(player.getUUID());
        return session == null || !session.hugging() ? null : session.maidUuid();
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
                    || MaidChildEntity.shouldStayChild(maid)
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
     * 单份亲密交互会话。
     *
     * @param hugging false 表示只是站立锁定；true 表示在同一锁定里额外切到了拥抱姿态
     */
    private record InteractionSession(
            UUID playerUuid,
            UUID maidUuid,
            Vec3 lockedPlayerPos,
            float lockedPlayerYaw,
            Vec3 lockedMaidPos,
            float lockedMaidYaw,
            boolean hugging
    ) {
        private InteractionSession withGroundedMaidPos(Vec3 groundedPos) {
            return new InteractionSession(playerUuid, maidUuid, lockedPlayerPos, lockedPlayerYaw, groundedPos, lockedMaidYaw, hugging);
        }

        private InteractionSession withHugging(boolean newHugging) {
            return new InteractionSession(playerUuid, maidUuid, lockedPlayerPos, lockedPlayerYaw, lockedMaidPos, lockedMaidYaw, newHugging);
        }
    }
}
