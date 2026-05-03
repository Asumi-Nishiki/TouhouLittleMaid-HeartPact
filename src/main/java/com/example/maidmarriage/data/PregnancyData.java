package com.example.maidmarriage.data;

import com.example.maidmarriage.compat.MaidMoodManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record PregnancyData(
        boolean pregnant,
        Optional<UUID> father,
        long conceivedGameTime,
        boolean twinsPregnancy,
        boolean firstExperience,
        boolean firstBirth,
        long lastRomanceDay,
        long postpartumEndGameTime,
        long lastBirthGameTime,
        int conceptionBonusStep,
        int failedConceptionCount,
        boolean guaranteedConceptionNextAttempt) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final long NO_ROMANCE_DAY = -1L;
    private static final int MAX_CONCEPTION_BONUS_STEP = 6;
    private static final int GUARANTEED_CONCEPTION_FAIL_COUNT = 8;
    private static final double BASE_CONCEPTION_CHANCE = 0.20D;
    private static final double CONCEPTION_STEP_CHANCE = 0.05D;

    public static final PregnancyData EMPTY = new PregnancyData(
            false,
            Optional.empty(),
            0L,
            false,
            false,
            false,
            NO_ROMANCE_DAY,
            0L,
            0L,
            0,
            0,
            false);

    public static final Codec<PregnancyData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("pregnant", false).forGetter(PregnancyData::pregnant),
                    UUID_CODEC.optionalFieldOf("father").forGetter(PregnancyData::father),
                    Codec.LONG.optionalFieldOf("conceived_game_time", 0L).forGetter(PregnancyData::conceivedGameTime),
                    Codec.BOOL.optionalFieldOf("twins_pregnancy", false).forGetter(PregnancyData::twinsPregnancy),
                    Codec.BOOL.optionalFieldOf("first_experience", false).forGetter(PregnancyData::firstExperience),
                    Codec.BOOL.optionalFieldOf("first_birth", false).forGetter(PregnancyData::firstBirth),
                    Codec.LONG.optionalFieldOf("last_romance_day", NO_ROMANCE_DAY).forGetter(PregnancyData::lastRomanceDay),
                    Codec.LONG.optionalFieldOf("postpartum_end_game_time", 0L).forGetter(PregnancyData::postpartumEndGameTime),
                    Codec.LONG.optionalFieldOf("last_birth_game_time", 0L).forGetter(PregnancyData::lastBirthGameTime),
                    Codec.INT.optionalFieldOf("conception_bonus_step", 0).forGetter(PregnancyData::conceptionBonusStep),
                    Codec.INT.optionalFieldOf("failed_conception_count", 0).forGetter(PregnancyData::failedConceptionCount),
                    Codec.BOOL.optionalFieldOf("guaranteed_conception_next_attempt", false)
                            .forGetter(PregnancyData::guaranteedConceptionNextAttempt)
            ).apply(instance, PregnancyData::new));

    public PregnancyData conceive(UUID fatherId, long gameTime, boolean twins) {
        return new PregnancyData(
                true,
                Optional.of(fatherId),
                gameTime,
                twins,
                firstExperience,
                firstBirth,
                lastRomanceDay,
                postpartumEndGameTime,
                lastBirthGameTime,
                0,
                0,
                false);
    }

    public PregnancyData conceive(UUID fatherId, long gameTime) {
        return conceive(fatherId, gameTime, false);
    }

    public PregnancyData clear() {
        return new PregnancyData(
                false,
                Optional.empty(),
                0L,
                false,
                firstExperience,
                firstBirth,
                lastRomanceDay,
                postpartumEndGameTime,
                lastBirthGameTime,
                conceptionBonusStep,
                failedConceptionCount,
                guaranteedConceptionNextAttempt);
    }

    public PregnancyData markRomance(long gameTime) {
        return new PregnancyData(
                pregnant,
                father,
                conceivedGameTime,
                twinsPregnancy,
                true,
                firstBirth,
                gameTime / 24000L,
                postpartumEndGameTime,
                lastBirthGameTime,
                conceptionBonusStep,
                failedConceptionCount,
                guaranteedConceptionNextAttempt);
    }

    public PregnancyData forceLonging(long gameTime) {
        long currentDay = gameTime / 24000L;
        long longingDay = currentDay - MaidMoodManager.LONGING_TRIGGER_DAYS;
        return new PregnancyData(
                pregnant,
                father,
                conceivedGameTime,
                twinsPregnancy,
                true,
                firstBirth,
                longingDay,
                postpartumEndGameTime,
                lastBirthGameTime,
                conceptionBonusStep,
                failedConceptionCount,
                guaranteedConceptionNextAttempt);
    }

    public PregnancyData completeBirth(long gameTime, long postpartumRecoveryTicks) {
        long endTick = postpartumRecoveryTicks <= 0L ? 0L : gameTime + postpartumRecoveryTicks;
        return new PregnancyData(
                false,
                Optional.empty(),
                0L,
                false,
                true,
                true,
                lastRomanceDay,
                endTick,
                gameTime,
                0,
                0,
                false);
    }

    public PregnancyData clearPostpartum() {
        return new PregnancyData(
                pregnant,
                father,
                conceivedGameTime,
                twinsPregnancy,
                firstExperience,
                firstBirth,
                lastRomanceDay,
                0L,
                lastBirthGameTime,
                conceptionBonusStep,
                failedConceptionCount,
                guaranteedConceptionNextAttempt);
    }

    public boolean isInPostpartumRecovery(long gameTime) {
        return postpartumEndGameTime > gameTime;
    }

    public PregnancyData reducePostpartumRemainingByPercent(long gameTime, double percent) {
        if (!isInPostpartumRecovery(gameTime)) {
            return this;
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, percent));
        long remaining = Math.max(0L, postpartumEndGameTime - gameTime);
        long reduced = Math.max(0L, (long) Math.floor(remaining * (1.0D - ratio)));
        long newEnd = gameTime + reduced;
        return new PregnancyData(
                pregnant,
                father,
                conceivedGameTime,
                twinsPregnancy,
                firstExperience,
                firstBirth,
                lastRomanceDay,
                newEnd,
                lastBirthGameTime,
                conceptionBonusStep,
                failedConceptionCount,
                guaranteedConceptionNextAttempt);
    }

    /**
     * 当前普通同眠的怀孕概率：
     * 首次 20%，之后每次失败 +5%，最高 50%。
     */
    public double currentConceptionChance() {
        if (guaranteedConceptionNextAttempt) {
            return 1.0D;
        }
        int clampedStep = Math.max(0, Math.min(MAX_CONCEPTION_BONUS_STEP, conceptionBonusStep));
        return BASE_CONCEPTION_CHANCE + clampedStep * CONCEPTION_STEP_CHANCE;
    }

    /**
     * 普通同眠失败后的保底推进：
     * 概率从 20% 平滑加到 50%，连续失败 8 次后下一次必怀。
     */
    public PregnancyData onConceptionFailed() {
        if (pregnant || guaranteedConceptionNextAttempt) {
            return this;
        }
        int nextFailedCount = failedConceptionCount + 1;
        int nextBonusStep = Math.min(MAX_CONCEPTION_BONUS_STEP, conceptionBonusStep + 1);
        boolean guaranteeNextAttempt = nextFailedCount >= GUARANTEED_CONCEPTION_FAIL_COUNT;
        return new PregnancyData(
                pregnant,
                father,
                conceivedGameTime,
                twinsPregnancy,
                firstExperience,
                firstBirth,
                lastRomanceDay,
                postpartumEndGameTime,
                lastBirthGameTime,
                nextBonusStep,
                nextFailedCount,
                guaranteeNextAttempt);
    }

    public PregnancyData resetConceptionProgress() {
        return new PregnancyData(
                pregnant,
                father,
                conceivedGameTime,
                twinsPregnancy,
                firstExperience,
                firstBirth,
                lastRomanceDay,
                postpartumEndGameTime,
                lastBirthGameTime,
                0,
                0,
                false);
    }

    public boolean isWithinRecentBirthCooldown(long gameTime, long cooldownTicks) {
        if (lastBirthGameTime <= 0L || cooldownTicks <= 0L) {
            return false;
        }
        return gameTime - lastBirthGameTime < cooldownTicks;
    }

    public boolean isPregnantWith(UUID fatherId) {
        return this.pregnant && this.father.filter(fatherId::equals).isPresent();
    }

    public Optional<MoodState> currentMood(long gameTime) {
        if (!firstExperience || lastRomanceDay == NO_ROMANCE_DAY) {
            return Optional.empty();
        }
        long currentDay = gameTime / 24000L;
        long missingDays = Math.max(0L, currentDay - lastRomanceDay);
        if (missingDays == 0) {
            return Optional.of(MoodState.CALM);
        }
        if (missingDays == 1) {
            return Optional.of(MoodState.NORMAL);
        }
        if (missingDays < Math.max(2L, MaidMoodManager.LONGING_TRIGGER_DAYS)) {
            return Optional.of(MoodState.NORMAL);
        }
        return Optional.of(MoodState.LONGING);
    }

    public enum MoodState {
        CALM,
        NORMAL,
        LONGING;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
