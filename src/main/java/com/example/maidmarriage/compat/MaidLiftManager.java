package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.entity.LiftProxyEntity;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModEntities;
import com.example.maidmarriage.config.ModConfigs;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 小女仆“举高高”状态管理器。
 *
 * <p>这一版只处理我们自己按 {@code O} 键触发的举高高，不会改动：
 * <ul>
 *     <li>TLM 原版的正常骑乘；</li>
 *     <li>原版 / 其他模组的马鞍、载具、公主抱逻辑；</li>
 *     <li>玩家手动造成的普通乘骑关系。</li>
 * </ul>
 *
 * <p>核心做法是生成一个不可见的代理实体：
 * <pre>
 * 小女仆 -> LiftProxyEntity -> 玩家
 * </pre>
 * 只有 O 键举高高才会生成这条链，其余情况完全不走这里。
 */
public final class MaidLiftManager {
    private static final double PUT_DOWN_FORWARD_OFFSET = 0.85D;
    private static final double AUTO_SELECT_RANGE_SQR = 20 * 20;
    private static final String TAG_PLAYER_LIFT_HEIGHT = "maidmarriage_player_lift_height";

    /**
     * 服务端权威映射：玩家 -> 当前被举起的小女仆。
     */
    private static final Map<UUID, UUID> PLAYER_TO_MAID = new ConcurrentHashMap<>();

    /**
     * 服务端权威映射：小女仆 -> 当前举着她的玩家。
     */
    private static final Map<UUID, UUID> MAID_TO_PLAYER = new ConcurrentHashMap<>();

    /**
     * 服务端权威映射：玩家 -> 当前用于举高高的代理实体。
     */
    private static final Map<UUID, UUID> PLAYER_TO_PROXY = new ConcurrentHashMap<>();

    /**
     * 客户端同步映射：玩家 -> 女仆。
     */
    private static final Map<UUID, UUID> CLIENT_PLAYER_TO_MAID = new ConcurrentHashMap<>();

    /**
     * 客户端同步映射：玩家 -> 代理实体。
     */
    private static final Map<UUID, UUID> CLIENT_PLAYER_TO_PROXY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> CLIENT_PLAYER_TO_LIFT_HEIGHT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_ELYTRA_FLIGHT_TALK_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_ELYTRA_FLIGHT_MOOD_TICK = new ConcurrentHashMap<>();

    /**
     * 记录举起前的 rideable 状态，放下时恢复。
     */
    private static final Map<UUID, Boolean> MAID_PREV_RIDEABLE = new ConcurrentHashMap<>();

    /**
     * 记录举起前的 home mode 状态，放下时恢复。
     */
    private static final Map<UUID, Boolean> MAID_PREV_HOME_MODE = new ConcurrentHashMap<>();

    private MaidLiftManager() {
    }

    /**
     * 判断这只女仆是否正处于“本模组的举高高状态”。
     *
     * <p>这里不再依赖“女仆直接骑玩家”，而是检查：
     * <pre>
     * 女仆 -> LiftProxyEntity -> 玩家
     * </pre>
     * 只有形成这条链时，才算我们的举高高状态。
     */
    public static boolean isLiftState(EntityMaid maid, Player player) {
        if (player == null) {
            return false;
        }
        if (!MaidChildEntity.shouldStayChild(maid)) {
            return false;
        }
        Player carrier = getLiftPlayer(maid);
        if (carrier == null || !carrier.getUUID().equals(player.getUUID())) {
            return false;
        }

        if (player.level().isClientSide()) {
            return true;
        }

        UUID mappedPlayer = MAID_TO_PLAYER.get(maid.getUUID());
        return mappedPlayer == null || mappedPlayer.equals(player.getUUID());
    }

    /**
     * 客户端本地玩家当前是否正处于“举高高”状态。
     *
     * <p>这里直接读取客户端同步映射，不依赖准星目标，
     * 方便把“放下小女仆”挂到任意快捷键上。
     */
    public static boolean isLocalPlayerLifting(@Nullable Player player) {
        return player != null && CLIENT_PLAYER_TO_MAID.containsKey(player.getUUID());
    }

    /**
     * 服务端/客户端通用判断：这个玩家当前是否正处于“举高高”状态。
     */
    public static boolean isPlayerLifting(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return player.level().isClientSide()
                ? CLIENT_PLAYER_TO_MAID.containsKey(player.getUUID())
                : PLAYER_TO_MAID.containsKey(player.getUUID());
    }

    /**
     * 服务端判断：这只小女仆是否正处于“玩家举高高”的代理骑乘链里。
     *
     * <p>窒息伤害拦截会用到它。举高高位置贴近玩家头顶，
     * 在低矮洞穴、树叶、雪层或起飞贴墙时容易短暂被方块判定挤到，
     * 这里把这类伤害视为姿态系统带来的误伤。
     */
    public static boolean isLiftedMaid(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return false;
        }
        return MAID_TO_PLAYER.containsKey(maid.getUUID());
    }

    /**
     * 客户端本地玩家当前举着的小女仆 UUID。
     */
    @Nullable
    public static UUID getLocalLiftedMaidUuid(@Nullable Player player) {
        return player == null ? null : CLIENT_PLAYER_TO_MAID.get(player.getUUID());
    }

    /**
     * 服务端/客户端通用读取：返回这个玩家当前举着的小女仆 UUID。
     */
    @Nullable
    public static UUID getLiftedMaidUuid(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        return player.level().isClientSide()
                ? CLIENT_PLAYER_TO_MAID.get(player.getUUID())
                : PLAYER_TO_MAID.get(player.getUUID());
    }

    /**
     * 返回当前举着这只女仆的玩家。
     *
     * <p>如果当前不是代理实体链，返回 {@code null}，这样原版骑乘不会误判成举高高。
     */
    @Nullable
    public static Player getLiftPlayer(EntityMaid maid) {
        if (!(maid.getVehicle() instanceof LiftProxyEntity proxy)) {
            return null;
        }
        return proxy.getCarrierPlayer();
    }

    public static double resolveLiftHeight(Player player) {
        if (player.level().isClientSide()) {
            return CLIENT_PLAYER_TO_LIFT_HEIGHT.getOrDefault(player.getUUID(), ModConfigs.liftHeight());
        }
        CompoundTag data = player.getPersistentData();
        if (data.contains(TAG_PLAYER_LIFT_HEIGHT)) {
            return data.getDouble(TAG_PLAYER_LIFT_HEIGHT);
        }
        return ModConfigs.liftHeight();
    }

    public static void updatePlayerLiftSettings(ServerPlayer player, double liftHeight) {
        player.getPersistentData().putDouble(TAG_PLAYER_LIFT_HEIGHT, Math.max(-0.20D, Math.min(1.50D, liftHeight)));
    }

    /**
     * 保留空实现，避免改动已有调用点。
     *
     * <p>旧版这里做过坐标调试统计，现在代理实体自己负责定位，已经不再需要。
     */
    public static void recordMixinPosition(Player player, boolean clientSide, double x, double y, double z) {
    }

    /**
     * 处理一次举高高切换。
     *
     * <p>未举起时举起，已举起时放下。
     */
    public static void handleLiftToggle(ServerPlayer player, @Nullable UUID maidUuid) {
        UUID liftedMaidUuid = PLAYER_TO_MAID.get(player.getUUID());
        if (liftedMaidUuid != null) {
            putDown(player, findMaidByUuid(player.serverLevel(), liftedMaidUuid));
            return;
        }

        EntityMaid maid = resolveTargetMaid(player, maidUuid);
        if (maid == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.no_target"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.need_owner", maid.getDisplayName()));
            return;
        }
        if (!MaidChildEntity.shouldStayChild(maid)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.need_child", maid.getDisplayName()));
            return;
        }
        if (MAID_TO_PLAYER.containsKey(maid.getUUID())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.already_lifted", maid.getDisplayName()));
            return;
        }

        liftUp(player, maid);
    }

    /**
     * 服务端每 tick 维持代理实体乘骑链。
     *
     * <p>如果链断了，会尝试重建；若条件不满足，则自动放下。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID maidUuid = PLAYER_TO_MAID.get(player.getUUID());
        if (maidUuid == null) {
            return;
        }

        EntityMaid maid = findMaidByUuid(player.serverLevel(), maidUuid);
        if (maid == null || !maid.isAlive() || !maid.isOwnedBy(player) || !MaidChildEntity.shouldStayChild(maid)) {
            putDown(player, maid);
            return;
        }

        handleElytraFlightTick(player, maid);

        LiftProxyEntity proxy = findProxyForPlayer(player);
        if (!ensureLiftChain(player, maid, proxy)) {
            putDown(player, maid);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.mount_failed", maid.getDisplayName()));
        }
    }

    /**
     * 鞘翅滑翔时保留举高高状态。
     *
     * <p>玩家带着小女仆飞起来本身是很有画面感的互动，
     * 所以正式版不再自动放下；这里只做低频反馈：
     * 偶尔让小女仆说一句在天上的感受，并给一点心情奖励。
     */
    private static void handleElytraFlightTick(ServerPlayer player, EntityMaid maid) {
        if (!player.isFallFlying()) {
            return;
        }

        long now = player.serverLevel().getGameTime();
        UUID maidUuid = maid.getUUID();

        long nextMoodTick = NEXT_ELYTRA_FLIGHT_MOOD_TICK.getOrDefault(maidUuid, 0L);
        if (now >= nextMoodTick) {
            MaidMoodManager.addMood(maid, 1);
            NEXT_ELYTRA_FLIGHT_MOOD_TICK.put(maidUuid, now + 1200L);
            player.serverLevel().sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    maid.getX(), maid.getY(0.8D), maid.getZ(),
                    4, 0.18D, 0.18D, 0.18D, 0.01D
            );
        }

        long nextTalkTick = NEXT_ELYTRA_FLIGHT_TALK_TICK.getOrDefault(maidUuid, 0L);
        if (now < nextTalkTick) {
            return;
        }
        int lineIndex = player.getRandom().nextInt(5) + 1;
        RomanceSleepManager.speakSingleLineWithChat(maid, "dialogue.maidmarriage.lift.fly." + lineIndex);
        NEXT_ELYTRA_FLIGHT_TALK_TICK.put(maidUuid, now + 2400L);
    }

    /**
     * 正式举起小女仆。
     *
     * <p>这里只会创建一次新的代理实体，不会碰普通骑乘逻辑。
     */
    private static void liftUp(ServerPlayer player, EntityMaid maid) {
        if (maid.isPassenger()) {
            maid.stopRiding();
        }
        maid.setInSittingPose(false);

        boolean prevRideable = maid.isRideable();
        MAID_PREV_RIDEABLE.put(maid.getUUID(), prevRideable);
        if (!prevRideable) {
            maid.setRideable(true);
        }

        /*
         * 先缓存举起前 home mode，仅用于“挂载失败时回滚”。
         *
         * 如果举高高成功，后面会直接清掉这个缓存，并让小女仆永久切到跟随模式；
         * 这样放下后不会恢复回家模式，也就不会因为 home mode 锚点导致瞬移回去。
         */
        boolean prevHomeMode = maid.isHomeModeEnable();
        MAID_PREV_HOME_MODE.put(maid.getUUID(), prevHomeMode);

        LiftProxyEntity proxy = createLiftProxy(player);
        if (!ensureLiftChain(player, maid, proxy)) {
            discardProxy(proxy);
            restoreRideableIfNeeded(maid);
            restoreHomeModeIfNeeded(maid);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.mount_failed", maid.getDisplayName()));
            return;
        }

        PLAYER_TO_MAID.put(player.getUUID(), maid.getUUID());
        MAID_TO_PLAYER.put(maid.getUUID(), player.getUUID());
        PLAYER_TO_PROXY.put(player.getUUID(), proxy.getUUID());

        /*
         * 举高高成功后，小女仆直接切到跟随模式（homeMode = false），且放下后不再恢复。
         *
         * TLM 的 home mode 会让女仆记住家/工作点；如果举高高后再恢复 home mode，
         * 放下时 AI 会立刻把她拉回原本锚点，表现成“瞬移回去”。
         */
        MAID_PREV_HOME_MODE.remove(maid.getUUID());
        maid.setHomeModeEnable(false);
        maid.setInSittingPose(true);

        player.serverLevel().sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                maid.getX(), maid.getY(1.0D), maid.getZ(),
                6, 0.2D, 0.15D, 0.2D, 0.01D
        );
        player.serverLevel().playSound(
                null, player.blockPosition(),
                SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
                SoundSource.PLAYERS, 0.7F, 1.15F
        );
        RomanceSleepManager.speakSingleLine(maid, "dialogue.maidmarriage.lift.up");
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.up_success", maid.getDisplayName()));
        syncLiftState(player, maid, proxy);
    }

    /**
     * 放下小女仆，并恢复举起前缓存的状态。
     */
    private static void putDown(ServerPlayer player, @Nullable EntityMaid maid) {
        UUID playerId = player.getUUID();
        UUID maidId = PLAYER_TO_MAID.remove(playerId);
        UUID proxyId = PLAYER_TO_PROXY.remove(playerId);
        if (maidId != null) {
            NEXT_ELYTRA_FLIGHT_TALK_TICK.remove(maidId);
            NEXT_ELYTRA_FLIGHT_MOOD_TICK.remove(maidId);
        }
        if (maidId != null) {
            MAID_TO_PLAYER.remove(maidId);
        }

        LiftProxyEntity proxy = LiftProxyEntity.find(player.serverLevel(), proxyId);
        if (maid == null && maidId != null) {
            maid = findMaidByUuid(player.serverLevel(), maidId);
        }

        if (maid != null) {
            if (maid.isPassenger()) {
                maid.stopRiding();
            }
            maid.setInSittingPose(false);
            restoreRideableIfNeeded(maid);
            restoreHomeModeIfNeeded(maid);

            float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
            double targetX = player.getX() - Mth.sin(yawRad) * PUT_DOWN_FORWARD_OFFSET;
            double targetZ = player.getZ() + Mth.cos(yawRad) * PUT_DOWN_FORWARD_OFFSET;
            maid.moveTo(targetX, player.getY(), targetZ, player.getYRot(), maid.getXRot());

            player.serverLevel().sendParticles(
                    ParticleTypes.CLOUD,
                    maid.getX(), maid.getY(0.6D), maid.getZ(),
                    5, 0.15D, 0.1D, 0.15D, 0.01D
            );
            player.serverLevel().playSound(
                    null, player.blockPosition(),
                    SoundEvents.ALLAY_ITEM_GIVEN,
                    SoundSource.PLAYERS, 0.65F, 0.95F
            );
            RomanceSleepManager.speakSingleLine(maid, "dialogue.maidmarriage.lift.down");
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.down_success", maid.getDisplayName()));
        }

        if (proxy != null) {
            discardProxy(proxy);
        }
        syncLiftState(player, null, null);
    }

    /**
     * 玩家死亡时自动放下。
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putDown(player, getLiftedMaid(player));
        }
    }

    /**
     * 切维度时自动放下。
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putDown(player, getLiftedMaid(player));
        }
    }

    /**
     * 玩家下线时清理缓存，避免代理实体映射残留。
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        putDown(player, getLiftedMaid(player));
    }

    /**
     * 客户端同步入口保留为空实现。
     *
     * <p>当前版本完全依赖实体链自然同步，不再手动伪造客户端乘骑关系。
     */
    public static void handleClientLiftStateSync(UUID playerUuid, @Nullable UUID maidUuid, @Nullable UUID proxyUuid, double liftHeight) {
        if (!com.example.maidmarriage.client.ClientEntityLookup.hasLevel()) {
            return;
        }

        if (maidUuid == null || proxyUuid == null) {
            detachClientLiftEntities(playerUuid);
            CLIENT_PLAYER_TO_MAID.remove(playerUuid);
            CLIENT_PLAYER_TO_PROXY.remove(playerUuid);
            CLIENT_PLAYER_TO_LIFT_HEIGHT.remove(playerUuid);
            return;
        }

        CLIENT_PLAYER_TO_MAID.put(playerUuid, maidUuid);
        CLIENT_PLAYER_TO_PROXY.put(playerUuid, proxyUuid);
        CLIENT_PLAYER_TO_LIFT_HEIGHT.put(playerUuid, liftHeight);
        attachClientLiftEntities(playerUuid, maidUuid, proxyUuid);
    }

    private static boolean ensureLiftChain(ServerPlayer player, EntityMaid maid, @Nullable LiftProxyEntity proxy) {
        LiftProxyEntity actualProxy = proxy;
        if (actualProxy == null || !actualProxy.isAlive()) {
            actualProxy = createLiftProxy(player);
        }

        if (!actualProxy.isPassenger() || actualProxy.getVehicle() != player) {
            if (actualProxy.isPassenger()) {
                actualProxy.stopRiding();
            }
            if (!actualProxy.startRiding(player, true)) {
                discardProxy(actualProxy);
                return false;
            }
        }

        if (maid.isPassenger() && maid.getVehicle() != actualProxy) {
            maid.stopRiding();
        }
        if (!maid.isPassenger() || maid.getVehicle() != actualProxy) {
            if (!maid.startRiding(actualProxy, true)) {
                discardProxy(actualProxy);
                return false;
            }
        }

        PLAYER_TO_PROXY.put(player.getUUID(), actualProxy.getUUID());
        return isActuallyMountedOn(player, maid, actualProxy);
    }

    private static LiftProxyEntity createLiftProxy(ServerPlayer player) {
        LiftProxyEntity proxy = ModEntities.LIFT_PROXY.get().create(player.level());
        if (proxy == null) {
            throw new IllegalStateException("Failed to create lift proxy entity");
        }
        proxy.moveTo(player.getX(), player.getY() + player.getBbHeight(), player.getZ(), player.getYRot(), 0.0F);
        player.level().addFreshEntity(proxy);
        return proxy;
    }

    private static void syncLiftState(ServerPlayer player, @Nullable EntityMaid maid, @Nullable LiftProxyEntity proxy) {
        com.example.maidmarriage.network.ModNetworking.sendLiftState(
                player,
                new com.example.maidmarriage.network.payload.LiftStateSyncPayload(
                        player.getUUID(),
                        maid == null ? null : maid.getUUID(),
                        proxy == null ? null : proxy.getUUID(),
                        resolveLiftHeight(player)
                )
        );
    }

    private static void discardProxy(@Nullable LiftProxyEntity proxy) {
        if (proxy == null) {
            return;
        }
        if (proxy.isPassenger()) {
            proxy.stopRiding();
        }
        proxy.ejectPassengers();
        proxy.discard();
    }

    private static boolean isActuallyMountedOn(ServerPlayer player, EntityMaid maid, LiftProxyEntity proxy) {
        return proxy.isPassenger()
                && proxy.getVehicle() == player
                && maid.isPassenger()
                && maid.getVehicle() == proxy
                && player.getPassengers().contains(proxy)
                && proxy.getPassengers().contains(maid);
    }

    @Nullable
    private static EntityMaid getLiftedMaid(ServerPlayer player) {
        UUID maidId = PLAYER_TO_MAID.get(player.getUUID());
        return maidId == null ? null : findMaidByUuid(player.serverLevel(), maidId);
    }

    @Nullable
    private static LiftProxyEntity findProxyForPlayer(ServerPlayer player) {
        LiftProxyEntity proxy = LiftProxyEntity.find(player.serverLevel(), PLAYER_TO_PROXY.get(player.getUUID()));
        if (proxy != null) {
            return proxy;
        }
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof LiftProxyEntity liftProxy) {
                return liftProxy;
            }
        }
        return null;
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

    private static void restoreRideableIfNeeded(EntityMaid maid) {
        Boolean prev = MAID_PREV_RIDEABLE.remove(maid.getUUID());
        if (prev != null) {
            maid.setRideable(prev);
        }
    }

    private static void restoreHomeModeIfNeeded(EntityMaid maid) {
        Boolean prev = MAID_PREV_HOME_MODE.remove(maid.getUUID());
        if (prev != null) {
            maid.setHomeModeEnable(prev);
        }
    }

    private static void attachClientLiftEntities(UUID playerUuid, UUID maidUuid, UUID proxyUuid) {
        Entity playerEntity = com.example.maidmarriage.client.ClientEntityLookup.findPlayer(playerUuid);
        Entity maidEntity = com.example.maidmarriage.client.ClientEntityLookup.findEntity(maidUuid);
        Entity proxyEntity = com.example.maidmarriage.client.ClientEntityLookup.findEntity(proxyUuid);
        if (!(playerEntity instanceof Player player)
                || !(maidEntity instanceof EntityMaid maid)
                || !(proxyEntity instanceof LiftProxyEntity proxy)) {
            return;
        }

        if (proxy.getVehicle() != player) {
            if (proxy.isPassenger()) {
                proxy.stopRiding();
            }
            proxy.startRiding(player, true);
        }
        if (maid.getVehicle() != proxy) {
            if (maid.isPassenger()) {
                maid.stopRiding();
            }
            maid.startRiding(proxy, true);
        }
        maid.setInSittingPose(true);
    }

    private static void detachClientLiftEntities(UUID playerUuid) {
        UUID maidUuid = CLIENT_PLAYER_TO_MAID.get(playerUuid);
        UUID proxyUuid = CLIENT_PLAYER_TO_PROXY.get(playerUuid);
        if (maidUuid != null) {
            Entity maidEntity = com.example.maidmarriage.client.ClientEntityLookup.findEntity(maidUuid);
            if (maidEntity instanceof EntityMaid maid && maid.isPassenger()) {
                maid.stopRiding();
                maid.setInSittingPose(false);
            }
        }
        if (proxyUuid != null) {
            Entity proxyEntity = com.example.maidmarriage.client.ClientEntityLookup.findEntity(proxyUuid);
            if (proxyEntity instanceof LiftProxyEntity proxy) {
                if (proxy.isPassenger()) {
                    proxy.stopRiding();
                }
            }
        }
    }
}
