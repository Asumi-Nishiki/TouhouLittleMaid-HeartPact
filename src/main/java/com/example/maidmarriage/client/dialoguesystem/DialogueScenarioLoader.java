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
    private static final Map<ResourceLocation, DialogueScenario> CACHE = new HashMap<>();

    private DialogueScenarioLoader() {
    }

    public static DialogueScenario load(ResourceLocation id) {
        ResourceLocation resolvedId = id == null ? new ResourceLocation("maidmarriage", "hug_menu_v2") : id;
        return CACHE.computeIfAbsent(resolvedId, DialogueScenarioLoader::readScenario);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static DialogueScenario readScenario(ResourceLocation id) {
        ResourceLocation file = new ResourceLocation(id.getNamespace(), "dialogue/scenarios/" + id.getPath() + ".json");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return new DialogueScenario().normalize(id);
        }
        var optionalResource = minecraft.getResourceManager().getResource(file);
        if (optionalResource.isEmpty()) {
            LOGGER.warn("未找到对话场景资源 {}，返回空场景占位", file);
            return new DialogueScenario().normalize(id);
        }
        try (var reader = new InputStreamReader(optionalResource.get().open(), StandardCharsets.UTF_8)) {
            DialogueScenario scenario = GSON.fromJson(reader, DialogueScenario.class);
            return scenario == null ? new DialogueScenario().normalize(id) : scenario.normalize(id);
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("读取对话场景 {} 失败，返回空场景占位：{}", id, exception.getMessage());
            return new DialogueScenario().normalize(id);
        }
    }
}
