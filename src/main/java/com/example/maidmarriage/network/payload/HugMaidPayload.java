package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

public class HugMaidPayload {
    @Nullable
    private final UUID maidUuid;

    public HugMaidPayload(@Nullable UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    public static void encode(HugMaidPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.maidUuid != null);
        if (msg.maidUuid != null) {
            buf.writeUUID(msg.maidUuid);
        }
    }

    public static HugMaidPayload decode(FriendlyByteBuf buf) {
        boolean has = buf.readBoolean();
        return new HugMaidPayload(has ? buf.readUUID() : null);
    }
}
