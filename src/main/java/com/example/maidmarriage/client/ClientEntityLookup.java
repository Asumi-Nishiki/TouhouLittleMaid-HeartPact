package com.example.maidmarriage.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ClientEntityLookup {
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
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (uuid.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }
}
