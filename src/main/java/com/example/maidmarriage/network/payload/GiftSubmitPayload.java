package com.example.maidmarriage.network.payload;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 送礼提交包。
 *
 * <p>客户端只提交“目标女仆 + 背包槽位”，
 * 服务端再按当前真实库存和礼物表完成最终结算。
 */
public class GiftSubmitPayload {
    private final UUID maidUuid;
    private final int slotIndex;

    public GiftSubmitPayload(UUID maidUuid, int slotIndex) {
        this.maidUuid = maidUuid;
        this.slotIndex = slotIndex;
    }

    public UUID maidUuid() {
        return maidUuid;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public static void encode(GiftSubmitPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUuid);
        buf.writeVarInt(msg.slotIndex);
    }

    public static GiftSubmitPayload decode(FriendlyByteBuf buf) {
        return new GiftSubmitPayload(buf.readUUID(), buf.readVarInt());
    }
}
