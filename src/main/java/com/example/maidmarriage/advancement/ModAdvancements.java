package com.example.maidmarriage.advancement;

import com.example.maidmarriage.MaidMarriageMod;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * 成就发放工具：统一处理结婚、同眠、生育相关成就奖励。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModAdvancements {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation ROOT = id("root");
    public static final ResourceLocation STAGE_WARM = id("stage_warm");
    public static final ResourceLocation STAGE_CLOSE = id("stage_close");
    public static final ResourceLocation HEART_PACT = id("heart_pact");
    public static final ResourceLocation MARRIAGE = id("marriage");
    public static final ResourceLocation FIRST_ROMANCE = id("first_romance");
    public static final ResourceLocation CHILDBIRTH = id("childbirth");
    public static final ResourceLocation ROMANCE_TEN = id("romance_ten");
    public static final ResourceLocation FLOWER_RED = id("flower_red");
    public static final ResourceLocation FLOWER_YELLOW = id("flower_yellow");
    public static final ResourceLocation FLOWER_BLUE = id("flower_blue");
    public static final ResourceLocation FLOWER_WHITE = id("flower_white");
    public static final ResourceLocation FLOWER_ORANGE = id("flower_orange");
    public static final ResourceLocation FLOWER_PINK = id("flower_pink");
    public static final ResourceLocation FLOWER_PURPLE = id("flower_purple");
    public static final ResourceLocation FLOWER_BLACK = id("flower_black");
    public static final ResourceLocation FLOWER_RAINBOW = id("flower_rainbow");

    private ModAdvancements() {
    }

    public static void grantMarriage(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, MARRIAGE);
    }

    public static void grantWarmStage(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, STAGE_WARM);
    }

    public static void grantCloseStage(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, STAGE_CLOSE);
    }

    public static void grantHeartPact(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, HEART_PACT);
    }

    public static void grantFirstRomance(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, FIRST_ROMANCE);
    }

    public static void grantChildbirth(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, CHILDBIRTH);
    }

    public static void grantRomanceTen(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, ROMANCE_TEN);
    }

    public static void grantFlowerGift(ServerPlayer player, String suffix) {
        grant(player, ROOT);
        switch (suffix) {
            case "red" -> grant(player, FLOWER_RED);
            case "yellow" -> grant(player, FLOWER_YELLOW);
            case "blue" -> grant(player, FLOWER_BLUE);
            case "white" -> grant(player, FLOWER_WHITE);
            case "orange" -> grant(player, FLOWER_ORANGE);
            case "pink" -> grant(player, FLOWER_PINK);
            case "purple" -> grant(player, FLOWER_PURPLE);
            case "black" -> grant(player, FLOWER_BLACK);
            default -> {
            }
        }
    }

    public static void grantRainbowBouquet(ServerPlayer player) {
        grant(player, ROOT);
        grant(player, FLOWER_RAINBOW);
    }

    private static void grant(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.serverLevel().getServer();
        if (server == null) {
            return;
        }
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        if (advancement == null) {
            LOGGER.warn("Cannot find advancement {} while granting to {}", id, player.getGameProfile().getName());
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : advancement.getCriteria().keySet()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MaidMarriageMod.MOD_ID, path);
    }
}
