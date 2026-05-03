package com.example.maidmarriage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;

/**
 * 子代状态持久化数据：
 * 1) 作为小女仆身份的权威数据源（借鉴婚姻/妊娠的 TaskData 方案）；
 * 2) 用于复活、存档重载后恢复小女仆成长信息与父母信息；
 * 3) 避免仅依赖实体类型或 persistentData 导致的跨流程丢失。
 */
public record ChildStateData(
        boolean child,
        int growthTicks,
        String growthStage,
        Optional<UUID> mother,
        Optional<UUID> father,
        boolean tameInitialized,
        boolean childNameConfirmed,
        Optional<String> customNameJson
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final ChildStateData EMPTY = new ChildStateData(
            false,
            0,
            "INFANT",
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            Optional.empty()
    );

    public static final Codec<ChildStateData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("child", false).forGetter(ChildStateData::child),
                    Codec.INT.optionalFieldOf("growth_ticks", 0).forGetter(ChildStateData::growthTicks),
                    Codec.STRING.optionalFieldOf("growth_stage", "INFANT").forGetter(ChildStateData::growthStage),
                    UUID_CODEC.optionalFieldOf("mother").forGetter(ChildStateData::mother),
                    UUID_CODEC.optionalFieldOf("father").forGetter(ChildStateData::father),
                    Codec.BOOL.optionalFieldOf("tame_initialized", false).forGetter(ChildStateData::tameInitialized),
                    Codec.BOOL.optionalFieldOf("child_name_confirmed", false).forGetter(ChildStateData::childNameConfirmed),
                    Codec.STRING.optionalFieldOf("custom_name_json").forGetter(ChildStateData::customNameJson)
            ).apply(instance, ChildStateData::new));
}
