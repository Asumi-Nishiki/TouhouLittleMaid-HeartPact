package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

public class CarryChildMaidPayload {
    @Nullable
    private final UUID childUuid;

    public CarryChildMaidPayload(@Nullable UUID childUuid) {
        this.childUuid = childUuid;
    }

    @Nullable
    public UUID childUuid() {
        return childUuid;
    }

    public static void encode(CarryChildMaidPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.childUuid != null);
        if (msg.childUuid != null) {
            buf.writeUUID(msg.childUuid);
        }
    }

    public static CarryChildMaidPayload decode(FriendlyByteBuf buf) {
        return new CarryChildMaidPayload(buf.readBoolean() ? buf.readUUID() : null);
    }
}
