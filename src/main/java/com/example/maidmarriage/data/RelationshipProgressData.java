package com.example.maidmarriage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 恋爱进度持久化数据。
 *
 * <p>这里专门记录“是否已经正式表白成功”这类不会被单纯好感数值表达清楚的状态。
 * 这样表白入口、婚礼入口、关系阶段显示都能共享一份权威数据源。
 */
public record RelationshipProgressData(
        boolean confessionCompleted
) {
    public static final RelationshipProgressData EMPTY = new RelationshipProgressData(false);

    public static final Codec<RelationshipProgressData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("confession_completed", false).forGetter(RelationshipProgressData::confessionCompleted)
            ).apply(instance, RelationshipProgressData::new));

    public RelationshipProgressData completeConfession() {
        if (confessionCompleted) {
            return this;
        }
        return new RelationshipProgressData(true);
    }
}
