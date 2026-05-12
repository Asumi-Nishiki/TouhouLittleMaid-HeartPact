package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.LapPillowManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 膝枕服务端姿态护栏。
 *
 * <p>一些模组或原版逻辑会在 tick 中把玩家姿态写回站立、游泳、爬行。
 * 膝枕会话期间我们只接受 {@link Pose#SLEEPING}，直到 LapPillowManager 主动结束会话。
 */
@Mixin(Entity.class)
public abstract class EntityLapPillowPoseServerGuardMixin {
    @Inject(method = "setForcedPose", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$guardServerForcedPose(Pose pose, CallbackInfo ci) {
        guardPoseWrite(pose, ci);
    }

    @Inject(method = "setPose(Lnet/minecraft/world/entity/Pose;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void maidmarriage$guardServerPose(Pose pose, CallbackInfo ci) {
        guardPoseWrite(pose, ci);
    }

    private void guardPoseWrite(Pose pose, CallbackInfo ci) {
        if (pose == Pose.SLEEPING || !((Object) this instanceof ServerPlayer player)) {
            return;
        }
        if (LapPillowManager.isPlayerInLapPillow(player)) {
            ci.cancel();
        }
    }
}
