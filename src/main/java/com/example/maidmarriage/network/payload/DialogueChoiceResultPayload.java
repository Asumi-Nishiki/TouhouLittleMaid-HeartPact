package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 对话选项结果上报包。
 *
 * <p>剧情 JSON 只描述“这个选项在好/中/差心情下分别给多少反馈”，
 * 真正的心情判定和数值结算在服务端完成。
 */
public class DialogueChoiceResultPayload {
    @Nullable
    private final UUID maidUuid;
    private final int positiveFavor;
    private final int neutralFavor;
    private final int negativeFavor;
    private final int positiveMoodDelta;
    private final int neutralMoodDelta;
    private final int negativeMoodDelta;

    public DialogueChoiceResultPayload(@Nullable UUID maidUuid,
                                       int positiveFavor,
                                       int neutralFavor,
                                       int negativeFavor,
                                       int positiveMoodDelta,
                                       int neutralMoodDelta,
                                       int negativeMoodDelta) {
        this.maidUuid = maidUuid;
        this.positiveFavor = positiveFavor;
        this.neutralFavor = neutralFavor;
        this.negativeFavor = negativeFavor;
        this.positiveMoodDelta = positiveMoodDelta;
        this.neutralMoodDelta = neutralMoodDelta;
        this.negativeMoodDelta = negativeMoodDelta;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    public int positiveFavor() {
        return positiveFavor;
    }

    public int neutralFavor() {
        return neutralFavor;
    }

    public int negativeFavor() {
        return negativeFavor;
    }

    public int positiveMoodDelta() {
        return positiveMoodDelta;
    }

    public int neutralMoodDelta() {
        return neutralMoodDelta;
    }

    public int negativeMoodDelta() {
        return negativeMoodDelta;
    }

    public static void encode(DialogueChoiceResultPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.maidUuid != null);
        if (msg.maidUuid != null) {
            buf.writeUUID(msg.maidUuid);
        }
        buf.writeVarInt(msg.positiveFavor);
        buf.writeVarInt(msg.neutralFavor);
        buf.writeVarInt(msg.negativeFavor);
        buf.writeVarInt(msg.positiveMoodDelta);
        buf.writeVarInt(msg.neutralMoodDelta);
        buf.writeVarInt(msg.negativeMoodDelta);
    }

    public static DialogueChoiceResultPayload decode(FriendlyByteBuf buf) {
        UUID maidUuid = buf.readBoolean() ? buf.readUUID() : null;
        int positiveFavor = buf.readVarInt();
        int neutralFavor = buf.readVarInt();
        int negativeFavor = buf.readVarInt();
        int positiveMoodDelta = buf.readVarInt();
        int neutralMoodDelta = buf.readVarInt();
        int negativeMoodDelta = buf.readVarInt();
        return new DialogueChoiceResultPayload(maidUuid, positiveFavor, neutralFavor, negativeFavor,
                positiveMoodDelta, neutralMoodDelta, negativeMoodDelta);
    }
}
