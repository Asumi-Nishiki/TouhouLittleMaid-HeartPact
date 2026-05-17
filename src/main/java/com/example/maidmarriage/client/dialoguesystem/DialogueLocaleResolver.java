package com.example.maidmarriage.client.dialoguesystem;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * 剧情台本语言解析器。
 *
 * <p>Minecraft 的普通物品名、提示文本可以直接走 lang 文件，
 * 但 GalGame 台本是完整 JSON，为了避免把几千句剧情塞进 lang，
 * 这里统一决定剧情资源应该优先读取哪个语言目录。
 */
public final class DialogueLocaleResolver {
    public static final String DEFAULT_LANGUAGE = "zh_cn";

    private DialogueLocaleResolver() {
    }

    public static String currentLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getLanguageManager() == null) {
            return DEFAULT_LANGUAGE;
        }
        String code = minecraft.getLanguageManager().getSelected();
        return normalizeLanguage(code);
    }

    public static Iterable<String> fallbackLanguages() {
        Set<String> languages = new LinkedHashSet<>();
        String current = currentLanguage();
        languages.add(current);
        if (current.startsWith("en_")) {
            languages.add("en_us");
        }
        if (current.startsWith("ja_")) {
            languages.add("ja_jp");
        }
        languages.add(DEFAULT_LANGUAGE);
        return languages;
    }

    private static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return raw.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
