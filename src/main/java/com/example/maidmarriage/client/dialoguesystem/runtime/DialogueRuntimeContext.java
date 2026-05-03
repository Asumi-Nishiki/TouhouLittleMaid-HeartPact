package com.example.maidmarriage.client.dialoguesystem.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 剧情运行时上下文。
 *
 * <p>这是新框架的“真相源”之一：
 * - 剧情变量放这里；
 * - 当前立绘/表情/动画覆盖状态放这里；
 * - 剧情抛出的动作请求也先缓存这里；
 * - 事件如果要求跳转节点，也先在这里登记。
 */
public final class DialogueRuntimeContext {
    private final Map<String, String> variables = new LinkedHashMap<>();
    private final Deque<DialogueActionRequest> pendingActions = new ArrayDeque<>();
    private String currentPortrait = "";
    private String currentExpression = "";
    private String currentAnimation = "";
    private String requestedJumpNode = "";

    public String getVariable(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return variables.getOrDefault(key, "");
    }

    public void setVariable(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        variables.put(key, value == null ? "" : value);
    }

    public Map<String, String> variablesView() {
        return Map.copyOf(variables);
    }

    public String renderTemplate(String raw) {
        String rendered = raw == null ? "" : raw;
        for (int pass = 0; pass < 3; pass++) {
            String before = rendered;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() == null ? "" : entry.getValue();
                rendered = rendered.replace("${" + key + "}", value);
                rendered = rendered.replace("{" + key + "}", value);
            }
            if (before.equals(rendered)) {
                break;
            }
        }
        return rendered;
    }

    public void setCurrentPortrait(String currentPortrait) {
        this.currentPortrait = currentPortrait == null ? "" : currentPortrait;
    }

    public String currentPortrait() {
        return currentPortrait;
    }

    public void setCurrentExpression(String currentExpression) {
        this.currentExpression = currentExpression == null ? "" : currentExpression;
    }

    public String currentExpression() {
        return currentExpression;
    }

    public void setCurrentAnimation(String currentAnimation) {
        this.currentAnimation = currentAnimation == null ? "" : currentAnimation;
    }

    public String currentAnimation() {
        return currentAnimation;
    }

    public void emitAction(String actionId, Map<String, String> params) {
        if (actionId == null || actionId.isBlank()) {
            return;
        }
        pendingActions.addLast(new DialogueActionRequest(actionId, params));
    }

    public Deque<DialogueActionRequest> pendingActions() {
        return pendingActions;
    }

    public DialogueActionRequest pollAction() {
        return pendingActions.pollFirst();
    }

    public void requestJump(String nodeId) {
        this.requestedJumpNode = nodeId == null ? "" : nodeId;
    }

    public String consumeRequestedJump() {
        String jumpTarget = requestedJumpNode;
        requestedJumpNode = "";
        return jumpTarget;
    }

    public void clearTransientPresentation() {
        currentAnimation = "";
    }
}
