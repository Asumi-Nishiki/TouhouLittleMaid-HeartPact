package com.example.maidmarriage.client.dialogueui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 角落图标按钮组件。
 */
public final class DialogueIconButtonComponent extends DialogueUiComponent {
    private ResourceLocation iconTexture;
    private int iconSize = 24;
    private int inset = 4;
    private int backgroundColor = 0x22000000;
    private int hoverColor = 0x44000000;
    private boolean chromeEnabled = true;

    public DialogueIconButtonComponent setIconTexture(ResourceLocation iconTexture) {
        this.iconTexture = iconTexture;
        return this;
    }

    /**
     * 是否绘制底框和背景。
     *
     * <p>拥抱 UI 右下角这一排工具按钮现在更适合做成“纯贴图”风格，
     * 所以这里提供一个开关，让按钮可以只显示图标本体。
     */
    public DialogueIconButtonComponent setChromeEnabled(boolean chromeEnabled) {
        this.chromeEnabled = chromeEnabled;
        return this;
    }

    public DialogueIconButtonComponent applyTheme(DialogueTheme.ControlIcon theme) {
        setBounds(theme.x, theme.y, theme.width, theme.height);
        setAlign(theme.alignX, theme.alignY);
        this.iconSize = theme.iconSize;
        this.inset = theme.inset;
        this.backgroundColor = theme.backgroundColor;
        this.hoverColor = theme.hoverColor;
        return this;
    }

    @Override
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (hidden) {
            return;
        }
        updateHover(mouseX, mouseY, screenWidth, screenHeight);
        int left = x1(screenWidth);
        int top = y1(screenHeight);
        int widthPx = widthPx(screenWidth);
        int heightPx = heightPx(screenHeight);
        if (chromeEnabled) {
            int drawColor = hovered ? hoverColor : backgroundColor;
            graphics.fill(left, top, left + widthPx, top + heightPx, drawColor);
            graphics.fill(left, top, left + widthPx, top + 1, 0x66FFF2F8);
            graphics.fill(left, top, left + 1, top + heightPx, 0x40FFF2F8);
        }
        if (iconTexture != null) {
            int size = Math.max(6, Math.min(iconSize, Math.min(widthPx - inset * 2, heightPx - inset * 2)));
            int iconX = left + (widthPx - size) / 2;
            int iconY = top + (heightPx - size) / 2;
            DialogueUiRender.blitScaled(graphics, iconTexture, iconX, iconY, size, size, 1.0F);
        }
    }
}
