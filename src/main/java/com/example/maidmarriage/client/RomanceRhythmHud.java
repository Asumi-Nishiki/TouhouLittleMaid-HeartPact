package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.SubmitRomanceRhythmPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RomanceRhythmHud {
    private static final int FAIL_STREAK_LIMIT = 8;
    private static final int PEAK_LIMIT = 3;
    private static final float NOTE_SPEED = 228f;
    private static final long STEP_MS = 580L;
    private static final int MAX_SPAWN_BATCH_PER_TICK = 2;
    private static final float PERFECT_EARLY_WINDOW = 48f;
    private static final float PERFECT_LATE_WINDOW = 32f;
    private static final float GOOD_EARLY_WINDOW = 92f;
    private static final float GOOD_LATE_WINDOW = 58f;
    private static final float TYPEWRITER_CPS = 20f;
    private static final float TRACK_WIDTH = 360f;
    private static final float JUDGE_OFFSET = 92f;
    private static final float TRACK_START_X = -24f;
    private static final float NOTE_RENDER_SIZE = 12f;
    private static final float MISS_BUFFER = 12f;
    private static final float EIGHTH_SPACING_FACTOR = 0.82f;
    private static final float SKIP_SCORE = 0.18f;
    private static final float FAIL_SCORE = 0.0f;
    private static final int[] PATTERN_STEPS = {
            1, 0, 1, 0, 2, 0, 1, 0,
            1, 0, 2, 0, 1, 1, 0, 0,
            2, 0, 1, 0, 2, 0, 1, 0,
            1, 1, 0, 1, 2, 0, 0, 0
    };
    private static final String[] POOL_START = {
            "dialogue.maidmarriage.rhythm.start.1",
            "dialogue.maidmarriage.rhythm.start.2",
            "dialogue.maidmarriage.rhythm.start.3"
    };
    private static final String[] POOL_PERFECT = {
            "dialogue.maidmarriage.rhythm.perfect.1",
            "dialogue.maidmarriage.rhythm.perfect.2",
            "dialogue.maidmarriage.rhythm.perfect.3"
    };
    private static final String[] POOL_GOOD = {
            "dialogue.maidmarriage.rhythm.good.1",
            "dialogue.maidmarriage.rhythm.good.2",
            "dialogue.maidmarriage.rhythm.good.3"
    };
    private static final String[] POOL_MISS = {
            "dialogue.maidmarriage.rhythm.miss.1",
            "dialogue.maidmarriage.rhythm.miss.2",
            "dialogue.maidmarriage.rhythm.miss.3"
    };
    private static final String[] POOL_FAIL = {
            "dialogue.maidmarriage.rhythm.fail.1",
            "dialogue.maidmarriage.rhythm.fail.2"
    };
    private static final String[] POOL_PLAYER_PEAK = {
            "dialogue.maidmarriage.rhythm.player_peak.1",
            "dialogue.maidmarriage.rhythm.player_peak.2"
    };
    private static final String[] POOL_MAID_PEAK = {
            "dialogue.maidmarriage.rhythm.maid_peak.1",
            "dialogue.maidmarriage.rhythm.maid_peak.2"
    };

    private static final ResourceLocation PORTRAIT_SOFT_SMILE =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/soft_smile.png");
    private static final ResourceLocation PORTRAIT_HOT_SMILE =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/hot_smile.png");
    private static final ResourceLocation PORTRAIT_KNOWING =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/knowing.png");
    private static final ResourceLocation PORTRAIT_SERIOUS =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/serious.png");
    private static final ResourceLocation PORTRAIT_SHY =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/shy.png");
    private static final ResourceLocation PORTRAIT_WINK =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/wink.png");
    private static final ResourceLocation PORTRAIT_TROUBLED =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/troubled.png");
    private static final ResourceLocation PORTRAIT_ANXIOUS =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/anxious.png");
    private static final ResourceLocation PORTRAIT_TIRED =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/tired.png");
    private static final ResourceLocation PORTRAIT_EMBARRASSED =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/embarrassed_smile.png");
    private static final ResourceLocation PORTRAIT_ACTION_TOUCHED =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/action/action_touched.png");
    private static final ResourceLocation PORTRAIT_ACTION_FLUSTERED =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/action/action_flustered.png");
    private static final ResourceLocation PORTRAIT_ACTION_HELPLESS =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/action/action_helpless.png");
    private static final ResourceLocation PORTRAIT_ACTION_DEJECTED =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/action/action_dejected.png");
    private static final ResourceLocation PORTRAIT_ACTION_DIZZY =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/action/action_dizzy.png");
    private static final ResourceLocation PORTRAIT_ACTION_SMUG =
            new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/action/action_smug.png");

    private static final ResourceLocation[] START_PORTRAITS = {
            PORTRAIT_SOFT_SMILE, PORTRAIT_KNOWING, PORTRAIT_ACTION_TOUCHED
    };
    private static final ResourceLocation[] PERFECT_PORTRAITS = {
            PORTRAIT_HOT_SMILE, PORTRAIT_WINK, PORTRAIT_ACTION_TOUCHED
    };
    private static final ResourceLocation[] GOOD_PORTRAITS = {
            PORTRAIT_SOFT_SMILE, PORTRAIT_SERIOUS, PORTRAIT_KNOWING
    };
    private static final ResourceLocation[] MISS_PORTRAITS = {
            PORTRAIT_TROUBLED, PORTRAIT_ANXIOUS, PORTRAIT_ACTION_HELPLESS
    };
    private static final ResourceLocation[] FAIL_PORTRAITS = {
            PORTRAIT_TIRED, PORTRAIT_ACTION_DEJECTED, PORTRAIT_ACTION_DIZZY
    };
    private static final ResourceLocation[] PLAYER_PEAK_PORTRAITS = {
            PORTRAIT_ACTION_FLUSTERED, PORTRAIT_EMBARRASSED, PORTRAIT_SHY
    };
    private static final ResourceLocation[] MAID_PEAK_PORTRAITS = {
            PORTRAIT_HOT_SMILE, PORTRAIT_ACTION_TOUCHED, PORTRAIT_ACTION_SMUG
    };

    private static boolean active = false;
    private static UUID maidId = null;
    private static boolean sent = false;
    private static boolean lastMouseDown = false;

    private static final List<Note> notes = new ArrayList<>();
    private static int missStreak = 0;
    private static int combo = 0;
    private static String judge = "";
    private static String fullLine = I18n.get("dialogue.maidmarriage.longing_wait");
    private static String shownLine = "";
    private static float typeProgress = 0f;
    private static String cachedMaidName = "";
    private static String cachedMasterName = "";
    private static ResourceLocation currentPortrait = PORTRAIT_SOFT_SMILE;

    private static float player = 10f;
    private static float maid = 15f;
    private static int playerPeak = 0;
    private static int maidPeak = 0;
    private static int perfectHits = 0;
    private static int goodHits = 0;
    private static int misses = 0;
    private static int maxCombo = 0;
    private static int patternIndex = 0;

    private static long lastMs = 0L;
    private static long stepTimer = 0L;

    private RomanceRhythmHud() {
    }

    public static void start(UUID targetMaid) {
        Minecraft mc = Minecraft.getInstance();
        maidId = targetMaid;
        sent = false;
        active = true;
        lastMouseDown = false;
        notes.clear();
        missStreak = 0;
        combo = 0;
        judge = I18n.get("ui.maidmarriage.rhythm.judge.idle");
        player = 10f;
        maid = 15f;
        playerPeak = 0;
        maidPeak = 0;
        perfectHits = 0;
        goodHits = 0;
        misses = 0;
        maxCombo = 0;
        patternIndex = 0;
        lastMs = Util.getMillis();
        stepTimer = 0L;
        cachedMaidName = resolveMaidName(mc, targetMaid);
        cachedMasterName = resolveMasterName(mc);
        setLineFromPool(POOL_START, START_PORTRAITS, cachedMaidName, cachedMasterName);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }

        long now = Util.getMillis();
        long rawDt = Math.max(0L, now - lastMs);
        lastMs = now;
        long dt = Math.min(rawDt, 50L);

        updateTypewriter(dt);

        if (ModConfigs.rhythmAlwaysSkip()) {
            finishAndSend(SKIP_SCORE);
            return;
        }

        /*
         * 先更新音符位置，再读输入。
         * 这样玩家看到音符和判定线重合时，实际判定位置也已经同步到这一帧，手感会稳定很多。
         */
        updateNotes(dt);
        if (!active) {
            return;
        }

        if (consumeHitInput(mc)) {
            hit();
            if (!active) {
                return;
            }
        }
        if (consumeSkipClick(mc)) {
            setLineByKey("ui.maidmarriage.rhythm.skip_used");
            currentPortrait = PORTRAIT_SERIOUS;
            finishAndSend(SKIP_SCORE);
            return;
        }

        player = clamp(player - 0.9f * (dt / 1000f), 0f, 100f);
        maid = clamp(maid - 0.6f * (dt / 1000f), 0f, 100f);
        checkPeakOrEnd();
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int panelW = 480;
        int panelH = 270;
        int x = w / 2 - panelW / 2;
        int y = Math.max(8, h / 2 - panelH / 2);

        g.fill(x, y, x + panelW, y + panelH, 0xEE12101A);
        g.fill(x, y, x + panelW, y + 22, 0xFF000000);
        g.drawString(mc.font,
                I18n.get("ui.maidmarriage.rhythm.current_hit_key", RhythmKeyMappings.boundKeyName(RhythmKeyMappings.RHYTHM_HIT)),
                x + 8,
                y + 7,
                0xFFFFFFFF,
                false);
        int skipW = 52;
        int skipH = 14;
        int skipX = x + panelW - skipW - 8;
        int skipY = y + 4;
        g.fill(skipX, skipY, skipX + skipW, skipY + skipH, 0xCC5A2030);
        g.drawCenteredString(mc.font, I18n.get("ui.maidmarriage.rhythm.skip"), skipX + skipW / 2, skipY + 3, 0xFFFFE6EE);

        String meta = String.format("%d/%d  %d/%d", playerPeak, PEAK_LIMIT, maidPeak, PEAK_LIMIT);
        g.drawString(mc.font, meta, x + 8, y + 28, 0xFFECE5FF, false);

        int barTop = y + 50;
        int barBottom = y + 206;
        drawBar(g, x + 20, barTop, barBottom, player, 0xFF4EA0FF);
        drawBar(g, x + panelW - 34, barTop, barBottom, maid, 0xFFFF69C5);

        int tx = x + 60;
        int ty = y + 88;
        int tw = panelW - 120;
        int th = 54;
        g.fill(tx, ty, tx + tw, ty + th, 0x99262333);
        g.fill(tx, ty + th / 2, tx + tw, ty + th / 2 + 1, 0x66FFFFFF);
        int judgeX = tx + tw - (int) JUDGE_OFFSET;
        g.fill(judgeX, ty + 4, judgeX + 2, ty + th - 4, 0xFFFBD36B);
        for (Note n : notes) {
            int nx = (int) (tx + n.x);
            int ny = ty + th / 2 - 6;
            int col = n.eighth ? 0xFFFFC247 : 0xFFE9DEFF;
            g.fill(nx, ny, nx + 12, ny + 12, col);
        }

        String mini = "x" + combo + "  " + judge;
        int miniW = mc.font.width(mini) + 12;
        g.fill(x + panelW - miniW - 10, y + 50, x + panelW - 10, y + 64, 0xCC1A1A1A);
        g.drawString(mc.font, mini, x + panelW - miniW - 4, y + 53, 0xFFEFE7FF, false);

        int dialogX = tx;
        int dialogY = y + panelH - 94;
        int dialogW = tw;
        int dialogH = 76;
        g.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xEE181722);
        g.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 1, 0xFF5A4D77);
        g.fill(dialogX + 10, dialogY - 10, dialogX + 66, dialogY + 4, 0xFF2B253C);
        g.drawString(mc.font, cachedMaidName, dialogX + 28, dialogY - 8, 0xFFFF8BCF, false);

        g.blit(currentPortrait == null ? PORTRAIT_SOFT_SMILE : currentPortrait,
                dialogX + 8, dialogY + 8, 0, 0, 48, 48, 48, 48);
        g.drawString(mc.font, shownLine, dialogX + 64, dialogY + 28, 0xFFF3E9FF, false);

        g.drawString(mc.font,
                I18n.get("ui.maidmarriage.rhythm.hit_key", RhythmKeyMappings.boundKeyName(RhythmKeyMappings.RHYTHM_HIT)),
                x + 12,
                y + panelH - 14,
                0xFFD3CAE9,
                false);
    }

    @SubscribeEvent
    public static void hideVanillaHudWhenActive(RenderGuiOverlayEvent.Pre event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        var overlayId = event.getOverlay().id();
        if (overlayId.equals(VanillaGuiOverlay.CHAT_PANEL.id())
                || overlayId.equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);
        }
    }

    private static void updateNotes(long dt) {
        stepTimer += dt;
        int spawnedBatches = 0;
        while (patternIndex < PATTERN_STEPS.length && stepTimer >= STEP_MS && spawnedBatches < MAX_SPAWN_BATCH_PER_TICK) {
            stepTimer -= STEP_MS;
            spawnPatternStep();
            spawnedBatches++;
        }
        if (patternIndex < PATTERN_STEPS.length && stepTimer >= STEP_MS) {
            /*
             * 掉帧时不把历史所有拍点一次性补完，避免瞬间刷出一堆音符导致进一步卡顿。
             */
            stepTimer = STEP_MS - 1L;
        }

        float dx = NOTE_SPEED * (dt / 1000f);
        float missX = TRACK_WIDTH + NOTE_RENDER_SIZE + MISS_BUFFER;
        Iterator<Note> it = notes.iterator();
        while (it.hasNext()) {
            Note note = it.next();
            note.x += dx;
            if (note.x > missX) {
                it.remove();
                onMiss();
                if (!active) {
                    return;
                }
            }
        }

        /**
         * 谱面是有限长度：
         * 当最后一个节拍生成完，且场上所有音符都已经判完/漏完时，
         * 立刻结束并发送最终成绩，不再无限循环刷谱。
         */
        if (active && patternIndex >= PATTERN_STEPS.length && notes.isEmpty()) {
            finishAndSend(calcRhythmScore());
        }
    }

    /**
     * 谱面不再完全随机，而是按一个短循环节奏表生成。
     * 这样会比纯随机更像“音乐有节拍”，黄色八分音符也会更明显。
     */
    private static void spawnPatternStep() {
        if (patternIndex >= PATTERN_STEPS.length) {
            return;
        }

        int patternType = PATTERN_STEPS[patternIndex];
        patternIndex++;

        if (patternType == 0) {
            return;
        }

        /*
         * 八分音符对需要明显前后分开，
         * 否则第二颗会挤进第一颗的 good 判定窗口里，体感上就像“一按吃掉两个”。
         */
        float pairSpacing = NOTE_SPEED * (STEP_MS / 1000f) * EIGHTH_SPACING_FACTOR;
        if (patternType == 1) {
            notes.add(new Note(TRACK_START_X, false));
            return;
        }

        notes.add(new Note(TRACK_START_X, true));
        notes.add(new Note(TRACK_START_X - pairSpacing, true));
    }

    private static void hit() {
        if (notes.isEmpty()) {
            onMiss();
            return;
        }
        float judgeX = judgeTrackX();
        Note nearest = null;
        float distMin = Float.MAX_VALUE;
        for (Note note : notes) {
            float distance = Math.abs(note.x - judgeX);
            if (distance < distMin) {
                distMin = distance;
                nearest = note;
            }
        }
        if (nearest == null) {
            onMiss();
            return;
        }
        float offset = nearest.x - judgeX;
        float perfectWindow = offset <= 0f ? PERFECT_EARLY_WINDOW : PERFECT_LATE_WINDOW;
        float goodWindow = offset <= 0f ? GOOD_EARLY_WINDOW : GOOD_LATE_WINDOW;
        if (distMin <= perfectWindow) {
            notes.remove(nearest);
            onPerfect();
        } else if (distMin <= goodWindow) {
            notes.remove(nearest);
            onGood();
        } else {
            onMiss();
        }
        checkPeakOrEnd();
    }

    private static void onPerfect() {
        perfectHits++;
        player = clamp(player + 9f + combo * 0.14f, 0f, 100f);
        maid = clamp(maid + (12f + combo * 0.17f) * maidMul(combo), 0f, 100f);
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        missStreak = 0;
        judge = I18n.get("ui.maidmarriage.rhythm.judge.perfect");
        setLineFromPool(POOL_PERFECT, PERFECT_PORTRAITS, cachedMaidName, cachedMasterName);
    }

    private static void onGood() {
        goodHits++;
        player = clamp(player + 4.5f + combo * 0.09f, 0f, 100f);
        maid = clamp(maid + (6.5f + combo * 0.10f) * maidMul(combo), 0f, 100f);
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        missStreak = 0;
        judge = I18n.get("ui.maidmarriage.rhythm.judge.good");
        setLineFromPool(POOL_GOOD, GOOD_PORTRAITS, cachedMaidName, cachedMasterName);
    }

    private static void onMiss() {
        float missMul = 1f + Math.min(missStreak * 0.08f, 0.64f);
        player = clamp(player + 4.2f * missMul, 0f, 100f);
        combo = 0;
        missStreak++;
        misses++;
        judge = I18n.get("ui.maidmarriage.rhythm.judge.miss");
        if (missStreak >= FAIL_STREAK_LIMIT) {
            player = 100f;
            playerPeak = Math.min(PEAK_LIMIT, playerPeak + 1);
            setLineFromPool(POOL_FAIL, FAIL_PORTRAITS, cachedMaidName, cachedMasterName);
            finishAndSend(FAIL_SCORE);
            return;
        }
        setLineFromPool(POOL_MISS, MISS_PORTRAITS, cachedMaidName, cachedMasterName);
    }

    private static void checkPeakOrEnd() {
        if (!active) {
            return;
        }
        if (player >= 100f) {
            playerPeak++;
            player = 25f;
            combo = 0;
            setLineFromPool(POOL_PLAYER_PEAK, PLAYER_PEAK_PORTRAITS, cachedMaidName, cachedMasterName);
        }
        if (maid >= 100f) {
            maidPeak++;
            maid = 25f;
            combo = 0;
            setLineFromPool(POOL_MAID_PEAK, MAID_PEAK_PORTRAITS, cachedMaidName, cachedMasterName);
        }
        if (playerPeak >= PEAK_LIMIT || maidPeak >= PEAK_LIMIT) {
            finishAndSend(calcRhythmScore());
        }
    }

    /**
     * 成绩只产出一个 0~1 分数，再交给服务端映射到最高 60% 怀孕率。
     * 这样客户端和服务端职责清晰，也方便后续继续调手感。
     */
    private static float calcRhythmScore() {
        if (missStreak >= FAIL_STREAK_LIMIT) {
            return FAIL_SCORE;
        }
        int totalJudged = perfectHits + goodHits + misses;
        if (totalJudged <= 0) {
            return FAIL_SCORE;
        }

        float weightedAccuracy = (perfectHits * 1.0f + goodHits * 0.72f) / totalJudged;
        float comboFactor = clamp(maxCombo / Math.max(14f, totalJudged * 0.55f), 0f, 1f);
        float peakFactor = clamp((playerPeak + maidPeak) / 6f, 0f, 1f);
        float missPenalty = clamp(misses / Math.max(6f, totalJudged * 0.45f), 0f, 1f);

        float score = weightedAccuracy * 0.62f
                + comboFactor * 0.23f
                + peakFactor * 0.15f
                - missPenalty * 0.18f;
        return clamp(score, 0f, 1f);
    }

    private static void finishAndSend(float rhythmScore) {
        if (!active || sent || maidId == null) {
            active = false;
            return;
        }
        sent = true;
        ModNetworking.sendSubmit(new SubmitRomanceRhythmPayload(maidId, clamp(rhythmScore, 0f, 1f)));
        notes.clear();
        maidId = null;
        stepTimer = 0L;
        active = false;
    }

    private static float maidMul(int comboCount) {
        if (comboCount >= 28) return 1.95f;
        if (comboCount >= 18) return 1.65f;
        if (comboCount >= 10) return 1.38f;
        if (comboCount >= 5) return 1.16f;
        return 1.0f;
    }

    private static void drawBar(GuiGraphics graphics, int x, int top, int bottom, float value, int color) {
        graphics.fill(x, top, x + 14, bottom, 0x66000000);
        int h = Math.max(0, Math.min(bottom - top, Math.round((bottom - top) * value / 100f)));
        graphics.fill(x, bottom - h, x + 14, bottom, color);
    }

    private static void setLine(String newLine) {
        fullLine = newLine == null ? "" : newLine;
        shownLine = "";
        typeProgress = 0f;
    }

    private static void setLineByKey(String key, Object... args) {
        setLine(I18n.get(key, args));
    }

    private static void setLineFromPool(String[] pool, Object... args) {
        setLineFromPool(pool, null, args);
    }

    private static void setLineFromPool(String[] pool, ResourceLocation[] portraitPool, Object... args) {
        if (pool == null || pool.length == 0) {
            return;
        }
        String key = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        setLineByKey(key, args);
        if (portraitPool != null && portraitPool.length > 0) {
            currentPortrait = portraitPool[ThreadLocalRandom.current().nextInt(portraitPool.length)];
        }
    }

    private static void updateTypewriter(long dtMs) {
        if (shownLine.length() >= fullLine.length()) {
            return;
        }
        typeProgress += (dtMs / 1000f) * TYPEWRITER_CPS;
        int len = Math.min(fullLine.length(), Math.max(0, (int) typeProgress));
        shownLine = fullLine.substring(0, len);
    }

    private static boolean consumeHitInput(Minecraft mc) {
        return RhythmKeyMappings.RHYTHM_HIT.consumeClick();
    }

    private static boolean consumeSkipClick(Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int panelW = 480;
        int panelH = 270;
        int x = w / 2 - panelW / 2;
        int y = Math.max(8, h / 2 - panelH / 2);
        int skipW = 52;
        int skipH = 14;
        int skipX = x + panelW - skipW - 8;
        int skipY = y + 4;

        double mouseGuiX = mc.mouseHandler.xpos() * w / (double) mc.getWindow().getScreenWidth();
        double mouseGuiY = mc.mouseHandler.ypos() * h / (double) mc.getWindow().getScreenHeight();

        boolean inBox = mouseGuiX >= skipX && mouseGuiX <= skipX + skipW
                && mouseGuiY >= skipY && mouseGuiY <= skipY + skipH;
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = inBox && down && !lastMouseDown;
        lastMouseDown = down;
        return clicked;
    }

    private static float judgeTrackX() {
        return TRACK_WIDTH - JUDGE_OFFSET;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String resolveMaidName(Minecraft mc, UUID targetMaid) {
        if (mc.level == null || targetMaid == null) {
            return I18n.get("entity.maidmarriage.maid_child");
        }
        for (var entity : mc.level.entitiesForRendering()) {
            if (targetMaid.equals(entity.getUUID())) {
                return entity.getName().getString();
            }
        }
        return I18n.get("entity.maidmarriage.maid_child");
    }

    private static String resolveMasterName(Minecraft mc) {
        String playerName = mc.player != null ? mc.player.getName().getString() : I18n.get("name.maidmarriage.master_default");
        return ModConfigs.resolveMaidAddressing(playerName);
    }

    private static final class Note {
        private float x;
        private final boolean eighth;

        private Note(float x, boolean eighth) {
            this.x = x;
            this.eighth = eighth;
        }
    }
}
