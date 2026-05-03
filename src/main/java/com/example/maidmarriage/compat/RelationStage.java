package com.example.maidmarriage.compat;

/**
 * 关系阶段。
 *
 * <p>这个枚举放在 common 包里，服务端规则、客户端 UI、剧情变量都只认这一份阶段定义。
 * 这样不会让服务端关系逻辑反向依赖客户端台本类，也能避免以后阈值和阶段名再次分叉。
 */
public enum RelationStage {
    /**
     * 初始阶段：还没到摸头门槛，亲密度比较克制。
     */
    INITIAL("initial"),
    /**
     * 温暖阶段：已解锁摸头，开始有明显的亲近感。
     */
    WARM("warm"),
    /**
     * 亲近阶段：已解锁拥抱，能接受更近距离的身体接触。
     */
    CLOSE("close"),
    /**
     * 交往阶段：已完成或达到表白/亲吻门槛，对话可以更直球。
     */
    DATING("dating"),
    /**
     * 婚姻阶段：已结婚，文案可以更稳定、更像长期关系。
     */
    MARRIAGE("marriage");

    private final String key;

    RelationStage(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static RelationStage fromKey(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        for (RelationStage stage : values()) {
            if (stage.key.equals(normalized)) {
                return stage;
            }
        }
        return INITIAL;
    }
}
