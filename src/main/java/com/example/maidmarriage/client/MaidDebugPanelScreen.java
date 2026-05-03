package com.example.maidmarriage.client;

import com.example.maidmarriage.compat.MaidMoodManager;
import com.example.maidmarriage.compat.RelationshipThresholds;
import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.MaidDebugDataPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 女仆剧情/好感测试面板。
 *
 * <p>这是纯测试入口：按 F7 后锁定当前互动目标或准星下的女仆，
 * 允许我们快速修改好感度和心情，从而测试“摸头 32 / 拥抱 64 / 交往 128 / 结婚 192+”等剧情解锁。
 *
 * <p>注意：面板只负责收集输入，真正写数据必须通过服务端包完成。
 * 这样多人环境里不会出现客户端本地改了但服务端不同步的问题，也能在服务端校验女仆归属。
 */
public class MaidDebugPanelScreen extends Screen {
    private static final int FAVORABILITY_MAX = RelationshipThresholds.FAVORABILITY_MAX;

    @Nullable
    private final UUID maidUuid;
    private final Component targetName;

    private EditBox favorabilityBox;
    private EditBox moodBox;
    private Component status = Component.literal("输入数值后点击应用。");
    private int statusColor = 0xFFD8D0EB;

    private MaidDebugPanelScreen(@Nullable UUID maidUuid, Component targetName) {
        super(Component.literal("女仆调试面板"));
        this.maidUuid = maidUuid;
        this.targetName = targetName == null ? Component.literal("未锁定女仆") : targetName;
    }

    public static void open(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen != null && !(minecraft.screen instanceof HugActionScreen)) {
            return;
        }

        UUID targetUuid = PetHeadClientHandler.resolveTargetMaidUuid(minecraft);
        EntityMaid maid = findMaidByUuid(minecraft, targetUuid);
        Component targetName = maid == null
                ? Component.literal("未锁定女仆：请对准女仆或先进入互动")
                : maid.getDisplayName();
        minecraft.setScreen(new MaidDebugPanelScreen(targetUuid, targetName));
    }

    @Override
    protected void init() {
        int panelLeft = this.width / 2 - 160;
        int panelTop = this.height / 2 - 92;
        int left = panelLeft + 18;
        int right = panelLeft + 164;
        int y = panelTop + 52;

        EntityMaid maid = findMaidByUuid(this.minecraft, maidUuid);
        int currentFavorability = maid == null ? RelationshipThresholds.HUG_UNLOCK : maid.getFavorability();
        int currentMood = maid == null ? MaidMoodData.DEFAULT_MOOD : MaidMoodManager.value(maid);

        favorabilityBox = this.addRenderableWidget(new EditBox(this.font, left, y, 128, 20, Component.literal("好感度")));
        favorabilityBox.setMaxLength(4);
        favorabilityBox.setValue(Integer.toString(currentFavorability));
        favorabilityBox.setFilter(MaidDebugPanelScreen::isSignedIntegerText);

        moodBox = this.addRenderableWidget(new EditBox(this.font, right, y, 128, 20, Component.literal("心情")));
        moodBox.setMaxLength(3);
        moodBox.setValue(Integer.toString(currentMood));
        moodBox.setFilter(MaidDebugPanelScreen::isSignedIntegerText);

        y += 32;
        addPresetButton(left, y, 60, "0", 0);
        addPresetButton(left + 66, y, 60, "32 摸头", RelationshipThresholds.PET_UNLOCK);
        addPresetButton(left + 132, y, 60, "64 拥抱", RelationshipThresholds.HUG_UNLOCK);
        addPresetButton(left + 198, y, 60, "128 交往", RelationshipThresholds.DATING_UNLOCK);

        y += 24;
        addPresetButton(left, y, 60, "192 结婚", RelationshipThresholds.MARRIAGE_UNLOCK);
        addMoodButton(left + 66, y, 60, "沮丧", 5);
        addMoodButton(left + 132, y, 60, "一般", 10);
        addMoodButton(left + 198, y, 60, "普通", 15);

        y += 24;
        addMoodButton(left, y, 94, "开心", 20);
        addMoodButton(left + 100, y, 158, "狗修金LOVE", 25);

        y += 36;
        this.addRenderableWidget(Button.builder(Component.literal("应用到女仆"), button -> applyDebugValues())
                .bounds(left, y, 128, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("刷新当前值"), button -> refreshFromTarget())
                .bounds(right, y, 92, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose())
                .bounds(right + 98, y, 30, 20)
                .build());
    }

    private void addPresetButton(int x, int y, int width, String label, int favorability) {
        this.addRenderableWidget(Button.builder(Component.literal(label), button ->
                        favorabilityBox.setValue(Integer.toString(favorability)))
                .bounds(x, y, width, 20)
                .build());
    }

    private void addMoodButton(int x, int y, int width, String label, int mood) {
        this.addRenderableWidget(Button.builder(Component.literal(label), button ->
                        moodBox.setValue(Integer.toString(mood)))
                .bounds(x, y, width, 20)
                .build());
    }

    private void applyDebugValues() {
        if (maidUuid == null) {
            setStatus("没有锁定目标：请对准女仆后再按 F7。", 0xFFFF8E8E);
            return;
        }

        int favorability = parseInt(favorabilityBox.getValue(), -1);
        int mood = parseInt(moodBox.getValue(), -1);
        if (favorability < 0 || mood < 0) {
            setStatus("请输入有效整数。", 0xFFFF8E8E);
            return;
        }

        int clampedFavorability = Mth.clamp(favorability, 0, FAVORABILITY_MAX);
        int clampedMood = Mth.clamp(mood, MaidMoodData.MIN_MOOD, MaidMoodData.MAX_MOOD);
        favorabilityBox.setValue(Integer.toString(clampedFavorability));
        moodBox.setValue(Integer.toString(clampedMood));

        ModNetworking.sendMaidDebugData(new MaidDebugDataPayload(maidUuid, clampedFavorability, clampedMood));
        setStatus("已发送到服务端，关闭重开互动 UI 可重新检查选项。", 0xFF9BD18B);
    }

    private void refreshFromTarget() {
        EntityMaid maid = findMaidByUuid(this.minecraft, maidUuid);
        if (maid == null) {
            setStatus("客户端附近找不到这个目标。", 0xFFFF8E8E);
            return;
        }
        favorabilityBox.setValue(Integer.toString(maid.getFavorability()));
        moodBox.setValue(Integer.toString(MaidMoodManager.value(maid)));
        setStatus("已读取当前客户端同步值。", 0xFF9BD18B);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int panelLeft = this.width / 2 - 160;
        int panelRight = this.width / 2 + 160;
        int panelTop = this.height / 2 - 92;
        int panelBottom = this.height / 2 + 92;

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xDD14121E);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 22, 0xEE201A33);
        graphics.fill(panelLeft + 10, panelTop + 38, panelRight - 10, panelBottom - 32, 0x55272735);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 7, 0xFFFFD6EA);
        graphics.drawString(this.font, Component.literal("目标：").append(targetName), panelLeft + 18, panelTop + 30, 0xFFD8D0EB, false);
        graphics.drawString(this.font, Component.literal("好感度 0~384"), favorabilityBox.getX(), favorabilityBox.getY() - 10, 0xFFD8D0EB, false);
        graphics.drawString(this.font, Component.literal("心情 5/10/15/20/25"), moodBox.getX(), moodBox.getY() - 10, 0xFFD8D0EB, false);
        graphics.drawWordWrap(this.font, status, panelLeft + 18, panelBottom - 24, 286, statusColor);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void setStatus(String text, int color) {
        this.status = Component.literal(text);
        this.statusColor = color;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean isSignedIntegerText(String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) {
            return true;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Nullable
    private static EntityMaid findMaidByUuid(Minecraft minecraft, @Nullable UUID maidUuid) {
        if (minecraft == null || minecraft.level == null || maidUuid == null) {
            return null;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof EntityMaid maid && maidUuid.equals(maid.getUUID())) {
                return maid;
            }
        }
        return null;
    }
}
