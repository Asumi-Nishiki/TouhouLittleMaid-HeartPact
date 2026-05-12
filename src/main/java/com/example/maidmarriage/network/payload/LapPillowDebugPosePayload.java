package com.example.maidmarriage.network.payload;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 膝枕调试姿态同步包（客户端 -> 服务端）。
 *
 * <p>这个包只服务于 F9 调试面板：玩家在客户端调整数值后，
 * 服务端用这些临时偏移重新锁定膝枕位置，方便现场校准。
 */
public class LapPillowDebugPosePayload {
    private final double sideOffset;
    private final double heightOffset;
    private final double forwardOffset;
    private final float yawOffset;

    public LapPillowDebugPosePayload(double sideOffset, double heightOffset, double forwardOffset, float yawOffset) {
        this.sideOffset = sideOffset;
        this.heightOffset = heightOffset;
        this.forwardOffset = forwardOffset;
        this.yawOffset = yawOffset;
    }

    public double sideOffset() {
        return sideOffset;
    }

    public double heightOffset() {
        return heightOffset;
    }

    public double forwardOffset() {
        return forwardOffset;
    }

    public float yawOffset() {
        return yawOffset;
    }

    public static void encode(LapPillowDebugPosePayload msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.sideOffset);
        buf.writeDouble(msg.heightOffset);
        buf.writeDouble(msg.forwardOffset);
        buf.writeFloat(msg.yawOffset);
    }

    public static LapPillowDebugPosePayload decode(FriendlyByteBuf buf) {
        return new LapPillowDebugPosePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }
}
