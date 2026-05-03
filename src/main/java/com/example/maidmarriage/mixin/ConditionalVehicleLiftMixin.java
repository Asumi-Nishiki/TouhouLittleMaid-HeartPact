package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.example.maidmarriage.compat.MaidLiftManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalVehicle;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 直接拦截 Gecko / YSM 的“载具条件动画”选择。
 *
 * <p>这层现在只负责非 YSM 举高高和抱小女仆这类“不能进入载具动画”的场景。
 *
 * <p>YSM 举高高已经改为主动复用模型包自己的 riding / ride_pig 坐姿，
 * 所以当女仆本身是 YSM 模型时，必须让 `vehicle$minecraft:player`
 * 正常返回，不能再清空。
 */
@Mixin(value = ConditionalVehicle.class, remap = false)
public abstract class ConditionalVehicleLiftMixin {
    @Inject(method = "doTest", at = @At("HEAD"), cancellable = true)
    private void maidmarriage$skipPlayerVehicleAnimation(Mob maid, CallbackInfoReturnable<String> cir) {
        if (!(maid instanceof EntityMaid entityMaid)) {
            return;
        }
        Player player = MaidLiftManager.getLiftPlayer(entityMaid);
        if ((!entityMaid.isYsmModel() && MaidLiftManager.isLiftState(entityMaid, player))
                || MaidCarryChildManager.isCarryChildState(entityMaid)
                || MaidCarryChildManager.isCarryAdultState(entityMaid)) {
            cir.setReturnValue("");
        }
    }
}
