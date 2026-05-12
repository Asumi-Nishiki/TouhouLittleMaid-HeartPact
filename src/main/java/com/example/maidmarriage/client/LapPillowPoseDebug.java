package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.LapPillowDebugPosePayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 膝枕姿态调试面板。
 *
 * <p>这些值只用于当前客户端调试。位置和睡姿朝向会同步给服务端用于锁位；
 * 镜头高度、镜头缩放、镜头俯仰和侧倾只影响本地第一人称画面。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LapPillowPoseDebug {
    public static final double MIN_CAMERA_HEIGHT_OFFSET = -3.00D;
    public static final double MAX_CAMERA_HEIGHT_OFFSET = 3.00D;
    public static final double MIN_CAMERA_FOV_SCALE = 0.10D;
    public static final double MAX_CAMERA_FOV_SCALE = 2.00D;

    private static final double DEFAULT_SIDE_OFFSET = 0.10D;
    private static final double DEFAULT_HEIGHT_OFFSET = 0.25D;
    private static final double DEFAULT_FORWARD_OFFSET = 0.45D;
    private static final float DEFAULT_YAW_OFFSET = -90.0F;
    private static final float DEFAULT_CAMERA_YAW_OFFSET = 0.0F;
    private static final float DEFAULT_CAMERA_PITCH = -90.0F;
    private static final float DEFAULT_CAMERA_ROLL = 0.0F;
    private static final double DEFAULT_CAMERA_HEIGHT_OFFSET = -1.50D;
    private static final double DEFAULT_CAMERA_FOV_SCALE = 1.25D;

    private static boolean enabled;
    private static double sideOffset = DEFAULT_SIDE_OFFSET;
    private static double heightOffset = DEFAULT_HEIGHT_OFFSET;
    private static double forwardOffset = DEFAULT_FORWARD_OFFSET;
    private static float yawOffset = DEFAULT_YAW_OFFSET;
    private static float cameraYawOffset = DEFAULT_CAMERA_YAW_OFFSET;
    private static float cameraPitch = DEFAULT_CAMERA_PITCH;
    private static float cameraRoll = DEFAULT_CAMERA_ROLL;
    private static double cameraHeightOffset = DEFAULT_CAMERA_HEIGHT_OFFSET;
    private static double cameraFovScale = DEFAULT_CAMERA_FOV_SCALE;

    private static boolean lastF9;
    private static boolean lastUp;
    private static boolean lastDown;
    private static boolean lastLeft;
    private static boolean lastRight;
    private static boolean lastCopy;
    private static boolean lastReset;

    private LapPillowPoseDebug() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ModConfigs.enableDebugTools()) {
            enabled = false;
            clearEdges();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clearEdges();
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean f9 = isDown(window, GLFW.GLFW_KEY_F9);
        if (pressed(f9, lastF9)) {
            enabled = !enabled;
            mc.player.displayClientMessage(Component.literal(enabled ? "膝枕调试：开启" : "膝枕调试：关闭"), true);
            if (enabled) {
                syncServerPose();
            }
        }
        lastF9 = f9;

        if (!enabled) {
            clearAdjustEdgesOnly();
            return;
        }

        boolean shift = isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean ctrl = isDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || isDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean alt = isDown(window, GLFW.GLFW_KEY_LEFT_ALT) || isDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
        boolean up = isDown(window, GLFW.GLFW_KEY_UP);
        boolean down = isDown(window, GLFW.GLFW_KEY_DOWN);
        boolean left = isDown(window, GLFW.GLFW_KEY_LEFT);
        boolean right = isDown(window, GLFW.GLFW_KEY_RIGHT);
        boolean copy = isDown(window, GLFW.GLFW_KEY_C);
        boolean reset = isDown(window, GLFW.GLFW_KEY_R);

        boolean serverPoseChanged = false;
        if (pressed(up, lastUp)) {
            if (alt && ctrl) {
                setCameraHeightOffset(cameraHeightOffset + (shift ? 0.01D : 0.05D));
            } else if (alt) {
                cameraPitch += shift ? 1.0F : 5.0F;
            } else {
                heightOffset += shift ? 0.01D : 0.05D;
                serverPoseChanged = true;
            }
        }
        if (pressed(down, lastDown)) {
            if (alt && ctrl) {
                setCameraHeightOffset(cameraHeightOffset - (shift ? 0.01D : 0.05D));
            } else if (alt) {
                cameraPitch -= shift ? 1.0F : 5.0F;
            } else {
                heightOffset -= shift ? 0.01D : 0.05D;
                serverPoseChanged = true;
            }
        }
        if (pressed(left, lastLeft)) {
            if (alt && ctrl) {
                setCameraFovScale(cameraFovScale - (shift ? 0.01D : 0.05D));
            } else if (alt) {
                yawOffset -= shift ? 1.0F : 5.0F;
                serverPoseChanged = true;
            } else if (ctrl) {
                forwardOffset -= shift ? 0.01D : 0.05D;
                serverPoseChanged = true;
            } else {
                sideOffset -= shift ? 0.01D : 0.05D;
                serverPoseChanged = true;
            }
        }
        if (pressed(right, lastRight)) {
            if (alt && ctrl) {
                setCameraFovScale(cameraFovScale + (shift ? 0.01D : 0.05D));
            } else if (alt) {
                yawOffset += shift ? 1.0F : 5.0F;
                serverPoseChanged = true;
            } else if (ctrl) {
                forwardOffset += shift ? 0.01D : 0.05D;
                serverPoseChanged = true;
            } else {
                sideOffset += shift ? 0.01D : 0.05D;
                serverPoseChanged = true;
            }
        }
        if (pressed(reset, lastReset)) {
            resetDefaults();
            syncServerPose();
            mc.player.displayClientMessage(Component.literal("膝枕调试：已恢复默认值"), true);
        }
        if (pressed(copy, lastCopy)) {
            mc.keyboardHandler.setClipboard(exportValues());
            mc.player.displayClientMessage(Component.literal("膝枕调试参数已复制到剪贴板"), true);
        }

        lastUp = up;
        lastDown = down;
        lastLeft = left;
        lastRight = right;
        lastCopy = copy;
        lastReset = reset;

        if (serverPoseChanged) {
            syncServerPose();
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (!enabled || !ModConfigs.enableDebugTools()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        Font font = mc.font;
        int x = 10;
        int y = 42;
        draw(event, font, x, y, "膝枕调试 F9", 0xFFFFE08A);
        draw(event, font, x, y + 12, String.format(Locale.ROOT, "左右 side: %.2f", sideOffset), 0xFF8BD7FF);
        draw(event, font, x, y + 24, String.format(Locale.ROOT, "高度 height: %.2f", heightOffset), 0xFF8BD7FF);
        draw(event, font, x, y + 36, String.format(Locale.ROOT, "前后 forward: %.2f", forwardOffset), 0xFF8BD7FF);
        draw(event, font, x, y + 48, String.format(Locale.ROOT, "朝向 yawOffset: %.1f", yawOffset), 0xFF8BFF98);
        draw(event, font, x, y + 64, String.format(Locale.ROOT, "镜头 yaw/pitch/roll: %.1f / %.1f / %.1f", cameraYawOffset, cameraPitch, cameraRoll), 0xFFFFC078);
        draw(event, font, x, y + 76, String.format(Locale.ROOT, "镜头高度/FOV: %.2f / %.2f", cameraHeightOffset, cameraFovScale), 0xFFFFC078);
        draw(event, font, x, y + 96, "↑/↓ 高度  ←/→ 左右  Ctrl+←/→ 前后", 0xFFE8E8E8);
        draw(event, font, x, y + 108, "Alt+←/→ 睡姿朝向  Alt+↑/↓ 镜头俯仰", 0xFFE8E8E8);
        draw(event, font, x, y + 120, "Ctrl+Alt+↑/↓ 镜头高低  Ctrl+Alt+←/→ 缩放", 0xFFE8E8E8);
        draw(event, font, x, y + 132, "Shift 微调  C复制  R重置", 0xFFE8E8E8);
    }

    public static float cameraYawOffset() {
        return cameraYawOffset;
    }

    public static float cameraPitch() {
        return cameraPitch;
    }

    public static float cameraRoll() {
        return cameraRoll;
    }

    public static double cameraHeightOffset() {
        return cameraHeightOffset;
    }

    public static void setCameraHeightOffset(double value) {
        cameraHeightOffset = clamp(value, MIN_CAMERA_HEIGHT_OFFSET, MAX_CAMERA_HEIGHT_OFFSET);
    }

    public static double cameraFovScale() {
        return cameraFovScale;
    }

    public static void setCameraFovScale(double value) {
        cameraFovScale = clamp(value, MIN_CAMERA_FOV_SCALE, MAX_CAMERA_FOV_SCALE);
    }

    public static String cameraHeightLabel() {
        return String.format(Locale.ROOT, "%+.2f", cameraHeightOffset);
    }

    public static String cameraFovLabel() {
        return String.format(Locale.ROOT, "%.0f%%", (1.0D / cameraFovScale) * 100.0D);
    }

    public static void resetDefaults() {
        sideOffset = DEFAULT_SIDE_OFFSET;
        heightOffset = DEFAULT_HEIGHT_OFFSET;
        forwardOffset = DEFAULT_FORWARD_OFFSET;
        yawOffset = DEFAULT_YAW_OFFSET;
        cameraYawOffset = DEFAULT_CAMERA_YAW_OFFSET;
        cameraPitch = DEFAULT_CAMERA_PITCH;
        cameraRoll = DEFAULT_CAMERA_ROLL;
        cameraHeightOffset = DEFAULT_CAMERA_HEIGHT_OFFSET;
        cameraFovScale = DEFAULT_CAMERA_FOV_SCALE;
    }

    private static String exportValues() {
        return String.format(
                Locale.ROOT,
                "lapPillowSideOffset=%.2f%nlapPillowHeightOffset=%.2f%nlapPillowForwardOffset=%.2f%nlapPillowYawOffset=%.1f%nlapPillowCameraYawOffset=%.1f%nlapPillowCameraPitch=%.1f%nlapPillowCameraRoll=%.1f%nlapPillowCameraHeightOffset=%.2f%nlapPillowCameraFovScale=%.2f",
                sideOffset,
                heightOffset,
                forwardOffset,
                yawOffset,
                cameraYawOffset,
                cameraPitch,
                cameraRoll,
                cameraHeightOffset,
                cameraFovScale
        );
    }

    private static void syncServerPose() {
        ModNetworking.sendLapPillowDebugPose(new LapPillowDebugPosePayload(sideOffset, heightOffset, forwardOffset, yawOffset));
    }

    private static void draw(RenderGuiOverlayEvent.Post event, Font font, int x, int y, String text, int color) {
        event.getGuiGraphics().drawString(font, text, x, y, color, true);
    }

    private static boolean isDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static boolean pressed(boolean current, boolean previous) {
        return current && !previous;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void clearEdges() {
        lastF9 = false;
        clearAdjustEdgesOnly();
    }

    private static void clearAdjustEdgesOnly() {
        lastUp = false;
        lastDown = false;
        lastLeft = false;
        lastRight = false;
        lastCopy = false;
        lastReset = false;
    }
}
