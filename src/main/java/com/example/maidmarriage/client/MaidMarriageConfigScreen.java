package com.example.maidmarriage.client;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.UpdateMaidAddressingPayload;
import com.example.maidmarriage.network.payload.UpdatePlayerSettingsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MaidMarriageConfigScreen extends Screen {
    private final Screen parent;

    private final List<AbstractWidget> generalWidgets = new ArrayList<>();
    private final List<AbstractWidget> rhythmWidgets = new ArrayList<>();
    private final List<AbstractWidget> actionWidgets = new ArrayList<>();
    private final List<AbstractWidget> debugWidgets = new ArrayList<>();

    private Button tabGeneral;
    private Button tabRhythm;
    private Button tabAction;
    private Button tabDebug;
    private Button doneButton;
    private Button resetButton;
    private EditBox maidAddressingBox;
    private EditBox childMaidAddressingBox;

    private Page activePage = Page.GENERAL;

    public MaidMarriageConfigScreen(Screen parent) {
        super(Component.translatable("config.maidmarriage.general"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.generalWidgets.clear();
        this.rhythmWidgets.clear();
        this.actionWidgets.clear();
        this.debugWidgets.clear();

        int panelLeft = this.width / 2 - 176;
        int panelWidth = 352;
        int panelTop = this.height / 6;

        int tabY = panelTop + 24;
        int tabW = 78;
        int tabGap = 8;

        tabGeneral = this.addRenderableWidget(Button.builder(Component.translatable("config.maidmarriage.general"),
                b -> setActivePage(Page.GENERAL)).bounds(panelLeft + 10, tabY, tabW, 20).build());
        tabRhythm = this.addRenderableWidget(Button.builder(Component.translatable("config.maidmarriage.rhythm_game"),
                b -> setActivePage(Page.RHYTHM)).bounds(panelLeft + 10 + tabW + tabGap, tabY, tabW, 20).build());
        tabAction = this.addRenderableWidget(Button.builder(Component.translatable("config.maidmarriage.action_keys"),
                b -> setActivePage(Page.ACTION)).bounds(panelLeft + 10 + (tabW + tabGap) * 2, tabY, tabW, 20).build());
        tabDebug = this.addRenderableWidget(Button.builder(Component.translatable("config.maidmarriage.debug"),
                b -> setActivePage(Page.DEBUG)).bounds(panelLeft + 10 + (tabW + tabGap) * 3, tabY, tabW, 20).build());

        int left = panelLeft + 16;
        int right = panelLeft + panelWidth / 2 + 4;
        int y = tabY + 28;

        // GENERAL PAGE
        addToPage(Page.GENERAL, CycleButton.booleanBuilder(Component.translatable("options.on"), Component.translatable("options.off"))
                .withInitialValue(ModConfigs.haremMode())
                .create(left, y, 156, 20, Component.translatable("config.maidmarriage.harem_mode"),
                        (btn, v) -> ModConfigs.setHaremMode(v)));

        y += 24;
        addToPage(Page.GENERAL, new IntSlider(left, y, 156, 20,
                Component.translatable("config.maidmarriage.pregnancy_birth_days"),
                1, 30, ModConfigs.pregnancyBirthDays(), ModConfigs::setPregnancyBirthDays));

        addToPage(Page.GENERAL, new IntSlider(right, y, 156, 20,
                Component.translatable("config.maidmarriage.child_growth_days"),
                3, 30, ModConfigs.childGrowthDays(), ModConfigs::setChildGrowthDays));

        y += 32;
        maidAddressingBox = addToPage(Page.GENERAL, new EditBox(this.font, left, y, 156, 20,
                Component.translatable("config.maidmarriage.maid_addressing")));
        maidAddressingBox.setMaxLength(64);
        maidAddressingBox.setValue(ModConfigs.maidAddressing());
        maidAddressingBox.setHint(Component.translatable("config.maidmarriage.maid_addressing.hint"));
        maidAddressingBox.setResponder(ModConfigs::setMaidAddressing);

        childMaidAddressingBox = addToPage(Page.GENERAL, new EditBox(this.font, right, y, 156, 20,
                Component.translatable("config.maidmarriage.child_maid_addressing")));
        childMaidAddressingBox.setMaxLength(64);
        childMaidAddressingBox.setValue(ModConfigs.childMaidAddressing());
        childMaidAddressingBox.setHint(Component.translatable("config.maidmarriage.child_maid_addressing.hint"));
        childMaidAddressingBox.setResponder(ModConfigs::setChildMaidAddressing);

        y += 34;
        addToPage(Page.GENERAL, Button.builder(Component.translatable("config.maidmarriage.open_guide"),
                        b -> this.minecraft.setScreen(new MaidMarriageGuideScreen(this)))
                .bounds(left, y, 320, 20)
                .build());

        // RHYTHM PAGE
        y = tabY + 28;
        addToPage(Page.RHYTHM, CycleButton.booleanBuilder(Component.translatable("options.on"), Component.translatable("options.off"))
                .withInitialValue(ModConfigs.rhythmAlwaysSkip())
                .create(left, y, 320, 20, Component.translatable("config.maidmarriage.rhythm_always_skip"),
                        (btn, v) -> ModConfigs.setRhythmAlwaysSkip(v)));

        // ACTION PAGE
        y = tabY + 28;
        addToPage(Page.ACTION, new DoubleSlider(left, y, 320, 20,
                Component.translatable("config.maidmarriage.lift_height"),
                -0.20, 1.50, ModConfigs.liftHeight(), ModConfigs::setLiftHeight));

        y += 24;
        addToPage(Page.ACTION, new DoubleSlider(left, y, 320, 20,
                Component.translatable("config.maidmarriage.hug_distance"),
                0.10, 2.00, ModConfigs.hugDistance(), ModConfigs::setHugDistance));

        // DEBUG PAGE
        y = tabY + 28;
        addToPage(Page.DEBUG, CycleButton.booleanBuilder(Component.translatable("options.on"), Component.translatable("options.off"))
                .withInitialValue(ModConfigs.enableDebugTools())
                .create(left, y, 156, 20, Component.translatable("config.maidmarriage.enable_debug_tools"),
                        (btn, v) -> ModConfigs.setEnableDebugTools(v)));

        y += 24;
        addToPage(Page.DEBUG, CycleButton.booleanBuilder(Component.translatable("options.on"), Component.translatable("options.off"))
                .withInitialValue(ModConfigs.showPregnancyDebugCountdown())
                .create(left, y, 156, 20, Component.translatable("config.maidmarriage.show_pregnancy_debug_countdown"),
                        (btn, v) -> ModConfigs.setShowPregnancyDebugCountdown(v)));

        addToPage(Page.DEBUG, CycleButton.booleanBuilder(Component.translatable("options.on"), Component.translatable("options.off"))
                .withInitialValue(ModConfigs.showUiActionDebug())
                .create(right, y, 156, 20, Component.translatable("config.maidmarriage.show_ui_action_debug"),
                        (btn, v) -> ModConfigs.setShowUiActionDebug(v)));

        resetButton = this.addRenderableWidget(Button.builder(Component.translatable("config.maidmarriage.reset_defaults"), b -> resetToDefaults())
                .bounds(this.width / 2 - 76, panelTop + 228, 74, 20)
                .build());

        doneButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 + 2, panelTop + 228, 74, 20)
                .build());

        setActivePage(Page.GENERAL);
    }

    private <T extends AbstractWidget> T addToPage(Page page, T widget) {
        this.addRenderableWidget(widget);
        switch (page) {
            case GENERAL -> generalWidgets.add(widget);
            case RHYTHM -> rhythmWidgets.add(widget);
            case ACTION -> actionWidgets.add(widget);
            case DEBUG -> debugWidgets.add(widget);
        }
        return widget;
    }

    private void setActivePage(Page page) {
        this.activePage = page;
        togglePage(generalWidgets, page == Page.GENERAL);
        togglePage(rhythmWidgets, page == Page.RHYTHM);
        togglePage(actionWidgets, page == Page.ACTION);
        togglePage(debugWidgets, page == Page.DEBUG);

        tabGeneral.active = page != Page.GENERAL;
        tabRhythm.active = page != Page.RHYTHM;
        tabAction.active = page != Page.ACTION;
        tabDebug.active = page != Page.DEBUG;

        tabGeneral.setMessage(page == Page.GENERAL
                ? Component.literal("> " + Component.translatable("config.maidmarriage.general").getString())
                : Component.translatable("config.maidmarriage.general"));
        tabRhythm.setMessage(page == Page.RHYTHM
                ? Component.literal("> " + Component.translatable("config.maidmarriage.rhythm_game").getString())
                : Component.translatable("config.maidmarriage.rhythm_game"));
        tabAction.setMessage(page == Page.ACTION
                ? Component.literal("> " + Component.translatable("config.maidmarriage.action_keys").getString())
                : Component.translatable("config.maidmarriage.action_keys"));
        tabDebug.setMessage(page == Page.DEBUG
                ? Component.literal("> " + Component.translatable("config.maidmarriage.debug").getString())
                : Component.translatable("config.maidmarriage.debug"));
    }

    private static void togglePage(List<AbstractWidget> widgets, boolean visible) {
        for (AbstractWidget widget : widgets) {
            widget.visible = visible;
            widget.active = visible;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int panelLeft = this.width / 2 - 176;
        int panelRight = this.width / 2 + 176;
        int panelTop = this.height / 6;
        int panelBottom = panelTop + 252;

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC14121E);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 20, 0xEE201A33);
        graphics.fill(panelLeft + 8, panelTop + 50, panelRight - 8, panelBottom - 26, 0x55272735);
        if (activePage == Page.GENERAL && maidAddressingBox != null && maidAddressingBox.visible) {
            graphics.drawString(this.font, Component.translatable("config.maidmarriage.maid_addressing"),
                    maidAddressingBox.getX(), maidAddressingBox.getY() - 10, 0xFFD8D0EB, false);
        }
        if (activePage == Page.GENERAL && childMaidAddressingBox != null && childMaidAddressingBox.visible) {
            graphics.drawString(this.font, Component.translatable("config.maidmarriage.child_maid_addressing"),
                    childMaidAddressingBox.getX(), childMaidAddressingBox.getY() - 10, 0xFFD8D0EB, false);
        }
        if (activePage == Page.DEBUG) {
            graphics.drawWordWrap(this.font, Component.translatable("config.maidmarriage.debug.hint"),
                    panelLeft + 16, panelTop + 96, 320, 0xFFB7ADCF);
            graphics.drawWordWrap(this.font, Component.translatable("config.maidmarriage.debug.status",
                            onOff(ModConfigs.enableDebugTools()),
                            onOff(ModConfigs.showPregnancyDebugCountdown()),
                            onOff(ModConfigs.showUiActionDebug())),
                    panelLeft + 16, panelTop + 128, 320, 0xFFD8D0EB);
            graphics.drawWordWrap(this.font, Component.translatable("config.maidmarriage.postpartum_server_only.hint"),
                    panelLeft + 16, panelTop + 156, 320, 0xFFFFD38B);
            graphics.drawWordWrap(this.font, Component.translatable("config.maidmarriage.server_authority.hint"),
                    panelLeft + 16, panelTop + 188, 320, 0xFFFFD38B);
        }
        if (activePage == Page.RHYTHM) {
            graphics.drawWordWrap(this.font, Component.translatable("config.maidmarriage.key_settings.hint"),
                    panelLeft + 16, panelTop + 148, 320, 0xFFB7ADCF);
        }
        if (activePage == Page.ACTION) {
            graphics.drawWordWrap(this.font, Component.translatable("config.maidmarriage.key_settings.hint"),
                    panelLeft + 16, panelTop + 136, 320, 0xFFB7ADCF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void resetToDefaults() {
        ModConfigs.setHaremMode(false);
        ModConfigs.setPregnancyBirthDays(5);
        ModConfigs.setChildGrowthDays(10);
        ModConfigs.setMaidAddressing("");
        ModConfigs.setChildMaidAddressing("爸爸");
        ModConfigs.setDialogueScriptPath("maidmarriage/custom-dialogues.json");
        ModConfigs.setRhythmAlwaysSkip(false);
        ModConfigs.setPregnancyChance(0.6D);
        ModConfigs.setLiftHeight(0.10D);
        ModConfigs.setHugDistance(0.80D);
        ModConfigs.setEnableDebugTools(false);
        ModConfigs.setShowPregnancyDebugCountdown(false);
        ModConfigs.setShowUiActionDebug(false);
        ModConfigs.setPostpartumRecoveryEnabled(true);
        DialogueScriptManager.reloadAll();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new MaidMarriageConfigScreen(parent));
        }
    }

    @Override
    public void onClose() {
        ModNetworking.sendUpdateMaidAddressing(new UpdateMaidAddressingPayload(
                ModConfigs.maidAddressing(),
                ModConfigs.childMaidAddressing()));
        syncRuntimeSettingsToServer();
        DialogueScriptManager.reloadAll();
        this.minecraft.setScreen(parent);
    }

    private void syncRuntimeSettingsToServer() {
        ModNetworking.sendUpdatePlayerSettings(new UpdatePlayerSettingsPayload(
                ModConfigs.liftHeight(),
                ModConfigs.hugDistance(),
                ModConfigs.haremMode()));
    }

    private static Component onOff(boolean enabled) {
        return Component.translatable(enabled ? "options.on" : "options.off");
    }

    private enum Page {
        GENERAL,
        RHYTHM,
        ACTION,
        DEBUG
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final Component label;
        private final int min;
        private final int max;
        private final java.util.function.IntConsumer setter;

        private IntSlider(int x, int y, int width, int height, Component label,
                          int min, int max, int value, java.util.function.IntConsumer setter) {
            super(x, y, width, height, Component.empty(), (value - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int current = min + (int) Math.round(value * (max - min));
            this.setMessage(Component.literal(label.getString() + ": " + current));
        }

        @Override
        protected void applyValue() {
            int current = min + (int) Math.round(value * (max - min));
            setter.accept(current);
        }
    }

    private static final class DoubleSlider extends AbstractSliderButton {
        private final Component label;
        private final double min;
        private final double max;
        private final java.util.function.DoubleConsumer setter;

        private DoubleSlider(int x, int y, int width, int height, Component label,
                             double min, double max, double value, java.util.function.DoubleConsumer setter) {
            super(x, y, width, height, Component.empty(), (value - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double current = min + value * (max - min);
            this.setMessage(Component.literal(label.getString() + ": " + String.format(Locale.ROOT, "%.2f", current)));
        }

        @Override
        protected void applyValue() {
            double current = min + value * (max - min);
            setter.accept(current);
        }
    }
}
