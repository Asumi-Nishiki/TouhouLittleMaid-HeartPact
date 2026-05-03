package com.example.maidmarriage.entity;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 大女仆抱小女仆时使用的中间代理实体。
 *
 * <p>这一版不再让大女仆自己成为 vehicle，而是改成：
 * <pre>
 * 大女仆 -> MaidCarryProxyEntity
 * 小女仆 -> MaidCarryProxyEntity
 * </pre>
 *
 * <p>这样做的目的是把“骑乘关系”完全收束到代理实体上，
 * 避免 TLM / YSM 因为检测到大女仆身上有 passenger，
 * 继续切入摩托、载具之类的底层骑乘动作。
 */
public class MaidCarryProxyEntity extends Entity {
    public static final double ADULT_Y_OFFSET = -0.02D;
    public static final double CHILD_FORWARD_OFFSET = 0.36D;
    public static final double CHILD_SIDE_OFFSET = 0.06D;
    public static final double CHILD_Y_OFFSET = 0.82D;

    private static final EntityDataAccessor<Optional<UUID>> DATA_ADULT_UUID =
            SynchedEntityData.defineId(MaidCarryProxyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_CHILD_UUID =
            SynchedEntityData.defineId(MaidCarryProxyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final String TAG_ADULT_UUID = "AdultUuid";
    private static final String TAG_CHILD_UUID = "ChildUuid";

    public MaidCarryProxyEntity(EntityType<? extends MaidCarryProxyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
        this.setInvisible(true);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ADULT_UUID, Optional.empty());
        this.entityData.define(DATA_CHILD_UUID, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_ADULT_UUID)) {
            setAdultUuid(tag.getUUID(TAG_ADULT_UUID));
        }
        if (tag.hasUUID(TAG_CHILD_UUID)) {
            setChildUuid(tag.getUUID(TAG_CHILD_UUID));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID adultUuid = getAdultUuid();
        UUID childUuid = getChildUuid();
        if (adultUuid != null) {
            tag.putUUID(TAG_ADULT_UUID, adultUuid);
        }
        if (childUuid != null) {
            tag.putUUID(TAG_CHILD_UUID, childUuid);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);

        if (level().isClientSide) {
            return;
        }

        EntityMaid adult = getAdultMaid();
        EntityMaid child = getChildMaid();
        if (adult == null || child == null || !adult.isAlive() || !child.isAlive()) {
            discard();
            return;
        }

        if (getPassengers().stream().noneMatch(EntityMaid.class::isInstance)) {
            discard();
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        if (!(passenger instanceof EntityMaid maid)) {
            super.positionRider(passenger, callback);
            return;
        }

        UUID adultUuid = getAdultUuid();
        UUID childUuid = getChildUuid();
        Vec3 targetPos;

        if (adultUuid != null && adultUuid.equals(maid.getUUID())) {
            targetPos = computeAdultAnchorPosition(position(), getYRot());
        } else if (childUuid != null && childUuid.equals(maid.getUUID())) {
            targetPos = computeChildAnchorPosition(position(), getYRot());
        } else {
            super.positionRider(passenger, callback);
            return;
        }

        callback.accept(passenger, targetPos.x, targetPos.y, targetPos.z);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

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

    public void setCarryTargets(@Nullable UUID adultUuid, @Nullable UUID childUuid) {
        setAdultUuid(adultUuid);
        setChildUuid(childUuid);
    }

    @Nullable
    public UUID getAdultUuid() {
        return this.entityData.get(DATA_ADULT_UUID).orElse(null);
    }

    @Nullable
    public UUID getChildUuid() {
        return this.entityData.get(DATA_CHILD_UUID).orElse(null);
    }

    @Nullable
    public EntityMaid getAdultMaid() {
        return findMaid(level(), getAdultUuid());
    }

    @Nullable
    public EntityMaid getChildMaid() {
        return findMaid(level(), getChildUuid());
    }

    private void setAdultUuid(@Nullable UUID adultUuid) {
        this.entityData.set(DATA_ADULT_UUID, Optional.ofNullable(adultUuid));
    }

    private void setChildUuid(@Nullable UUID childUuid) {
        this.entityData.set(DATA_CHILD_UUID, Optional.ofNullable(childUuid));
    }

    /**
     * 代理实体本身就是整个抱起状态的“世界锚点”。
     * 大女仆被放回代理实体中心，并略微下沉一点，
     * 这样视觉上会更像自然站立，而不是悬在半空中。
     */
    public static Vec3 computeAdultAnchorPosition(Vec3 rootPos, float yawDegrees) {
        return rootPos.add(0.0D, ADULT_Y_OFFSET, 0.0D);
    }

    /**
     * 小女仆的位置放在大女仆身前稍高一点，方便姿态层把她修成“被抱在怀里”。
     */
    public static Vec3 computeChildAnchorPosition(Vec3 rootPos, float yawDegrees) {
        float yawRad = yawDegrees * ((float) Math.PI / 180.0F);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double sideX = Math.cos(yawRad);
        double sideZ = Math.sin(yawRad);
        return new Vec3(
                rootPos.x + forwardX * CHILD_FORWARD_OFFSET + sideX * CHILD_SIDE_OFFSET,
                rootPos.y + CHILD_Y_OFFSET,
                rootPos.z + forwardZ * CHILD_FORWARD_OFFSET + sideZ * CHILD_SIDE_OFFSET
        );
    }

    /**
     * 由“大女仆当前身体位置”反推代理锚点。
     *
     * <p>注意这里必须把成人乘客自身的 Y 偏移加回去，
     * 否则每 tick 都会拿“已经被压低后的乘客坐标”继续反推代理实体，
     * 最终形成持续向下漂移的递归反馈。
     */
    public static Vec3 computeRootPositionFromAdult(EntityMaid adult) {
        return new Vec3(adult.getX(), adult.getY() - ADULT_Y_OFFSET, adult.getZ());
    }

    @Nullable
    private static EntityMaid findMaid(Level level, @Nullable UUID maidUuid) {
        if (maidUuid == null) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(maidUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    @Nullable
    public static MaidCarryProxyEntity find(ServerLevel level, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        return entity instanceof MaidCarryProxyEntity proxy ? proxy : null;
    }
}
