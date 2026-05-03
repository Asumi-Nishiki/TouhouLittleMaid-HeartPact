package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.MaidLiftManager;
import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.builtin.QueryBinding;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修正 `query.*` 在特殊固定姿态场景下的语义。
 *
 * <p>普通 Gecko / Bedrock 举高高仍然需要屏蔽部分骑乘查询，
 * 避免模型包把“小女仆骑在代理实体上”误识别成公主抱或平躺。
 *
 * <p>但 YSM 举高高现在明确回到它自己的 riding / ride_pig 路线：
 * 对 YSM 模型来说，骑乘状态就是正确的动作入口，不能再在这里伪装成站立。
 * 因此 {@link #isSpecialStandState(Entity)} 会特意放行 YSM 的 lift 状态。
 */
@Mixin(value = QueryBinding.class, remap = false)
public abstract class QueryBindingLiftStateMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void maidmarriage$overrideLiftRidingQuery(CallbackInfo ci) {
        QueryBinding binding = (QueryBinding) (Object) this;

        binding.entityVar(
                "has_rider",
                ctx -> isSpecialStandState(ctx.entity()) ? false : ctx.entity().isVehicle()
        );
        binding.entityVar(
                "is_riding",
                ctx -> isSpecialStandState(ctx.entity()) ? false : ctx.entity().isPassenger()
        );
        binding.entityVar(
                "is_on_ground",
                ctx -> isSpecialStandState(ctx.entity()) ? true : ctx.entity().onGround()
        );
        binding.entityVar(
                "is_sneaking",
                ctx -> isSpecialStandState(ctx.entity()) ? false : (ctx.entity().onGround() && ctx.entity().isCrouching())
        );
        binding.entityVar(
                "is_swimming",
                ctx -> isSpecialStandState(ctx.entity()) ? false : ctx.entity().isSwimming()
        );
        binding.entityVar(
                "ground_speed",
                ctx -> isSpecialStandState(ctx.entity()) ? 0.0F : getGroundSpeed(ctx.entity())
        );
        binding.entityVar(
                "modified_distance_moved",
                ctx -> isSpecialStandState(ctx.entity()) ? 0.0F : ctx.entity().walkDist
        );
        binding.entityVar(
                "walk_distance",
                ctx -> isSpecialStandState(ctx.entity()) ? 0.0F : ctx.entity().moveDist
        );
        binding.entityVar(
                "vertical_speed",
                ctx -> isSpecialStandState(ctx.entity()) ? 0.0F : getVerticalSpeed(ctx.entity())
        );
        binding.entityVar(
                "yaw_speed",
                ctx -> isSpecialStandState(ctx.entity()) ? 0.0F : getYawSpeed(ctx.entity())
        );
    }

    /**
     * 只要是我们自己强行“钉在某个姿态”的场景，就把 Molang 里的运动量全部钳成静止值。
     *
     * <p>注意：YSM 举高高不属于这里的“站立固定姿态”。
     * 它必须保留 query.is_riding / query.is_passenger 的真实结果，
     * 这样 YSM 模型包才能命中自己的 riding / ride_pig 坐姿。
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

    private static float getGroundSpeed(Entity entity) {
        Vec3 delta = entity.getDeltaMovement();
        return 20.0F * Mth.sqrt((float) (delta.x * delta.x + delta.z * delta.z));
    }

    private static float getVerticalSpeed(Entity entity) {
        return 20.0F * (float) (entity.position().y - entity.yo);
    }

    private static float getYawSpeed(Entity entity) {
        return 20.0F * (entity.getYRot() - entity.yRotO);
    }
}
