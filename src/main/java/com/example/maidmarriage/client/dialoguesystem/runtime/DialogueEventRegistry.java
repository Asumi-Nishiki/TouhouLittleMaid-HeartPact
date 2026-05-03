package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.client.dialoguesystem.DialogueScenario;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全事件注册表。
 *
 * <p>当前只放“重构阶段确定需要的、安全的事件”：
 * - 切换头像槽位；
 * - 切换表情；
 * - 播放 UI 动画；
 * - 设置剧情变量；
 * - 发出动作请求；
 * - 跳转节点。
 *
 * <p>注意：这里不会直接碰旧的拥抱/亲吻业务逻辑。
 */
public final class DialogueEventRegistry {
    private final Map<String, DialogueEventHandler> handlers = new HashMap<>();

    public DialogueEventRegistry register(String type, DialogueEventHandler handler) {
        if (type != null && !type.isBlank() && handler != null) {
            handlers.put(type.trim().toLowerCase(), handler);
        }
        return this;
    }

    public void fire(DialogueScenario.Event event, DialogueRuntimeContext context) {
        if (event == null || event.type == null || event.type.isBlank()) {
            return;
        }
        DialogueEventHandler handler = handlers.get(event.type.trim().toLowerCase());
        if (handler != null) {
            handler.handle(event, context);
        }
    }

    public static DialogueEventRegistry createDefault() {
        DialogueEventRegistry registry = new DialogueEventRegistry();
        registry.register("set_portrait", (event, context) -> context.setCurrentPortrait(event.value));
        registry.register("set_expression", (event, context) -> context.setCurrentExpression(event.value));
        registry.register("play_ui_animation", (event, context) -> context.setCurrentAnimation(event.value));
        registry.register("set_variable", (event, context) -> context.setVariable(event.target, event.value));
        registry.register("emit_action", (event, context) -> context.emitAction(event.value, event.params));
        registry.register("goto_node", (event, context) -> context.requestJump(event.next.isBlank() ? event.value : event.next));
        return registry;
    }
}
