package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.LapPillowActionPayload;
import com.example.maidmarriage.network.payload.LapPillowStateSyncPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端膝枕同步状态。
 *
 * <p>服务端负责真实锁位与校验；客户端这里只保存“谁正在膝枕”和“摸头动作还剩多久”，
 * 供 UI 条件和模型动作桥读取。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LapPillowClientState {
    private static final Map<UUID, UUID> PLAYER_TO_MAID = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> MAID_TO_PLAYER = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> PLAYER_TO_SLEEP_YAW = new ConcurrentHashMap<>();
    private static final Map<UUID, LapPillowStateSyncPayload.RecoveryStatus> PLAYER_TO_RECOVERY_STATUS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MAID_PET_START_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MAID_PET_UNTIL_TICK = new ConcurrentHashMap<>();
    public static int renderingDepth = 0;

    private LapPillowClientState() {
    }

    public static void handleSync(UUID playerUuid, @Nullable UUID maidUuid,
                                  boolean active, float sleepYaw, int petTicks,
                                  LapPillowStateSyncPayload.RecoveryStatus recoveryStatus) {
        if (!active || maidUuid == null) {
            UUID oldMaid = PLAYER_TO_MAID.remove(playerUuid);
            if (oldMaid != null) {
                MAID_TO_PLAYER.remove(oldMaid);
                MAID_PET_START_TICK.remove(oldMaid);
                MAID_PET_UNTIL_TICK.remove(oldMaid);
            }
            PLAYER_TO_SLEEP_YAW.remove(playerUuid);
            PLAYER_TO_RECOVERY_STATUS.remove(playerUuid);
            restoreForcedPoseIfLocal(playerUuid);
            return;
        }

        UUID oldMaid = PLAYER_TO_MAID.put(playerUuid, maidUuid);
        PLAYER_TO_SLEEP_YAW.put(playerUuid, sleepYaw);
        PLAYER_TO_RECOVERY_STATUS.put(playerUuid, recoveryStatus);
        if (oldMaid != null && !oldMaid.equals(maidUuid)) {
            MAID_TO_PLAYER.remove(oldMaid);
            MAID_PET_START_TICK.remove(oldMaid);
            MAID_PET_UNTIL_TICK.remove(oldMaid);
        }
        MAID_TO_PLAYER.put(maidUuid, playerUuid);
        applyForcedPoseIfLocal(playerUuid);
        if (petTicks > 0) {
            long now = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
            MAID_PET_START_TICK.put(maidUuid, now);
            MAID_PET_UNTIL_TICK.put(maidUuid, now + petTicks);
        }
    }

    /**
     * 客户端每 tick 维持 forcedPose 并清理短动作计时，不把玩家真实 Pose 改成睡觉。
     *
     * <p>膝枕需要“看起来躺下”，但不能触发原版床/睡觉 UI 语义。
     * forcedPose 只影响渲染表现；真实实体状态仍保持普通交互状态，避免剧情面板被隐藏。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            clear();
            return;
        }
        if (isLocalPlayerActive()) {
            if (TweakerooFreecamCompat.isFreecamActive(minecraft)) {
                ModNetworking.sendLapPillowAction(new LapPillowActionPayload(LapPillowActionPayload.ACTION_EXIT, null));
                minecraft.player.displayClientMessage(Component.translatable("message.maidmarriage.lap_pillow.freecam_exit"), false);
                minecraft.player.setForcedPose(null);
                clear();
                return;
            }
            minecraft.player.setForcedPose(Pose.SLEEPING);
            while (RhythmKeyMappings.LAP_PILLOW_EXIT.consumeClick()) {
                ModNetworking.sendLapPillowAction(new LapPillowActionPayload(LapPillowActionPayload.ACTION_EXIT, null));
                clear();
            }
        } else if (minecraft.player.getForcedPose() == Pose.SLEEPING) {
            /*
             * 客户端静态缓存如果因为切换存档、断线或异常包顺序丢了退出同步，
             * 这里会把残留睡姿清掉，避免玩家在新世界里仍然保持躺姿。
             */
            minecraft.player.setForcedPose(null);
        }
        if (minecraft.level != null && !MAID_PET_UNTIL_TICK.isEmpty()) {
            long now = minecraft.level.getGameTime();
            MAID_PET_UNTIL_TICK.entrySet().removeIf(entry -> {
                boolean expired = entry.getValue() <= now;
                if (expired) {
                    MAID_PET_START_TICK.remove(entry.getKey());
                }
                return expired;
            });
        }
    }

    public static boolean isLocalPlayerActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && PLAYER_TO_MAID.containsKey(minecraft.player.getUUID());
    }

    public static LapPillowStateSyncPayload.RecoveryStatus getLocalRecoveryStatus() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return LapPillowStateSyncPayload.RecoveryStatus.EMPTY;
        }
        return PLAYER_TO_RECOVERY_STATUS.getOrDefault(
                minecraft.player.getUUID(),
                LapPillowStateSyncPayload.RecoveryStatus.EMPTY
        );
    }

    public static boolean isPlayerActive(Player player) {
        return player != null && PLAYER_TO_MAID.containsKey(player.getUUID());
    }

    public static boolean isRealLocalPlayer(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        return player != null && minecraft.player == player;
    }

    /**
     * 渲染期睡姿桥。
     *
     * <p>膝枕时让玩家实体持续保持
     * {@code forcedPose=SLEEPING}，但不把玩家接入原版床的睡觉流程。
     * 客户端 mixin 会在渲染调用栈内把 {@code getPose/isSleeping/bedDirection}
     * 伪装成睡觉状态，从而让玩家模型真正躺下，同时我们的剧情 UI 仍然可以显示。
     */
    public static boolean shouldUseSleepPoseBridge(AbstractClientPlayer player) {
        return isRealLocalPlayer(player) && PLAYER_TO_MAID.containsKey(player.getUUID());
    }

    public static Direction resolveSleepDirection(AbstractClientPlayer player) {
        /*
         * 不要使用玩家当前 YRot。
         * 玩家 YRot 会被鼠标视角实时改动，如果直接拿它算床朝向，
         * 膝枕睡姿就会跟着镜头一起旋转。
         */
        float lockedYaw = PLAYER_TO_SLEEP_YAW.getOrDefault(player.getUUID(), player.getYRot());
        return Direction.fromYRot(lockedYaw);
    }

    @Nullable
    public static UUID getLocalLapPillowMaidUuid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        return PLAYER_TO_MAID.get(minecraft.player.getUUID());
    }

    public static boolean isLocalPlayerActiveWith(@Nullable UUID maidUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || maidUuid == null) {
            return false;
        }
        return maidUuid.equals(PLAYER_TO_MAID.get(minecraft.player.getUUID()));
    }

    public static boolean isLapPillowMaid(@Nullable UUID maidUuid) {
        return maidUuid != null && MAID_TO_PLAYER.containsKey(maidUuid);
    }

    public static void predictPetPlayerHead(@Nullable UUID maidUuid, int petTicks) {
        if (maidUuid == null || Minecraft.getInstance().level == null || !isLapPillowMaid(maidUuid)) {
            return;
        }
        long now = Minecraft.getInstance().level.getGameTime();
        MAID_PET_START_TICK.put(maidUuid, now);
        MAID_PET_UNTIL_TICK.put(maidUuid, now + Math.max(1, petTicks));
    }

    public static float petPlayerHeadProgress(@Nullable UUID maidUuid) {
        float rawProgress = petPlayerHeadRawProgress(maidUuid);
        if (rawProgress <= 0.0F) {
            return 0.0F;
        }
        /*
         * 膝枕安慰动作要有完整过程：
         * 先慢慢伸手，再停在头顶轻抚，最后自然收回。
         * 这里返回的是“手臂参与动作的强度”，具体轻抚的细微晃动由渲染层叠加。
         */
        if (rawProgress < 0.28F) {
            return smooth(rawProgress / 0.28F);
        }
        if (rawProgress < 0.82F) {
            return 1.0F;
        }
        return 1.0F - smooth((rawProgress - 0.82F) / 0.18F);
    }

    public static float petPlayerHeadRawProgress(@Nullable UUID maidUuid) {
        if (maidUuid == null || Minecraft.getInstance().level == null) {
            return 0.0F;
        }
        Long start = MAID_PET_START_TICK.get(maidUuid);
        Long until = MAID_PET_UNTIL_TICK.get(maidUuid);
        if (start == null || until == null) {
            return 0.0F;
        }
        long now = Minecraft.getInstance().level.getGameTime();
        long remaining = until - now;
        if (remaining <= 0L) {
            MAID_PET_START_TICK.remove(maidUuid);
            MAID_PET_UNTIL_TICK.remove(maidUuid);
            return 0.0F;
        }
        long duration = Math.max(1L, until - start);
        return Math.max(0.0F, Math.min(1.0F, (now - start) / (float) duration));
    }

    private static float smooth(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static void applyForcedPoseIfLocal(UUID playerUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && playerUuid.equals(minecraft.player.getUUID())) {
            minecraft.player.setForcedPose(Pose.SLEEPING);
        }
    }

    private static void restoreForcedPoseIfLocal(UUID playerUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && playerUuid.equals(minecraft.player.getUUID())) {
            minecraft.player.setForcedPose(null);
        }
    }

    public static void clear() {
        PLAYER_TO_MAID.clear();
        MAID_TO_PLAYER.clear();
        PLAYER_TO_SLEEP_YAW.clear();
        PLAYER_TO_RECOVERY_STATUS.clear();
        MAID_PET_START_TICK.clear();
        MAID_PET_UNTIL_TICK.clear();
        renderingDepth = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.setForcedPose(null);
        }
    }
}
