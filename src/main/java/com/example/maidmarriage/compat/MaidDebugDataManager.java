package com.example.maidmarriage.compat;

import com.example.maidmarriage.data.MaidMoodData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 女仆调试数据的服务端写入入口。
 *
 * <p>所有来自 F7 调试面板的修改都必须经过这里：
 * 1. 服务端按 UUID 找真实女仆实体；
 * 2. 校验玩家是否拥有这只女仆，OP 则允许跨所有者测试；
 * 3. 使用统一的好感/心情管理器写入，保证同步与原版属性刷新不被绕过。
 */
public final class MaidDebugDataManager {
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;

    private MaidDebugDataManager() {
    }

    public static void handleDebugData(ServerPlayer player, UUID maidUuid, int favorability, int mood) {
        if (player == null || maidUuid == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("调试面板仅限 OP 使用。"), true);
            return;
        }

        EntityMaid maid = findMaid(player, maidUuid);
        if (maid == null) {
            player.displayClientMessage(Component.literal("调试失败：找不到目标女仆。"), true);
            return;
        }
        if (!maid.isOwnedBy(player) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("调试失败：只能修改自己的女仆。"), true);
            return;
        }

        int safeFavorability = Mth.clamp(favorability, 0, FAVORABILITY_CAP);
        int safeMood = Mth.clamp(mood, MaidMoodData.MIN_MOOD, MaidMoodData.MAX_MOOD);
        MaidMoodManager.setFavorabilityWithRefresh(maid, safeFavorability, FAVORABILITY_CAP);
        MaidMoodManager.setMood(maid, safeMood);
        player.displayClientMessage(Component.literal(
                "调试已应用：好感度=" + maid.getFavorability() + "，心情=" + MaidMoodManager.value(maid)
        ), true);
    }

    @Nullable
    private static EntityMaid findMaid(ServerPlayer player, UUID maidUuid) {
        Entity sameLevelEntity = player.serverLevel().getEntity(maidUuid);
        if (sameLevelEntity instanceof EntityMaid maid) {
            return maid;
        }

        if (player.getServer() == null) {
            return null;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(maidUuid);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }
}
