package com.example.maidmarriage.mixin;

import com.example.maidmarriage.client.CarryChildPoseDebug;
import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.GlWrapper;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import java.util.HashMap;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 大女仆抱小女仆的最终姿态覆盖。
 *
 * <p>服务端只负责把两只女仆锁在稳定位置；客户端这一层只做显示姿态：
 * 成年女仆负责“伸手托抱”，小女仆负责“横向被抱”。这样逻辑和视觉分离，
 * 后续微调距离、动作角度时不会再牵扯乘骑链或玩家动画。
 */
@Mixin(value = HardcodedAnimationManger.class, remap = false)
public abstract class HardcodedAnimationManagerCarryChildPoseMixin {
    /**
     * 小女仆被抱起时的整体视觉微调。
     *
     * <p>当前直接骑在大女仆身上后，基础挂点会让小女仆略微偏向身体外侧，
     * 看起来像“挂在肩膀边上”，而不是落在怀里。
     * 这里额外给一个很小的整体平移，只修正整体落点，不去破坏已经调好的四肢角度。
     */
    @Inject(method = "playMaidAnimation", at = @At("RETURN"))
    private static void maidmarriage$applyBedrockCarryChildPose(IMaid maid, HashMap<String, ModelRendererWrapper> models,
                                                                float limbSwing, float limbSwingAmount, float ageInTicks,
                                                                float netHeadYaw, float headPitch, CallbackInfo ci) {
        EntityMaid entityMaid = maid.asStrictMaid();
        if (entityMaid == null) {
            return;
        }
        if (MaidCarryChildManager.isAdultCarrier(entityMaid)) {
            applyBedrockAdultCarryPose(models);
            return;
        }
        if (MaidCarryChildManager.isCarriedChild(entityMaid)) {
            applyBedrockCarriedChildPose(models);
            return;
        }
    }

    @Inject(method = "playGeckoMaidAnimation", at = @At("RETURN"))
    private static void maidmarriage$applyGeckoCarryChildPose(IMaid maid, AnimatedGeoModel model,
                                                              float limbSwing, float limbSwingAmount, float ageInTicks,
                                                              float netHeadYaw, float headPitch, CallbackInfo ci) {
        EntityMaid entityMaid = maid.asStrictMaid();
        if (entityMaid == null) {
            return;
        }
        if (MaidCarryChildManager.isAdultCarrier(entityMaid)) {
            applyGeckoAdultCarryPose(model);
            return;
        }
        if (MaidCarryChildManager.isCarriedChild(entityMaid)) {
            applyGeckoCarriedChildPose(model);
            return;
        }
    }

    private static void applyBedrockAdultCarryPose(HashMap<String, ModelRendererWrapper> models) {
        ModelRendererWrapper body = models.get("body");
        ModelRendererWrapper upperBody = models.get("upperBody");
        ModelRendererWrapper head = models.get("head");
        ModelRendererWrapper armLeft = models.get("armLeft");
        ModelRendererWrapper armRight = models.get("armRight");
        ModelRendererWrapper armLeft2 = models.get("armLeft2");
        ModelRendererWrapper armRight2 = models.get("armRight2");
        ModelRendererWrapper legLeft = models.get("legLeft");
        ModelRendererWrapper legRight = models.get("legRight");

        /**
         * 大女仆的姿态重点改成“把孩子托在怀里”：
         * 上臂保持前伸但不过分抬高，手肘向下沉，
         * 让前臂自然停在胸腹前半段，形成“托着孩子”的感觉，
         * 而不是把整只手举到脸前挡住头部。
         */
        if (body != null) {
            body.setRotateAngleX(0.06f);
        }
        if (upperBody != null) {
            upperBody.setRotateAngleX(0.12f);
        }
        if (head != null) {
            head.setRotateAngleX(-0.02f);
            head.setRotateAngleY(0.0f);
            head.setRotateAngleZ(0.0f);
        }
        if (armLeft != null) {
            armLeft.setRotateAngleX(-0.94f);
            armLeft.setRotateAngleY(-0.18f);
            armLeft.setRotateAngleZ(-0.26f);
        }
        if (armRight != null) {
            armRight.setRotateAngleX(-0.94f);
            armRight.setRotateAngleY(0.18f);
            armRight.setRotateAngleZ(0.26f);
        }
        if (armLeft2 != null) {
            armLeft2.setRotateAngleX(-0.76f);
            armLeft2.setRotateAngleY(0.18f);
            armLeft2.setRotateAngleZ(0.08f);
        }
        if (armRight2 != null) {
            armRight2.setRotateAngleX(-0.76f);
            armRight2.setRotateAngleY(-0.18f);
            armRight2.setRotateAngleZ(-0.08f);
        }
        if (legLeft != null) {
            legLeft.setRotateAngleZ(-0.03f);
        }
        if (legRight != null) {
            legRight.setRotateAngleZ(0.03f);
        }
    }

    private static void applyBedrockCarriedChildPose(HashMap<String, ModelRendererWrapper> models) {
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

        if (root != null) {
            /**
             * Bedrock/东方模型和 GeckoLib 模型的根骨轴向不同：
             * Gecko 用 X=180° 是横躺，Bedrock 用同样角度会更像站/坐着。
             * 这里 Bedrock 单独使用 X=90°，Y/Z 继续沿用调好的朝向。
             */
            root.setRotateAngleX(deg(CarryChildPoseDebug.bedrockRotationX()));
            root.setRotateAngleY(deg(CarryChildPoseDebug.bedrockRotationY()));
            root.setRotateAngleZ(deg(CarryChildPoseDebug.bedrockRotationZ()));
        }
        /**
         * 小女仆被横抱时，局部骨骼尽量保持“身体舒展开、只有轻微蜷缩”的状态。
         *
         * 之前这里给上半身和小腿的弯折太大，叠加整体 root 旋转后，
         * 视觉上会变成“腰部往上拱、膝盖跪起来”的奇怪姿态。
         * 现在改成更平缓的弯曲，并进一步加强胸腹段的自然下垂。
         * 这样被抱时视觉重点会落在“上半身被托住、身体顺着手臂自然沉下去”，
         * 不会再像板直躺着。
         */
        if (body != null) {
            body.setRotateAngleX(deg(8.0f));
        }
        if (upperBody != null) {
            upperBody.setRotateAngleX(deg(-14.0f));
        }
        if (head != null) {
            head.setRotateAngleX(deg(14.0f));
            head.setRotateAngleY(0.0f);
            head.setRotateAngleZ(0.0f);
        }
        if (armLeft != null) {
            armLeft.setRotateAngleX(deg(-10.0f));
            armLeft.setRotateAngleY(deg(24.0f));
            armLeft.setRotateAngleZ(deg(-6.0f));
        }
        if (armRight != null) {
            armRight.setRotateAngleX(deg(-10.0f));
            armRight.setRotateAngleY(deg(-24.0f));
            armRight.setRotateAngleZ(deg(6.0f));
        }
        if (armLeft2 != null) {
            armLeft2.setRotateAngleX(deg(-36.0f));
        }
        if (armRight2 != null) {
            armRight2.setRotateAngleX(deg(-36.0f));
        }
        if (legLeft != null) {
            legLeft.setRotateAngleX(deg(14.0f));
            legLeft.setRotateAngleY(deg(4.0f));
            legLeft.setRotateAngleZ(deg(-2.0f));
        }
        if (legRight != null) {
            legRight.setRotateAngleX(deg(14.0f));
            legRight.setRotateAngleY(deg(-4.0f));
            legRight.setRotateAngleZ(deg(2.0f));
        }
        if (legLeft2 != null) {
            legLeft2.setRotateAngleX(deg(12.0f));
        }
        if (legRight2 != null) {
            legRight2.setRotateAngleX(deg(12.0f));
        }
        if (root != null) {
            /**
             * Bedrock/东方模型的根骨坐标和 GeckoLib 不同，不能直接复用
             * F8 面板里为 Gecko 调好的大负数偏移，否则会出现“小女仆坐到头上”的情况。
             */
            root.setOffsetX(CarryChildPoseDebug.bedrockShiftX());
            root.setOffsetY(CarryChildPoseDebug.bedrockShiftY());
            root.setOffsetZ(CarryChildPoseDebug.bedrockShiftZ());
        }
        GlWrapper.translate(
                CarryChildPoseDebug.bedrockTranslateX(),
                -0.08D + CarryChildPoseDebug.bedrockTranslateY(),
                CarryChildPoseDebug.bedrockTranslateZ()
        );
    }

    private static void applyGeckoAdultCarryPose(AnimatedGeoModel model) {
        AnimatedGeoBone allBody = findBone(model, "AllBody", "allBody", "Body", "body");
        AnimatedGeoBone upBody = findBone(model, "UpBody", "upBody");
        AnimatedGeoBone upperBody = findBone(model, "UpperBody", "upperBody");
        AnimatedGeoBone head = findBone(model, "Head", "head");
        AnimatedGeoBone leftArm = findBone(model, "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        AnimatedGeoBone rightArm = findBone(model, "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        AnimatedGeoBone leftForeArm = findBone(model, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm");
        AnimatedGeoBone rightForeArm = findBone(model, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm");

        /**
         * GeckoLib 分支和 Bedrock 分支保持一致：
         * 让大女仆的双臂形成自然托抱：
         * 手肘偏下、手臂停在胸口一半左右，不挡脸。
         */
        if (allBody != null) {
            allBody.setRotationX(0.06f);
            allBody.setRotationY(0.0f);
            allBody.setRotationZ(0.0f);
        }
        if (upBody != null) {
            upBody.setRotationX(0.10f);
        }
        if (upperBody != null) {
            upperBody.setRotationX(0.12f);
        }
        if (head != null) {
            head.setRotationX(-0.02f);
            head.setRotationY(0.0f);
            head.setRotationZ(0.0f);
        }
        if (leftArm != null) {
            leftArm.setRotationX(0.94f);
            leftArm.setRotationY(0.06f);
            leftArm.setRotationZ(-0.26f);
        }
        if (rightArm != null) {
            rightArm.setRotationX(0.94f);
            rightArm.setRotationY(-0.06f);
            rightArm.setRotationZ(0.26f);
        }
        if (leftForeArm != null) {
            leftForeArm.setRotationX(0.76f);
            leftForeArm.setRotationY(0.14f);
            leftForeArm.setRotationZ(0.08f);
        }
        if (rightForeArm != null) {
            rightForeArm.setRotationX(0.76f);
            rightForeArm.setRotationY(-0.14f);
            rightForeArm.setRotationZ(-0.08f);
        }
    }

    private static void applyGeckoCarriedChildPose(AnimatedGeoModel model) {
        AnimatedGeoBone root = findBone(model, "Root", "root");
        AnimatedGeoBone upBody = findBone(model, "UpBody", "upBody");
        AnimatedGeoBone upperBody = findBone(model, "UpperBody", "upperBody");
        AnimatedGeoBone head = findBone(model, "Head", "head");
        AnimatedGeoBone leftArm = findBone(model, "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        AnimatedGeoBone rightArm = findBone(model, "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        AnimatedGeoBone leftForeArm = findBone(model, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm");
        AnimatedGeoBone rightForeArm = findBone(model, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm");
        AnimatedGeoBone leftLeg = findBone(model, "LeftLeg", "leftLeg", "legLeft", "LegLeft", "left_leg");
        AnimatedGeoBone rightLeg = findBone(model, "RightLeg", "rightLeg", "legRight", "LegRight", "right_leg");
        AnimatedGeoBone leftLowerLeg = findBone(model, "LeftLowerLeg", "leftLowerLeg", "legLeft2", "left_lower_leg");
        AnimatedGeoBone rightLowerLeg = findBone(model, "RightLowerLeg", "rightLowerLeg", "legRight2", "right_lower_leg");

        if (root != null) {
            root.setRotationX(deg(CarryChildPoseDebug.rotationX()));
            root.setRotationY(deg(CarryChildPoseDebug.rotationY()));
            root.setRotationZ(deg(CarryChildPoseDebug.rotationZ()));
        }
        /**
         * GeckoLib 分支与 Bedrock 分支保持同一套姿态意图：
         * 让上半身更明显地下垂一点，做出被公主抱托着时
         * 胸腹自然放松下沉的感觉。
         */
        if (upBody != null) {
            upBody.setRotationX(deg(-14.0f));
        }
        if (upperBody != null) {
            upperBody.setRotationX(deg(-14.0f));
        }
        if (head != null) {
            head.setRotationX(deg(14.0f));
            head.setRotationY(0.0f);
            head.setRotationZ(0.0f);
        }
        if (leftArm != null) {
            leftArm.setRotationX(deg(-10.0f));
            leftArm.setRotationY(deg(24.0f));
            leftArm.setRotationZ(deg(-6.0f));
        }
        if (rightArm != null) {
            rightArm.setRotationX(deg(-10.0f));
            rightArm.setRotationY(deg(-24.0f));
            rightArm.setRotationZ(deg(6.0f));
        }
        if (leftForeArm != null) {
            leftForeArm.setRotationX(deg(-36.0f));
        }
        if (rightForeArm != null) {
            rightForeArm.setRotationX(deg(-36.0f));
        }
        if (leftLeg != null) {
            leftLeg.setRotationX(deg(14.0f));
            leftLeg.setRotationY(deg(4.0f));
            leftLeg.setRotationZ(deg(-2.0f));
        }
        if (rightLeg != null) {
            rightLeg.setRotationX(deg(14.0f));
            rightLeg.setRotationY(deg(-4.0f));
            rightLeg.setRotationZ(deg(2.0f));
        }
        if (leftLowerLeg != null) {
            leftLowerLeg.setRotationX(deg(12.0f));
        }
        if (rightLowerLeg != null) {
            rightLowerLeg.setRotationX(deg(12.0f));
        }
        if (root != null) {
            root.setPositionX(CarryChildPoseDebug.shiftX());
            root.setPositionY(CarryChildPoseDebug.shiftY());
            root.setPositionZ(CarryChildPoseDebug.shiftZ());
        }
    }

    private static float deg(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
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
