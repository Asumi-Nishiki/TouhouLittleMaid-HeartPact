package com.example.maidmarriage.client.dialogueui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 贴图式选项卡组件。
 *
 * <p>参考项目的关键点不是“选项多复杂”，而是每个选项本身就是完整组件：
 * 它自己知道默认贴图、选中贴图、锁定贴图，以及文本如何落在卡片里。
 */
public final class DialogueOptionComponent extends DialogueUiComponent {
    private final String id;
    private final Component title;
    private final Component description;
    private ResourceLocation texture;
    private ResourceLocation selectedTexture;
    private ResourceLocation lockedTexture;
    private float titleXPercent;
    private float titleYPercent;
    private float descriptionXPercent;
    private float descriptionYPercent;
    private float titleScale;
    private float descriptionScale;
    private float titleWrapPercent;
    private float descriptionWrapPercent;
    private DialogueUiComponent.AlignX textAlign = AlignX.CENTER;
    private int titleColor;
    private int titleSelectedColor;
    private int descriptionColor;
    private int descriptionSelectedColor;
    private boolean active;
    private boolean locked;

    public DialogueOptionComponent(String id, Component title, Component description) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.renderOrder = 20;
    }

    public DialogueOptionComponent applyTheme(DialogueTheme.Option theme, int index) {
        setBounds(theme.x, theme.y + index * (theme.height + theme.gapY), theme.width, theme.height);
        setAlign(theme.alignX, theme.alignY);
        this.texture = DialogueTheme.parseTexture(theme.texture, null);
        this.selectedTexture = DialogueTheme.parseTexture(theme.selectedTexture, texture);
        this.lockedTexture = DialogueTheme.parseTexture(theme.lockedTexture, texture);
        this.titleXPercent = theme.titleX;
        this.titleYPercent = theme.titleY;
        this.descriptionXPercent = theme.descriptionX;
        this.descriptionYPercent = theme.descriptionY;
        this.titleScale = theme.titleScale;
        this.descriptionScale = theme.descriptionScale;
        this.titleWrapPercent = theme.titleWrap;
        this.descriptionWrapPercent = theme.descriptionWrap;
        this.textAlign = AlignX.fromName(theme.textAlign);
        this.titleColor = theme.titleColor;
        this.titleSelectedColor = theme.titleSelectedColor;
        this.descriptionColor = theme.descriptionColor;
        this.descriptionSelectedColor = theme.descriptionSelectedColor;
        return this;
    }

    public DialogueOptionComponent setActive(boolean active) {
        this.active = active;
        return this;
    }

    public DialogueOptionComponent setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    public DialogueOptionComponent setTextColors(int titleColor,
                                                 int titleSelectedColor,
                                                 int descriptionColor,
                                                 int descriptionSelectedColor) {
        this.titleColor = titleColor;
        this.titleSelectedColor = titleSelectedColor;
        this.descriptionColor = descriptionColor;
        this.descriptionSelectedColor = descriptionSelectedColor;
        return this;
    }

    public String id() {
        return id;
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
        boolean selected = active || hovered;
        renderHtmlSkin(graphics, left, top, widthPx, heightPx, selected, locked);

        int titleX = resolveTextX(left, widthPx, titleXPercent, titleWrapPercent);
        int descX = resolveTextX(left, widthPx, descriptionXPercent, descriptionWrapPercent);
        int titleY = top + Math.round(heightPx * (titleYPercent / 100.0F));
        int descY = top + Math.round(heightPx * (descriptionYPercent / 100.0F));
        int titleWrap = Math.round(widthPx * (titleWrapPercent / 100.0F));
        int descWrap = Math.round(widthPx * (descriptionWrapPercent / 100.0F));

        int drawTitleColor = selected ? titleSelectedColor : titleColor;
        int drawDescriptionColor = selected ? descriptionSelectedColor : descriptionColor;

        DialogueUiRender.drawWrappedScaledText(graphics, Minecraft.getInstance().font, title,
                titleX, titleY, titleWrap, titleScale, drawTitleColor);
        DialogueUiRender.drawWrappedScaledText(graphics, Minecraft.getInstance().font, description,
                descX, descY, descWrap, descriptionScale, drawDescriptionColor);
    }

    private int resolveTextX(int left, int widthPx, float offsetPercent, float wrapPercent) {
        int wrapWidth = Math.round(widthPx * (wrapPercent / 100.0F));
        if (textAlign == AlignX.CENTER) {
            return left + (widthPx - wrapWidth) / 2 + Math.round(widthPx * (offsetPercent / 100.0F));
        }
        if (textAlign == AlignX.RIGHT) {
            return left + widthPx - wrapWidth + Math.round(widthPx * (offsetPercent / 100.0F));
        }
        return left + Math.round(widthPx * (offsetPercent / 100.0F));
    }

    private void renderHtmlSkin(GuiGraphics graphics, int left, int top, int widthPx, int heightPx, boolean selected, boolean locked) {
        int borderColor = locked ? 0x66A68C98 : (selected ? 0xD1FFF5FA : 0x6BFFDFEB);
        int glowColor = locked ? 0x10261C25 : (selected ? 0x24FFE7F1 : 0x14FFE7F1);
        int start = locked ? 0x20442B3A : (selected ? 0x47FFEFF5 : 0x29FFE9F2);
        int mid = locked ? 0x12331E2B : (selected ? 0x24FFEFF5 : 0x17FFE9F2);
        int fade = locked ? 0x04211218 : (selected ? 0x0AFFF3F7 : 0x06FFE9F2);

        graphics.fill(left - 1, top - 1, left + widthPx + 1, top + heightPx + 1, glowColor);
        DialogueUiRender.fillHorizontalGradient(graphics, left, top, widthPx, heightPx, start, mid, fade, 0x00000000);
        graphics.fill(left, top, left + 3, top + heightPx, borderColor);
    }
}
