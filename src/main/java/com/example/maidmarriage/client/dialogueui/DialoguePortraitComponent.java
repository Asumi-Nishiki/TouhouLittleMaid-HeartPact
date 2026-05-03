package com.example.maidmarriage.client.dialogueui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 对话头像组件。
 *
 * <p>先支持单张贴图头像，已经足够覆盖我们当前“表情贴图 + 事件切换”的需求。
 * 以后要扩展多状态、立绘、Live2D 风格，也可以继续在这一层迭代。
 */
public final class DialoguePortraitComponent extends DialogueUiComponent {
    private ResourceLocation texture;
    private float alpha = 1.0F;

    public DialoguePortraitComponent setTexture(ResourceLocation texture) {
        this.texture = texture;
        return this;
    }

    public DialoguePortraitComponent setAlpha(float alpha) {
        this.alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        return this;
    }

    @Override
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (hidden || texture == null) {
            return;
        }
        DialogueUiRender.blitScaled(graphics, texture, x1(screenWidth), y1(screenHeight), widthPx(screenWidth), heightPx(screenHeight), alpha);
    }
}
