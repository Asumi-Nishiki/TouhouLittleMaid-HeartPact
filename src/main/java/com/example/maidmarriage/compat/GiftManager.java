package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.GiftResultPayload;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * 送礼结算入口。
 *
 * <p>客户端只负责挑礼物和看预览，真正的好感、心情、消耗和台词都在这里统一结算。
 * 这样以后再细分礼物效果时，不需要把规则复制到 UI 和网络层两份。
 */
public final class GiftManager {
    private static final String TAG_GIFT_DAY_PREFIX = "maidmarriage_gift_day_";
    private static final String TAG_GIFT_COUNT_PREFIX = "maidmarriage_gift_count_";
    private static final String TAG_GIFT_FAVOR_GAIN_DAY = "maidmarriage_gift_favor_gain_day";
    private static final String TAG_GIFT_FAVOR_GAIN_TODAY = "maidmarriage_gift_favor_gain_today";
    private static final String TAG_GIFT_FAVOR_LOSS_DAY = "maidmarriage_gift_favor_loss_day";
    private static final String TAG_GIFT_FAVOR_LOSS_TODAY = "maidmarriage_gift_favor_loss_today";
    private static final String TAG_GIFT_RECEIVE_DAY = "maidmarriage_gift_receive_day";
    private static final String TAG_GIFT_RECEIVE_TODAY = "maidmarriage_gift_receive_today";
    private static final int GIFT_FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;
    private static final int GIFT_DAILY_RECEIVE_LIMIT = 2;
    private static final int GIFT_DAILY_GAIN_LIMIT = 12;
    private static final int GIFT_DAILY_LOSS_LIMIT = 8;
    private static final int GIFT_CATEGORY_REPEAT_LIMIT = 2;
    private static final Map<GiftTable.GiftCategory, String> GIFT_DIALOGUE_KEYS = Map.of(
            GiftTable.GiftCategory.FLOWER, "dialogue.maidmarriage.gift.flower",
            GiftTable.GiftCategory.SWEET, "dialogue.maidmarriage.gift.sweet",
            GiftTable.GiftCategory.MEAL, "dialogue.maidmarriage.gift.meal",
            GiftTable.GiftCategory.VALUABLE, "dialogue.maidmarriage.gift.valuable",
            GiftTable.GiftCategory.GENERIC, "dialogue.maidmarriage.gift.generic",
            GiftTable.GiftCategory.ODD, "dialogue.maidmarriage.gift.odd",
            GiftTable.GiftCategory.OFFENSIVE, "dialogue.maidmarriage.gift.offensive"
    );
    private static final Map<GiftTable.GiftCategory, String> CHILD_GIFT_DIALOGUE_KEYS = Map.of(
            GiftTable.GiftCategory.FLOWER, "dialogue.maidmarriage.child_gift.flower",
            GiftTable.GiftCategory.SWEET, "dialogue.maidmarriage.child_gift.sweet",
            GiftTable.GiftCategory.MEAL, "dialogue.maidmarriage.child_gift.meal",
            GiftTable.GiftCategory.VALUABLE, "dialogue.maidmarriage.child_gift.valuable",
            GiftTable.GiftCategory.GENERIC, "dialogue.maidmarriage.child_gift.generic",
            GiftTable.GiftCategory.ODD, "dialogue.maidmarriage.child_gift.odd",
            GiftTable.GiftCategory.OFFENSIVE, "dialogue.maidmarriage.child_gift.offensive"
    );

    private GiftManager() {
    }

    public static void handleGiftSubmit(ServerPlayer player, @Nullable UUID maidUuid, int slotIndex) {
        if (player == null || maidUuid == null) {
            return;
        }
        if (slotIndex < 0 || slotIndex >= player.getInventory().items.size()) {
            return;
        }

        ServerLevel level = player.serverLevel();
        EntityMaid maid = resolveMaid(level, maidUuid);
        if (maid == null || !maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.gift.invalid_target"));
            return;
        }
        if (maid.distanceToSqr(player) > 36.0D) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.gift.too_far", maid.getDisplayName()));
            return;
        }

        ItemStack stack = player.getInventory().items.get(slotIndex);
        if (stack.isEmpty()) {
            return;
        }

        GiftTable.GiftPreview preview = GiftTable.preview(stack, maid);
        boolean childGift = MaidChildEntity.shouldStayChild(maid);
        if (!preview.allowed()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                    player,
                    "message.maidmarriage.gift.blocked",
                    maid.getDisplayName(),
                    Component.translatable(preview.detailKey())));
            return;
        }

        if (!childGift && GiftTable.isFlowerGift(stack)) {
            boolean willActuallyReceiveGift = MarriageEventHandler.canAcceptFlowerGiftFromUi(maid, stack);
            if (willActuallyReceiveGift) {
                if (!canReceiveGiftToday(maid)) {
                    sendDailyLimitMessage(player, maid);
                    return;
                }
                if (MarriageEventHandler.handleFlowerGiftFromUi(player, maid, stack)) {
                    syncGiftSlot(player, slotIndex, stack);
                    recordGiftReceived(maid);
                    MaidMoodManager.markMeaningfulInteraction(maid);
                    sendGiftResult(player, maid, GiftTable.GiftCategory.FLOWER, preview.reaction());
                    return;
                }
            }
            // 已经送过颜色的普通花不会再触发花束收集奖励，但仍然可以作为日常礼物送出。
            preview = GiftTable.repeatedFlowerAsGenericPreview(maid);
        }

        if (!canReceiveGiftToday(maid)) {
            sendDailyLimitMessage(player, maid);
            return;
        }

        int favorDelta = preview.favorabilityDelta();
        int moodDelta = preview.moodDelta();
        int categoryCount = getCategoryCount(maid, preview.category());
        if (favorDelta > 0 && categoryCount >= GIFT_CATEGORY_REPEAT_LIMIT) {
            favorDelta = Math.max(0, favorDelta - 1);
            moodDelta = Math.max(0, moodDelta - 1);
        }

        int actualFavorDelta = applyGiftFavorabilityDelta(maid, favorDelta);
        if (moodDelta != 0) {
            MaidMoodManager.addMood(maid, moodDelta);
        }
        recordCategoryUse(maid, preview.category());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            player.getInventory().setChanged();
            syncGiftSlot(player, slotIndex, stack);
        }
        recordGiftReceived(maid);
        MaidMoodManager.markMeaningfulInteraction(maid);

        String dialogueKey = giftDialogueKey(preview.category(), childGift);
        RomanceSleepManager.speakSingleLine(maid, dialogueKey);
        sendGiftResult(player, maid, preview.category(), preview.reaction());
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                player,
                "message.maidmarriage.gift.result",
                maid.getDisplayName(),
                Component.translatable(categoryLabelKey(preview.category())),
                actualFavorDelta,
                moodDelta));
    }

    private static void syncGiftSlot(ServerPlayer player, int slotIndex, ItemStack stack) {
        if (player == null || slotIndex < 0) {
            return;
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slotIndex, stack.copy()));
    }

    private static String giftDialogueKey(GiftTable.GiftCategory category, boolean childGift) {
        Map<GiftTable.GiftCategory, String> table = childGift ? CHILD_GIFT_DIALOGUE_KEYS : GIFT_DIALOGUE_KEYS;
        return table.getOrDefault(
                category,
                childGift ? "dialogue.maidmarriage.child_gift.generic" : "dialogue.maidmarriage.gift.generic");
    }

    private static void sendDailyLimitMessage(ServerPlayer player, EntityMaid maid) {
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                player,
                "message.maidmarriage.gift.daily_limit",
                maid.getDisplayName(),
                GIFT_DAILY_RECEIVE_LIMIT));
        sendGiftLimitResult(player, maid);
    }

    private static void sendGiftResult(ServerPlayer player, EntityMaid maid, GiftTable.GiftCategory category, GiftTable.GiftReaction reaction) {
        if (player == null || maid == null || category == null || reaction == null) {
            return;
        }
        ModNetworking.sendGiftResult(player, new GiftResultPayload(maid.getUUID(), category.name(), reaction.name()));
    }

    private static void sendGiftLimitResult(ServerPlayer player, EntityMaid maid) {
        if (player == null || maid == null) {
            return;
        }
        // 每日上限不是物品分类，而是一次“她明确拒绝继续收礼”的剧情反馈。
        ModNetworking.sendGiftResult(player, new GiftResultPayload(maid.getUUID(), "daily_limit", "blocked"));
    }

    private static boolean canReceiveGiftToday(EntityMaid maid) {
        return getGiftReceiveCountToday(maid) < GIFT_DAILY_RECEIVE_LIMIT;
    }

    private static void recordGiftReceived(EntityMaid maid) {
        var tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        long recordedDay = tag.getLong(TAG_GIFT_RECEIVE_DAY);
        if (recordedDay != currentDay) {
            tag.putLong(TAG_GIFT_RECEIVE_DAY, currentDay);
            tag.putInt(TAG_GIFT_RECEIVE_TODAY, 0);
        }
        tag.putInt(TAG_GIFT_RECEIVE_TODAY, Mth.clamp(tag.getInt(TAG_GIFT_RECEIVE_TODAY) + 1, 0, GIFT_DAILY_RECEIVE_LIMIT));
    }

    private static int getGiftReceiveCountToday(EntityMaid maid) {
        // 使用女仆自己的持久数据计数：同一只女仆不管哪个入口送礼，每个游戏日都只收两份。
        var tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        long recordedDay = tag.getLong(TAG_GIFT_RECEIVE_DAY);
        if (recordedDay != currentDay) {
            tag.putLong(TAG_GIFT_RECEIVE_DAY, currentDay);
            tag.putInt(TAG_GIFT_RECEIVE_TODAY, 0);
            return 0;
        }
        return Math.max(0, tag.getInt(TAG_GIFT_RECEIVE_TODAY));
    }

    private static int applyGiftFavorabilityDelta(EntityMaid maid, int delta) {
        if (delta == 0) {
            return 0;
        }
        var tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        if (delta > 0) {
            long recordedDay = tag.getLong(TAG_GIFT_FAVOR_GAIN_DAY);
            if (recordedDay != currentDay) {
                tag.putLong(TAG_GIFT_FAVOR_GAIN_DAY, currentDay);
                tag.putInt(TAG_GIFT_FAVOR_GAIN_TODAY, 0);
            }
            int gainedToday = Math.max(0, tag.getInt(TAG_GIFT_FAVOR_GAIN_TODAY));
            int remaining = Math.max(0, GIFT_DAILY_GAIN_LIMIT - gainedToday);
            if (remaining <= 0) {
                return 0;
            }
            int applied = MaidMoodManager.setFavorabilityWithRefresh(maid, maid.getFavorability() + Math.min(delta, remaining), GIFT_FAVORABILITY_CAP);
            if (applied > 0) {
                tag.putInt(TAG_GIFT_FAVOR_GAIN_TODAY, gainedToday + applied);
            }
            return applied;
        }

        long recordedDay = tag.getLong(TAG_GIFT_FAVOR_LOSS_DAY);
        if (recordedDay != currentDay) {
            tag.putLong(TAG_GIFT_FAVOR_LOSS_DAY, currentDay);
            tag.putInt(TAG_GIFT_FAVOR_LOSS_TODAY, 0);
        }
        int lostToday = Math.max(0, tag.getInt(TAG_GIFT_FAVOR_LOSS_TODAY));
        int remaining = Math.max(0, GIFT_DAILY_LOSS_LIMIT - lostToday);
        if (remaining <= 0) {
            return 0;
        }
        int applied = MaidMoodManager.setFavorabilityWithRefresh(maid, maid.getFavorability() + Math.max(delta, -remaining), GIFT_FAVORABILITY_CAP);
        if (applied < 0) {
            tag.putInt(TAG_GIFT_FAVOR_LOSS_TODAY, lostToday + Math.abs(applied));
        }
        return applied;
    }

    private static int getCategoryCount(EntityMaid maid, GiftTable.GiftCategory category) {
        if (maid == null || category == null) {
            return 0;
        }
        var tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        String dayKey = TAG_GIFT_DAY_PREFIX + category.name().toLowerCase();
        String countKey = TAG_GIFT_COUNT_PREFIX + category.name().toLowerCase();
        long recordedDay = tag.getLong(dayKey);
        if (recordedDay != currentDay) {
            tag.putLong(dayKey, currentDay);
            tag.putInt(countKey, 0);
        }
        return Math.max(0, tag.getInt(countKey));
    }

    private static void recordCategoryUse(EntityMaid maid, GiftTable.GiftCategory category) {
        if (maid == null || category == null) {
            return;
        }
        var tag = maid.getPersistentData();
        long currentDay = maid.level().getGameTime() / 24000L;
        String dayKey = TAG_GIFT_DAY_PREFIX + category.name().toLowerCase();
        String countKey = TAG_GIFT_COUNT_PREFIX + category.name().toLowerCase();
        long recordedDay = tag.getLong(dayKey);
        if (recordedDay != currentDay) {
            tag.putLong(dayKey, currentDay);
            tag.putInt(countKey, 0);
        }
        tag.putInt(countKey, Mth.clamp(tag.getInt(countKey) + 1, 0, 64));
    }

    @Nullable
    private static EntityMaid resolveMaid(ServerLevel level, UUID maidUuid) {
        if (level == null || maidUuid == null) {
            return null;
        }
        var entity = level.getEntity(maidUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private static String categoryLabelKey(GiftTable.GiftCategory category) {
        return switch (category) {
            case FLOWER -> "ui.maidmarriage.gift.category.flower";
            case SWEET -> "ui.maidmarriage.gift.category.sweet";
            case MEAL -> "ui.maidmarriage.gift.category.meal";
            case VALUABLE -> "ui.maidmarriage.gift.category.valuable";
            case GENERIC -> "ui.maidmarriage.gift.category.generic";
            case ODD -> "ui.maidmarriage.gift.category.odd";
            case OFFENSIVE -> "ui.maidmarriage.gift.category.offensive";
            case SPECIAL_BLOCKED -> "ui.maidmarriage.gift.category.special";
            case EMPTY -> "ui.maidmarriage.gift.category.empty";
        };
    }
}
