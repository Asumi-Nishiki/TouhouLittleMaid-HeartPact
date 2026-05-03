package com.example.maidmarriage.client.dialoguesystem.runtime;

import java.util.List;

/**
 * 当前这一帧/这一页应该显示给 UI 的内容快照。
 *
 * <p>UI 层拿到这个对象之后，只需要负责：
 * - 画谁在说话；
 * - 画哪句文本；
 * - 画当前表情贴图；
 * - 画当前有哪些选项。
 *
 * <p>它不需要知道底层节点类型，也不需要知道剧情怎么跳。
 */
public record DialogueFrameView(
        String scenarioId,
        String nodeId,
        int lineIndex,
        String speaker,
        String text,
        String portraitId,
        String portraitTexture,
        String expressionId,
        String animationId,
        boolean choiceNode,
        boolean ended,
        List<DialogueChoiceView> choices
) {
}
