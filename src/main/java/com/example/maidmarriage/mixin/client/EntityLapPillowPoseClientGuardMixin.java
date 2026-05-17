package com.example.maidmarriage.mixin.client;

import com.example.maidmarriage.client.LapPillowClientState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 膝枕客户端姿态桥与姿态护栏。
 *
 * <p>真实交互状态仍然交给我们的 UI 系统；这里仅让渲染和模型动画认为玩家处于睡姿。
 */
@Mixin(Entity.class)
public abstract class EntityLapPillowPoseClientGuardMixin {
    @Inject(method = "setPose(Lnet/minecraft/world/entity/Pose;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$guardClientPose(Pose pose, CallbackInfo ci) {
        guardPoseWrite(pose, ci);
    }

    @Inject(method = "hasPose(Lnet/minecraft/world/entity/Pose;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$bridgeSleepingHasPose(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        if (pose == Pose.SLEEPING
                && (Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.renderingDepth > 0
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPose()Lnet/minecraft/world/entity/Pose;", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$bridgeSleepingGetPose(CallbackInfoReturnable<Pose> cir) {
        if ((Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(LapPillowClientState.renderingDepth > 0 ? Pose.SLEEPING : Pose.STANDING);
        }
    }

    @Inject(method = "onGround()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$bridgeLapPillowOnGround(CallbackInfoReturnable<Boolean> cir) {
        /*
         * 部分玩家模型会根据“是否离地”切换动作。
         * 膝枕期间位置由服务端锁定，视觉上应当视为稳定落地，避免被模型包误判成腾空。
         */
        if ((Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isSwimming()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$disableSwimmingForLapPillow(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractClientPlayer player
                && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            cir.setReturnValue(false);
        }
    }

    private void guardPoseWrite(Pose pose, CallbackInfo ci) {
        if (pose == Pose.SLEEPING || !((Object) this instanceof AbstractClientPlayer player)) {
            return;
        }
        if (LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            ci.cancel();
        }
    }
}
