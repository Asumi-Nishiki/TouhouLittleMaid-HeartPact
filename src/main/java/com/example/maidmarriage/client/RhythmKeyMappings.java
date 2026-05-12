package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class RhythmKeyMappings {
    public static final KeyMapping RHYTHM_HIT = new KeyMapping(
            "key.maidmarriage.rhythm_hit",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.maidmarriage"
    );
    public static final KeyMapping PET_HEAD = new KeyMapping(
            "key.maidmarriage.pet_head",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.maidmarriage"
    );
    public static final KeyMapping INTERACTION = new KeyMapping(
            "key.maidmarriage.interaction",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.maidmarriage"
    );
    public static final KeyMapping CARRY_POSE_DEBUG = new KeyMapping(
            "key.maidmarriage.carry_pose_debug",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.maidmarriage"
    );
    public static final KeyMapping MAID_DEBUG_PANEL = new KeyMapping(
            "key.maidmarriage.maid_debug_panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.maidmarriage"
    );
    public static final KeyMapping LAP_PILLOW_EXIT = new KeyMapping(
            "key.maidmarriage.lap_pillow_exit",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.maidmarriage"
    );

    private RhythmKeyMappings() {
    }

    public static void applyConfigKeyMappings() {
        /*
         * 正式版以 Minecraft 原版“控制”界面为唯一按键来源。
         * 这里保留方法只是兼容旧调用点，不再从模组配置反向覆盖玩家在 options.txt 里的绑定。
         */
        KeyMapping.resetMapping();
    }

    /**
     * 返回当前实际绑定按键的短文本。
     * 交互悬浮界面直接读取这个结果，就能始终和玩家设置保持同步。
     */
    public static String boundKeyName(KeyMapping keyMapping) {
        String translated = keyMapping.getTranslatedKeyMessage().getString();
        if (translated == null || translated.isBlank()) {
            return "?";
        }
        String keyName = "key.keyboard.space".equals(translated) ? "Space" : translated.toUpperCase();
        KeyModifier modifier = keyMapping.getKeyModifier();
        if (modifier == KeyModifier.ALT) {
            return keyName.startsWith("ALT+") ? keyName : "Alt+" + keyName;
        }
        if (modifier == KeyModifier.CONTROL) {
            return keyName.startsWith("CTRL+") ? keyName : "Ctrl+" + keyName;
        }
        if (modifier == KeyModifier.SHIFT) {
            return keyName.startsWith("SHIFT+") ? keyName : "Shift+" + keyName;
        }
        return keyName;
    }
}

