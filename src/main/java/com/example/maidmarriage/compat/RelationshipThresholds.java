package com.example.maidmarriage.compat;

/**
 * 关系阈值统一入口。
 *
 * <p>车万女仆原版主要好感档位是 64 / 192 / 384。女仆婚姻在此基础上保留两个更细的前期节点：
 * 32 解锁摸头，64 解锁拥抱，128 进入表白/亲吻/交往阶段，192 以上进入结婚阶段。
 */
public final class RelationshipThresholds {
    public static final int PET_UNLOCK = 32;
    public static final int HUG_UNLOCK = 64;
    public static final int DATING_UNLOCK = 128;
    public static final int MARRIAGE_UNLOCK = 192;
    public static final int FAVORABILITY_MAX = 384;

    private RelationshipThresholds() {
    }
}
