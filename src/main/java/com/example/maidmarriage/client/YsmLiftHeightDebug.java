package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
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
 * YSM 专用调试面板。
 *
 * <p>当前负责：
 * 1. `YSM举高高` 的高度基线；
 * 2. `YSM大女仆抱小女仆` 的左右 / 前后 / 上下三轴偏移。
 *
 * <p>按键：
 * <ul>
 *   <li>`F8` 开关面板</li>
 *   <li>`↑ / ↓` 调抱小女仆上下</li>
 *   <li>`← / →` 调抱小女仆左右</li>
 *   <li>`Ctrl + ← / →` 调抱小女仆前后</li>
 *   <li>`Shift + ↑ / ↓` 调举高高高度</li>
 *   <li>`C` 复制参数，`R` 重置参数</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class YsmLiftHeightDebug {
    private static final double DEFAULT_VISUAL_HEIGHT = -0.20D;
    private static final double DEFAULT_CARRY_CHILD_VISUAL_OFFSET_X = 0.75D;
    private static final double DEFAULT_CARRY_CHILD_VISUAL_OFFSET_Z = -0.35D;
    private static final double DEFAULT_CARRY_CHILD_VISUAL_HEIGHT = 0.50D;

    private static boolean enabled;
    private static double visualHeight = DEFAULT_VISUAL_HEIGHT;
    private static double carryChildVisualOffsetX = DEFAULT_CARRY_CHILD_VISUAL_OFFSET_X;
    private static double carryChildVisualOffsetZ = DEFAULT_CARRY_CHILD_VISUAL_OFFSET_Z;
    private static double carryChildVisualHeight = DEFAULT_CARRY_CHILD_VISUAL_HEIGHT;

    private static boolean lastF8;
    private static boolean lastUp;
    private static boolean lastDown;
    private static boolean lastLeft;
    private static boolean lastRight;
    private static boolean lastCopy;
    private static boolean lastReset;

    private YsmLiftHeightDebug() {
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
        if (mc.player == null || mc.level == null || mc.screen != null) {
            clearEdges();
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean f8 = isDown(window, GLFW.GLFW_KEY_F8);
        if (pressed(f8, lastF8)) {
            enabled = !enabled;
            mc.player.displayClientMessage(Component.literal(enabled
                    ? "YSM高度调试：开启"
                    : "YSM高度调试：关闭"), true);
        }
        lastF8 = f8;

        if (!enabled) {
            clearAdjustEdgesOnly();
            return;
        }

        boolean shift = isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean ctrl = isDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || isDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean up = isDown(window, GLFW.GLFW_KEY_UP);
        boolean down = isDown(window, GLFW.GLFW_KEY_DOWN);
        boolean left = isDown(window, GLFW.GLFW_KEY_LEFT);
        boolean right = isDown(window, GLFW.GLFW_KEY_RIGHT);
        boolean copy = isDown(window, GLFW.GLFW_KEY_C);
        boolean reset = isDown(window, GLFW.GLFW_KEY_R);

        if (pressed(up, lastUp)) {
            if (shift) {
                visualHeight += 0.05D;
            } else {
                carryChildVisualHeight += 0.05D;
            }
        }
        if (pressed(down, lastDown)) {
            if (shift) {
                visualHeight -= 0.05D;
            } else {
                carryChildVisualHeight -= 0.05D;
            }
        }
        if (pressed(left, lastLeft)) {
            if (ctrl) {
                carryChildVisualOffsetZ -= 0.05D;
            } else {
                carryChildVisualOffsetX -= 0.05D;
            }
        }
        if (pressed(right, lastRight)) {
            if (ctrl) {
                carryChildVisualOffsetZ += 0.05D;
            } else {
                carryChildVisualOffsetX += 0.05D;
            }
        }
        if (pressed(reset, lastReset)) {
            visualHeight = DEFAULT_VISUAL_HEIGHT;
            carryChildVisualOffsetX = DEFAULT_CARRY_CHILD_VISUAL_OFFSET_X;
            carryChildVisualOffsetZ = DEFAULT_CARRY_CHILD_VISUAL_OFFSET_Z;
            carryChildVisualHeight = DEFAULT_CARRY_CHILD_VISUAL_HEIGHT;
            mc.player.displayClientMessage(Component.literal("YSM高度调试：已恢复默认值"), true);
        }
        if (pressed(copy, lastCopy)) {
            mc.keyboardHandler.setClipboard(exportValues());
            mc.player.displayClientMessage(Component.literal("YSM高度调试参数已复制到剪贴板"), true);
        }

        lastUp = up;
        lastDown = down;
        lastLeft = left;
        lastRight = right;
        lastCopy = copy;
        lastReset = reset;
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
        String title = "YSM高度调试";
        String liftValue = String.format(Locale.ROOT, "举高高: %.2f", visualHeight);
        String carryX = String.format(Locale.ROOT, "抱小女仆 左右: %.2f", carryChildVisualOffsetX);
        String carryZ = String.format(Locale.ROOT, "抱小女仆 前后: %.2f", carryChildVisualOffsetZ);
        String carryY = String.format(Locale.ROOT, "抱小女仆 上下: %.2f", carryChildVisualHeight);
        String help1 = "↑/↓ 调抱小女仆上下";
        String help2 = "←/→ 调抱小女仆左右";
        String help3 = "Ctrl+←/→ 调抱小女仆前后";
        String help4 = "Shift+↑/↓ 调举高高";
        String help5 = "C复制  R重置";

        int width = Math.max(
                Math.max(
                        Math.max(font.width(title), Math.max(font.width(liftValue), Math.max(font.width(carryX), Math.max(font.width(carryZ), font.width(carryY))))),
                        Math.max(font.width(help1), Math.max(font.width(help2), Math.max(font.width(help3), font.width(help4))))
                ),
                font.width(help5)
        ) + 18;

        int x = mc.getWindow().getGuiScaledWidth() - width - 10;
        int y = 42;

        event.getGuiGraphics().fill(x - 5, y - 5, x + width, y + 100, 0xAA1E1024);
        event.getGuiGraphics().fill(x - 5, y - 5, x + width, y - 4, 0xFFFF8BCF);
        draw(event, font, x, y, title, 0xFFFFE08A);
        draw(event, font, x, y + 12, liftValue, 0xFF8BFF98);
        draw(event, font, x, y + 24, carryX, 0xFF8BD7FF);
        draw(event, font, x, y + 36, carryZ, 0xFF8BD7FF);
        draw(event, font, x, y + 48, carryY, 0xFF8BD7FF);
        draw(event, font, x, y + 62, help1, 0xFFE8E8E8);
        draw(event, font, x, y + 74, help2, 0xFFE8E8E8);
        draw(event, font, x, y + 86, help3, 0xFFE8E8E8);
        draw(event, font, x, y + 98, help4, 0xFFE8E8E8);
        draw(event, font, x + 118, y + 98, help5, 0xFFE8E8E8);
    }

    public static double visualHeight() {
        return visualHeight;
    }

    public static double carryChildVisualOffsetX() {
        return carryChildVisualOffsetX;
    }

    public static double carryChildVisualOffsetZ() {
        return carryChildVisualOffsetZ;
    }

    public static double carryChildVisualHeight() {
        return carryChildVisualHeight;
    }

    public static double resolveVisualHeight(double configuredLiftHeight) {
        return visualHeight + (configuredLiftHeight - ModConfigs.DEFAULT_LIFT_HEIGHT);
    }

    private static String exportValues() {
        return String.format(
                Locale.ROOT,
                "ysmLiftVisualHeight=%.2f%nymCarryChildVisualOffsetX=%.2f%nymCarryChildVisualOffsetZ=%.2f%nymCarryChildVisualHeight=%.2f",
                visualHeight,
                carryChildVisualOffsetX,
                carryChildVisualOffsetZ,
                carryChildVisualHeight
        );
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

    private static void clearEdges() {
        lastF8 = false;
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
