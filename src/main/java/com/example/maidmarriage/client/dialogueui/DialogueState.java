package com.example.maidmarriage.client.dialogueui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 运行时对话数据。
 *
 * <p>主题控制“长什么样”，状态控制“此刻显示什么内容”。这样就完成了解耦，别的功能也能直接复用。
 */
public final class DialogueState {
    private String speaker = "";
    private String text = "";
    private String hint = "";
    private ResourceLocation portraitTexture;

    public String speaker() {
        return speaker;
    }

    public String text() {
        return text;
    }

    public String hint() {
        return hint;
    }

    public ResourceLocation portraitTexture() {
        return portraitTexture;
    }

    public DialogueState setSpeaker(String speaker) {
        this.speaker = speaker == null ? "" : speaker;
        return this;
    }

    public DialogueState setText(Component text) {
        this.text = text == null ? "" : text.getString();
        return this;
    }

    public DialogueState setHint(Component hint) {
        this.hint = hint == null ? "" : hint.getString();
        return this;
    }

    public DialogueState setPortraitTexture(ResourceLocation portraitTexture) {
        this.portraitTexture = portraitTexture;
        return this;
    }
}
