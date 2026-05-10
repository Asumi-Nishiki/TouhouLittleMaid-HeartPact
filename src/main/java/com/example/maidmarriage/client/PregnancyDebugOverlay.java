package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.compat.bauble.GrowthPauseUtil;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PregnancyDebugOverlay {
    private static final int CACHE_REFRESH_TICKS = 20;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static boolean cachedShowPregnancyDebug;
    private static List<OverlayLine> cachedLines = List.of();
    private static BirthWarning cachedBirthWarning;

    private PregnancyDebugOverlay() {
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            resetCache();
            return;
        }

        boolean showPregnancyDebug = ModConfigs.showPregnancyDebugCountdown();
        long now = mc.level.getGameTime();
        if (shouldRefreshCache(now, showPregnancyDebug)) {
            refreshCache(mc, now, showPregnancyDebug);
        }

        if (cachedBirthWarning != null) {
            cachedBirthWarning.draw(event.getGuiGraphics(), mc);
        }
        if (cachedLines.isEmpty()) {
            return;
        }

        drawLines(event.getGuiGraphics(), mc, cachedLines);
    }

    private static boolean shouldRefreshCache(long now, boolean showPregnancyDebug) {
        return cachedGameTime == Long.MIN_VALUE
                || now < cachedGameTime
                || now - cachedGameTime >= CACHE_REFRESH_TICKS
                || cachedShowPregnancyDebug != showPregnancyDebug;
    }

    private static void refreshCache(Minecraft mc, long now, boolean showPregnancyDebug) {
        cachedGameTime = now;
        cachedShowPregnancyDebug = showPregnancyDebug;
        cachedBirthWarning = null;

        AABB search = mc.player.getBoundingBox().inflate(64.0D);
        List<EntityMaid> maids = mc.level.getEntitiesOfClass(
                EntityMaid.class,
                search,
                maid -> maid.isOwnedBy(mc.player));
        if (maids.isEmpty()) {
            cachedLines = List.of();
            return;
        }

        cachedBirthWarning = collectUpcomingBirthWarning(mc, maids);

        List<OverlayLine> lines = new ArrayList<>();
        if (showPregnancyDebug) {
            int adultAfterTicks = Math.max(1, ModConfigs.childGrowthDays()) * 24000;
            for (EntityMaid maid : maids) {
                PregnancyData data = maid.getData(ModTaskData.PREGNANCY_DATA);
                if (data != null && data.pregnant()) {
                    long needTicks = (long) ModConfigs.pregnancyBirthDays() * 24000L;
                    long passedTicks = Math.max(0L, maid.level().getGameTime() - data.conceivedGameTime());
                    long leftTicks = Math.max(0L, needTicks - passedTicks);
                    long leftSeconds = (long) Math.ceil(leftTicks / 20.0D);
                    Component text = Component.translatable("overlay.maidmarriage.debug.birth_countdown", maid.getName(), formatSeconds(leftSeconds));
                    lines.add(new OverlayLine(leftTicks, 0xFF8BFF98, text));
                }
                if (MaidChildEntity.shouldStayChild(maid)) {
                    int growthTicks = resolveGrowthTicks(maid);
                    long leftTicks = Math.max(0L, (long) adultAfterTicks - growthTicks);
                    long leftSeconds = (long) Math.ceil(leftTicks / 20.0D);
                    Component text = Component.translatable("overlay.maidmarriage.debug.growth_countdown", maid.getName(), formatSeconds(leftSeconds));
                    lines.add(new OverlayLine(leftTicks, 0xFF8BD7FF, text));
                    if (GrowthPauseUtil.hasSunflowerHairpin(maid)) {
                        lines.add(new OverlayLine(leftTicks, 0xFFFFD38B,
                                Component.translatable("overlay.maidmarriage.debug.growth_paused_hairpin")));
                    }
                }
            }
        }

        for (EntityMaid maid : maids) {
            PregnancyData data = maid.getData(ModTaskData.PREGNANCY_DATA);
            if (data == null || !data.isInPostpartumRecovery(now)) {
                continue;
            }
            long leftTicks = Math.max(0L, data.postpartumEndGameTime() - now);
            long leftSeconds = (long) Math.ceil(leftTicks / 20.0D);
            Component text = Component.translatable("overlay.maidmarriage.postpartum_countdown", maid.getName(), formatSeconds(leftSeconds));
            lines.add(new OverlayLine(leftTicks, 0xFFFFD38B, text));
        }

        lines.sort(Comparator.comparingLong(OverlayLine::sortTicks).thenComparingInt(OverlayLine::color));
        cachedLines = List.copyOf(lines);
    }

    private static void drawLines(GuiGraphics guiGraphics, Minecraft mc, List<OverlayLine> lines) {
        int drawY = 8;
        int maxLines = 8;
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            OverlayLine line = lines.get(i);
            guiGraphics.drawString(mc.font, line.text(), 8, drawY, line.color(), true);
            drawY += 10;
        }
        if (lines.size() > maxLines) {
            Component more = Component.literal("... +" + (lines.size() - maxLines));
            guiGraphics.drawString(mc.font, more, 8, drawY, 0xFFB8B8B8, true);
        }
    }

    private static void resetCache() {
        cachedGameTime = Long.MIN_VALUE;
        cachedShowPregnancyDebug = false;
        cachedLines = List.of();
        cachedBirthWarning = null;
    }

    private static String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static int resolveGrowthTicks(EntityMaid maid) {
        if (maid instanceof MaidChildEntity child) {
            return Math.max(0, child.debugGrowthTicks());
        }
        com.example.maidmarriage.data.ChildStateData state = maid.getData(ModTaskData.CHILD_STATE_DATA);
        if (state != null && state.child()) {
            return Math.max(0, state.growthTicks());
        }
        return Math.max(0, maid.getPersistentData().getInt(MaidChildEntity.PERSISTENT_GROWTH_TICKS_KEY));
    }

    private record OverlayLine(long sortTicks, int color, Component text) {
    }

    private record BirthWarning(Component text) {
        private void draw(GuiGraphics guiGraphics, Minecraft mc) {
            int drawX = mc.getWindow().getGuiScaledWidth() - mc.font.width(text) - 8;
            guiGraphics.drawString(mc.font, text, drawX, 8, 0xFFFF5A5A, true);
        }
    }

    /**
     * 每秒缓存一次临近分娩提示，避免 GUI 每一层、每一帧都扫描 64 格内所有女仆。
     */
    private static BirthWarning collectUpcomingBirthWarning(Minecraft mc, List<EntityMaid> maids) {
        long minLeftTicks = Long.MAX_VALUE;
        EntityMaid target = null;
        long warningWindowTicks = 24000L;
        for (EntityMaid maid : maids) {
            PregnancyData data = maid.getData(ModTaskData.PREGNANCY_DATA);
            if (data == null || !data.pregnant()) {
                continue;
            }
            long needTicks = (long) ModConfigs.pregnancyBirthDays() * 24000L;
            long passedTicks = Math.max(0L, maid.level().getGameTime() - data.conceivedGameTime());
            long leftTicks = Math.max(0L, needTicks - passedTicks);
            if (leftTicks <= warningWindowTicks && leftTicks < minLeftTicks) {
                minLeftTicks = leftTicks;
                target = maid;
            }
        }
        if (target == null) {
            return null;
        }
        String timeText = formatSeconds((long) Math.ceil(minLeftTicks / 20.0D));
        Component text = Component.translatable("overlay.maidmarriage.birth_warning", target.getName(), timeText);
        return new BirthWarning(text);
    }
}
