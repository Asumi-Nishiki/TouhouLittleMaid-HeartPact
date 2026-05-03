package com.example.maidmarriage.network.payload;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

public class StartRomanceRhythmPayload {
    private final UUID maidUuid;

    public StartRomanceRhythmPayload(UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    public UUID maidUuid() {
        return maidUuid;
    }

    public static void encode(StartRomanceRhythmPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUuid);
    }

    public static StartRomanceRhythmPayload decode(FriendlyByteBuf buf) {
        return new StartRomanceRhythmPayload(buf.readUUID());
    }
}