package com.example.maidmarriage.compat;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.CarryChildStateSyncPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 大女仆抱小女仆状态管理器。
 *
 * <p>当前采用最直接、最容易稳定测试的方案：
 * <pre>
 * 小女仆 -> 大女仆
 * </pre>
 *
 * <p>也就是说，小女仆直接骑乘大女仆，不再插入中间代理实体。
 * 这样先解决“四实体链路导致的持续下坠/碰撞异常”，
 * 后续只专注拦截大女仆错误命中的骑乘动作。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidCarryChildManager {
    private static final double CHILD_AUTO_SELECT_RANGE_SQR = 20 * 20;
    private static final double ADULT_AUTO_SELECT_RANGE_SQR = 8 * 8;
    private static final double PUT_DOWN_FORWARD_OFFSET = 0.85D;
    private static final double PUT_DOWN_SIDE_OFFSET = 0.55D;
    private static final int IN_WALL_GRACE_TICKS = 10;

    private static final Map<UUID, UUID> OWNER_TO_CHILD = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> OWNER_TO_ADULT = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CHILD_TO_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> ADULT_TO_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CHILD_IN_WALL_GRACE_UNTIL = new ConcurrentHashMap<>();

    private static final Map<UUID, UUID> CLIENT_OWNER_TO_CHILD = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CLIENT_OWNER_TO_ADULT = new ConcurrentHashMap<>();

    private MaidCarryChildManager() {
    }

    public static void handleCarryToggle(ServerPlayer player, @Nullable UUID childUuid) {
        UUID carriedChildUuid = OWNER_TO_CHILD.get(player.getUUID());
        if (carriedChildUuid != null) {
            EntityMaid carriedChild = findMaidByUuid(player.serverLevel(), carriedChildUuid);
            if (rejectManualInfantPutDown(player, carriedChild)) {
                return;
            }
            putDown(player, carriedChild);
            return;
        }

        EntityMaid child = resolveTargetChild(player, childUuid);
        if (child == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.carry_child.no_child"));
            return;
        }
        if (!child.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.carry_child.need_owner", child.getDisplayName()));
            return;
        }
        if (!MaidChildEntity.shouldStayChild(child)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.carry_child.need_child", child.getDisplayName()));
            return;
        }
        if (CHILD_TO_OWNER.containsKey(child.getUUID())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.carry_child.child_busy", child.getDisplayName()));
            return;
        }
        if (child.isMaidInSittingPose()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.need_standing", child.getDisplayName()));
            return;
        }

        EntityMaid adult = resolveCarrierAdult(player, child);
        if (adult == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.carry_child.no_mother"));
            return;
        }
        if (ADULT_TO_OWNER.containsKey(adult.getUUID())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.carry_child.adult_busy", adult.getDisplayName()));
            return;
        }

        carryUp(player, adult, child);
    }

    public static boolean isCarryChildState(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        if (MaidChildEntity.isInfantBeingCarried(maid)) {
            return true;
        }
        if (maid.level().isClientSide()) {
            return CLIENT_OWNER_TO_CHILD.containsValue(maid.getUUID());
        }
        return CHILD_TO_OWNER.containsKey(maid.getUUID());
    }

    public static boolean isCarryAdultState(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        if (MaidChildEntity.isMotherCarryingInfant(maid)) {
            return true;
        }
        if (maid.level().isClientSide()) {
            return CLIENT_OWNER_TO_ADULT.containsValue(maid.getUUID());
        }
        return ADULT_TO_OWNER.containsKey(maid.getUUID());
    }

    public static boolean isAdultCarrier(EntityMaid maid) {
        return isCarryAdultState(maid);
    }

    public static boolean isCarriedChild(EntityMaid maid) {
        return isCarryChildState(maid);
    }

    /**
     * 成年女仆在被魂符/照片收纳前，先把怀里的小女仆安全放下并清掉 carry 同步。
     *
     * <p>这层的目标不是“备份再重生”，而是把已经存在的小女仆状态彻底清账：
     * 1. 先解除骑乘；
     * 2. 找到一个不会塞进墙里的落点；
     * 3. 强制同步清空客户端 carry 映射。
     */
    public static void releaseBeforeMaidTransform(EntityMaid adult) {
        if (adult == null || !(adult.level() instanceof ServerLevel level)) {
            return;
        }
        UUID ownerUuid = ADULT_TO_OWNER.get(adult.getUUID());
        if (ownerUuid == null) {
            releaseDirectChildPassengersBeforeTransform(level, adult);
            return;
        }
        EntityMaid child = findMaidByUuid(level, OWNER_TO_CHILD.get(ownerUuid));
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        releaseCarryState(owner, ownerUuid, adult, child, false);
        releaseDirectChildPassengersBeforeTransform(level, adult);
    }

    private static void releaseDirectChildPassengersBeforeTransform(ServerLevel level, EntityMaid adult) {
        if (adult == null || adult.getPassengers().isEmpty()) {
            return;
        }
        for (Entity passenger : adult.getPassengers()) {
            if (!(passenger instanceof EntityMaid child)) {
                continue;
            }
            if (!MaidChildEntity.shouldStayChild(child)
                    && !MaidChildEntity.isMotherOfChild(child, adult)) {
                continue;
            }
            if (child.isPassenger()) {
                child.stopRiding();
            }
            child.noPhysics = false;
            child.setDeltaMovement(Vec3.ZERO);
            Vec3 pos = findSafePutDownPosition(level, adult, child, null);
            if (pos == null) {
                pos = adult.position();
            }
            child.moveTo(pos.x, pos.y, pos.z, adult.getYRot(), 0.0F);
            child.setInSittingPose(false);
            child.fallDistance = 0.0F;
        }
    }

    /**
     * 供客户端快捷键判断：当前本地玩家是否正处于“大女仆抱小女仆”状态。
     *
     * <p>这样“摸头举高高”快捷键就可以和玩家自己举高高共用同一个放下入口：
     * - 玩家自己举着小女仆时，按键放下自己头顶上的小女仆；
     * - 玩家让大女仆抱着小女仆时，按键改为让大女仆把小女仆放回地面。
     */
    public static boolean isLocalPlayerCarryingChild(@Nullable Player player) {
        return getLocalCarriedChildUuid(player) != null;
    }

    /**
     * 服务端/客户端通用判断：这个玩家当前是否正处于“大女仆抱小女仆”状态。
     */
    public static boolean isPlayerCarryingChild(@Nullable Player player) {
        return getCarriedChildUuid(player) != null;
    }

    /**
     * 返回当前本地玩家对应抱起中的小女仆 UUID。
     *
     * <p>服务端真正处理时已经支持“再次触发 carry toggle 就放下”，
     * 这里把客户端可见状态暴露出来，仅用于快捷键层决定是否优先走放下分支。
     */
    @Nullable
    public static UUID getLocalCarriedChildUuid(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        return CLIENT_OWNER_TO_CHILD.get(player.getUUID());
    }

    /**
     * 服务端/客户端通用读取：返回这个玩家当前抱起中的小女仆 UUID。
     */
    @Nullable
    public static UUID getCarriedChildUuid(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        return player.level().isClientSide()
                ? CLIENT_OWNER_TO_CHILD.get(player.getUUID())
                : OWNER_TO_CHILD.get(player.getUUID());
    }

    @Nullable
    public static EntityMaid getCarryAdult(EntityMaid child) {
        if (child == null) {
            return null;
        }
        if (child.level().isClientSide()) {
            UUID ownerUuid = null;
            for (Map.Entry<UUID, UUID> entry : CLIENT_OWNER_TO_CHILD.entrySet()) {
                if (entry.getValue().equals(child.getUUID())) {
                    ownerUuid = entry.getKey();
                    break;
                }
            }
            if (ownerUuid == null || !com.example.maidmarriage.client.ClientEntityLookup.hasLevel()) {
                return null;
            }
            UUID adultUuid = CLIENT_OWNER_TO_ADULT.get(ownerUuid);
            if (adultUuid == null) {
                return null;
            }
            return com.example.maidmarriage.client.ClientEntityLookup.findMaid(adultUuid);
        }

        UUID ownerUuid = CHILD_TO_OWNER.get(child.getUUID());
        if (ownerUuid == null || !(child.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID adultUuid = OWNER_TO_ADULT.get(ownerUuid);
        return adultUuid == null ? null : findMaidByUuid(serverLevel, adultUuid);
    }

    public static void handleClientCarryStateSync(UUID ownerUuid, @Nullable UUID adultUuid,
                                                  @Nullable UUID childUuid, @Nullable UUID proxyUuid) {
        if (!com.example.maidmarriage.client.ClientEntityLookup.hasLevel()) {
            return;
        }

        if (adultUuid == null || childUuid == null) {
            detachClientCarryEntities(ownerUuid);
            CLIENT_OWNER_TO_CHILD.remove(ownerUuid);
            CLIENT_OWNER_TO_ADULT.remove(ownerUuid);
            return;
        }

        CLIENT_OWNER_TO_CHILD.put(ownerUuid, childUuid);
        CLIENT_OWNER_TO_ADULT.put(ownerUuid, adultUuid);
        attachClientCarryEntities(adultUuid, childUuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID childUuid = OWNER_TO_CHILD.get(player.getUUID());
        if (childUuid == null) {
            return;
        }

        EntityMaid child = findMaidByUuid(player.serverLevel(), childUuid);
        EntityMaid adult = findMaidByUuid(player.serverLevel(), OWNER_TO_ADULT.get(player.getUUID()));
        if (child == null || adult == null
                || !child.isAlive() || !adult.isAlive()
                || !child.isOwnedBy(player) || !adult.isOwnedBy(player)
                || !MaidChildEntity.shouldStayChild(child) || MaidChildEntity.shouldStayChild(adult)
                || child.isMaidInSittingPose() || adult.isMaidInSittingPose()) {
            putDown(player, child);
            return;
        }

        if (!ensureCarryChain(adult, child)) {
            putDown(player, child);
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.mount_failed", child.getDisplayName()));
            return;
        }

        stabilizeCarryMaid(adult, child);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (shouldIgnoreInWallDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (shouldIgnoreInWallDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putDown(player, getCarriedChild(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putDown(player, getCarriedChild(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            putDown(player, getCarriedChild(player));
        }
    }

    @SubscribeEvent
    public static void onMaidDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID ownerUuid = CHILD_TO_OWNER.get(maid.getUUID());
        if (ownerUuid == null) {
            ownerUuid = ADULT_TO_OWNER.get(maid.getUUID());
        }
        if (ownerUuid == null) {
            return;
        }
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner != null) {
            putDown(owner, getCarriedChild(owner));
        }
    }

    private static void carryUp(ServerPlayer player, EntityMaid adult, EntityMaid child) {
        if (child.isPassenger()) {
            child.stopRiding();
        }
        adult.setInSittingPose(false);
        child.setInSittingPose(false);

        if (!ensureCarryChain(adult, child)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.lift.mount_failed", child.getDisplayName()));
            return;
        }

        OWNER_TO_CHILD.put(player.getUUID(), child.getUUID());
        OWNER_TO_ADULT.put(player.getUUID(), adult.getUUID());
        CHILD_TO_OWNER.put(child.getUUID(), player.getUUID());
        ADULT_TO_OWNER.put(adult.getUUID(), player.getUUID());
        setCarryPhysics(child, true);

        /*
         * 大女仆抱起小女仆时，是否让小女仆切到跟随模式，取决于“抱她的人”当前是不是跟随模式。
         *
         * 规则按你的要求做成单向继承：
         * 1. 如果成年女仆当前就是跟随模式（homeMode = false），
         *    那么被抱起的小女仆也立刻切到跟随模式；
         * 2. 如果成年女仆不是跟随模式，就完全不动小女仆原本的模式。
         *
         * 这样可以避免“大女仆在跟随、放下后小女仆却瞬移回锚点”，
         * 同时又不会强行覆盖那些本来就想让小女仆留在原地/留在家模式里的情况。
         */
        if (!adult.isHomeModeEnable()) {
            child.setHomeModeEnable(false);
        }

        stabilizeCarryMaid(adult, child);

        player.serverLevel().sendParticles(
                ParticleTypes.HEART,
                child.getX(), child.getY(0.8D), child.getZ(),
                6, 0.15D, 0.15D, 0.15D, 0.01D
        );
        player.serverLevel().playSound(
                null, adult.blockPosition(),
                SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
                SoundSource.PLAYERS, 0.7F, 1.05F
        );
        RomanceSleepManager.speakSingleLineWithChat(child, "dialogue.maidmarriage.carry_child.start");
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                player,
                "message.maidmarriage.carry_child.start_success",
                adult.getDisplayName(),
                child.getDisplayName()
        ));
        syncCarryState(player, adult, child);
    }

    private static void putDown(ServerPlayer player, @Nullable EntityMaid child) {
        releaseCarryState(player, player.getUUID(), null, child, true);
    }

    private static boolean rejectManualInfantPutDown(ServerPlayer player, @Nullable EntityMaid child) {
        if (child == null || MaidChildEntity.resolveGrowthStage(child) != MaidChildEntity.GrowthStage.INFANT) {
            return false;
        }

        EntityMaid mother = getCarryAdult(child);
        if (mother == null && child.getVehicle() instanceof EntityMaid vehicle) {
            mother = vehicle;
        }
        if (mother != null && !MaidChildEntity.isMotherOfChild(child, mother)) {
            mother = null;
        }

        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                player,
                "message.maidmarriage.carry_child.infant_cannot_put_down",
                child.getDisplayName()
        ));
        if (mother != null) {
            int lineIndex = player.getRandom().nextInt(5) + 1;
            RomanceSleepManager.speakSingleLineWithChat(
                    mother,
                    "dialogue.maidmarriage.carry_child.infant_hold." + lineIndex
            );
            player.serverLevel().sendParticles(
                    ParticleTypes.HEART,
                    mother.getX(), mother.getY(0.9D), mother.getZ(),
                    5, 0.18D, 0.18D, 0.18D, 0.01D
            );
            player.serverLevel().playSound(
                    null,
                    mother.blockPosition(),
                    SoundEvents.ALLAY_AMBIENT_WITH_ITEM,
                    SoundSource.PLAYERS,
                    0.55F,
                    1.15F
            );
        }
        return true;
    }

    private static boolean ensureCarryChain(EntityMaid adult, EntityMaid child) {
        if (child.isPassenger() && child.getVehicle() != adult) {
            child.stopRiding();
        }
        if (!child.isPassenger() || child.getVehicle() != adult) {
            if (!child.startRiding(adult, true)) {
                return false;
            }
        }
        return child.getVehicle() == adult;
    }

    private static void stabilizeCarryMaid(EntityMaid adult, EntityMaid child) {
        /*
         * 这里不要再每 tick 强行停止“大女仆”的导航。
         *
         * 之前抱起后走不动，根因就在这里：
         * onPlayerTick -> stabilizeCarryMaid() 每 tick 都执行一次，
         * 导致成年女仆的寻路被持续清空，表现出来就是“抱着小女仆后完全不会走路”。
         *
         * 我们真正需要稳定的只有：
         * 1. 大女仆的朝向尽量保持一致；
         * 2. 小女仆不要自己乱寻路、乱锁敌、乱转头。
         *
         * 所以成年女仆保留自身 AI / 寻路，让她还能继续跟随、移动、靠近玩家；
         * 小女仆这一侧仍然持续压住导航和目标，避免乘骑状态下抖动或乱跑。
         */
        adult.setYBodyRot(adult.getYRot());
        adult.setYHeadRot(adult.getYRot());

        child.getNavigation().stop();
        child.setTarget(null);
        child.setYBodyRot(adult.getYRot());
        child.setYHeadRot(adult.getYRot());
        setCarryPhysics(child, true);
    }

    private static void releaseCarryState(@Nullable ServerPlayer player,
                                          @Nullable UUID ownerUuid,
                                          @Nullable EntityMaid knownAdult,
                                          @Nullable EntityMaid knownChild,
                                          boolean notifyPlayer) {
        ServerLevel level = resolveServerLevel(player, knownAdult, knownChild);
        if (level == null) {
            return;
        }

        UUID resolvedOwnerUuid = ownerUuid != null
                ? ownerUuid
                : player != null ? player.getUUID() : knownAdult != null ? ADULT_TO_OWNER.get(knownAdult.getUUID()) : knownChild != null ? CHILD_TO_OWNER.get(knownChild.getUUID()) : null;
        UUID childId = resolvedOwnerUuid == null ? knownChild == null ? null : knownChild.getUUID() : OWNER_TO_CHILD.remove(resolvedOwnerUuid);
        UUID adultId = resolvedOwnerUuid == null ? knownAdult == null ? null : knownAdult.getUUID() : OWNER_TO_ADULT.remove(resolvedOwnerUuid);

        if (childId != null) {
            CHILD_TO_OWNER.remove(childId);
        }
        if (adultId != null) {
            ADULT_TO_OWNER.remove(adultId);
        }

        EntityMaid adult = knownAdult != null ? knownAdult : findMaidByUuid(level, adultId);
        EntityMaid child = knownChild != null ? knownChild : findMaidByUuid(level, childId);
        if (adult != null) {
            adult.setInSittingPose(false);
        }

        if (child != null) {
            Vec3 safeDropPos = findSafePutDownPosition(level, adult, child, player);
            if (child.isPassenger()) {
                child.stopRiding();
            }
            setCarryPhysics(child, false);
            applyReleasedChildState(level, child, adult, player, safeDropPos);
            if (notifyPlayer && player != null) {
                RomanceSleepManager.speakSingleLineWithChat(child, "dialogue.maidmarriage.carry_child.stop");
                player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                        player,
                        "message.maidmarriage.carry_child.stop_success",
                        child.getDisplayName()
                ));
            }
        }

        ServerPlayer syncTarget = player;
        if (syncTarget == null && resolvedOwnerUuid != null) {
            syncTarget = level.getServer().getPlayerList().getPlayer(resolvedOwnerUuid);
        }
        if (syncTarget != null) {
            syncCarryState(syncTarget, null, null);
        }
    }

    @Nullable
    private static ServerLevel resolveServerLevel(@Nullable ServerPlayer player,
                                                  @Nullable EntityMaid adult,
                                                  @Nullable EntityMaid child) {
        if (player != null) {
            return player.serverLevel();
        }
        if (adult != null && adult.level() instanceof ServerLevel level) {
            return level;
        }
        if (child != null && child.level() instanceof ServerLevel level) {
            return level;
        }
        return null;
    }

    private static void applyReleasedChildState(ServerLevel level,
                                                EntityMaid child,
                                                @Nullable EntityMaid adult,
                                                @Nullable ServerPlayer player,
                                                @Nullable Vec3 safeDropPos) {
        Vec3 dropPos = safeDropPos != null ? safeDropPos : fallbackPutDownPosition(adult, player, child);
        float yaw = adult != null ? adult.getYRot() : player != null ? player.getYRot() : child.getYRot();

        child.setInSittingPose(false);
        child.setPose(Pose.STANDING);
        child.getNavigation().stop();
        child.setTarget(null);
        child.setDeltaMovement(Vec3.ZERO);
        child.fallDistance = 0.0F;
        child.teleportTo(dropPos.x, dropPos.y, dropPos.z);
        child.setYRot(yaw);
        child.setYHeadRot(yaw);
        child.setYBodyRot(yaw);
        child.setXRot(0.0F);
        child.setOnGround(true);
        child.xo = dropPos.x;
        child.yo = dropPos.y;
        child.zo = dropPos.z;
        child.xOld = dropPos.x;
        child.yOld = dropPos.y;
        child.zOld = dropPos.z;
        CHILD_IN_WALL_GRACE_UNTIL.put(child.getUUID(), level.getGameTime() + IN_WALL_GRACE_TICKS);
    }

    private static Vec3 fallbackPutDownPosition(@Nullable EntityMaid adult,
                                                @Nullable ServerPlayer player,
                                                EntityMaid child) {
        float yaw = adult != null ? adult.getYRot() : player != null ? player.getYRot() : child.getYRot();
        Vec3 origin = adult != null ? adult.position() : player != null ? player.position() : child.position();
        float yawRad = yaw * Mth.DEG_TO_RAD;
        double targetX = origin.x - Mth.sin(yawRad) * PUT_DOWN_FORWARD_OFFSET;
        double targetZ = origin.z + Mth.cos(yawRad) * PUT_DOWN_FORWARD_OFFSET;
        return new Vec3(targetX, origin.y, targetZ);
    }

    @Nullable
    private static Vec3 findSafePutDownPosition(ServerLevel level,
                                                @Nullable EntityMaid adult,
                                                EntityMaid child,
                                                @Nullable ServerPlayer player) {
        float yaw = adult != null ? adult.getYRot() : player != null ? player.getYRot() : child.getYRot();
        Vec3 origin = adult != null ? adult.position() : player != null ? player.position() : child.position();
        double[][] offsets = new double[][]{
                {PUT_DOWN_FORWARD_OFFSET, 0.0D},
                {PUT_DOWN_FORWARD_OFFSET, PUT_DOWN_SIDE_OFFSET},
                {PUT_DOWN_FORWARD_OFFSET, -PUT_DOWN_SIDE_OFFSET},
                {0.0D, PUT_DOWN_FORWARD_OFFSET},
                {0.0D, -PUT_DOWN_FORWARD_OFFSET},
                {-0.55D, 0.0D},
                {-0.35D, PUT_DOWN_SIDE_OFFSET},
                {-0.35D, -PUT_DOWN_SIDE_OFFSET}
        };

        for (double[] offset : offsets) {
            Vec3 horizontal = offsetFromYaw(origin, yaw, offset[0], offset[1]);
            Vec3 grounded = resolveSafeGroundedPosition(level, child, horizontal.x, horizontal.z, origin.y);
            if (grounded != null) {
                return grounded;
            }
        }
        return null;
    }

    private static Vec3 offsetFromYaw(Vec3 origin, float yawDegrees, double forward, double side) {
        float yawRad = yawDegrees * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yawRad);
        double forwardZ = Mth.cos(yawRad);
        double sideX = Mth.cos(yawRad);
        double sideZ = Mth.sin(yawRad);
        return new Vec3(
                origin.x + forwardX * forward + sideX * side,
                origin.y,
                origin.z + forwardZ * forward + sideZ * side
        );
    }

    @Nullable
    private static Vec3 resolveSafeGroundedPosition(ServerLevel level,
                                                    EntityMaid child,
                                                    double x,
                                                    double z,
                                                    double referenceY) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(x, referenceY + 1.5D, z).mutable();
        for (int i = 0; i < 8; i++) {
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                double topY = cursor.getY() + shape.max(Direction.Axis.Y);
                Vec3 candidate = new Vec3(x, topY, z);
                if (canStandAt(level, child, candidate)) {
                    return candidate;
                }
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static boolean canStandAt(Level level, EntityMaid child, Vec3 pos) {
        EntityDimensions dimensions = child.getDimensions(Pose.STANDING);
        AABB targetBox = dimensions.makeBoundingBox(pos.x, pos.y, pos.z);
        if (!level.noCollision(child, targetBox)) {
            return false;
        }

        BlockPos supportPos = BlockPos.containing(pos.x, pos.y - 0.0625D, pos.z);
        VoxelShape supportShape = level.getBlockState(supportPos).getCollisionShape(level, supportPos);
        if (supportShape.isEmpty()) {
            return false;
        }

        double supportTop = supportPos.getY() + supportShape.max(Direction.Axis.Y);
        return supportTop >= pos.y - 0.2D;
    }

    private static boolean shouldIgnoreInWallDamage(Entity entity, DamageSource source) {
        if (!(entity instanceof EntityMaid maid) || !source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        return isCarryChildState(maid) || MaidLiftManager.isLiftedMaid(maid) || hasInWallGrace(maid);
    }

    private static boolean hasInWallGrace(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        Long untilTick = CHILD_IN_WALL_GRACE_UNTIL.get(maid.getUUID());
        if (untilTick == null) {
            return false;
        }
        if (untilTick <= level.getGameTime()) {
            CHILD_IN_WALL_GRACE_UNTIL.remove(maid.getUUID());
            return false;
        }
        return true;
    }

    @Nullable
    private static EntityMaid getCarriedChild(ServerPlayer player) {
        UUID childId = OWNER_TO_CHILD.get(player.getUUID());
        return childId == null ? null : findMaidByUuid(player.serverLevel(), childId);
    }

    @Nullable
    private static EntityMaid resolveTargetChild(ServerPlayer player, @Nullable UUID childUuid) {
        if (childUuid != null) {
            Entity entity = player.serverLevel().getEntity(childUuid);
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
            if (dist > CHILD_AUTO_SELECT_RANGE_SQR || dist >= bestDist) {
                continue;
            }
            best = maid;
            bestDist = dist;
        }
        return best;
    }

    @Nullable
    private static EntityMaid resolveCarrierAdult(ServerPlayer player, EntityMaid child) {
        EntityMaid best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (!(entity instanceof EntityMaid maid) || maid == child) {
                continue;
            }
            if (!maid.isOwnedBy(player)
                    || MaidChildEntity.shouldStayChild(maid)
                    || !MaidChildEntity.isMotherOfChild(child, maid)
                    || maid.isMaidInSittingPose()
                    || maid.isPassenger()) {
                continue;
            }
            double dist = maid.distanceToSqr(child);
            if (dist > ADULT_AUTO_SELECT_RANGE_SQR || dist >= bestDist) {
                continue;
            }
            best = maid;
            bestDist = dist;
        }
        return best;
    }

    @Nullable
    private static EntityMaid findMaidByUuid(ServerLevel level, @Nullable UUID maidUuid) {
        if (maidUuid == null) {
            return null;
        }
        Entity entity = level.getEntity(maidUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private static void syncCarryState(ServerPlayer player, @Nullable EntityMaid adult, @Nullable EntityMaid child) {
        ModNetworking.sendCarryChildState(
                player,
                new CarryChildStateSyncPayload(
                        player.getUUID(),
                        adult == null ? null : adult.getUUID(),
                        child == null ? null : child.getUUID(),
                        null
                )
        );
    }

    private static void attachClientCarryEntities(UUID adultUuid, UUID childUuid) {
        Entity adultEntity = com.example.maidmarriage.client.ClientEntityLookup.findEntity(adultUuid);
        Entity childEntity = com.example.maidmarriage.client.ClientEntityLookup.findEntity(childUuid);
        if (!(adultEntity instanceof EntityMaid adult) || !(childEntity instanceof EntityMaid child)) {
            return;
        }

        if (child.getVehicle() != adult) {
            if (child.isPassenger()) {
                child.stopRiding();
            }
            child.startRiding(adult, true);
        }
    }

    private static void detachClientCarryEntities(UUID ownerUuid) {
        UUID childUuid = CLIENT_OWNER_TO_CHILD.get(ownerUuid);
        if (childUuid == null) {
            return;
        }
        Entity childEntity = com.example.maidmarriage.client.ClientEntityLookup.findEntity(childUuid);
        if (childEntity instanceof EntityMaid child && child.isPassenger()) {
            child.stopRiding();
        }
    }

    private static void setCarryPhysics(EntityMaid child, boolean carried) {
        if (child == null) {
            return;
        }
        // 被抱起时临时关闭碰撞，避免儿童阶段的大一点的碰撞箱和成年女仆、门框、方块边缘互相卡住。
        // 放下后立即恢复，尽量只影响“抱在怀里”这段状态。
        child.noPhysics = carried;
        child.setDeltaMovement(Vec3.ZERO);
        child.fallDistance = 0.0F;
        if (carried) {
            child.getNavigation().stop();
            child.setTarget(null);
        }
    }
}
