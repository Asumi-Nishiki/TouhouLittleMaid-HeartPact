package com.example.maidmarriage.client;

import com.example.maidmarriage.client.hugui.actions.StoryChestTapPoseLibrary;
import com.example.maidmarriage.client.hugui.actions.StoryHeadCuePoseLibrary;
import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.example.maidmarriage.compat.MaidHugManager;
import com.example.maidmarriage.compat.MaidLiftManager;
import com.example.maidmarriage.compat.PetHeadManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;

/**
 * YSM 固定动作运行时姿态桥。
 *
 * <p>这层是我们后续所有“YSM 固定姿态动作”的统一入口。
 * 当 YSM 已经算完当前帧动画、即将真正绘制模型时，
 * 我们在最后一刻抓取运行时骨骼表，并把需要的固定姿态直接覆写进去。
 *
 * <p>这里刻意只处理“固定动作最终层”：
 * <ul>
 *   <li>拥抱这类站立固定姿态：走本桥，在 draw call 前最终覆写关键骨骼；</li>
 *   <li>举高高这类骑乘/坐姿动作：不在这里硬掰腿，继续交给 YSM 自己的 riding / ride_pig，再由外层只修正高度；</li>
 *   <li>后续 kiss / pet / carry 如果也需要固定姿态，同样沿着 {@link FixedPoseAction} 扩展。</li>
 * </ul>
 *
 * <p>这里不使用“当前值 + 偏移”的原因也很直接：
 * YSM 自己的待机、走路、控制器和 riding 结果都可能还在骨链上，
 * 如果只做增量叠加，很容易出现扭曲、折叠或动作串线。
 *
 * <p>因此这里统一采用“绝对覆写最终姿态”的方式，并且全部通过反射访问 YSM 运行时对象，
 * 避免对 YSM 建立编译期依赖。
 */
public final class YsmRuntimeHugPoseBridge {
    private static final String WRAPPER_ENTITY_GETTER = "OOO00Oo0OoOOooO0O0O00o0O";
    private static final String WRAPPER_MODEL_GETTER = "o0oo00OOooo0oooOoooo00O0";
    private static final String MODEL_BONES_GETTER = "Oooo0O0OO0O0000Oooo0Oo0o";
    private static final String BONE_NAME_GETTER = "oo0O0oOo0OOOO0O0OOOoo0oo";
    private static final String BONE_ROT_X_METHOD = "O0OOOoOooOO0OO0o00OoO0O0";
    private static final String BONE_ROT_Y_METHOD = "ooOooO0OOo00O0oooo000oOO";
    private static final String BONE_ROT_Z_METHOD = "Oooo0O0OO0O0000Oooo0Oo0o";

    private YsmRuntimeHugPoseBridge() {
    }

    /**
     * 本桥支持的“固定姿态动作”。
     *
     * <p>以后需要新增新的 YSM 固定姿态时，只在这里增加枚举值，
     * 再在 {@link #resolveFixedPoseAction(EntityMaid)} 和 {@link #applyFixedPose(FixedPoseAction, Map)}
     * 两处接入状态判定与骨骼参数，避免各个动作到处散落反射改骨代码。
     */
    private enum FixedPoseAction {
        NONE,
        HUG,
        CARRY_ADULT,
        CARRIED_CHILD
    }

    /**
     * 提供给外层渲染注入点快速判断：当前这个 YSM wrapper 是否正处于举高高状态。
     *
     * <p>举高高不再进入本桥的骨骼覆写；这个方法只给外层渲染 mixin 做高度补偿判定。
     */
    public static boolean isLiftState(Object ysmWrapper) {
        if (ysmWrapper == null) {
            return false;
        }
        try {
            EntityMaid maid = resolveMaid(ysmWrapper);
            if (maid == null) {
                return false;
            }
            Player player = MaidLiftManager.getLiftPlayer(maid);
            return MaidLiftManager.isLiftState(maid, player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 提供给外层渲染注入点判断：当前这个 YSM wrapper 是否正处于成年女仆拥抱状态。
     *
     * <p>拥抱是站立固定姿态，需要进入本桥做最终骨骼覆写。
     */
    public static boolean isHugState(Object ysmWrapper) {
        if (ysmWrapper == null) {
            return false;
        }
        try {
            EntityMaid maid = resolveMaid(ysmWrapper);
            if (maid == null) {
                return false;
            }
            Player player = MaidHugManager.getHugPlayer(maid);
            return MaidHugManager.isHugState(maid, player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 解析当前这只 YSM 女仆对应玩家的“全局举高高高度配置”。
     *
     * <p>这个值来自模组自己的举高高配置，同一玩家不论使用普通模型还是 YSM，
     * 服务端真实锚点都共享同一套高度逻辑。
     */
    public static double resolveLiftConfiguredHeight(Object ysmWrapper) {
        if (ysmWrapper == null) {
            return 0.0D;
        }
        try {
            EntityMaid maid = resolveMaid(ysmWrapper);
            if (maid == null) {
                return 0.0D;
            }
            Player player = MaidLiftManager.getLiftPlayer(maid);
            return player == null ? 0.0D : MaidLiftManager.resolveLiftHeight(player);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    /**
     * 当前这只 YSM 女仆是否正处于“大女仆抱小女仆”里的小女仆一侧。
     *
     * <p>这个判定专门给渲染层做额外高度补偿使用：
     * 只有被抱着的小女仆才需要这层视觉 Y 偏移，
     * 大女仆本体保持原位即可。
     */
    public static boolean isCarriedChildState(Object ysmWrapper) {
        if (ysmWrapper == null) {
            return false;
        }
        try {
            EntityMaid maid = resolveMaid(ysmWrapper);
            return maid != null && MaidCarryChildManager.isCarriedChild(maid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 读取 YSM 抱小女仆调试面板中的当前额外高度。
     *
     * <p>这块和举高高一样，只作为客户端渲染层的可视修正，不参与服务端位置逻辑。
     */
    public static double resolveCarryChildVisualHeight() {
        return YsmLiftHeightDebug.carryChildVisualHeight();
    }

    public static double resolveCarryChildVisualOffsetX() {
        return YsmLiftHeightDebug.carryChildVisualOffsetX();
    }

    public static double resolveCarryChildVisualOffsetZ() {
        return YsmLiftHeightDebug.carryChildVisualOffsetZ();
    }

    /**
     * YSM 固定姿态统一入口。
     *
     * <p>外层 mixin 只调用这个方法，不关心具体动作；
     * 这里内部根据女仆状态决定是否需要套用固定姿态。
     */
    public static void applyIfNeeded(Object ysmWrapper) {
        if (ysmWrapper == null) {
            return;
        }
        try {
            EntityMaid maid = resolveMaid(ysmWrapper);
            if (maid == null) {
                return;
            }

            FixedPoseAction action = resolveFixedPoseAction(maid);
            boolean applyStandaloneShyPose = HugClientState.isPostKissShyTurnActive(maid.getUUID());
            boolean applyStandaloneHeadCue = HugClientState.isHeadCueActive(maid.getUUID());
            boolean applyStandaloneShyCoverFace = HugClientState.isShyCoverFaceActive(maid.getUUID());
            boolean applyStandaloneShyPeek = HugClientState.isShyPeekActive(maid.getUUID());
            Player hugPlayer = MaidHugManager.getHugPlayer(maid);
            float hugPoseProgress = MaidHugManager.clientHugPoseProgress(hugPlayer);
            if (action == FixedPoseAction.NONE
                    && hugPoseProgress <= 0.0F
                    && !applyStandaloneShyPose
                    && !applyStandaloneHeadCue
                    && !applyStandaloneShyCoverFace
                    && !applyStandaloneShyPeek) {
                return;
            }

            Object model = invokeNoArg(ysmWrapper, WRAPPER_MODEL_GETTER);
            if (model == null) {
                return;
            }

            Map<String, Object> bones = collectBones(model);
            if (bones.isEmpty()) {
                return;
            }

            FixedPoseAction effectiveAction = action == FixedPoseAction.NONE && hugPoseProgress > 0.0F
                    ? FixedPoseAction.HUG
                    : action;
            applyFixedPose(effectiveAction, bones, hugPoseProgress);
            applyHeadCuePose(maid, bones, effectiveAction == FixedPoseAction.HUG);
            applyPostKissShyPose(maid, bones, effectiveAction == FixedPoseAction.HUG);
            applyShyCoverFacePose(maid, bones, effectiveAction == FixedPoseAction.HUG);
            applyShyPeekPose(maid, bones, effectiveAction == FixedPoseAction.HUG);
            applyChestTapPose(maid, bones, effectiveAction == FixedPoseAction.HUG);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 根据当前女仆状态解析需要套用的固定姿态。
     *
     * <p>这里故意不返回举高高。
     * 举高高对 YSM 来说应该继续走模型包自己的 riding / ride_pig 坐姿，
     * 我们只在外层 `PoseStack` 做高度修正；否则很容易和模型自带坐姿叠加成扭曲。
     */
    private static FixedPoseAction resolveFixedPoseAction(EntityMaid maid) {
        /*
         * 大女仆抱小女仆不是普通 riding / sitting 动作。
         *
         * 普通 Gecko / Bedrock 模型会在 HardcodedAnimationManagerCarryChildPoseMixin
         * 的 RETURN 阶段拿到一套最终姿态；YSM 渲染不走那条模型对象，所以这里必须
         * 也走“draw 前最终覆写骨骼”的接口。否则 YSM 模型包仍可能根据内部控制器残留
         * 出坐姿、骑乘姿态，视觉上就会变成“大女仆坐着抱小女仆”。
         */
        if (MaidCarryChildManager.isAdultCarrier(maid)) {
            return FixedPoseAction.CARRY_ADULT;
        }
        if (MaidCarryChildManager.isCarriedChild(maid)) {
            return FixedPoseAction.CARRIED_CHILD;
        }

        Player hugPlayer = MaidHugManager.getHugPlayer(maid);
        if (MaidHugManager.isHugState(maid, hugPlayer)) {
            return FixedPoseAction.HUG;
        }
        return FixedPoseAction.NONE;
    }

    /**
     * 固定姿态统一分发点。
     *
     * <p>以后新增固定动作时，只需要在这里增加一个 `case`。
     */
    private static void applyFixedPose(FixedPoseAction action, Map<String, Object> bones, float progress) throws Exception {
        if (action == FixedPoseAction.HUG) {
            applyHugPose(bones, progress);
            return;
        }
        if (action == FixedPoseAction.CARRY_ADULT) {
            applyAdultCarryPose(bones);
            return;
        }
        if (action == FixedPoseAction.CARRIED_CHILD) {
            applyCarriedChildPose(bones);
        }
    }

    /**
     * YSM 大女仆抱小女仆时的成人托抱姿态。
     *
     * <p>这组参数和 GeckoLib 分支的语义保持一致：身体保持站立，只让上半身轻微前倾，
     * 双臂向前托住小女仆；同时显式把腿、小腿和脚写回接近站立的角度。
     *
     * <p>最后这一步很关键：YSM 模型包里如果上一帧或控制器里残留了 riding/sitting，
     * 只改手臂是不够的，腿部仍会保持坐姿，所以这里把下半身也一起作为“最终姿态”覆写。
     */
    private static void applyAdultCarryPose(Map<String, Object> bones) throws Exception {
        setBoneRotationAny(bones, 0.06f, 0.0f, 0.0f, "AllBody", "Root", "root", "Body", "body");
        setBoneRotationAny(bones, 0.10f, 0.0f, 0.0f, "UpBody", "upBody");
        setBoneRotationAny(bones, 0.12f, 0.0f, 0.0f, "UpperBody", "upperBody");
        setBoneRotationAny(bones, -0.02f, 0.0f, 0.0f, "Head", "head");
        setBoneRotationAny(bones, 0.04f, 0.0f, 0.0f, "Neck", "neck");

        setBoneRotationAny(bones, 0.08f, 0.08f, -0.14f, "LeftShoulder", "leftShoulder");
        setBoneRotationAny(bones, 0.08f, -0.08f, 0.14f, "RightShoulder", "rightShoulder");
        setBoneRotationAny(bones, 0.94f, 0.06f, -0.26f, "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        setBoneRotationAny(bones, 0.94f, -0.06f, 0.26f, "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        setBoneRotationAny(bones, 0.76f, 0.14f, 0.08f, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm", "armLeft2");
        setBoneRotationAny(bones, 0.76f, -0.14f, -0.08f, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm", "armRight2");

        setBoneRotationAny(bones, 0.0f, 0.0f, -0.03f, "LeftLeg", "leftLeg", "legLeft", "LegLeft", "left_leg");
        setBoneRotationAny(bones, 0.0f, 0.0f, 0.03f, "RightLeg", "rightLeg", "legRight", "LegRight", "right_leg");
        setBoneRotationAny(bones, 0.0f, 0.0f, 0.0f, "LeftLowerLeg", "leftLowerLeg", "legLeft2", "left_lower_leg");
        setBoneRotationAny(bones, 0.0f, 0.0f, 0.0f, "RightLowerLeg", "rightLowerLeg", "legRight2", "right_lower_leg");
        setBoneRotationAny(bones, 0.0f, 0.0f, 0.0f, "LeftFoot", "leftFoot", "footLeft", "left_foot");
        setBoneRotationAny(bones, 0.0f, 0.0f, 0.0f, "RightFoot", "rightFoot", "footRight", "right_foot");
        setBoneRotationAny(bones, -0.03f, 0.0f, 0.0f, "Skirt", "skirt");
    }

    /**
     * YSM 小女仆被大女仆抱起时的被抱姿态。
     *
     * <p>服务端实体位置仍然由抱娃代理实体负责，这里只修正 YSM 模型的最终骨骼姿态。
     * 也就是说：不在这里移动实体、不改乘骑链，只把 YSM 可能误入的坐姿压成我们自己的
     * “横向被托抱”姿态。
     */
    private static void applyCarriedChildPose(Map<String, Object> bones) throws Exception {
        setBoneRotationAny(bones, deg(180.0f), deg(-90.0f), deg(-100.0f), "Root", "root");
        setBoneRotationAny(bones, deg(-14.0f), 0.0f, 0.0f, "AllBody", "allBody", "Body", "body");
        setBoneRotationAny(bones, deg(-14.0f), 0.0f, 0.0f, "UpBody", "upBody");
        setBoneRotationAny(bones, deg(-14.0f), 0.0f, 0.0f, "UpperBody", "upperBody");
        setBoneRotationAny(bones, deg(14.0f), 0.0f, 0.0f, "Head", "head");

        setBoneRotationAny(bones, deg(-10.0f), deg(24.0f), deg(-6.0f), "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        setBoneRotationAny(bones, deg(-10.0f), deg(-24.0f), deg(6.0f), "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        setBoneRotationAny(bones, deg(-36.0f), 0.0f, 0.0f, "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm", "armLeft2");
        setBoneRotationAny(bones, deg(-36.0f), 0.0f, 0.0f, "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm", "armRight2");

        setBoneRotationAny(bones, deg(14.0f), deg(4.0f), deg(-2.0f), "LeftLeg", "leftLeg", "legLeft", "LegLeft", "left_leg");
        setBoneRotationAny(bones, deg(14.0f), deg(-4.0f), deg(2.0f), "RightLeg", "rightLeg", "legRight", "LegRight", "right_leg");
        setBoneRotationAny(bones, deg(12.0f), 0.0f, 0.0f, "LeftLowerLeg", "leftLowerLeg", "legLeft2", "left_lower_leg");
        setBoneRotationAny(bones, deg(12.0f), 0.0f, 0.0f, "RightLowerLeg", "rightLowerLeg", "legRight2", "right_lower_leg");
        setBoneRotationAny(bones, 0.0f, 0.0f, 0.0f, "LeftFoot", "leftFoot", "footLeft", "left_foot");
        setBoneRotationAny(bones, 0.0f, 0.0f, 0.0f, "RightFoot", "rightFoot", "footRight", "right_foot");
    }

    /**
     * YSM 拥抱固定姿态。
     *
     * <p>这里继续保留“整条关键骨链最终覆写”的策略，但参数必须统一为弧度。
     *
     * <p>这次问题能够彻底定位，关键在于确认了两件事：
     * 1. 我们的覆写确实已经写进 YSM 当前帧骨骼；
     * 2. draw 结束后这些值也没有再被 YSM 改回去。
     *
     * <p>因此之前出现的“倒立、双腿岔开、整个人折叠”，根因不是注入时机，也不是状态串线，
     * 而是旧版本往这里写入了一组像角度一样的大数值。
     *
     * <p>而 YSM 运行时骨骼链内部会走弧度链路，
     * 所以这里一旦直接写入超大值，视觉上就会等价于对躯干和四肢做超大幅翻转。
     *
     * <p>现在改成和 Gecko 拥抱姿态同源的一套弧度参数，
     * 让 Bedrock / Gecko / YSM 三条路线在动作语义上尽量一致，
     * 后续再调细节时也能围绕同一套姿态继续收敛。
     */
    private static void applyHugPose(Map<String, Object> bones, float progress) throws Exception {
        float openProgress = delayedOpenProgress(progress);
        setBoneRotation(bones, "AllBody", 0.10f * progress, 0.0f, 0.0f);
        setBoneRotation(bones, "UpBody", 0.10f * progress, 0.0f, 0.0f);
        setBoneRotation(bones, "UpperBody", 0.18f * progress, 0.0f, 0.0f);
        setBoneRotation(bones, "Head", -0.06f * progress, 0.14f * progress, 0.04f * progress);
        setBoneRotation(bones, "Neck", 0.05f * progress, 0.0f, 0.0f);
        /*
         * YSM 这里同样不能直接吃 Bedrock 的张开量，
         * 但原先这组参数又明显太保守，结果就是双臂更像向前夹，而不是先抬起再向外张开。
         *
         * 所以这里给 YSM 一套和 Gecko 接近的中等张开量，
         * 同时让前臂带一点反向回扣，形成更明显的“环抱弧线”。
         *
         * 注意：YSM 这条运行时骨骼路线的 Y 轴开合方向和 Bedrock 直觉相反。
         * 如果使用负左正右，会变成双手向内合拢甚至交叉；
         * 这里改成正左负右，才是视觉上的向外打开。
         */
        setBoneRotation(bones, "LeftShoulder", 0.06f * progress, 0.18f * openProgress, -0.15f * openProgress);
        setBoneRotation(bones, "RightShoulder", 0.06f * progress, -0.18f * openProgress, 0.15f * openProgress);
        setBoneRotation(bones, "LeftArm", 1.18f * progress, 0.48f * openProgress, -0.54f * openProgress);
        setBoneRotation(bones, "LeftForeArm", 0.32f * progress, -0.22f * openProgress, 0.16f * openProgress);
        setBoneRotation(bones, "RightArm", 1.18f * progress, -0.48f * openProgress, 0.54f * openProgress);
        setBoneRotation(bones, "RightForeArm", 0.32f * progress, 0.22f * openProgress, -0.16f * openProgress);
        setBoneRotation(bones, "LeftLeg", 0.02f * progress, 0.02f * progress, -0.04f * progress);
        setBoneRotation(bones, "RightLeg", 0.02f * progress, -0.02f * progress, 0.04f * progress);
        setBoneRotation(bones, "LeftLowerLeg", -0.02f * progress, 0.0f, 0.0f);
        setBoneRotation(bones, "RightLowerLeg", -0.02f * progress, 0.0f, 0.0f);
        setBoneRotation(bones, "LeftFoot", 0.02f * progress, 0.0f, 0.0f);
        setBoneRotation(bones, "RightFoot", 0.02f * progress, 0.0f, 0.0f);
        setBoneRotation(bones, "Skirt", -0.05f * progress, 0.0f, 0.0f);
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
     * YSM 的亲吻害羞别头补丁。
     *
     * <p>这层要同时兼容两种情况：
     * 1. 当前已经是拥抱姿态：在拥抱基础姿态上再叠一层害羞别头；
     * 2. 当前只是站立锁定：只改头部，不改身体与四肢。
     */
    private static void applyPostKissShyPose(EntityMaid maid, Map<String, Object> bones,
                                             boolean huggingBasePoseApplied) throws Exception {
        if (!HugClientState.isPostKissShyTurnActive(maid.getUUID())) {
            return;
        }
        if (PetHeadManager.isPetHeadAnimating(maid)) {
            return;
        }
        // YSM 的头部 yaw 轴和东方模型方向相反，亲吻后的别头需要单独反号。
        float headYawDegrees = -HugClientState.currentPostKissShyYawDegrees(maid.getUUID());
        float headPitchDegrees = HugClientState.currentPostKissShyPitchDegrees(maid.getUUID());
        float cueYawDegrees = HugClientState.currentHeadCueYawDegrees(maid.getUUID());
        float cuePitchDegrees = HugClientState.currentHeadCuePitchDegrees(maid.getUUID());
        float cueRollDegrees = HugClientState.currentHeadCueRollDegrees(maid.getUUID());
        if (Math.abs(headYawDegrees) < 4.0F && Math.abs(headPitchDegrees) < 1.0F
                && Math.abs(cueYawDegrees) < 0.5F && Math.abs(cuePitchDegrees) < 0.5F && Math.abs(cueRollDegrees) < 0.5F) {
            return;
        }

        float yawOffset = net.minecraft.util.Mth.clamp((headYawDegrees + cueYawDegrees) * net.minecraft.util.Mth.DEG_TO_RAD, -1.49f, 1.49f);
        /*
         * 低头 / 抬头是剧情主演出，不再把 pitch 额外压成一半。
         * 之前 6~8 度在 YSM 上几乎看不出来，视觉上就像“动作没播”。
         */
        float pitchOffset = net.minecraft.util.Mth.clamp((headPitchDegrees + cuePitchDegrees) * net.minecraft.util.Mth.DEG_TO_RAD, -0.55f, 0.55f);
        float rollOffset = net.minecraft.util.Mth.clamp(cueRollDegrees * net.minecraft.util.Mth.DEG_TO_RAD, -0.30f, 0.30f);

        float baseX = huggingBasePoseApplied ? -0.06f : 0.0f;
        float baseY = huggingBasePoseApplied ? 0.14f : 0.0f;
        float baseZ = huggingBasePoseApplied ? 0.04f : 0.0f;
        setBoneRotation(bones, "Head", baseX + pitchOffset, baseY + yawOffset, baseZ - yawOffset * 0.08f + rollOffset);
    }

    /**
     * 让 YSM 里的剧情低头 / 抬头也成为一套持续姿态，而不是只改 head 骨。
     *
     * <p>用户现在看到的一瞬间点头，本质上就是只有 head 在动。
     * 这里同步把 AllBody / UpBody / UpperBody / Neck / 肩膀一起纳入 cue，
     * 告白场景里的“低着头不敢看你”就会稳定很多。
     */
    private static void applyHeadCuePose(EntityMaid maid, Map<String, Object> bones,
                                         boolean huggingBasePoseApplied) throws Exception {
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

        setBoneRotationAny(bones, lerp(resolvePoseValue(allBodyBaseX, fromPose != null ? fromPose.bodyX() : allBodyBaseX, fromPoseAlpha), returnToNeutral ? allBodyBaseX : pose.bodyX(), cueProgress), 0.0F, 0.0F, "AllBody", "allBody", "Body", "body");
        setBoneRotationAny(bones, lerp(resolvePoseValue(upBodyBaseX, fromPose != null ? fromPose.upBodyX() : upBodyBaseX, fromPoseAlpha), returnToNeutral ? upBodyBaseX : pose.upBodyX(), cueProgress), 0.0F, 0.0F, "UpBody", "upBody");
        setBoneRotationAny(bones, lerp(resolvePoseValue(upperBodyBaseX, fromPose != null ? fromPose.upperBodyX() : upperBodyBaseX, fromPoseAlpha), returnToNeutral ? upperBodyBaseX : pose.upperBodyX(), cueProgress), 0.0F, 0.0F, "UpperBody", "upperBody");
        setBoneRotationAny(bones, lerp(resolvePoseValue(neckBaseX, fromPose != null ? fromPose.neckX() : neckBaseX, fromPoseAlpha), returnToNeutral ? neckBaseX : pose.neckX(), cueProgress), 0.0F, 0.0F, "Neck", "neck");
        setBoneRotationAny(bones, lerp(resolvePoseValue(headBaseX, fromPose != null ? fromPose.headX() : headBaseX, fromPoseAlpha), returnToNeutral ? headBaseX : pose.headX(), cueProgress), lerp(resolvePoseValue(headBaseY, fromPose != null ? fromPose.headY() : headBaseY, fromPoseAlpha), returnToNeutral ? headBaseY : pose.headY(), cueProgress), lerp(resolvePoseValue(headBaseZ, fromPose != null ? fromPose.headZ() : headBaseZ, fromPoseAlpha), returnToNeutral ? headBaseZ : pose.headZ(), cueProgress), "Head", "head");
        setBoneRotationAny(bones,
                lerp(resolvePoseValue(leftShoulderBaseX, fromPose != null ? fromPose.leftShoulderX() : leftShoulderBaseX, fromPoseAlpha), returnToNeutral ? leftShoulderBaseX : pose.leftShoulderX(), cueProgress),
                lerp(resolvePoseValue(leftShoulderBaseY, fromPose != null ? fromPose.leftShoulderY() : leftShoulderBaseY, fromPoseAlpha), returnToNeutral ? leftShoulderBaseY : pose.leftShoulderY(), cueProgress),
                lerp(resolvePoseValue(leftShoulderBaseZ, fromPose != null ? fromPose.leftShoulderZ() : leftShoulderBaseZ, fromPoseAlpha), returnToNeutral ? leftShoulderBaseZ : pose.leftShoulderZ(), cueProgress),
                "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        setBoneRotationAny(bones,
                lerp(resolvePoseValue(rightShoulderBaseX, fromPose != null ? fromPose.rightShoulderX() : rightShoulderBaseX, fromPoseAlpha), returnToNeutral ? rightShoulderBaseX : pose.rightShoulderX(), cueProgress),
                lerp(resolvePoseValue(rightShoulderBaseY, fromPose != null ? fromPose.rightShoulderY() : rightShoulderBaseY, fromPoseAlpha), returnToNeutral ? rightShoulderBaseY : pose.rightShoulderY(), cueProgress),
                lerp(resolvePoseValue(rightShoulderBaseZ, fromPose != null ? fromPose.rightShoulderZ() : rightShoulderBaseZ, fromPoseAlpha), returnToNeutral ? rightShoulderBaseZ : pose.rightShoulderZ(), cueProgress),
                "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");
    }

    private static float resolvePoseValue(float baseValue, float poseValue, float poseAlpha) {
        return lerp(baseValue, poseValue, Math.max(0.0F, Math.min(1.0F, poseAlpha)));
    }

    /**
     * YSM ????????
     *
     * <p>????????????????????????
     * ?????????? + ??????????????????
     */
    private static void applyShyCoverFacePose(EntityMaid maid, Map<String, Object> bones,
                                              boolean huggingBasePoseApplied) throws Exception {
        float progress = HugClientState.currentShyCoverFaceProgress(maid.getUUID());
        if (progress <= 0.0F) {
            return;
        }

        float headBaseX = huggingBasePoseApplied ? -0.06F : 0.0F;
        float headBaseY = huggingBasePoseApplied ? 0.14F : 0.0F;
        float headBaseZ = huggingBasePoseApplied ? 0.04F : 0.0F;
        setBoneRotationAny(bones,
                lerp(headBaseX, 0.12F, progress),
                lerp(headBaseY, 0.0F, progress),
                lerp(headBaseZ, 0.0F, progress),
                "Head", "head");

        float leftShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float leftShoulderBaseY = huggingBasePoseApplied ? 0.18F : 0.0F;
        float leftShoulderBaseZ = huggingBasePoseApplied ? -0.15F : 0.0F;
        float rightShoulderBaseX = huggingBasePoseApplied ? 0.06F : 0.0F;
        float rightShoulderBaseY = huggingBasePoseApplied ? -0.18F : 0.0F;
        float rightShoulderBaseZ = huggingBasePoseApplied ? 0.15F : 0.0F;
        setBoneRotationAny(bones,
                lerp(leftShoulderBaseX, 0.14F, progress),
                lerp(leftShoulderBaseY, -0.08F, progress),
                lerp(leftShoulderBaseZ, -0.18F, progress),
                "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        setBoneRotationAny(bones,
                lerp(rightShoulderBaseX, 0.14F, progress),
                lerp(rightShoulderBaseY, 0.08F, progress),
                lerp(rightShoulderBaseZ, 0.18F, progress),
                "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");

        float leftArmBaseX = huggingBasePoseApplied ? 1.18F : 0.0F;
        float leftArmBaseY = huggingBasePoseApplied ? 0.48F : 0.0F;
        float leftArmBaseZ = huggingBasePoseApplied ? -0.54F : 0.0F;
        float rightArmBaseX = huggingBasePoseApplied ? 1.18F : 0.0F;
        float rightArmBaseY = huggingBasePoseApplied ? -0.48F : 0.0F;
        float rightArmBaseZ = huggingBasePoseApplied ? 0.54F : 0.0F;
        setBoneRotationAny(bones,
                lerp(leftArmBaseX, 1.02F, progress),
                lerp(leftArmBaseY, -0.14F, progress),
                lerp(leftArmBaseZ, -0.34F, progress),
                "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        setBoneRotationAny(bones,
                lerp(rightArmBaseX, 1.02F, progress),
                lerp(rightArmBaseY, 0.14F, progress),
                lerp(rightArmBaseZ, 0.34F, progress),
                "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");

        float leftForeArmBaseX = huggingBasePoseApplied ? 0.32F : 0.0F;
        float leftForeArmBaseY = huggingBasePoseApplied ? -0.22F : 0.0F;
        float leftForeArmBaseZ = huggingBasePoseApplied ? 0.16F : 0.0F;
        float rightForeArmBaseX = huggingBasePoseApplied ? 0.32F : 0.0F;
        float rightForeArmBaseY = huggingBasePoseApplied ? 0.22F : 0.0F;
        float rightForeArmBaseZ = huggingBasePoseApplied ? -0.16F : 0.0F;
        setBoneRotationAny(bones,
                lerp(leftForeArmBaseX, 1.26F, progress),
                lerp(leftForeArmBaseY, 0.24F, progress),
                lerp(leftForeArmBaseZ, 0.12F, progress),
                "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm", "armLeft2");
        setBoneRotationAny(bones,
                lerp(rightForeArmBaseX, 1.26F, progress),
                lerp(rightForeArmBaseY, -0.24F, progress),
                lerp(rightForeArmBaseZ, -0.12F, progress),
                "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm", "armRight2");
    }

    private static void applyShyPeekPose(EntityMaid maid, Map<String, Object> bones,
                                         boolean huggingBasePoseApplied) throws Exception {
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

        float allBodyBaseX = huggingBasePoseApplied ? 0.10F : 0.0F;
        float upBodyBaseX = huggingBasePoseApplied ? 0.18F : 0.0F;
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

        setBoneRotationAny(bones, lerp(allBodyBaseX, lerp(allBodyBaseX, pose.bodyX(), 0.42F), scaled), 0.0F, 0.0F, "AllBody", "allBody", "Body", "body");
        setBoneRotationAny(bones, lerp(upBodyBaseX, lerp(upBodyBaseX, pose.upBodyX(), 0.42F), scaled), 0.0F, 0.0F, "UpBody", "upBody");
        setBoneRotationAny(bones, lerp(upperBodyBaseX, lerp(upperBodyBaseX, pose.upperBodyX(), 0.42F), scaled), 0.0F, 0.0F, "UpperBody", "upperBody");
        setBoneRotationAny(bones, lerp(neckBaseX, lerp(neckBaseX, pose.neckX(), 0.42F), scaled), 0.0F, 0.0F, "Neck", "neck");
        setBoneRotationAny(bones, lerp(headBaseX, lerp(headBaseX, pose.headX(), 0.42F), scaled), lerp(headBaseY, lerp(headBaseY, pose.headY(), 0.42F), scaled), lerp(headBaseZ, lerp(headBaseZ, pose.headZ(), 0.42F), scaled), "Head", "head");
        setBoneRotationAny(bones,
                lerp(leftShoulderBaseX, lerp(leftShoulderBaseX, pose.leftShoulderX(), 0.42F), scaled),
                lerp(leftShoulderBaseY, lerp(leftShoulderBaseY, pose.leftShoulderY(), 0.42F), scaled),
                lerp(leftShoulderBaseZ, lerp(leftShoulderBaseZ, pose.leftShoulderZ(), 0.42F), scaled),
                "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        setBoneRotationAny(bones,
                lerp(rightShoulderBaseX, lerp(rightShoulderBaseX, pose.rightShoulderX(), 0.42F), scaled),
                lerp(rightShoulderBaseY, lerp(rightShoulderBaseY, pose.rightShoulderY(), 0.42F), scaled),
                lerp(rightShoulderBaseZ, lerp(rightShoulderBaseZ, pose.rightShoulderZ(), 0.42F), scaled),
                "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");
    }

    private static void applyChestTapPose(EntityMaid maid, Map<String, Object> bones,
                                          boolean huggingBasePoseApplied) throws Exception {
        float poseProgress = HugClientState.currentChestTapPoseProgress(maid.getUUID());
        if (poseProgress <= 0.0F) {
            return;
        }
        float hitStrength = HugClientState.currentChestTapHitStrength(maid.getUUID());
        StoryChestTapPoseLibrary.ChestTapPose pose = StoryChestTapPoseLibrary.resolve(
                StoryChestTapPoseLibrary.RigKind.GECKO,
                huggingBasePoseApplied
        );

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

        setBoneRotationAny(bones,
                lerp(leftShoulderBaseX, pose.leftShoulderX(), poseProgress),
                lerp(leftShoulderBaseY, pose.leftShoulderY(), poseProgress),
                lerp(leftShoulderBaseZ, pose.leftShoulderZ(), poseProgress),
                "LeftShoulder", "leftShoulder", "shoulderLeft", "left_shoulder");
        setBoneRotationAny(bones,
                lerp(rightShoulderBaseX, pose.rightShoulderX(), poseProgress),
                lerp(rightShoulderBaseY, pose.rightShoulderY(), poseProgress),
                lerp(rightShoulderBaseZ, pose.rightShoulderZ(), poseProgress),
                "RightShoulder", "rightShoulder", "shoulderRight", "right_shoulder");
        setBoneRotationAny(bones,
                lerp(leftArmBaseX, pose.leftArmX(), poseProgress),
                lerp(leftArmBaseY, pose.leftArmY(), poseProgress),
                lerp(leftArmBaseZ, pose.leftArmZ(), poseProgress),
                "LeftArm", "leftArm", "armLeft", "ArmLeft", "left_arm");
        setBoneRotationAny(bones,
                lerp(rightArmBaseX, pose.rightArmX() + hitStrength * 0.10F, poseProgress),
                lerp(rightArmBaseY, pose.rightArmY() + hitStrength * 0.08F, poseProgress),
                lerp(rightArmBaseZ, pose.rightArmZ() + hitStrength * 0.06F, poseProgress),
                "RightArm", "rightArm", "armRight", "ArmRight", "right_arm");
        setBoneRotationAny(bones,
                lerp(leftForeArmBaseX, pose.leftForeArmX(), poseProgress),
                lerp(leftForeArmBaseY, pose.leftForeArmY(), poseProgress),
                lerp(leftForeArmBaseZ, pose.leftForeArmZ(), poseProgress),
                "LeftForeArm", "leftForeArm", "foreArmLeft", "left_fore_arm", "armLeft2");
        setBoneRotationAny(bones,
                lerp(rightForeArmBaseX, pose.rightForeArmX() + hitStrength * 0.34F, poseProgress),
                lerp(rightForeArmBaseY, pose.rightForeArmY() - hitStrength * 0.12F, poseProgress),
                lerp(rightForeArmBaseZ, pose.rightForeArmZ(), poseProgress),
                "RightForeArm", "rightForeArm", "foreArmRight", "right_fore_arm", "armRight2");
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static EntityMaid resolveMaid(Object ysmWrapper) throws Exception {
        Object entity = invokeNoArg(ysmWrapper, WRAPPER_ENTITY_GETTER);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> collectBones(Object ysmModel) throws Exception {
        Object rawMap = invokeNoArg(ysmModel, MODEL_BONES_GETTER);
        if (!(rawMap instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, Object> result = new HashMap<>();
        Collection<?> values = map.values();
        for (Object bone : values) {
            if (bone == null) {
                continue;
            }
            Object rawName = invokeNoArg(bone, BONE_NAME_GETTER);
            if (rawName instanceof String name && !name.isEmpty()) {
                result.putIfAbsent(name, bone);
            }
        }
        return result;
    }

    /**
     * 绝对覆写单根骨骼的最终旋转。
     *
     * <p>这是本类最关键的接口：固定动作不要再做增量叠加，
     * 否则 YSM 模型包自己的 controller、riding 或中间骨链都会和我们互相叠加。
     */
    private static void setBoneRotation(Map<String, Object> bones, String name,
                                        float rotX, float rotY, float rotZ) throws Exception {
        Object bone = bones.get(name);
        if (bone == null) {
            return;
        }
        invokeFloat(bone, BONE_ROT_X_METHOD, rotX);
        invokeFloat(bone, BONE_ROT_Y_METHOD, rotY);
        invokeFloat(bone, BONE_ROT_Z_METHOD, rotZ);
    }

    /**
     * 按多个常见骨骼命名尝试覆写同一块身体部位。
     *
     * <p>不同 YSM 模型包可能沿用 Gecko 风格、Bedrock 风格或作者自定义大小写；
     * 这里不要求模型必须同时存在所有名字，只要命中其中一个就写入一次。
     */
    private static void setBoneRotationAny(Map<String, Object> bones,
                                           float rotX, float rotY, float rotZ,
                                           String... names) throws Exception {
        for (String name : names) {
            setBoneRotation(bones, name, rotX, rotY, rotZ);
        }
    }

    private static float deg(float degrees) {
        return degrees * net.minecraft.util.Mth.DEG_TO_RAD;
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void invokeFloat(Object target, String methodName, float value) throws Exception {
        Method method = target.getClass().getMethod(methodName, float.class);
        method.setAccessible(true);
        method.invoke(target, value);
    }
}
