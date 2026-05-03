package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拥抱与亲吻专用的第一人称视角缩放。
 *
 * <p>拥抱距离如果为了画面好看而设得太近，玩家和女仆碰撞箱就容易互相挤压。
 * 因此这里不再继续依赖“实体贴近”制造亲密感，而是在第一人称下压低 FOV。
 * 实体可以保持更安全的物理距离，玩家看到的画面仍然像靠近了女仆。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HugCameraZoom {
    private static final double MIN_HUG_FOV_SCALE = 0.34D;
    private static final double MAX_HUG_FOV_SCALE = 0.96D;
    private static final double DEFAULT_HUG_FOV_SCALE = 0.58D;
    private static final float MIN_CAMERA_PITCH_OFFSET = -24.0F;
    private static final float MAX_CAMERA_PITCH_OFFSET = 24.0F;
    private static final float DEFAULT_CAMERA_PITCH_OFFSET = 0.0F;
    private static final double KISS_FOV_SCALE = 0.32D;
    private static final long KISS_ZOOM_TICKS = 18L;
    private static final double STORY_CLOSE_FOV_SCALE = 0.44D;
    private static final long STORY_CLOSE_ZOOM_TICKS = 16L;

    private static double hugFovScale = DEFAULT_HUG_FOV_SCALE;
    private static float cameraPitchOffsetDegrees = DEFAULT_CAMERA_PITCH_OFFSET;
    private static long transientZoomUntilClientTick = 0L;
    private static double transientZoomScale = 1.0D;
    private static long clientTick = 0L;

    static {
        HugCameraSettingsStore.Settings settings = HugCameraSettingsStore.loadOrDefault(defaultSettings());
        setHugFovScale(settings.fovScale());
        setCameraPitchOffsetDegrees(settings.pitchOffsetDegrees());
    }

    private HugCameraZoom() {
    }

    /**
     * 鼠标滚轮调整拥抱时的第一人称缩放。
     * 滚轮向上会拉近画面，滚轮向下会拉远画面。
     */
    public static void adjustHugZoom(double scrollDelta) {
        setHugFovScale(hugFovScale - scrollDelta * 0.065D);
    }

    /**
     * 亲吻成功后触发短时更强的拉近效果。
     * 这个入口由服务端确认亲吻成立后回包调用，避免客户端误播。
     */
    public static void playKissZoom() {
        playTransientZoom(KISS_FOV_SCALE, KISS_ZOOM_TICKS);
    }

    /**
     * 剧情里“稍微靠近一点”的通用镜头拉近。
     *
     * <p>它比亲吻镜头更轻，不会一下子压到那么近；
     * 适合暧昧、告白前、轻轻凑近之类的文本演出。
     */
    public static void playStoryCloseZoom() {
        playTransientZoom(STORY_CLOSE_FOV_SCALE, STORY_CLOSE_ZOOM_TICKS);
    }

    public static void playTransientZoom(double targetScale, long durationTicks) {
        transientZoomScale = clamp(targetScale, MIN_HUG_FOV_SCALE, 1.0D);
        transientZoomUntilClientTick = Math.max(transientZoomUntilClientTick, clientTick + Math.max(1L, durationTicks));
    }

    public static double currentHugFovScale() {
        return hugFovScale;
    }

    public static void setHugFovScale(double value) {
        hugFovScale = clamp(value, MIN_HUG_FOV_SCALE, MAX_HUG_FOV_SCALE);
    }

    public static double minHugFovScale() {
        return MIN_HUG_FOV_SCALE;
    }

    public static double maxHugFovScale() {
        return MAX_HUG_FOV_SCALE;
    }

    public static float currentCameraPitchOffsetDegrees() {
        return cameraPitchOffsetDegrees;
    }

    public static void setCameraPitchOffsetDegrees(float value) {
        cameraPitchOffsetDegrees = (float) clamp(value, MIN_CAMERA_PITCH_OFFSET, MAX_CAMERA_PITCH_OFFSET);
    }

    public static float minCameraPitchOffsetDegrees() {
        return MIN_CAMERA_PITCH_OFFSET;
    }

    public static float maxCameraPitchOffsetDegrees() {
        return MAX_CAMERA_PITCH_OFFSET;
    }

    public static boolean savePersistentSettings() {
        return HugCameraSettingsStore.save(new HugCameraSettingsStore.Settings(hugFovScale, cameraPitchOffsetDegrees));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            clientTick++;
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        boolean adultInteractionZoomActive = HugClientState.isLocalPlayerInteracting();
        boolean childInteractionZoomActive = ChildInteractionClientState.isLocalPlayerInteracting();
        if (!adultInteractionZoomActive && !childInteractionZoomActive && clientTick > transientZoomUntilClientTick) {
            return;
        }

        double scale = adultInteractionZoomActive || childInteractionZoomActive ? hugFovScale : 1.0D;
        if (clientTick <= transientZoomUntilClientTick) {
            scale = Math.min(scale, transientZoomScale);
        }
        event.setFOV(event.getFOV() * scale);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (!isCameraAdjustmentActive() && clientTick > transientZoomUntilClientTick) {
            return;
        }

        /*
         * 这里只改当前渲染帧的相机 pitch，不改玩家真实视角和实体朝向。
         * 正值会让镜头略微低头，负值会让镜头略微抬头；
         * 用来适配不同模型高低，避免亲吻/拥抱时画面对不到脸。
         */
        event.setPitch(event.getPitch() + cameraPitchOffsetDegrees);
    }

    /**
     * UI 上显示当前缩放倍率，方便测试时知道滚轮调到了哪里。
     */
    public static String zoomLabel() {
        return String.format(java.util.Locale.ROOT, "%.0f%%", (1.0D / hugFovScale) * 100.0D);
    }

    public static String pitchOffsetLabel() {
        return String.format(java.util.Locale.ROOT, "%+.1f°", cameraPitchOffsetDegrees);
    }

    private static boolean isCameraAdjustmentActive() {
        return HugClientState.isLocalPlayerInteracting() || ChildInteractionClientState.isLocalPlayerInteracting();
    }

    private static HugCameraSettingsStore.Settings defaultSettings() {
        return new HugCameraSettingsStore.Settings(DEFAULT_HUG_FOV_SCALE, DEFAULT_CAMERA_PITCH_OFFSET);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
