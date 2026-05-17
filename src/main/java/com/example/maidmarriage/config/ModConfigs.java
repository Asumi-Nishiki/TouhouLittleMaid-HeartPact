package com.example.maidmarriage.config;

import com.example.maidmarriage.compat.RelationshipThresholds;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ModConfigs {
    public static final double DEFAULT_LIFT_HEIGHT = 0.10D;
    public static final double MIN_LIFT_HEIGHT = -0.20D;
    public static final double MAX_LIFT_HEIGHT = 1.50D;

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue HAREM_MODE;
    private static final ForgeConfigSpec.IntValue REQUIRED_FAVORABILITY;
    private static final ForgeConfigSpec.DoubleValue PREGNANCY_CHANCE;
    private static final ForgeConfigSpec.IntValue PREGNANCY_BIRTH_DAYS;
    private static final ForgeConfigSpec.IntValue CHILD_GROWTH_DAYS;
    private static final ForgeConfigSpec.IntValue LONGING_DAYS;
    private static final ForgeConfigSpec.BooleanValue CLINGY_MAID_ENABLED;
    private static final ForgeConfigSpec.BooleanValue POSTPARTUM_RECOVERY_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> MAID_ADDRESSING;
    private static final ForgeConfigSpec.ConfigValue<String> CHILD_MAID_ADDRESSING;
    private static final ForgeConfigSpec.ConfigValue<String> DIALOGUE_SCRIPT_PATH;
    private static final ForgeConfigSpec.BooleanValue HEART_PACT_VOICE_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> HEART_PACT_VOICE_SCRIPT_NAME;
    private static final ForgeConfigSpec.ConfigValue<String> HEART_PACT_TTS_CHILD_NAME;
    private static final ForgeConfigSpec.ConfigValue<String> HEART_PACT_TTS_PLAYER_NAME;
    private static final ForgeConfigSpec.ConfigValue<String> HEART_PACT_TTS_PLAYER_MAID_NAME;
    private static final ForgeConfigSpec.DoubleValue HEART_PACT_VOICE_VOLUME;
    private static final ForgeConfigSpec.EnumValue<RhythmHitKey> RHYTHM_HIT_KEY;
    private static final ForgeConfigSpec.EnumValue<ActionKey> PET_HEAD_KEY;
    private static final ForgeConfigSpec.EnumValue<ActionKey> INTERACTION_KEY;
    private static final ForgeConfigSpec.BooleanValue RHYTHM_ALWAYS_SKIP;
    private static final ForgeConfigSpec.DoubleValue LIFT_HEIGHT;
    private static final ForgeConfigSpec.DoubleValue HUG_DISTANCE;
    private static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_TOOLS;
    private static final ForgeConfigSpec.BooleanValue SHOW_PREGNANCY_DEBUG_COUNTDOWN;
    private static final ForgeConfigSpec.BooleanValue SHOW_UI_ACTION_DEBUG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("General settings for Maid Marriage.")
                .translation("config.maidmarriage.general")
                .push("general");

        HAREM_MODE = builder
                .comment("Allow a player to marry multiple maids.")
                .translation("config.maidmarriage.harem_mode")
                .define("haremMode", false);

        REQUIRED_FAVORABILITY = builder
                .comment("Favorability threshold to marry.")
                .translation("config.maidmarriage.required_favorability")
                .defineInRange("requiredFavorability", RelationshipThresholds.MARRIAGE_UNLOCK, 0, RelationshipThresholds.FAVORABILITY_MAX);

        PREGNANCY_CHANCE = builder
                .comment("Conception chance after each romance scene. Range: 0.0~1.0")
                .translation("config.maidmarriage.pregnancy_chance")
                .defineInRange("pregnancyChance", 0.6D, 0.0D, 1.0D);

        PREGNANCY_BIRTH_DAYS = builder
                .comment("Days required from conception to childbirth.")
                .translation("config.maidmarriage.pregnancy_birth_days")
                .defineInRange("pregnancyBirthDays", 5, 1, 120);

        CHILD_GROWTH_DAYS = builder
                .comment("Days required for child maid to grow into an adult maid.")
                .translation("config.maidmarriage.child_growth_days")
                .defineInRange("childGrowthDays", 10, 3, 120);

        LONGING_DAYS = builder
                .comment("Days without romance before entering longing mood.")
                .translation("config.maidmarriage.longing_days")
                .defineInRange("longingDays", 3, 1, 30);

        CLINGY_MAID_ENABLED = builder
                .comment("Enable clingy maid behavior and longing mood display.")
                .translation("config.maidmarriage.clingy_maid_enabled")
                .define("clingyMaidEnabled", true);

        POSTPARTUM_RECOVERY_ENABLED = builder
                .comment("Server authority: enable postpartum recovery effects. Clients cannot disable this in the in-game config screen.")
                .translation("config.maidmarriage.postpartum_recovery_enabled")
                .define("postpartumRecoveryEnabled", true);

        MAID_ADDRESSING = builder
                .comment("How maids address the player in dialogue. Empty means use player username.")
                .translation("config.maidmarriage.maid_addressing")
                .define("maidAddressing", "");

        CHILD_MAID_ADDRESSING = builder
                .comment("How child maids address the player in dialogue.")
                .translation("config.maidmarriage.child_maid_addressing")
                .define("childMaidAddressing", "爸爸");

        DIALOGUE_SCRIPT_PATH = builder
                .comment("External dialogue script path. Relative paths are resolved under the config folder.")
                .translation("config.maidmarriage.dialogue_script_path")
                .define("dialogueScriptPath", "maidmarriage/custom-dialogues.json");

        builder.pop();

        builder.comment("Heart Pact voice settings.")
                .translation("config.maidmarriage.voice")
                .push("voice");

        HEART_PACT_VOICE_ENABLED = builder
                .comment("Enable Heart Pact dialogue voice playback when prepared voice files are available.")
                .translation("config.maidmarriage.voice_enabled")
                .define("heartPactVoiceEnabled", false);

        HEART_PACT_VOICE_SCRIPT_NAME = builder
                .comment("Dialogue locale/script name used by Heart Pact voice files, for example ja_jp, zh_cn or en_us.")
                .translation("config.maidmarriage.voice_script_name")
                .define("heartPactVoiceScriptName", "ja_jp");

        HEART_PACT_TTS_CHILD_NAME = builder
                .comment("TTS-only replacement for {child} in Heart Pact voice text. Empty means use locale default.")
                .translation("config.maidmarriage.heart_pact_tts_child_name")
                .define("heartPactTtsChildName", "");

        HEART_PACT_TTS_PLAYER_NAME = builder
                .comment("TTS-only replacement for {player} in Heart Pact voice text. Empty means use current addressing / player username.")
                .translation("config.maidmarriage.heart_pact_tts_player_name")
                .define("heartPactTtsPlayerName", "");

        HEART_PACT_TTS_PLAYER_MAID_NAME = builder
                .comment("TTS-only replacement for {player_maid} in Heart Pact voice text. Empty means use current maid addressing / player username.")
                .translation("config.maidmarriage.heart_pact_tts_player_maid_name")
                .define("heartPactTtsPlayerMaidName", "");

        HEART_PACT_VOICE_VOLUME = builder
                .comment("Heart Pact dialogue voice volume.")
                .translation("config.maidmarriage.voice_volume")
                .defineInRange("heartPactVoiceVolume", 1.0D, 0.0D, 2.0D);

        builder.pop();

        builder.comment("Rhythm game settings.")
                .translation("config.maidmarriage.rhythm_game")
                .push("rhythm_game");

        RHYTHM_HIT_KEY = builder
                .comment("Rhythm game hit key.")
                .translation("config.maidmarriage.rhythm_hit_key")
                .defineEnum("rhythmHitKey", RhythmHitKey.J);

        RHYTHM_ALWAYS_SKIP = builder
                .comment("Always skip rhythm game and settle with skip chance.")
                .translation("config.maidmarriage.rhythm_always_skip")
                .define("rhythmAlwaysSkip", false);

        builder.pop();

        builder.comment("Action key and interaction settings.")
                .translation("config.maidmarriage.action_keys")
                .push("action_keys");

        PET_HEAD_KEY = builder
                .comment("Head-pat / lift interaction key.")
                .translation("config.maidmarriage.pet_head_key")
                .defineEnum("petHeadKey", ActionKey.O);

        INTERACTION_KEY = builder
                .comment("Unified interaction key.")
                .translation("config.maidmarriage.interaction_key")
                .defineEnum("interactionKey", ActionKey.J);

        LIFT_HEIGHT = builder
                .comment("Extra height offset for lift little maid pose.")
                .translation("config.maidmarriage.lift_height")
                .defineInRange("liftHeight", DEFAULT_LIFT_HEIGHT, MIN_LIFT_HEIGHT, MAX_LIFT_HEIGHT);

        HUG_DISTANCE = builder
                .comment("Lock distance used by standing hug pose.")
                .translation("config.maidmarriage.hug_distance")
                .defineInRange("hugDistance", 0.80D, 0.10D, 2.00D);

        builder.pop();

        builder.comment("Debug settings.")
                .translation("config.maidmarriage.debug")
                .push("debug");

        ENABLE_DEBUG_TOOLS = builder
                .comment("Enable developer-only hotkeys and debug data tools.")
                .translation("config.maidmarriage.enable_debug_tools")
                .define("enableDebugTools", false);

        SHOW_PREGNANCY_DEBUG_COUNTDOWN = builder
                .comment("Show pregnancy countdown in top-left corner with seconds.")
                .translation("config.maidmarriage.show_pregnancy_debug_countdown")
                .define("showPregnancyDebugCountdown", false);

        SHOW_UI_ACTION_DEBUG = builder
                .comment("Show dialogue UI action execution tips in the top-left corner.")
                .translation("config.maidmarriage.show_ui_action_debug")
                .define("showUiActionDebug", false);

        builder.pop();
        SPEC = builder.build();
    }

    private ModConfigs() {
    }

    public static boolean haremMode() {
        return HAREM_MODE.get();
    }

    public static void setHaremMode(boolean enabled) {
        HAREM_MODE.set(enabled);
    }

    public static int requiredFavorability() {
        return REQUIRED_FAVORABILITY.get();
    }

    public static void setRequiredFavorability(int value) {
        REQUIRED_FAVORABILITY.set(Math.max(0, Math.min(RelationshipThresholds.FAVORABILITY_MAX, value)));
    }

    public static double pregnancyChance() {
        return PREGNANCY_CHANCE.get();
    }

    public static void setPregnancyChance(double value) {
        PREGNANCY_CHANCE.set(Math.max(0.0D, Math.min(1.0D, value)));
    }

    public static int childGrowthDays() {
        return CHILD_GROWTH_DAYS.get();
    }

    public static void setChildGrowthDays(int value) {
        CHILD_GROWTH_DAYS.set(Math.max(3, Math.min(120, value)));
    }

    public static int pregnancyBirthDays() {
        return PREGNANCY_BIRTH_DAYS.get();
    }

    public static void setPregnancyBirthDays(int value) {
        PREGNANCY_BIRTH_DAYS.set(Math.max(1, Math.min(120, value)));
    }

    public static int longingDays() {
        return LONGING_DAYS.get();
    }

    public static void setLongingDays(int value) {
        LONGING_DAYS.set(Math.max(1, Math.min(30, value)));
    }

    public static boolean clingyMaidEnabled() {
        return CLINGY_MAID_ENABLED.get();
    }

    public static void setClingyMaidEnabled(boolean enabled) {
        CLINGY_MAID_ENABLED.set(enabled);
    }

    public static boolean postpartumRecoveryEnabled() {
        return POSTPARTUM_RECOVERY_ENABLED.get();
    }

    public static void setPostpartumRecoveryEnabled(boolean enabled) {
        POSTPARTUM_RECOVERY_ENABLED.set(enabled);
    }

    public static String maidAddressing() {
        return MAID_ADDRESSING.get();
    }

    public static void setMaidAddressing(String value) {
        MAID_ADDRESSING.set(value == null ? "" : value.trim());
    }

    public static String childMaidAddressing() {
        return CHILD_MAID_ADDRESSING.get();
    }

    public static void setChildMaidAddressing(String value) {
        CHILD_MAID_ADDRESSING.set(value == null ? "爸爸" : value.trim());
    }

    public static String resolveMaidAddressing(String playerName) {
        String custom = maidAddressing();
        if (custom == null || custom.isBlank()) {
            return playerName;
        }
        return custom;
    }

    public static String resolveChildMaidAddressing(String playerName) {
        String custom = childMaidAddressing();
        if (custom == null || custom.isBlank()) {
            return playerName;
        }
        return custom;
    }

    public static String dialogueScriptPath() {
        return DIALOGUE_SCRIPT_PATH.get();
    }

    public static void setDialogueScriptPath(String value) {
        String sanitized = value == null ? "" : value.trim().replace('\\', '/');
        if (sanitized.length() > 512) {
            sanitized = sanitized.substring(0, 512);
        }
        DIALOGUE_SCRIPT_PATH.set(sanitized);
    }

    public static boolean heartPactVoiceEnabled() {
        return HEART_PACT_VOICE_ENABLED.get();
    }

    public static void setHeartPactVoiceEnabled(boolean enabled) {
        HEART_PACT_VOICE_ENABLED.set(enabled);
    }

    public static String heartPactVoiceScriptName() {
        return HEART_PACT_VOICE_SCRIPT_NAME.get();
    }

    public static void setHeartPactVoiceScriptName(String value) {
        String sanitized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('\\', '_').replace('/', '_');
        HEART_PACT_VOICE_SCRIPT_NAME.set(sanitized.isBlank() ? "ja_jp" : sanitized);
    }

    public static String heartPactTtsChildName() {
        return HEART_PACT_TTS_CHILD_NAME.get();
    }

    public static void setHeartPactTtsChildName(String value) {
        HEART_PACT_TTS_CHILD_NAME.set(sanitizeTtsName(value));
    }

    public static String heartPactTtsPlayerName() {
        return HEART_PACT_TTS_PLAYER_NAME.get();
    }

    public static void setHeartPactTtsPlayerName(String value) {
        HEART_PACT_TTS_PLAYER_NAME.set(sanitizeTtsName(value));
    }

    public static String heartPactTtsPlayerMaidName() {
        return HEART_PACT_TTS_PLAYER_MAID_NAME.get();
    }

    public static void setHeartPactTtsPlayerMaidName(String value) {
        HEART_PACT_TTS_PLAYER_MAID_NAME.set(sanitizeTtsName(value));
    }

    public static String resolveHeartPactTtsChildName(String scriptName) {
        String custom = heartPactTtsChildName();
        return custom == null || custom.isBlank() ? "小さなメイド" : custom;
    }

    public static String resolveHeartPactTtsPlayerName(String scriptName, String fallbackPlayerName) {
        String custom = heartPactTtsPlayerName();
        if (custom == null || custom.isBlank()) {
            return "ご主人様";
        }
        return custom;
    }

    public static String resolveHeartPactTtsPlayerMaidName(String scriptName, String fallbackPlayerName) {
        String custom = heartPactTtsPlayerMaidName();
        if (custom == null || custom.isBlank()) {
            return "ご主人様";
        }
        return custom;
    }

    public static double heartPactVoiceVolume() {
        return HEART_PACT_VOICE_VOLUME.get();
    }

    public static void setHeartPactVoiceVolume(double value) {
        HEART_PACT_VOICE_VOLUME.set(Math.max(0.0D, Math.min(2.0D, value)));
    }

    public static RhythmHitKey rhythmHitKey() {
        return RHYTHM_HIT_KEY.get();
    }

    public static void setRhythmHitKey(RhythmHitKey key) {
        RHYTHM_HIT_KEY.set(key);
    }

    public static boolean rhythmAlwaysSkip() {
        return RHYTHM_ALWAYS_SKIP.get();
    }

    public static void setRhythmAlwaysSkip(boolean enabled) {
        RHYTHM_ALWAYS_SKIP.set(enabled);
    }

    public static ActionKey petHeadKey() {
        return PET_HEAD_KEY.get();
    }

    public static void setPetHeadKey(ActionKey key) {
        PET_HEAD_KEY.set(key);
    }

    public static ActionKey interactionKey() {
        return INTERACTION_KEY.get();
    }

    public static void setInteractionKey(ActionKey key) {
        INTERACTION_KEY.set(key);
    }

    public static void migrateOldActionKeyDefaults() {
        if (PET_HEAD_KEY.get() == ActionKey.L && INTERACTION_KEY.get() == ActionKey.J) {
            PET_HEAD_KEY.set(ActionKey.K);
            SPEC.save();
        }
    }

    public static double liftHeight() {
        return LIFT_HEIGHT.get();
    }

    public static void setLiftHeight(double value) {
        LIFT_HEIGHT.set(Math.max(MIN_LIFT_HEIGHT, Math.min(MAX_LIFT_HEIGHT, value)));
    }

    public static double hugDistance() {
        return HUG_DISTANCE.get();
    }

    public static void setHugDistance(double value) {
        HUG_DISTANCE.set(Math.max(0.10D, Math.min(2.00D, value)));
    }

    public static boolean enableDebugTools() {
        return ENABLE_DEBUG_TOOLS.get();
    }

    public static void setEnableDebugTools(boolean enabled) {
        ENABLE_DEBUG_TOOLS.set(enabled);
    }

    public static boolean showPregnancyDebugCountdown() {
        return SHOW_PREGNANCY_DEBUG_COUNTDOWN.get();
    }

    public static void setShowPregnancyDebugCountdown(boolean show) {
        SHOW_PREGNANCY_DEBUG_COUNTDOWN.set(show);
    }

    public static boolean showUiActionDebug() {
        return SHOW_UI_ACTION_DEBUG.get();
    }

    public static void setShowUiActionDebug(boolean show) {
        SHOW_UI_ACTION_DEBUG.set(show);
    }

    private static String sanitizeTtsName(String value) {
        String sanitized = value == null ? "" : value.trim();
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        return sanitized;
    }

    private static String defaultHeartPactChildName(String scriptName) {
        return switch (normalizeHeartPactLanguage(scriptName)) {
            case "zh" -> "小女仆";
            case "en" -> "Little Maid";
            default -> "小さなメイド";
        };
    }

    private static String defaultPlayerFallback(String scriptName, String fallbackPlayerName) {
        String fallback = fallbackPlayerName == null ? "" : fallbackPlayerName.trim();
        if (!fallback.isBlank()) {
            return fallback;
        }
        return switch (normalizeHeartPactLanguage(scriptName)) {
            case "zh" -> "主人";
            case "en" -> "Master";
            default -> "ご主人様";
        };
    }

    private static String defaultHeartPactPlayerTitle(String scriptName) {
        return switch (normalizeHeartPactLanguage(scriptName)) {
            case "zh" -> "主人";
            case "en" -> "Master";
            default -> "ご主人様";
        };
    }

    private static String normalizeHeartPactLanguage(String scriptName) {
        String normalized = scriptName == null ? "" : scriptName.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("zh")) {
            return "zh";
        }
        if (normalized.startsWith("en")) {
            return "en";
        }
        return "ja";
    }

    public enum RhythmHitKey {
        J,
        K,
        L,
        SEMICOLON,
        SPACE
    }

    public enum ActionKey {
        O,
        P,
        I,
        U,
        J,
        K,
        L,
        SEMICOLON,
        SPACE
    }
}
