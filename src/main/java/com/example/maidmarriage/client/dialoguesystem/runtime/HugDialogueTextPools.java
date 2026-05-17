package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.compat.RelationStage;
import com.example.maidmarriage.client.dialoguesystem.DialogueLocaleResolver;
import com.example.maidmarriage.data.MaidMoodData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class HugDialogueTextPools {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String POOL_ROOT = "dialogue/pools/";
    private static final String RESOURCE_PATH = "hug_menu_v4.json";
    private static final String TOPIC_RESOURCE_PATH = "hug_chat_topics_v1.json";
    private static final Gson GSON = new Gson();
    private static final Map<String, LoadedPoolResource> CACHE = new HashMap<>();

    private HugDialogueTextPools() {
    }

    public static String pickEntry(RelationStage stage, MaidMoodData.MoodState mood) {
        return pickEntryCue(stage, mood).text();
    }

    public static PickedLine pickEntryCue(RelationStage stage, MaidMoodData.MoodState mood) {
        return normalize(pick(path("entry", stageKey(stage), moodKey(mood))));
    }

    public static String pickTimeEntry(RelationStage stage, String timeOfDay) {
        return pickTimeEntryCue(stage, timeOfDay).text();
    }

    public static PickedLine pickTimeEntryCue(RelationStage stage, String timeOfDay) {
        return normalize(pick(topicPath("entry_time", timeBucket(timeOfDay), stageKey(stage))));
    }

    public static String pickMixedEntry(RelationStage stage,
                                        MaidMoodData.MoodState mood,
                                        String timeOfDay) {
        return pickMixedEntryCue(stage, mood, timeOfDay).text();
    }

    public static PickedLine pickMixedEntryCue(RelationStage stage,
                                               MaidMoodData.MoodState mood,
                                               String timeOfDay) {
        PickedLine moodEntry = pickEntryCue(stage, mood);
        PickedLine timeEntry = pickTimeEntryCue(stage, timeOfDay);
        if (moodEntry.text().isBlank()) {
            return timeEntry;
        }
        if (timeEntry.text().isBlank()) {
            return moodEntry;
        }
        return ThreadLocalRandom.current().nextBoolean() ? moodEntry : timeEntry;
    }

    public static String pickLongingEntry(RelationStage stage, MaidMoodData.MoodState mood) {
        String key = longingEntryKey(stage, mood);
        return pickLongingEntryCue(stage, mood).text();
    }

    public static PickedLine pickLongingEntryCue(RelationStage stage, MaidMoodData.MoodState mood) {
        String key = longingEntryKey(stage, mood);
        return normalize(pick(path("longing_entry", key)));
    }

    public static String pickChat(RelationStage stage, MaidMoodData.MoodState mood) {
        return pickChatCue(stage, mood).text();
    }

    public static PickedLine pickChatCue(RelationStage stage, MaidMoodData.MoodState mood) {
        return pickEntryCue(stage, mood);
    }

    public static String pickPet(RelationStage stage) {
        return pickPetCue(stage).text();
    }

    public static PickedLine pickPetCue(RelationStage stage) {
        return normalize(pick(path("action", "pet", actionStageKey(stage, "warm"))));
    }

    public static String pickHug(RelationStage stage) {
        return pickHugCue(stage).text();
    }

    public static PickedLine pickHugCue(RelationStage stage) {
        return normalize(pick(path("action", "hug", actionStageKey(stage, "close"))));
    }

    public static String pickKiss(RelationStage stage) {
        return pickKissCue(stage).text();
    }

    public static PickedLine pickKissCue(RelationStage stage) {
        return normalize(pick(path("action", "kiss", actionStageKey(stage, "dating"))));
    }

    public static String pickReleaseHug() {
        return pickReleaseHugCue().text();
    }

    public static PickedLine pickReleaseHugCue() {
        return normalize(pick(path("action", "release_hug")));
    }

    public static String pickLowComfort() {
        return pickLowComfortCue().text();
    }

    public static PickedLine pickLowComfortCue() {
        return normalize(pick(path("action", "low_comfort")));
    }

    public static String pickFlatterPraise() {
        return pickFlatterPraiseCue().text();
    }

    public static PickedLine pickFlatterPraiseCue() {
        return normalize(pick(path("branch", "flatter_praise")));
    }

    public static String pickFlatterGift() {
        return pickFlatterGiftCue().text();
    }

    public static PickedLine pickFlatterGiftCue() {
        return normalize(pick(path("branch", "flatter_gift")));
    }

    public static String pickCommunicationCrankHard(RelationStage stage) {
        return pickCommunicationCrankHardCue(stage).text();
    }

    public static PickedLine pickCommunicationCrankHardCue(RelationStage stage) {
        return normalize(pick(topicPath("communication_crank", "hard", stageKey(stage))));
    }

    public static String pickCommunicationCrankSoft(RelationStage stage) {
        return pickCommunicationCrankSoftCue(stage).text();
    }

    public static PickedLine pickCommunicationCrankSoftCue(RelationStage stage) {
        return normalize(pick(topicPath("communication_crank", "soft", stageKey(stage))));
    }

    public static String pickChatTopic(String topicKey, RelationStage stage) {
        return pickChatTopicCue(topicKey, stage).text();
    }

    public static PickedLine pickChatTopicCue(String topicKey, RelationStage stage) {
        return normalize(pick(topicPath("topics", topicKey, stageKey(stage))));
    }

    public static String pickTimeTopic(RelationStage stage, String timeOfDay) {
        return pickTimeTopicCue(stage, timeOfDay).text();
    }

    public static PickedLine pickTimeTopicCue(RelationStage stage, String timeOfDay) {
        return normalize(pick(topicPath("topics", "time", timeBucket(timeOfDay), stageKey(stage))));
    }

    public static String pickWeatherSpecialTopic(String category, RelationStage stage) {
        return pickWeatherSpecialTopicCue(category, stage).text();
    }

    public static PickedLine pickWeatherSpecialTopicCue(String category, RelationStage stage) {
        if (category == null || category.isBlank()) {
            return PickedLine.empty();
        }
        return normalize(pick(topicPath("topics", "weather_special", category, stageKey(stage))));
    }

    private static LoadedPoolResource loadRoot() {
        return loadRoot(RESOURCE_PATH);
    }

    private static LoadedPoolResource loadRoot(String resourcePath) {
        String language = DialogueLocaleResolver.currentLanguage();
        String cacheKey = language + ":" + resourcePath;
        LoadedPoolResource cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        LoadedPoolResource loaded = loadLocalizedRoot(resourcePath);
        CACHE.put(cacheKey, loaded);
        return loaded;
    }

    private static LoadedPoolResource loadLocalizedRoot(String resourcePath) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            for (String language : DialogueLocaleResolver.fallbackLanguages()) {
                ResourceLocation file = new ResourceLocation(
                        "maidmarriage",
                        "dialogue/" + language + "/pools/" + resourcePath
                );
                var optionalResource = minecraft.getResourceManager().getResource(file);
                if (optionalResource.isEmpty()) {
                    continue;
                }
                try (InputStream stream = optionalResource.get().open()) {
                    JsonObject root = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    return new LoadedPoolResource(file, root == null ? new JsonObject() : root);
                } catch (Exception exception) {
                    LOGGER.warn("Failed to load localized dialogue pool resource {}", file, exception);
                }
            }
        }

        String legacyPath = "/assets/maidmarriage/" + POOL_ROOT + resourcePath;
        try (InputStream stream = HugDialogueTextPools.class.getResourceAsStream(legacyPath)) {
            if (stream == null) {
                LOGGER.warn("Cannot find dialogue pool resource {}", legacyPath);
                return new LoadedPoolResource(new ResourceLocation("maidmarriage", POOL_ROOT + resourcePath), new JsonObject());
            }
            JsonObject root = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            return new LoadedPoolResource(
                    new ResourceLocation("maidmarriage", POOL_ROOT + resourcePath),
                    root == null ? new JsonObject() : root
            );
        } catch (Exception exception) {
            LOGGER.warn("Failed to load dialogue pool resource {}", legacyPath, exception);
            return new LoadedPoolResource(new ResourceLocation("maidmarriage", POOL_ROOT + resourcePath), new JsonObject());
        }
    }

    private static PoolValues path(String... segments) {
        return path(loadRoot(), segments);
    }

    private static PoolValues topicPath(String... segments) {
        return path(loadRoot(TOPIC_RESOURCE_PATH), segments);
    }

    private static PoolValues path(LoadedPoolResource resource, String... segments) {
        JsonElement current = resource.root();
        for (String segment : segments) {
            if (current == null || !current.isJsonObject()) {
                return new PoolValues(resource.location(), jsonPath(segments), List.of());
            }
            current = current.getAsJsonObject().get(segment);
        }
        if (current == null || !current.isJsonArray()) {
            return new PoolValues(resource.location(), jsonPath(segments), List.of());
        }

        JsonArray array = current.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return new PoolValues(resource.location(), jsonPath(segments), List.copyOf(values));
    }

    private static PickedLine pick(PoolValues values) {
        if (values == null || values.values().isEmpty()) {
            return PickedLine.empty();
        }
        int index = ThreadLocalRandom.current().nextInt(values.values().size());
        String raw = values.values().get(index);
        String source = values.location() + "#" + values.jsonPath() + "[" + index + "]:0";
        return new PickedLine(raw == null ? "" : raw, source);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw
                .replace("{player}", "${player}")
                .replace("{maid}", "${maid}");
    }

    private static PickedLine normalize(PickedLine picked) {
        if (picked == null) {
            return PickedLine.empty();
        }
        return new PickedLine(normalize(picked.text()), picked.source());
    }

    private static String jsonPath(String... segments) {
        StringBuilder builder = new StringBuilder("$");
        for (String segment : segments) {
            builder.append('.').append(segment);
        }
        return builder.toString();
    }

    private static String stageKey(RelationStage stage) {
        if (stage == null) {
            return "initial";
        }
        return switch (stage) {
            case INITIAL -> "initial";
            case WARM -> "warm";
            case CLOSE -> "close";
            case DATING -> "dating";
            case MARRIAGE -> "marriage";
        };
    }

    private static String actionStageKey(RelationStage stage, String fallback) {
        if (stage == null) {
            return fallback;
        }
        return switch (stage) {
            case INITIAL -> fallback;
            case WARM -> "warm";
            case CLOSE -> "close";
            case DATING -> "dating";
            case MARRIAGE -> "marriage";
        };
    }

    private static String moodKey(MaidMoodData.MoodState mood) {
        if (mood == null) {
            return "normal";
        }
        return switch (mood) {
            case DEPRESSED -> "depressed";
            case GENERAL -> "general";
            case NORMAL -> "normal";
            case HAPPY -> "happy";
            case LOVE -> "love";
        };
    }

    private static String longingEntryKey(RelationStage stage, MaidMoodData.MoodState mood) {
        if (mood == MaidMoodData.MoodState.DEPRESSED || mood == MaidMoodData.MoodState.GENERAL) {
            return "low_mood";
        }
        return stage == RelationStage.MARRIAGE ? "marriage" : "dating";
    }

    private static String timeBucket(String timeOfDay) {
        if (timeOfDay == null || timeOfDay.isBlank()) {
            return "day";
        }
        return switch (timeOfDay) {
            case "morning" -> "morning";
            case "evening" -> "evening";
            case "night", "midnight" -> "night";
            case "noon", "afternoon" -> "day";
            default -> "day";
        };
    }

    private record LoadedPoolResource(ResourceLocation location, JsonObject root) {
    }

    private record PoolValues(ResourceLocation location, String jsonPath, List<String> values) {
    }

    public record PickedLine(String text, String source) {
        public static PickedLine empty() {
            return new PickedLine("", "");
        }
    }
}
