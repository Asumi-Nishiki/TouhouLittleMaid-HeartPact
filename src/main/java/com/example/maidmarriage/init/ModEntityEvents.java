package com.example.maidmarriage.init;

import com.example.maidmarriage.MaidMarriageMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
/**
 * 实体事件初始化：绑定实体属性与相关事件。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class ModEntityEvents {
    private ModEntityEvents() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MAID_CHILD.get(), EntityMaid.createAttributes().build());
    }
}
