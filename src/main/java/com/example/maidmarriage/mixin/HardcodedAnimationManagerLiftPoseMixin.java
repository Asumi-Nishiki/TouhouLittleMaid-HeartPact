package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidLiftManager;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.GlWrapper;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import java.util.HashMap;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 举高高专用的硬编码姿态覆盖。
 *
 * <p>普通模型和 Gecko 模型都会在原动画跑完之后，再额外覆写核心骨骼姿态。
 * 这样即使底层还保留“骑乘玩家”这一实体关系，也不会继续显示成公主抱。
 */
@Mixin(value = HardcodedAnimationManger.class, remap = false)
public abstract class HardcodedAnimationManagerLiftPoseMixin {
    @Inject(method = "playMaidAnimation", at = @At("RETURN"))
    private static void maidmarriage$applyLiftPose(IMaid maid, HashMap<String, ModelRendererWrapper> models,
                                                   float limbSwing, float limbSwingAmount, float ageInTicks,
                                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        EntityMaid entityMaid = maid.asStrictMaid();
        if (entityMaid == null) {
            return;
        }
        Player player = MaidLiftManager.getLiftPlayer(entityMaid);
        if (!MaidLiftManager.isLiftState(entityMaid, player)) {
            return;
        }

        applyBedrockLiftPose(models);
        GlWrapper.translate(0, 0.3, 0);
    }

    @Inject(method = "playGeckoMaidAnimation", at = @At("RETURN"))
    private static void maidmarriage$applyGeckoLiftPose(IMaid maid, AnimatedGeoModel model,
                                                        float limbSwing, float limbSwingAmount, float ageInTicks,
                                                        float netHeadYaw, float headPitch, CallbackInfo ci) {
        EntityMaid entityMaid = maid.asStrictMaid();
        if (entityMaid == null) {
            return;
        }
        Player player = MaidLiftManager.getLiftPlayer(entityMaid);
        if (!MaidLiftManager.isLiftState(entityMaid, player)) {
            return;
        }

        applyGeckoLiftPose(model);
    }

    /**
     * 对普通 Bedrock 女仆模型施加举高高姿态。
     */
    private static void applyBedrockLiftPose(HashMap<String, ModelRendererWrapper> models) {
        ModelRendererWrapper root = models.get("root");
        ModelRendererWrapper body = models.get("body");
        ModelRendererWrapper upperBody = models.get("upperBody");
        ModelRendererWrapper head = models.get("head");
        ModelRendererWrapper armLeft = models.get("armLeft");
        ModelRendererWrapper armRight = models.get("armRight");
        ModelRendererWrapper armLeft2 = models.get("armLeft2");
        ModelRendererWrapper armRight2 = models.get("armRight2");
        ModelRendererWrapper legLeft = models.get("legLeft");
        ModelRendererWrapper legRight = models.get("legRight");
        ModelRendererWrapper legLeft2 = models.get("legLeft2");
        ModelRendererWrapper legRight2 = models.get("legRight2");
        ModelRendererWrapper skirt = models.get("skirt");
        ModelRendererWrapper sittingRotationSkirt = models.get("sittingRotationSkirt");
        ModelRendererWrapper sittingRotationSwingSkirt = models.get("sittingRotationSwingSkirt");

        resetRotation(root);
        if (body != null) {
            body.setRotateAngleX(0.0f);
        }
        if (upperBody != null) {
            upperBody.setRotateAngleX(0.17f);
        }
        if (head != null) {
            head.setOffsetY(0.0f);
            head.setRotateAngleX(-0.17f);
            head.setRotateAngleY(0.0f);
            head.setRotateAngleZ(0.0f);
        }
        if (armLeft != null) {
            armLeft.setRotateAngleX(-0.65f);
            armLeft.setRotateAngleY(0.24f);
            armLeft.setRotateAngleZ(0.25f);
        }
        if (armRight != null) {
            armRight.setRotateAngleX(-0.65f);
            armRight.setRotateAngleY(-0.24f);
            armRight.setRotateAngleZ(-0.25f);
        }
        if (armLeft2 != null) {
            armLeft2.setRotateAngleX(-0.57f);
        }
        if (armRight2 != null) {
            armRight2.setRotateAngleX(-0.57f);
        }
        /*
         * 新春酒狐原生 ride_pig 坐姿参考：
         * LeftLeg/RightLeg = -75°，LeftLowerLeg/RightLowerLeg = 72.5°，Foot = 20°。
         *
         * 上一版为了表现“岔开”叠了过多 Y/Z 轴，YSM 上会像腿被折成一团。
         * 这里回到模型包自己的坐姿逻辑，只保留很小的左右外展，
         * 让它更像坐在玩家头上，而不是跪着或蜷成球。
         */
        if (legLeft != null) {
            legLeft.setRotateAngleX(-1.31f);
            legLeft.setRotateAngleY(-0.08f);
            legLeft.setRotateAngleZ(-0.04f);
        }
        if (legRight != null) {
            legRight.setRotateAngleX(-1.31f);
            legRight.setRotateAngleY(0.08f);
            legRight.setRotateAngleZ(0.04f);
        }
        if (legLeft2 != null) {
            legLeft2.setRotateAngleX(1.27f);
        }
        if (legRight2 != null) {
            legRight2.setRotateAngleX(1.27f);
        }
        if (models.get("footLeft") != null) {
            models.get("footLeft").setRotateAngleX(0.35f);
        }
        if (models.get("footRight") != null) {
            models.get("footRight").setRotateAngleX(0.35f);
        }
        if (skirt != null) {
            skirt.setRotateAngleX(-0.35f);
        }
        if (sittingRotationSkirt != null) {
            sittingRotationSkirt.setRotateAngleX(-0.48f);
        }
        if (sittingRotationSwingSkirt != null) {
            sittingRotationSwingSkirt.setRotateAngleX(-0.48f);
            sittingRotationSwingSkirt.setRotateAngleZ(sittingRotationSwingSkirt.getInitRotateAngleZ());
        }
    }

    /**
     * 对 Gecko / YSM 模型施加核心骨通用姿态。
     *
     * <p>这里只改头、躯干、四肢、裙摆等核心骨骼，
     * 尽量兼容不同 YSM 模型对扩展骨骼的差异。
     */
    private static void applyGeckoLiftPose(AnimatedGeoModel model) {
        AnimatedGeoBone root = findBone(model, "Root", "root");
        AnimatedGeoBone allBody = findBone(model, "AllBody", "allBody", "Body", "body");
        AnimatedGeoBone upBody = findBone(model, "UpBody", "upBody");
        AnimatedGeoBone upperBody = findBone(model, "UpperBody", "upperBody");
        AnimatedGeoBone head = findBone(model, "Head", "head");
        AnimatedGeoBone leftArm = findBone(model, "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        AnimatedGeoBone leftForeArm = findBone(model, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm");
        AnimatedGeoBone rightArm = findBone(model, "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        AnimatedGeoBone rightForeArm = findBone(model, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm");
        AnimatedGeoBone leftLeg = findBone(model, "LeftLeg", "leftLeg", "legLeft", "LegLeft", "left_leg");
        AnimatedGeoBone leftLowerLeg = findBone(model, "LeftLowerLeg", "leftLowerLeg", "left_lower_leg");
        AnimatedGeoBone leftFoot = findBone(model, "LeftFoot", "leftFoot", "left_foot");
        AnimatedGeoBone rightLeg = findBone(model, "RightLeg", "rightLeg", "legRight", "LegRight", "right_leg");
        AnimatedGeoBone rightLowerLeg = findBone(model, "RightLowerLeg", "rightLowerLeg", "right_lower_leg");
        AnimatedGeoBone rightFoot = findBone(model, "RightFoot", "rightFoot", "right_foot");
        AnimatedGeoBone skirt = findBone(model, "Skirt", "skirt");
        AnimatedGeoBone frontSkirt = findBone(model, "FrontSkirt", "frontSkirt");
        AnimatedGeoBone leftSkirt = findBone(model, "LeftSkirt", "leftSkirt");
        AnimatedGeoBone rightSkirt = findBone(model, "RightSkirt", "rightSkirt");

        resetRotation(root);
        if (allBody != null) {
            allBody.setRotationX(0.0f);
        }
        if (upBody != null) {
            upBody.setRotationX(0.17f);
        }
        if (upperBody != null) {
            upperBody.setRotationX(0.0f);
        }
        if (head != null) {
            head.setPositionY(0.0f);
            head.setRotationX(-0.17f);
            head.setRotationY(0.0f);
            head.setRotationZ(0.0f);
        }
        if (leftArm != null) {
            leftArm.setRotationX(-0.65f);
            leftArm.setRotationY(0.24f);
            leftArm.setRotationZ(0.25f);
        }
        if (rightArm != null) {
            rightArm.setRotationX(-0.65f);
            rightArm.setRotationY(-0.24f);
            rightArm.setRotationZ(-0.25f);
        }
        if (leftForeArm != null) {
            leftForeArm.setRotationX(-0.57f);
        }
        if (rightForeArm != null) {
            rightForeArm.setRotationX(-0.57f);
        }
        if (leftLeg != null) {
            leftLeg.setRotationX(-1.31f);
            leftLeg.setRotationY(-0.08f);
            leftLeg.setRotationZ(-0.04f);
        }
        if (rightLeg != null) {
            rightLeg.setRotationX(-1.31f);
            rightLeg.setRotationY(0.08f);
            rightLeg.setRotationZ(0.04f);
        }
        if (leftLowerLeg != null) {
            leftLowerLeg.setRotationX(1.27f);
        }
        if (rightLowerLeg != null) {
            rightLowerLeg.setRotationX(1.27f);
        }
        if (leftFoot != null) {
            leftFoot.setRotationX(0.35f);
        }
        if (rightFoot != null) {
            rightFoot.setRotationX(0.35f);
        }
        if (skirt != null) {
            skirt.setRotationX(-0.35f);
        }
        if (frontSkirt != null) {
            frontSkirt.setRotationX(-0.48f);
        }
        if (leftSkirt != null) {
            leftSkirt.setRotationZ(-0.04f);
        }
        if (rightSkirt != null) {
            rightSkirt.setRotationZ(0.04f);
        }
    }

    private static void resetRotation(ModelRendererWrapper modelPart) {
        if (modelPart == null) {
            return;
        }
        modelPart.setRotateAngleX(0.0f);
        modelPart.setRotateAngleY(0.0f);
        modelPart.setRotateAngleZ(0.0f);
    }

    private static void resetRotation(AnimatedGeoBone bone) {
        if (bone == null) {
            return;
        }
        bone.setRotationX(0.0f);
        bone.setRotationY(0.0f);
        bone.setRotationZ(0.0f);
    }

    private static AnimatedGeoBone findBone(AnimatedGeoModel model, String... names) {
        for (String name : names) {
            AnimatedGeoBone bone = model.bones().get(name);
            if (bone != null) {
                return bone;
            }
        }
        return null;
    }
}
