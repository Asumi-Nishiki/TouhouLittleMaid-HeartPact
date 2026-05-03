package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidHugManager;
import com.example.maidmarriage.compat.PetHeadManager;
import com.example.maidmarriage.client.HugClientState;
import com.example.maidmarriage.client.hugui.actions.StoryChestTapPoseLibrary;
import com.example.maidmarriage.client.hugui.actions.StoryHeadCuePoseLibrary;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import java.util.HashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 成年女仆站立拥抱姿势覆盖。
 *
 * <p>拥抱本身是一个静态的面对面姿势，因此这里在硬编码动画的最后一层统一覆盖骨骼。
 * 当拥抱中再触发摸头时，会继续在这个最终骨骼层叠加“轻摸头”版本：
 * 幅度更小、时长更短、不再额外带动身体。
 */
@Mixin(value = com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger.class, remap = false)
public abstract class HardcodedAnimationManagerHugPoseMixin {
    @Inject(method = "playMaidAnimation", at = @At("RETURN"))
    private static void maidmarriage$applyBedrockHugPose(IMaid maid, HashMap<String, ModelRendererWrapper> models,
                                                         float limbSwing, float limbSwingAmount, float ageInTicks,
                                                         float netHeadYaw, float headPitch, CallbackInfo ci) {
        EntityMaid entityMaid = maid.asStrictMaid();
        if (entityMaid == null) {
            return;
        }
        Player player = MaidHugManager.getHugPlayer(entityMaid);
        float hugPoseProgress = MaidHugManager.clientHugPoseProgress(player);
        if (!MaidHugManager.isHugState(entityMaid, player) && hugPoseProgress <= 0.0F) {
            applyBedrockStandalonePostKissShyPose(entityMaid, models);
            applyBedrockStandaloneHeadCuePose(entityMaid, models);
            applyBedrockStandaloneShyCoverFacePose(entityMaid, models);
            applyBedrockShyPeekPose(entityMaid, models, false);
            applyBedrockStandaloneChestTapPose(entityMaid, models);
            return;
        }
        applyBedrockHugPose(entityMaid, models, ageInTicks, hugPoseProgress);
    }

    @Inject(method = "playGeckoMaidAnimation", at = @At("RETURN"))
    private static void maidmarriage$applyGeckoHugPose(IMaid maid, AnimatedGeoModel model,
                                                       float limbSwing, float limbSwingAmount, float ageInTicks,
                                                       float netHeadYaw, float headPitch, CallbackInfo ci) {
        EntityMaid entityMaid = maid.asStrictMaid();
        if (entityMaid == null) {
            return;
        }
        Player player = MaidHugManager.getHugPlayer(entityMaid);
        float hugPoseProgress = MaidHugManager.clientHugPoseProgress(player);
        if (!MaidHugManager.isHugState(entityMaid, player) && hugPoseProgress <= 0.0F) {
            applyGeckoStandalonePostKissShyPose(entityMaid, model);
            applyGeckoStandaloneHeadCuePose(entityMaid, model);
            applyGeckoStandaloneShyCoverFacePose(entityMaid, model);
            applyGeckoShyPeekPose(entityMaid, model, false);
            applyGeckoStandaloneChestTapPose(entityMaid, model);
            return;
        }
        applyGeckoHugPose(entityMaid, model, ageInTicks, hugPoseProgress);
    }

    private static void applyBedrockHugPose(EntityMaid maid, HashMap<String, ModelRendererWrapper> models,
                                            float ageInTicks, float progress) {
        float openProgress = delayedOpenProgress(progress);
        ModelRendererWrapper body = models.get("body");
        ModelRendererWrapper upperBody = models.get("upperBody");
        ModelRendererWrapper head = models.get("head");
        ModelRendererWrapper neck = models.get("neck");
        ModelRendererWrapper armLeft = models.get("armLeft");
        ModelRendererWrapper armRight = models.get("armRight");
        ModelRendererWrapper armLeft2 = models.get("armLeft2");
        ModelRendererWrapper armRight2 = models.get("armRight2");
        ModelRendererWrapper shoulderLeft = models.get("shoulderLeft");
        ModelRendererWrapper shoulderRight = models.get("shoulderRight");
        ModelRendererWrapper legLeft = models.get("legLeft");
        ModelRendererWrapper legRight = models.get("legRight");
        ModelRendererWrapper legLeft2 = models.get("legLeft2");
        ModelRendererWrapper legRight2 = models.get("legRight2");
        ModelRendererWrapper footLeft = models.get("footLeft");
        ModelRendererWrapper footRight = models.get("footRight");
        ModelRendererWrapper skirt = models.get("skirt");

        if (body != null) {
            body.setRotateAngleX(0.10f * progress);
        }
        if (upperBody != null) {
            upperBody.setRotateAngleX(0.18f * progress);
        }
        if (head != null) {
            head.setRotateAngleX(-0.06f * progress);
            head.setRotateAngleY(0.14f * progress);
            head.setRotateAngleZ(0.04f * progress);
            applyBedrockHugPetHeadPose(maid, head, ageInTicks);
            applyBedrockPostKissShyPose(maid, head);
            applyBedrockHugShyCoverFacePose(maid, models);
        }
        if (neck != null) {
            neck.setRotateAngleX(0.05f * progress);
        }
        /*
         * Bedrock / 东方模型这条链原先张得太夸张，
         * 看起来更像“把手臂掰到两边”而不是自然环抱。
         *
         * 这里把肩膀和大臂外展幅度收一点，
         * 同时保留前臂的轻微反向回扣，形成抱住玩家的弧线。
         */
        if (shoulderLeft != null) {
            shoulderLeft.setRotateAngleX(0.16f * progress);
            shoulderLeft.setRotateAngleY(-0.20f * openProgress);
            shoulderLeft.setRotateAngleZ(-0.15f * openProgress);
        }
        if (shoulderRight != null) {
            shoulderRight.setRotateAngleX(0.16f * progress);
            shoulderRight.setRotateAngleY(0.20f * openProgress);
            shoulderRight.setRotateAngleZ(0.15f * openProgress);
        }
        if (armLeft != null) {
            armLeft.setRotateAngleX(-1.28f * progress);
            armLeft.setRotateAngleY(-0.62f * openProgress);
            armLeft.setRotateAngleZ(-0.56f * openProgress);
        }
        if (armRight != null) {
            armRight.setRotateAngleX(-1.28f * progress);
            armRight.setRotateAngleY(0.62f * openProgress);
            armRight.setRotateAngleZ(0.56f * openProgress);
        }
        if (armLeft2 != null) {
            armLeft2.setRotateAngleX(-0.52f * progress);
            armLeft2.setRotateAngleY(0.30f * openProgress);
            armLeft2.setRotateAngleZ(0.24f * openProgress);
        }
        if (armRight2 != null) {
            armRight2.setRotateAngleX(-0.52f * progress);
            armRight2.setRotateAngleY(-0.30f * openProgress);
            armRight2.setRotateAngleZ(-0.24f * openProgress);
        }
        if (legLeft != null) {
            legLeft.setRotateAngleX(0.02f * progress);
            legLeft.setRotateAngleY(0.02f * progress);
            legLeft.setRotateAngleZ(-0.04f * progress);
        }
        if (legRight != null) {
            legRight.setRotateAngleX(0.02f * progress);
            legRight.setRotateAngleY(-0.02f * progress);
            legRight.setRotateAngleZ(0.04f * progress);
        }
        if (legLeft2 != null) {
            legLeft2.setRotateAngleX(-0.02f * progress);
        }
        if (legRight2 != null) {
            legRight2.setRotateAngleX(-0.02f * progress);
        }
        if (footLeft != null) {
            footLeft.setRotateAngleX(0.02f * progress);
        }
        if (footRight != null) {
            footRight.setRotateAngleX(0.02f * progress);
        }
        if (skirt != null) {
            skirt.setRotateAngleX(-0.05f * progress);
        }
        applyBedrockHugHeadCuePose(maid, models);
        applyBedrockShyPeekPose(maid, models, true);
        applyBedrockHugChestTapPose(maid, models);
    }

    private static void applyGeckoHugPose(EntityMaid maid, AnimatedGeoModel model, float ageInTicks, float progress) {
        float openProgress = delayedOpenProgress(progress);
        AnimatedGeoBone allBody = findBone(model, "AllBody", "allBody", "Body", "body");
        AnimatedGeoBone upBody = findBone(model, "UpBody", "upBody");
        AnimatedGeoBone upperBody = findBone(model, "UpperBody", "upperBody");
        AnimatedGeoBone head = findBone(model, "Head", "head");
        AnimatedGeoBone neck = findBone(model, "Neck", "neck");
        AnimatedGeoBone leftShoulder = findBone(model, "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        AnimatedGeoBone rightShoulder = findBone(model, "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");
        AnimatedGeoBone leftArm = findBone(model, "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        AnimatedGeoBone leftForeArm = findBone(model, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm");
        AnimatedGeoBone rightArm = findBone(model, "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        AnimatedGeoBone rightForeArm = findBone(model, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm");
        AnimatedGeoBone leftLeg = findBone(model, "LeftLeg", "leftLeg", "legLeft", "LegLeft", "left_leg");
        AnimatedGeoBone rightLeg = findBone(model, "RightLeg", "rightLeg", "legRight", "LegRight", "right_leg");
        AnimatedGeoBone leftLowerLeg = findBone(model, "LeftLowerLeg", "leftLowerLeg", "legLeft2", "left_lower_leg");
        AnimatedGeoBone rightLowerLeg = findBone(model, "RightLowerLeg", "rightLowerLeg", "legRight2", "right_lower_leg");
        AnimatedGeoBone leftFoot = findBone(model, "LeftFoot", "leftFoot", "footLeft", "left_foot");
        AnimatedGeoBone rightFoot = findBone(model, "RightFoot", "rightFoot", "footRight", "right_foot");
        AnimatedGeoBone skirt = findBone(model, "Skirt", "skirt");

        if (allBody != null) {
            allBody.setRotationX(0.10f * progress);
            allBody.setRotationY(0.0f);
            allBody.setRotationZ(0.0f);
        }
        if (upBody != null) {
            upBody.setRotationX(0.10f * progress);
            upBody.setRotationY(0.0f);
            upBody.setRotationZ(0.0f);
        }
        if (upperBody != null) {
            upperBody.setRotationX(0.18f * progress);
            upperBody.setRotationY(0.0f);
            upperBody.setRotationZ(0.0f);
        }
        if (head != null) {
            head.setRotationX(-0.06f * progress);
            head.setRotationY(0.14f * progress);
            head.setRotationZ(0.04f * progress);
            applyGeckoHugPetHeadPose(maid, head, ageInTicks);
            applyGeckoPostKissShyPose(maid, head);
            applyGeckoHugShyCoverFacePose(maid, model);
        }
        if (neck != null) {
            neck.setRotationX(0.05f * progress);
            neck.setRotationY(0.0f);
            neck.setRotationZ(0.0f);
        }
        /*
         * Gecko 骨架和 Bedrock 骨架的局部轴语义不完全一样，
         * 不能直接照抄同一份数值。
         *
         * 之前这里外展量太小，而且前臂完全没有参与“张开→回扣”的弧线，
         * 所以看上去就像双手笔直往前伸。
         *
         * 注意 Gecko / YSM 的左右开合轴和 Bedrock 模型的表现不完全同向：
         * 上一版按 Bedrock 的直觉加大后，实际变成了内收交叉。
         * 因此这里把 Y 轴开合方向反过来，让双臂真正向外打开。
         */
        if (leftShoulder != null) {
            leftShoulder.setRotationX(0.06f * progress);
            leftShoulder.setRotationY(0.18f * openProgress);
            leftShoulder.setRotationZ(-0.15f * openProgress);
        }
        if (rightShoulder != null) {
            rightShoulder.setRotationX(0.06f * progress);
            rightShoulder.setRotationY(-0.18f * openProgress);
            rightShoulder.setRotationZ(0.15f * openProgress);
        }
        if (leftArm != null) {
            leftArm.setRotationX(1.18f * progress);
            leftArm.setRotationY(0.48f * openProgress);
            leftArm.setRotationZ(-0.54f * openProgress);
        }
        if (rightArm != null) {
            rightArm.setRotationX(1.18f * progress);
            rightArm.setRotationY(-0.48f * openProgress);
            rightArm.setRotationZ(0.54f * openProgress);
        }
        if (leftForeArm != null) {
            leftForeArm.setRotationX(0.32f * progress);
            leftForeArm.setRotationY(-0.22f * openProgress);
            leftForeArm.setRotationZ(0.16f * openProgress);
        }
        if (rightForeArm != null) {
            rightForeArm.setRotationX(0.32f * progress);
            rightForeArm.setRotationY(0.22f * openProgress);
            rightForeArm.setRotationZ(-0.16f * openProgress);
        }
        if (leftLeg != null) {
            leftLeg.setRotationX(0.02f * progress);
            leftLeg.setRotationY(0.02f * progress);
            leftLeg.setRotationZ(-0.04f * progress);
        }
        if (rightLeg != null) {
            rightLeg.setRotationX(0.02f * progress);
            rightLeg.setRotationY(-0.02f * progress);
            rightLeg.setRotationZ(0.04f * progress);
        }
        if (leftLowerLeg != null) {
            leftLowerLeg.setRotationX(-0.02f * progress);
        }
        if (rightLowerLeg != null) {
            rightLowerLeg.setRotationX(-0.02f * progress);
        }
        if (leftFoot != null) {
            leftFoot.setRotationX(0.02f * progress);
        }
        if (rightFoot != null) {
            rightFoot.setRotationX(0.02f * progress);
        }
        if (skirt != null) {
            skirt.setRotationX(-0.05f * progress);
        }
        applyGeckoHugHeadCuePose(maid, model);
        applyGeckoShyPeekPose(maid, model, true);
        applyGeckoHugChestTapPose(maid, model);
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

    /**
     * 拥抱内摸头使用新的“轻动作”版本：
     * 只轻微摆头，不再像普通摸头那样明显前倾或大幅左右晃动。
     */
    private static void applyBedrockHugPetHeadPose(EntityMaid maid,
                                                   ModelRendererWrapper head, float ageInTicks) {
        if (!PetHeadManager.isHugPetHeadAnimating(maid)) {
            return;
        }
        float phase = ageInTicks * 0.58f;
        head.setRotateAngleX(-0.10f + (float) Math.sin(phase * 0.70f) * 0.018f);
        head.setRotateAngleY(0.14f + (float) Math.sin(phase) * 0.12f);
        head.setRotateAngleZ((float) Math.sin(phase) * 0.035f);
    }

    private static void applyGeckoHugPetHeadPose(EntityMaid maid,
                                                 AnimatedGeoBone head, float ageInTicks) {
        if (!PetHeadManager.isHugPetHeadAnimating(maid)) {
            return;
        }
        float phase = ageInTicks * 0.58f;
        head.setRotationX(-0.10f + (float) Math.sin(phase * 0.70f) * 0.018f);
        head.setRotationY(0.14f + (float) Math.sin(phase) * 0.12f);
        head.setRotationZ((float) Math.sin(phase) * 0.035f);
    }

    /**
     * 站立亲吻后的害羞收尾。
     *
     * <p>之前害羞别头只挂在“拥抱姿态最终覆盖”里，
     * 所以一旦允许站立亲吻，客户端虽然收到了 shy turn 同步，
     * 但常规站立渲染路线根本没有地方把它真正写回头骨。
     *
     * <p>这里补一层“非拥抱态也允许单独覆写头部”的兜底，
     * 只动头部，不碰身体和四肢，这样不会把普通站立姿态掰坏。
     */
    private static void applyBedrockStandalonePostKissShyPose(EntityMaid maid,
                                                              HashMap<String, ModelRendererWrapper> models) {
        if (!HugClientState.isPostKissShyTurnActive(maid.getUUID())) {
            return;
        }
        ModelRendererWrapper head = models.get("head");
        if (head == null || PetHeadManager.isPetHeadAnimating(maid)) {
            return;
        }
        float headYawDegrees = HugClientState.currentPostKissShyYawDegrees(maid.getUUID());
        float headPitchDegrees = HugClientState.currentPostKissShyPitchDegrees(maid.getUUID());
        float cueYawDegrees = HugClientState.currentHeadCueYawDegrees(maid.getUUID());
        float cuePitchDegrees = HugClientState.currentHeadCuePitchDegrees(maid.getUUID());
        float cueRollDegrees = HugClientState.currentHeadCueRollDegrees(maid.getUUID());
        if (Math.abs(headYawDegrees) < 4.0F && Math.abs(headPitchDegrees) < 1.0F
                && Math.abs(cueYawDegrees) < 0.5F && Math.abs(cuePitchDegrees) < 0.5F && Math.abs(cueRollDegrees) < 0.5F) {
            return;
        }
        float yawOffset = Mth.clamp((headYawDegrees + cueYawDegrees) * Mth.DEG_TO_RAD, -1.49f, 1.49f);
        /*
         * 和 YSM 分支保持一致：
         * 低头 / 抬头是剧情里的主姿态，不再额外砍半 pitch。
         * 否则 JSON 明明发了低头，实际只有一点点点头感，肉眼近乎不可见。
         */
        float pitchOffset = Mth.clamp((headPitchDegrees + cuePitchDegrees) * Mth.DEG_TO_RAD, -0.55f, 0.55f);
        float rollOffset = Mth.clamp(cueRollDegrees * Mth.DEG_TO_RAD, -0.30f, 0.30f);
        head.setRotateAngleX(pitchOffset);
        head.setRotateAngleY(yawOffset);
        head.setRotateAngleZ(-yawOffset * 0.08f + rollOffset);
    }

    private static void applyBedrockStandaloneHeadCuePose(EntityMaid maid,
                                                          HashMap<String, ModelRendererWrapper> models) {
        applyBedrockHeadCuePose(maid, models, false);
    }

    private static void applyGeckoStandalonePostKissShyPose(EntityMaid maid,
                                                            AnimatedGeoModel model) {
        if (!HugClientState.isPostKissShyTurnActive(maid.getUUID())) {
            return;
        }
        AnimatedGeoBone head = findBone(model, "Head", "head");
        if (head == null || PetHeadManager.isPetHeadAnimating(maid)) {
            return;
        }
        // GeckoLib 模型的头部 yaw 轴和普通东方模型相反，亲吻后的别头在这里单独反号。
        float headYawDegrees = -HugClientState.currentPostKissShyYawDegrees(maid.getUUID());
        float headPitchDegrees = HugClientState.currentPostKissShyPitchDegrees(maid.getUUID());
        float cueYawDegrees = HugClientState.currentHeadCueYawDegrees(maid.getUUID());
        float cuePitchDegrees = HugClientState.currentHeadCuePitchDegrees(maid.getUUID());
        float cueRollDegrees = HugClientState.currentHeadCueRollDegrees(maid.getUUID());
        if (Math.abs(headYawDegrees) < 4.0F && Math.abs(headPitchDegrees) < 1.0F
                && Math.abs(cueYawDegrees) < 0.5F && Math.abs(cuePitchDegrees) < 0.5F && Math.abs(cueRollDegrees) < 0.5F) {
            return;
        }
        float yawOffset = Mth.clamp((headYawDegrees + cueYawDegrees) * Mth.DEG_TO_RAD, -1.49f, 1.49f);
        float pitchOffset = Mth.clamp((headPitchDegrees + cuePitchDegrees) * Mth.DEG_TO_RAD, -0.55f, 0.55f);
        float rollOffset = Mth.clamp(cueRollDegrees * Mth.DEG_TO_RAD, -0.30f, 0.30f);
        head.setRotationX(pitchOffset);
        head.setRotationY(yawOffset);
        head.setRotationZ(-yawOffset * 0.08f + rollOffset);
    }

    private static void applyGeckoStandaloneHeadCuePose(EntityMaid maid,
                                                        AnimatedGeoModel model) {
        applyGeckoHeadCuePose(maid, model, false);
    }

    private static void applyBedrockHugHeadCuePose(EntityMaid maid,
                                                   HashMap<String, ModelRendererWrapper> models) {
        applyBedrockHeadCuePose(maid, models, true);
    }

    private static void applyGeckoHugHeadCuePose(EntityMaid maid,
                                                 AnimatedGeoModel model) {
        applyGeckoHeadCuePose(maid, model, true);
    }

    private static void applyBedrockStandaloneChestTapPose(EntityMaid maid,
                                                           HashMap<String, ModelRendererWrapper> models) {
        applyBedrockChestTapPose(maid, models, false);
    }

    private static void applyGeckoStandaloneChestTapPose(EntityMaid maid,
                                                         AnimatedGeoModel model) {
        applyGeckoChestTapPose(maid, model, false);
    }

    private static void applyBedrockHugChestTapPose(EntityMaid maid,
                                                    HashMap<String, ModelRendererWrapper> models) {
        applyBedrockChestTapPose(maid, models, true);
    }

    private static void applyGeckoHugChestTapPose(EntityMaid maid,
                                                  AnimatedGeoModel model) {
        applyGeckoChestTapPose(maid, model, true);
    }

    private static void applyBedrockStandaloneShyCoverFacePose(EntityMaid maid,
                                                              HashMap<String, ModelRendererWrapper> models) {
        float progress = HugClientState.currentShyCoverFaceProgress(maid.getUUID());
        if (progress <= 0.0F) {
            return;
        }
        applyBedrockShyCoverFacePose(models, progress, false);
    }

    private static void applyGeckoStandaloneShyCoverFacePose(EntityMaid maid,
                                                             AnimatedGeoModel model) {
        float progress = HugClientState.currentShyCoverFaceProgress(maid.getUUID());
        if (progress <= 0.0F) {
            return;
        }
        applyGeckoShyCoverFacePose(model, progress, false);
    }

    private static void applyBedrockHugShyCoverFacePose(EntityMaid maid,
                                                        HashMap<String, ModelRendererWrapper> models) {
        float progress = HugClientState.currentShyCoverFaceProgress(maid.getUUID());
        if (progress <= 0.0F) {
            return;
        }
        applyBedrockShyCoverFacePose(models, progress, true);
    }

    private static void applyGeckoHugShyCoverFacePose(EntityMaid maid,
                                                      AnimatedGeoModel model) {
        float progress = HugClientState.currentShyCoverFaceProgress(maid.getUUID());
        if (progress <= 0.0F) {
            return;
        }
        applyGeckoShyCoverFacePose(model, progress, true);
    }

    private static void applyBedrockShyPeekPose(EntityMaid maid,
                                                HashMap<String, ModelRendererWrapper> models,
                                                boolean huggingBasePoseApplied) {
        float progress = HugClientState.currentShyPeekProgress(maid.getUUID());
        if (progress <= 0.0F) {
            return;
        }
        StoryHeadCuePoseLibrary.HeadCuePose pose = StoryHeadCuePoseLibrary.resolve(
                StoryHeadCuePoseLibrary.RigKind.BEDROCK,
                huggingBasePoseApplied,
                HugClientState.HeadCueType.RAISE_HEAD
        );
        if (pose == null) {
            return;
        }
        float scaled = progress * 0.42F;

        ModelRendererWrapper body = models.get("body");
        ModelRendererWrapper upperBody = models.get("upperBody");
        ModelRendererWrapper neck = models.get("neck");
        ModelRendererWrapper head = models.get("head");
        ModelRendererWrapper shoulderLeft = models.get("shoulderLeft");
        ModelRendererWrapper shoulderRight = models.get("shoulderRight");

        float bodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upperBodyBaseX = huggingBasePoseApplied ? 0.18F : 0.0F;
        float neckBaseX = huggingBasePoseApplied ? 0.05F : 0.0F;
        float headBaseX = huggingBasePoseApplied ? -0.06F : 0.0F;
        float headBaseY = huggingBasePoseApplied ? 0.14F : 0.0F;
        float headBaseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
        float shoulderLeftBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderLeftBaseY = huggingBasePoseApplied ? -0.20F : 0.0F;
        float shoulderLeftBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float shoulderRightBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderRightBaseY = huggingBasePoseApplied ? 0.20F : 0.0F;
        float shoulderRightBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;

        if (body != null) {
            body.setRotateAngleX(lerp(bodyBaseX, lerp(bodyBaseX, pose.bodyX(), 0.42F), scaled));
        }
        if (upperBody != null) {
            upperBody.setRotateAngleX(lerp(upperBodyBaseX, lerp(upperBodyBaseX, pose.upperBodyX(), 0.42F), scaled));
        }
        if (neck != null) {
            neck.setRotateAngleX(lerp(neckBaseX, lerp(neckBaseX, pose.neckX(), 0.42F), scaled));
        }
        if (head != null) {
            head.setRotateAngleX(lerp(headBaseX, lerp(headBaseX, pose.headX(), 0.42F), scaled));
            head.setRotateAngleY(lerp(headBaseY, lerp(headBaseY, pose.headY(), 0.42F), scaled));
            head.setRotateAngleZ(lerp(headBaseZ, lerp(headBaseZ, pose.headZ(), 0.42F), scaled));
        }
        if (shoulderLeft != null) {
            shoulderLeft.setRotateAngleX(lerp(shoulderLeftBaseX, lerp(shoulderLeftBaseX, pose.leftShoulderX(), 0.42F), scaled));
            shoulderLeft.setRotateAngleY(lerp(shoulderLeftBaseY, lerp(shoulderLeftBaseY, pose.leftShoulderY(), 0.42F), scaled));
            shoulderLeft.setRotateAngleZ(lerp(shoulderLeftBaseZ, lerp(shoulderLeftBaseZ, pose.leftShoulderZ(), 0.42F), scaled));
        }
        if (shoulderRight != null) {
            shoulderRight.setRotateAngleX(lerp(shoulderRightBaseX, lerp(shoulderRightBaseX, pose.rightShoulderX(), 0.42F), scaled));
            shoulderRight.setRotateAngleY(lerp(shoulderRightBaseY, lerp(shoulderRightBaseY, pose.rightShoulderY(), 0.42F), scaled));
            shoulderRight.setRotateAngleZ(lerp(shoulderRightBaseZ, lerp(shoulderRightBaseZ, pose.rightShoulderZ(), 0.42F), scaled));
        }
    }

    private static void applyGeckoShyPeekPose(EntityMaid maid,
                                              AnimatedGeoModel model,
                                              boolean huggingBasePoseApplied) {
        float progress = HugClientState.currentShyPeekProgress(maid.getUUID());
        if (progress <= 0.0F) {
            return;
        }
        StoryHeadCuePoseLibrary.HeadCuePose pose = StoryHeadCuePoseLibrary.resolve(
                StoryHeadCuePoseLibrary.RigKind.GECKO,
                huggingBasePoseApplied,
                HugClientState.HeadCueType.RAISE_HEAD
        );
        if (pose == null) {
            return;
        }
        float scaled = progress * 0.42F;

        AnimatedGeoBone body = findBone(model, "Body", "body");
        AnimatedGeoBone upperBody = findBone(model, "UpperBody", "upperBody");
        AnimatedGeoBone neck = findBone(model, "Neck", "neck");
        AnimatedGeoBone head = findBone(model, "Head", "head");
        AnimatedGeoBone shoulderLeft = findBone(model, "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        AnimatedGeoBone shoulderRight = findBone(model, "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");

        float bodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upperBodyBaseX = huggingBasePoseApplied ? 0.18F : 0.0F;
        float neckBaseX = huggingBasePoseApplied ? 0.05F : 0.0F;
        float headBaseX = huggingBasePoseApplied ? -0.06F : 0.0F;
        float headBaseY = huggingBasePoseApplied ? 0.14F : 0.0F;
        float headBaseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
        float shoulderLeftBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderLeftBaseY = huggingBasePoseApplied ? -0.20F : 0.0F;
        float shoulderLeftBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float shoulderRightBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderRightBaseY = huggingBasePoseApplied ? 0.20F : 0.0F;
        float shoulderRightBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;

        if (body != null) {
            body.setRotationX(lerp(bodyBaseX, lerp(bodyBaseX, pose.bodyX(), 0.42F), scaled));
        }
        if (upperBody != null) {
            upperBody.setRotationX(lerp(upperBodyBaseX, lerp(upperBodyBaseX, pose.upperBodyX(), 0.42F), scaled));
        }
        if (neck != null) {
            neck.setRotationX(lerp(neckBaseX, lerp(neckBaseX, pose.neckX(), 0.42F), scaled));
        }
        if (head != null) {
            head.setRotationX(lerp(headBaseX, lerp(headBaseX, pose.headX(), 0.42F), scaled));
            head.setRotationY(lerp(headBaseY, lerp(headBaseY, pose.headY(), 0.42F), scaled));
            head.setRotationZ(lerp(headBaseZ, lerp(headBaseZ, pose.headZ(), 0.42F), scaled));
        }
        if (shoulderLeft != null) {
            shoulderLeft.setRotationX(lerp(shoulderLeftBaseX, lerp(shoulderLeftBaseX, pose.leftShoulderX(), 0.42F), scaled));
            shoulderLeft.setRotationY(lerp(shoulderLeftBaseY, lerp(shoulderLeftBaseY, pose.leftShoulderY(), 0.42F), scaled));
            shoulderLeft.setRotationZ(lerp(shoulderLeftBaseZ, lerp(shoulderLeftBaseZ, pose.leftShoulderZ(), 0.42F), scaled));
        }
        if (shoulderRight != null) {
            shoulderRight.setRotationX(lerp(shoulderRightBaseX, lerp(shoulderRightBaseX, pose.rightShoulderX(), 0.42F), scaled));
            shoulderRight.setRotationY(lerp(shoulderRightBaseY, lerp(shoulderRightBaseY, pose.rightShoulderY(), 0.42F), scaled));
            shoulderRight.setRotationZ(lerp(shoulderRightBaseZ, lerp(shoulderRightBaseZ, pose.rightShoulderZ(), 0.42F), scaled));
        }
    }

    /**
     * 剧情低头 / 抬头不是单纯转一下 head 骨。
     *
     * <p>如果只动头骨，视觉上就只是“点头”；
     * 真正的“低着头”“慢慢抬起来看你”需要带一点脖子、上半身和肩线的收放。
     *
     * <p>这里统一做成持续姿态层：
     * - `LOWER_HEAD`：脖子往下压，上半身轻微前收，肩线略内扣；
     * - `RAISE_HEAD`：脖子和上半身缓一点点打开，形成“鼓起勇气抬头”的感觉。
     */
    private static void applyBedrockHeadCuePose(EntityMaid maid,
                                                HashMap<String, ModelRendererWrapper> models,
                                                boolean huggingBasePoseApplied) {
        float cueProgress = HugClientState.currentHeadCueProgress(maid.getUUID());
        float fromPoseAlpha = HugClientState.currentHeadCueFromPoseAlpha(maid.getUUID());
        boolean returnToNeutral = HugClientState.currentHeadCueReturnsToNeutral(maid.getUUID());
        StoryHeadCuePoseLibrary.HeadCuePose fromPose = StoryHeadCuePoseLibrary.resolve(
                StoryHeadCuePoseLibrary.RigKind.BEDROCK,
                huggingBasePoseApplied,
                HugClientState.currentHeadCueFromType(maid.getUUID())
        );
        StoryHeadCuePoseLibrary.HeadCuePose pose = returnToNeutral ? null : StoryHeadCuePoseLibrary.resolve(
                StoryHeadCuePoseLibrary.RigKind.BEDROCK,
                huggingBasePoseApplied,
                HugClientState.currentHeadCueType(maid.getUUID())
        );
        if ((pose == null && !returnToNeutral) || cueProgress <= 0.0F) {
            return;
        }

        ModelRendererWrapper body = models.get("body");
        ModelRendererWrapper upperBody = models.get("upperBody");
        ModelRendererWrapper neck = models.get("neck");
        ModelRendererWrapper head = models.get("head");
        ModelRendererWrapper shoulderLeft = models.get("shoulderLeft");
        ModelRendererWrapper shoulderRight = models.get("shoulderRight");

        float bodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upperBodyBaseX = huggingBasePoseApplied ? 0.18F : 0.0F;
        float neckBaseX = huggingBasePoseApplied ? 0.05F : 0.0F;
        float headBaseX = huggingBasePoseApplied ? -0.06F : 0.0F;
        float headBaseY = huggingBasePoseApplied ? 0.14F : 0.0F;
        float headBaseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
        float shoulderLeftBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderLeftBaseY = huggingBasePoseApplied ? -0.20F : 0.0F;
        float shoulderLeftBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float shoulderRightBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float shoulderRightBaseY = huggingBasePoseApplied ? 0.20F : 0.0F;
        float shoulderRightBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;

        if (body != null) {
            body.setRotateAngleX(lerp(resolvePoseValue(bodyBaseX, fromPose != null ? fromPose.bodyX() : bodyBaseX, fromPoseAlpha), returnToNeutral ? bodyBaseX : pose.bodyX(), cueProgress));
        }
        if (upperBody != null) {
            upperBody.setRotateAngleX(lerp(resolvePoseValue(upperBodyBaseX, fromPose != null ? fromPose.upperBodyX() : upperBodyBaseX, fromPoseAlpha), returnToNeutral ? upperBodyBaseX : pose.upperBodyX(), cueProgress));
        }
        if (neck != null) {
            neck.setRotateAngleX(lerp(resolvePoseValue(neckBaseX, fromPose != null ? fromPose.neckX() : neckBaseX, fromPoseAlpha), returnToNeutral ? neckBaseX : pose.neckX(), cueProgress));
        }
        if (head != null) {
            head.setRotateAngleX(lerp(resolvePoseValue(headBaseX, fromPose != null ? fromPose.headX() : headBaseX, fromPoseAlpha), returnToNeutral ? headBaseX : pose.headX(), cueProgress));
            head.setRotateAngleY(lerp(resolvePoseValue(headBaseY, fromPose != null ? fromPose.headY() : headBaseY, fromPoseAlpha), returnToNeutral ? headBaseY : pose.headY(), cueProgress));
            head.setRotateAngleZ(lerp(resolvePoseValue(headBaseZ, fromPose != null ? fromPose.headZ() : headBaseZ, fromPoseAlpha), returnToNeutral ? headBaseZ : pose.headZ(), cueProgress));
        }
        if (shoulderLeft != null) {
            shoulderLeft.setRotateAngleX(lerp(resolvePoseValue(shoulderLeftBaseX, fromPose != null ? fromPose.leftShoulderX() : shoulderLeftBaseX, fromPoseAlpha), returnToNeutral ? shoulderLeftBaseX : pose.leftShoulderX(), cueProgress));
            shoulderLeft.setRotateAngleY(lerp(resolvePoseValue(shoulderLeftBaseY, fromPose != null ? fromPose.leftShoulderY() : shoulderLeftBaseY, fromPoseAlpha), returnToNeutral ? shoulderLeftBaseY : pose.leftShoulderY(), cueProgress));
            shoulderLeft.setRotateAngleZ(lerp(resolvePoseValue(shoulderLeftBaseZ, fromPose != null ? fromPose.leftShoulderZ() : shoulderLeftBaseZ, fromPoseAlpha), returnToNeutral ? shoulderLeftBaseZ : pose.leftShoulderZ(), cueProgress));
        }
        if (shoulderRight != null) {
            shoulderRight.setRotateAngleX(lerp(resolvePoseValue(shoulderRightBaseX, fromPose != null ? fromPose.rightShoulderX() : shoulderRightBaseX, fromPoseAlpha), returnToNeutral ? shoulderRightBaseX : pose.rightShoulderX(), cueProgress));
            shoulderRight.setRotateAngleY(lerp(resolvePoseValue(shoulderRightBaseY, fromPose != null ? fromPose.rightShoulderY() : shoulderRightBaseY, fromPoseAlpha), returnToNeutral ? shoulderRightBaseY : pose.rightShoulderY(), cueProgress));
            shoulderRight.setRotateAngleZ(lerp(resolvePoseValue(shoulderRightBaseZ, fromPose != null ? fromPose.rightShoulderZ() : shoulderRightBaseZ, fromPoseAlpha), returnToNeutral ? shoulderRightBaseZ : pose.rightShoulderZ(), cueProgress));
        }
    }

    private static void applyGeckoHeadCuePose(EntityMaid maid,
                                              AnimatedGeoModel model,
                                              boolean huggingBasePoseApplied) {
        float cueProgress = HugClientState.currentHeadCueProgress(maid.getUUID());
        float fromPoseAlpha = HugClientState.currentHeadCueFromPoseAlpha(maid.getUUID());
        boolean returnToNeutral = HugClientState.currentHeadCueReturnsToNeutral(maid.getUUID());
        StoryHeadCuePoseLibrary.HeadCuePose fromPose = StoryHeadCuePoseLibrary.resolve(
                StoryHeadCuePoseLibrary.RigKind.GECKO,
                huggingBasePoseApplied,
                HugClientState.currentHeadCueFromType(maid.getUUID())
        );
        StoryHeadCuePoseLibrary.HeadCuePose pose = returnToNeutral ? null : StoryHeadCuePoseLibrary.resolve(
                StoryHeadCuePoseLibrary.RigKind.GECKO,
                huggingBasePoseApplied,
                HugClientState.currentHeadCueType(maid.getUUID())
        );
        if ((pose == null && !returnToNeutral) || cueProgress <= 0.0F) {
            return;
        }

        AnimatedGeoBone allBody = findBone(model, "AllBody", "allBody", "Body", "body");
        AnimatedGeoBone upBody = findBone(model, "UpBody", "upBody");
        AnimatedGeoBone upperBody = findBone(model, "UpperBody", "upperBody");
        AnimatedGeoBone neck = findBone(model, "Neck", "neck");
        AnimatedGeoBone head = findBone(model, "Head", "head");
        AnimatedGeoBone leftShoulder = findBone(model, "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        AnimatedGeoBone rightShoulder = findBone(model, "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");

        float allBodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upBodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upperBodyBaseX = huggingBasePoseApplied ? 0.18F : 0.0F;
        float neckBaseX = huggingBasePoseApplied ? 0.05F : 0.0F;
        float headBaseX = huggingBasePoseApplied ? -0.06F : 0.0F;
        float headBaseY = huggingBasePoseApplied ? 0.14F : 0.0F;
        float headBaseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
        float leftShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float leftShoulderBaseY = huggingBasePoseApplied ? 0.18F : 0.0F;
        float leftShoulderBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float rightShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float rightShoulderBaseY = huggingBasePoseApplied ? -0.18F : 0.0F;
        float rightShoulderBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;

        if (allBody != null) {
            allBody.setRotationX(lerp(resolvePoseValue(allBodyBaseX, fromPose != null ? fromPose.bodyX() : allBodyBaseX, fromPoseAlpha), returnToNeutral ? allBodyBaseX : pose.bodyX(), cueProgress));
        }
        if (upBody != null) {
            upBody.setRotationX(lerp(resolvePoseValue(upBodyBaseX, fromPose != null ? fromPose.upBodyX() : upBodyBaseX, fromPoseAlpha), returnToNeutral ? upBodyBaseX : pose.upBodyX(), cueProgress));
        }
        if (upperBody != null) {
            upperBody.setRotationX(lerp(resolvePoseValue(upperBodyBaseX, fromPose != null ? fromPose.upperBodyX() : upperBodyBaseX, fromPoseAlpha), returnToNeutral ? upperBodyBaseX : pose.upperBodyX(), cueProgress));
        }
        if (neck != null) {
            neck.setRotationX(lerp(resolvePoseValue(neckBaseX, fromPose != null ? fromPose.neckX() : neckBaseX, fromPoseAlpha), returnToNeutral ? neckBaseX : pose.neckX(), cueProgress));
        }
        if (head != null) {
            head.setRotationX(lerp(resolvePoseValue(headBaseX, fromPose != null ? fromPose.headX() : headBaseX, fromPoseAlpha), returnToNeutral ? headBaseX : pose.headX(), cueProgress));
            head.setRotationY(lerp(resolvePoseValue(headBaseY, fromPose != null ? fromPose.headY() : headBaseY, fromPoseAlpha), returnToNeutral ? headBaseY : pose.headY(), cueProgress));
            head.setRotationZ(lerp(resolvePoseValue(headBaseZ, fromPose != null ? fromPose.headZ() : headBaseZ, fromPoseAlpha), returnToNeutral ? headBaseZ : pose.headZ(), cueProgress));
        }
        if (leftShoulder != null) {
            leftShoulder.setRotationX(lerp(resolvePoseValue(leftShoulderBaseX, fromPose != null ? fromPose.leftShoulderX() : leftShoulderBaseX, fromPoseAlpha), returnToNeutral ? leftShoulderBaseX : pose.leftShoulderX(), cueProgress));
            leftShoulder.setRotationY(lerp(resolvePoseValue(leftShoulderBaseY, fromPose != null ? fromPose.leftShoulderY() : leftShoulderBaseY, fromPoseAlpha), returnToNeutral ? leftShoulderBaseY : pose.leftShoulderY(), cueProgress));
            leftShoulder.setRotationZ(lerp(resolvePoseValue(leftShoulderBaseZ, fromPose != null ? fromPose.leftShoulderZ() : leftShoulderBaseZ, fromPoseAlpha), returnToNeutral ? leftShoulderBaseZ : pose.leftShoulderZ(), cueProgress));
        }
        if (rightShoulder != null) {
            rightShoulder.setRotationX(lerp(resolvePoseValue(rightShoulderBaseX, fromPose != null ? fromPose.rightShoulderX() : rightShoulderBaseX, fromPoseAlpha), returnToNeutral ? rightShoulderBaseX : pose.rightShoulderX(), cueProgress));
            rightShoulder.setRotationY(lerp(resolvePoseValue(rightShoulderBaseY, fromPose != null ? fromPose.rightShoulderY() : rightShoulderBaseY, fromPoseAlpha), returnToNeutral ? rightShoulderBaseY : pose.rightShoulderY(), cueProgress));
            rightShoulder.setRotationZ(lerp(resolvePoseValue(rightShoulderBaseZ, fromPose != null ? fromPose.rightShoulderZ() : rightShoulderBaseZ, fromPoseAlpha), returnToNeutral ? rightShoulderBaseZ : pose.rightShoulderZ(), cueProgress));
        }
    }

    private static float resolvePoseValue(float baseValue, float poseValue, float poseAlpha) {
        return lerp(baseValue, poseValue, Math.max(0.0F, Math.min(1.0F, poseAlpha)));
    }

    private static void applyBedrockChestTapPose(EntityMaid maid,
                                                 HashMap<String, ModelRendererWrapper> models,
                                                 boolean huggingBasePoseApplied) {
        float poseProgress = HugClientState.currentChestTapPoseProgress(maid.getUUID());
        if (poseProgress <= 0.0F) {
            return;
        }
        float hitStrength = HugClientState.currentChestTapHitStrength(maid.getUUID());
        StoryChestTapPoseLibrary.ChestTapPose pose = StoryChestTapPoseLibrary.resolve(
                StoryChestTapPoseLibrary.RigKind.BEDROCK,
                huggingBasePoseApplied
        );

        ModelRendererWrapper shoulderLeft = models.get("shoulderLeft");
        ModelRendererWrapper shoulderRight = models.get("shoulderRight");
        ModelRendererWrapper armLeft = models.get("armLeft");
        ModelRendererWrapper armRight = models.get("armRight");
        ModelRendererWrapper armLeft2 = models.get("armLeft2");
        ModelRendererWrapper armRight2 = models.get("armRight2");

        float leftShoulderBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float leftShoulderBaseY = huggingBasePoseApplied ? -0.20F : 0.0F;
        float leftShoulderBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float rightShoulderBaseX = huggingBasePoseApplied ? 0.16F : 0.0F;
        float rightShoulderBaseY = huggingBasePoseApplied ? 0.20F : 0.0F;
        float rightShoulderBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;
        float leftArmBaseX = huggingBasePoseApplied ? -1.28F : 0.0F;
        float leftArmBaseY = huggingBasePoseApplied ? -0.62F : 0.0F;
        float leftArmBaseZ = huggingBasePoseApplied ? -0.56F : 0.0F;
        float rightArmBaseX = huggingBasePoseApplied ? -1.28F : 0.0F;
        float rightArmBaseY = huggingBasePoseApplied ? 0.62F : 0.0F;
        float rightArmBaseZ = huggingBasePoseApplied ? 0.56F : 0.0F;
        float leftForeArmBaseX = huggingBasePoseApplied ? -0.52F : 0.0F;
        float leftForeArmBaseY = huggingBasePoseApplied ? 0.30F : 0.0F;
        float leftForeArmBaseZ = huggingBasePoseApplied ? 0.24F : 0.0F;
        float rightForeArmBaseX = huggingBasePoseApplied ? -0.52F : 0.0F;
        float rightForeArmBaseY = huggingBasePoseApplied ? -0.30F : 0.0F;
        float rightForeArmBaseZ = huggingBasePoseApplied ? -0.24F : 0.0F;

        if (shoulderLeft != null) {
            shoulderLeft.setRotateAngleX(lerp(leftShoulderBaseX, pose.leftShoulderX(), poseProgress));
            shoulderLeft.setRotateAngleY(lerp(leftShoulderBaseY, pose.leftShoulderY(), poseProgress));
            shoulderLeft.setRotateAngleZ(lerp(leftShoulderBaseZ, pose.leftShoulderZ(), poseProgress));
        }
        if (shoulderRight != null) {
            shoulderRight.setRotateAngleX(lerp(rightShoulderBaseX, pose.rightShoulderX(), poseProgress));
            shoulderRight.setRotateAngleY(lerp(rightShoulderBaseY, pose.rightShoulderY(), poseProgress));
            shoulderRight.setRotateAngleZ(lerp(rightShoulderBaseZ, pose.rightShoulderZ(), poseProgress));
        }
        if (armLeft != null) {
            armLeft.setRotateAngleX(lerp(leftArmBaseX, pose.leftArmX(), poseProgress));
            armLeft.setRotateAngleY(lerp(leftArmBaseY, pose.leftArmY(), poseProgress));
            armLeft.setRotateAngleZ(lerp(leftArmBaseZ, pose.leftArmZ(), poseProgress));
        }
        if (armRight != null) {
            armRight.setRotateAngleX(lerp(rightArmBaseX, pose.rightArmX() + hitStrength * 0.10F, poseProgress));
            armRight.setRotateAngleY(lerp(rightArmBaseY, pose.rightArmY() - hitStrength * 0.08F, poseProgress));
            armRight.setRotateAngleZ(lerp(rightArmBaseZ, pose.rightArmZ() + hitStrength * 0.06F, poseProgress));
        }
        if (armLeft2 != null) {
            armLeft2.setRotateAngleX(lerp(leftForeArmBaseX, pose.leftForeArmX(), poseProgress));
            armLeft2.setRotateAngleY(lerp(leftForeArmBaseY, pose.leftForeArmY(), poseProgress));
            armLeft2.setRotateAngleZ(lerp(leftForeArmBaseZ, pose.leftForeArmZ(), poseProgress));
        }
        if (armRight2 != null) {
            armRight2.setRotateAngleX(lerp(rightForeArmBaseX, pose.rightForeArmX() - hitStrength * 0.32F, poseProgress));
            armRight2.setRotateAngleY(lerp(rightForeArmBaseY, pose.rightForeArmY() - hitStrength * 0.12F, poseProgress));
            armRight2.setRotateAngleZ(lerp(rightForeArmBaseZ, pose.rightForeArmZ(), poseProgress));
        }
    }

    private static void applyGeckoChestTapPose(EntityMaid maid,
                                               AnimatedGeoModel model,
                                               boolean huggingBasePoseApplied) {
        float poseProgress = HugClientState.currentChestTapPoseProgress(maid.getUUID());
        if (poseProgress <= 0.0F) {
            return;
        }
        float hitStrength = HugClientState.currentChestTapHitStrength(maid.getUUID());
        StoryChestTapPoseLibrary.ChestTapPose pose = StoryChestTapPoseLibrary.resolve(
                StoryChestTapPoseLibrary.RigKind.GECKO,
                huggingBasePoseApplied
        );

        AnimatedGeoBone leftShoulder = findBone(model, "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        AnimatedGeoBone rightShoulder = findBone(model, "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");
        AnimatedGeoBone leftArm = findBone(model, "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        AnimatedGeoBone leftForeArm = findBone(model, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm");
        AnimatedGeoBone rightArm = findBone(model, "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        AnimatedGeoBone rightForeArm = findBone(model, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm");

        float leftShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float leftShoulderBaseY = huggingBasePoseApplied ? 0.18F : 0.0F;
        float leftShoulderBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float rightShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float rightShoulderBaseY = huggingBasePoseApplied ? -0.18F : 0.0F;
        float rightShoulderBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;
        float leftArmBaseX = huggingBasePoseApplied ? 1.18F : 0.0F;
        float leftArmBaseY = huggingBasePoseApplied ? 0.48F : 0.0F;
        float leftArmBaseZ = huggingBasePoseApplied ? -0.54F : 0.0F;
        float rightArmBaseX = huggingBasePoseApplied ? 1.18F : 0.0F;
        float rightArmBaseY = huggingBasePoseApplied ? -0.48F : 0.0F;
        float rightArmBaseZ = huggingBasePoseApplied ? 0.54F : 0.0F;
        float leftForeArmBaseX = huggingBasePoseApplied ? 0.32F : 0.0F;
        float leftForeArmBaseY = huggingBasePoseApplied ? -0.22F : 0.0F;
        float leftForeArmBaseZ = huggingBasePoseApplied ? 0.16F : 0.0F;
        float rightForeArmBaseX = huggingBasePoseApplied ? 0.32F : 0.0F;
        float rightForeArmBaseY = huggingBasePoseApplied ? 0.22F : 0.0F;
        float rightForeArmBaseZ = huggingBasePoseApplied ? -0.16F : 0.0F;

        if (leftShoulder != null) {
            leftShoulder.setRotationX(lerp(leftShoulderBaseX, pose.leftShoulderX(), poseProgress));
            leftShoulder.setRotationY(lerp(leftShoulderBaseY, pose.leftShoulderY(), poseProgress));
            leftShoulder.setRotationZ(lerp(leftShoulderBaseZ, pose.leftShoulderZ(), poseProgress));
        }
        if (rightShoulder != null) {
            rightShoulder.setRotationX(lerp(rightShoulderBaseX, pose.rightShoulderX(), poseProgress));
            rightShoulder.setRotationY(lerp(rightShoulderBaseY, pose.rightShoulderY(), poseProgress));
            rightShoulder.setRotationZ(lerp(rightShoulderBaseZ, pose.rightShoulderZ(), poseProgress));
        }
        if (leftArm != null) {
            leftArm.setRotationX(lerp(leftArmBaseX, pose.leftArmX(), poseProgress));
            leftArm.setRotationY(lerp(leftArmBaseY, pose.leftArmY(), poseProgress));
            leftArm.setRotationZ(lerp(leftArmBaseZ, pose.leftArmZ(), poseProgress));
        }
        if (rightArm != null) {
            rightArm.setRotationX(lerp(rightArmBaseX, pose.rightArmX() + hitStrength * 0.10F, poseProgress));
            rightArm.setRotationY(lerp(rightArmBaseY, pose.rightArmY() + hitStrength * 0.08F, poseProgress));
            rightArm.setRotationZ(lerp(rightArmBaseZ, pose.rightArmZ() + hitStrength * 0.06F, poseProgress));
        }
        if (leftForeArm != null) {
            leftForeArm.setRotationX(lerp(leftForeArmBaseX, pose.leftForeArmX(), poseProgress));
            leftForeArm.setRotationY(lerp(leftForeArmBaseY, pose.leftForeArmY(), poseProgress));
            leftForeArm.setRotationZ(lerp(leftForeArmBaseZ, pose.leftForeArmZ(), poseProgress));
        }
        if (rightForeArm != null) {
            rightForeArm.setRotationX(lerp(rightForeArmBaseX, pose.rightForeArmX() + hitStrength * 0.34F, poseProgress));
            rightForeArm.setRotationY(lerp(rightForeArmBaseY, pose.rightForeArmY() - hitStrength * 0.12F, poseProgress));
            rightForeArm.setRotationZ(lerp(rightForeArmBaseZ, pose.rightForeArmZ(), poseProgress));
        }
    }

    private static void applyBedrockShyCoverFacePose(HashMap<String, ModelRendererWrapper> models,
                                                     float progress, boolean huggingBasePoseApplied) {
        ModelRendererWrapper head = models.get("head");
        ModelRendererWrapper shoulderLeft = models.get("shoulderLeft");
        ModelRendererWrapper shoulderRight = models.get("shoulderRight");
        ModelRendererWrapper armLeft = models.get("armLeft");
        ModelRendererWrapper armRight = models.get("armRight");
        ModelRendererWrapper armLeft2 = models.get("armLeft2");
        ModelRendererWrapper armRight2 = models.get("armRight2");

        if (head != null) {
            float baseX = huggingBasePoseApplied ? -0.06F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.14F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
            head.setRotateAngleX(lerp(baseX, 0.12F, progress));
            head.setRotateAngleY(lerp(baseY, 0.0F, progress));
            head.setRotateAngleZ(lerp(baseZ, 0.0F, progress));
        }
        if (shoulderLeft != null) {
            float baseX = huggingBasePoseApplied ? 0.16F : 0.0F;
            float baseY = huggingBasePoseApplied ? -0.20F : 0.0F;
            float baseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
            shoulderLeft.setRotateAngleX(lerp(baseX, 0.18F, progress));
            shoulderLeft.setRotateAngleY(lerp(baseY, -0.10F, progress));
            shoulderLeft.setRotateAngleZ(lerp(baseZ, -0.20F, progress));
        }
        if (shoulderRight != null) {
            float baseX = huggingBasePoseApplied ? 0.16F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.20F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.15F : 0.0F;
            shoulderRight.setRotateAngleX(lerp(baseX, 0.18F, progress));
            shoulderRight.setRotateAngleY(lerp(baseY, 0.10F, progress));
            shoulderRight.setRotateAngleZ(lerp(baseZ, 0.20F, progress));
        }
        if (armLeft != null) {
            float baseX = huggingBasePoseApplied ? -1.28F : 0.0F;
            float baseY = huggingBasePoseApplied ? -0.62F : 0.0F;
            float baseZ = huggingBasePoseApplied ? -0.56F : 0.0F;
            armLeft.setRotateAngleX(lerp(baseX, -0.98F, progress));
            armLeft.setRotateAngleY(lerp(baseY, -0.20F, progress));
            armLeft.setRotateAngleZ(lerp(baseZ, -0.44F, progress));
        }
        if (armRight != null) {
            float baseX = huggingBasePoseApplied ? -1.28F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.62F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.56F : 0.0F;
            armRight.setRotateAngleX(lerp(baseX, -0.98F, progress));
            armRight.setRotateAngleY(lerp(baseY, 0.20F, progress));
            armRight.setRotateAngleZ(lerp(baseZ, 0.44F, progress));
        }
        if (armLeft2 != null) {
            float baseX = huggingBasePoseApplied ? -0.52F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.30F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.24F : 0.0F;
            armLeft2.setRotateAngleX(lerp(baseX, -1.34F, progress));
            armLeft2.setRotateAngleY(lerp(baseY, 0.16F, progress));
            armLeft2.setRotateAngleZ(lerp(baseZ, 0.16F, progress));
        }
        if (armRight2 != null) {
            float baseX = huggingBasePoseApplied ? -0.52F : 0.0F;
            float baseY = huggingBasePoseApplied ? -0.30F : 0.0F;
            float baseZ = huggingBasePoseApplied ? -0.24F : 0.0F;
            armRight2.setRotateAngleX(lerp(baseX, -1.34F, progress));
            armRight2.setRotateAngleY(lerp(baseY, -0.16F, progress));
            armRight2.setRotateAngleZ(lerp(baseZ, -0.16F, progress));
        }
    }

    private static void applyGeckoShyCoverFacePose(AnimatedGeoModel model,
                                                   float progress, boolean huggingBasePoseApplied) {
        AnimatedGeoBone head = findBone(model, "Head", "head");
        AnimatedGeoBone leftShoulder = findBone(model, "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        AnimatedGeoBone rightShoulder = findBone(model, "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");
        AnimatedGeoBone leftArm = findBone(model, "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        AnimatedGeoBone leftForeArm = findBone(model, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm");
        AnimatedGeoBone rightArm = findBone(model, "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        AnimatedGeoBone rightForeArm = findBone(model, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm");

        if (head != null) {
            float baseX = huggingBasePoseApplied ? -0.06F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.14F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
            head.setRotationX(lerp(baseX, 0.12F, progress));
            head.setRotationY(lerp(baseY, 0.0F, progress));
            head.setRotationZ(lerp(baseZ, 0.0F, progress));
        }
        if (leftShoulder != null) {
            float baseX = huggingBasePoseApplied ? 0.06F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.18F : 0.0F;
            float baseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
            leftShoulder.setRotationX(lerp(baseX, 0.14F, progress));
            leftShoulder.setRotationY(lerp(baseY, -0.08F, progress));
            leftShoulder.setRotationZ(lerp(baseZ, -0.18F, progress));
        }
        if (rightShoulder != null) {
            float baseX = huggingBasePoseApplied ? 0.06F : 0.0F;
            float baseY = huggingBasePoseApplied ? -0.18F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.15F : 0.0F;
            rightShoulder.setRotationX(lerp(baseX, 0.14F, progress));
            rightShoulder.setRotationY(lerp(baseY, 0.08F, progress));
            rightShoulder.setRotationZ(lerp(baseZ, 0.18F, progress));
        }
        if (leftArm != null) {
            float baseX = huggingBasePoseApplied ? 1.18F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.48F : 0.0F;
            float baseZ = huggingBasePoseApplied ? -0.54F : 0.0F;
            leftArm.setRotationX(lerp(baseX, 1.02F, progress));
            leftArm.setRotationY(lerp(baseY, -0.14F, progress));
            leftArm.setRotationZ(lerp(baseZ, -0.34F, progress));
        }
        if (rightArm != null) {
            float baseX = huggingBasePoseApplied ? 1.18F : 0.0F;
            float baseY = huggingBasePoseApplied ? -0.48F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.54F : 0.0F;
            rightArm.setRotationX(lerp(baseX, 1.02F, progress));
            rightArm.setRotationY(lerp(baseY, 0.14F, progress));
            rightArm.setRotationZ(lerp(baseZ, 0.34F, progress));
        }
        if (leftForeArm != null) {
            float baseX = huggingBasePoseApplied ? 0.32F : 0.0F;
            float baseY = huggingBasePoseApplied ? -0.22F : 0.0F;
            float baseZ = huggingBasePoseApplied ? 0.16F : 0.0F;
            leftForeArm.setRotationX(lerp(baseX, 1.26F, progress));
            leftForeArm.setRotationY(lerp(baseY, 0.24F, progress));
            leftForeArm.setRotationZ(lerp(baseZ, 0.12F, progress));
        }
        if (rightForeArm != null) {
            float baseX = huggingBasePoseApplied ? 0.32F : 0.0F;
            float baseY = huggingBasePoseApplied ? 0.22F : 0.0F;
            float baseZ = huggingBasePoseApplied ? -0.16F : 0.0F;
            rightForeArm.setRotationX(lerp(baseX, 1.26F, progress));
            rightForeArm.setRotationY(lerp(baseY, -0.24F, progress));
            rightForeArm.setRotationZ(lerp(baseZ, -0.12F, progress));
        }
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float delayedOpenProgress(float progress) {
        if (progress <= 0.46f) {
            return 0.0f;
        }
        float local = (progress - 0.46f) / 0.54f;
        local = Math.max(0.0f, Math.min(1.0f, local));
        return local * local * (3.0f - 2.0f * local);
    }

    /**
     * 亲吻结束后的收尾动作只允许“头别过去”，身体、上半身、手臂全部保持拥抱原姿势不变。
     *
     * <p>这里不额外依赖客户端自定义同步变量，
     * 而是直接读取实体同步过来的头部朝向差值，
     * 再把它映射到最终姿势覆盖层，确保 Bedrock / Gecko 模型都能吃到同一份结果。
     */
    private static void applyBedrockPostKissShyPose(EntityMaid maid, ModelRendererWrapper head) {
        if (!HugClientState.isPostKissShyTurnActive(maid.getUUID())) {
            return;
        }
        if (PetHeadManager.isHugPetHeadAnimating(maid)) {
            return;
        }
        float headYawDegrees = HugClientState.currentPostKissShyYawDegrees(maid.getUUID());
        float headPitchDegrees = HugClientState.currentPostKissShyPitchDegrees(maid.getUUID());
        float cueYawDegrees = HugClientState.currentHeadCueYawDegrees(maid.getUUID());
        float cuePitchDegrees = HugClientState.currentHeadCuePitchDegrees(maid.getUUID());
        float cueRollDegrees = HugClientState.currentHeadCueRollDegrees(maid.getUUID());
        if (Math.abs(headYawDegrees) < 4.0F && Math.abs(headPitchDegrees) < 1.0F
                && Math.abs(cueYawDegrees) < 0.5F && Math.abs(cuePitchDegrees) < 0.5F && Math.abs(cueRollDegrees) < 0.5F) {
            return;
        }
        /*
         * 亲后别头需要明显得多，用户希望接近 85 度。
         * 这里直接按客户端动画角度映射到头骨，不再额外压缩幅度，
         * 只做一个安全钳制，避免极端模型把头拧穿。
         */
        float yawOffset = Mth.clamp((headYawDegrees + cueYawDegrees) * Mth.DEG_TO_RAD, -1.49f, 1.49f);
        float pitchOffset = Mth.clamp((headPitchDegrees + cuePitchDegrees) * Mth.DEG_TO_RAD, -0.55f, 0.55f);
        float rollOffset = Mth.clamp(cueRollDegrees * Mth.DEG_TO_RAD, -0.30f, 0.30f);
        head.setRotateAngleX(-0.06f + pitchOffset);
        head.setRotateAngleY(0.14f + yawOffset);
        head.setRotateAngleZ(0.04f - yawOffset * 0.08f + rollOffset);
    }

    private static void applyGeckoPostKissShyPose(EntityMaid maid, AnimatedGeoBone head) {
        if (!HugClientState.isPostKissShyTurnActive(maid.getUUID())) {
            return;
        }
        if (PetHeadManager.isHugPetHeadAnimating(maid)) {
            return;
        }
        // GeckoLib 模型的头部 yaw 轴和普通东方模型相反，亲吻后的别头在这里单独反号。
        float headYawDegrees = -HugClientState.currentPostKissShyYawDegrees(maid.getUUID());
        float headPitchDegrees = HugClientState.currentPostKissShyPitchDegrees(maid.getUUID());
        float cueYawDegrees = HugClientState.currentHeadCueYawDegrees(maid.getUUID());
        float cuePitchDegrees = HugClientState.currentHeadCuePitchDegrees(maid.getUUID());
        float cueRollDegrees = HugClientState.currentHeadCueRollDegrees(maid.getUUID());
        if (Math.abs(headYawDegrees) < 4.0F && Math.abs(headPitchDegrees) < 1.0F
                && Math.abs(cueYawDegrees) < 0.5F && Math.abs(cuePitchDegrees) < 0.5F && Math.abs(cueRollDegrees) < 0.5F) {
            return;
        }
        float yawOffset = Mth.clamp((headYawDegrees + cueYawDegrees) * Mth.DEG_TO_RAD, -1.49f, 1.49f);
        float pitchOffset = Mth.clamp((headPitchDegrees + cuePitchDegrees) * Mth.DEG_TO_RAD, -0.55f, 0.55f);
        float rollOffset = Mth.clamp(cueRollDegrees * Mth.DEG_TO_RAD, -0.30f, 0.30f);
        head.setRotationX(-0.06f + pitchOffset);
        head.setRotationY(0.14f + yawOffset);
        head.setRotationZ(0.04f - yawOffset * 0.08f + rollOffset);
    }
}
