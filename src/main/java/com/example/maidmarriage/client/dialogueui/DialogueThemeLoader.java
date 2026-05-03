package com.example.maidmarriage.client.dialogueui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * 轻量主题加载器。
 *
 * <p>这里故意不去接数据包同步那一整大套，而是先做成客户端资源主题：
 * - 支持多个主题 JSON；
 * - 失败时自动回落；
 * - 后续别的界面直接换主题 id 即可复用。
 */
public final class DialogueThemeLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<ResourceLocation, DialogueTheme> CACHE = new HashMap<>();
    private static final ResourceLocation DEFAULT_THEME_ID = new ResourceLocation("maidmarriage", "hug_gal");

    private DialogueThemeLoader() {
    }

    public static DialogueTheme load(ResourceLocation id) {
        ResourceLocation resolvedId = id == null ? DEFAULT_THEME_ID : id;
        return CACHE.computeIfAbsent(resolvedId, DialogueThemeLoader::readTheme);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static DialogueTheme readTheme(ResourceLocation id) {
        ResourceLocation file = new ResourceLocation(id.getNamespace(), "dialogue_ui/themes/" + id.getPath() + ".json");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return new DialogueTheme();
        }
        var optionalResource = minecraft.getResourceManager().getResource(file);
        if (optionalResource.isEmpty()) {
            LOGGER.warn("未找到对话框主题资源 {}，改用默认回退主题", file);
            return new DialogueTheme();
        }
        try (var reader = new InputStreamReader(optionalResource.get().open(), StandardCharsets.UTF_8)) {
            DialogueTheme theme = GSON.fromJson(reader, DialogueTheme.class);
            return theme == null ? new DialogueTheme() : theme;
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("加载对话框主题 {} 失败，改用默认回退主题：{}", id, exception.getMessage());
            return new DialogueTheme();
        }
    }
}
