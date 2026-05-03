package com.example.maidmarriage.client.dialoguesystem.runtime;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * 婚礼剧情的临时续播状态。
 *
 * <p>用于“剧情里打开女仆主面板后，再次回到互动界面时从指定节点继续”。
 * 当前只需要单条会话，因此客户端保留一份轻量静态状态就够了。
 */
public final class HugStoryResumeState {
    @Nullable
    private static PendingResume pendingResume;

    private HugStoryResumeState() {
    }

    public static void remember(@Nullable UUID maidUuid, ResourceLocation scenarioId, String nodeId) {
        if (maidUuid == null || scenarioId == null || nodeId == null || nodeId.isBlank()) {
            pendingResume = null;
            return;
        }
        pendingResume = new PendingResume(maidUuid, scenarioId, nodeId);
    }

    @Nullable
    public static PendingResume consumeIfMatches(@Nullable UUID maidUuid, ResourceLocation scenarioId) {
        if (pendingResume == null || maidUuid == null || scenarioId == null) {
            return null;
        }
        if (!maidUuid.equals(pendingResume.maidUuid()) || !scenarioId.equals(pendingResume.scenarioId())) {
            return null;
        }
        PendingResume resolved = pendingResume;
        pendingResume = null;
        return resolved;
    }

    public record PendingResume(UUID maidUuid, ResourceLocation scenarioId, String nodeId) {
    }
}
