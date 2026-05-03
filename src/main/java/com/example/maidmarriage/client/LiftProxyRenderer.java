package com.example.maidmarriage.client;

import com.example.maidmarriage.entity.LiftProxyEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * 举高高代理实体的空渲染器。
 *
 * <p>代理实体本身完全不可见，因此这里直接禁止渲染。
 */
public class LiftProxyRenderer extends EntityRenderer<LiftProxyEntity> {
    public LiftProxyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldRender(LiftProxyEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(LiftProxyEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
