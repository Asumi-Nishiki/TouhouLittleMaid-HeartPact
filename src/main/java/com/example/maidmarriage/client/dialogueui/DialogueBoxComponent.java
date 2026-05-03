package com.example.maidmarriage.client.dialogueui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 贴图式主对话框组件。
 *
 * <p>这里直接沿用参考项目那种职责划分：
 * - 对话框自己维护打字机状态；
 * - 自己处理贴图背景；
 * - Screen 只负责告诉它当前该显示什么内容。
 */
public final class DialogueBoxComponent extends DialogueUiComponent {
    private ResourceLocation texture;
    private String speaker = "";
    private String fullText = "";
    private String shownText = "";
    private String hint = "";
    private float typeProgress;

    private float lineWidthPercent = 70.0F;
    private float nameXPercent = 20.0F;
    private float nameYPercent = 10.0F;
    private float textXPercent = 20.0F;
    private float textYPercent = 15.0F;
    private float hintXPercent = 20.0F;
    private float hintYPercent = 72.0F;
    private float speakerScale = 1.0F;
    private float textScale = 1.0F;
    private float hintScale = 0.8F;
    private int speakerColor = 0xFFFFFFFF;
    private int textColor = 0xFFFFFFFF;
    private int hintColor = 0xD9F6D7E6;

    public DialogueBoxComponent() {
        this.renderOrder = 10;
    }

    public DialogueBoxComponent applyTheme(DialogueTheme.DialogBox theme) {
        setBounds(theme.x, theme.y, theme.width, theme.height);
        setAlign(theme.alignX, theme.alignY);
        this.texture = DialogueTheme.parseTexture(theme.texture, null);
        this.lineWidthPercent = theme.lineWidth;
        this.nameXPercent = theme.nameX;
        this.nameYPercent = theme.nameY;
        this.textXPercent = theme.textX;
        this.textYPercent = theme.textY;
        this.hintXPercent = theme.hintX;
        this.hintYPercent = theme.hintY;
        this.speakerScale = theme.speakerScale;
        this.textScale = theme.textScale;
        this.hintScale = theme.hintScale;
        this.speakerColor = theme.speakerColor;
        this.textColor = theme.textColor;
        this.hintColor = theme.hintColor;
        return this;
    }

    public DialogueBoxComponent setSpeaker(String speaker) {
        this.speaker = speaker == null ? "" : speaker;
        return this;
    }

    public DialogueBoxComponent setFullText(String text, boolean resetTypewriter) {
        this.fullText = text == null ? "" : text;
        if (resetTypewriter) {
            this.shownText = "";
            this.typeProgress = 0.0F;
        } else {
            this.shownText = this.fullText;
            this.typeProgress = this.fullText.length();
        }
        return this;
    }

    public DialogueBoxComponent setHint(String hint) {
        this.hint = hint == null ? "" : hint;
        return this;
    }

    public void tickTypewriter() {
        tickTypewriter(1.0F);
    }

    public void tickTypewriter(float speedMultiplier) {
        this.typeProgress = Math.min(fullText.length(), typeProgress + 0.95F * Math.max(1.0F, speedMultiplier));
        int visibleChars = Math.max(0, Math.min(fullText.length(), (int) typeProgress));
        this.shownText = fullText.substring(0, visibleChars);
    }

    public void revealAll() {
        this.typeProgress = fullText.length();
        this.shownText = fullText;
    }

    public boolean isComplete() {
        return shownText.length() >= fullText.length();
    }

    @Override
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (hidden) {
            return;
        }
        int left = x1(screenWidth);
        int top = y1(screenHeight);
        int widthPx = widthPx(screenWidth);
        int heightPx = heightPx(screenHeight);
        renderHtmlSkin(graphics, left, top, widthPx, heightPx);

        int nameX = left + Math.round(widthPx * (nameXPercent / 100.0F));
        int nameY = top + Math.round(heightPx * (nameYPercent / 100.0F));
        int textX = left + Math.round(widthPx * (textXPercent / 100.0F));
        int textY = top + Math.round(heightPx * (textYPercent / 100.0F));
        int hintX = left + Math.round(widthPx * (hintXPercent / 100.0F));
        int hintY = top + Math.round(heightPx * (hintYPercent / 100.0F));
        int wrapWidth = Math.round(widthPx * (lineWidthPercent / 100.0F));

        DialogueUiRender.drawScaledText(graphics, Minecraft.getInstance().font, Component.literal(speaker),
                nameX, nameY, speakerScale, speakerColor, false);
        DialogueUiRender.drawWrappedScaledText(graphics, Minecraft.getInstance().font, Component.literal(shownText),
                textX, textY, wrapWidth, textScale, textColor);
        DialogueUiRender.drawWrappedScaledText(graphics, Minecraft.getInstance().font, Component.literal(hint),
                hintX, hintY, wrapWidth, hintScale, hintColor);
    }

    private void renderHtmlSkin(GuiGraphics graphics, int left, int top, int widthPx, int heightPx) {
        int shadowTop = top - Math.max(14, heightPx / 3);
        DialogueUiRender.fillVerticalGradient(graphics, left, shadowTop, widthPx, top - shadowTop,
                0x00000000, 0x120F0811, 0x26110A14, 0x3D140C18);

        DialogueUiRender.fillVerticalGradient(graphics, left, top, widthPx, heightPx,
                0x0A3D1C37,
                0x2E3D1C37,
                0x8A2E142A,
                0xD13D1C37,
                0xF21C0C1A);

        int softWidth = Math.round(widthPx * 0.42F);
        DialogueUiRender.fillHorizontalGradient(graphics, left, top, softWidth, heightPx,
                0x22FFDDEE, 0x14FFDDEE, 0x08FFDDEE, 0x00FFDDEE);

        graphics.fill(left, top, left + widthPx, top + 1, 0x8CFFF0F6);
        graphics.fill(left, top + 1, left + widthPx, top + 2, 0x46F6D7E6);
    }
}
