package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalPassenger;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 直接拦截“女仆身上有乘客”这条条件动画分支。
 *
 * <p>当前大女仆抱小女仆时，最容易命中的错误动作并不是普通 riding，
 * 而是模型包里基于 `passenger$...` 条件注册出来的“载人/骑乘”动作。
 * 这层只要命中一次，就会把大女仆切进类似骑摩托的姿态。
 *
 * <p>因此这里不再尝试依赖上层 controller 自己兜底，
 * 而是直接在 `ConditionalPassenger#doTest` 返回前把结果清空，
 * 让“大女仆抱小女仆”状态永远无法命中 passenger 条件动作。
 */
@Mixin(value = ConditionalPassenger.class, remap = false)
public abstract class ConditionalPassengerLiftMixin {
    @Inject(method = "doTest", at = @At("HEAD"), cancellable = true)
    private void maidmarriage$skipPassengerConditionForCarryAdult(Mob maid, CallbackInfoReturnable<String> cir) {
        if (maid instanceof EntityMaid entityMaid && MaidCarryChildManager.isCarryAdultState(entityMaid)) {
            cir.setReturnValue("");
        }
    }
}
