package com.example.maidmarriage.mixin.client;

import com.example.maidmarriage.client.LapPillowClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记录当前是否处于实体渲染调用栈中。
 *
 * <p>睡姿桥只应该影响“模型怎么画”，不应该污染普通逻辑判断。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherLapPillowMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void maidmarriage$renderHead(E entity,
                                                            double x,
                                                            double y,
                                                            double z,
                                                            float rotationYaw,
                                                            float partialTick,
                                                            PoseStack poseStack,
                                                            MultiBufferSource bufferSource,
                                                            int packedLight,
                                                            CallbackInfo ci) {
        LapPillowClientState.renderingDepth++;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private <E extends Entity> void maidmarriage$renderTail(E entity,
                                                            double x,
                                                            double y,
                                                            double z,
                                                            float rotationYaw,
                                                            float partialTick,
                                                            PoseStack poseStack,
                                                            MultiBufferSource bufferSource,
                                                            int packedLight,
                                                            CallbackInfo ci) {
        LapPillowClientState.renderingDepth = Math.max(0, LapPillowClientState.renderingDepth - 1);
    }
}
