package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 举高高状态同步包（服务端 -> 客户端）。
 */
public class LiftStateSyncPayload {
    private final UUID playerUuid;
    @Nullable
    private final UUID maidUuid;
    @Nullable
    private final UUID proxyUuid;
    private final double liftHeight;

    public LiftStateSyncPayload(UUID playerUuid, @Nullable UUID maidUuid, @Nullable UUID proxyUuid, double liftHeight) {
        this.playerUuid = playerUuid;
        this.maidUuid = maidUuid;
        this.proxyUuid = proxyUuid;
        this.liftHeight = liftHeight;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    @Nullable
    public UUID proxyUuid() {
        return proxyUuid;
    }

    public double liftHeight() {
        return liftHeight;
    }

    public static void encode(LiftStateSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUuid);
        boolean hasMaid = msg.maidUuid != null;
        buf.writeBoolean(hasMaid);
        if (hasMaid) {
            buf.writeUUID(msg.maidUuid);
        }
        boolean hasProxy = msg.proxyUuid != null;
        buf.writeBoolean(hasProxy);
        if (hasProxy) {
            buf.writeUUID(msg.proxyUuid);
        }
        buf.writeDouble(msg.liftHeight);
    }

    public static LiftStateSyncPayload decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        boolean hasMaid = buf.readBoolean();
        UUID maidUuid = hasMaid ? buf.readUUID() : null;
        boolean hasProxy = buf.readBoolean();
        UUID proxyUuid = hasProxy ? buf.readUUID() : null;
        double liftHeight = buf.readDouble();
        return new LiftStateSyncPayload(playerUuid, maidUuid, proxyUuid, liftHeight);
    }
}
