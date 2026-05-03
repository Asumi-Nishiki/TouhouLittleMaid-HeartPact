package com.example.maidmarriage.client.dialoguesystem.runtime;

/**
 * 剧情条件判断接口。
 *
 * <p>先抽成接口，而不是直接把具体规则写死，
 * 是因为后面很可能会接：
 * - 好感度；
 * - 女仆年龄；
 * - 当前动作状态；
 * - 是否为主人；
 * - 是否 YSM / GeckoLib；
 * - 自定义剧情变量。
 */
@FunctionalInterface
public interface DialogueConditionEvaluator {
    boolean test(String condition, DialogueRuntimeContext context);
}
