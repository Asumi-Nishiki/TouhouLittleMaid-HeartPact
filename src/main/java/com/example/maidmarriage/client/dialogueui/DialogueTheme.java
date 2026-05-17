package com.example.maidmarriage.client.dialogueui;

import net.minecraft.resources.ResourceLocation;

/**
 * 可复用对话框主题数据。
 *
 * <p>这层的目标不是只服务当前拥抱界面，而是把“贴图式对话框 / 选项 / 头像 / 角落按钮”
 * 统一收口成可配置主题。之后如果我们要做亲吻、剧情、出生事件、任务对话，都可以继续复用。
 */
public class DialogueTheme {
    public Background background = new Background();
    public Portrait portrait = new Portrait();
    public DialogBox dialogBox = new DialogBox();
    public Option option = new Option();
    public ControlIcon controlIcon = new ControlIcon();
    public ZoomLabel zoomLabel = new ZoomLabel();

    public static class LayoutBlock {
        public float x;
        public float y;
        public float width;
        public float height;
        public String alignX = "left";
        public String alignY = "top";
    }

    public static class Background {
        public float lineY = 47.0F;
        public float lineX = 3.0F;
        public float lineWidth = 74.0F;
    }

    public static class Portrait extends LayoutBlock {
        {
            this.x = 4.0F;
            this.y = 52.0F;
            this.width = 13.0F;
            this.height = 23.0F;
            this.alignX = "left";
            this.alignY = "top";
        }
        public boolean visible = true;
        public String texture = "maidmarriage:textures/gui/emotion/soft_smile.png";
        public float alpha = 1.0F;
    }

    public static class DialogBox extends LayoutBlock {
        {
            this.x = 0.0F;
            this.y = 0.0F;
            this.width = 100.0F;
            this.height = 40.0F;
            this.alignX = "left";
            this.alignY = "bottom";
        }
        public String texture = "maidmarriage:textures/gui/dialogue/chatbox/gal_dialog_box.png";
        public float lineWidth = 70.0F;
        public float nameX = 20.0F;
        public float nameY = 10.0F;
        public float textX = 20.0F;
        public float textY = 15.0F;
        public float hintX = 20.0F;
        public float hintY = 72.0F;
        public float speakerScale = 1.0F;
        public float textScale = 1.0F;
        public float hintScale = 0.8F;
        public int speakerColor = 0xFFFFFFFF;
        public int textColor = 0xFFFFFFFF;
        public int hintColor = 0xD9F6D7E6;
    }

    public static class Option extends LayoutBlock {
        {
            this.x = 59.0F;
            this.y = 21.5F;
            this.width = 24.0F;
            this.height = 6.1F;
            this.alignX = "left";
            this.alignY = "top";
        }
        public String texture = "maidmarriage:textures/gui/dialogue/options/gal_no_checked_option.png";
        public String selectedTexture = "maidmarriage:textures/gui/dialogue/options/gal_checked_option.png";
        public String lockedTexture = "maidmarriage:textures/gui/dialogue/options/gal_no_checked_option.png";
        public float gapY = 1.8F;
        public float titleX = 0.0F;
        public float titleY = 2.7F;
        public float descriptionX = 0.0F;
        public float descriptionY = 5.2F;
        public float titleScale = 0.92F;
        public float descriptionScale = 0.68F;
        public float titleWrap = 80.0F;
        public float descriptionWrap = 76.0F;
        public String textAlign = "center";
        public int titleColor = 0xFFF8F2F8;
        public int titleSelectedColor = 0xFFFFF6FB;
        public int descriptionColor = 0xFFD7C1CF;
        public int descriptionSelectedColor = 0xFFF1DCE8;
    }

    public static class ControlIcon extends LayoutBlock {
        {
            this.x = 88.4F;
            this.y = 78.8F;
            this.width = 3.6F;
            this.height = 3.6F;
            this.alignX = "left";
            this.alignY = "top";
        }
        public int iconSize = 32;
        public int backgroundColor = 0x22000000;
        public int hoverColor = 0x44000000;
        public int inset = 4;
        public float gapX = 0.8F;
        public Float hideX;
        public Float hideY;
        public Float hideWidth;
        public Float hideHeight;
        public Float voiceX;
        public Float voiceY;
        public Float voiceWidth;
        public Float voiceHeight;
        public Float exitX;
        public Float exitY;
        public Float exitWidth;
        public Float exitHeight;
        public Float cameraX;
        public Float cameraY;
        public Float cameraWidth;
        public Float cameraHeight;
    }

    public static class ZoomLabel extends LayoutBlock {
        {
            this.x = 70.0F;
            this.y = 79.0F;
            this.width = 12.0F;
            this.height = 3.0F;
            this.alignX = "left";
            this.alignY = "top";
        }
        public float scale = 0.92F;
        public int color = 0xFFF7EEF8;
    }

    public static ResourceLocation parseTexture(String raw, ResourceLocation fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        return parsed == null ? fallback : parsed;
    }
}
