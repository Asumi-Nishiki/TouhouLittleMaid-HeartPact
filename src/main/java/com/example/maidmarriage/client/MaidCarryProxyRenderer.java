package com.example.maidmarriage.client;

import com.example.maidmarriage.entity.MaidCarryProxyEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * 抱小女仆代理实体的空渲染器。
 */
public class MaidCarryProxyRenderer extends EntityRenderer<MaidCarryProxyEntity> {
    public MaidCarryProxyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(MaidCarryProxyEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(MaidCarryProxyEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
