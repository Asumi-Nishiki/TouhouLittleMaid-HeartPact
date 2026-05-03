package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 剧情关键节点的服务端入口。
 *
 * <p>表白成功、婚礼前的主面板打开、最终结婚提交，都通过这里统一落盘。
 */
public final class MaidStoryInteractionManager {
    private static final Map<UUID, PendingMarriageSession> PENDING_MARRIAGES = new ConcurrentHashMap<>();

    private MaidStoryInteractionManager() {
    }

    public static void handleStoryAction(ServerPlayer player, @Nullable UUID maidUuid, String actionId) {
        EntityMaid maid = resolveCurrentInteractionMaid(player, maidUuid);
        if (maid == null || !maid.isOwnedBy(player) || actionId == null || actionId.isBlank()) {
            return;
        }

        switch (actionId) {
            case "confession_accept" -> {
                if (!MaidRelationshipManager.canShowConfession(player, maid)) {
                    player.sendSystemMessage(DialogueScriptManager.componentForPlayer(
                            player,
                            MaidRelationshipManager.isBlockedByMonogamy(player, maid)
                                    ? "message.maidmarriage.proposal.harem_disabled"
                                    : "message.maidmarriage.confession.not_ready",
                            maid.getDisplayName()));
                    return;
                }
                MaidRelationshipManager.completeConfession(maid);
            }
            case "open_marriage_panel" -> openMarriagePanel(player, maid);
            case "commit_marriage" -> commitMarriage(player, maid);
            default -> {
            }
        }
    }

    private static void openMarriagePanel(ServerPlayer player, EntityMaid maid) {
        if (!canCommitMarriage(player, maid) && !MaidRelationshipManager.isMarried(maid)) {
            player.sendSystemMessage(Component.literal("现在还没有准备好进行婚礼誓约。"));
            return;
        }
        PENDING_MARRIAGES.put(player.getUUID(), new PendingMarriageSession(maid.getUUID(), player.level().getGameTime()));
        maid.openMaidGui(player);
    }

    private static void commitMarriage(ServerPlayer player, EntityMaid maid) {
        PendingMarriageSession pending = PENDING_MARRIAGES.get(player.getUUID());
        if (pending == null || !maid.getUUID().equals(pending.maidUuid())) {
            player.sendSystemMessage(Component.literal("先给女仆戴上戒指，再继续婚礼吧。"));
            return;
        }
        if (MaidRelationshipManager.isMarried(maid)) {
            PENDING_MARRIAGES.remove(player.getUUID());
            return;
        }
        if (!canCommitMarriage(player, maid)) {
            player.sendSystemMessage(Component.literal("现在还没有准备好进行婚礼誓约。"));
            return;
        }
        ItemStack playerRing = player.getOffhandItem();
        ItemStack maidRing = maid.getMainHandItem();
        if (!playerRing.is(ModItems.PROPOSAL_RING.get()) || !maidRing.is(ModItems.PROPOSAL_RING.get())) {
            player.sendSystemMessage(Component.literal("请先给自己副手和女仆主手都戴上求婚戒指。"));
            return;
        }
        if (MarriageEventHandler.isRingUsed(playerRing) || MarriageEventHandler.isRingUsed(maidRing)) {
            player.sendSystemMessage(Component.literal("这枚戒指已经绑定过了，请换一枚新的。"));
            return;
        }

        MarriageData currentData = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        ItemStack playerVowRing = createVowRing(player, maid);
        ItemStack maidVowRing = createVowRing(player, maid);
        consumePlayerOffhandRing(player);
        consumeMaidMainhandRing(maid);
        givePlayerVowRing(player, playerVowRing);
        MarriageEventHandler.giveRingToMaid(maid, maidVowRing);
        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, currentData.marry(player.getUUID(), maid.level().getGameTime()));
        MarriageEventHandler.giveMarriagePillows(player, maid);
        MarriageEventHandler.clearConsentApproval(maid);
        MarriageEventHandler.markPrimaryMaidIfNeeded(player, maid);
        playMarriageEffects(player, maid);
        ModAdvancements.grantMarriage(player);
        PENDING_MARRIAGES.remove(player.getUUID());
    }

    public static boolean canCommitMarriage(Player player, EntityMaid maid) {
        return MaidRelationshipManager.canShowMarriage(player, maid);
    }

    private static ItemStack createVowRing(Player player, EntityMaid maid) {
        ItemStack ring = new ItemStack(ModItems.PROPOSAL_RING.get());
        MarriageEventHandler.engraveRing(ring, player, maid);
        return ring;
    }

    private static void consumePlayerOffhandRing(ServerPlayer player) {
        // 婚礼是把两枚普通戒指刻成誓约戒指，不按创造模式保留原物，避免同一枚戒指出现普通/誓约两份。
        ItemStack offhand = player.getOffhandItem();
        offhand.shrink(1);
        if (offhand.isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static void consumeMaidMainhandRing(EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        mainHand.shrink(1);
        if (mainHand.isEmpty()) {
            maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static void givePlayerVowRing(ServerPlayer player, ItemStack ring) {
        player.setItemInHand(InteractionHand.OFF_HAND, ring);
    }

    private static void playMarriageEffects(ServerPlayer player, EntityMaid maid) {
        if (maid.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY(1), maid.getZ(),
                    10, 0.25, 0.25, 0.25, 0.01);
        }
        maid.level().playSound(null, maid.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.sendSystemMessage(Component.literal("婚礼誓约已经完成。"));
    }

    @Nullable
    private static EntityMaid resolveCurrentInteractionMaid(ServerPlayer player, @Nullable UUID maidUuid) {
        EntityMaid adultMaid = MaidHugManager.getInteractingMaid(player);
        if (matchesTarget(adultMaid, maidUuid)) {
            return adultMaid;
        }

        EntityMaid childMaid = ChildInteractionManager.getInteractingMaid(player);
        if (matchesTarget(childMaid, maidUuid)) {
            return childMaid;
        }
        return null;
    }

    private static boolean matchesTarget(@Nullable EntityMaid maid, @Nullable UUID maidUuid) {
        if (maid == null) {
            return false;
        }
        return maidUuid == null || maidUuid.equals(maid.getUUID());
    }

    private record PendingMarriageSession(UUID maidUuid, long startedGameTime) {
    }
}
