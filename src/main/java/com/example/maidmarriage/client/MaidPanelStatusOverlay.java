package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.data.PregnancyData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, value = Dist.CLIENT)
/**
 * 女仆面板状态扩展：在原面板额外显示婚姻与生理信息。
 * 该类的具体逻辑可参见下方方法与字段定义。
 */
public final class MaidPanelStatusOverlay {
    private MaidPanelStatusOverlay() {
    }

    @SubscribeEvent
    public static void onRenderMaidPanel(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractMaidContainerGui<?> maidGui)) {
            return;
        }
        EntityMaid maid = maidGui.getMaid();
        Font font = Minecraft.getInstance().font;

        MarriageData marriage = maid.getData(ModTaskData.MARRIAGE_DATA);
        PregnancyData pregnancy = maid.getData(ModTaskData.PREGNANCY_DATA);

        Component marriageText = (marriage != null && marriage.married())
                ? Component.translatable("panel.maidmarriage.marriage.married")
                : Component.translatable("panel.maidmarriage.marriage.single");

        Component physiologyText;
        if (MaidChildEntity.shouldStayChild(maid)) {
            Component stageText = Component.translatable(
                    "panel.maidmarriage.child_stage." + MaidChildEntity.resolveGrowthStage(maid).name().toLowerCase());
            physiologyText = Component.translatable("panel.maidmarriage.physiology.child_stage", stageText);
        } else {
            physiologyText = (pregnancy != null && pregnancy.firstExperience())
                    ? (pregnancy.pregnant()
                    ? Component.translatable("panel.maidmarriage.physiology.pregnant")
                    : Component.translatable("panel.maidmarriage.physiology.normal"))
                    : Component.translatable("panel.maidmarriage.physiology.untried");
        }

        MaidMoodData mood = maid.getOrCreateData(ModTaskData.MOOD_DATA, MaidMoodData.EMPTY);

        int x = maidGui.getGuiLeft() + 8;
        int y = maidGui.getGuiTop() + 72;
        event.getGuiGraphics().drawString(font, marriageText, x, y, 0xFFF6C782, false);
        event.getGuiGraphics().drawString(font, physiologyText, x, y + 10, 0xFFF19FB6, false);
        Component moodText = Component.translatable(
                "panel.maidmarriage.mood." + mood.state().key(),
                mood.moodValue(),
                MaidMoodData.MAX_MOOD);
        event.getGuiGraphics().drawString(font, moodText, x, y + 20, 0xFFFFAEC8, false);
    }
}
