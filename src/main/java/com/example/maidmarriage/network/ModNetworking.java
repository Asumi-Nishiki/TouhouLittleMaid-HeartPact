package com.example.maidmarriage.network;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.compat.ChildInteractionManager;
import com.example.maidmarriage.compat.ChildNameManager;
import com.example.maidmarriage.compat.GiftManager;
import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.example.maidmarriage.compat.MaidDebugDataManager;
import com.example.maidmarriage.compat.MaidDialogueInteractionManager;
import com.example.maidmarriage.compat.MaidLiftManager;
import com.example.maidmarriage.compat.MaidHugManager;
import com.example.maidmarriage.compat.MaidKissManager;
import com.example.maidmarriage.compat.LapPillowManager;
import com.example.maidmarriage.compat.MaidStoryInteractionManager;
import com.example.maidmarriage.compat.PetHeadManager;
import com.example.maidmarriage.compat.RomanceSleepManager;
import com.example.maidmarriage.network.payload.CarryChildMaidPayload;
import com.example.maidmarriage.network.payload.CarryChildStateSyncPayload;
import com.example.maidmarriage.network.payload.ChildInteractionPayload;
import com.example.maidmarriage.network.payload.ChildInteractionStateSyncPayload;
import com.example.maidmarriage.network.payload.ChildNameSubmitPayload;
import com.example.maidmarriage.network.payload.DialogueChoiceResultPayload;
import com.example.maidmarriage.network.payload.FavorabilityEffectPayload;
import com.example.maidmarriage.network.payload.GiftResultPayload;
import com.example.maidmarriage.network.payload.GiftSubmitPayload;
import com.example.maidmarriage.network.payload.HugMaidPayload;
import com.example.maidmarriage.network.payload.HugStateSyncPayload;
import com.example.maidmarriage.network.payload.KissEffectPayload;
import com.example.maidmarriage.network.payload.KissMaidPayload;
import com.example.maidmarriage.network.payload.LapPillowActionPayload;
import com.example.maidmarriage.network.payload.LapPillowDebugPosePayload;
import com.example.maidmarriage.network.payload.LapPillowStateSyncPayload;
import com.example.maidmarriage.network.payload.LiftMaidPayload;
import com.example.maidmarriage.network.payload.LiftStateSyncPayload;
import com.example.maidmarriage.network.payload.MaidDebugDataPayload;
import com.example.maidmarriage.network.payload.PetHeadPayload;
import com.example.maidmarriage.network.payload.StartRomanceRhythmPayload;
import com.example.maidmarriage.network.payload.StoryProgressActionPayload;
import com.example.maidmarriage.network.payload.ToggleHugPosePayload;
import com.example.maidmarriage.network.payload.SubmitRomanceRhythmPayload;
import com.example.maidmarriage.network.payload.UpdateMaidAddressingPayload;
import com.example.maidmarriage.network.payload.UpdatePlayerSettingsPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL = "19";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MaidMarriageMod.MOD_ID, "main"),
            () -> PROTOCOL,
            ModNetworking::acceptRemoteVersion,
            ModNetworking::acceptRemoteVersion
    );

    private ModNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(StartRomanceRhythmPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StartRomanceRhythmPayload::encode)
                .decoder(StartRomanceRhythmPayload::decode)
                .consumerMainThread((msg, ctx) -> handleStartRomanceRhythmClient(msg))
                .add();

        CHANNEL.messageBuilder(SubmitRomanceRhythmPayload.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitRomanceRhythmPayload::encode)
                .decoder(SubmitRomanceRhythmPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        RomanceSleepManager.onRhythmPanelResult(sender, msg.maidUuid(), msg.rhythmScore());
                    }
                })
                .add();

        CHANNEL.messageBuilder(UpdateMaidAddressingPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateMaidAddressingPayload::encode)
                .decoder(UpdateMaidAddressingPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        RomanceSleepManager.updatePlayerMaidAddressing(sender, msg.addressing(), msg.childAddressing());
                    }
                })
                .add();

        CHANNEL.messageBuilder(UpdatePlayerSettingsPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdatePlayerSettingsPayload::encode)
                .decoder(UpdatePlayerSettingsPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                        ServerPlayer sender = ctx.get().getSender();
                        if (sender != null) {
                            MaidLiftManager.updatePlayerLiftSettings(sender, msg.liftHeight());
                            MaidHugManager.updatePlayerHugSettings(sender, msg.hugDistance());
                            RomanceSleepManager.updatePlayerHaremMode(sender, msg.haremMode());
                        }
                })
                .add();

        CHANNEL.messageBuilder(PetHeadPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PetHeadPayload::encode)
                .decoder(PetHeadPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        PetHeadManager.handlePetHeadRequest(sender, msg.maidUuid());
                    }
                })
                .add();

        CHANNEL.messageBuilder(LiftMaidPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LiftMaidPayload::encode)
                .decoder(LiftMaidPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidLiftManager.handleLiftToggle(sender, msg.maidUuid());
                    }
                })
                .add();

        CHANNEL.messageBuilder(LiftStateSyncPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LiftStateSyncPayload::encode)
                .decoder(LiftStateSyncPayload::decode)
                .consumerMainThread((msg, ctx) -> handleLiftStateSyncClient(msg))
                .add();

        CHANNEL.messageBuilder(HugMaidPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HugMaidPayload::encode)
                .decoder(HugMaidPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidHugManager.handleInteractionToggle(sender, msg.maidUuid());
                    }
                })
                .add();

        CHANNEL.messageBuilder(ToggleHugPosePayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleHugPosePayload::encode)
                .decoder(ToggleHugPosePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidHugManager.handleHugPoseToggle(sender, msg.maidUuid());
                    }
                })
                .add();

        CHANNEL.messageBuilder(HugStateSyncPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HugStateSyncPayload::encode)
                .decoder(HugStateSyncPayload::decode)
                .consumerMainThread((msg, ctx) -> handleHugStateSyncClient(msg))
                .add();

        CHANNEL.messageBuilder(ChildInteractionPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChildInteractionPayload::encode)
                .decoder(ChildInteractionPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        ChildInteractionManager.handleInteractionToggle(sender, msg.maidUuid());
                    }
                })
                .add();

        CHANNEL.messageBuilder(ChildInteractionStateSyncPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChildInteractionStateSyncPayload::encode)
                .decoder(ChildInteractionStateSyncPayload::decode)
                .consumerMainThread((msg, ctx) -> handleChildInteractionStateSyncClient(msg))
                .add();

        CHANNEL.messageBuilder(DialogueChoiceResultPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DialogueChoiceResultPayload::encode)
                .decoder(DialogueChoiceResultPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidDialogueInteractionManager.handleDialogueChoiceResult(
                                sender,
                                msg.maidUuid(),
                                msg.positiveFavor(),
                                msg.neutralFavor(),
                                msg.negativeFavor(),
                                msg.positiveMoodDelta(),
                                msg.neutralMoodDelta(),
                                msg.negativeMoodDelta()
                        );
                    }
                })
                .add();

        CHANNEL.messageBuilder(GiftSubmitPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GiftSubmitPayload::encode)
                .decoder(GiftSubmitPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        GiftManager.handleGiftSubmit(sender, msg.maidUuid(), msg.slotIndex());
                    }
                })
                .add();

        CHANNEL.messageBuilder(GiftResultPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GiftResultPayload::encode)
                .decoder(GiftResultPayload::decode)
                .consumerMainThread((msg, ctx) -> handleGiftResultClient(msg))
                .add();

        CHANNEL.messageBuilder(KissMaidPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KissMaidPayload::encode)
                .decoder(KissMaidPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidKissManager.handleKissRequest(sender, msg.maidUuid());
                    }
                })
                .add();

        CHANNEL.messageBuilder(KissEffectPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KissEffectPayload::encode)
                .decoder(KissEffectPayload::decode)
                .consumerMainThread((msg, ctx) -> handleKissEffectClient(msg))
                .add();

        CHANNEL.messageBuilder(FavorabilityEffectPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FavorabilityEffectPayload::encode)
                .decoder(FavorabilityEffectPayload::decode)
                .consumerMainThread((msg, ctx) -> handleFavorabilityEffectClient(msg))
                .add();

        CHANNEL.messageBuilder(CarryChildMaidPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CarryChildMaidPayload::encode)
                .decoder(CarryChildMaidPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidCarryChildManager.handleCarryToggle(sender, msg.childUuid());
                    }
                })
                .add();

        CHANNEL.messageBuilder(CarryChildStateSyncPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CarryChildStateSyncPayload::encode)
                .decoder(CarryChildStateSyncPayload::decode)
                .consumerMainThread((msg, ctx) -> handleCarryChildStateSyncClient(msg))
                .add();

        CHANNEL.messageBuilder(LapPillowActionPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LapPillowActionPayload::encode)
                .decoder(LapPillowActionPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        switch (msg.action()) {
                            case LapPillowActionPayload.ACTION_START -> LapPillowManager.handleStart(sender, msg.maidUuid());
                            case LapPillowActionPayload.ACTION_EXIT -> LapPillowManager.handleExit(sender);
                            case LapPillowActionPayload.ACTION_PET_PLAYER_HEAD -> LapPillowManager.handlePetPlayerHead(sender, msg.maidUuid());
                            default -> {
                            }
                        }
                    }
                })
                .add();

        CHANNEL.messageBuilder(LapPillowStateSyncPayload.class, ++id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LapPillowStateSyncPayload::encode)
                .decoder(LapPillowStateSyncPayload::decode)
                .consumerMainThread((msg, ctx) -> handleLapPillowStateSyncClient(msg))
                .add();

        CHANNEL.messageBuilder(LapPillowDebugPosePayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LapPillowDebugPosePayload::encode)
                .decoder(LapPillowDebugPosePayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        LapPillowManager.handleDebugPose(sender, msg.sideOffset(), msg.heightOffset(), msg.forwardOffset(), msg.yawOffset());
                    }
                })
                .add();

        CHANNEL.messageBuilder(MaidDebugDataPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MaidDebugDataPayload::encode)
                .decoder(MaidDebugDataPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidDebugDataManager.handleDebugData(sender, msg.maidUuid(), msg.favorability(), msg.mood());
                    }
                })
                .add();

        CHANNEL.messageBuilder(StoryProgressActionPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StoryProgressActionPayload::encode)
                .decoder(StoryProgressActionPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        MaidStoryInteractionManager.handleStoryAction(sender, msg.maidUuid(), msg.actionId());
                    }
                })
                .add();

        CHANNEL.messageBuilder(ChildNameSubmitPayload.class, ++id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChildNameSubmitPayload::encode)
                .decoder(ChildNameSubmitPayload::decode)
                .consumerMainThread((msg, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        ChildNameManager.handleNameSubmit(sender, msg.motherUuid(), msg.name());
                    }
                })
                .add();
    }

    public static void sendStart(ServerPlayer player, StartRomanceRhythmPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendSubmit(SubmitRomanceRhythmPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendUpdateMaidAddressing(UpdateMaidAddressingPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendUpdatePlayerSettings(UpdatePlayerSettingsPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendPetHead(PetHeadPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendLiftMaid(LiftMaidPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendLiftState(ServerPlayer player, LiftStateSyncPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendHugMaid(HugMaidPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendToggleHugPose(ToggleHugPosePayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendHugState(ServerPlayer player, HugStateSyncPayload payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), payload);
    }

    public static void sendChildInteraction(ChildInteractionPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendChildInteractionState(ServerPlayer player, ChildInteractionStateSyncPayload payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), payload);
    }

    public static void sendDialogueChoiceResult(DialogueChoiceResultPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendGiftSubmit(GiftSubmitPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendGiftResult(ServerPlayer player, GiftResultPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendKissMaid(KissMaidPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendKissEffect(ServerPlayer player, KissEffectPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendFavorabilityEffect(net.minecraft.world.entity.Entity entity, FavorabilityEffectPayload payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), payload);
    }

    public static void sendCarryChildMaid(CarryChildMaidPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendLapPillowAction(LapPillowActionPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendLapPillowDebugPose(LapPillowDebugPosePayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendMaidDebugData(MaidDebugDataPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendStoryProgressAction(StoryProgressActionPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    public static void sendChildNameSubmit(ChildNameSubmitPayload payload) {
        if (!canSendToServer()) {
            return;
        }
        CHANNEL.sendToServer(payload);
    }

    private static boolean canSendToServer() {
        Boolean canSend = DistExecutor.safeCallWhenOn(
                Dist.CLIENT,
                () -> com.example.maidmarriage.client.ClientNetworkState::canSendToServer
        );
        return Boolean.TRUE.equals(canSend);
    }

    public static void sendCarryChildState(ServerPlayer player, CarryChildStateSyncPayload payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), payload);
    }

    public static void sendLapPillowState(ServerPlayer player, LapPillowStateSyncPayload payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), payload);
    }

    private static void handleStartRomanceRhythmClient(StartRomanceRhythmPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.example.maidmarriage.client.RomanceRhythmHud.start(msg.maidUuid()));
    }

    private static void handleLiftStateSyncClient(LiftStateSyncPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                MaidLiftManager.handleClientLiftStateSync(msg.playerUuid(), msg.maidUuid(), msg.proxyUuid(), msg.liftHeight()));
    }

    private static void handleHugStateSyncClient(HugStateSyncPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                {
                    MaidHugManager.handleClientHugStateSync(msg.playerUuid(), msg.maidUuid(), msg.hugging());
                    com.example.maidmarriage.client.HugClientState.handleSync(
                            msg.playerUuid(),
                            msg.maidUuid(),
                            msg.hugging(),
                            msg.childNameRequired()
                    );
                });
    }

    private static void handleChildInteractionStateSyncClient(ChildInteractionStateSyncPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                {
                    ChildInteractionManager.handleClientInteractionStateSync(msg.playerUuid(), msg.maidUuid());
                    com.example.maidmarriage.client.ChildInteractionClientState.handleSync(msg.playerUuid(), msg.maidUuid());
                });
    }

    private static void handleKissEffectClient(KissEffectPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                {
                    com.example.maidmarriage.client.HugCameraZoom.playKissZoom();
                    com.example.maidmarriage.client.HugClientState.startPostKissShyTurn(
                            msg.maidUuid(),
                            msg.shyDelayTicks(),
                            msg.shyDurationTicks(),
                            msg.shyHeadYawDegrees(),
                            msg.shyHeadPitchDegrees(),
                            msg.shyDirectionSign()
                    );
                });
    }

    private static void handleFavorabilityEffectClient(FavorabilityEffectPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.example.maidmarriage.client.FavorabilityPopupClient.show(msg.maidUuid(), msg.delta()));
    }

    private static void handleGiftResultClient(GiftResultPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.example.maidmarriage.client.HugActionScreen.handleGiftResult(msg));
    }

    private static void handleCarryChildStateSyncClient(CarryChildStateSyncPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                MaidCarryChildManager.handleClientCarryStateSync(
                        msg.ownerUuid(),
                        msg.adultUuid(),
                        msg.childUuid(),
                        msg.proxyUuid()
                ));
    }

    private static void handleLapPillowStateSyncClient(LapPillowStateSyncPayload msg) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.example.maidmarriage.client.LapPillowClientState.handleSync(
                        msg.playerUuid(),
                        msg.maidUuid(),
                        msg.active(),
                        msg.sleepYaw(),
                        msg.petTicks()
                ));
    }

    private static boolean acceptRemoteVersion(String remoteVersion) {
        return PROTOCOL.equals(remoteVersion);
    }
}
