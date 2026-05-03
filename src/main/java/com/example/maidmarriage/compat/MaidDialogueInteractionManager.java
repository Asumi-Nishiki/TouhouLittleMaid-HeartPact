package com.example.maidmarriage.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * 剧情对话里“选项结果”的服务端结算入口。
 *
 * <p>这一层只负责三件事：
 * 1. 校验这次对话结果是否作用在当前正在互动的那只女仆身上；
 * 2. 按“今日心情”把选项结果折算成真正的好感变化；
 * 3. 同步写回心情变化。
 *
 * <p>它刻意不处理 UI 文本，也不自己弹剧情；
 * 文本继续由现有的对话 JSON 和 `HugActionScreen` 控制，
 * 服务端这里只做安全结算。
 */
public final class MaidDialogueInteractionManager {
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;

    private MaidDialogueInteractionManager() {
    }

    public static void handleDialogueChoiceResult(ServerPlayer player,
                                                  @Nullable UUID maidUuid,
                                                  int positiveFavor,
                                                  int neutralFavor,
                                                  int negativeFavor,
                                                  int positiveMoodDelta,
                                                  int neutralMoodDelta,
                                                  int negativeMoodDelta) {
        EntityMaid maid = resolveCurrentInteractionMaid(player, maidUuid);
        if (maid == null || !maid.isOwnedBy(player)) {
            return;
        }

        MaidMoodManager.ensureDailyMood(maid);
        MaidMoodManager.applyDialogueFavorabilityResult(
                maid,
                positiveFavor,
                neutralFavor,
                negativeFavor,
                FAVORABILITY_CAP
        );
        MaidMoodManager.applyDialogueMoodResult(
                maid,
                positiveMoodDelta,
                neutralMoodDelta,
                negativeMoodDelta
        );
        MaidMoodManager.markMeaningfulInteraction(maid);
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
}
