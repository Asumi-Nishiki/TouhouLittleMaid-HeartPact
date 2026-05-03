package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.init.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
/**
 * 客户端事件注册：绑定渲染器与客户端显示逻辑。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityRenderers.register(ModEntities.MAID_CHILD.get(), MaidChildRenderer::new);
        EntityRenderers.register(ModEntities.LIFT_PROXY.get(), LiftProxyRenderer::new);
        EntityRenderers.register(ModEntities.MAID_CARRY_PROXY.get(), MaidCarryProxyRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        RhythmKeyMappings.applyConfigKeyMappings();
        event.register(RhythmKeyMappings.RHYTHM_HIT);
        event.register(RhythmKeyMappings.PET_HEAD);
        event.register(RhythmKeyMappings.INTERACTION);
        event.register(RhythmKeyMappings.CARRY_POSE_DEBUG);
        event.register(RhythmKeyMappings.MAID_DEBUG_PANEL);
    }
}
