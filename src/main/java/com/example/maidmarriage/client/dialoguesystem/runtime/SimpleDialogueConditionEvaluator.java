package com.example.maidmarriage.client.dialoguesystem.runtime;

/**
 * 默认的轻量条件判断器。
 *
 * <p>它只支持最基础的变量判定，用于现在“先搭底座”的阶段：
 * - 空条件 / always -> true
 * - key -> 判断布尔真值
 * - !key -> 取反
 * - key=value -> 字符串相等
 *
 * <p>后面真要接入好感度、实体状态时，再替换成更完整的 evaluator。
 */
public final class SimpleDialogueConditionEvaluator implements DialogueConditionEvaluator {
    @Override
    public boolean test(String condition, DialogueRuntimeContext context) {
        if (condition == null || condition.isBlank() || "always".equalsIgnoreCase(condition.trim())) {
            return true;
        }

        String trimmed = condition.trim();
        if (trimmed.contains("||")) {
            for (String part : trimmed.split("\\|\\|")) {
                if (test(part, context)) {
                    return true;
                }
            }
            return false;
        }
        if (trimmed.contains("&&")) {
            for (String part : trimmed.split("&&")) {
                if (!test(part, context)) {
                    return false;
                }
            }
            return true;
        }
        if (trimmed.startsWith("!")) {
            return !truthy(context.getVariable(trimmed.substring(1).trim()));
        }

        int notEqualsIndex = trimmed.indexOf("!=");
        if (notEqualsIndex >= 0) {
            String key = trimmed.substring(0, notEqualsIndex).trim();
            String expected = trimmed.substring(notEqualsIndex + 2).trim();
            return !expected.equals(context.getVariable(key));
        }

        int equalsIndex = trimmed.indexOf('=');
        if (equalsIndex >= 0) {
            String key = trimmed.substring(0, equalsIndex).trim();
            String expected = trimmed.substring(equalsIndex + 1).trim();
            return expected.equals(context.getVariable(key));
        }

        return truthy(context.getVariable(trimmed));
    }

    private boolean truthy(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value.trim().toLowerCase()) {
            case "0", "false", "no", "off" -> false;
            default -> true;
        };
    }
}
