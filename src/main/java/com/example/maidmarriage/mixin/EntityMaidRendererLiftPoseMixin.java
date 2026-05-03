package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.example.maidmarriage.compat.MaidLiftManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 举高高专用渲染姿态混入。
 *
 * <p>原版 TLM 只要检测到“女仆骑在玩家身上”，就会在渲染阶段强制套用公主抱旋转。
 * 这会把我们前面设置的坐姿视觉完全盖掉，所以看起来始终还是公主抱。
 *
 * <p>这里在“举高高状态”下接管 setupRotations：
 * 1. 保留实体基础朝向；
 * 2. 保留 TLM 其它硬编码旋转；
 * 3. 跳过那段公主抱专用旋转。
 *
 * <p>这样举高高时就能显示普通朝向下的坐姿，而不是横着被抱起。
 */
@Mixin(EntityMaidRenderer.class)
public abstract class EntityMaidRendererLiftPoseMixin extends MobRenderer<Mob, BedrockModel<Mob>> {
    @Shadow(remap = false)
    private MaidModelInfo mainInfo;

    protected EntityMaidRendererLiftPoseMixin(EntityRendererProvider.Context context, BedrockModel<Mob> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(
            method = "setupRotations(Lnet/minecraft/world/entity/Mob;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void maidmarriage$useLiftPoseInsteadOfPrincessCarry(Mob mob, PoseStack poseStack,
                                                                 float ageInTicks, float rotationYaw, float partialTicks,
                                                                 CallbackInfo ci) {
        if (!(mob instanceof EntityMaid maid)) {
            return;
        }
        Player player = MaidLiftManager.getLiftPlayer(maid);
        if (!MaidLiftManager.isLiftState(maid, player)
                && !MaidCarryChildManager.isCarryChildState(maid)
                && !MaidCarryChildManager.isCarryAdultState(maid)) {
            return;
        }
        super.setupRotations(mob, poseStack, ageInTicks, rotationYaw, partialTicks);
        boolean isGeckoModel = this.mainInfo != null && this.mainInfo.isGeckoModel();
        if (!isGeckoModel && MaidCarryChildManager.isCarriedChild(maid)) {
            /**
             * 东方/Bedrock 模型在骑乘状态下会先被底层套成坐姿。
             *
             * <p>单纯转 root 骨骼只能改变骨骼内部姿态，压不倒整个渲染实体，
             * 所以看起来还是“坐着”。这里在渲染旋转层额外把小女仆整体放倒，
             * 并额外绕 Z 轴侧转 90°，尝试把“脚朝前”的平面改成横过来。
             * GeckoLib 分支不走这里，因为 Gecko 那套姿态已经正常。
             */
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }

        HardcodedAnimationManger.setupRotations(
                mob,
                poseStack,
                ageInTicks,
                rotationYaw,
                partialTicks,
                isGeckoModel
        );
        ci.cancel();
    }
}
