package com.example.maidmarriage.network.payload;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 亲吻特效同步包（服务端 -> 客户端）。
 *
 * <p>亲吻后的害羞回头只属于视觉表现，
 * 服务端只同步女仆 UUID 和表现参数，客户端再按当前渲染实体应用姿态。
 * 这样不会把客户端镜头、模型骨骼或临时动画状态写回服务端数据。
 */
public class KissEffectPayload {
    private final UUID maidUuid;
    private final int shyDelayTicks;
    private final int shyDurationTicks;
    private final float shyHeadYawDegrees;
    private final float shyHeadPitchDegrees;
    private final int shyDirectionSign;

    public KissEffectPayload(UUID maidUuid, int shyDelayTicks, int shyDurationTicks,
                             float shyHeadYawDegrees, float shyHeadPitchDegrees, int shyDirectionSign) {
        this.maidUuid = maidUuid;
        this.shyDelayTicks = shyDelayTicks;
        this.shyDurationTicks = shyDurationTicks;
        this.shyHeadYawDegrees = shyHeadYawDegrees;
        this.shyHeadPitchDegrees = shyHeadPitchDegrees;
        this.shyDirectionSign = shyDirectionSign;
    }

    public static void encode(KissEffectPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUuid);
        buf.writeVarInt(msg.shyDelayTicks);
        buf.writeVarInt(msg.shyDurationTicks);
        buf.writeFloat(msg.shyHeadYawDegrees);
        buf.writeFloat(msg.shyHeadPitchDegrees);
        buf.writeVarInt(msg.shyDirectionSign);
    }

    public static KissEffectPayload decode(FriendlyByteBuf buf) {
        return new KissEffectPayload(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt()
        );
    }

    public UUID maidUuid() {
        return maidUuid;
    }

    public int shyDelayTicks() {
        return shyDelayTicks;
    }

    public int shyDurationTicks() {
        return shyDurationTicks;
    }

    public float shyHeadYawDegrees() {
        return shyHeadYawDegrees;
    }

    public float shyHeadPitchDegrees() {
        return shyHeadPitchDegrees;
    }

    public int shyDirectionSign() {
        return shyDirectionSign;
    }
}
