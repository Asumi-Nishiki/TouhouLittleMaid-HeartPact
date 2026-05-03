package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * 好感度变化的客户端视觉反馈。
 *
 * <p>服务端只告诉客户端“哪只女仆好感变化了多少”，
 * 这里只负责播放女仆附近的粒子反馈。
 *
 * <p>我们尝试过世界空间文字和屏幕投影飘字，但实际显示容易受视角、深度和 HUD 状态影响。
 * 现在收回到稳定的粒子效果，避免为了一个数字反馈把渲染链写复杂。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, value = Dist.CLIENT)
public final class FavorabilityPopupClient {
    private FavorabilityPopupClient() {
    }

    public static void show(UUID maidUuid, int delta) {
        if (maidUuid == null || delta == 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        EntityMaid maid = findMaid(minecraft, maidUuid);
        if (maid != null) {
            spawnParticles(minecraft, maid, delta);
        }
    }

    private static void spawnParticles(Minecraft minecraft, EntityMaid maid, int delta) {
        if (minecraft.level == null) {
            return;
        }

        int count = delta > 0 ? Mth.clamp(Math.abs(delta), 1, 6) : Mth.clamp(Math.abs(delta) * 4, 8, 24);
        for (int i = 0; i < count; i++) {
            double offsetX = (maid.getRandom().nextDouble() - 0.5D) * 0.45D;
            double offsetY = 0.95D + maid.getRandom().nextDouble() * 0.35D;
            double offsetZ = (maid.getRandom().nextDouble() - 0.5D) * 0.45D;
            double speedY = 0.015D + maid.getRandom().nextDouble() * 0.015D;
            if (delta > 0) {
                minecraft.level.addParticle(
                        ParticleTypes.HEART,
                        maid.getX() + offsetX,
                        maid.getY() + offsetY,
                        maid.getZ() + offsetZ,
                        0.0D,
                        speedY,
                        0.0D
                );
            } else if (i % 4 == 0) {
                minecraft.level.addParticle(ParticleTypes.ANGRY_VILLAGER,
                        maid.getX() + offsetX, maid.getY() + offsetY + 0.15D, maid.getZ() + offsetZ,
                        0.0D, speedY, 0.0D);
            } else if (i % 2 == 0) {
                minecraft.level.addParticle(ParticleTypes.SMOKE,
                        maid.getX() + offsetX, maid.getY() + offsetY, maid.getZ() + offsetZ,
                        offsetX * 0.03D, speedY * 0.4D, offsetZ * 0.03D);
            } else {
                minecraft.level.addParticle(ParticleTypes.DAMAGE_INDICATOR,
                        maid.getX() + offsetX, maid.getY() + offsetY, maid.getZ() + offsetZ,
                        0.0D, speedY, 0.0D);
            }
        }
        if (delta < 0) {
            minecraft.level.playLocalSound(maid.getX(), maid.getY(), maid.getZ(),
                    SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.75F, 0.85F, false);
        }
    }

    private static EntityMaid findMaid(Minecraft minecraft, UUID maidUuid) {
        if (minecraft == null || minecraft.level == null || maidUuid == null) {
            return null;
        }
        for (Entity candidate : minecraft.level.entitiesForRendering()) {
            if (candidate instanceof EntityMaid maid && maidUuid.equals(candidate.getUUID())) {
                return maid;
            }
        }
        return null;
    }

}
