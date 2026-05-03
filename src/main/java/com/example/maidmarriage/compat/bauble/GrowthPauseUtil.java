package com.example.maidmarriage.compat.bauble;

import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

public final class GrowthPauseUtil {
    private GrowthPauseUtil() {
    }

    public static boolean hasSunflowerHairpin(EntityMaid maid) {
        var bauble = maid.getMaidBauble();
        if (!bauble.containsItem(ModItems.SUNFLOWER_HAIRPIN.get())) {
            return false;
        }
        // Double-check actual stacks to avoid stale-cache false positives.
        for (int slot = 0; slot < bauble.getSlots(); slot++) {
            ItemStack stack = bauble.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == ModItems.SUNFLOWER_HAIRPIN.get()) {
                return true;
            }
        }
        return false;
    }
}
