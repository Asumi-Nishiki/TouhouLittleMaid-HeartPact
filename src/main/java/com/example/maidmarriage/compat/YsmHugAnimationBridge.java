package com.example.maidmarriage.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * YSM 拥抱动作桥。
 *
 * <p>这里故意不再强制调用类似 `playRouletteAnim("extra8")` 的模型包私有动作。
 *
 * <p>原因是 YSM 的 extra 动画本质上属于“模型包作者自己的约定”：
 * 官方未加密酒狐、商店加密模型、玩家自制模型，完全可能把 `extra8` 做成彼此不同的内容。
 * 如果我们把某个模型包的 extra 动作硬当成通用拥抱入口，
 * 最终就会把错误动作套到别的模型上，出现姿态串线甚至骑乘/坐姿误入的问题。
 *
 * <p>现在 YSM 拥抱统一交给客户端侧的运行时姿态桥在渲染期按骨骼兜底，
 * 这样既不依赖模型包是否加密，也不会污染模型包作者自己定义的轮盘动作。
 */
public final class YsmHugAnimationBridge {
    private YsmHugAnimationBridge() {
    }

    public static void playHugIfAvailable(EntityMaid maid) {
        // 刻意留空：不要再强制播放模型包私有 extra 动画，避免不同 YSM 模型之间动作串线。
    }

    public static void stopIfAvailable(EntityMaid maid) {
        // 本类没有主动启动轮盘动作，因此结束拥抱时也不应该去停止模型包作者自己的私有动画。
    }
}
