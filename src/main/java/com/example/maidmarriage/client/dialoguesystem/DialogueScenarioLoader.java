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
 * 剧情场景加载器。
 *
 * <p>职责很单一：只负责从资源包读取 JSON 并做基础缓存。
 * 不在这一层处理旧逻辑兼容，也不在这里偷塞业务判断。
 */
public final class DialogueScenarioLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<CacheKey, DialogueScenario> CACHE = new HashMap<>();

    private DialogueScenarioLoader() {
    }

    public static DialogueScenario load(ResourceLocation id) {
        ResourceLocation resolvedId = id == null ? new ResourceLocation("maidmarriage", "hug_menu_v2") : id;
        String language = DialogueLocaleResolver.currentLanguage();
        return CACHE.computeIfAbsent(new CacheKey(resolvedId, language), key -> readScenario(key.id()));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static DialogueScenario readScenario(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return new DialogueScenario().normalize(id);
        }
        for (String language : DialogueLocaleResolver.fallbackLanguages()) {
            ResourceLocation localizedFile = new ResourceLocation(
                    id.getNamespace(),
                    "dialogue/" + language + "/scenarios/" + id.getPath() + ".json"
            );
            var optionalResource = minecraft.getResourceManager().getResource(localizedFile);
            if (optionalResource.isEmpty()) {
                continue;
            }
            try (var reader = new InputStreamReader(optionalResource.get().open(), StandardCharsets.UTF_8)) {
                DialogueScenario scenario = GSON.fromJson(reader, DialogueScenario.class);
                return scenario == null ? new DialogueScenario().normalize(id) : scenario.normalize(id);
            } catch (IOException | JsonParseException exception) {
                LOGGER.warn("读取本地化对话场景 {} 失败，将继续尝试回退资源：{}", localizedFile, exception.getMessage());
            }
        }

        ResourceLocation legacyFile = new ResourceLocation(id.getNamespace(), "dialogue/scenarios/" + id.getPath() + ".json");
        var optionalResource = minecraft.getResourceManager().getResource(legacyFile);
        if (optionalResource.isPresent()) {
            try (var reader = new InputStreamReader(optionalResource.get().open(), StandardCharsets.UTF_8)) {
                DialogueScenario scenario = GSON.fromJson(reader, DialogueScenario.class);
                return scenario == null ? new DialogueScenario().normalize(id) : scenario.normalize(id);
            } catch (IOException | JsonParseException exception) {
                LOGGER.warn("读取旧路径对话场景 {} 失败，返回空场景占位：{}", legacyFile, exception.getMessage());
            }
        }
        LOGGER.warn("未找到对话场景资源 {}，返回空场景占位", id);
        return new DialogueScenario().normalize(id);
    }

    private record CacheKey(ResourceLocation id, String language) {
    }
}
