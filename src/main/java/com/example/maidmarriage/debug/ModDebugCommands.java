package com.example.maidmarriage.debug;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.init.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ModDebugCommands {
    private ModDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("maidmarriage_selftest")
                .requires(source -> source.hasPermission(2))
                .executes(ModDebugCommands::runSelfTest)
                .then(Commands.literal("run").executes(ModDebugCommands::runSelfTest)));
        dispatcher.register(Commands.literal("mm_selftest")
                .requires(source -> source.hasPermission(2))
                .executes(ModDebugCommands::runSelfTest));
        dispatcher.register(Commands.literal("maidmarriage_divorce")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("maid", EntityArgument.entity())
                        .executes(ModDebugCommands::runDivorce)));
        dispatcher.register(Commands.literal("mm_divorce")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("maid", EntityArgument.entity())
                        .executes(ModDebugCommands::runDivorce)));
    }

    private static int runSelfTest(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        give(player, new ItemStack(ModItems.PROPOSAL_RING.get(), 2));
        give(player, new ItemStack(ModItems.YES_PILLOW.get(), 2));
        give(player, new ItemStack(ModItems.MARRIAGE_CONSENT_FORM.get(), 2));
        give(player, new ItemStack(ModItems.LONGING_TESTER.get(), 1));
        give(player, new ItemStack(ModItems.FLOWER_TEST_KIT.get(), 1));
        give(player, new ItemStack(Items.DIAMOND, 8));
        give(player, new ItemStack(Items.IRON_NUGGET, 32));
        give(player, new ItemStack(Items.WHITE_WOOL, 16));
        give(player, new ItemStack(Items.RED_WOOL, 16));
        give(player, new ItemStack(Items.BREAD, 32));
        give(player, new ItemStack(Items.STICK, 32));
        give(player, new ItemStack(Items.BOOK, 16));
        give(player, new ItemStack(Items.GLASS_BOTTLE, 16));
        give(player, new ItemStack(Items.IRON_SWORD, 8));

        context.getSource().sendSuccess(() -> Component.literal("[MaidMarriage] Self-test pack granted."), false);
        return 1;
    }

    private static int runDivorce(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Entity target = EntityArgument.getEntity(context, "maid");
        if (!(target instanceof EntityMaid maid)) {
            context.getSource().sendFailure(DialogueScriptManager.component("message.maidmarriage.command.divorce.not_maid"));
            return 0;
        }

        boolean isAdmin = context.getSource().hasPermission(2);
        if (!maid.isOwnedBy(player) && !isAdmin) {
            context.getSource().sendFailure(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.command.divorce.no_permission", maid.getDisplayName()));
            return 0;
        }

        maid.setAndSyncData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
        maid.setAndSyncData(ModTaskData.PREGNANCY_DATA, PregnancyData.EMPTY);
        context.getSource().sendSuccess(() -> DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.command.divorce.success", maid.getDisplayName()), true);
        return 1;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }
}
