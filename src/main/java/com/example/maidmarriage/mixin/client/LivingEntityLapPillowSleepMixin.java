package com.example.maidmarriage.mixin.client;

import com.example.maidmarriage.client.LapPillowClientState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 渲染期睡觉状态桥。
 *
 * <p>只在实体渲染调用栈中让玩家“看起来正在睡觉”，避免打开原版睡觉界面或黑屏逻辑。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLapPillowSleepMixin {
    @Inject(method = "isSleeping()Z", at = @At("HEAD"), cancellable = true)
    private void maidmarriage$bridgeSleepingState(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.renderingDepth > 0
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getBedOrientation()Lnet/minecraft/core/Direction;", at = @At("HEAD"), cancellable = true)
    private void maidmarriage$bridgeBedOrientation(CallbackInfoReturnable<Direction> cir) {
        if ((Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.renderingDepth > 0
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(LapPillowClientState.resolveSleepDirection(player));
        }
    }

    @Inject(method = "isFallFlying()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$disableFallFlyingForLapPillow(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isVisuallySwimming()Z", at = @At("HEAD"), cancellable = true)
    private void maidmarriage$disableVisualSwimmingForLapPillow(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.renderingDepth > 0
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(false);
        }
    }
}
