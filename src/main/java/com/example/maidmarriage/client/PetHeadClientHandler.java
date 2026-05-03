package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.example.maidmarriage.compat.MaidLiftManager;
import com.example.maidmarriage.compat.PetHeadManager;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.CarryChildMaidPayload;
import com.example.maidmarriage.network.payload.ChildInteractionPayload;
import com.example.maidmarriage.network.payload.DialogueChoiceResultPayload;
import com.example.maidmarriage.network.payload.HugMaidPayload;
import com.example.maidmarriage.network.payload.KissMaidPayload;
import com.example.maidmarriage.network.payload.LiftMaidPayload;
import com.example.maidmarriage.network.payload.PetHeadPayload;
import com.example.maidmarriage.network.payload.ToggleHugPosePayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端交互分发入口。
 *
 * <p>现在世界内只保留两个快捷入口：
 * 1. 统一交互键：进入/退出成年女仆或小女仆互动会话；
 * 2. 坐下摸头键：仅对准星下的坐姿女仆生效。
 *
 * <p>其余动作（亲吻、切换拥抱、举高高、大女仆抱小女仆）全部改为由互动面板按钮显式调用，
 * 这里仅保留它们的客户端发包入口，避免再次和世界热键耦合。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PetHeadClientHandler {
    private static final java.util.Map<UUID, Long> CLIENT_PET_HEAD_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();

    private PetHeadClientHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        cleanupClientPredictedPetHead(mc);
        if (RhythmKeyMappings.MAID_DEBUG_PANEL.consumeClick()) {
            if (ModConfigs.enableDebugTools()) {
                MaidDebugPanelScreen.open(mc);
            }
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof HugActionScreen)) {
            return;
        }

        if (RhythmKeyMappings.PET_HEAD.consumeClick()) {
            triggerSeatedPetHeadShortcut(mc);
        }
        if (RhythmKeyMappings.INTERACTION.consumeClick()) {
            triggerUnifiedInteraction(mc);
        }
    }

    /**
     * 解析当前动作应作用到哪只女仆。
     *
     * <p>优先级如下：
     * 1. 当前打开中的互动面板固定目标；
     * 2. 当前小女仆互动会话目标；
     * 3. 当前成年女仆互动会话目标；
     * 4. 准星下直接命中的女仆。
     */
    @Nullable
    public static UUID resolveTargetMaidUuid(Minecraft mc) {
        if (mc.screen instanceof HugActionScreen screen) {
            UUID screenTarget = screen.targetMaidUuidForActions();
            if (screenTarget != null) {
                return screenTarget;
            }
        }

        UUID childInteractionMaidUuid = ChildInteractionClientState.getLocalInteractionMaidUuid();
        if (childInteractionMaidUuid != null) {
            return childInteractionMaidUuid;
        }

        UUID interactionMaidUuid = HugClientState.getLocalInteractionMaidUuid();
        if (interactionMaidUuid != null) {
            return interactionMaidUuid;
        }

        EntityMaid maid = resolveCrosshairMaid(mc);
        return maid == null ? null : maid.getUUID();
    }

    /**
     * 切换成年女仆互动会话：
     * - 未进入会话时，尝试进入；
     * - 已进入会话时，直接退出。
     */
    public static void triggerInteraction(Minecraft mc) {
        ModNetworking.sendHugMaid(new HugMaidPayload(resolveTargetMaidUuid(mc)));
    }

    /**
     * 世界内统一交互键。
     *
     * <p>规则：
     * 1. 若当前已经在小女仆互动中，则再次按下直接退出小女仆互动；
     * 2. 若当前已经在成年女仆互动中，则再次按下直接退出成年女仆互动；
     * 3. 若尚未处于任何互动中，则只有“准星明确命中的小女仆”才进入小女仆互动；
     * 4. 其他情况默认进入成年女仆互动。
     */
    public static void triggerUnifiedInteraction(Minecraft mc) {
        if (ChildInteractionClientState.isLocalPlayerInteracting()) {
            triggerChildInteraction(mc, ChildInteractionClientState.getLocalInteractionMaidUuid());
            return;
        }
        if (HugClientState.isLocalPlayerInteracting()) {
            triggerInteraction(mc);
            return;
        }

        UUID childMaidUuid = resolveDirectChildInteractionTargetMaidUuid(mc);
        if (childMaidUuid != null) {
            triggerChildInteraction(mc, childMaidUuid);
            return;
        }

        triggerInteraction(mc);
    }

    /**
     * 在当前成年女仆互动会话里切换“拥抱 / 放开女仆”姿态。
     */
    public static void triggerHugPoseToggle(Minecraft mc) {
        ModNetworking.sendToggleHugPose(new ToggleHugPosePayload(resolveTargetMaidUuid(mc)));
    }

    public static void triggerPetHead(Minecraft mc) {
        triggerPetHead(mc, resolveTargetMaidUuid(mc));
    }

    /**
     * 世界快捷键保留“摸头 / 举高高 / 放下”的轻量语义：
     * <ul>
     *     <li>玩家自己正举着小女仆时：再次按下直接放下；</li>
     *     <li>当前是大女仆代抱状态时：再次按下让大女仆放下；</li>
     *     <li>准星指向站立小女仆时：触发举高高；</li>
     *     <li>准星指向坐姿女仆时：触发摸头。</li>
     * </ul>
     *
     * <p>这样保留了玩家已经习惯的快捷操作，同时不会和互动 UI 的显式按钮冲突。
     */
    public static void triggerSeatedPetHeadShortcut(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return;
        }

        UUID liftedMaidUuid = MaidLiftManager.getLocalLiftedMaidUuid(mc.player);
        if (liftedMaidUuid != null) {
            triggerLift(mc, liftedMaidUuid);
            return;
        }

        UUID carriedChildUuid = MaidCarryChildManager.getLocalCarriedChildUuid(mc.player);
        if (carriedChildUuid != null) {
            triggerCarryChild(mc, carriedChildUuid);
            return;
        }

        EntityMaid maid = resolveCrosshairMaid(mc);
        if (maid == null) {
            return;
        }

        if (MaidChildEntity.shouldStayChild(maid) && !maid.isMaidInSittingPose() && !maid.isPassenger()) {
            triggerLift(mc, maid.getUUID());
            return;
        }

        if (maid.isMaidInSittingPose()) {
            triggerPetHead(mc, maid.getUUID());
        }
    }

    public static void triggerKiss(Minecraft mc) {
        ModNetworking.sendKissMaid(new KissMaidPayload(resolveTargetMaidUuid(mc)));
    }

    public static void triggerDialogueChoiceResult(Minecraft mc,
                                                   @Nullable UUID maidUuid,
                                                   int positiveFavor,
                                                   int neutralFavor,
                                                   int negativeFavor,
                                                   int positiveMoodDelta,
                                                   int neutralMoodDelta,
                                                   int negativeMoodDelta) {
        UUID resolvedTarget = maidUuid != null ? maidUuid : resolveTargetMaidUuid(mc);
        ModNetworking.sendDialogueChoiceResult(new DialogueChoiceResultPayload(
                resolvedTarget,
                positiveFavor,
                neutralFavor,
                negativeFavor,
                positiveMoodDelta,
                neutralMoodDelta,
                negativeMoodDelta
        ));
    }

    /**
     * 小女仆互动面板的唯一客户端入口。
     *
     * <p>这里不再直接本地打开界面，而是发包给服务端建立互动会话，
     * 再由服务端同步状态让客户端自动弹出界面。
     */
    public static void triggerChildInteraction(Minecraft mc, @Nullable UUID maidUuid) {
        if (ChildInteractionClientState.isLocalPlayerInteracting()) {
            ChildInteractionClientState.clear();
            if (mc != null && mc.screen instanceof HugActionScreen screen && screen.isChildInteractionScreen()) {
                mc.setScreen(null);
            }
        }
        ModNetworking.sendChildInteraction(new ChildInteractionPayload(maidUuid));
    }

    public static void triggerPetHead(Minecraft mc, @Nullable UUID maidUuid) {
        ModNetworking.sendPetHead(new PetHeadPayload(maidUuid));
        markClientPredictedPetHead(mc, maidUuid);
    }

    public static void triggerLift(Minecraft mc, @Nullable UUID maidUuid) {
        ModNetworking.sendLiftMaid(new LiftMaidPayload(maidUuid));
    }

    public static void triggerCarryChild(Minecraft mc, @Nullable UUID maidUuid) {
        ModNetworking.sendCarryChildMaid(new CarryChildMaidPayload(maidUuid));
    }

    @Nullable
    private static EntityMaid resolveTargetMaid(Minecraft mc, @Nullable UUID maidUuid) {
        if (mc == null || mc.level == null || maidUuid == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EntityMaid maid && maidUuid.equals(maid.getUUID())) {
                return maid;
            }
        }
        return null;
    }

    @Nullable
    private static EntityMaid resolveCrosshairMaid(Minecraft mc) {
        if (mc == null) {
            return null;
        }
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof EntityMaid maid) {
            return maid;
        }
        return null;
    }

    private static void markClientPredictedPetHead(Minecraft mc, @Nullable UUID maidUuid) {
        EntityMaid maid = resolveTargetMaid(mc, maidUuid);
        if (maid == null) {
            maid = resolveCrosshairMaid(mc);
        }
        if (maid == null || mc == null || mc.level == null) {
            return;
        }

        boolean hugVariant = HugClientState.isLocalPlayerHugging()
                && maidUuid != null
                && maidUuid.equals(HugClientState.getLocalInteractionMaidUuid());
        long endTick = mc.level.getGameTime() + PetHeadManager.clientPredictedAnimTicks(hugVariant);
        CLIENT_PET_HEAD_UNTIL.put(maid.getUUID(), endTick);
        PetHeadManager.startClientPredictedPetHead(maid, hugVariant);
    }

    private static void cleanupClientPredictedPetHead(Minecraft mc) {
        if (mc == null || mc.level == null || CLIENT_PET_HEAD_UNTIL.isEmpty()) {
            return;
        }

        long now = mc.level.getGameTime();
        var iterator = CLIENT_PET_HEAD_UNTIL.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now < entry.getValue()) {
                continue;
            }
            EntityMaid maid = resolveTargetMaid(mc, entry.getKey());
            if (maid != null) {
                PetHeadManager.stopClientPredictedPetHead(maid);
            }
            iterator.remove();
        }
    }

    /**
     * 统一交互键用于识别“小女仆显式目标”。
     *
     * <p>只认以下两种情况：
     * 1. 当前已经在小女仆互动会话里；
     * 2. 当前准星明确指向一只小女仆。
     *
     * <p>这里故意不再做“附近最近小女仆自动吸附”，避免成年互动被误分流。
     */
    @Nullable
    private static UUID resolveDirectChildInteractionTargetMaidUuid(Minecraft mc) {
        if (mc == null) {
            return null;
        }
        if (mc.screen instanceof HugActionScreen screen && screen.isChildInteractionScreen()) {
            return screen.targetMaidUuidForActions();
        }
        UUID activeChildMaidUuid = ChildInteractionClientState.getLocalInteractionMaidUuid();
        if (activeChildMaidUuid != null) {
            return activeChildMaidUuid;
        }
        EntityMaid maid = resolveCrosshairMaid(mc);
        if (maid != null && MaidChildEntity.shouldStayChild(maid)) {
            return maid.getUUID();
        }
        return null;
    }
}
