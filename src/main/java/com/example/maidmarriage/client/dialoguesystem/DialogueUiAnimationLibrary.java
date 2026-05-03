package com.example.maidmarriage.client.dialoguesystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * 对话 UI 动画库。
 *
 * <p>这部分是从 chatbox 的思路里抽出来的“安全版动画描述层”：
 * 我们先允许配置动画数据，但暂时不把它和旧的拥抱界面强耦合。
 *
 * <p>未来无论是头像淡入、按钮弹跳、文本框滑入、表情切换时的小抖动，
 * 都应当走这套关键帧数据，而不是继续把演出写死在 Screen 里。
 */
public final class DialogueUiAnimationLibrary {
    public String id = "";
    public Map<String, Animation> animations = new LinkedHashMap<>();

    public DialogueUiAnimationLibrary normalize(ResourceLocation fallbackId) {
        if (id == null || id.isBlank()) {
            id = fallbackId == null ? "" : fallbackId.toString();
        }
        if (animations == null) {
            animations = new LinkedHashMap<>();
        }
        animations.values().forEach(Animation::normalize);
        return this;
    }

    public static Easing resolveEasing(String raw) {
        if (raw == null || raw.isBlank()) {
            return Easing.LINEAR;
        }
        try {
            return Easing.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Easing.LINEAR;
        }
    }

    public static final class Animation {
        public boolean loop;
        public List<Keyframe> keyframes = new ArrayList<>();

        public void normalize() {
            if (keyframes == null) {
                keyframes = new ArrayList<>();
            }
            keyframes.forEach(Keyframe::normalize);
        }
    }

    /**
     * 轻量关键帧。
     *
     * <p>字段基本对应我们后续 UI 组件真正会用到的可动画属性。
     * 先把数据格式定住，后面再把播放器接到具体组件上。
     */
    public static final class Keyframe {
        public int time = 1;
        public Float x;
        public Float y;
        public Float xOffset;
        public Float yOffset;
        public Float scale;
        public Float alpha;
        public Float angle;
        public String texture = "";
        public String easing = "linear";

        public void normalize() {
            if (time <= 0) {
                time = 1;
            }
            if (texture == null) {
                texture = "";
            }
            if (easing == null || easing.isBlank()) {
                easing = "linear";
            }
        }
    }

    public enum Easing {
        LINEAR,
        EASE_OUT_SINE,
        EASE_IN_SINE,
        EASE_IN_OUT_SINE
    }
}
