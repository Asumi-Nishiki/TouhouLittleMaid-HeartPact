package com.example.maidmarriage.network.payload;

import net.minecraft.network.FriendlyByteBuf;

public class UpdateMaidAddressingPayload {
    private final String addressing;
    private final String childAddressing;

    public UpdateMaidAddressingPayload(String addressing) {
        this(addressing, "");
    }

    public UpdateMaidAddressingPayload(String addressing, String childAddressing) {
        this.addressing = addressing == null ? "" : addressing;
        this.childAddressing = childAddressing == null ? "" : childAddressing;
    }

    public String addressing() {
        return addressing;
    }

    public String childAddressing() {
        return childAddressing;
    }

    public static void encode(UpdateMaidAddressingPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.addressing, 64);
        buf.writeUtf(msg.childAddressing, 64);
    }

    public static UpdateMaidAddressingPayload decode(FriendlyByteBuf buf) {
        return new UpdateMaidAddressingPayload(buf.readUtf(64), buf.readUtf(64));
    }
}
