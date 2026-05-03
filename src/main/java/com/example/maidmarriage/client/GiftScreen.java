package com.example.maidmarriage.client;

import com.example.maidmarriage.compat.GiftTable;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.GiftSubmitPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * 送礼面板。
 *
 * <p>左侧展示玩家背包中的可选礼物，右侧只给出大致反应。
 * 精确收益不在 UI 里明牌，最终结算也仍然交给服务端统一处理。
 */
public final class GiftScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLUMNS = 9;
    private static final int GRID_ROWS = 4;
    private static final int GRID_COUNT = GRID_COLUMNS * GRID_ROWS;

    private final Screen parent;
    @Nullable
    private final UUID maidUuid;
    private final List<GiftSlot> giftSlots = new ArrayList<>();
    private Button sendButton;
    private Button cancelButton;
    private int selectedIndex = -1;
    private Component statusLine = Component.empty();
    private int statusColor = 0xFFD8D0EB;

    private GiftScreen(Screen parent, @Nullable UUID maidUuid) {
        super(Component.translatable("ui.maidmarriage.gift_screen.title"));
        this.parent = parent;
        this.maidUuid = maidUuid;
    }

    public static GiftScreen open(Screen parent, @Nullable UUID maidUuid) {
        return new GiftScreen(parent, maidUuid);
    }

    @Override
    protected void init() {
        rebuildGiftSlots();
        int panelLeft = this.width / 2 - 176;
        int panelTop = this.height / 2 - 111;
        sendButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.maidmarriage.gift_screen.send"), b -> submitGift())
                .bounds(panelLeft + 214, panelTop + 198, 64, 20)
                .build());
        cancelButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.maidmarriage.gift_screen.cancel"), b -> onClose())
                .bounds(panelLeft + 284, panelTop + 198, 56, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            return;
        }
        if (selectedIndex >= 0) {
            rebuildGiftSlots();
            if (selectedIndex >= giftSlots.size() || giftSlots.get(selectedIndex).stack().isEmpty()) {
                selectedIndex = -1;
            }
        }
        updateButtonState();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelLeft = this.width / 2 - 176;
        int panelTop = this.height / 2 - 111;
        int panelRight = panelLeft + 352;
        int panelBottom = panelTop + 222;

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE171520);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 20, 0xFF2A1E35);
        graphics.fill(panelLeft + 8, panelTop + 26, panelLeft + 176, panelBottom - 8, 0x551C1A25);
        graphics.fill(panelLeft + 182, panelTop + 26, panelRight - 8, panelBottom - 8, 0x551C1A25);

        graphics.drawString(this.font, this.title, panelLeft + 10, panelTop + 7, 0xFFF3E9FF, false);
        graphics.drawString(this.font, Component.translatable("ui.maidmarriage.gift_screen.inventory"), panelLeft + 12, panelTop + 30, 0xFFD8D0EB, false);
        graphics.drawString(this.font, Component.translatable("ui.maidmarriage.gift_screen.preview"), panelLeft + 186, panelTop + 30, 0xFFD8D0EB, false);

        drawInventoryGrid(graphics, panelLeft + 12, panelTop + 42, mouseX, mouseY);
        drawPreview(graphics, panelLeft + 188, panelTop + 42);

        if (!statusLine.getString().isBlank()) {
            graphics.drawWordWrap(this.font, statusLine, panelLeft + 12, panelBottom - 24, 320, statusColor);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoveredItemTooltip(graphics, panelLeft + 12, panelTop + 42, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int panelLeft = this.width / 2 - 176;
        int panelTop = this.height / 2 - 111;
        int gridX = panelLeft + 12;
        int gridY = panelTop + 42;
        for (int i = 0; i < giftSlots.size(); i++) {
            int slotX = gridX + (i % GRID_COLUMNS) * SLOT_SIZE;
            int slotY = gridY + (i / GRID_COLUMNS) * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                if (!giftSlots.get(i).stack().isEmpty()) {
                    selectedIndex = i;
                    statusLine = Component.translatable("ui.maidmarriage.gift_screen.selected", giftSlots.get(i).stack().getHoverName());
                    statusColor = 0xFFD8D0EB;
                    updateButtonState();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

    private void submitGift() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.player == null || selectedIndex < 0 || selectedIndex >= giftSlots.size()) {
            return;
        }
        GiftSlot slot = giftSlots.get(selectedIndex);
        if (slot.stack().isEmpty() || maidUuid == null) {
            statusLine = Component.translatable("ui.maidmarriage.gift_screen.no_selection");
            statusColor = 0xFFFF8E8E;
            return;
        }
        ModNetworking.sendGiftSubmit(new GiftSubmitPayload(maidUuid, slot.slotIndex()));
        onClose();
    }

    private void rebuildGiftSlots() {
        giftSlots.clear();
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < Math.min(inventory.items.size(), GRID_COUNT); i++) {
            giftSlots.add(new GiftSlot(i, inventory.items.get(i).copy()));
        }
        while (giftSlots.size() < GRID_COUNT) {
            giftSlots.add(new GiftSlot(giftSlots.size(), ItemStack.EMPTY));
        }
    }

    private void updateButtonState() {
        if (sendButton == null) {
            return;
        }
        boolean enabled = maidUuid != null
                && selectedIndex >= 0
                && selectedIndex < giftSlots.size()
                && !giftSlots.get(selectedIndex).stack().isEmpty()
                && selectedPreview().allowed();
        sendButton.active = enabled;
    }

    private void drawInventoryGrid(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < giftSlots.size(); i++) {
            GiftSlot slot = giftSlots.get(i);
            int slotX = x + (i % GRID_COLUMNS) * SLOT_SIZE;
            int slotY = y + (i / GRID_COLUMNS) * SLOT_SIZE;
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
            boolean selected = i == selectedIndex;
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, selected ? 0xAA6D4C7E : hovered ? 0x885A445C : 0x66373747);
            if (!slot.stack().isEmpty()) {
                graphics.renderItem(slot.stack(), slotX, slotY);
                graphics.renderItemDecorations(this.font, slot.stack(), slotX, slotY);
            }
        }
    }

    private void renderHoveredItemTooltip(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < giftSlots.size(); i++) {
            GiftSlot slot = giftSlots.get(i);
            if (slot.stack().isEmpty()) {
                continue;
            }
            int slotX = x + (i % GRID_COLUMNS) * SLOT_SIZE;
            int slotY = y + (i / GRID_COLUMNS) * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                graphics.renderTooltip(this.font, slot.stack(), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawPreview(GuiGraphics graphics, int x, int y) {
        GiftTable.GiftPreview preview = selectedPreview();
        GraphicsText.draw(graphics, this.font, Component.translatable("ui.maidmarriage.gift_screen.category",
                Component.translatable(categoryKey(preview.category()))), x, y, 0xFFF3E9FF);
        GraphicsText.draw(graphics, this.font, Component.translatable("ui.maidmarriage.gift_screen.reaction",
                Component.translatable(reactionKey(preview.reaction()))), x, y + 12, 0xFFD8D0EB);
        GraphicsText.drawWrapped(graphics, this.font, Component.translatable(preview.detailKey()), x, y + 32, 152, 0xFFFFE08A);
        GraphicsText.drawWrapped(graphics, this.font, Component.translatable("ui.maidmarriage.gift_screen.discovery_hint"), x, y + 96, 152, 0xFFCDBFE3);
    }

    private GiftTable.GiftPreview selectedPreview() {
        if (selectedIndex < 0 || selectedIndex >= giftSlots.size()) {
            return GiftTable.preview(ItemStack.EMPTY, resolveMaid());
        }
        return GiftTable.preview(giftSlots.get(selectedIndex).stack(), resolveMaid());
    }

    @Nullable
    private EntityMaid resolveMaid() {
        if (this.minecraft == null || maidUuid == null) {
            return null;
        }
        return ClientEntityLookup.findMaid(maidUuid);
    }

    private static String categoryKey(GiftTable.GiftCategory category) {
        return switch (category) {
            case FLOWER -> "ui.maidmarriage.gift.category.flower";
            case SWEET -> "ui.maidmarriage.gift.category.sweet";
            case MEAL -> "ui.maidmarriage.gift.category.meal";
            case VALUABLE -> "ui.maidmarriage.gift.category.valuable";
            case GENERIC -> "ui.maidmarriage.gift.category.generic";
            case ODD -> "ui.maidmarriage.gift.category.odd";
            case OFFENSIVE -> "ui.maidmarriage.gift.category.offensive";
            case SPECIAL_BLOCKED -> "ui.maidmarriage.gift.category.special";
            case EMPTY -> "ui.maidmarriage.gift.category.empty";
        };
    }

    private static String reactionKey(GiftTable.GiftReaction reaction) {
        return switch (reaction) {
            case LOVE -> "ui.maidmarriage.gift.reaction.love";
            case LIKE -> "ui.maidmarriage.gift.reaction.like";
            case WARM -> "ui.maidmarriage.gift.reaction.warm";
            case NORMAL -> "ui.maidmarriage.gift.reaction.normal";
            case HESITANT -> "ui.maidmarriage.gift.reaction.hesitant";
            case AWKWARD -> "ui.maidmarriage.gift.reaction.awkward";
            case DISLIKE -> "ui.maidmarriage.gift.reaction.dislike";
            case ANGRY -> "ui.maidmarriage.gift.reaction.angry";
            case BLOCKED -> "ui.maidmarriage.gift.reaction.blocked";
        };
    }

    private record GiftSlot(int slotIndex, ItemStack stack) {
    }

    private static final class GraphicsText {
        private static void draw(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text, int x, int y, int color) {
            graphics.drawString(font, text, x, y, color, false);
        }

        private static void drawWrapped(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text, int x, int y, int width, int color) {
            graphics.drawWordWrap(font, text, x, y, width, color);
        }
    }
}
