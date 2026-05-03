package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 小女仆互动会话的客户端同步包。
 *
 * <p>这里只同步“是否正在和某只小女仆保持站立锁定”。
 * 这层不带拥抱标记，因为小女仆互动页没有“hugging / not hugging”二级状态。
 */
public class ChildInteractionStateSyncPayload {
    private final UUID playerUuid;
    @Nullable
    private final UUID maidUuid;

    public ChildInteractionStateSyncPayload(UUID playerUuid, @Nullable UUID maidUuid) {
        this.playerUuid = playerUuid;
        this.maidUuid = maidUuid;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    public static void encode(ChildInteractionStateSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUuid);
        buf.writeBoolean(msg.maidUuid != null);
        if (msg.maidUuid != null) {
            buf.writeUUID(msg.maidUuid);
        }
    }

    public static ChildInteractionStateSyncPayload decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        boolean hasMaid = buf.readBoolean();
        UUID maidUuid = hasMaid ? buf.readUUID() : null;
        return new ChildInteractionStateSyncPayload(playerUuid, maidUuid);
    }
}
