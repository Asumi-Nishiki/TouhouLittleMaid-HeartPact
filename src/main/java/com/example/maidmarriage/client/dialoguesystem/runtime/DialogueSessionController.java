package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.client.dialoguesystem.DialogueScenario;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenario.Branch;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenario.Choice;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenario.Event;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenario.Line;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenario.Node;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenario.NodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话会话控制器。
 *
 * <p>这就是本次重构最核心的收口层：
 * - 剧情怎么跳，由它决定；
 * - 当前应该显示哪一句，由它决定；
 * - 事件什么时候触发，由它决定；
 * - UI 只拿结果，不再自己决定业务流程。
 *
 * <p>当前版本有两个刻意的边界：
 * 1. 先不接旧的拥抱/亲吻逻辑，只把动作语义排队；
 * 2. 先不强行接到现有 HugActionScreen，避免一边重构一边继续堆屎山。
 */
public final class DialogueSessionController {
    private final DialogueScenario scenario;
    private final DialogueEventRegistry eventRegistry;
    private final DialogueConditionEvaluator conditionEvaluator;
    private final DialogueRuntimeContext runtimeContext = new DialogueRuntimeContext();

    private String currentNodeId = "";
    private int currentLineIndex;
    private boolean started;
    private boolean ended;

    public DialogueSessionController(DialogueScenario scenario,
                                     DialogueEventRegistry eventRegistry,
                                     DialogueConditionEvaluator conditionEvaluator) {
        this.scenario = scenario == null ? new DialogueScenario() : scenario;
        this.eventRegistry = eventRegistry == null ? DialogueEventRegistry.createDefault() : eventRegistry;
        this.conditionEvaluator = conditionEvaluator == null ? new SimpleDialogueConditionEvaluator() : conditionEvaluator;
    }

    public DialogueRuntimeContext runtimeContext() {
        return runtimeContext;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        jumpToNode(scenario.start);
        settleUntilDisplayable();
    }

    public boolean isEnded() {
        return ended;
    }

    public String currentNodeId() {
        return currentNodeId;
    }

    public DialogueFrameView currentFrame() {
        if (!started) {
            start();
        }
        if (ended) {
            return new DialogueFrameView(
                    scenario.id,
                    currentNodeId,
                    currentLineIndex,
                    "",
                    "",
                    runtimeContext.currentPortrait(),
                    resolvePortraitTexture(runtimeContext.currentPortrait(), runtimeContext.currentExpression()),
                    runtimeContext.currentExpression(),
                    runtimeContext.currentAnimation(),
                    false,
                    true,
                    List.of()
            );
        }

        Node node = currentNode();
        if (node == null) {
            ended = true;
            return currentFrame();
        }

        NodeType nodeType = DialogueScenario.resolveNodeType(node.type);
        return switch (nodeType) {
            case SEQUENCE -> buildSequenceFrame(node);
            case CHOICE -> buildChoiceFrame(node);
            case ACTION, BRANCH, END -> new DialogueFrameView(
                    scenario.id,
                    currentNodeId,
                    currentLineIndex,
                    "",
                    "",
                    runtimeContext.currentPortrait(),
                    resolvePortraitTexture(runtimeContext.currentPortrait(), runtimeContext.currentExpression()),
                    runtimeContext.currentExpression(),
                    runtimeContext.currentAnimation(),
                    false,
                    ended,
                    List.of()
            );
        };
    }

    public boolean advance() {
        if (!started) {
            start();
        }
        if (ended) {
            return false;
        }

        Node node = currentNode();
        if (node == null) {
            ended = true;
            return false;
        }

        if (DialogueScenario.resolveNodeType(node.type) != NodeType.SEQUENCE) {
            return false;
        }

        Line currentLine = currentLine(node);
        if (currentLine != null) {
            fireEvents(currentLine.events, "line_end");
            String jumpTarget = runtimeContext.consumeRequestedJump();
            if (!jumpTarget.isBlank()) {
                jumpToNode(jumpTarget);
                settleUntilDisplayable();
                return !ended;
            }
        }

        if (currentLineIndex + 1 < node.lines.size()) {
            currentLineIndex++;
            applyLinePresentation(currentLine(node));
            fireEvents(currentLine(node).events, "line_start");
            String jumpTarget = runtimeContext.consumeRequestedJump();
            if (!jumpTarget.isBlank()) {
                jumpToNode(jumpTarget);
                settleUntilDisplayable();
            }
            return true;
        }

        fireEvents(node.events, "exit");
        jumpToNode(node.next);
        settleUntilDisplayable();
        return !ended;
    }

    public boolean choose(String choiceId) {
        if (!started) {
            start();
        }
        if (ended) {
            return false;
        }

        Node node = currentNode();
        if (node == null || DialogueScenario.resolveNodeType(node.type) != NodeType.CHOICE) {
            return false;
        }

        for (Choice choice : node.choices) {
            if (!choice.id.equals(choiceId)) {
                continue;
            }
            if (!conditionEvaluator.test(choice.condition, runtimeContext)) {
                return false;
            }

            applyChoicePresentation(choice);
            fireEvents(choice.events, "choice");
            if (!choice.action.isBlank()) {
                runtimeContext.emitAction(choice.action, Map.of());
            }

            String jumpTarget = runtimeContext.consumeRequestedJump();
            if (!jumpTarget.isBlank()) {
                jumpToNode(jumpTarget);
            } else if (!choice.next.isBlank()) {
                jumpToNode(choice.next);
            } else {
                jumpToNode(node.next);
            }
            settleUntilDisplayable();
            return true;
        }
        return false;
    }

    public void jumpToNodeAndSettle(String nodeId) {
        if (!started) {
            start();
        }
        if (ended) {
            return;
        }
        jumpToNode(nodeId);
        settleUntilDisplayable();
    }

    private void settleUntilDisplayable() {
        int guard = 0;
        while (!ended && guard++ < 64) {
            Node node = currentNode();
            if (node == null) {
                ended = true;
                return;
            }

            NodeType nodeType = DialogueScenario.resolveNodeType(node.type);
            switch (nodeType) {
                case SEQUENCE -> {
                    currentLineIndex = 0;
                    fireEvents(node.events, "enter");
                    String jumpTarget = runtimeContext.consumeRequestedJump();
                    if (!jumpTarget.isBlank()) {
                        jumpToNode(jumpTarget);
                        continue;
                    }

                    Line firstLine = currentLine(node);
                    if (firstLine == null) {
                        jumpToNode(node.next);
                        continue;
                    }
                    applyLinePresentation(firstLine);
                    fireEvents(firstLine.events, "line_start");
                    jumpTarget = runtimeContext.consumeRequestedJump();
                    if (!jumpTarget.isBlank()) {
                        jumpToNode(jumpTarget);
                        continue;
                    }
                    return;
                }
                case CHOICE -> {
                    applyPromptPresentation(node.prompt);
                    fireEvents(node.events, "enter");
                    fireEvents(node.prompt.events, "enter");
                    String jumpTarget = runtimeContext.consumeRequestedJump();
                    if (!jumpTarget.isBlank()) {
                        jumpToNode(jumpTarget);
                        continue;
                    }
                    return;
                }
                case ACTION -> {
                    fireEvents(node.events, "enter");
                    if (!node.action.isBlank()) {
                        runtimeContext.emitAction(node.action, Map.of());
                    }
                    String jumpTarget = runtimeContext.consumeRequestedJump();
                    jumpToNode(jumpTarget.isBlank() ? node.next : jumpTarget);
                }
                case BRANCH -> {
                    fireEvents(node.events, "enter");
                    String resolvedNext = resolveBranchNext(node);
                    String jumpTarget = runtimeContext.consumeRequestedJump();
                    jumpToNode(jumpTarget.isBlank() ? resolvedNext : jumpTarget);
                }
                case END -> {
                    fireEvents(node.events, "enter");
                    ended = true;
                    return;
                }
            }
        }
    }

    private void jumpToNode(String nodeId) {
        runtimeContext.clearTransientPresentation();
        if (nodeId == null || nodeId.isBlank()) {
            ended = true;
            currentNodeId = "";
            return;
        }
        currentNodeId = nodeId;
        currentLineIndex = 0;
        ended = !scenario.nodes.containsKey(nodeId);
    }

    private String resolveBranchNext(Node node) {
        for (Branch branch : node.branches) {
            if (conditionEvaluator.test(branch.condition, runtimeContext)) {
                return branch.next;
            }
        }
        return node.next;
    }

    private Node currentNode() {
        if (currentNodeId == null || currentNodeId.isBlank()) {
            return null;
        }
        return scenario.nodes.get(currentNodeId);
    }

    private Line currentLine(Node node) {
        if (node == null || node.lines.isEmpty()) {
            return null;
        }
        int safeIndex = Math.max(0, Math.min(node.lines.size() - 1, currentLineIndex));
        return node.lines.get(safeIndex);
    }

    private DialogueFrameView buildSequenceFrame(Node node) {
        Line line = currentLine(node);
        if (line == null) {
            return new DialogueFrameView(scenario.id, currentNodeId, currentLineIndex, "", "",
                    runtimeContext.currentPortrait(),
                    resolvePortraitTexture(runtimeContext.currentPortrait(), runtimeContext.currentExpression()),
                    runtimeContext.currentExpression(), runtimeContext.currentAnimation(),
                    false, ended, List.of());
        }
        return new DialogueFrameView(
                scenario.id,
                currentNodeId,
                currentLineIndex,
                line.speaker,
                line.text,
                runtimeContext.currentPortrait(),
                resolvePortraitTexture(runtimeContext.currentPortrait(), runtimeContext.currentExpression()),
                runtimeContext.currentExpression(),
                runtimeContext.currentAnimation(),
                false,
                false,
                List.of()
        );
    }

    private DialogueFrameView buildChoiceFrame(Node node) {
        List<DialogueChoiceView> choiceViews = new ArrayList<>();
        for (Choice choice : node.choices) {
            boolean available = conditionEvaluator.test(choice.condition, runtimeContext);
            choiceViews.add(new DialogueChoiceView(choice.id, choice.title, choice.description, available));
        }
        return new DialogueFrameView(
                scenario.id,
                currentNodeId,
                0,
                node.prompt.speaker,
                node.prompt.text,
                runtimeContext.currentPortrait(),
                resolvePortraitTexture(runtimeContext.currentPortrait(), runtimeContext.currentExpression()),
                runtimeContext.currentExpression(),
                runtimeContext.currentAnimation(),
                true,
                false,
                List.copyOf(choiceViews)
        );
    }

    private void applyPromptPresentation(DialogueScenario.Prompt prompt) {
        if (prompt == null) {
            return;
        }
        String portrait = runtimeContext.renderTemplate(prompt.portrait);
        String expression = runtimeContext.renderTemplate(prompt.expression);
        String animation = runtimeContext.renderTemplate(prompt.animation);
        if (!portrait.isBlank()) {
            runtimeContext.setCurrentPortrait(portrait);
        }
        if (!expression.isBlank()) {
            runtimeContext.setCurrentExpression(expression);
        }
        if (!animation.isBlank()) {
            runtimeContext.setCurrentAnimation(animation);
        }
    }

    private void applyLinePresentation(Line line) {
        if (line == null) {
            return;
        }
        applyPromptPresentation(line);
    }

    private void applyChoicePresentation(Choice choice) {
        if (choice == null) {
            return;
        }
        String portrait = runtimeContext.renderTemplate(choice.portrait);
        String expression = runtimeContext.renderTemplate(choice.expression);
        String animation = runtimeContext.renderTemplate(choice.animation);
        if (!portrait.isBlank()) {
            runtimeContext.setCurrentPortrait(portrait);
        }
        if (!expression.isBlank()) {
            runtimeContext.setCurrentExpression(expression);
        }
        if (!animation.isBlank()) {
            runtimeContext.setCurrentAnimation(animation);
        }
    }

    private void fireEvents(List<Event> events, String when) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (Event event : events) {
            if (!when.equalsIgnoreCase(event.when)) {
                continue;
            }
            eventRegistry.fire(event, runtimeContext);
        }
    }

    private String resolvePortraitTexture(String portraitId, String expressionId) {
        if (portraitId == null || portraitId.isBlank()) {
            return "";
        }
        DialogueScenario.PortraitProfile profile = scenario.portraits.get(portraitId);
        if (profile == null) {
            return "";
        }
        String resolvedExpression = expressionId;
        if (resolvedExpression == null || resolvedExpression.isBlank()) {
            resolvedExpression = profile.defaultExpression;
        }
        return profile.expressions.getOrDefault(resolvedExpression, profile.texture);
    }
}
