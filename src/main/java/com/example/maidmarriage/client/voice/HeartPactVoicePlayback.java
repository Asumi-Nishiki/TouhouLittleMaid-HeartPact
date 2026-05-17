package com.example.maidmarriage.client.voice;

import com.example.maidmarriage.client.dialoguesystem.runtime.DialogueFrameView;
import com.example.maidmarriage.config.ModConfigs;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Playback entry for pre-generated Heart Pact voice files.
 */
public final class HeartPactVoicePlayback {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HeartPactVoicePlayback() {
    }

    public static void playFrame(DialogueFrameView frame, String speaker, String text,
                                 @Nullable String sourceKey,
                                 @Nullable EntityMaid maid) {
        if (frame == null || frame.choiceNode()) {
            return;
        }
        play(frame.scenarioId(), frame.nodeId(), frame.lineIndex(), 0, speaker, text, sourceKey, maid, false);
    }

    public static void playStructuredLine(DialogueFrameView frame, int structuredIndex,
                                          String speaker, String text,
                                          @Nullable String sourceKey,
                                          @Nullable EntityMaid maid) {
        if (frame == null) {
            play("", "", 0, structuredIndex, speaker, text, sourceKey, maid, false);
            return;
        }
        play(frame.scenarioId(), frame.nodeId(), frame.lineIndex(), structuredIndex, speaker, text, sourceKey, maid, false);
    }

    public static void replayStructuredLine(DialogueFrameView frame, int structuredIndex,
                                            String speaker, String text,
                                            @Nullable String sourceKey,
                                            @Nullable EntityMaid maid) {
        if (frame == null) {
            play("", "", 0, structuredIndex, speaker, text, sourceKey, maid, true);
            return;
        }
        play(frame.scenarioId(), frame.nodeId(), frame.lineIndex(), structuredIndex, speaker, text, sourceKey, maid, true);
    }

    public static boolean hasStructuredVoice(DialogueFrameView frame, int structuredIndex,
                                             String speaker, String text,
                                             @Nullable String sourceKey) {
        if (frame == null || text == null || text.isBlank()) {
            return false;
        }
        return resolveAudioPath(frame.scenarioId(), frame.nodeId(), frame.lineIndex(), structuredIndex,
                speaker, normalizeText(text), sourceKey) != null;
    }

    private static void play(String scenarioId, String nodeId, int lineIndex, int structuredIndex,
                             String speaker, String text,
                             @Nullable String sourceKey,
                             @Nullable EntityMaid maid, boolean forceReplay) {
        if (!ModConfigs.heartPactVoiceEnabled() || maid == null || text == null || text.isBlank()) {
            return;
        }
        String cleanedText = normalizeText(text);
        if (cleanedText.isBlank()) {
            return;
        }

        Path audioPath = resolveAudioPath(scenarioId, nodeId, lineIndex, structuredIndex, speaker, cleanedText, sourceKey);
        if (audioPath == null || !Files.isRegularFile(audioPath)) {
            return;
        }

        try {
            byte[] audio = Files.readAllBytes(audioPath);
            float volume = (float) ModConfigs.heartPactVoiceVolume();
            LOGGER.info("Heart Pact voice playback | file={} bytes={} volume={}", audioPath, audio.length, volume);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                LOGGER.warn("Heart Pact voice playback skipped: Minecraft client is null");
                return;
            }
            minecraft.getSoundManager().play(new HeartPactVoiceSoundInstance(maid, audio, volume));
        } catch (Exception exception) {
            LOGGER.error("Heart Pact voice playback failed: {}", audioPath, exception);
        }
    }

    @Nullable
    private static Path resolveAudioPath(String scenarioId, String nodeId, int lineIndex, int structuredIndex,
                                         String speaker, String text,
                                         @Nullable String sourceKey) {
        Path dir = voiceDirectory();
        String normalizedSourceKey = normalizeSourceKey(sourceKey);
        if (!normalizedSourceKey.isBlank()) {
            String sourceId = sourceOnlyId(normalizedSourceKey);
            Path sourcePath = dir.resolve(sourceId + ".wav");
            if (Files.isRegularFile(sourcePath)) {
                return sourcePath;
            }
            Path fallbackSource = findInAnyVoiceScript(sourceId + ".wav");
            if (fallbackSource != null) {
                return fallbackSource;
            }
        }
        String frameId = sha1(scenarioId + "\n" + nodeId + "\n" + lineIndex + "\n" + structuredIndex + "\n"
                + nullToEmpty(speaker) + "\n" + text);
        Path framePath = dir.resolve(frameId + ".wav");
        if (Files.isRegularFile(framePath)) {
            return framePath;
        }

        String textId = sha1(text);
        Path textPath = dir.resolve(textId + ".wav");
        if (Files.isRegularFile(textPath)) {
            return textPath;
        }
        String legacyTextId = Integer.toHexString(nullToEmpty(text).hashCode());
        Path legacyTextPath = dir.resolve(legacyTextId + ".wav");
        if (Files.isRegularFile(legacyTextPath)) {
            return legacyTextPath;
        }

        Path fallback = findInAnyVoiceScript(frameId + ".wav");
        if (fallback != null) {
            return fallback;
        }
        fallback = findInAnyVoiceScript(textId + ".wav");
        if (fallback != null) {
            return fallback;
        }
        fallback = findInAnyVoiceScript(legacyTextId + ".wav");
        if (fallback != null) {
            return fallback;
        }
        return findByProgressText(text, speaker, normalizedSourceKey);
    }

    @Nullable
    private static Path findInAnyVoiceScript(String fileName) {
        Path root = voiceRootDirectory();
        if (!Files.isDirectory(root) || fileName == null || fileName.isBlank()) {
            return null;
        }
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve(fileName))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Path voiceRootDirectory() {
        return FMLPaths.CONFIGDIR.get()
                .resolve("easyttstlm")
                .resolve("heart_pact_voice");
    }

    @Nullable
    private static Path findByProgressText(String text, String speaker, @Nullable String sourceKey) {
        Path root = voiceRootDirectory();
        if (!Files.isDirectory(root) || ((text == null || text.isBlank()) && (sourceKey == null || sourceKey.isBlank()))) {
            return null;
        }
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .map(dir -> findByProgressTextInScript(dir, text, speaker, sourceKey))
                    .filter(path -> path != null && Files.isRegularFile(path))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    @Nullable
    private static Path findByProgressTextInScript(Path scriptDir, String text, String speaker, @Nullable String sourceKey) {
        Path progressPath = scriptDir.resolve("progress.json");
        if (!Files.isRegularFile(progressPath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(progressPath, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                return null;
            }
            JsonObject done = getObject(root.getAsJsonObject(), "done");
            if (done == null) {
                return null;
            }
            for (var entry : done.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject item = entry.getValue().getAsJsonObject();
                if (!matchesStoredSource(getString(item, "source"), getString(item, "cross_source"), sourceKey)
                        && !matchesStoredText(getString(item, "text"), text, speaker)) {
                    continue;
                }
                String audio = getString(item, "audio");
                if (audio.isBlank()) {
                    continue;
                }
                Path audioPath = Path.of(audio);
                if (Files.isRegularFile(audioPath)) {
                    return audioPath;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public static Path voiceDirectory() {
        return voiceRootDirectory()
                .resolve(ModConfigs.heartPactVoiceScriptName());
    }

    public static String playbackId(String scenarioId, String nodeId, int lineIndex, int structuredIndex,
                                    String speaker, String text) {
        return sha1(scenarioId + "\n" + nodeId + "\n" + lineIndex + "\n" + structuredIndex + "\n"
                + nullToEmpty(speaker) + "\n" + normalizeText(text));
    }

    public static String textOnlyId(String text) {
        return sha1(normalizeText(text));
    }

    public static String sourceOnlyId(String sourceKey) {
        return sha1(normalizeSourceKey(sourceKey));
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    public static String normalizeSourceKey(String sourceKey) {
        String normalized = sourceKey == null ? "" : sourceKey.trim().replace('\\', '/');
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.replaceFirst("dialogue/[^/]+/", "dialogue/*/");
    }

    private static boolean matchesStoredText(String storedText, String expectedText, String speaker) {
        String stored = normalizeText(storedText);
        String expected = normalizeText(expectedText);
        if (stored.equals(expected)) {
            return true;
        }
        return canonicalizeVoiceText(stored, speaker).equals(canonicalizeVoiceText(expected, speaker));
    }

    private static boolean matchesStoredSource(String storedSource, String storedCrossSource, @Nullable String expectedSource) {
        String expected = normalizeSourceKey(expectedSource);
        if (expected.isBlank()) {
            return false;
        }
        return expected.equals(normalizeSourceKey(storedSource))
                || expected.equals(normalizeSourceKey(storedCrossSource));
    }

    private static String canonicalizeVoiceText(String text, String speaker) {
        String canonical = normalizeText(text);
        for (String alias : maidAliases(speaker)) {
            canonical = canonical.replace(alias, "<maid>");
        }
        for (String alias : playerAliases()) {
            canonical = canonical.replace(alias, "<player>");
        }
        return canonical;
    }

    private static Set<String> maidAliases(String speaker) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addAlias(aliases, speaker);
        addAlias(aliases, "女仆");
        addAlias(aliases, "小女仆");
        addAlias(aliases, "Maid");
        addAlias(aliases, "Little Maid");
        addAlias(aliases, "メイド");
        addAlias(aliases, "小さなメイド");
        return aliases;
    }

    private static Set<String> playerAliases() {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String currentPlayerName = currentPlayerName();
        addAlias(aliases, currentPlayerName);
        addAlias(aliases, ModConfigs.resolveMaidAddressing(currentPlayerName));
        addAlias(aliases, ModConfigs.resolveChildMaidAddressing(currentPlayerName));
        for (String script : new String[]{"zh_cn", "en_us", "ja_jp"}) {
            addAlias(aliases, ModConfigs.resolveHeartPactTtsPlayerName(script, currentPlayerName));
            addAlias(aliases, ModConfigs.resolveHeartPactTtsPlayerMaidName(script, currentPlayerName));
        }
        addAlias(aliases, "主人");
        addAlias(aliases, "Master");
        addAlias(aliases, "ご主人様");
        return aliases;
    }

    private static String currentPlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            String name = minecraft.player.getName().getString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "";
    }

    private static void addAlias(Set<String> aliases, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return;
        }
        aliases.add(trimmed);
        aliases.add(trimmed.toLowerCase(Locale.ROOT));
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(nullToEmpty(text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return Integer.toHexString(nullToEmpty(text).hashCode());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Nullable
    private static JsonObject getObject(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(key);
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
