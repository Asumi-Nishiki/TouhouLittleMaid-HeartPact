package com.example.maidmarriage.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 给 YSM 的 `tlm.*` molang 提供我们模组自己的动作状态。
 *
 * <p>这里不直接依赖 YSM 的任意类，只提供普通 Java 静态查询函数，
 * 由单独的 YSM 反射注入层在运行时桥接过去。
 */
public final class YsmMolangActionBridge {
    public static final int ACTION_NONE = 0;
    public static final int ACTION_HUG = 1;
    public static final int ACTION_KISS = 2;
    public static final int ACTION_PET = 3;
    public static final int ACTION_LIFT = 4;

    private YsmMolangActionBridge() {
    }

    public static int action(@Nullable EntityMaid maid) {
        if (maid == null) {
            return ACTION_NONE;
        }
        /*
         * 拥抱优先于举高高。
         *
         * 正常情况下成年女仆拥抱和小女仆举高高不会同时成立；
         * 但客户端渲染帧可能比服务端同步慢半拍。如果这里先返回 LIFT，
         * YSM 模型包就可能把“拥抱中的成年女仆”错误送进骑乘 / 坐姿分支，
         * 视觉上就会变成倒立并且双腿岔开的动作。
         */
        if (isHug(maid)) {
            return ACTION_HUG;
        }
        if (isLift(maid)) {
            return ACTION_LIFT;
        }
        if (isKiss(maid)) {
            return ACTION_KISS;
        }
        if (isPet(maid)) {
            return ACTION_PET;
        }
        return ACTION_NONE;
    }

    public static boolean isHug(@Nullable EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        Player player = MaidHugManager.getHugPlayer(maid);
        return MaidHugManager.isHugState(maid, player);
    }

    public static boolean isLift(@Nullable EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        Player player = MaidLiftManager.getLiftPlayer(maid);
        return MaidLiftManager.isLiftState(maid, player);
    }

    public static boolean isPet(@Nullable EntityMaid maid) {
        return maid != null && PetHeadManager.isPetHeadAnimating(maid);
    }

    public static boolean isKiss(@Nullable EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        Boolean active = DistExecutor.safeCallWhenOn(Dist.CLIENT, () -> () ->
                com.example.maidmarriage.client.HugClientState.isPostKissShyTurnActive(maid.getUUID()));
        return Boolean.TRUE.equals(active);
    }

    /**
     * 先提供一个最小可用字段。
     *
     * <p>YSM 动画通常已经有 `query.anim_time` 可用，
     * 所以这里先只返回动作是否激活，后续若模型包确实需要精确时长，再补充独立计时同步。
     */
    public static double actionTime(@Nullable EntityMaid maid) {
        return action(maid) == ACTION_NONE ? 0.0D : 1.0D;
    }
}
