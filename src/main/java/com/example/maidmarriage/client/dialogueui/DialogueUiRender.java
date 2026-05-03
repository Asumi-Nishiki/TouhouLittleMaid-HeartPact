package com.example.maidmarriage.client.dialogueui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 对话 UI 的共用绘制工具。
 */
public final class DialogueUiRender {
    private DialogueUiRender() {
    }

    public static void drawScaledText(GuiGraphics graphics, Font font, Component text,
                                      int x, int y, float scale, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, Math.round(x / scale), Math.round(y / scale), color, shadow);
        graphics.pose().popPose();
    }

    public static void drawWrappedScaledText(GuiGraphics graphics, Font font, Component text,
                                             int x, int y, int maxWidth, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawWordWrap(font, text,
                Math.round(x / scale),
                Math.round(y / scale),
                Math.round(maxWidth / scale),
                color);
        graphics.pose().popPose();
    }

    public static void blitScaled(GuiGraphics graphics, ResourceLocation texture,
                                  int x, int y, int width, int height, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(texture, x, y, 0, 0, width, height, width, height);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void fillHorizontalGradient(GuiGraphics graphics, int x, int y, int width, int height, int... colors) {
        if (width <= 0 || colors.length == 0) {
            return;
        }
        for (int col = 0; col < width; col++) {
            float progress = width == 1 ? 0.0F : col / (float) (width - 1);
            graphics.fill(x + col, y, x + col + 1, y + height, sampleGradient(colors, progress));
        }
    }

    public static void fillVerticalGradient(GuiGraphics graphics, int x, int y, int width, int height, int... colors) {
        if (height <= 0 || colors.length == 0) {
            return;
        }
        for (int row = 0; row < height; row++) {
            float progress = height == 1 ? 0.0F : row / (float) (height - 1);
            graphics.fill(x, y + row, x + width, y + row + 1, sampleGradient(colors, progress));
        }
    }

    private static int sampleGradient(int[] colors, float progress) {
        if (colors.length == 1) {
            return colors[0];
        }
        float scaled = clamp(progress, 0.0F, 1.0F) * (colors.length - 1);
        int index = Math.min(colors.length - 2, (int) scaled);
        float local = scaled - index;
        return lerpColor(colors[index], colors[index + 1], local);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
