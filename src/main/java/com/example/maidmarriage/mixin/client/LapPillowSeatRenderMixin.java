package com.example.maidmarriage.mixin.client;

import com.example.maidmarriage.client.LapPillowClientState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 睡姿渲染时不要按乘客姿态处理。
 *
 * <p>虽然当前膝枕躺姿不使用骑乘，但保留这个桥可以兼容其它模组临时把玩家塞进载具的情况。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LapPillowSeatRenderMixin {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"),
            require = 0
    )
    private boolean maidmarriage$overridePassengerForSleepPoseBridge(LivingEntity entity) {
        if (entity instanceof AbstractClientPlayer player && LapPillowClientState.shouldUseSleepPoseBridge(player)) {
            return false;
        }
        return entity.isPassenger();
    }
}
