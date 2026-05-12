package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidHugManager;
import com.example.maidmarriage.client.LapPillowClientState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家“站立拥抱”动作层。
 *
 * <p>第三层先做一个稳定的通用姿势：身体微微前倾，双臂向前收拢。
 * 这样本地与远端玩家都能看到“正在拥抱”的动作变化。
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelHugPoseMixin<T extends LivingEntity> extends HumanoidModel<T> {
    protected PlayerModelHugPoseMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void maidmarriage$applyHugPose(T entity, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch,
                                           CallbackInfo ci) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (player instanceof AbstractClientPlayer clientPlayer
                && LapPillowClientState.isPlayerActive(player)
                && !LapPillowClientState.shouldUseSleepPoseBridge(clientPlayer)) {
            applyLapPillowNeutralPose();
            return;
        }
        if (!MaidHugManager.isClientPlayerHugPoseVisible(player)) {
            return;
        }

        float progress = MaidHugManager.clientHugPoseProgress(player);
        float openProgress = delayedOpenProgress(progress);

        this.body.xRot += 0.10f * progress;
        if (this.head.xRot > 0.10f) {
            this.head.xRot = lerp(this.head.xRot, 0.10f, progress);
        }

        this.rightArm.xRot = lerp(this.rightArm.xRot, -1.25f, progress);
        this.rightArm.yRot = lerp(this.rightArm.yRot, -0.62f, openProgress);
        this.rightArm.zRot = lerp(this.rightArm.zRot, 0.24f, openProgress);

        this.leftArm.xRot = lerp(this.leftArm.xRot, -1.25f, progress);
        this.leftArm.yRot = lerp(this.leftArm.yRot, 0.62f, openProgress);
        this.leftArm.zRot = lerp(this.leftArm.zRot, -0.24f, openProgress);

        this.rightLeg.xRot = lerp(this.rightLeg.xRot, this.rightLeg.xRot * 0.25f, progress);
        this.leftLeg.xRot = lerp(this.leftLeg.xRot, this.leftLeg.xRot * 0.25f, progress);
    }

    private float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    /**
     * 拥抱入场分成两段：
     * 1. 先把手臂抬起来；
     * 2. 中间略停一下，再向左右展开。
     */
    private float delayedOpenProgress(float progress) {
        if (progress <= 0.46f) {
            return 0.0f;
        }
        float local = (progress - 0.46f) / 0.54f;
        local = Math.max(0.0f, Math.min(1.0f, local));
        return local * local * (3.0f - 2.0f * local);
    }

    /**
     * 膝枕睡姿桥未接管时的兜底姿态。
     * 正常情况下会由 client.PlayerModelLapPillowSleepPoseMixin 接原版睡姿；
     * 这里仅避免极端情况下跑步/挥手动画残留在横躺身体上。
     */
    private void applyLapPillowNeutralPose() {
        this.head.xRot = 0.0F;
        this.head.yRot = 0.0F;
        this.head.zRot = 0.0F;
        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;
        this.rightArm.xRot = 0.0F;
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.leftArm.xRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        this.leftArm.zRot = 0.0F;
        this.rightLeg.xRot = 0.0F;
        this.rightLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
        this.leftLeg.xRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.leftLeg.zRot = 0.0F;
    }
}
