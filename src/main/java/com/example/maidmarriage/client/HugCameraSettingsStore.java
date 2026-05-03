package com.example.maidmarriage.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * 拥抱/小女仆互动镜头参数的本地存储。
 *
 * <p>这个文件只保存客户端视觉偏好，不参与服务端同步：
 * 不同玩家、不同屏幕比例、不同模型高度需要的镜头参数可能都不一样，
 * 所以它更适合放在本地 `config/maidmarriage/hug-camera.json`。
 */
public final class HugCameraSettingsStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SETTINGS_PATH = FMLPaths.CONFIGDIR.get().resolve("maidmarriage").resolve("hug-camera.json");

    private HugCameraSettingsStore() {
    }

    public static Settings loadOrDefault(Settings fallback) {
        if (!Files.isRegularFile(SETTINGS_PATH)) {
            return fallback;
        }
        try (Reader reader = Files.newBufferedReader(SETTINGS_PATH, StandardCharsets.UTF_8)) {
            Settings settings = GSON.fromJson(reader, Settings.class);
            return settings == null ? fallback : settings;
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("读取拥抱镜头配置失败，继续使用默认值：{}", exception.getMessage());
            return fallback;
        }
    }

    public static boolean save(Settings settings) {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(SETTINGS_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.warn("保存拥抱镜头配置失败：{}", exception.getMessage());
            return false;
        }
    }

    public static Path path() {
        return SETTINGS_PATH;
    }

    /**
     * 可序列化的数据对象。
     *
     * @param fovScale          FOV 缩放倍率，越小越拉近。
     * @param pitchOffsetDegrees 相机上下偏移角度，正值低头，负值抬头。
     */
    public record Settings(double fovScale, float pitchOffsetDegrees) {
    }
}
