package com.example.maidmarriage.client.voice;

import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;

/**
 * Reflection bridge for opening the EasyTTS Heart Pact voice manager when the addon is installed.
 */
public final class HeartPactVoiceManagerLauncher {
    private static final String BRIDGE_CLASS = "com.asuminishiki.easyttstlm.client.gui.HeartPactVoiceManagerBridge";

    private HeartPactVoiceManagerLauncher() {
    }

    public static boolean open(Screen parent, String scriptName, @Nullable String textOnlyId) {
        return open(parent, scriptName, textOnlyId, null);
    }

    public static boolean open(Screen parent, String scriptName, @Nullable String textOnlyId, @Nullable String sourceOnlyId) {
        try {
            Class<?> bridge = Class.forName(BRIDGE_CLASS);
            try {
                bridge.getMethod("open", Screen.class, String.class, String.class, String.class)
                        .invoke(null, parent, scriptName, textOnlyId, sourceOnlyId);
            } catch (NoSuchMethodException ignored) {
                bridge.getMethod("open", Screen.class, String.class, String.class)
                        .invoke(null, parent, scriptName, textOnlyId);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
