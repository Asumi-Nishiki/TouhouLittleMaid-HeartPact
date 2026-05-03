package com.example.maidmarriage.client.hugui.actions;

import com.example.maidmarriage.client.HugClientState;
import javax.annotation.Nullable;

/**
 * 剧情“低头 / 抬头”姿态库。
 *
 * <p>之前这些目标角度直接散落在 mixin 和 YSM bridge 里，
 * 每次想调一处动作都得改两三份代码，后面只会越堆越乱。
 *
 * <p>这里把剧情头部演出抽成可复用的姿态规格：
 * - 不关心具体怎么把角度写进 Bedrock / Gecko / YSM；
 * - 只负责定义“这个动作在这个骨架上应该长什么样”。
 *
 * <p>这样以后其他剧情想复用“低着头不敢看人”“慢慢抬头”时，
 * 直接复用这份规格就行，不需要再往大文件里复制一遍参数。
 */
public final class StoryHeadCuePoseLibrary {
    public enum RigKind {
        BEDROCK,
        GECKO
    }

    public record HeadCuePose(
            float bodyX,
            float upBodyX,
            float upperBodyX,
            float neckX,
            float headX,
            float headY,
            float headZ,
            float leftShoulderX,
            float leftShoulderY,
            float leftShoulderZ,
            float rightShoulderX,
            float rightShoulderY,
            float rightShoulderZ
    ) {
    }

    private StoryHeadCuePoseLibrary() {
    }

    @Nullable
    public static HeadCuePose resolve(RigKind rigKind,
                                      boolean huggingBasePoseApplied,
                                      @Nullable HugClientState.HeadCueType cueType) {
        if (rigKind == null || cueType == null || cueType == HugClientState.HeadCueType.NONE) {
            return null;
        }

        return switch (rigKind) {
            case BEDROCK -> resolveBedrock(huggingBasePoseApplied, cueType);
            case GECKO -> resolveGecko(huggingBasePoseApplied, cueType);
        };
    }

    private static HeadCuePose resolveBedrock(boolean huggingBasePoseApplied,
                                              HugClientState.HeadCueType cueType) {
        float bodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upperBodyBaseX = huggingBasePoseApplied ? 0.18F : 0.0F;
        float neckBaseX = huggingBasePoseApplied ? 0.05F : 0.0F;
        float headBaseX = huggingBasePoseApplied ? -0.06F : 0.0F;
        float headBaseY = huggingBasePoseApplied ? 0.14F : 0.0F;
        float headBaseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
        float shoulderLeftBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderLeftBaseY = huggingBasePoseApplied ? -0.20F : 0.0F;
        float shoulderLeftBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float shoulderRightBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderRightBaseY = huggingBasePoseApplied ? 0.20F : 0.0F;
        float shoulderRightBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;

        if (cueType == HugClientState.HeadCueType.LOWER_HEAD) {
            return new HeadCuePose(
                    bodyBaseX - 0.05F,
                    bodyBaseX - 0.05F,
                    upperBodyBaseX - 0.18F,
                    neckBaseX - 0.22F,
                    headBaseX - 0.36F,
                    headBaseY,
                    headBaseZ,
                    shoulderLeftBaseX + 0.05F,
                    shoulderLeftBaseY + 0.05F,
                    shoulderLeftBaseZ - 0.04F,
                    shoulderRightBaseX + 0.05F,
                    shoulderRightBaseY - 0.05F,
                    shoulderRightBaseZ + 0.04F
            );
        }

        return new HeadCuePose(
                bodyBaseX + 0.02F,
                bodyBaseX + 0.02F,
                upperBodyBaseX + 0.08F,
                neckBaseX + 0.10F,
                headBaseX + 0.18F,
                headBaseY,
                headBaseZ,
                shoulderLeftBaseX - 0.02F,
                shoulderLeftBaseY - 0.03F,
                shoulderLeftBaseZ + 0.02F,
                shoulderRightBaseX - 0.02F,
                shoulderRightBaseY + 0.03F,
                shoulderRightBaseZ - 0.02F
        );
    }

    private static HeadCuePose resolveGecko(boolean huggingBasePoseApplied,
                                            HugClientState.HeadCueType cueType) {
        float allBodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upBodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upperBodyBaseX = huggingBasePoseApplied ? 0.18F : 0.0F;
        float neckBaseX = huggingBasePoseApplied ? 0.05F : 0.0F;
        float headBaseX = huggingBasePoseApplied ? -0.06F : 0.0F;
        float headBaseY = huggingBasePoseApplied ? 0.14F : 0.0F;
        float headBaseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
        float leftShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float leftShoulderBaseY = huggingBasePoseApplied ? 0.18F : 0.0F;
        float leftShoulderBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float rightShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float rightShoulderBaseY = huggingBasePoseApplied ? -0.18F : 0.0F;
        float rightShoulderBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;

        if (cueType == HugClientState.HeadCueType.LOWER_HEAD) {
            return new HeadCuePose(
                    allBodyBaseX - 0.05F,
                    upBodyBaseX - 0.05F,
                    upperBodyBaseX - 0.18F,
                    neckBaseX - 0.22F,
                    headBaseX - 0.36F,
                    headBaseY,
                    headBaseZ,
                    leftShoulderBaseX + 0.05F,
                    leftShoulderBaseY - 0.03F,
                    leftShoulderBaseZ - 0.04F,
                    rightShoulderBaseX + 0.05F,
                    rightShoulderBaseY + 0.03F,
                    rightShoulderBaseZ + 0.04F
            );
        }

        return new HeadCuePose(
                allBodyBaseX + 0.02F,
                upBodyBaseX + 0.02F,
                upperBodyBaseX + 0.08F,
                neckBaseX + 0.10F,
                headBaseX + 0.18F,
                headBaseY,
                headBaseZ,
                leftShoulderBaseX - 0.02F,
                leftShoulderBaseY + 0.03F,
                leftShoulderBaseZ + 0.02F,
                rightShoulderBaseX - 0.02F,
                rightShoulderBaseY - 0.03F,
                rightShoulderBaseZ - 0.02F
        );
    }
}
