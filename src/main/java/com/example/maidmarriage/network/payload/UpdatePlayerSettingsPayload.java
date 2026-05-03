package com.example.maidmarriage.network.payload;

import net.minecraft.network.FriendlyByteBuf;

public class UpdatePlayerSettingsPayload {
    private final double liftHeight;
    private final double hugDistance;
    private final boolean haremMode;

    public UpdatePlayerSettingsPayload(double liftHeight, double hugDistance, boolean haremMode) {
        this.liftHeight = liftHeight;
        this.hugDistance = hugDistance;
        this.haremMode = haremMode;
    }

    public double liftHeight() {
        return liftHeight;
    }

    public double hugDistance() {
        return hugDistance;
    }

    public boolean haremMode() {
        return haremMode;
    }

    public static void encode(UpdatePlayerSettingsPayload msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.liftHeight);
        buf.writeDouble(msg.hugDistance);
        buf.writeBoolean(msg.haremMode);
    }

    public static UpdatePlayerSettingsPayload decode(FriendlyByteBuf buf) {
        return new UpdatePlayerSettingsPayload(
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean());
    }
}
