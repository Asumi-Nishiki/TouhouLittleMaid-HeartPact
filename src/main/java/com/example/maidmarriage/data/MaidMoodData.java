package com.example.maidmarriage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;

/**
 * 女仆心情持久化数据。
 *
 * <p>这里刻意把心情从 PregnancyData 中拆出来：
 * 怀孕数据只负责生育流程，心情数据负责日常互动、好感加成和面板显示。
 * 这样以后继续改事件权重时，不会误伤已经存在的怀孕/分娩存档字段。</p>
 */
public record MaidMoodData(int moodValue, long moodDay, long lastInteractionDay) {
    /** 心情最小值：彻底疲惫。低于最低档时，部分行动会被阻止。 */
    public static final int MIN_MOOD = 0;
    /** 心情最大值：五档离散值中的最高档。 */
    public static final int MAX_MOOD = 25;
    /** 心情档位跨度：UI 显示仍按 5 一档划分。 */
    public static final int MOOD_STEP = 5;
    /** 默认从“普通”档开始。 */
    public static final int DEFAULT_MOOD = 15;

    public static final long UNSET_DAY = -1L;

    public static final MaidMoodData EMPTY = new MaidMoodData(DEFAULT_MOOD, UNSET_DAY, UNSET_DAY);

    public static final Codec<MaidMoodData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("mood_value", DEFAULT_MOOD).forGetter(MaidMoodData::moodValue),
                    Codec.LONG.optionalFieldOf("mood_day", UNSET_DAY).forGetter(MaidMoodData::moodDay),
                    Codec.LONG.optionalFieldOf("last_interaction_day", UNSET_DAY).forGetter(MaidMoodData::lastInteractionDay)
            ).apply(instance, MaidMoodData::new));

    public MaidMoodData {
        moodValue = clamp(moodValue);
    }

    public MaidMoodData(int moodValue) {
        this(moodValue, UNSET_DAY, UNSET_DAY);
    }

    public MaidMoodData(int moodValue, long moodDay) {
        this(moodValue, moodDay, UNSET_DAY);
    }

    public MaidMoodData add(int amount) {
        return new MaidMoodData(moodValue + amount, moodDay, lastInteractionDay);
    }

    public MaidMoodData set(int value) {
        return new MaidMoodData(value, moodDay, lastInteractionDay);
    }

    public MaidMoodData rerollForDay(int value, long day) {
        return new MaidMoodData(value, day, lastInteractionDay);
    }

    public MaidMoodData markInteraction(long day) {
        return new MaidMoodData(moodValue, moodDay, day);
    }

    public MoodState state() {
        return MoodState.fromValue(moodValue);
    }

    public static int clamp(int value) {
        return Math.max(MIN_MOOD, Math.min(MAX_MOOD, value));
    }

    public static int snap(int value) {
        int clamped = Math.max(MIN_MOOD, Math.min(MAX_MOOD, value));
        int snapped = Math.round(clamped / (float) MOOD_STEP) * MOOD_STEP;
        return Math.max(MIN_MOOD, Math.min(MAX_MOOD, snapped));
    }

    public enum MoodState {
        DEPRESSED(5, 0.00D),
        GENERAL(10, 0.00D),
        NORMAL(15, 1.00D),
        HAPPY(20, 1.50D),
        LOVE(25, 2.00D);

        private final int value;
        private final double favorabilityMultiplier;

        MoodState(int value, double favorabilityMultiplier) {
            this.value = value;
            this.favorabilityMultiplier = favorabilityMultiplier;
        }

        public int value() {
            return value;
        }

        public double favorabilityMultiplier() {
            return favorabilityMultiplier;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static MoodState fromValue(int value) {
            int clamped = clamp(value);
            if (clamped < GENERAL.value) {
                return DEPRESSED;
            }
            if (clamped < NORMAL.value) {
                return GENERAL;
            }
            if (clamped < HAPPY.value) {
                return NORMAL;
            }
            if (clamped < LOVE.value) {
                return HAPPY;
            }
            return LOVE;
        }
    }
}
