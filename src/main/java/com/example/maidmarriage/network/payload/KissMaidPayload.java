package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 亲吻请求数据包（客户端 -> 服务端）。
 * 客户端只负责提交当前想交互的女仆 UUID，
 * 服务端会再次核对所有权与拥抱状态，防止状态不同步。
 */
public class KissMaidPayload {
    @Nullable
    private final UUID maidUuid;

    public KissMaidPayload(@Nullable UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    public static void encode(KissMaidPayload msg, FriendlyByteBuf buf) {
        boolean hasMaid = msg.maidUuid != null;
        buf.writeBoolean(hasMaid);
        if (hasMaid) {
            buf.writeUUID(msg.maidUuid);
        }
    }

    public static KissMaidPayload decode(FriendlyByteBuf buf) {
        boolean hasMaid = buf.readBoolean();
        return new KissMaidPayload(hasMaid ? buf.readUUID() : null);
    }
}
