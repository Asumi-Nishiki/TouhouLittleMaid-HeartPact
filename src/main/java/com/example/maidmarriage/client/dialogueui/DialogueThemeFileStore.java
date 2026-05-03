package com.example.maidmarriage.client.dialogueui;

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
 * 拥抱对话框布局的本地配置文件读写。
 *
 * <p>资源包里的主题只负责提供默认值；玩家在 F8 调试模式里改出来的布局，
 * 会保存到 config/maidmarriage/hug-ui-layout.json。这样以后不用重新打包 jar，
 * 也能继续微调 UI 位置、尺寸和文字锚点。
 */
public final class DialogueThemeFileStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path LAYOUT_PATH = FMLPaths.CONFIGDIR.get().resolve("maidmarriage").resolve("hug-ui-layout.json");

    private DialogueThemeFileStore() {
    }

    public static DialogueTheme loadOrDefault(DialogueTheme fallback) {
        if (!Files.isRegularFile(LAYOUT_PATH)) {
            return fallback;
        }
        try (Reader reader = Files.newBufferedReader(LAYOUT_PATH, StandardCharsets.UTF_8)) {
            DialogueTheme theme = GSON.fromJson(reader, DialogueTheme.class);
            return theme == null ? fallback : theme;
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("读取拥抱 UI 布局配置失败，继续使用内置布局：{}", exception.getMessage());
            return fallback;
        }
    }

    public static boolean save(DialogueTheme theme) {
        try {
            Files.createDirectories(LAYOUT_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(LAYOUT_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(theme, writer);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.warn("保存拥抱 UI 布局配置失败：{}", exception.getMessage());
            return false;
        }
    }

    public static Path path() {
        return LAYOUT_PATH;
    }
}
