package com.example.maidmarriage.rhythm;

import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.StartRomanceRhythmPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class RomanceRhythmSync {
    /**
     * 节奏面板等待结果的超时时间。
     *
     * <p>旧值 20 秒过短：玩家如果完整打完一整套谱面，或者中途掉帧，
     * 服务端可能会先按“超时=0分”结算，导致客户端明明打完了，
     * 最终却像是没有正确结算、怀孕概率也异常偏低。
     *
     * <p>这里放宽到 60 秒，给完整谱面、低帧率和短暂卡顿留足余量。
     */
    private static final long DECISION_TIMEOUT_TICKS = 20L * 60L;
    private static final Map<UUID, PendingDecision> PENDING = new ConcurrentHashMap<>();

    private RomanceRhythmSync() {
    }

    public static void requestDecision(ServerPlayer player, EntityMaid maid, long conceivedGameTime, BlockPos playerBedPos) {
        PENDING.put(player.getUUID(), new PendingDecision(
                maid.getUUID(),
                conceivedGameTime,
                playerBedPos.immutable(),
                player.level().getGameTime() + DECISION_TIMEOUT_TICKS));
        ModNetworking.sendStart(player, new StartRomanceRhythmPayload(maid.getUUID()));
    }

    public static PendingDecision consume(UUID playerId) {
        return PENDING.remove(playerId);
    }

    public static PendingDecision peek(UUID playerId) {
        return PENDING.get(playerId);
    }

    public static void clear(UUID playerId) {
        PENDING.remove(playerId);
    }

    public static boolean isTimedOut(UUID playerId, long nowGameTime) {
        PendingDecision pending = PENDING.get(playerId);
        return pending != null && nowGameTime >= pending.expireGameTime();
    }

    public record PendingDecision(UUID maidUuid, long conceivedGameTime, BlockPos playerBedPos, long expireGameTime) {
    }
}
