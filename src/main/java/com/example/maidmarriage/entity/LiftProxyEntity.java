package com.example.maidmarriage.entity;

import com.example.maidmarriage.compat.MaidLiftManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 举高高专用的“代理锚点实体”。
 *
 * <p>这个实体完全不负责展示，也不参与普通玩法逻辑，只做两件事：
 * <ul>
 *     <li>自己作为玩家的乘客，挂在玩家身上；</li>
 *     <li>再把小女仆作为自己的乘客，强制摆到玩家头顶。</li>
 * </ul>
 *
 * <p>这样做的目的，是把“女仆直接骑玩家”的关系，改成：
 * <pre>
 * 女仆 -> 代理实体 -> 玩家
 * </pre>
 * 从而尽量绕开 YSM / TLM 里针对“玩家乘客女仆”的特殊姿态判断。
 */
public class LiftProxyEntity extends Entity {
    private static final double HEAD_OFFSET_Y = 0.10D;
    private static final double CROUCH_OFFSET_Y = -0.20D;

    public LiftProxyEntity(EntityType<? extends LiftProxyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
        this.setInvisible(true);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /**
     * 代理实体每 tick 都把自己钉在玩家附近，避免客户端因为默认乘骑偏移出现抖动或胸口挂载。
     */
    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);

        Player player = getCarrierPlayer();
        if (player == null || !player.isAlive()) {
            if (!level().isClientSide) {
                discard();
            }
            return;
        }

        Vec3 anchorPos = computeAnchorPosition(player);
        setPos(anchorPos.x, anchorPos.y, anchorPos.z);
        setYRot(player.getYRot());
        setYHeadRot(player.getYRot());
        setXRot(0.0F);

        if (!level().isClientSide && getPassengers().stream().noneMatch(EntityMaid.class::isInstance)) {
            discard();
        }
    }

    /**
     * 让女仆始终显示在玩家头顶，而不是显示在代理实体默认的乘骑偏移位置。
     */
    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        Player player = getCarrierPlayer();
        if (!(passenger instanceof EntityMaid) || player == null) {
            super.positionRider(passenger, callback);
            return;
        }
        Vec3 anchorPos = computeAnchorPosition(player);
        callback.accept(passenger, anchorPos.x, anchorPos.y, anchorPos.z);
    }

    /**
     * 代理实体自己骑在玩家身上时，不需要再额外下沉。
     */
    @Override
    public double getMyRidingOffset() {
        return 0.0D;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Nullable
    public Player getCarrierPlayer() {
        return getVehicle() instanceof Player player ? player : null;
    }

    public static Vec3 computeAnchorPosition(Player player) {
        double y = player.getY() + player.getBbHeight() + MaidLiftManager.resolveLiftHeight(player);
        if (player.isCrouching()) {
            y += CROUCH_OFFSET_Y;
        }
        return new Vec3(player.getX(), y, player.getZ());
    }

    @Nullable
    public static LiftProxyEntity find(ServerLevel level, @Nullable java.util.UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        return entity instanceof LiftProxyEntity proxy ? proxy : null;
    }
}
