package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 小女仆互动会话切换包。
 *
 * <p>语义与成年女仆的互动入口一致：
 * - 当前没有小女仆互动会话时：尝试进入站立锁定；
 * - 当前已经有会话时：结束这份会话。
 */
public class ChildInteractionPayload {
    @Nullable
    private final UUID maidUuid;

    public ChildInteractionPayload(@Nullable UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    public static void encode(ChildInteractionPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.maidUuid != null);
        if (msg.maidUuid != null) {
            buf.writeUUID(msg.maidUuid);
        }
    }

    public static ChildInteractionPayload decode(FriendlyByteBuf buf) {
        boolean has = buf.readBoolean();
        return new ChildInteractionPayload(has ? buf.readUUID() : null);
    }
}
