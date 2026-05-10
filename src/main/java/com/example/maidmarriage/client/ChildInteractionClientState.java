package com.example.maidmarriage.client;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;

/**
 * 小女仆互动会话的轻量客户端状态。
 *
 * <p>这层完全仿照成年女仆的拥抱 UI 唤起链：
 * 1. 服务端建立站立锁定会话；
 * 2. 回包同步到客户端；
 * 3. 客户端在 tick 中检测到会话存在后自动打开互动页。
 *
 * <p>这样小女仆不会再走“按键直接本地开 UI”的临时路线，
 * 而是和拥抱页一样，始终以服务端权威会话为准。
 */
public final class ChildInteractionClientState {
    @Nullable
    private static UUID localPlayerUuid;
    @Nullable
    private static UUID localInteractionMaidUuid;

    private ChildInteractionClientState() {
    }

    public static void handleSync(UUID playerUuid, @Nullable UUID maidUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !playerUuid.equals(minecraft.player.getUUID())) {
            return;
        }
        localPlayerUuid = playerUuid;
        localInteractionMaidUuid = maidUuid;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        localPlayerUuid = minecraft.player.getUUID();
        if (localInteractionMaidUuid != null && !hasEntityWithUuid(minecraft, localInteractionMaidUuid)) {
            localInteractionMaidUuid = null;
        }
    }

    public static void clear() {
        localPlayerUuid = null;
        localInteractionMaidUuid = null;
    }

    public static boolean isLocalPlayerInteracting() {
        return localPlayerUuid != null && localInteractionMaidUuid != null;
    }

    @Nullable
    public static UUID getLocalInteractionMaidUuid() {
        return localInteractionMaidUuid;
    }

    public static void ensureActionScreen(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        if (!isLocalPlayerInteracting()) {
            if (minecraft.screen instanceof HugActionScreen screen && screen.isChildInteractionScreen()) {
                minecraft.setScreen(null);
            }
            return;
        }

        /*
         * 小女仆互动会话期间，送礼面板也是合法的临时子界面。
         * 之前这里只认 HugActionScreen，导致 GiftScreen 刚打开就被下一帧重新顶回小女仆对话页。
         */
        if (minecraft.screen instanceof GiftScreen) {
            return;
        }

        if (!(minecraft.screen instanceof HugActionScreen screen && screen.isChildInteractionScreen())) {
            minecraft.setScreen(HugActionScreen.childInteraction(localInteractionMaidUuid));
            minecraft.mouseHandler.releaseMouse();
        }
    }

    private static boolean hasEntityWithUuid(Minecraft minecraft, UUID uuid) {
        return minecraft != null && minecraft.level != null && ClientEntityLookup.findEntity(uuid) != null;
    }
}
