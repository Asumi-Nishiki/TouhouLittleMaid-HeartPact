package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.mixin.client.CameraLapPillowAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
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

    public static void resetRuntimeDefaults() {
        setHugFovScale(DEFAULT_HUG_FOV_SCALE);
        setCameraPitchOffsetDegrees(DEFAULT_CAMERA_PITCH_OFFSET);
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
        boolean lapPillowActive = LapPillowClientState.isLocalPlayerActive();
        boolean adultInteractionZoomActive = HugClientState.isLocalPlayerInteracting();
        boolean childInteractionZoomActive = ChildInteractionClientState.isLocalPlayerInteracting();
        if (!lapPillowActive && !adultInteractionZoomActive && !childInteractionZoomActive && clientTick > transientZoomUntilClientTick) {
            return;
        }

        double scale = lapPillowActive
                ? LapPillowPoseDebug.cameraFovScale()
                : adultInteractionZoomActive || childInteractionZoomActive ? hugFovScale : 1.0D;
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
        boolean lapPillowActive = LapPillowClientState.isLocalPlayerActive();
        if (!lapPillowActive && !isCameraAdjustmentActive() && clientTick > transientZoomUntilClientTick) {
            return;
        }

        if (lapPillowActive) {
            applyLapPillowCamera(event, minecraft);
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
     * 膝枕专用第一人称镜头。
     *
     * <p>服务端的 {@code LapPillowManager} 只负责把玩家实体锁到女仆膝边；
     * 但第一人称相机不会因为 {@code Pose.SLEEPING} 自动表现成“枕在膝上看着她”。
     * 所以这里在渲染帧里单独覆盖相机角度：不改玩家真实朝向，不影响服务端同步，
     * 只让本地玩家看到一个侧躺、抬眼看向女仆脸的镜头。
     */
    private static void applyLapPillowCamera(ViewportEvent.ComputeCameraAngles event, Minecraft minecraft) {
        /*
         * 第一人称膝枕镜头不要自动追女仆。
         * 自动朝向女仆会让“躺下看天花板”的基准永远带着斜角，调试时会很难判断到底偏在哪里。
         * 这里保留玩家当前水平朝向，只叠加调试面板里的镜头 yaw，让默认画面更接近平躺后看向正上方。
         */
        event.setYaw(event.getYaw() + LapPillowPoseDebug.cameraYawOffset());
        event.setPitch(LapPillowPoseDebug.cameraPitch() + cameraPitchOffsetDegrees * 0.35F);
        event.setRoll(LapPillowPoseDebug.cameraRoll());
        Vec3 position = event.getCamera().getPosition();
        ((CameraLapPillowAccessor) event.getCamera()).maidmarriage$setPosition(
                position.add(0.0D, LapPillowPoseDebug.cameraHeightOffset(), 0.0D)
        );
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
