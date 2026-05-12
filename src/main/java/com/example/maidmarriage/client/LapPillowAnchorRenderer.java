package com.example.maidmarriage.client;

import com.example.maidmarriage.entity.LapPillowAnchorEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * 膝枕锚点的空渲染器。
 */
public class LapPillowAnchorRenderer extends EntityRenderer<LapPillowAnchorEntity> {
    public LapPillowAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(LapPillowAnchorEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(LapPillowAnchorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
