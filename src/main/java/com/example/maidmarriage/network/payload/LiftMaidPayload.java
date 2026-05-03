package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 举高高请求包（客户端 -> 服务端）。
 * <p>
 * 按键触发时发送，允许携带一个可选女仆 UUID（准星命中时）。
 */
public class LiftMaidPayload {
    @Nullable
    private final UUID maidUuid;

    public LiftMaidPayload(@Nullable UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    public static void encode(LiftMaidPayload msg, FriendlyByteBuf buf) {
        boolean has = msg.maidUuid != null;
        buf.writeBoolean(has);
        if (has) {
            buf.writeUUID(msg.maidUuid);
        }
    }

    public static LiftMaidPayload decode(FriendlyByteBuf buf) {
        boolean has = buf.readBoolean();
        return new LiftMaidPayload(has ? buf.readUUID() : null);
    }
}
