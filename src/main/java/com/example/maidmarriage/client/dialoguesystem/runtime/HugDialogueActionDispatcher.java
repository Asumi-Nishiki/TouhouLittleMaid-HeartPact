package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.client.HugCameraZoom;
import com.example.maidmarriage.client.HugClientState;
import com.example.maidmarriage.client.ChildNameScreen;
import com.example.maidmarriage.client.GiftScreen;
import com.example.maidmarriage.client.PetHeadClientHandler;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.StoryProgressActionPayload;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 拥抱/互动剧情动作分发器。
 *
 * <p>剧情 JSON 只允许发出 {@code maidmarriage:kiss} 这类语义动作，
 * 这一层再把语义动作映射到现有的客户端触发器和网络包。
 * 这样剧情作者不能绕过白名单直接调用 Java 方法，也不会把 Screen 继续写成巨大 switch。
 */
public final class HugDialogueActionDispatcher {
    private static final ResourceLocation HUG_SCENARIO_ID = new ResourceLocation("maidmarriage", "hug_menu_v2");

    private HugDialogueActionDispatcher() {
    }

    /**
     * 取出剧情运行时积压的动作请求，并按白名单逐个执行。
     *
     * @param targetMaidUuid 当前剧情目标女仆，普通拥抱页来自成年女仆锁定，小女仆页来自固定 UUID
     * @param fixedMaidUuid 小女仆互动页的固定目标 UUID，关闭小女仆会话时需要用它发包
     * @param childInteractionMode 当前界面是否是小女仆互动页
     * @param debugSink Screen 的短提示出口，保持调试信息仍显示在左上角
     * @param closeScreen 需要关闭界面时由 Screen 自己执行，避免分发器直接依赖 Screen 类型
     */
    public static void drainAndExecute(HugDialogueRuntimeBridge dialogueRuntime,
                                       Minecraft minecraft,
                                       @Nullable UUID targetMaidUuid,
                                       @Nullable UUID fixedMaidUuid,
                                       boolean childInteractionMode,
                                       Consumer<String> debugSink,
                                       Runnable closeScreen) {
        if (dialogueRuntime == null) {
            return;
        }

        List<DialogueActionRequest> requests = dialogueRuntime.drainActionRequests();
        if (requests.isEmpty()) {
            return;
        }

        for (DialogueActionRequest request : requests) {
            execute(minecraft, request, targetMaidUuid, fixedMaidUuid, childInteractionMode, debugSink, closeScreen);
        }
    }

    private static void execute(Minecraft minecraft,
                                DialogueActionRequest request,
                                @Nullable UUID targetMaidUuid,
                                @Nullable UUID fixedMaidUuid,
                                boolean childInteractionMode,
                                Consumer<String> debugSink,
                                Runnable closeScreen) {
        if (request == null || request.actionId() == null || request.actionId().isBlank()) {
            return;
        }

        switch (request.actionId()) {
            case "maidmarriage:kiss", "maidmarriage:interaction_kiss" -> {
                PetHeadClientHandler.triggerKiss(minecraft);
                debug(debugSink, "已执行剧情动作: kiss");
            }
            case "maidmarriage:pet_head", "maidmarriage:pet" -> {
                PetHeadClientHandler.triggerPetHead(minecraft, targetMaidUuid);
                debug(debugSink, "已执行剧情动作: pet_head");
            }
            case "maidmarriage:lift", "maidmarriage:lift_child" -> {
                PetHeadClientHandler.triggerLift(minecraft, targetMaidUuid);
                debug(debugSink, "已执行剧情动作: lift");
            }
            case "maidmarriage:hug_toggle", "maidmarriage:hug_end" -> {
                PetHeadClientHandler.triggerHugPoseToggle(minecraft);
                debug(debugSink, "已执行剧情动作: hug_pose_toggle");
            }
            case "maidmarriage:carry_child" -> {
                PetHeadClientHandler.triggerCarryChild(minecraft, targetMaidUuid);
                debug(debugSink, "已执行剧情动作: carry_child");
            }
            case "maidmarriage:carry_child_put_down" -> {
                PetHeadClientHandler.triggerCarryChild(minecraft, targetMaidUuid);
                debug(debugSink, "已执行剧情动作: carry_child_put_down");
            }
            case "maidmarriage:lap_pillow_start" -> {
                PetHeadClientHandler.triggerLapPillowStart(minecraft, targetMaidUuid);
                debug(debugSink, "已执行剧情动作: lap_pillow_start");
            }
            case "maidmarriage:lap_pillow_exit" -> {
                PetHeadClientHandler.triggerLapPillowExit(minecraft);
                debug(debugSink, "已执行剧情动作: lap_pillow_exit");
            }
            case "maidmarriage:lap_pillow_pet_player_head" -> {
                PetHeadClientHandler.triggerLapPillowPetPlayerHead(minecraft, targetMaidUuid);
                debug(debugSink, "已执行剧情动作: lap_pillow_pet_player_head");
            }
            case "maidmarriage:head_lower" -> {
                executeHeadCue(targetMaidUuid, HugClientState.HeadCueType.LOWER_HEAD, request.params());
                debug(debugSink, "已执行剧情动作: head_lower");
            }
            case "maidmarriage:head_raise" -> {
                executeHeadCue(targetMaidUuid, HugClientState.HeadCueType.RAISE_HEAD, request.params());
                debug(debugSink, "已执行剧情动作: head_raise");
            }
            case "maidmarriage:head_return_neutral" -> {
                executeHeadReturnNeutral(targetMaidUuid, request.params());
                debug(debugSink, "已执行剧情动作: head_return_neutral");
            }
            case "maidmarriage:head_cue_clear" -> {
                HugClientState.clearActiveHeadCue();
                debug(debugSink, "已执行剧情动作: head_cue_clear");
            }
            case "maidmarriage:shy_cover_face" -> {
                executeShyCoverFace(targetMaidUuid, request.params());
                debug(debugSink, "已执行剧情动作: shy_cover_face");
            }
            case "maidmarriage:shy_cover_face_clear" -> {
                HugClientState.clearActiveShyCoverFace();
                debug(debugSink, "已执行剧情动作: shy_cover_face_clear");
            }
            case "maidmarriage:shy_peek_up" -> {
                executeShyPeek(targetMaidUuid, request.params());
                debug(debugSink, "已执行剧情动作: shy_peek_up");
            }
            case "maidmarriage:chest_tap_twice" -> {
                executeChestTap(targetMaidUuid, request.params());
                debug(debugSink, "已执行剧情动作: chest_tap_twice");
            }
            case "maidmarriage:story_kiss_zoom" -> {
                HugCameraZoom.playKissZoom();
                debug(debugSink, "已执行剧情动作: story_kiss_zoom");
            }
            case "maidmarriage:story_close_zoom" -> {
                HugCameraZoom.playStoryCloseZoom();
                debug(debugSink, "已执行剧情动作: story_close_zoom");
            }
            case "maidmarriage:turn_head_away" -> {
                executeTurnHeadAway(targetMaidUuid, request.params());
                debug(debugSink, "已执行剧情动作: turn_head_away");
            }
            case "maidmarriage:dialogue_result" -> {
                executeDialogueResult(minecraft, targetMaidUuid, request.params());
                debug(debugSink, "已执行剧情动作: dialogue_result");
            }
            case "maidmarriage:story_confession_accept" -> {
                sendStoryAction(targetMaidUuid, "confession_accept");
                debug(debugSink, "已执行剧情动作: story_confession_accept");
            }
            case "maidmarriage:story_open_maid_panel" -> {
                executeStoryOpenMaidPanel(targetMaidUuid, request.params());
                debug(debugSink, "已执行剧情动作: story_open_maid_panel");
            }
            case "maidmarriage:story_commit_marriage" -> {
                sendStoryAction(targetMaidUuid, "commit_marriage");
                debug(debugSink, "已执行剧情动作: story_commit_marriage");
            }
            case "maidmarriage:open_gift_screen" -> {
                if (minecraft != null) {
                    minecraft.setScreen(GiftScreen.open(minecraft.screen, targetMaidUuid));
                }
                debug(debugSink, "已执行剧情动作: open_gift_screen");
            }
            case "maidmarriage:open_child_name_screen" -> {
                if (minecraft != null) {
                    minecraft.setScreen(ChildNameScreen.open(minecraft.screen, targetMaidUuid));
                }
                debug(debugSink, "已执行剧情动作: open_child_name_screen");
            }
            case "maidmarriage:close_child_interaction" -> {
                if (childInteractionMode) {
                    PetHeadClientHandler.triggerChildInteraction(minecraft, fixedMaidUuid);
                }
                if (closeScreen != null) {
                    closeScreen.run();
                }
                }
            default -> debug(debugSink, "未注册剧情动作: " + request.actionId());
        }
    }

    private static void executeHeadCue(@Nullable UUID targetMaidUuid,
                                       HugClientState.HeadCueType cueType,
                                       Map<String, String> params) {
        if (targetMaidUuid == null || cueType == null) {
            return;
        }
        int delayTicks = parseIntParam(params, "delayTicks", 0);
        int durationTicks = parseIntParam(params, "durationTicks", 18);
        float pitchDegrees = parseFloatParam(params, "pitchDegrees", cueType == HugClientState.HeadCueType.RAISE_HEAD ? 10.0F : 12.0F);
        float yawDegrees = parseFloatParam(params, "yawDegrees", 0.0F);
        float rollDegrees = parseFloatParam(params, "rollDegrees", 0.0F);
        HugClientState.startHeadCue(targetMaidUuid, cueType, delayTicks, durationTicks, yawDegrees, pitchDegrees, rollDegrees);
    }

    private static void executeHeadReturnNeutral(@Nullable UUID targetMaidUuid, Map<String, String> params) {
        if (targetMaidUuid == null) {
            return;
        }
        int delayTicks = parseIntParam(params, "delayTicks", 0);
        int durationTicks = parseIntParam(params, "durationTicks", 18);
        HugClientState.returnHeadToNeutral(targetMaidUuid, delayTicks, durationTicks);
    }

    private static void executeDialogueResult(Minecraft minecraft, @Nullable UUID targetMaidUuid, Map<String, String> params) {
        int sharedMoodDelta = parseIntParam(params, "moodDelta", 0);
        PetHeadClientHandler.triggerDialogueChoiceResult(
                minecraft,
                targetMaidUuid,
                parseIntParam(params, "positiveFavor", 2),
                parseIntParam(params, "neutralFavor", 0),
                parseIntParam(params, "negativeFavor", -1),
                parseIntParam(params, "positiveMoodDelta", sharedMoodDelta),
                parseIntParam(params, "neutralMoodDelta", sharedMoodDelta),
                parseIntParam(params, "negativeMoodDelta", sharedMoodDelta)
        );
    }

    private static void executeShyCoverFace(@Nullable UUID targetMaidUuid, Map<String, String> params) {
        if (targetMaidUuid == null) {
            return;
        }
        int delayTicks = parseIntParam(params, "delayTicks", 0);
        int durationTicks = parseIntParam(params, "durationTicks", 24);
        HugClientState.startShyCoverFace(targetMaidUuid, delayTicks, durationTicks);
    }

    private static void executeShyPeek(@Nullable UUID targetMaidUuid, Map<String, String> params) {
        if (targetMaidUuid == null) {
            return;
        }
        int delayTicks = parseIntParam(params, "delayTicks", 0);
        int durationTicks = parseIntParam(params, "durationTicks", 24);
        HugClientState.startShyPeek(targetMaidUuid, delayTicks, durationTicks);
    }

    private static void executeChestTap(@Nullable UUID targetMaidUuid, Map<String, String> params) {
        if (targetMaidUuid == null) {
            return;
        }
        int delayTicks = parseIntParam(params, "delayTicks", 0);
        int durationTicks = parseIntParam(params, "durationTicks", 24);
        HugClientState.startChestTap(targetMaidUuid, delayTicks, durationTicks);
    }

    private static void executeTurnHeadAway(@Nullable UUID targetMaidUuid, Map<String, String> params) {
        if (targetMaidUuid == null) {
            return;
        }
        int delayTicks = parseIntParam(params, "delayTicks", 0);
        int durationTicks = parseIntParam(params, "durationTicks", 24);
        int enterTicks = parseIntParam(params, "enterTicks", 10);
        int returnTicks = parseIntParam(params, "returnTicks", 8);
        float yawDegrees = parseFloatParam(params, "yawDegrees", 42.0F);
        float pitchDegrees = parseFloatParam(params, "pitchDegrees", 5.0F);
        int directionSign = parseIntParam(params, "directionSign", 1);
        HugClientState.startPostKissShyTurn(
                targetMaidUuid,
                delayTicks,
                durationTicks,
                yawDegrees,
                pitchDegrees,
                directionSign,
                enterTicks,
                returnTicks
        );
    }

    private static void executeStoryOpenMaidPanel(@Nullable UUID targetMaidUuid, Map<String, String> params) {
        if (targetMaidUuid == null) {
            return;
        }
        String resumeNode = params == null ? "" : params.getOrDefault("resumeNode", "");
        if (!resumeNode.isBlank()) {
            HugStoryResumeState.remember(targetMaidUuid, HUG_SCENARIO_ID, resumeNode);
        }
        sendStoryAction(targetMaidUuid, "open_marriage_panel");
    }

    private static void sendStoryAction(@Nullable UUID maidUuid, String actionId) {
        ModNetworking.sendStoryProgressAction(new StoryProgressActionPayload(maidUuid, actionId));
    }

    private static int parseIntParam(Map<String, String> params, String key, int fallback) {
        if (params == null || key == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(params.getOrDefault(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloatParam(Map<String, String> params, String key, float fallback) {
        if (params == null || key == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(params.getOrDefault(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void debug(Consumer<String> debugSink, String message) {
        if (debugSink != null && ModConfigs.showUiActionDebug()) {
            debugSink.accept(message);
        }
    }
}
