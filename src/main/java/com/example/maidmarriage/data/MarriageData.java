package com.example.maidmarriage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;

/**
 * 婚姻数据结构：记录女仆当前婚配对象与结婚时间。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public record MarriageData(boolean married, Optional<UUID> spouse, long marriedGameTime) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final MarriageData EMPTY = new MarriageData(false, Optional.empty(), 0L);

    public static final Codec<MarriageData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("married", false).forGetter(MarriageData::married),
                    UUID_CODEC.optionalFieldOf("spouse").forGetter(MarriageData::spouse),
                    Codec.LONG.optionalFieldOf("married_game_time", 0L).forGetter(MarriageData::marriedGameTime)
            ).apply(instance, MarriageData::new));

    public MarriageData marry(UUID playerId, long gameTime) {
        return new MarriageData(true, Optional.of(playerId), gameTime);
    }

    public boolean isMarriedWith(UUID playerId) {
        return this.married && this.spouse.filter(playerId::equals).isPresent();
    }
}
