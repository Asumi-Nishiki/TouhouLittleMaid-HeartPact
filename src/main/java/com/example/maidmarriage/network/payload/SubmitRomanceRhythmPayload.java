package com.example.maidmarriage.network.payload;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

public class SubmitRomanceRhythmPayload {
    private final UUID maidUuid;
    private final float rhythmScore;

    public SubmitRomanceRhythmPayload(UUID maidUuid, float rhythmScore) {
        this.maidUuid = maidUuid;
        this.rhythmScore = rhythmScore;
    }

    public UUID maidUuid() {
        return maidUuid;
    }

    public float rhythmScore() {
        return rhythmScore;
    }

    public static void encode(SubmitRomanceRhythmPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.maidUuid);
        buf.writeFloat(msg.rhythmScore);
    }

    public static SubmitRomanceRhythmPayload decode(FriendlyByteBuf buf) {
        return new SubmitRomanceRhythmPayload(buf.readUUID(), buf.readFloat());
    }
}
