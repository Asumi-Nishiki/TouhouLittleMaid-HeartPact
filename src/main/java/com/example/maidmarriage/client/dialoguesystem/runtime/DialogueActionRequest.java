package com.example.maidmarriage.client.dialoguesystem.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 剧情运行时抛出的动作请求。
 *
 * <p>这里故意只记录“请求了什么动作”，不直接执行旧的交互逻辑。
 * 这样我们就能先把剧情系统和旧逻辑解耦，
 * 等框架稳定之后，再把这些语义动作接到现有网络包/管理器上。
 */
public record DialogueActionRequest(String actionId, Map<String, String> params) {
    public DialogueActionRequest {
        params = params == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(params));
    }
}
