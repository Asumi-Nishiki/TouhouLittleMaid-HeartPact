package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

public class CarryChildStateSyncPayload {
    private final UUID ownerUuid;
    @Nullable
    private final UUID adultUuid;
    @Nullable
    private final UUID childUuid;
    @Nullable
    private final UUID proxyUuid;

    public CarryChildStateSyncPayload(UUID ownerUuid, @Nullable UUID adultUuid, @Nullable UUID childUuid, @Nullable UUID proxyUuid) {
        this.ownerUuid = ownerUuid;
        this.adultUuid = adultUuid;
        this.childUuid = childUuid;
        this.proxyUuid = proxyUuid;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    @Nullable
    public UUID adultUuid() {
        return adultUuid;
    }

    @Nullable
    public UUID childUuid() {
        return childUuid;
    }

    @Nullable
    public UUID proxyUuid() {
        return proxyUuid;
    }

    public static void encode(CarryChildStateSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.ownerUuid);
        buf.writeBoolean(msg.adultUuid != null);
        if (msg.adultUuid != null) {
            buf.writeUUID(msg.adultUuid);
        }
        buf.writeBoolean(msg.childUuid != null);
        if (msg.childUuid != null) {
            buf.writeUUID(msg.childUuid);
        }
        buf.writeBoolean(msg.proxyUuid != null);
        if (msg.proxyUuid != null) {
            buf.writeUUID(msg.proxyUuid);
        }
    }

    public static CarryChildStateSyncPayload decode(FriendlyByteBuf buf) {
        UUID ownerUuid = buf.readUUID();
        UUID adultUuid = buf.readBoolean() ? buf.readUUID() : null;
        UUID childUuid = buf.readBoolean() ? buf.readUUID() : null;
        UUID proxyUuid = buf.readBoolean() ? buf.readUUID() : null;
        return new CarryChildStateSyncPayload(ownerUuid, adultUuid, childUuid, proxyUuid);
    }
}
