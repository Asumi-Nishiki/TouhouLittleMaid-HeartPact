package com.example.maidmarriage.client.hugui.actions;

/**
 * 剧情“锤胸口两下”姿态库。
 *
 * <p>这个动作的语义不是攻击，而是告白后那种又哭又羞、带点撒娇埋怨地轻轻锤你两下。
 * 所以这里的目标姿态是：
 * - 手先抬到胸前；
 * - 身体不大动；
 * - 主要靠右臂和前臂往里轻敲两下。
 */
public final class StoryChestTapPoseLibrary {
    public enum RigKind {
        BEDROCK,
        GECKO
    }

    public record ChestTapPose(
            float leftShoulderX,
            float leftShoulderY,
            float leftShoulderZ,
            float rightShoulderX,
            float rightShoulderY,
            float rightShoulderZ,
            float leftArmX,
            float leftArmY,
            float leftArmZ,
            float rightArmX,
            float rightArmY,
            float rightArmZ,
            float leftForeArmX,
            float leftForeArmY,
            float leftForeArmZ,
            float rightForeArmX,
            float rightForeArmY,
            float rightForeArmZ
    ) {
    }

    private StoryChestTapPoseLibrary() {
    }

    public static ChestTapPose resolve(RigKind rigKind, boolean huggingBasePoseApplied) {
        return rigKind == RigKind.BEDROCK
                ? resolveBedrock(huggingBasePoseApplied)
                : resolveGecko(huggingBasePoseApplied);
    }

    private static ChestTapPose resolveBedrock(boolean huggingBasePoseApplied) {
        float lsx = huggingBasePoseApplied ? 0.16F : 0.06F;
        float lsy = huggingBasePoseApplied ? -0.20F : -0.02F;
        float lsz = huggingBasePoseApplied ? -0.15F : -0.10F;
        float rsx = huggingBasePoseApplied ? 0.16F : 0.08F;
        float rsy = huggingBasePoseApplied ? 0.20F : 0.04F;
        float rsz = huggingBasePoseApplied ? 0.15F : 0.12F;
        float lax = huggingBasePoseApplied ? -1.28F : -0.86F;
        float lay = huggingBasePoseApplied ? -0.62F : -0.24F;
        float laz = huggingBasePoseApplied ? -0.56F : -0.34F;
        float rax = huggingBasePoseApplied ? -1.28F : -1.02F;
        float ray = huggingBasePoseApplied ? 0.62F : 0.28F;
        float raz = huggingBasePoseApplied ? 0.56F : 0.46F;
        float lfx = huggingBasePoseApplied ? -0.52F : -0.92F;
        float lfy = huggingBasePoseApplied ? 0.30F : 0.12F;
        float lfz = huggingBasePoseApplied ? 0.24F : 0.10F;
        float rfx = huggingBasePoseApplied ? -0.52F : -1.20F;
        float rfy = huggingBasePoseApplied ? -0.30F : -0.18F;
        float rfz = huggingBasePoseApplied ? -0.24F : -0.12F;
        return new ChestTapPose(lsx, lsy, lsz, rsx, rsy, rsz, lax, lay, laz, rax, ray, raz, lfx, lfy, lfz, rfx, rfy, rfz);
    }

    private static ChestTapPose resolveGecko(boolean huggingBasePoseApplied) {
        float lsx = huggingBasePoseApplied ? 0.06F : 0.04F;
        float lsy = huggingBasePoseApplied ? 0.18F : 0.02F;
        float lsz = huggingBasePoseApplied ? -0.15F : -0.10F;
        float rsx = huggingBasePoseApplied ? 0.06F : 0.06F;
        float rsy = huggingBasePoseApplied ? -0.18F : -0.02F;
        float rsz = huggingBasePoseApplied ? 0.15F : 0.10F;
        float lax = huggingBasePoseApplied ? 1.18F : 0.90F;
        float lay = huggingBasePoseApplied ? 0.48F : 0.24F;
        float laz = huggingBasePoseApplied ? -0.54F : -0.30F;
        float rax = huggingBasePoseApplied ? 1.18F : 1.06F;
        float ray = huggingBasePoseApplied ? -0.48F : -0.28F;
        float raz = huggingBasePoseApplied ? 0.54F : 0.38F;
        float lfx = huggingBasePoseApplied ? 0.32F : 0.98F;
        float lfy = huggingBasePoseApplied ? -0.22F : -0.06F;
        float lfz = huggingBasePoseApplied ? 0.16F : 0.04F;
        float rfx = huggingBasePoseApplied ? 0.32F : 1.28F;
        float rfy = huggingBasePoseApplied ? 0.22F : 0.16F;
        float rfz = huggingBasePoseApplied ? -0.16F : -0.06F;
        return new ChestTapPose(lsx, lsy, lsz, rsx, rsy, rsz, lax, lay, laz, rax, ray, raz, lfx, lfy, lfz, rfx, rfy, rfz);
    }
}
