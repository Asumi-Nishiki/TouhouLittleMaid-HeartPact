package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * 小女仆首次命名的服务端权威处理。
 *
 * <p>命名只能发生一次，客户端只负责提交输入；这里重新检查妈妈、孩子、
 * 所有权和当前抱起状态，避免客户端伪造目标或重复改名。
 */
public final class ChildNameManager {
    private static final int MAX_NAME_LENGTH = 24;

    private ChildNameManager() {
    }

    public static void handleNameSubmit(ServerPlayer player, @Nullable UUID motherUuid, String rawName) {
        if (player == null || motherUuid == null) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(motherUuid);
        if (!(entity instanceof EntityMaid mother) || !mother.isAlive() || !mother.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_name.invalid_mother"));
            return;
        }
        if (MaidChildEntity.shouldStayChild(mother)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_name.invalid_mother"));
            return;
        }

        EntityMaid child = findCarriedChild(mother);
        if (child == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_name.no_child"));
            return;
        }
        if (!child.isOwnedBy(player) || !MaidChildEntity.isMotherOfChild(child, mother)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_name.not_family"));
            return;
        }
        if (MaidChildEntity.hasConfirmedChildName(child)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_name.already_named", child.getDisplayName()));
            return;
        }

        String name = sanitizeName(rawName);
        if (name.isBlank()) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_name.empty"));
            return;
        }

        Component nameComponent = Component.literal(name);
        MaidChildEntity.applyOneTimeChildName(child, nameComponent);
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.child_name.success", nameComponent));
        RomanceSleepManager.speakSingleLineWithChat(mother, "dialogue.maidmarriage.child_name.success");
        player.serverLevel().sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                child.getX(), child.getY(0.8D), child.getZ(),
                8, 0.22D, 0.18D, 0.22D, 0.02D
        );
        player.serverLevel().playSound(
                null,
                mother.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS,
                0.75F,
                1.15F
        );
    }

    @Nullable
    private static EntityMaid findCarriedChild(EntityMaid mother) {
        for (Entity passenger : mother.getPassengers()) {
            if (passenger instanceof EntityMaid child
                    && MaidChildEntity.shouldStayChild(child)
                    && MaidChildEntity.isMotherOfChild(child, mother)) {
                return child;
            }
        }
        return null;
    }

    private static String sanitizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String compact = rawName.strip()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
        while (compact.contains("  ")) {
            compact = compact.replace("  ", " ");
        }
        if (compact.length() <= MAX_NAME_LENGTH) {
            return compact;
        }
        return compact.substring(0, MAX_NAME_LENGTH);
    }
}
