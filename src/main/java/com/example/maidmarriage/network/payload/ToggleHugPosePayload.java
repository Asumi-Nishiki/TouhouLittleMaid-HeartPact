package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * “切换拥抱姿态”请求包。
 *
 * <p>这里故意和“进入/退出交互会话”的请求包拆开：
 * 现在 Ctrl+拥抱键只负责开启或结束站立锁定，
 * 而剧情面板里的“拥抱 / 放开女仆”按钮只负责切换同一会话里的拥抱姿态。
 *
 * <p>这样客户端语义会更清晰：
 * - 交互入口 -> 站立锁定；
 * - 面板动作 -> 切换 hugActive；
 * 不会再出现 UI 想切姿态，却意外把整个交互会话关掉的问题。
 */
public class ToggleHugPosePayload {
    @Nullable
    private final UUID maidUuid;

    public ToggleHugPosePayload(@Nullable UUID maidUuid) {
        this.maidUuid = maidUuid;
    }

    @Nullable
    public UUID maidUuid() {
        return maidUuid;
    }

    public static void encode(ToggleHugPosePayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.maidUuid != null);
        if (msg.maidUuid != null) {
            buf.writeUUID(msg.maidUuid);
        }
    }

    public static ToggleHugPosePayload decode(FriendlyByteBuf buf) {
        boolean has = buf.readBoolean();
        return new ToggleHugPosePayload(has ? buf.readUUID() : null);
    }
}
