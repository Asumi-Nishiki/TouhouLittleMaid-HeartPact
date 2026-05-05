package com.example.maidmarriage.compat.bauble;

import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

public final class GrowthPauseUtil {
    private GrowthPauseUtil() {
    }

    public static boolean hasSunflowerHairpin(EntityMaid maid) {
        var bauble = maid.getMaidBauble();
        // 直接扫描真实槽位，不依赖 BaubleItemHandler 的物品缓存。
        // 原版缓存通常可靠，但魂符恢复、子类实体和 GUI 同步边界下，真实槽位才是最终状态。
        for (int slot = 0; slot < bauble.getSlots(); slot++) {
            ItemStack stack = bauble.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == ModItems.SUNFLOWER_HAIRPIN.get()) {
                return true;
            }
        }
        return false;
    }
}
