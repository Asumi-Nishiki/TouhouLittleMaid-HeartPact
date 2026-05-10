package com.example.maidmarriage.client;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

/**
 * 本地亲密交互会话的轻量客户端缓存。
 *
 * <p>这里现在也和服务端一样拆成两层状态：
 * 1. localInteractionMaidUuid：当前是否正在和某只女仆进行站立锁定交互；
 * 2. localHugging：这份交互会话当前是否已经切到拥抱姿态。
 *
 * <p>这样客户端各模块就能各取所需：
 * - UI 面板看“是否在交互中”；
 * - 拥抱相机与拥抱姿态看“是否在拥抱中”；
 * - 目标解析优先取“交互目标”，避免准星偏掉时点到错误女仆。
 */
public final class HugClientState {
    public enum HeadCueType {
        NONE,
        LOWER_HEAD,
        RAISE_HEAD
    }

    @Nullable
    private static UUID localPlayerUuid;
    @Nullable
    private static UUID localInteractionMaidUuid;
    private static boolean localHugging;
    private static boolean localChildNameRequired;
    @Nullable
    private static UUID shyTurnMaidUuid;
    private static long clientTick;
    private static long shyTurnStartTick;
    private static long shyTurnEndTick;
    private static long shyTurnEnterTicks;
    private static long shyTurnReturnTicks;
    private static float shyTurnYawDegrees;
    private static float shyTurnPitchDegrees;
    private static int shyTurnDirectionSign;
    @Nullable
    private static UUID headCueMaidUuid;
    private static HeadCueType headCueType = HeadCueType.NONE;
    private static HeadCueType headCueFromType = HeadCueType.NONE;
    private static boolean headCueReturnToNeutral;
    private static long headCueStartTick;
    private static long headCueEndTick;
    private static float headCueFromPoseAlpha;
    private static float headCueFromYawDegrees;
    private static float headCueFromPitchDegrees;
    private static float headCueFromRollDegrees;
    private static float headCueYawDegrees;
    private static float headCuePitchDegrees;
    private static float headCueRollDegrees;
    @Nullable
    private static UUID shyCoverFaceMaidUuid;
    private static long shyCoverFaceStartTick;
    private static long shyCoverFaceEndTick;
    @Nullable
    private static UUID shyPeekMaidUuid;
    private static long shyPeekStartTick;
    private static long shyPeekEndTick;
    @Nullable
    private static UUID chestTapMaidUuid;
    private static long chestTapStartTick;
    private static long chestTapEndTick;

    private HugClientState() {
    }

    /**
     * 处理服务端同步过来的本地交互会话状态。
     */
    public static void handleSync(UUID playerUuid, @Nullable UUID maidUuid, boolean hugging) {
        handleSync(playerUuid, maidUuid, hugging, false);
    }

    /**
     * 处理服务端同步过来的本地交互会话状态。
     *
     * <p>childNameRequired 是服务端权威判断，用来处理“妈妈抱着未正式命名的新生儿”这种
     * 客户端实体/乘客/TaskData 可能尚未同步完整的入口剧情。
     */
    public static void handleSync(UUID playerUuid, @Nullable UUID maidUuid, boolean hugging, boolean childNameRequired) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !playerUuid.equals(minecraft.player.getUUID())) {
            return;
        }
        localPlayerUuid = playerUuid;
        localInteractionMaidUuid = maidUuid;
        localHugging = maidUuid != null && hugging;
        localChildNameRequired = maidUuid != null && childNameRequired;
    }

    public static void tick(Minecraft minecraft) {
        clientTick++;
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }

        localPlayerUuid = minecraft.player.getUUID();

        /*
         * 客户端如果已经收到了交互会话同步，但目标女仆实体在当前世界里不存在了，
         * 就把本地缓存清掉，避免 UI 或动作还拿着一份已经失效的 UUID。
         */
        if (localInteractionMaidUuid != null && !hasEntityWithUuid(minecraft, localInteractionMaidUuid)) {
            localInteractionMaidUuid = null;
            localHugging = false;
            localChildNameRequired = false;
        }
        if (shyTurnMaidUuid != null && clientTick > shyTurnEndTick) {
            clearShyTurn();
        }
        if (shyCoverFaceMaidUuid != null && clientTick > shyCoverFaceEndTick) {
            clearShyCoverFace();
        }
        if (shyPeekMaidUuid != null && clientTick > shyPeekEndTick) {
            clearShyPeek();
        }
        if (chestTapMaidUuid != null && clientTick > chestTapEndTick) {
            clearChestTap();
        }
    }

    public static void clear() {
        localPlayerUuid = null;
        localInteractionMaidUuid = null;
        localHugging = false;
        localChildNameRequired = false;
        clearShyTurn();
        clearHeadCue();
        clearShyCoverFace();
        clearShyPeek();
        clearChestTap();
    }

    public static boolean isLocalPlayerInteracting() {
        return localPlayerUuid != null && localInteractionMaidUuid != null;
    }

    public static boolean isLocalPlayerHugging() {
        return isLocalPlayerInteracting() && localHugging;
    }

    public static boolean isLocalChildNameRequired() {
        return isLocalPlayerInteracting() && localChildNameRequired;
    }

    @Nullable
    public static UUID getLocalInteractionMaidUuid() {
        return localInteractionMaidUuid;
    }

    /**
     * 保留旧接口给仍然只关心“真正拥抱目标”的旧调用点。
     */
    @Nullable
    public static UUID getLocalHugMaidUuid() {
        return isLocalPlayerHugging() ? localInteractionMaidUuid : null;
    }

    /**
     * 启动“亲吻后的害羞别头”客户端动画。
     *
     * <p>这是纯客户端渲染态，不会修改实体真实朝向，
     * 只给本地玩家看到一个稳定的头部收尾动作，
     * 从根源上避免服务端活体朝向系统把身体一起带偏。
     */
    public static void startPostKissShyTurn(UUID maidUuid, int delayTicks, int durationTicks,
                                            float yawDegrees, float pitchDegrees, int directionSign) {
        startPostKissShyTurn(maidUuid, delayTicks, durationTicks, yawDegrees, pitchDegrees, directionSign, -1, -1);
    }

    /**
     * 启动一个可配置节奏的别头动作。
     *
     * <p>普通亲吻后的害羞别头和剧情里的“慢慢把视线移开”不是同一种拍子：
     * - 前者可以更突然一点；
     * - 后者需要更慢、更犹豫。
     *
     * <p>因此这里补一个可配置 enter/return 时长的入口。
     * 旧调用不传时继续走默认节奏，剧情动作可以单独放慢。
     */
    public static void startPostKissShyTurn(UUID maidUuid, int delayTicks, int durationTicks,
                                            float yawDegrees, float pitchDegrees, int directionSign,
                                            int enterTicks, int returnTicks) {
        shyTurnMaidUuid = maidUuid;
        shyTurnStartTick = clientTick + Math.max(0, delayTicks);
        shyTurnEndTick = shyTurnStartTick + Math.max(1, durationTicks);
        shyTurnEnterTicks = Math.max(-1, enterTicks);
        shyTurnReturnTicks = Math.max(-1, returnTicks);
        shyTurnYawDegrees = Math.abs(yawDegrees);
        shyTurnPitchDegrees = Math.max(0.0F, pitchDegrees);
        shyTurnDirectionSign = directionSign >= 0 ? 1 : -1;
    }

    public static boolean isPostKissShyTurnActive(@Nullable UUID maidUuid) {
        return maidUuid != null
                && maidUuid.equals(shyTurnMaidUuid)
                && clientTick >= shyTurnStartTick
                && clientTick <= shyTurnEndTick;
    }

    public static float currentPostKissShyYawDegrees(@Nullable UUID maidUuid) {
        if (!isPostKissShyTurnActive(maidUuid)) {
            return 0.0F;
        }
        return shyTurnYawDegrees * shyTurnDirectionSign * easedShyTurnProgress();
    }

    public static float currentPostKissShyPitchDegrees(@Nullable UUID maidUuid) {
        if (!isPostKissShyTurnActive(maidUuid)) {
            return 0.0F;
        }
        return shyTurnPitchDegrees * easedShyTurnProgress();
    }

    /**
     * 启动一个通用头部演出 cue。
     *
     * <p>这层和亲吻后的 shy turn 完全独立，专门留给剧情脚本后续复用：
     * 比如“低头不敢看”“慢慢抬头看你”等。
     */
    public static void startHeadCue(UUID maidUuid, HeadCueType cueType, int delayTicks, int durationTicks,
                                    float yawDegrees, float pitchDegrees, float rollDegrees) {
        startHeadCue(maidUuid, cueType, delayTicks, durationTicks, yawDegrees, pitchDegrees, rollDegrees, false);
    }

    public static void startHeadCue(UUID maidUuid, HeadCueType cueType, int delayTicks, int durationTicks,
                                    float yawDegrees, float pitchDegrees, float rollDegrees,
                                    boolean returnToNeutral) {
        if (maidUuid == null || cueType == null || cueType == HeadCueType.NONE) {
            clearHeadCue();
            return;
        }
        if (maidUuid.equals(headCueMaidUuid) && headCueType != HeadCueType.NONE) {
            headCueFromType = headCueType;
            headCueFromPoseAlpha = currentHeadCuePoseAlpha(maidUuid);
            headCueFromYawDegrees = currentHeadCueYawDegrees(maidUuid);
            headCueFromPitchDegrees = currentHeadCuePitchDegrees(maidUuid);
            headCueFromRollDegrees = currentHeadCueRollDegrees(maidUuid);
        } else {
            headCueFromType = HeadCueType.NONE;
            headCueFromPoseAlpha = 0.0F;
            headCueFromYawDegrees = 0.0F;
            headCueFromPitchDegrees = 0.0F;
            headCueFromRollDegrees = 0.0F;
        }
        headCueReturnToNeutral = returnToNeutral;
        headCueMaidUuid = maidUuid;
        headCueType = cueType;
        headCueStartTick = clientTick + Math.max(0, delayTicks);
        headCueEndTick = headCueStartTick + Math.max(1, durationTicks);
        headCueYawDegrees = headCueReturnToNeutral ? 0.0F : yawDegrees;
        headCuePitchDegrees = headCueReturnToNeutral ? 0.0F : pitchDegrees;
        headCueRollDegrees = headCueReturnToNeutral ? 0.0F : rollDegrees;
    }

    public static void startLowerHead(UUID maidUuid, int delayTicks, int durationTicks, float pitchDegrees) {
        startHeadCue(maidUuid, HeadCueType.LOWER_HEAD, delayTicks, durationTicks, 0.0F, Math.abs(pitchDegrees), 0.0F);
    }

    public static void startRaiseHead(UUID maidUuid, int delayTicks, int durationTicks, float pitchDegrees) {
        startHeadCue(maidUuid, HeadCueType.RAISE_HEAD, delayTicks, durationTicks, 0.0F, -Math.abs(pitchDegrees), 0.0F);
    }

    public static void returnHeadToNeutral(UUID maidUuid, int delayTicks, int durationTicks) {
        startHeadCue(maidUuid, HeadCueType.RAISE_HEAD, delayTicks, durationTicks, 0.0F, 0.0F, 0.0F, true);
    }

    public static void clearActiveHeadCue() {
        clearHeadCue();
    }

    /**
     * 害羞捂脸是一个完整播完就结束的短动作：
     * 起手抬手，短暂停住，最后再慢慢放下。
     *
     * <p>它和低头/抬头不同，不需要在结尾保持姿态，因此仍然保留自动结束。
     */
    public static void startShyCoverFace(UUID maidUuid, int delayTicks, int durationTicks) {
        if (maidUuid == null) {
            clearShyCoverFace();
            return;
        }
        shyCoverFaceMaidUuid = maidUuid;
        shyCoverFaceStartTick = clientTick + Math.max(0, delayTicks);
        shyCoverFaceEndTick = shyCoverFaceStartTick + Math.max(1, durationTicks);
    }

    public static void clearActiveShyCoverFace() {
        clearShyCoverFace();
    }

    /**
     * 轻轻拍头后的“害羞抬眼”不是锁姿态，而是一个完整的小拍子：
     * 先稍微抬头看看你，再因为害羞慢慢缩回去。
     *
     * <p>它和普通的 head_raise 不同，不应该在结尾停在抬头状态。
     */
    public static void startShyPeek(UUID maidUuid, int delayTicks, int durationTicks) {
        if (maidUuid == null) {
            clearShyPeek();
            return;
        }
        shyPeekMaidUuid = maidUuid;
        shyPeekStartTick = clientTick + Math.max(0, delayTicks);
        shyPeekEndTick = shyPeekStartTick + Math.max(1, durationTicks);
    }

    public static void clearActiveShyPeek() {
        clearShyPeek();
    }

    /**
     * 锤胸口是一个短时完整动作：
     * 先把手提到胸前，再连续敲两下，最后放下。
     */
    public static void startChestTap(UUID maidUuid, int delayTicks, int durationTicks) {
        if (maidUuid == null) {
            clearChestTap();
            return;
        }
        chestTapMaidUuid = maidUuid;
        chestTapStartTick = clientTick + Math.max(0, delayTicks);
        chestTapEndTick = chestTapStartTick + Math.max(1, durationTicks);
    }

    public static boolean isChestTapActive(@Nullable UUID maidUuid) {
        return maidUuid != null
                && maidUuid.equals(chestTapMaidUuid)
                && clientTick >= chestTapStartTick
                && clientTick <= chestTapEndTick;
    }

    public static float currentChestTapPoseProgress(@Nullable UUID maidUuid) {
        if (!isChestTapActive(maidUuid)) {
            return 0.0F;
        }
        return easedChestTapPoseProgress();
    }

    public static float currentChestTapHitStrength(@Nullable UUID maidUuid) {
        if (!isChestTapActive(maidUuid)) {
            return 0.0F;
        }
        return easedChestTapHitStrength();
    }

    public static boolean isShyCoverFaceActive(@Nullable UUID maidUuid) {
        return maidUuid != null
                && maidUuid.equals(shyCoverFaceMaidUuid)
                && clientTick >= shyCoverFaceStartTick
                && clientTick <= shyCoverFaceEndTick;
    }

    public static float currentShyCoverFaceProgress(@Nullable UUID maidUuid) {
        if (!isShyCoverFaceActive(maidUuid)) {
            return 0.0F;
        }
        return easedShyCoverFaceProgress();
    }

    public static boolean isShyPeekActive(@Nullable UUID maidUuid) {
        return maidUuid != null
                && maidUuid.equals(shyPeekMaidUuid)
                && clientTick >= shyPeekStartTick
                && clientTick <= shyPeekEndTick;
    }

    public static float currentShyPeekProgress(@Nullable UUID maidUuid) {
        if (!isShyPeekActive(maidUuid)) {
            return 0.0F;
        }
        return easedShyPeekProgress();
    }

    public static boolean isHeadCueActive(@Nullable UUID maidUuid) {
        return maidUuid != null
                && maidUuid.equals(headCueMaidUuid)
                && headCueType != HeadCueType.NONE
                && clientTick >= headCueStartTick;
    }

    public static float currentHeadCueYawDegrees(@Nullable UUID maidUuid) {
        if (!isHeadCueActive(maidUuid)) {
            return 0.0F;
        }
        return lerp(headCueFromYawDegrees, headCueYawDegrees, easedHeadCueProgress());
    }

    public static float currentHeadCuePitchDegrees(@Nullable UUID maidUuid) {
        if (!isHeadCueActive(maidUuid)) {
            return 0.0F;
        }
        return lerp(headCueFromPitchDegrees, headCuePitchDegrees, easedHeadCueProgress());
    }

    public static float currentHeadCueRollDegrees(@Nullable UUID maidUuid) {
        if (!isHeadCueActive(maidUuid)) {
            return 0.0F;
        }
        return lerp(headCueFromRollDegrees, headCueRollDegrees, easedHeadCueProgress());
    }

    /**
     * 提供给渲染层读取“剧情头部演出当前已经走到哪一步”。
     *
     * <p>之前渲染层只能拿到最终头部角度，因此只能把 cue 理解成“头转了多少度”，
     * 做不出一整套持续姿态，只会像点一下头。
     *
     * <p>把这个进度开放出去之后，渲染层就能同步驱动脖子、上半身和肩线，
     * 让“低着头”“慢慢抬起头”真正成为一套完整姿态，而不是只有头骨在动。
     */
    public static float currentHeadCueProgress(@Nullable UUID maidUuid) {
        if (!isHeadCueActive(maidUuid)) {
            return 0.0F;
        }
        return easedHeadCueProgress();
    }

    @Nullable
    public static HeadCueType currentHeadCueType(@Nullable UUID maidUuid) {
        return isHeadCueActive(maidUuid) ? headCueType : null;
    }

    @Nullable
    public static HeadCueType currentHeadCueFromType(@Nullable UUID maidUuid) {
        return isHeadCueActive(maidUuid) ? headCueFromType : null;
    }

    public static float currentHeadCueFromPoseAlpha(@Nullable UUID maidUuid) {
        return isHeadCueActive(maidUuid) ? headCueFromPoseAlpha : 0.0F;
    }

    public static boolean currentHeadCueReturnsToNeutral(@Nullable UUID maidUuid) {
        return isHeadCueActive(maidUuid) && headCueReturnToNeutral;
    }

    private static float easedShyTurnProgress() {
        /*
         * 亲吻后的收尾现在拆成完整三段：
         * 1. 前几 tick 快速别头；
         * 2. 中间保持住，配合几句害羞台词；
         * 3. 末尾再平滑转回默认状态。
         */
        long totalTicks = Math.max(1L, shyTurnEndTick - shyTurnStartTick);
        long quickTurnTicks = shyTurnEnterTicks > 0
                ? Math.min(totalTicks, shyTurnEnterTicks)
                : Math.max(2L, Math.min(6L, totalTicks / 4L));
        long returnTicks = shyTurnReturnTicks > 0
                ? Math.min(totalTicks, shyTurnReturnTicks)
                : Math.max(6L, Math.min(12L, totalTicks / 3L));
        long holdEndTick = Math.max(shyTurnStartTick + quickTurnTicks, shyTurnEndTick - returnTicks);

        if (clientTick <= shyTurnStartTick + quickTurnTicks) {
            float progress = (float) (clientTick - shyTurnStartTick) / Math.max(1L, quickTurnTicks);
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            return 1.0F - (1.0F - progress) * (1.0F - progress);
        }
        if (clientTick < holdEndTick) {
            return 1.0F;
        }

        float returnProgress = (float) (clientTick - holdEndTick) / Math.max(1L, shyTurnEndTick - holdEndTick);
        returnProgress = Math.max(0.0F, Math.min(1.0F, returnProgress));
        float easedReturn = returnProgress * returnProgress * (3.0F - 2.0F * returnProgress);
        return 1.0F - easedReturn;
    }

    private static float easedHeadCueProgress() {
        if (clientTick <= headCueStartTick) {
            return 0.0F;
        }
        long totalTicks = Math.max(1L, headCueEndTick - headCueStartTick);
        if (clientTick >= headCueEndTick) {
            return 1.0F;
        }
        float normalized = (float) (clientTick - headCueStartTick) / (float) totalTicks;
        normalized = Math.max(0.0F, Math.min(1.0F, normalized));
        return smoothStep(normalized);
    }

    private static float smoothStep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float easedShyCoverFaceProgress() {
        long totalTicks = Math.max(1L, shyCoverFaceEndTick - shyCoverFaceStartTick);
        float normalized = (float) (clientTick - shyCoverFaceStartTick) / (float) totalTicks;
        normalized = Math.max(0.0F, Math.min(1.0F, normalized));

        final float enterEnd = 0.28F;
        final float holdEnd = 0.72F;
        if (normalized < enterEnd) {
            return smoothStep(normalized / enterEnd);
        }
        if (normalized < holdEnd) {
            return 1.0F;
        }
        float local = (normalized - holdEnd) / Math.max(0.0001F, 1.0F - holdEnd);
        return 1.0F - smoothStep(local);
    }

    private static float easedChestTapPoseProgress() {
        long totalTicks = Math.max(1L, chestTapEndTick - chestTapStartTick);
        float normalized = (float) (clientTick - chestTapStartTick) / (float) totalTicks;
        normalized = Math.max(0.0F, Math.min(1.0F, normalized));

        final float enterEnd = 0.22F;
        final float holdEnd = 0.82F;
        if (normalized < enterEnd) {
            return smoothStep(normalized / enterEnd);
        }
        if (normalized < holdEnd) {
            return 1.0F;
        }
        float local = (normalized - holdEnd) / Math.max(0.0001F, 1.0F - holdEnd);
        return 1.0F - smoothStep(local);
    }

    private static float easedChestTapHitStrength() {
        long totalTicks = Math.max(1L, chestTapEndTick - chestTapStartTick);
        float normalized = (float) (clientTick - chestTapStartTick) / (float) totalTicks;
        normalized = Math.max(0.0F, Math.min(1.0F, normalized));

        // 前段抬手，中段连续两下，后段收手。
        final float hitStart = 0.24F;
        final float hitEnd = 0.72F;
        if (normalized <= hitStart || normalized >= hitEnd) {
            return 0.0F;
        }
        float local = (normalized - hitStart) / (hitEnd - hitStart);
        // 两次敲击，取正半波。
        return Math.max(0.0F, (float) Math.sin(local * Math.PI * 4.0F));
    }

    private static float easedShyPeekProgress() {
        long totalTicks = Math.max(1L, shyPeekEndTick - shyPeekStartTick);
        float normalized = (float) (clientTick - shyPeekStartTick) / (float) totalTicks;
        normalized = Math.max(0.0F, Math.min(1.0F, normalized));

        final float enterEnd = 0.34F;
        final float holdEnd = 0.56F;
        if (normalized < enterEnd) {
            return smoothStep(normalized / enterEnd);
        }
        if (normalized < holdEnd) {
            return 1.0F;
        }
        float local = (normalized - holdEnd) / Math.max(0.0001F, 1.0F - holdEnd);
        return 1.0F - smoothStep(local);
    }

    private static void clearShyTurn() {
        shyTurnMaidUuid = null;
        shyTurnStartTick = 0L;
        shyTurnEndTick = 0L;
        shyTurnEnterTicks = 0L;
        shyTurnReturnTicks = 0L;
        shyTurnYawDegrees = 0.0F;
        shyTurnPitchDegrees = 0.0F;
        shyTurnDirectionSign = 1;
    }

    private static void clearHeadCue() {
        headCueMaidUuid = null;
        headCueType = HeadCueType.NONE;
        headCueFromType = HeadCueType.NONE;
        headCueReturnToNeutral = false;
        headCueStartTick = 0L;
        headCueEndTick = 0L;
        headCueFromPoseAlpha = 0.0F;
        headCueFromYawDegrees = 0.0F;
        headCueFromPitchDegrees = 0.0F;
        headCueFromRollDegrees = 0.0F;
        headCueYawDegrees = 0.0F;
        headCuePitchDegrees = 0.0F;
        headCueRollDegrees = 0.0F;
    }

    private static float currentHeadCuePoseAlpha(@Nullable UUID maidUuid) {
        if (!isHeadCueActive(maidUuid)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, easedHeadCueProgress()));
    }

    private static void clearShyCoverFace() {
        shyCoverFaceMaidUuid = null;
        shyCoverFaceStartTick = 0L;
        shyCoverFaceEndTick = 0L;
    }

    private static void clearShyPeek() {
        shyPeekMaidUuid = null;
        shyPeekStartTick = 0L;
        shyPeekEndTick = 0L;
    }

    private static void clearChestTap() {
        chestTapMaidUuid = null;
        chestTapStartTick = 0L;
        chestTapEndTick = 0L;
    }

    /**
     * 客户端世界层没有像服务端那样稳定暴露 `getEntity(UUID)`，
     * 因此这里用一次轻量遍历来确认“当前同步目标是否还存在于本地世界”。
     */
    private static boolean hasEntityWithUuid(Minecraft minecraft, UUID uuid) {
        return minecraft != null && minecraft.level != null && ClientEntityLookup.findEntity(uuid) != null;
    }

    /**
     * 交互会话存在时自动维持一个透明交互屏。
     *
     * <p>这里现在改成跟“交互会话”走，而不是跟“拥抱姿态”走。
     * 这正是这次重构的核心：先站立锁定进面板，再由面板内部切换是否拥抱。
     */
    public static void ensureActionScreen(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        if (!isLocalPlayerInteracting()) {
            /*
             * 这里一定不能把“小女仆互动页”也一起关掉。
             *
             * 之前小女仆会话打开后，成年拥抱状态这边因为没有 interaction，
             * 每个客户端 tick 都会先 setScreen(null)，随后 ChildInteractionClientState
             * 又把小女仆页重新打开。结果就是鼠标在同一帧附近反复 grab/release，
             * 玩家看到的表现就是鼠标像被锁回准心。
             */
            if (minecraft.screen instanceof HugActionScreen screen && !screen.isChildInteractionScreen()) {
                minecraft.setScreen(null);
            }
            return;
        }
        if (minecraft.screen == null) {
            minecraft.setScreen(new HugActionScreen());
            minecraft.mouseHandler.releaseMouse();
        }
    }
}
