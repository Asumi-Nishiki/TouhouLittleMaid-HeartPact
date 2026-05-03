package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.HashSet;
import java.util.Set;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MaidChildScaleRenderHook {
    private static final float INFANT_RENDER_SCALE = 0.56F;
    private static final float JUVENILE_RENDER_SCALE = 0.72F;
    private static final float CHILD_RENDER_SCALE = 0.86F;
    private static final ThreadLocal<Set<Integer>> SCALED_ENTITY_IDS = ThreadLocal.withInitial(HashSet::new);

    private MaidChildScaleRenderHook() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!MaidChildEntity.shouldStayChild(maid)) {
            return;
        }
        float scale = resolveRenderScale(maid);
        event.getPoseStack().pushPose();
        event.getPoseStack().scale(scale, scale, scale);
        SCALED_ENTITY_IDS.get().add(maid.getId());
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        Set<Integer> ids = SCALED_ENTITY_IDS.get();
        if (!ids.remove(maid.getId())) {
            return;
        }
        event.getPoseStack().popPose();
    }

    private static float resolveRenderScale(EntityMaid maid) {
        return switch (MaidChildEntity.resolveGrowthStage(maid)) {
            case INFANT -> INFANT_RENDER_SCALE;
            case JUVENILE -> JUVENILE_RENDER_SCALE;
            case CHILD -> CHILD_RENDER_SCALE;
            case ADULT -> 1.0F;
        };
    }
}
