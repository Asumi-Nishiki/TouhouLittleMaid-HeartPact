package com.example.maidmarriage.compat;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTombstoneEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidDeathBridge {
    private MaidDeathBridge() {
    }

    @SubscribeEvent
    public static void onMaidTombstone(MaidTombstoneEvent event) {
        if (MaidChildEntity.shouldStayChild(event.getMaid())) {
            event.setCanceled(true);
        }
    }
}

