package com.example.maidmarriage.compat;

import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.example.maidmarriage.network.ModNetworking;
import com.example.maidmarriage.network.payload.KissEffectPayload;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 亲密交互会话内的亲吻管理器。
 *
 * <p>这里不增加新的骑乘或复杂动作状态，只在现有拥抱锁定上叠加：
 * 1. 服务端校验目标是否合法；
 * 2. 双方短时持续对视；
 * 3. 发出爱心粒子；
 * 4. 不播放任何声音。
 */
public final class MaidKissManager {
    private static final String TAG_KISS_COOLDOWN_UNTIL = "maidmarriage_kiss_cooldown_until";
    private static final long KISS_COOLDOWN_TICKS = 12L;
    private static final long KISS_LOOK_TICKS = 14L;
    private static final long KISS_SHY_DELAY_TICKS = 18L;
    private static final long KISS_SHY_TURN_SETTLE_TICKS = 8L;
    private static final long KISS_SHY_DIALOGUE_INTERVAL_TICKS = 10L;
    private static final long KISS_SHY_DURATION_TICKS = 40L;
    private static final float KISS_SHY_HEAD_TURN_DEGREES = 85.0F;
    private static final float KISS_SHY_HEAD_PITCH_DEGREES = 5.0F;
    private static final int FAVORABILITY_GAIN = 1;
    private static final int FAVORABILITY_CAP = RelationshipThresholds.FAVORABILITY_MAX;
    private static final List<String> KISS_SHY_DIALOGUES = List.of(
            "dialogue.maidmarriage.kiss.shy.1",
            "dialogue.maidmarriage.kiss.shy.2",
            "dialogue.maidmarriage.kiss.shy.3"
    );
    private static final Map<UUID, KissSession> ACTIVE_KISS = new ConcurrentHashMap<>();
    private static final Map<UUID, ShyHeadTurnSession> ACTIVE_SHY_HEAD_TURN = new ConcurrentHashMap<>();

    private MaidKissManager() {
    }

    /**
     * 处理一次亲吻请求。
     * 只允许对当前交互锁定中的成年女仆触发，确保服务端和客户端目标一致。
     *
     * <p>这次重构后，亲吻被拆成两种表现：
     * - 站立锁定中的亲吻；
     * - 拥抱姿态中的亲吻。
     *
     * <p>按钮文案仍然共用“亲吻”这一项，但底层不再强制要求必须先处于拥抱姿态。
     */
    public static void handleKissRequest(ServerPlayer player, @Nullable UUID maidUuid) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(TAG_KISS_COOLDOWN_UNTIL) > now) {
            return;
        }

        EntityMaid maid = resolveTargetMaid(player, maidUuid);
        if (maid == null) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.kiss.no_target"));
            return;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.kiss.need_owner", maid.getDisplayName()));
            return;
        }
        /*
         * 亲吻和结婚一样，沿用同一套血缘限制：
         * 如果目标成年女仆在长期血缘数据上属于玩家的子代分支，
         * 那么无论她当前是否已经长大，都不能进入亲吻交互。
         *
         * 这里直接复用现有的血缘判定方法，保持和求婚/婚约逻辑一致，
         * 避免出现“结婚被禁止，但亲吻还能继续”的规则割裂。
         */
        if (MaidChildEntity.isParentOfMaid(maid, player.getUUID())) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.kiss.bloodline_blocked", maid.getDisplayName()));
            return;
        }
        if (!MaidHugManager.isInteractionState(maid, player)) {
            player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.kiss.need_interaction", maid.getDisplayName()));
            return;
        }

        player.getPersistentData().putLong(TAG_KISS_COOLDOWN_UNTIL, now + KISS_COOLDOWN_TICKS);
        ACTIVE_KISS.put(player.getUUID(), new KissSession(player.getUUID(), maid.getUUID(), now + KISS_LOOK_TICKS));

        maid.getLookControl().setLookAt(player, 80.0F, 80.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, maid.getEyePosition());

        double midX = (player.getX() + maid.getX()) * 0.5D;
        double midY = (player.getEyeY() + maid.getEyeY()) * 0.5D;
        double midZ = (player.getZ() + maid.getZ()) * 0.5D;
        level.sendParticles(ParticleTypes.HEART, midX, midY, midZ, 7, 0.10D, 0.08D, 0.10D, 0.01D);

        MaidMoodManager.applyLimitedInteractionMoodGain(maid, MaidMoodManager.EVENT_KISS);
        MaidMoodManager.applyInteractionFavorabilityGain(maid, FAVORABILITY_GAIN, FAVORABILITY_CAP);
        MaidMoodManager.markMeaningfulInteraction(maid);
        int shyDirectionSign = chooseShyDirection(maid);
        ModNetworking.sendKissEffect(player, new KissEffectPayload(
                maid.getUUID(),
                (int) KISS_SHY_DELAY_TICKS,
                (int) KISS_SHY_DURATION_TICKS,
                KISS_SHY_HEAD_TURN_DEGREES,
                KISS_SHY_HEAD_PITCH_DEGREES,
                shyDirectionSign
        ));
        player.sendSystemMessage(DialogueScriptManager.componentForPlayer(player, "message.maidmarriage.kiss.success", maid.getDisplayName()));

        /*
         * 亲吻成功后不要立刻别头。
         * 先留出一小段时间给“亲吻拉近镜头”完整播放，
         * 等镜头基本恢复后，再进入一个只转头不动身子的害羞收尾状态。
         */
        ACTIVE_SHY_HEAD_TURN.put(
                maid.getUUID(),
                new ShyHeadTurnSession(
                        player.getUUID(),
                        maid.getUUID(),
                        now + KISS_SHY_DELAY_TICKS,
                        now + KISS_SHY_DELAY_TICKS + KISS_SHY_DURATION_TICKS,
                        shyDirectionSign,
                        0,
                        now + KISS_SHY_DELAY_TICKS + KISS_SHY_TURN_SETTLE_TICKS
                )
        );
    }

    /**
     * 在亲吻后的短时间内维持“对视”，避免客户端插值造成的瞬间偏头。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ACTIVE_KISS.isEmpty()) {
            var iterator = ACTIVE_KISS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, KissSession> entry = iterator.next();
                KissSession session = entry.getValue();
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(session.playerUuid());
                if (player == null || !player.isAlive()) {
                    iterator.remove();
                    continue;
                }
                EntityMaid maid = MaidHugManager.getInteractingMaid(player);
                if (maid == null || !maid.isAlive() || !maid.getUUID().equals(session.maidUuid())) {
                    iterator.remove();
                    continue;
                }
                long now = player.serverLevel().getGameTime();
                if (now > session.expireTick()) {
                    iterator.remove();
                    continue;
                }
                maid.getLookControl().setLookAt(player, 80.0F, 80.0F);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, maid.getEyePosition());
            }
        }

        if (!ACTIVE_SHY_HEAD_TURN.isEmpty()) {
            var iterator = ACTIVE_SHY_HEAD_TURN.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, ShyHeadTurnSession> entry = iterator.next();
                ShyHeadTurnSession session = entry.getValue();
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(session.playerUuid());
                if (player == null || !player.isAlive()) {
                    iterator.remove();
                    continue;
                }

                Entity entity = player.serverLevel().getEntity(session.maidUuid());
                if (!(entity instanceof EntityMaid maid) || !maid.isAlive() || !MaidHugManager.isInteractionState(maid, player)) {
                    iterator.remove();
                    continue;
                }

                long now = player.serverLevel().getGameTime();
                if (now > session.endTick()) {
                    iterator.remove();
                    continue;
                }
                if (now < session.startTick()) {
                    continue;
                }

                if (session.dialogueIndex() >= KISS_SHY_DIALOGUES.size()) {
                    continue;
                }
                if (now < session.nextDialogueTick()) {
                    continue;
                }

                if (now >= session.startTick()) {
                    RomanceSleepManager.speakSingleLineWithChat(
                            maid,
                            KISS_SHY_DIALOGUES.get(session.dialogueIndex())
                    );
                    ACTIVE_SHY_HEAD_TURN.put(session.maidUuid(), session.advanceDialogue(now + KISS_SHY_DIALOGUE_INTERVAL_TICKS));
                }
            }
        }
    }

    @Nullable
    private static EntityMaid resolveTargetMaid(ServerPlayer player, @Nullable UUID maidUuid) {
        UUID resolvedUuid = maidUuid != null ? maidUuid : MaidHugManager.getInteractingMaidUuid(player);
        if (resolvedUuid == null) {
            return null;
        }
        Entity entity = player.serverLevel().getEntity(resolvedUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    /**
     * 让不同女仆在害羞时有稳定但不完全一致的偏头方向。
     * 同一只女仆每次都会偏向同一侧，视觉记忆会更自然。
     */
    private static int chooseShyDirection(EntityMaid maid) {
        /*
         * 上一版用户实测反馈“方向反了”，
         * 说明我们当时定义的左右侧与最终渲染骨骼的正负方向相反。
         * 这里直接整体翻转符号，保持同一只女仆仍有稳定侧偏习惯，
         * 但最终看到的方向与玩家预期一致。
         */
        return (maid.getUUID().getLeastSignificantBits() & 1L) == 0L ? -1 : 1;
    }

    private record KissSession(UUID playerUuid, UUID maidUuid, long expireTick) {
    }

    /**
     * 亲吻收尾的“害羞别头”状态。
     *
     * @param directionSign +1 代表向一侧偏头，-1 代表向另一侧偏头
     * @param dialogueSent  防止同一段害羞台词在持续时间内重复发送
     */
    private record ShyHeadTurnSession(
            UUID playerUuid,
            UUID maidUuid,
            long startTick,
            long endTick,
            int directionSign,
            int dialogueIndex,
            long nextDialogueTick
    ) {
        private ShyHeadTurnSession advanceDialogue(long nextTick) {
            return new ShyHeadTurnSession(playerUuid, maidUuid, startTick, endTick, directionSign, dialogueIndex + 1, nextTick);
        }
    }
}
