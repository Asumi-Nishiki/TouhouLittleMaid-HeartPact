package com.example.maidmarriage.compat.bauble;

import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

/**
 * 金蒲公英发卡的女仆饰品逻辑。
 *
 * <p>暂停成长本身仍由 {@link GrowthPauseUtil} 在服务端 tick 中判断；
 * 这里专门处理戴上/摘下瞬间的状态刷新，避免饰品栏同步或缓存延迟导致
 * 小女仆摘下发卡后还停留在“成长暂停”的显示与生命周期状态。
 */
public final class GrowthPauseHairpinBauble implements IMaidBauble {
    @Override
    public void onPutOn(EntityMaid maid, ItemStack stack) {
        MaidChildEntity.refreshChildLifecycleAfterBaubleChange(maid);
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack stack) {
        MaidChildEntity.refreshChildLifecycleAfterBaubleChange(maid);
    }
}
