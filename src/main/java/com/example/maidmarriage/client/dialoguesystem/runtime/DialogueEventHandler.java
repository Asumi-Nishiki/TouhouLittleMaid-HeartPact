package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.client.dialoguesystem.DialogueScenario;

/**
 * 安全事件处理器。
 *
 * <p>JSON 里只写事件类型，
 * 具体如何处理则由注册表里的白名单 handler 决定。
 * 这样我们就不会重新走回“配置文件里什么都能执行”的老路。
 */
@FunctionalInterface
public interface DialogueEventHandler {
    void handle(DialogueScenario.Event event, DialogueRuntimeContext context);
}
