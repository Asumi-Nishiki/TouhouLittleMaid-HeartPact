package com.example.maidmarriage.compat;

import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.compat.bauble.NoopMaidBauble;
import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;

@LittleMaidExtension
/**
 * 车万女仆兼容入口：注册任务数据与交互事件监听。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public class LittleMaidCompat implements ILittleMaid {
    public LittleMaidCompat() {
        MinecraftForge.EVENT_BUS.register(MarriageEventHandler.class);
        MinecraftForge.EVENT_BUS.register(RomanceSleepManager.class);
        MinecraftForge.EVENT_BUS.register(MaidWorkManager.class);
        MinecraftForge.EVENT_BUS.register(MaidLiftManager.class);
        /*
         * 亲吻管理器除了处理网络包外，还负责服务端 tick 中的两段后续逻辑：
         * 1. 亲吻后的短暂对视维持；
         * 2. 镜头恢复后的“害羞别头 + 台词”收尾。
         * 如果这里不注册到事件总线，亲吻请求虽然能发到服务端，
         * 但后续动作和延迟台词都不会执行。
         */
        MinecraftForge.EVENT_BUS.register(MaidKissManager.class);
        MinecraftForge.EVENT_BUS.register(PetHeadManager.class);
        MinecraftForge.EVENT_BUS.register(SoulSlabChildBridge.class);
    }

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(ModItems.PROPOSAL_RING, new NoopMaidBauble());
        manager.bind(ModItems.SUNFLOWER_HAIRPIN, new NoopMaidBauble());
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        ModTaskData.registerAll(register);
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        MaidWorkManager.addChildWorkTasks(manager);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addMaidTips(MaidTipsOverlay overlay) {
        overlay.addTips("overlay.maidmarriage.proposal_ring.tip", ModItems.PROPOSAL_RING.get());
        overlay.addTips("overlay.maidmarriage.sunflower_hairpin.tip", ModItems.SUNFLOWER_HAIRPIN.get());
        overlay.addTips("overlay.maidmarriage.yes_pillow.tip", ModItems.YES_PILLOW.get());
        overlay.addTips("overlay.maidmarriage.rainbow_bouquet.tip", ModItems.RAINBOW_BOUQUET.get());
        overlay.addTips("overlay.maidmarriage.longing_tester.tip", ModItems.LONGING_TESTER.get());
        overlay.addTips("overlay.maidmarriage.flower_test_kit.tip", ModItems.FLOWER_TEST_KIT.get());
        overlay.addTips("overlay.maidmarriage.pregnancy_test_tool.tip", ModItems.PREGNANCY_TEST_TOOL.get());
        overlay.addTips("overlay.maidmarriage.family_tree_tool.tip", ModItems.FAMILY_TREE_TOOL.get());
        overlay.addTips("overlay.maidmarriage.marriage_consent_form.tip", ModItems.MARRIAGE_CONSENT_FORM.get());
        overlay.addTips("overlay.maidmarriage.child.learn.enchantment", net.minecraft.world.item.Items.BOOK);
        overlay.addTips("overlay.maidmarriage.child.learn.alchemy", net.minecraft.world.item.Items.GLASS_BOTTLE);
        overlay.addTips("overlay.maidmarriage.child.learn.tactics", net.minecraft.world.item.Items.IRON_SWORD);
        overlay.addTips("overlay.maidmarriage.child.explore", net.minecraft.world.item.Items.STICK);
        overlay.addTips("overlay.maidmarriage.child.favor.recover", net.minecraft.world.item.Items.SUGAR);
    }

}
