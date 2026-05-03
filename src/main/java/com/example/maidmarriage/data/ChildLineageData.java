package com.example.maidmarriage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;

public record ChildLineageData(
        boolean bornMaid,
        Optional<UUID> mother,
        Optional<UUID> father,
        Optional<UUID> grandParent,
        Optional<String> customNameJson
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final ChildLineageData EMPTY = new ChildLineageData(
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
    );

    public static final Codec<ChildLineageData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("born_maid", false).forGetter(ChildLineageData::bornMaid),
                    UUID_CODEC.optionalFieldOf("mother").forGetter(ChildLineageData::mother),
                    UUID_CODEC.optionalFieldOf("father").forGetter(ChildLineageData::father),
                    UUID_CODEC.optionalFieldOf("grand_parent").forGetter(ChildLineageData::grandParent),
                    Codec.STRING.optionalFieldOf("custom_name_json").forGetter(ChildLineageData::customNameJson)
            ).apply(instance, ChildLineageData::new));
}
