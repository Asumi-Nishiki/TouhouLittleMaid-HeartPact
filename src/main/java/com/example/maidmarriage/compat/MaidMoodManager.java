package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.FavorabilityEffectPayload;
import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.data.ModTaskData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * 心情系统统一入口。
 *
 * <p>所有“事件改变心情 / 心情影响好感”的逻辑都集中在这里，
 * 后面要调数值只改这个类，不需要在送花、摸头、亲吻等事件里到处翻。</p>
 */
public final class MaidMoodManager {
    private static final String TAG_INTERACTION_FAVOR_DAY = "maidmarriage_interaction_favor_day";
    private static final String TAG_INTERACTION_FAVOR_GAIN_TODAY = "maidmarriage_interaction_favor_gain_today";
    private static final String TAG_INTERACTION_MOOD_DAY_PREFIX = "maidmarriage_interaction_mood_day_";
    private static final String TAG_INTERACTION_MOOD_COUNT_PREFIX = "maidmarriage_interaction_mood_count_";
    public static final int INTERACTION_DAILY_FAVOR_LIMIT = 16;
    public static final int INTERACTION_MOOD_GAIN = 2;
    public static final int INTERACTION_MOOD_GAIN_LIMIT_PER_DAY = 2;
    public static final String EVENT_FLOWER = "flower";
    public static final String EVENT_PET_HEAD = "pet_head";
    public static final String EVENT_HUG = "hug";
    public static final String EVENT_KISS = "kiss";
    public static final String EVENT_ROMANCE = "romance";
    public static final int LOVE_TEST_VALUE = MaidMoodData.MAX_MOOD;
    /**
     * 正式版固定三天未有效互动进入“忍耐”状态。
     *
     * <p>这个天数不再暴露给客户端配置，避免多人服务器里每个客户端显示/体验不一致。
     * 后续如果要调数值，只改这里这一处。</p>
     */
    public static final int LONGING_TRIGGER_DAYS = 3;

    private MaidMoodManager() {
    }

    public static MaidMoodData get(EntityMaid maid) {
        if (ModTaskData.MOOD_DATA == null) {
            return MaidMoodData.EMPTY;
        }
        MaidMoodData data = maid.getOrCreateData(ModTaskData.MOOD_DATA, MaidMoodData.EMPTY);
        return data == null ? MaidMoodData.EMPTY : data;
    }

    public static MaidMoodData.MoodState state(EntityMaid maid) {
        return get(maid).state();
    }

    public static int value(EntityMaid maid) {
        return get(maid).moodValue();
    }

    public static boolean isLove(EntityMaid maid) {
        return state(maid) == MaidMoodData.MoodState.LOVE;
    }

    public static void addMood(EntityMaid maid, int amount) {
        if (ModTaskData.MOOD_DATA == null || maid.level().isClientSide()) {
            return;
        }
        maid.setAndSyncData(ModTaskData.MOOD_DATA, get(maid).add(amount));
    }

    public static void setMood(EntityMaid maid, int value) {
        if (ModTaskData.MOOD_DATA == null || maid.level().isClientSide()) {
            return;
        }
        maid.setAndSyncData(ModTaskData.MOOD_DATA, get(maid).set(value));
    }

    /**
     * 正向互动的心情收益：
     * - 只在“沮丧 / 一般”时生效；
     * - 每次固定 +1；
     * - 每种互动每天最多生效 2 次。
     */
    public static int applyLimitedInteractionMoodGain(EntityMaid maid, String interactionKey) {
        if (maid == null || interactionKey == null || interactionKey.isBlank() || maid.level().isClientSide()) {
            return 0;
        }
        if (resolvePositiveInteractionFavorabilityGain(maid, 1) > 0) {
            return 0;
        }

        var tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        String dayKey = TAG_INTERACTION_MOOD_DAY_PREFIX + interactionKey;
        String countKey = TAG_INTERACTION_MOOD_COUNT_PREFIX + interactionKey;
        long recordedDay = tag.getLong(dayKey);
        if (recordedDay != currentDay) {
            tag.putLong(dayKey, currentDay);
            tag.putInt(countKey, 0);
        }

        int gainedCountToday = Math.max(0, tag.getInt(countKey));
        if (gainedCountToday >= INTERACTION_MOOD_GAIN_LIMIT_PER_DAY) {
            return 0;
        }

        int before = value(maid);
        addMood(maid, INTERACTION_MOOD_GAIN);
        int applied = value(maid) - before;
        if (applied > 0) {
            tag.putInt(countKey, gainedCountToday + 1);
        }
        return applied;
    }

    /**
     * 确保女仆每天只会随机一次“今日心情”。
     *
     * <p>这里先走轻量版本：
     * - 以世界 dayTime / 24000 作为“今天”的标记；
     * - 每到新的一天重新掷一次基础心情；
     * - 不在客户端执行，避免本地预测与服务端状态打架。
     *
     * <p>后续如果我们要加入“昨晚是否被安慰”“最近是否被冷落”“天气对心情权重的修正”，
     * 只需要继续在这里扩展，而不需要改动 UI 和剧情系统。
     */
    public static void ensureDailyMood(EntityMaid maid) {
        if (ModTaskData.MOOD_DATA == null || maid.level().isClientSide()) {
            return;
        }

        MaidMoodData current = get(maid);
        long today = maid.level().getDayTime() / 24000L;
        if (current.moodDay() == today) {
            return;
        }

        int rolledMood = applyLongingMoodPenalty(maid, current, today, rollDailyMoodValue(maid));
        maid.setAndSyncData(ModTaskData.MOOD_DATA, current.rerollForDay(rolledMood, today));
    }

    /**
     * 记录一次“真正陪她互动过”的日期。
     *
     * <p>只打开 UI 不会调用这里；聊天结果、摸头、拥抱、亲吻、送礼、同眠等有反馈的行为才会刷新。
     * 这样“忍耐”表达的是恋人/妻子最近被冷落，而不是玩家只是路过或点开面板看了一眼。</p>
     */
    public static void markMeaningfulInteraction(EntityMaid maid) {
        if (ModTaskData.MOOD_DATA == null || maid == null || maid.level().isClientSide()) {
            return;
        }
        long today = maid.level().getDayTime() / 24000L;
        MaidMoodData current = get(maid);
        if (current.lastInteractionDay() == today) {
            return;
        }
        maid.setAndSyncData(ModTaskData.MOOD_DATA, current.markInteraction(today));
    }

    public static boolean isLongingForInteraction(EntityMaid maid) {
        return longingDaysSinceInteraction(maid) >= LONGING_TRIGGER_DAYS;
    }

    public static long longingDaysSinceInteraction(EntityMaid maid) {
        if (maid == null || !isDatingOrMarried(maid)) {
            return 0L;
        }
        long lastInteractionDay = get(maid).lastInteractionDay();
        if (lastInteractionDay == MaidMoodData.UNSET_DAY) {
            return 0L;
        }
        long today = maid.level().getDayTime() / 24000L;
        return Math.max(0L, today - lastInteractionDay);
    }

    public static boolean isDatingOrMarried(EntityMaid maid) {
        RelationStage stage = MaidRelationshipManager.resolveStage(maid);
        return stage == RelationStage.DATING || stage == RelationStage.MARRIAGE;
    }

    /**
     * 根据当前心情，把“聊天/互动给出的基础结果”转换成真正的好感变化。
     *
     * <p>设计目标：
     * - 开心/LOVE：正反馈明显；
     * - 普通：走中性收益；
     * - 一般/沮丧：会吃到负反馈；
     * - 数值全部做硬钳制，避免客户端参数把服务端数据拉爆。
     */
    public static int applyDialogueFavorabilityResult(EntityMaid maid,
                                                      int positiveFavor,
                                                      int neutralFavor,
                                                      int negativeFavor,
                                                      int cap) {
        int safePositive = clampDialogueDelta(positiveFavor);
        int safeNeutral = clampDialogueDelta(neutralFavor);
        int safeNegative = clampDialogueDelta(negativeFavor);

        int resolvedDelta = switch (state(maid)) {
            case HAPPY, LOVE -> safePositive;
            case NORMAL -> safeNeutral;
            case GENERAL, DEPRESSED -> safeNegative;
        };
        return applyDailyLimitedInteractionFavorabilityDelta(maid, resolvedDelta, cap);
    }

    public static void applyDialogueMoodResult(EntityMaid maid,
                                               int positiveMoodDelta,
                                               int neutralMoodDelta,
                                               int negativeMoodDelta) {
        int safePositive = clampDialogueDelta(positiveMoodDelta);
        int safeNeutral = clampDialogueDelta(neutralMoodDelta);
        int safeNegative = clampDialogueDelta(negativeMoodDelta);
        int resolvedDelta = switch (state(maid)) {
            case HAPPY, LOVE -> safePositive;
            case NORMAL -> safeNeutral;
            case GENERAL, DEPRESSED -> safeNegative;
        };
        applyDialogueMoodDelta(maid, resolvedDelta);
    }

    public static void applyDialogueMoodDelta(EntityMaid maid, int moodDelta) {
        addMood(maid, Mth.clamp(moodDelta, -MaidMoodData.MOOD_STEP * 2, MaidMoodData.MOOD_STEP * 2));
    }

    public static String dialogueMoodKey(EntityMaid maid) {
        return state(maid).key();
    }

    /**
     * 按当前心情结算好感增量。
     *
     * <p>沮丧/一般会降低收益，普通保持原值，开心/LOVE 会提高收益。
     * 最小正收益保底为 1，避免小额互动在负面心情下完全没有反馈。</p>
     */
    public static int applyFavorabilityGain(EntityMaid maid, int baseGain, int cap) {
        if (baseGain <= 0) {
            return 0;
        }
        int actualGain = resolvePositiveInteractionFavorabilityGain(maid, baseGain);
        if (actualGain <= 0) {
            return 0;
        }
        return applyFavorabilityDeltaWithRefresh(maid, actualGain, cap);
    }

    public static int applyInteractionFavorabilityGain(EntityMaid maid, int baseGain, int cap) {
        if (baseGain <= 0) {
            return 0;
        }
        int actualGain = resolvePositiveInteractionFavorabilityGain(maid, baseGain);
        if (actualGain <= 0) {
            return 0;
        }
        return applyDailyLimitedInteractionFavorabilityDelta(maid, actualGain, cap);
    }

    /**
     * 直接互动的好感收益规则：
     * - 沮丧 / 一般：不加好感；
     * - 普通：x1；
     * - 开心：x1.5；
     * - LOVE：x2。
     */
    public static int resolvePositiveInteractionFavorabilityGain(EntityMaid maid, int baseGain) {
        if (maid == null || baseGain <= 0) {
            return 0;
        }
        double multiplier = state(maid).favorabilityMultiplier();
        if (multiplier <= 0.0D) {
            return 0;
        }
        return Math.max(1, (int) Math.floor(baseGain * multiplier));
    }

    public static int applyDailyLimitedInteractionFavorabilityDelta(EntityMaid maid, int delta, int cap) {
        if (delta <= 0) {
            return applyFavorabilityDeltaWithRefresh(maid, delta, cap);
        }
        var tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        long recordedDay = tag.getLong(TAG_INTERACTION_FAVOR_DAY);
        if (recordedDay != currentDay) {
            tag.putLong(TAG_INTERACTION_FAVOR_DAY, currentDay);
            tag.putInt(TAG_INTERACTION_FAVOR_GAIN_TODAY, 0);
        }

        int gainedToday = Math.max(0, tag.getInt(TAG_INTERACTION_FAVOR_GAIN_TODAY));
        int remainingToday = Math.max(0, INTERACTION_DAILY_FAVOR_LIMIT - gainedToday);
        if (remainingToday <= 0) {
            return 0;
        }

        int appliedDelta = applyFavorabilityDeltaWithRefresh(maid, Math.min(delta, remainingToday), cap);
        if (appliedDelta > 0) {
            tag.putInt(TAG_INTERACTION_FAVOR_GAIN_TODAY, gainedToday + appliedDelta);
        }
        return appliedDelta;
    }

    /**
     * 直接按“最终增量”修改好感，并复用原版好感管理器的等级切换逻辑。
     *
     * <p>之前模组里很多地方都是直接调用 {@code maid.setFavorability(...)}，
     * 这会导致数值虽然变了，但 TLM 原版在“跨等级时刷新攻击/血量上限”的逻辑没有执行。
     * 典型表现就是：
     * 1. 用花、摸头等方式把好感从一级推到二级；
     * 2. 面板上的好感数字升级了；
     * 3. 但最大生命值仍停留在旧阶段；
     * 4. 直到后续某次走了原版 FavorabilityManager.add/reduce 流程，属性才一次性补刷新。
     *
     * <p>这里统一改成通过原版 {@code FavorabilityManager} 加减好感，
     * 让等级变化、属性刷新、相关事件与触发器都走同一条稳定路径。
     *
     * @param maid 女仆实体
     * @param delta 期望增量，可正可负
     * @param cap 好感度上限，通常为 384（即原版三级上限）
     * @return 实际应用的增量；正数表示增加，负数表示减少
     */
    public static int applyFavorabilityDeltaWithRefresh(EntityMaid maid, int delta, int cap) {
        if (delta == 0) {
            return 0;
        }
        int currentFavorability = maid.getFavorability();
        int targetFavorability = Mth.clamp(currentFavorability + delta, 0, cap);
        return setFavorabilityWithRefresh(maid, targetFavorability, cap);
    }

    /**
     * 将好感度设置到指定值，同时确保跨阶段时立即刷新原版属性加成。
     *
     * <p>注意这里不是裸写 {@code setFavorability}，而是根据目标值与当前值的差，
     * 分别委托给原版的 {@code add(...)} / {@code reduceWithoutLevel(...)}。
     * 这样可以让 TLM 原版已经实现好的：
     * - 等级切换时的最大生命值刷新
     * - 攻击力刷新
     * - 相关事件、粒子与部分进度触发
     * 全部继续生效，避免我们自己维护一套易漏逻辑。
     *
     * @param maid 女仆实体
     * @param targetFavorability 目标好感度
     * @param cap 好感度上限
     * @return 实际变化量；正数表示提高，负数表示降低
     */
    public static int setFavorabilityWithRefresh(EntityMaid maid, int targetFavorability, int cap) {
        int currentFavorability = maid.getFavorability();
        int clampedTargetFavorability = Mth.clamp(targetFavorability, 0, cap);
        if (clampedTargetFavorability == currentFavorability) {
            return 0;
        }

        if (clampedTargetFavorability > currentFavorability) {
            maid.getFavorabilityManager().add(clampedTargetFavorability - currentFavorability);
        } else {
            maid.getFavorabilityManager().reduceWithoutLevel(currentFavorability - clampedTargetFavorability);
        }
        int actualDelta = maid.getFavorability() - currentFavorability;
        notifyFavorabilityChanged(maid, actualDelta);
        maybeGrantRelationStageAdvancements(maid);
        return actualDelta;
    }

    private static void notifyFavorabilityChanged(EntityMaid maid, int actualDelta) {
        if (actualDelta == 0 || maid.level().isClientSide()) {
            return;
        }
        ModNetworking.sendFavorabilityEffect(
                maid,
                new FavorabilityEffectPayload(maid.getUUID(), actualDelta)
        );
    }

    private static void maybeGrantRelationStageAdvancements(EntityMaid maid) {
        if (maid.level().isClientSide() || !(maid.getOwner() instanceof ServerPlayer owner)) {
            return;
        }

        int favorability = maid.getFavorability();
        if (favorability >= RelationshipThresholds.PET_UNLOCK) {
            ModAdvancements.grantWarmStage(owner);
        }
        if (favorability >= RelationshipThresholds.HUG_UNLOCK) {
            ModAdvancements.grantCloseStage(owner);
        }
    }

    private static int rollDailyMoodValue(EntityMaid maid) {
        int roll = maid.getRandom().nextInt(100);
        if (roll < 12) {
            return MaidMoodData.MoodState.DEPRESSED.value();
        }
        if (roll < 30) {
            return MaidMoodData.MoodState.GENERAL.value();
        }
        if (roll < 65) {
            return MaidMoodData.MoodState.NORMAL.value();
        }
        if (roll < 86) {
            return MaidMoodData.MoodState.HAPPY.value();
        }
        return MaidMoodData.MoodState.LOVE.value();
    }

    private static int applyLongingMoodPenalty(EntityMaid maid, MaidMoodData current, long today, int rolledMood) {
        if (!isDatingOrMarried(maid) || current.lastInteractionDay() == MaidMoodData.UNSET_DAY) {
            return rolledMood;
        }
        long missingDays = Math.max(0L, today - current.lastInteractionDay());
        if (missingDays < LONGING_TRIGGER_DAYS) {
            return rolledMood;
        }
        int penaltySteps = (int) Math.min(2L, 1L + (missingDays - LONGING_TRIGGER_DAYS) / 2L);
        return Math.max(MaidMoodData.MoodState.GENERAL.value(), rolledMood - penaltySteps * MaidMoodData.MOOD_STEP);
    }

    private static int clampDialogueDelta(int value) {
        return Mth.clamp(value, -MaidMoodData.MOOD_STEP * 2, MaidMoodData.MOOD_STEP * 2);
    }
}
