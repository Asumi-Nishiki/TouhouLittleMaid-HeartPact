package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.compat.MaidCarryChildManager;
import com.example.maidmarriage.compat.MaidMoodManager;
import com.example.maidmarriage.compat.MaidRelationshipManager;
import com.example.maidmarriage.compat.RelationStage;
import com.example.maidmarriage.compat.RelationshipThresholds;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.data.MarriageData;
import com.example.maidmarriage.data.MaidMoodData;
import com.example.maidmarriage.data.ModTaskData;
import com.example.maidmarriage.entity.MaidChildEntity;
import com.example.maidmarriage.init.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 拥抱/互动剧情的客户端变量绑定器。
 *
 * <p>这层专门负责把“当前女仆状态、玩家关系、世界时间、天气”翻译成剧情 JSON 能读懂的变量。
 * 这样 {@code HugActionScreen} 就不用继续膨胀成半个业务管理器，只保留 UI 渲染和鼠标键盘交互。
 */
public final class HugDialogueContextVariables {
    private static final String TAG_RING_USED = "maidmarriage_ring_used";

    private HugDialogueContextVariables() {
    }

    /**
     * 刷新当前帧剧情条件变量。
     *
     * <p>这些变量会被 {@code hug_menu_v2.json} 的 {@code condition} 使用，例如：
     * {@code favor_hug_unlocked} 控制拥抱选项是否出现，{@code weather_rain} 控制雨天对白分支。
     */
    public static void refresh(HugDialogueRuntimeBridge dialogueRuntime,
                               @Nullable UUID targetMaidUuid,
                               String playerMaidAddressing,
                               String playerChildAddressing,
                               String poolAnchor) {
        if (dialogueRuntime == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Entity targetEntity = findEntityByUuid(minecraft, targetMaidUuid);
        Player player = minecraft.player;
        String timeOfDay = resolveTimeOfDay(minecraft);
        String weather = resolveWeather(minecraft);

        dialogueRuntime.setVariable("player_maid", safeValue(playerMaidAddressing));
        dialogueRuntime.setVariable("player_child", safeValue(playerChildAddressing));
        dialogueRuntime.setVariable("bloodline_kiss_blocked", Boolean.toString(isBloodlineKissBlocked(minecraft, targetEntity)));
        writeWorldVariables(dialogueRuntime, timeOfDay, weather);
        writeJokeVariables(dialogueRuntime, poolAnchor);
        writeMaidStateVariables(dialogueRuntime, player, targetEntity, playerChildAddressing, poolAnchor, timeOfDay);
    }

    private static void writeJokeVariables(HugDialogueRuntimeBridge dialogueRuntime, String poolAnchor) {
        String anchor = safeValue(poolAnchor);
        if (anchor.equals(dialogueRuntime.renderTemplate("${joke_pool_anchor}"))
                && !dialogueRuntime.renderTemplate("${joke_pick}").isBlank()) {
            return;
        }
        int pick = ThreadLocalRandom.current().nextInt(10) + 1;
        dialogueRuntime.setVariable("joke_pool_anchor", anchor);
        dialogueRuntime.setVariable("joke_pick", "joke_" + pick);
    }

    private static void writeMaidStateVariables(HugDialogueRuntimeBridge dialogueRuntime,
                                                @Nullable Player player,
                                                @Nullable Entity targetEntity,
                                                String playerChildAddressing,
                                                String poolAnchor,
                                                String timeOfDay) {
        if (!(targetEntity instanceof EntityMaid maid)) {
            /**
             * 当前没有真正拿到成年女仆实体时，仍然要把变量写成一套完整的默认值。
             *
             * 这样前端剧情条件在任何时刻都能稳定读取：
             * - 不会因为变量不存在导致条件判断抖动；
             * - 不会因为某个 Screen 打开时机偏早而拿到半套上下文。
             */
            dialogueRuntime.setVariable("favorability", "0");
            dialogueRuntime.setVariable("favor_pet_unlocked", "false");
            dialogueRuntime.setVariable("favor_hug_unlocked", "false");
            dialogueRuntime.setVariable("favor_kiss_unlocked", "false");
            dialogueRuntime.setVariable("favor_marriage_unlocked", "false");
            dialogueRuntime.setVariable("confession_completed", "false");
            dialogueRuntime.setVariable("marriage_completed", "false");
            dialogueRuntime.setVariable("can_show_confession", "false");
            dialogueRuntime.setVariable("can_show_marriage", "false");
            dialogueRuntime.setVariable("player_offhand_proposal_ring", "false");
            dialogueRuntime.setVariable("maid_mainhand_proposal_ring", "false");
            dialogueRuntime.setVariable("player_offhand_ring_unused", "false");
            dialogueRuntime.setVariable("maid_mainhand_ring_unused", "false");
            dialogueRuntime.setVariable("marriage_ring_ready", "false");
            dialogueRuntime.setVariable("marriage_commit_ready", "false");
            dialogueRuntime.setVariable("maid_carrying_child", "false");
            dialogueRuntime.setVariable("carried_child_infant", "false");
            dialogueRuntime.setVariable("carried_child_name_pending", "false");
            dialogueRuntime.setVariable("carried_child_default_named_infant", "false");
            dialogueRuntime.setVariable("child_carried_by_mother", "false");
            dialogueRuntime.setVariable("child_infant", "false");
            dialogueRuntime.setVariable("child_entry_speaker", "小女仆");
            dialogueRuntime.setVariable("child_entry_text", pickChildEntryText(playerChildAddressing, false, MaidChildEntity.GrowthStage.CHILD));
            writeFavorStageVariables(dialogueRuntime, RelationStage.INITIAL);
            writeMoodVariables(dialogueRuntime, MaidMoodData.MoodState.NORMAL);
            writeV4PoolVariables(dialogueRuntime,
                    RelationStage.INITIAL,
                    MaidMoodData.MoodState.NORMAL,
                    poolAnchor,
                    timeOfDay,
                    false);
            return;
        }

        int favorability = maid.getFavorability();
        /**
         * 好感数值本身仍然保留给 JSON 用，
         * 但“关系阶段”也会额外算一份语义化变量出来。
         *
         * 这么做的意义是：
         * - JSON 条件想用具体数值时，还能直接读 favorability；
         * - UI / 文案 / 特效如果只关心阶段，就不用自己再写阈值判断。
         */
        RelationStage relationStage = MaidRelationshipManager.resolveStage(maid);
        boolean confessionCompleted = MaidRelationshipManager.isConfessionCompleted(maid);
        boolean marriageCompleted = MaidRelationshipManager.isMarried(maid);
        dialogueRuntime.setVariable("favorability", Integer.toString(favorability));
        dialogueRuntime.setVariable("favor_pet_unlocked", Boolean.toString(favorability >= RelationshipThresholds.PET_UNLOCK));
        dialogueRuntime.setVariable("favor_hug_unlocked", Boolean.toString(favorability >= RelationshipThresholds.HUG_UNLOCK));
        dialogueRuntime.setVariable("favor_kiss_unlocked", Boolean.toString(MaidRelationshipManager.isKissUnlocked(maid)));
        dialogueRuntime.setVariable("favor_marriage_unlocked", Boolean.toString(favorability >= MaidRelationshipManager.MARRIAGE_UNLOCK_FAVORABILITY));
        dialogueRuntime.setVariable("confession_completed", Boolean.toString(confessionCompleted));
        dialogueRuntime.setVariable("marriage_completed", Boolean.toString(marriageCompleted));
        boolean blockedByClientMonogamy = hasOtherLoadedMarriageOnClient(player, maid);
        dialogueRuntime.setVariable("can_show_confession", Boolean.toString(
                MaidRelationshipManager.canShowConfession(player, maid) && !blockedByClientMonogamy));
        dialogueRuntime.setVariable("can_show_marriage", Boolean.toString(
                MaidRelationshipManager.canShowMarriage(player, maid) && !blockedByClientMonogamy));
        writeMarriageRingVariables(dialogueRuntime, player, maid);
        dialogueRuntime.setVariable(
                "maid_carrying_child",
                Boolean.toString(!MaidChildEntity.shouldStayChild(maid) && MaidCarryChildManager.isCarryAdultState(maid))
        );
        dialogueRuntime.setVariable("carried_child_infant", Boolean.toString(hasCarriedInfant(maid)));
        /*
         * 命名剧情入口优先认服务端随交互会话同步下来的权威标记。
         * 出生后第一时间，客户端可能还没拿到小女仆乘客或 TaskData；如果只靠本地实体推断，
         * UI 就会误进普通菜单。
         */
        boolean childNamePending = com.example.maidmarriage.client.HugClientState.isLocalChildNameRequired()
                || hasCarriedUnnamedChild(maid);
        dialogueRuntime.setVariable("carried_child_name_pending", Boolean.toString(childNamePending));
        dialogueRuntime.setVariable("carried_child_default_named_infant", Boolean.toString(hasCarriedDefaultNamedInfant(maid)));
        writeChildInteractionVariables(dialogueRuntime, maid, playerChildAddressing);
        writeFavorStageVariables(dialogueRuntime, relationStage);

        MaidMoodData.MoodState moodState = MaidMoodManager.state(maid);
        writeMoodVariables(dialogueRuntime, moodState);
        writeV4PoolVariables(dialogueRuntime, relationStage, moodState, poolAnchor, timeOfDay,
                MaidMoodManager.isLongingForInteraction(maid));
    }

    private static void writeChildInteractionVariables(HugDialogueRuntimeBridge dialogueRuntime,
                                                       EntityMaid maid,
                                                       String playerChildAddressing) {
        if (!MaidChildEntity.shouldStayChild(maid)) {
            dialogueRuntime.setVariable("child_carried_by_mother", "false");
            dialogueRuntime.setVariable("child_infant", "false");
            dialogueRuntime.setVariable("child_entry_speaker", safeValue(maid.getDisplayName().getString()));
            dialogueRuntime.setVariable("child_entry_text", pickChildEntryText(playerChildAddressing, false, MaidChildEntity.GrowthStage.ADULT));
            return;
        }

        MaidChildEntity.GrowthStage stage = MaidChildEntity.resolveGrowthStage(maid);
        EntityMaid carrier = null;
        if (maid.getVehicle() instanceof EntityMaid vehicle) {
            carrier = vehicle;
        }
        if (carrier == null) {
            carrier = MaidCarryChildManager.getCarryAdult(maid);
        }
        boolean carriedByMother = carrier != null && MaidChildEntity.isMotherOfChild(maid, carrier);
        dialogueRuntime.setVariable("child_carried_by_mother", Boolean.toString(carriedByMother));
        dialogueRuntime.setVariable("child_infant", Boolean.toString(stage == MaidChildEntity.GrowthStage.INFANT));
        dialogueRuntime.setVariable("child_entry_speaker", safeValue(maid.getDisplayName().getString()));
        dialogueRuntime.setVariable("child_entry_text", pickChildEntryText(playerChildAddressing, false, stage));
    }

    private static boolean hasCarriedUnnamedChild(EntityMaid maid) {
        if (maid == null || MaidChildEntity.shouldStayChild(maid)) {
            return false;
        }
        for (Entity passenger : maid.getPassengers()) {
            if (passenger instanceof EntityMaid child
                    && MaidChildEntity.shouldStayChild(child)
                    && MaidChildEntity.isMotherOfChild(child, maid)
                    && !MaidChildEntity.hasConfirmedChildName(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 成年女仆怀里是否有婴儿阶段的小女仆。
     *
     * <p>这个变量专门给“放下小女仆”这类 UI 结果分支用：婴儿第一天服务端会拒绝放下，
     * UI 也必须显示拒绝台词，不能提前显示“已经放下”的成功文本。
     */
    private static boolean hasCarriedInfant(EntityMaid maid) {
        if (maid == null || MaidChildEntity.shouldStayChild(maid)) {
            return false;
        }
        for (Entity passenger : maid.getPassengers()) {
            if (passenger instanceof EntityMaid child
                    && MaidChildEntity.shouldStayChild(child)
                    && MaidChildEntity.resolveGrowthStage(child) == MaidChildEntity.GrowthStage.INFANT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 命名入口的稳定 UI 条件。
     *
     * <p>出生流程会先给新生儿一个默认显示名“小女仆”。玩家正式取名后，
     * 显示名会变成玩家输入的名字。这里直接按“妈妈怀里有婴儿阶段且仍叫默认名的小女仆”
     * 判断是否显示取名剧情入口，避免再依赖客户端拿不到的服务端确认标记。
     */
    private static boolean hasCarriedDefaultNamedInfant(EntityMaid maid) {
        if (maid == null || MaidChildEntity.shouldStayChild(maid)) {
            return false;
        }
        for (Entity passenger : maid.getPassengers()) {
            if (passenger instanceof EntityMaid child
                    && MaidChildEntity.shouldStayChild(child)
                    && MaidChildEntity.resolveGrowthStage(child) == MaidChildEntity.GrowthStage.INFANT
                    && isDefaultChildName(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDefaultChildName(EntityMaid child) {
        String name = safeValue(child.getDisplayName().getString()).strip();
        return "小女仆".equals(name)
                || "Little Maid".equalsIgnoreCase(name)
                || "entity.maidmarriage.maid_child".equals(name);
    }

    private static String pickChildEntryText(String playerChildAddressing,
                                             boolean carriedByMother,
                                             MaidChildEntity.GrowthStage stage) {
        String address = safeValue(playerChildAddressing).isBlank() ? "主人" : safeValue(playerChildAddressing);
        if (carriedByMother) {
            return switch (ThreadLocalRandom.current().nextInt(6)) {
                case 0 -> address + "，我们的孩子真可爱呢……你看，她刚刚还偷偷抓着我的袖口。";
                case 1 -> address + "，她在怀里睡得好乖。今天就让她多靠着妈妈一会儿吧。";
                case 2 -> address + "，你看她的小手，是不是一直想抓住我们呀？";
                case 3 -> address + "，抱着她的时候，总觉得家里一下子热闹起来了。";
                case 4 -> address + "，她刚才轻轻蹭了我一下……小小的，软软的，真的很让人安心。";
                default -> address + "，等她再长大一点，就能自己跑过来抱住我们了吧。";
            };
        }
        if (stage == MaidChildEntity.GrowthStage.INFANT) {
            return "咿呀咿呀咿呀……呜呜呜呜。\n她似乎还不会说话。\n小女仆才刚出生，请不要强制把她放下来。";
        }
        return address + "，今天也要陪我玩吗？我会乖乖听话的！";
    }

    private static void writeMarriageRingVariables(HugDialogueRuntimeBridge dialogueRuntime, net.minecraft.world.entity.player.Player player, EntityMaid maid) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack playerOffhand = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getOffhandItem();
        ItemStack maidMainhand = maid == null ? ItemStack.EMPTY : maid.getMainHandItem();
        boolean playerHasRing = isProposalRing(playerOffhand);
        boolean maidHasRing = isProposalRing(maidMainhand);
        boolean playerRingUnused = playerHasRing && !isRingUsed(playerOffhand);
        boolean maidRingUnused = maidHasRing && !isRingUsed(maidMainhand);
        boolean relationshipReady = MaidRelationshipManager.canShowMarriage(player, maid);
        dialogueRuntime.setVariable("player_offhand_proposal_ring", Boolean.toString(playerHasRing));
        dialogueRuntime.setVariable("maid_mainhand_proposal_ring", Boolean.toString(maidHasRing));
        dialogueRuntime.setVariable("player_offhand_ring_unused", Boolean.toString(playerRingUnused));
        dialogueRuntime.setVariable("maid_mainhand_ring_unused", Boolean.toString(maidRingUnused));
        dialogueRuntime.setVariable("marriage_ring_ready", Boolean.toString(playerHasRing && maidHasRing));
        dialogueRuntime.setVariable("marriage_commit_ready", Boolean.toString(playerRingUnused && maidRingUnused && relationshipReady));
    }

    private static boolean isProposalRing(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(ModItems.PROPOSAL_RING.get());
    }

    private static boolean isRingUsed(ItemStack stack) {
        return stack != null
                && stack.getTag() != null
                && stack.getTag().getBoolean(TAG_RING_USED);
    }

    /**
     * 写入关系阶段变量。
     *
     * <p>这些变量后面既可以给剧情条件用，也可以给 UI 做阶段标签、阶段特效之类的扩展。
     * 先在这里统一写入，后面就不会又散在 Screen 和业务代码里各写一份。
     */
    private static void writeFavorStageVariables(HugDialogueRuntimeBridge dialogueRuntime,
                                                 RelationStage relationStage) {
        RelationStage resolvedStage = relationStage == null
                ? RelationStage.INITIAL
                : relationStage;
        /**
         * 同时写两类变量：
         * 1. `favor_stage=dating` 这种单值变量，适合做模板替换和日志观察；
         * 2. `favor_stage_dating=true` 这种布尔变量，适合直接写进条件表达式。
         *
         * 两套都给，是为了让剧情 JSON 更好写，不用每次都手搓字符串比较。
         */
        dialogueRuntime.setVariable("favor_stage", resolvedStage.key());
        for (RelationStage stage : RelationStage.values()) {
            dialogueRuntime.setVariable("favor_stage_" + stage.key(), Boolean.toString(stage == resolvedStage));
        }
    }

    private static void writeMoodVariables(HugDialogueRuntimeBridge dialogueRuntime, MaidMoodData.MoodState mood) {
        MaidMoodData.MoodState resolvedMood = mood == null ? MaidMoodData.MoodState.NORMAL : mood;
        dialogueRuntime.setVariable("mood", resolvedMood.key());
        dialogueRuntime.setVariable("mood_idle_text", idleTextForMood(resolvedMood));
        dialogueRuntime.setVariable("entry_expression", entryExpressionForMood(resolvedMood));
        for (MaidMoodData.MoodState state : MaidMoodData.MoodState.values()) {
            dialogueRuntime.setVariable("mood_" + state.key(), Boolean.toString(state == resolvedMood));
        }

        boolean positiveMood = resolvedMood == MaidMoodData.MoodState.HAPPY
                || resolvedMood == MaidMoodData.MoodState.LOVE;
        boolean negativeMood = resolvedMood == MaidMoodData.MoodState.DEPRESSED
                || resolvedMood == MaidMoodData.MoodState.GENERAL;
        dialogueRuntime.setVariable("mood_positive", Boolean.toString(positiveMood));
        dialogueRuntime.setVariable("mood_negative", Boolean.toString(negativeMood));
    }

    private static void writeV4PoolVariables(HugDialogueRuntimeBridge dialogueRuntime,
                                             RelationStage relationStage,
                                             MaidMoodData.MoodState moodState,
                                             String poolAnchor,
                                             String timeOfDay,
                                             boolean longingForInteraction) {
        String anchor = safeValue(poolAnchor)
                + "|"
                + (relationStage == null ? "" : relationStage.key())
                + "|"
                + (moodState == null ? "" : moodState.key())
                + "|"
                + safeValue(timeOfDay)
                + "|"
                + longingForInteraction;
        if (anchor.equals(dialogueRuntime.renderTemplate("${v4_pool_anchor}"))
                && !dialogueRuntime.renderTemplate("${entry_text}").isBlank()) {
            return;
        }
        dialogueRuntime.setVariable("v4_pool_anchor", anchor);
        String entryText = longingForInteraction
                ? HugDialogueTextPools.pickLongingEntry(relationStage, moodState)
                : HugDialogueTextPools.pickMixedEntry(relationStage, moodState, timeOfDay);
        dialogueRuntime.setVariable("entry_text", entryText);
        dialogueRuntime.setVariable("chat_text", HugDialogueTextPools.pickChat(relationStage, moodState));
        dialogueRuntime.setVariable("pet_intro_text", HugDialogueTextPools.pickPet(relationStage));
        dialogueRuntime.setVariable("hug_intro_text", HugDialogueTextPools.pickHug(relationStage));
        dialogueRuntime.setVariable("kiss_intro_text", HugDialogueTextPools.pickKiss(relationStage));
        dialogueRuntime.setVariable("release_hug_text", HugDialogueTextPools.pickReleaseHug());
        dialogueRuntime.setVariable("low_comfort_text", HugDialogueTextPools.pickLowComfort());
        dialogueRuntime.setVariable("flatter_praise_text", HugDialogueTextPools.pickFlatterPraise());
        dialogueRuntime.setVariable("flatter_gift_text", HugDialogueTextPools.pickFlatterGift());
        dialogueRuntime.setVariable("communication_crank_hard_text", HugDialogueTextPools.pickCommunicationCrankHard(relationStage));
        dialogueRuntime.setVariable("communication_crank_soft_text", HugDialogueTextPools.pickCommunicationCrankSoft(relationStage));
        dialogueRuntime.setVariable("chat_topic_life_text", HugDialogueTextPools.pickChatTopic("life", relationStage));
        dialogueRuntime.setVariable("chat_topic_heart_text", HugDialogueTextPools.pickChatTopic("heart", relationStage));
        dialogueRuntime.setVariable("chat_topic_rest_text", HugDialogueTextPools.pickChatTopic("rest", relationStage));
        dialogueRuntime.setVariable("chat_topic_time_text", HugDialogueTextPools.pickTimeTopic(relationStage, timeOfDay));
        dialogueRuntime.setVariable("chat_topic_depend_text", HugDialogueTextPools.pickChatTopic("depend", relationStage));
        dialogueRuntime.setVariable("chat_topic_future_text", HugDialogueTextPools.pickChatTopic("future", relationStage));
        String weatherSpecialCategory = resolveWeatherSpecialCategory(Minecraft.getInstance());
        dialogueRuntime.setVariable("weather_special_category", weatherSpecialCategory);
        dialogueRuntime.setVariable("weather_special_topic", Boolean.toString(!weatherSpecialCategory.isBlank()));
        dialogueRuntime.setVariable("chat_topic_weather_special_text",
                HugDialogueTextPools.pickWeatherSpecialTopic(weatherSpecialCategory, relationStage));
    }

    private static String idleTextForMood(MaidMoodData.MoodState mood) {
        return switch (mood) {
            case DEPRESSED -> "今天有点提不起精神……如果你愿意的话，先让我在你身边待一会儿。";
            case GENERAL -> "今天的心情只能说一般般……不过看到你之后，好像没有那么糟了。";
            case NORMAL -> "${player}的肩膀……比我想的还要宽一点呢。这样靠着的时候，会让人很安心。";
            case HAPPY -> "嘿嘿，今天一看到你就觉得心情很好。要不要多陪我一会儿？";
            case LOVE -> "一看到你，心里就开始冒粉红泡泡了……今天要对我负责到底哦。";
        };
    }

    private static String entryExpressionForMood(MaidMoodData.MoodState mood) {
        return switch (mood) {
            case DEPRESSED -> "sad";
            case GENERAL -> "troubled";
            case NORMAL -> "soft_smile";
            case HAPPY -> "hot_smile";
            case LOVE -> "action_touched";
        };
    }

    private static void writeWorldVariables(HugDialogueRuntimeBridge dialogueRuntime, String timeOfDay, String weather) {
        dialogueRuntime.setVariable("time_of_day", timeOfDay);
        dialogueRuntime.setVariable("time_morning", Boolean.toString("morning".equals(timeOfDay)));
        dialogueRuntime.setVariable("time_noon", Boolean.toString("noon".equals(timeOfDay)));
        dialogueRuntime.setVariable("time_afternoon", Boolean.toString("afternoon".equals(timeOfDay)));
        dialogueRuntime.setVariable("time_evening", Boolean.toString("evening".equals(timeOfDay)));
        dialogueRuntime.setVariable("time_night", Boolean.toString("night".equals(timeOfDay)));
        dialogueRuntime.setVariable("time_midnight", Boolean.toString("midnight".equals(timeOfDay)));

        dialogueRuntime.setVariable("weather", weather);
        dialogueRuntime.setVariable("weather_clear", Boolean.toString("clear".equals(weather)));
        dialogueRuntime.setVariable("weather_rain", Boolean.toString("rain".equals(weather)));
        dialogueRuntime.setVariable("weather_snow", Boolean.toString("snow".equals(weather)));
        dialogueRuntime.setVariable("weather_thunder", Boolean.toString("thunder".equals(weather)));
    }

    private static boolean isBloodlineKissBlocked(Minecraft minecraft, @Nullable Entity targetEntity) {
        if (minecraft == null || minecraft.player == null || !(targetEntity instanceof EntityMaid maid)) {
            return false;
        }
        return MaidChildEntity.isParentOfMaid(maid, minecraft.player.getUUID());
    }

    private static boolean hasOtherLoadedMarriageOnClient(@Nullable Player player, EntityMaid currentMaid) {
        if (player == null || currentMaid == null || ModConfigs.haremMode()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ModTaskData.MARRIAGE_DATA == null) {
            return false;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof EntityMaid maid) || maid == currentMaid || !maid.isAlive()) {
                continue;
            }
            MarriageData data = maid.getOrCreateData(ModTaskData.MARRIAGE_DATA, MarriageData.EMPTY);
            if (data != null && data.isMarriedWith(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    private static String resolveTimeOfDay(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return "day";
        }
        long dayTime = minecraft.level.getDayTime() % 24000L;
        if (dayTime < 5000L) {
            return "morning";
        }
        if (dayTime < 8000L) {
            return "noon";
        }
        if (dayTime < 12000L) {
            return "afternoon";
        }
        if (dayTime < 14000L) {
            return "evening";
        }
        if (dayTime < 22000L) {
            return "night";
        }
        return "midnight";
    }

    private static String resolveWeather(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return "clear";
        }
        if (minecraft.level.isThundering()) {
            return "thunder";
        }
        if (minecraft.level.isRaining()) {
            if (minecraft.player != null
                    && minecraft.level.getBiome(minecraft.player.blockPosition()).value().coldEnoughToSnow(minecraft.player.blockPosition())) {
                return "snow";
            }
            return "rain";
        }
        return "clear";
    }

    private static String resolveWeatherSpecialCategory(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return "";
        }

        if (minecraft.level.dimension() == net.minecraft.world.level.Level.NETHER) {
            return "nether";
        }
        if (minecraft.level.dimension() == net.minecraft.world.level.Level.END) {
            return "end";
        }

        BlockPos pos = minecraft.player.blockPosition();
        var biomeKey = minecraft.level.getBiome(pos).unwrapKey();
        String path = biomeKey.map(key -> key.location().getPath()).orElse("");
        if (path.isBlank()) {
            return "";
        }
        if (path.contains("deep_dark")) {
            return "deep_dark";
        }
        if (path.contains("lush_caves")) {
            return "lush_caves";
        }
        if (path.contains("mushroom")) {
            return "mushroom";
        }
        if (path.contains("cherry_grove") || path.contains("cherry")) {
            return "cherry";
        }
        if (path.contains("desert") || path.contains("badlands")) {
            return "arid";
        }
        if (path.contains("jungle") || path.contains("bamboo")) {
            return "jungle";
        }
        if (path.contains("ocean") || path.contains("beach") || path.contains("river")) {
            return "waterside";
        }
        return "";
    }

    @Nullable
    private static Entity findEntityByUuid(Minecraft minecraft, @Nullable UUID uuid) {
        if (minecraft == null || minecraft.level == null || uuid == null) {
            return null;
        }
        for (Entity candidate : minecraft.level.entitiesForRendering()) {
            if (uuid.equals(candidate.getUUID())) {
                return candidate;
            }
        }
        return null;
    }

    private static String safeValue(String raw) {
        return raw == null ? "" : raw;
    }
}
