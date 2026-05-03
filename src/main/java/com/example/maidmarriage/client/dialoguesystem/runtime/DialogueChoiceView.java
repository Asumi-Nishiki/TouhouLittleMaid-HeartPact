package com.example.maidmarriage.client.dialoguesystem.runtime;

/**
 * 运行时可展示选项视图。
 *
 * <p>这一层已经把“原始剧情定义”变成了 UI 真正关心的数据：
 * 标题、描述、是否可点、点击要传回去的 choiceId。
 */
public record DialogueChoiceView(
        String id,
        String title,
        String description,
        boolean available
) {
}
