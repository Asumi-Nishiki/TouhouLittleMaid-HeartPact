package com.example.maidmarriage.compat;

import com.example.maidmarriage.advancement.ModAdvancements;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.RelationshipProgressData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 女仆关系进度统一入口。
 *
 * <p>好感度只负责“数值成熟度”，表白成功与否负责“关系是否真正跨阶段”。
 * 这样可以把表白/婚礼入口与右上角关系显示、互动解锁统一起来。
 */
public final class MaidRelationshipManager {
    public static final int PET_UNLOCK_FAVORABILITY = RelationshipThresholds.PET_UNLOCK;
    public static final int HUG_UNLOCK_FAVORABILITY = RelationshipThresholds.HUG_UNLOCK;
    public static final int CONFESSION_UNLOCK_FAVORABILITY = RelationshipThresholds.DATING_UNLOCK;
    public static final int MARRIAGE_UNLOCK_FAVORABILITY = RelationshipThresholds.MARRIAGE_UNLOCK;
    public static final int FAVORABILITY_MAX = RelationshipThresholds.FAVORABILITY_MAX;
    private static final String TAG_PLAYER_PRIMARY_MAID = "maidmarriage_primary_maid";

    private MaidRelationshipManager() {
    }

    public static RelationshipProgressData get(EntityMaid maid) {
        if (maid == null || ModTaskData.RELATIONSHIP_PROGRESS_DATA == null) {
            return RelationshipProgressData.EMPTY;
        }
        RelationshipProgressData data = maid.getOrCreateData(ModTaskData.RELATIONSHIP_PROGRESS_DATA, RelationshipProgressData.EMPTY);
        return data == null ? RelationshipProgressData.EMPTY : data;
    }

    public static boolean isConfessionCompleted(EntityMaid maid) {
        return get(maid).confessionCompleted();
    }

    public static void completeConfession(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide() || ModTaskData.RELATIONSHIP_PROGRESS_DATA == null) {
            return;
        }
        maid.setAndSyncData(ModTaskData.RELATIONSHIP_PROGRESS_DATA, get(maid).completeConfession());
        if (maid.getOwner() instanceof ServerPlayer owner) {
            ModAdvancements.grantHeartPact(owner);
        }
    }

    public static boolean isMarried(EntityMaid maid) {
        if (maid == null || ModTaskData.MARRIAGE_DATA == null) {
            return false;
        }
        MarriageData marriageData = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        return marriageData != null && marriageData.married();
    }

    public static boolean canShowConfession(EntityMaid maid) {
        return canShowConfession(null, maid);
    }

    public static boolean canShowConfession(Player player, EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        return maid.getFavorability() >= CONFESSION_UNLOCK_FAVORABILITY
                && !isConfessionCompleted(maid)
                && !isMarried(maid)
                && !hasBlockedMarriageByMonogamy(player, maid);
    }

    public static boolean canShowMarriage(EntityMaid maid) {
        return canShowMarriage(null, maid);
    }

    public static boolean canShowMarriage(Player player, EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        return maid.getFavorability() >= MARRIAGE_UNLOCK_FAVORABILITY
                && isConfessionCompleted(maid)
                && !isMarried(maid)
                && !hasBlockedMarriageByMonogamy(player, maid);
    }

    public static boolean isKissUnlocked(EntityMaid maid) {
        return maid != null
                && maid.getFavorability() >= CONFESSION_UNLOCK_FAVORABILITY
                && isConfessionCompleted(maid);
    }

    public static RelationStage resolveStage(EntityMaid maid) {
        if (maid == null) {
            return RelationStage.INITIAL;
        }
        return resolveStage(maid.getFavorability(), isConfessionCompleted(maid), isMarried(maid));
    }

    public static RelationStage resolveStage(int favorability, boolean confessionCompleted, boolean married) {
        if (married) {
            return RelationStage.MARRIAGE;
        }
        if (confessionCompleted) {
            return RelationStage.DATING;
        }
        if (favorability >= HUG_UNLOCK_FAVORABILITY) {
            return RelationStage.CLOSE;
        }
        if (favorability >= PET_UNLOCK_FAVORABILITY) {
            return RelationStage.WARM;
        }
        return RelationStage.INITIAL;
    }

    private static boolean hasBlockedMarriageByMonogamy(Player player, EntityMaid currentMaid) {
        return isBlockedByMonogamy(player, currentMaid);
    }

    public static boolean isBlockedByMonogamy(Player player, EntityMaid currentMaid) {
        if (player == null || currentMaid == null) {
            return false;
        }
        if (RomanceSleepManager.resolveHaremMode(player)) {
            return false;
        }
        CompoundTag tag = player.getPersistentData();
        if (!tag.hasUUID(TAG_PLAYER_PRIMARY_MAID)) {
            return hasOtherLoadedMarriage(player, currentMaid);
        }
        return !tag.getUUID(TAG_PLAYER_PRIMARY_MAID).equals(currentMaid.getUUID());
    }

    private static boolean hasOtherLoadedMarriage(Player player, EntityMaid currentMaid) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        for (Entity entity : serverPlayer.serverLevel().getAllEntities()) {
            if (!(entity instanceof EntityMaid maid) || maid == currentMaid || !maid.isAlive()) {
                continue;
            }
            if (isMarriedWithPlayer(maid, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMarriedWithPlayer(EntityMaid maid, Player player) {
        if (maid == null || player == null || ModTaskData.MARRIAGE_DATA == null) {
            return false;
        }
        MarriageData data = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        return data != null && data.isMarriedWith(player.getUUID());
    }
}
