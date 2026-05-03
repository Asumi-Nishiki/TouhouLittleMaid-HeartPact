package com.example.maidmarriage.client;

import net.minecraft.client.Minecraft;

public final class ClientNetworkState {
    private ClientNetworkState() {
    }

    public static boolean canSendToServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.getConnection() != null;
    }
}
