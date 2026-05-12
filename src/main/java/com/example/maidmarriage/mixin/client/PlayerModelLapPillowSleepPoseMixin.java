package com.example.maidmarriage.mixin.client;

import com.example.maidmarriage.client.LapPillowClientState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 膝枕睡姿模型兜底。
 *
 * <p>部分模型动画会在玩家睡姿之后继续改四肢，这里在尾部重置成人形睡姿的干净底座。
 */
@Mixin(value = PlayerModel.class, priority = 500)
public abstract class PlayerModelLapPillowSleepPoseMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
    private void maidmarriage$normalizeLapPillowSleepPose(T entity,
                                                          float limbSwing,
                                                          float limbSwingAmount,
                                                          float ageInTicks,
                                                          float netHeadYaw,
                                                          float headPitch,
                                                          CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)
                || LapPillowClientState.renderingDepth <= 0
                || !LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        model.head.resetPose();
        model.body.resetPose();
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.rightLeg.resetPose();
        model.leftLeg.resetPose();
        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        model.jacket.copyFrom(model.body);
    }
}
