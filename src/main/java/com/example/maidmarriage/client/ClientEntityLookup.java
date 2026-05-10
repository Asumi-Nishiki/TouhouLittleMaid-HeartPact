package com.example.maidmarriage.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ClientEntityLookup {
    @Nullable
    private static ClientLevel cachedLevel;
    @Nullable
    private static UUID cachedUuid;
    @Nullable
    private static Entity cachedEntity;
    private static long cachedGameTime = Long.MIN_VALUE;

    private ClientEntityLookup() {
    }

    public static boolean hasLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null;
    }

    @Nullable
    public static Player findPlayer(UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || uuid == null) {
            return null;
        }
        return minecraft.level.getPlayerByUUID(uuid);
    }

    @Nullable
    public static EntityMaid findMaid(UUID uuid) {
        Entity entity = findEntity(uuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    @Nullable
    public static Entity findEntity(UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || uuid == null) {
            return null;
        }
        long gameTime = minecraft.level.getGameTime();
        if (minecraft.level == cachedLevel
                && gameTime == cachedGameTime
                && uuid.equals(cachedUuid)
                && cachedEntity != null
                && !cachedEntity.isRemoved()
                && uuid.equals(cachedEntity.getUUID())) {
            return cachedEntity;
        }

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (uuid.equals(entity.getUUID())) {
                cache(minecraft.level, gameTime, uuid, entity);
                return entity;
            }
        }
        cache(minecraft.level, gameTime, uuid, null);
        return null;
    }

    private static void cache(ClientLevel level, long gameTime, UUID uuid, @Nullable Entity entity) {
        cachedLevel = level;
        cachedGameTime = gameTime;
        cachedUuid = uuid;
        cachedEntity = entity;
    }
}
