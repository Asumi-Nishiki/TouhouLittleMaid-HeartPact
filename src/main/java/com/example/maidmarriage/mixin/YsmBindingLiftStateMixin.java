package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidLiftManager;
import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.variable.MoveInputVariable;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.molang.YSMBinding;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修正 `ysm.*` 在特殊固定姿态场景下的语义。
 *
 * <p>这里不能再把 YSM 举高高伪装成非乘客。
 * 现在的约定是：YSM 举高高继续走模型包自己的 riding / ride_pig 坐姿，
 * 我们只在最终渲染层补一层可视高度。
 *
 * <p>所以本类只继续处理“抱小女仆”这类真正需要伪装成静止站立的场景，
 * 避免把 lift 的正确 riding 入口再次挡掉。
 */
@Mixin(value = YSMBinding.class, remap = false)
public abstract class YsmBindingLiftStateMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void maidmarriage$overrideLiftPassengerQuery(CallbackInfo ci) {
        YSMBinding binding = (YSMBinding) (Object) this;
        binding.entityVar(
                "is_passenger",
                ctx -> isSpecialStandState(ctx.entity()) ? false : ctx.entity().isPassenger()
        );
        binding.entityVar(
                "input_vertical",
                ctx -> isSpecialStandState(ctx.entity()) ? 0.0F : MoveInputVariable.getVertical(ctx)
        );
        binding.entityVar(
                "input_horizontal",
                ctx -> isSpecialStandState(ctx.entity()) ? 0.0F : MoveInputVariable.getHorizontal(ctx)
        );
        binding.entityVar(
                "is_sleep",
                ctx -> isSpecialStandState(ctx.entity()) ? false : ctx.entity().getPose() == net.minecraft.world.entity.Pose.SLEEPING
        );
        binding.entityVar(
                "is_sneak",
                ctx -> isSpecialStandState(ctx.entity()) ? false : (ctx.entity().onGround() && ctx.entity().isCrouching())
        );
    }

    /**
     * YSM 自己也维护了一套 `ysm.*` 条件变量。
     * 这里和 `query.*` 同步钳制，但明确不包含 YSM 举高高。
     */
    private static boolean isSpecialStandState(Entity entity) {
        if (!(entity instanceof EntityMaid maid)) {
            return false;
        }

        Player liftPlayer = MaidLiftManager.getLiftPlayer(maid);
        boolean nonYsmLift = !maid.isYsmModel() && MaidLiftManager.isLiftState(maid, liftPlayer);
        return nonYsmLift
                || MaidCarryChildManager.isAdultCarrier(maid)
                || MaidCarryChildManager.isCarriedChild(maid);
    }
}
