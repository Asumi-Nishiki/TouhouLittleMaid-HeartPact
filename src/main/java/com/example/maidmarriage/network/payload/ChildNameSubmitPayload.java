package com.example.maidmarriage.network.payload;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 小女仆命名提交包。
 *
 * <p>客户端只提交“正在互动的妈妈 + 玩家输入的名字”，具体能不能命名、
 * 应该命名哪个孩子，都由服务端按当前实体状态重新判定。
 */
public class ChildNameSubmitPayload {
    @Nullable
    private final UUID motherUuid;
    private final String name;

    public ChildNameSubmitPayload(@Nullable UUID motherUuid, String name) {
        this.motherUuid = motherUuid;
        this.name = name == null ? "" : name;
    }

    @Nullable
    public UUID motherUuid() {
        return motherUuid;
    }

    public String name() {
        return name;
    }

    public static void encode(ChildNameSubmitPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.motherUuid != null);
        if (msg.motherUuid != null) {
            buf.writeUUID(msg.motherUuid);
        }
        buf.writeUtf(msg.name, 64);
    }

    public static ChildNameSubmitPayload decode(FriendlyByteBuf buf) {
        UUID motherUuid = buf.readBoolean() ? buf.readUUID() : null;
        return new ChildNameSubmitPayload(motherUuid, buf.readUtf(64));
    }
}
