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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 膝枕专用锚点实体。
 *
 * <p>它不参与渲染和碰撞，只作为服务端与客户端都能看到的“姿态锚点”：
 * 女仆坐在原地，玩家的位置由 {@code LapPillowManager} 每 tick 锁到这个锚点附近。
 * 这样我们既能保留互动 UI，又不会把玩家塞进普通骑乘链里和 TLM/YSM 的载具逻辑打架。
 */
public class LapPillowAnchorEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_PLAYER_UUID =
            SynchedEntityData.defineId(LapPillowAnchorEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_MAID_UUID =
            SynchedEntityData.defineId(LapPillowAnchorEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final String TAG_PLAYER_UUID = "PlayerUuid";
    private static final String TAG_MAID_UUID = "MaidUuid";

    public static final double PLAYER_FORWARD_OFFSET = 0.45D;
    public static final double PLAYER_SIDE_OFFSET = 0.10D;
    public static final double PLAYER_Y_OFFSET = 0.25D;

    public LapPillowAnchorEntity(EntityType<? extends LapPillowAnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
        this.setInvisible(true);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_PLAYER_UUID, Optional.empty());
        this.entityData.define(DATA_MAID_UUID, Optional.empty());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_PLAYER_UUID)) {
            setPlayerUuid(tag.getUUID(TAG_PLAYER_UUID));
        }
        if (tag.hasUUID(TAG_MAID_UUID)) {
            setMaidUuid(tag.getUUID(TAG_MAID_UUID));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID playerUuid = getPlayerUuid();
        UUID maidUuid = getMaidUuid();
        if (playerUuid != null) {
            tag.putUUID(TAG_PLAYER_UUID, playerUuid);
        }
        if (maidUuid != null) {
            tag.putUUID(TAG_MAID_UUID, maidUuid);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);

        EntityMaid maid = getMaid();
        if (maid == null || !maid.isAlive()) {
            if (!level().isClientSide) {
                discard();
            }
            return;
        }

        moveToMaidLap(maid);
    }

    public void setTargets(@Nullable UUID playerUuid, @Nullable UUID maidUuid) {
        setPlayerUuid(playerUuid);
        setMaidUuid(maidUuid);
    }

    @Nullable
    public UUID getPlayerUuid() {
        return this.entityData.get(DATA_PLAYER_UUID).orElse(null);
    }

    @Nullable
    public UUID getMaidUuid() {
        return this.entityData.get(DATA_MAID_UUID).orElse(null);
    }

    @Nullable
    public EntityMaid getMaid() {
        return findMaid(level(), getMaidUuid());
    }

    public void moveToMaidLap(EntityMaid maid) {
        Vec3 pos = computePlayerRestPosition(maid);
        setPos(pos.x, pos.y, pos.z);
        setYRot(maid.getYRot());
        setYHeadRot(maid.getYRot());
        setXRot(0.0F);
    }

    public static Vec3 computePlayerRestPosition(EntityMaid maid) {
        return computePlayerRestPosition(maid, PLAYER_SIDE_OFFSET, PLAYER_Y_OFFSET, PLAYER_FORWARD_OFFSET);
    }

    public static Vec3 computePlayerRestPosition(EntityMaid maid,
                                                 double sideOffset,
                                                 double heightOffset,
                                                 double forwardOffset) {
        float yawRad = maid.getYRot() * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yawRad);
        double forwardZ = Mth.cos(yawRad);
        double sideX = Mth.cos(yawRad);
        double sideZ = Mth.sin(yawRad);
        return new Vec3(
                maid.getX() + forwardX * forwardOffset + sideX * sideOffset,
                maid.getY() + heightOffset,
                maid.getZ() + forwardZ * forwardOffset + sideZ * sideOffset
        );
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

    private void setPlayerUuid(@Nullable UUID playerUuid) {
        this.entityData.set(DATA_PLAYER_UUID, Optional.ofNullable(playerUuid));
    }

    private void setMaidUuid(@Nullable UUID maidUuid) {
        this.entityData.set(DATA_MAID_UUID, Optional.ofNullable(maidUuid));
    }

    @Nullable
    private static EntityMaid findMaid(Level level, @Nullable UUID maidUuid) {
        if (maidUuid == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(maidUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    @Nullable
    public static LapPillowAnchorEntity find(ServerLevel level, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        return entity instanceof LapPillowAnchorEntity anchor ? anchor : null;
    }
}
