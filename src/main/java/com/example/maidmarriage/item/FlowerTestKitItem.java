package com.example.maidmarriage.item;

import com.example.maidmarriage.config.DialogueScriptManager;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class FlowerTestKitItem extends DescriptionItem {
    private static final List<Item> TEST_FLOWERS = List.of(
            Items.POPPY, Items.RED_TULIP, Items.ROSE_BUSH,
            Items.DANDELION, Items.SUNFLOWER,
            Items.BLUE_ORCHID, Items.CORNFLOWER,
            Items.AZURE_BLUET, Items.WHITE_TULIP, Items.OXEYE_DAISY, Items.LILY_OF_THE_VALLEY,
            Items.ORANGE_TULIP, Items.TORCHFLOWER,
            Items.PINK_TULIP, Items.PEONY,
            Items.ALLIUM, Items.LILAC,
            Items.WITHER_ROSE);

    public FlowerTestKitItem(Properties properties, String tooltipKey) {
        super(properties, tooltipKey);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack inHand = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(inHand, true);
        }
        for (Item flower : TEST_FLOWERS) {
            ItemStack reward = new ItemStack(flower, 2);
            if (!player.getInventory().add(reward)) {
                ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), reward);
                level.addFreshEntity(drop);
            }
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(DialogueScriptManager.componentForPlayer(serverPlayer, "message.maidmarriage.flower_test_kit.give"));
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(inHand, false);
    }
}
