package com.example.maidmarriage.network.payload;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 好感度变化的客户端表现包。
 *
 * <p>服务端完成真实好感结算后，把“目标女仆 + 实际变化量”同步给客户端，
 * 客户端再负责播放粒子和飘字。
 */
public class FavorabilityEffectPayload {
    private final UUID maidUuid;
    private final int delta;

    public FavorabilityEffectPayload(UUID maidUuid, int delta) {
        this.maidUuid = maidUuid;
        this.delta = delta;
    }

    public UUID maidUuid() {
        return maidUuid;
    }

    public int delta() {
        return delta;
    }

    public static void encode(FavorabilityEffectPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUuid);
        buf.writeVarInt(msg.delta);
    }

    public static FavorabilityEffectPayload decode(FriendlyByteBuf buf) {
        return new FavorabilityEffectPayload(buf.readUUID(), buf.readVarInt());
    }
}
