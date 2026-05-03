package com.example.maidmarriage.client.dialoguesystem;

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
 * UI 动画库加载器。
 *
 * <p>和剧情场景分开加载，目的是后续可以：
 * 1. 多个剧情共用同一套 UI 动画；
 * 2. 不同主题使用不同动画库；
 * 3. 保持“剧情内容”和“UI 演出资源”解耦。
 */
public final class DialogueUiAnimationLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<ResourceLocation, DialogueUiAnimationLibrary> CACHE = new HashMap<>();

    private DialogueUiAnimationLoader() {
    }

    public static DialogueUiAnimationLibrary load(ResourceLocation id) {
        ResourceLocation resolvedId = id == null ? new ResourceLocation("maidmarriage", "default") : id;
        return CACHE.computeIfAbsent(resolvedId, DialogueUiAnimationLoader::readLibrary);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static DialogueUiAnimationLibrary readLibrary(ResourceLocation id) {
        ResourceLocation file = new ResourceLocation(id.getNamespace(), "dialogue_ui/animations/" + id.getPath() + ".json");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return new DialogueUiAnimationLibrary().normalize(id);
        }
        var optionalResource = minecraft.getResourceManager().getResource(file);
        if (optionalResource.isEmpty()) {
            LOGGER.warn("未找到对话 UI 动画库 {}，返回空动画库占位", file);
            return new DialogueUiAnimationLibrary().normalize(id);
        }
        try (var reader = new InputStreamReader(optionalResource.get().open(), StandardCharsets.UTF_8)) {
            DialogueUiAnimationLibrary library = GSON.fromJson(reader, DialogueUiAnimationLibrary.class);
            return library == null ? new DialogueUiAnimationLibrary().normalize(id) : library.normalize(id);
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("读取对话 UI 动画库 {} 失败，返回空动画库占位：{}", id, exception.getMessage());
            return new DialogueUiAnimationLibrary().normalize(id);
        }
    }
}
