package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.example.maidmarriage.compat.MaidLiftManager;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationManager;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 举高高期间跳过 TLM 自带的载具动画选择。
 *
 * <p>这条链路主要影响普通 Gecko 模型。
 * YSM 举高高现在明确复用模型包自己的 riding / ride_pig，
 * 因此如果当前女仆是 YSM 模型，就不能在这里取消载具动画。
 */
@Mixin(value = AnimationManager.class, remap = false)
public abstract class AnimationManagerLiftVehicleMixin {
    @Inject(method = "getVehicleAnimation", at = @At("HEAD"), cancellable = true)
    private void maidmarriage$skipVehicleAnimationWhenLifted(AnimationEvent<GeckoMaidEntity<?>> event,
                                                             CallbackInfoReturnable<PlayState> cir) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return;
        }
        if (!(maid.asEntity() instanceof EntityMaid entityMaid)) {
            return;
        }
        Player player = MaidLiftManager.getLiftPlayer(entityMaid);
        if ((!entityMaid.isYsmModel() && player != null && MaidLiftManager.isLiftState(entityMaid, player))
                || MaidCarryChildManager.isCarryChildState(entityMaid)
                || MaidCarryChildManager.isCarryAdultState(entityMaid)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "predicatePassengerAnimation", at = @At("HEAD"), cancellable = true)
    private void maidmarriage$skipPassengerAnimationWhenCarryingChild(AnimationEvent<GeckoMaidEntity<?>> event,
                                                                      CallbackInfoReturnable<PlayState> cir) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (maid == null) {
            return;
        }
        if (!(maid.asEntity() instanceof EntityMaid entityMaid)) {
            return;
        }
        /*
         * 大女仆抱小女仆时，成年女仆本身会成为代理实体的 vehicle。
         * TLM 原版会把“身上有乘客”的女仆交给 passenger 条件动画处理，
         * 很多模型包会把这条分支做成骑车/载具动作，所以这里必须在最早入口跳过。
         */
        if (MaidCarryChildManager.isCarryAdultState(entityMaid)
                || MaidCarryChildManager.isCarryChildState(entityMaid)) {
            cir.setReturnValue(PlayState.STOP);
        }
    }
}
