package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 膝枕状态同步包（服务端 -> 客户端）。
 */
public class LapPillowStateSyncPayload {
    private final UUID playerUuid;
    @Nullable
    private final UUID maidUuid;
    @Nullable
    private final UUID anchorUuid;
    private final boolean active;
    private final float sleepYaw;
    private final int petTicks;

    public LapPillowStateSyncPayload(UUID playerUuid, @Nullable UUID maidUuid,
                                     @Nullable UUID anchorUuid, boolean active,
                                     float sleepYaw, int petTicks) {
        this.playerUuid = playerUuid;
        this.maidUuid = maidUuid;
        this.anchorUuid = anchorUuid;
        this.active = active;
        this.sleepYaw = sleepYaw;
        this.petTicks = petTicks;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    @Nullable
    public UUID anchorUuid() {
        return anchorUuid;
    }

    public boolean active() {
        return active;
    }

    public float sleepYaw() {
        return sleepYaw;
    }

    public int petTicks() {
        return petTicks;
    }

    public static void encode(LapPillowStateSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUuid);
        buf.writeBoolean(msg.maidUuid != null);
        if (msg.maidUuid != null) {
            buf.writeUUID(msg.maidUuid);
        }
        buf.writeBoolean(msg.anchorUuid != null);
        if (msg.anchorUuid != null) {
            buf.writeUUID(msg.anchorUuid);
        }
        buf.writeBoolean(msg.active);
        buf.writeFloat(msg.sleepYaw);
        buf.writeVarInt(msg.petTicks);
    }

    public static LapPillowStateSyncPayload decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        UUID maidUuid = buf.readBoolean() ? buf.readUUID() : null;
        UUID anchorUuid = buf.readBoolean() ? buf.readUUID() : null;
        boolean active = buf.readBoolean();
        float sleepYaw = buf.readFloat();
        int petTicks = buf.readVarInt();
        return new LapPillowStateSyncPayload(playerUuid, maidUuid, anchorUuid, active, sleepYaw, petTicks);
    }
}
