package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.compat.RelationStage;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;

public final class HugDialogueTextPools {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE_PATH = "/assets/maidmarriage/dialogue/pools/hug_menu_v4.json";
    private static final String TOPIC_RESOURCE_PATH = "/assets/maidmarriage/dialogue/pools/hug_chat_topics_v1.json";
    private static final Gson GSON = new Gson();
    private static final JsonObject ROOT = loadRoot();
    private static final JsonObject TOPIC_ROOT = loadRoot(TOPIC_RESOURCE_PATH);

    private HugDialogueTextPools() {
    }

    public static String pickEntry(RelationStage stage, MaidMoodData.MoodState mood) {
        return normalize(pick(path("entry", stageKey(stage), moodKey(mood))));
    }

    public static String pickTimeEntry(RelationStage stage, String timeOfDay) {
        return normalize(pick(topicPath("entry_time", timeBucket(timeOfDay), stageKey(stage))));
    }

    public static String pickMixedEntry(RelationStage stage,
                                        MaidMoodData.MoodState mood,
                                        String timeOfDay) {
        String moodEntry = pickEntry(stage, mood);
        String timeEntry = pickTimeEntry(stage, timeOfDay);
        if (moodEntry.isBlank()) {
            return timeEntry;
        }
        if (timeEntry.isBlank()) {
            return moodEntry;
        }
        return ThreadLocalRandom.current().nextBoolean() ? moodEntry : timeEntry;
    }

    public static String pickLongingEntry(RelationStage stage, MaidMoodData.MoodState mood) {
        String key = longingEntryKey(stage, mood);
        return normalize(pick(path("longing_entry", key)));
    }

    public static String pickChat(RelationStage stage, MaidMoodData.MoodState mood) {
        return pickEntry(stage, mood);
    }

    public static String pickPet(RelationStage stage) {
        return normalize(pick(path("action", "pet", actionStageKey(stage, "warm"))));
    }

    public static String pickHug(RelationStage stage) {
        return normalize(pick(path("action", "hug", actionStageKey(stage, "close"))));
    }

    public static String pickKiss(RelationStage stage) {
        return normalize(pick(path("action", "kiss", actionStageKey(stage, "dating"))));
    }

    public static String pickReleaseHug() {
        return normalize(pick(path("action", "release_hug")));
    }

    public static String pickLowComfort() {
        return normalize(pick(path("action", "low_comfort")));
    }

    public static String pickFlatterPraise() {
        return normalize(pick(path("branch", "flatter_praise")));
    }

    public static String pickFlatterGift() {
        return normalize(pick(path("branch", "flatter_gift")));
    }

    public static String pickCommunicationCrankHard(RelationStage stage) {
        return normalize(pick(topicPath("communication_crank", "hard", stageKey(stage))));
    }

    public static String pickCommunicationCrankSoft(RelationStage stage) {
        return normalize(pick(topicPath("communication_crank", "soft", stageKey(stage))));
    }

    public static String pickChatTopic(String topicKey, RelationStage stage) {
        return normalize(pick(topicPath("topics", topicKey, stageKey(stage))));
    }

    public static String pickTimeTopic(RelationStage stage, String timeOfDay) {
        return normalize(pick(topicPath("topics", "time", timeBucket(timeOfDay), stageKey(stage))));
    }

    public static String pickWeatherSpecialTopic(String category, RelationStage stage) {
        if (category == null || category.isBlank()) {
            return "";
        }
        return normalize(pick(topicPath("topics", "weather_special", category, stageKey(stage))));
    }

    private static JsonObject loadRoot() {
        return loadRoot(RESOURCE_PATH);
    }

    private static JsonObject loadRoot(String resourcePath) {
        try (InputStream stream = HugDialogueTextPools.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.warn("Cannot find dialogue pool resource {}", resourcePath);
                return new JsonObject();
            }
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception exception) {
            LOGGER.warn("Failed to load dialogue pool resource {}", resourcePath, exception);
            return new JsonObject();
        }
    }

    private static List<String> path(String... segments) {
        return path(ROOT, segments);
    }

    private static List<String> topicPath(String... segments) {
        return path(TOPIC_ROOT, segments);
    }

    private static List<String> path(JsonObject root, String... segments) {
        JsonElement current = root;
        for (String segment : segments) {
            if (current == null || !current.isJsonObject()) {
                return List.of();
            }
            current = current.getAsJsonObject().get(segment);
        }
        if (current == null || !current.isJsonArray()) {
            return List.of();
        }

        JsonArray array = current.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private static String pick(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw
                .replace("{player}", "${player}")
                .replace("{maid}", "${maid}");
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
}
