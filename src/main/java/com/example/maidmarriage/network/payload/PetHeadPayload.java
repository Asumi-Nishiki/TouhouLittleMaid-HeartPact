package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 摸头请求数据包（客户端 -> 服务端）。
 * <p>
 * 客户端按下摸头热键后发送该包，请求服务端执行一次“摸头交互”。
 * 可选携带一个女仆 UUID（表示玩家当前准星选中的女仆），
 * 若未携带则服务端会自动查找一个可摸头的坐姿女仆。
 */
public class PetHeadPayload {
    @Nullable
    private final UUID maidUuid;

    public PetHeadPayload(@Nullable UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    /**
     * 编码：先写是否存在女仆 UUID，再按需写入 UUID。
     */
    public static void encode(PetHeadPayload msg, FriendlyByteBuf buf) {
        boolean hasMaid = msg.maidUuid != null;
        buf.writeBoolean(hasMaid);
        if (hasMaid) {
            buf.writeUUID(msg.maidUuid);
        }
    }

    /**
     * 解码：按“存在标记 + UUID”格式恢复请求数据。
     */
    public static PetHeadPayload decode(FriendlyByteBuf buf) {
        boolean hasMaid = buf.readBoolean();
        return new PetHeadPayload(hasMaid ? buf.readUUID() : null);
    }
}
