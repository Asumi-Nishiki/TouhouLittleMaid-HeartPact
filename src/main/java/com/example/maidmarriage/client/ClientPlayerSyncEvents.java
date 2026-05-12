package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.UpdateMaidAddressingPayload;
import com.example.maidmarriage.network.payload.UpdatePlayerSettingsPayload;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientPlayerSyncEvents {
    private ClientPlayerSyncEvents() {
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        LapPillowClientState.clear();
        ModNetworking.sendUpdateMaidAddressing(new UpdateMaidAddressingPayload(
                ModConfigs.maidAddressing(),
                ModConfigs.childMaidAddressing()));
        ModNetworking.sendUpdatePlayerSettings(new UpdatePlayerSettingsPayload(
                ModConfigs.liftHeight(),
                ModConfigs.hugDistance(),
                ModConfigs.haremMode()));
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HugClientState.clear();
        ChildInteractionClientState.clear();
        LapPillowClientState.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        HugClientState.tick(minecraft);
        ChildInteractionClientState.tick(minecraft);
        HugActionScreen.tickCompactLookHotkey(minecraft);
        HugClientState.ensureActionScreen(minecraft);
        ChildInteractionClientState.ensureActionScreen(minecraft);
    }
}
