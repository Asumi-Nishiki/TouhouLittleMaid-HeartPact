package com.example.maidmarriage.client.dialogueui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 可复用 UI 组件基类。
 *
 * <p>和参考项目一样，组件自己维护：
 * - 百分比布局；
 * - 对齐方式；
 * - hover 状态；
 * - 绘制层级。
 *
 * <p>这样具体 Screen 只负责交互调度，不再写一屏幕常量坐标。
 */
public abstract class DialogueUiComponent {
    public float x;
    public float y;
    public float width;
    public float height;
    public AlignX alignX = AlignX.LEFT;
    public AlignY alignY = AlignY.TOP;
    public int renderOrder;
    public boolean hidden;
    public boolean hovered;

    public DialogueUiComponent setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public DialogueUiComponent setAlign(String alignX, String alignY) {
        this.alignX = AlignX.fromName(alignX);
        this.alignY = AlignY.fromName(alignY);
        return this;
    }

    public DialogueUiComponent setAlign(AlignX alignX, AlignY alignY) {
        this.alignX = alignX;
        this.alignY = alignY;
        return this;
    }

    public int x1(int screenWidth) {
        int raw = Math.round(screenWidth * (x / 100.0F));
        int widthPx = widthPx(screenWidth);
        return switch (alignX) {
            case LEFT -> raw;
            case CENTER -> raw + (screenWidth - widthPx) / 2;
            case RIGHT -> raw + screenWidth - widthPx;
        };
    }

    public int y1(int screenHeight) {
        int raw = Math.round(screenHeight * (y / 100.0F));
        int heightPx = heightPx(screenHeight);
        return switch (alignY) {
            case TOP -> raw;
            case CENTER -> raw + (screenHeight - heightPx) / 2;
            case BOTTOM -> raw + screenHeight - heightPx;
        };
    }

    public int widthPx(int screenWidth) {
        return Math.round(screenWidth * (width / 100.0F));
    }

    public int heightPx(int screenHeight) {
        return Math.round(screenHeight * (height / 100.0F));
    }

    public boolean contains(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        int left = x1(screenWidth);
        int top = y1(screenHeight);
        int right = left + widthPx(screenWidth);
        int bottom = top + heightPx(screenHeight);
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    public void updateHover(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.hovered = contains(mouseX, mouseY, screenWidth, screenHeight);
    }

    public abstract void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY);

    public enum AlignX {
        LEFT,
        CENTER,
        RIGHT;

        public static AlignX fromName(String raw) {
            if (raw == null) {
                return LEFT;
            }
            return switch (raw.toLowerCase()) {
                case "center" -> CENTER;
                case "right" -> RIGHT;
                default -> LEFT;
            };
        }
    }

    public enum AlignY {
        TOP,
        CENTER,
        BOTTOM;

        public static AlignY fromName(String raw) {
            if (raw == null) {
                return TOP;
            }
            return switch (raw.toLowerCase()) {
                case "center" -> CENTER;
                case "bottom" -> BOTTOM;
                default -> TOP;
            };
        }
    }
}
