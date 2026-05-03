package com.example.maidmarriage.client;

import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.ChildNameSubmitPayload;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 小女仆首次命名面板。
 *
 * <p>这个界面只收集输入并提交妈妈 UUID；服务端会重新确认她怀里的孩子是否允许命名。
 */
public final class ChildNameScreen extends Screen {
    private static final int MAX_NAME_LENGTH = 24;

    @Nullable
    private final Screen parent;
    @Nullable
    private final UUID motherUuid;
    private EditBox nameBox;
    private Button confirmButton;

    private ChildNameScreen(@Nullable Screen parent, @Nullable UUID motherUuid) {
        super(Component.translatable("ui.maidmarriage.child_name.title"));
        this.parent = parent;
        this.motherUuid = motherUuid;
    }

    public static ChildNameScreen open(@Nullable Screen parent, @Nullable UUID motherUuid) {
        return new ChildNameScreen(parent, motherUuid);
    }

    @Override
    protected void init() {
        int panelLeft = this.width / 2 - 150;
        int panelTop = this.height / 2 - 70;
        nameBox = this.addRenderableWidget(new EditBox(
                this.font,
                panelLeft + 24,
                panelTop + 58,
                252,
                20,
                Component.translatable("ui.maidmarriage.child_name.input")
        ));
        nameBox.setMaxLength(MAX_NAME_LENGTH);
        nameBox.setHint(Component.translatable("ui.maidmarriage.child_name.input_hint"));
        nameBox.setResponder(value -> updateConfirmButton());
        setInitialFocus(nameBox);

        confirmButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("ui.maidmarriage.child_name.confirm"),
                        button -> submitName())
                .bounds(panelLeft + 66, panelTop + 104, 78, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("ui.maidmarriage.child_name.cancel"),
                        button -> onClose())
                .bounds(panelLeft + 156, panelTop + 104, 78, 20)
                .build());
        updateConfirmButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelLeft = this.width / 2 - 150;
        int panelTop = this.height / 2 - 70;
        graphics.fill(panelLeft, panelTop, panelLeft + 300, panelTop + 140, 0xEE171520);
        graphics.fill(panelLeft, panelTop, panelLeft + 300, panelTop + 22, 0xFF2A1E35);
        graphics.drawString(this.font, this.title, panelLeft + 12, panelTop + 8, 0xFFF3E9FF, false);
        graphics.drawWordWrap(
                this.font,
                Component.translatable("ui.maidmarriage.child_name.description"),
                panelLeft + 24,
                panelTop + 32,
                252,
                0xFFD8D0EB
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (confirmButton != null && confirmButton.active) {
                submitName();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void submitName() {
        if (motherUuid == null || nameBox == null) {
            return;
        }
        String name = nameBox.getValue() == null ? "" : nameBox.getValue().strip();
        if (name.isBlank()) {
            updateConfirmButton();
            return;
        }
        ModNetworking.sendChildNameSubmit(new ChildNameSubmitPayload(motherUuid, name));
        onClose();
    }

    private void updateConfirmButton() {
        if (confirmButton == null || nameBox == null) {
            return;
        }
        confirmButton.active = motherUuid != null && !nameBox.getValue().strip().isBlank();
    }
}
