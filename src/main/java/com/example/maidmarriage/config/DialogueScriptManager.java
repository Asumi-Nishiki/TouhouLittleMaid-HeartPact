package com.example.maidmarriage.config;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * 统一管理外置台本文件。
 *
 * <p>这一层的职责是：
 * 1. 允许把互动文本、剧情文本、敏感文本外置；
 * 2. 允许 JSON 内直接写中文注释，方便维护；
 * 3. 读取失败时自动回退内置语言键，保证游戏先跑起来；
 * 4. 提供配置页测试入口，直接校验路径与格式。
 */
public final class DialogueScriptManager {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Pattern FORMAT_PATTERN = Pattern.compile(
            "%(?:(\\d+)\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?(?:[tT])?([a-zA-Z%])");
    private static final Set<String> DEFAULT_INCLUDED_PREFIXES = Set.of(
            "message.maidmarriage.",
            "dialogue.maidmarriage.",
            "scene.maidmarriage.",
            "name.maidmarriage.",
            "ui.maidmarriage.hug_action."
    );
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static volatile Map<String, String> builtInTemplates;

    private DialogueScriptManager() {
    }

    public static Component componentForPlayer(@Nullable Player player, String key, Object... args) {
        String template = resolveTemplate(player, key);
        if (template == null) {
            return Component.translatable(key, args);
        }
        return Component.literal(formatTemplate(template, args));
    }

    public static Component componentForMaid(EntityMaid maid, String key, Object... args) {
        Entity owner = maid.getOwner();
        if (owner instanceof Player player) {
            return componentForPlayer(player, key, args);
        }
        return component(key, args);
    }

    public static Component component(String key, Object... args) {
        return componentForPlayer(null, key, args);
    }

    public static ValidationResult validateConfiguredScript() {
        return validatePath(ModConfigs.dialogueScriptPath());
    }

    public static ValidationResult validatePath(String rawPath) {
        String sanitizedPath = sanitizePath(rawPath);
        if (sanitizedPath.isBlank()) {
            return ValidationResult.failure("台本路径不能为空。");
        }

        Path scriptPath = resolvePath(sanitizedPath);
        boolean generatedExample = false;
        try {
            if (Files.notExists(scriptPath)) {
                writeExampleFile(scriptPath);
                generatedExample = true;
            }

            LoadedScript script = loadScript(scriptPath);
            List<String> errors = new ArrayList<>();
            int missingKnownKeys = 0;
            int unknownKeys = 0;
            Set<String> knownKeys = knownKeys();

            for (Map.Entry<String, List<String>> entry : script.entries().entrySet()) {
                String key = entry.getKey();
                if (!knownKeys.contains(key)) {
                    unknownKeys++;
                }
                List<String> values = entry.getValue();
                if (values.isEmpty()) {
                    errors.add("键 \"" + key + "\" 的数组不能为空。");
                    continue;
                }
                for (int i = 0; i < values.size(); i++) {
                    String value = values.get(i);
                    if (value == null || value.isBlank()) {
                        errors.add("键 \"" + key + "\" 的第 " + (i + 1) + " 条文本为空。");
                        continue;
                    }
                    String formatError = validateFormatString(key, value);
                    if (formatError != null) {
                        errors.add(formatError);
                    }
                }
            }

            for (String knownKey : knownKeys) {
                if (!script.entries().containsKey(knownKey)) {
                    missingKnownKeys++;
                }
            }

            if (!errors.isEmpty()) {
                return ValidationResult.failure(MessageFormat.format(
                        "台本读取失败：共发现 {0} 个问题。第一条：{1}",
                        errors.size(),
                        errors.get(0)
                ));
            }

            String summary = MessageFormat.format(
                    generatedExample
                            ? "已创建示例台本并校验通过：读取 {0} 个键，缺省回退 {1} 个内置键，额外自定义 {2} 个键。"
                            : "台本校验通过：读取 {0} 个键，缺省回退 {1} 个内置键，额外自定义 {2} 个键。",
                    script.entries().size(),
                    missingKnownKeys,
                    unknownKeys
            );
            return ValidationResult.success(summary, scriptPath, knownKeys.size(), script.entries().size());
        } catch (Exception exception) {
            return ValidationResult.failure("台本读取失败：" + exception.getMessage());
        }
    }

    public static void reloadAll() {
        CACHE.clear();
    }

    public static String resolvePlayerScriptPath(@Nullable Player player) {
        // Dedicated servers must own script selection. Clients may still edit
        // their local/common config for single-player and local validation.
        return sanitizePath(ModConfigs.dialogueScriptPath());
    }

    private static @Nullable String resolveTemplate(@Nullable Player player, String key) {
        String path = resolvePlayerScriptPath(player);
        if (path.isBlank()) {
            return null;
        }
        try {
            Path scriptPath = resolvePath(path);
            if (Files.notExists(scriptPath)) {
                writeExampleFile(scriptPath);
            }
            LoadedScript script = loadScript(scriptPath);
            List<String> values = script.entries().get(key);
            if (values == null || values.isEmpty()) {
                return null;
            }
            if (values.size() == 1) {
                return values.get(0);
            }
            int index = Math.floorMod(Objects.hash(key, System.nanoTime()), values.size());
            return values.get(index);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LoadedScript loadScript(Path scriptPath) throws IOException {
        Path normalized = scriptPath.toAbsolutePath().normalize();
        String cacheKey = normalized.toString();
        long lastModified = Files.getLastModifiedTime(normalized).toMillis();
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && cached.lastModified() == lastModified) {
            return cached.script();
        }

        try (Reader reader = Files.newBufferedReader(normalized, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonElement root = JsonParser.parseReader(jsonReader);
            if (!root.isJsonObject()) {
                throw new JsonParseException("根节点必须是 JSON 对象。");
            }
            LoadedScript script = parseScriptObject(root.getAsJsonObject());
            CACHE.put(cacheKey, new CacheEntry(lastModified, script));
            return script;
        }
    }

    private static LoadedScript parseScriptObject(JsonObject root) {
        Map<String, List<String>> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            List<String> lines = new ArrayList<>();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                lines.add(value.getAsString());
            } else if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                for (JsonElement element : array) {
                    if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                        throw new JsonParseException("键 \"" + key + "\" 的数组里只能放字符串。");
                    }
                    lines.add(element.getAsString());
                }
            } else {
                throw new JsonParseException("键 \"" + key + "\" 只能是字符串或字符串数组。");
            }
            entries.put(key, List.copyOf(lines));
        }
        return new LoadedScript(Map.copyOf(entries));
    }

    private static String formatTemplate(String template, Object... args) {
        Object[] normalizedArgs = normalizeArgs(args);
        try {
            return String.format(Locale.ROOT, escapeBarePercents(template), normalizedArgs);
        } catch (Exception exception) {
            return template;
        }
    }

    /**
     * 把台本里的普通百分号自动转成 String.format 可接受的字面量百分号。
     *
     * <p>玩家写台本时更符合直觉的是“血量低于30%”，
     * 但 Java 的 {@link String#format(String, Object...)} 会把单独的 % 当成格式化占位符开头。
     * 如果强迫玩家写成“30%%”很容易出错，所以这里自动区分：
     * - 合法占位符，如 %s、%1$s、%.2f，保持不变；
     * - 已转义的 %% 和换行 %n，保持不变；
     * - 其他普通 %，自动改成 %%。
     */
    private static String escapeBarePercents(String template) {
        StringBuilder result = new StringBuilder(template.length());
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current != '%') {
                result.append(current);
                index++;
                continue;
            }

            if (index + 1 < template.length()) {
                char next = template.charAt(index + 1);
                if (next == '%' || next == 'n') {
                    result.append('%').append(next);
                    index += 2;
                    continue;
                }
            }

            Matcher matcher = FORMAT_PATTERN.matcher(template);
            matcher.region(index, template.length());
            if (matcher.lookingAt()) {
                result.append(template, index, matcher.end());
                index = matcher.end();
                continue;
            }

            result.append("%%");
            index++;
        }
        return result.toString();
    }

    private static Object[] normalizeArgs(Object[] args) {
        Object[] normalized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof Component component) {
                normalized[i] = component.getString();
            } else if (value instanceof Entity entity) {
                normalized[i] = entity.getDisplayName().getString();
            } else {
                normalized[i] = value;
            }
        }
        return normalized;
    }

    private static String sanitizePath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String sanitized = rawPath.trim().replace('\\', '/');
        if (sanitized.length() > 512) {
            sanitized = sanitized.substring(0, 512);
        }
        return sanitized;
    }

    private static Path resolvePath(String rawPath) {
        Path path = Path.of(rawPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return FMLPaths.CONFIGDIR.get().resolve(path).normalize();
    }

    private static void writeExampleFile(Path scriptPath) throws IOException {
        if (scriptPath.getParent() != null) {
            Files.createDirectories(scriptPath.getParent());
        }
        StringWriter writer = new StringWriter();
        writeExampleContent(writer);
        Files.writeString(scriptPath, writer.toString(), StandardCharsets.UTF_8);
        reloadAll();
    }

    /**
     * 这里直接输出带中文注释的 JSONC 风格示例文件。
     *
     * <p>用户明确要求“包括 json 的注释”，
     * 所以这里不走纯标准 JSON 序列化，而是生成可读性更好的注释版文件。
     */
    private static void writeExampleContent(Writer writer) throws IOException {
        Map<String, String> defaults = defaultTemplates();
        List<String> keys = defaults.keySet().stream().sorted(Comparator.naturalOrder()).toList();

        writer.write("{\n");
        writer.write("  // 女仆婚姻模组外置台本。\n");
        writer.write("  // 1. 支持单条文本：\"key\": \"文本\"\n");
        writer.write("  // 2. 支持随机文本：\"key\": [\"文本1\", \"文本2\"]\n");
        writer.write("  // 3. 支持 String.format 占位符，例如 %1$s、%2$s、%s\n");
        writer.write("  // 4. 请保持 UTF-8 编码。\n");
        writer.write("  // 5. 未填写的键会自动回退到模组内置文本。\n\n");

        String currentGroup = "";
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String group = commentGroupForKey(key);
            if (!group.equals(currentGroup)) {
                if (!currentGroup.isEmpty()) {
                    writer.write("\n");
                }
                writer.write("  // " + group + "\n");
                currentGroup = group;
            }
            writer.write("  " + GSON.toJson(key) + ": " + GSON.toJson(defaults.get(key)));
            if (i < keys.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write("}\n");
    }

    private static String commentGroupForKey(String key) {
        if (key.startsWith("message.maidmarriage.")) {
            return "系统消息文本";
        }
        if (key.startsWith("dialogue.maidmarriage.")) {
            return "女仆对话文本";
        }
        if (key.startsWith("scene.maidmarriage.")) {
            return "剧情演出文本";
        }
        if (key.startsWith("name.maidmarriage.")) {
            return "称呼文本";
        }
        if (key.startsWith("ui.maidmarriage.hug_action.")) {
            return "拥抱悬浮界面文本";
        }
        return "其他文本";
    }

    private static @Nullable String validateFormatString(String key, String template) {
        try {
            String.format(Locale.ROOT, escapeBarePercents(template), buildFormatArgs(template));
            return null;
        } catch (Exception exception) {
            return "键 \"" + key + "\" 的格式化占位符有误：" + exception.getMessage();
        }
    }

    private static Object[] buildFormatArgs(String template) {
        List<Object> args = new ArrayList<>();
        Matcher matcher = FORMAT_PATTERN.matcher(template);
        int nextAutoIndex = 1;
        Map<Integer, Object> positional = new LinkedHashMap<>();
        Object lastArg = "示例";

        while (matcher.find()) {
            String conversionGroup = matcher.group(2);
            if (conversionGroup == null) {
                continue;
            }
            char conversion = conversionGroup.charAt(conversionGroup.length() - 1);
            if (conversion == '%') {
                continue;
            }

            String explicitIndexText = matcher.group(1);
            boolean reusePrevious = template.charAt(matcher.start() + 1) == '<';
            int index;
            if (reusePrevious && !positional.isEmpty()) {
                index = positional.keySet().stream().max(Integer::compareTo).orElse(1);
            } else if (explicitIndexText != null) {
                index = Integer.parseInt(explicitIndexText);
            } else {
                index = nextAutoIndex++;
            }

            positional.putIfAbsent(index, sampleArgForConversion(conversion));
            lastArg = positional.get(index);
        }

        if (positional.isEmpty()) {
            return new Object[0];
        }

        int maxIndex = positional.keySet().stream().max(Integer::compareTo).orElse(0);
        for (int i = 1; i <= maxIndex; i++) {
            args.add(positional.getOrDefault(i, lastArg));
        }
        return args.toArray();
    }

    private static Object sampleArgForConversion(char conversion) {
        return switch (Character.toLowerCase(conversion)) {
            case 'b' -> Boolean.TRUE;
            case 'c' -> '中';
            case 'd', 'o', 'x' -> 1;
            case 'e', 'f', 'g', 'a' -> 1.0D;
            case 'h', 's' -> "示例";
            case 't' -> Date.from(Instant.now());
            default -> "示例";
        };
    }

    private static Set<String> knownKeys() {
        return defaultTemplates().keySet();
    }

    private static Map<String, String> defaultTemplates() {
        Map<String, String> local = builtInTemplates;
        if (local != null) {
            return local;
        }

        synchronized (DialogueScriptManager.class) {
            if (builtInTemplates != null) {
                return builtInTemplates;
            }

            Map<String, String> result = new LinkedHashMap<>();
            try (InputStream stream = DialogueScriptManager.class.getClassLoader()
                    .getResourceAsStream("assets/maidmarriage/lang/zh_cn.json")) {
                if (stream == null) {
                    builtInTemplates = Map.of();
                    return builtInTemplates;
                }
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                        String key = entry.getKey();
                        if (shouldIncludeInExample(key)
                                && entry.getValue().isJsonPrimitive()
                                && entry.getValue().getAsJsonPrimitive().isString()) {
                            result.put(key, entry.getValue().getAsString());
                        }
                    }
                }
            } catch (Exception ignored) {
                result.clear();
            }

            builtInTemplates = Map.copyOf(result);
            return builtInTemplates;
        }
    }

    private static boolean shouldIncludeInExample(String key) {
        for (String prefix : DEFAULT_INCLUDED_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private record CacheEntry(long lastModified, LoadedScript script) {
    }

    private record LoadedScript(Map<String, List<String>> entries) {
    }

    public record ValidationResult(boolean success, String message, @Nullable Path path, int knownKeys, int loadedKeys) {
        private static ValidationResult success(String message, Path path, int knownKeys, int loadedKeys) {
            return new ValidationResult(true, message, path, knownKeys, loadedKeys);
        }

        private static ValidationResult failure(String message) {
            return new ValidationResult(false, message, null, 0, 0);
        }
    }
}
