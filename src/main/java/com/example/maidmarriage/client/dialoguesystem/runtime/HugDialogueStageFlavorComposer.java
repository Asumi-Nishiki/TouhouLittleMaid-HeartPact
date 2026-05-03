package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.compat.RelationshipThresholds;
import com.example.maidmarriage.compat.RelationStage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 拥抱剧情结果页的“阶段化补句”生成器。
 *
 * <p>用户前面已经把剧情节点从单句扩成了多句，但如果所有好感阶段共用同一套结果页，
 * 玩家还是会很快感觉“虽然话题变多了，关系推进感却不明显”。
 *
 * <p>这层专门解决这个问题：
 * 1. 不去把整份剧情 JSON 复制成四五套，避免台本指数级膨胀；
 * 2. 只在“结果页第一页”追加一句和当前关系阶段绑定的反馈；
 * 3. 原本的主题台词仍然保留，阶段感作为附加层叠上去；
 * 4. 后面继续加新话题时，只要往这里补一条映射即可，不需要重写整棵流程树。
 *
 * <p>目前只对“跨阶段会复用的回答节点”补句：
 * - 低落心情
 * - 雷雨天
 * - 雨天
 * - 早晨
 * - 夜晚
 * - 日常聊天
 *
 * <p>像 {@code chat_stage_warm_*} / {@code chat_stage_close_*} 这种本来就按阶段拆开的节点，
 * 暂时不在这里再额外叠加，避免文案重复得太密。
 */
public final class HugDialogueStageFlavorComposer {
    private static final Map<String, StageFlavorSet> RESULT_STAGE_FLAVORS = createResultStageFlavors();

    private HugDialogueStageFlavorComposer() {
    }

    /**
     * 对当前对话帧做阶段化补句。
     *
     * <p>只处理连续文本节点的第一页，并且只处理已经登记过的“共享结果节点”。
     * 这样不会影响旁白页、选项页，也不会把本来就已经足够长的后续页继续堆胖。
     */
    public static DialogueFrameView apply(DialogueFrameView frame, DialogueRuntimeContext context) {
        if (frame == null || context == null) {
            return frame;
        }
        /**
         * 这里故意只处理“连续文本页的第一页”。
         *
         * 原因有两个：
         * 1. 选项页本身只负责展示可点内容，不应该再偷偷改文案；
         * 2. 如果把每一页都继续叠加阶段补句，会让一段三句话的结果页膨胀成非常啰嗦的五六句。
         *
         * 所以当前策略是：
         * - 第一句负责把“关系阶段感”打出来；
         * - 后两句仍然保持原本这个话题自己的节奏。
         */
        if (frame.choiceNode() || frame.ended() || frame.lineIndex() != 0) {
            return frame;
        }

        String nodeId = safe(frame.nodeId());
        /**
         * nodeId 就是当前正在显示的剧情节点名。
         *
         * 我们这里不按“当前好感度数值 + 当前天气 + 当前时间”去直接拼文本，
         * 而是先看这个结果页有没有登记为“需要阶段化增强的共享节点”。
         *
         * 好处是：
         * - 主题仍然由 JSON 台本控制；
         * - 阶段差异由 Java 做一个很薄的补层；
         * - 不会把原有剧情结构打散。
         */
        StageFlavorSet flavorSet = RESULT_STAGE_FLAVORS.get(nodeId);
        if (flavorSet == null) {
            return frame;
        }

        /**
         * favor_stage 变量由 HugDialogueContextVariables 在每次刷新上下文时写入。
         *
         * 这样这里就不用自己重复读好感度数值、重复算阈值，
         * 整个“阶段判定”逻辑始终只有一份真相源。
         */
        RelationStage relationStage = RelationStage.fromKey(context.getVariable("favor_stage"));
        String extraLine = flavorSet.resolve(relationStage);
        if (extraLine.isBlank()) {
            return frame;
        }

        String mergedText = mergeText(frame.text(), extraLine);
        return new DialogueFrameView(
                frame.scenarioId(),
                frame.nodeId(),
                frame.lineIndex(),
                frame.speaker(),
                mergedText,
                frame.portraitId(),
                frame.portraitTexture(),
                frame.expressionId(),
                frame.animationId(),
                frame.choiceNode(),
                frame.ended(),
                frame.choices()
        );
    }

    /**
     * 根据好感度数值解析关系阶段。
     *
     * <p>这个阶段划分同时服务于：
     * - 剧情条件变量；
     * - 结果页补句；
     * - 后续可能扩展的阶段 UI 提示。
     */
    public static RelationStage resolveStage(int favorability) {
        /**
         * 阶段边界直接和当前玩法设计对齐：
         * - 32：能摸头，算开始变暖；
         * - 64：能拥抱，算明显亲近；
         * - 128：能亲吻，算正式交往；
         * - 192：能结婚，算婚后阶段。
         *
         * 这里不要用 0/1/2/3 这种裸数字枚举，
         * 否则后面你回来看剧情条件或日志时会很难一眼看懂。
         */
        if (favorability >= RelationshipThresholds.MARRIAGE_UNLOCK) {
            return RelationStage.MARRIAGE;
        }
        if (favorability >= RelationshipThresholds.DATING_UNLOCK) {
            return RelationStage.DATING;
        }
        if (favorability >= RelationshipThresholds.HUG_UNLOCK) {
            return RelationStage.CLOSE;
        }
        if (favorability >= RelationshipThresholds.PET_UNLOCK) {
            return RelationStage.WARM;
        }
        return RelationStage.INITIAL;
    }

    private static String mergeText(String originalText, String extraLine) {
        String base = safe(originalText);
        String extra = safe(extraLine).trim();
        if (extra.isEmpty()) {
            return base;
        }
        if (base.isBlank()) {
            return extra;
        }
        return base + "\n" + extra;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, StageFlavorSet> createResultStageFlavors() {
        Map<String, StageFlavorSet> map = new LinkedHashMap<>();

        /**
         * 下面这一批是“低落时的共享回答”。
         *
         * 这类节点不区分天气/时间，而是专门吃 mood_negative 分支。
         * 阶段变化重点不是“更甜”，而是从克制接受、到愿意示弱、再到主动依赖。
         */
        map.put("chat_low_quiet_result", new StageFlavorSet(
                "谢谢你没有追问。这样就很好，我会自己慢慢调整的。",
                "你不说话也陪着我……我有点不知道该怎么谢你。",
                "如果是你的话，我好像可以稍微把软弱露出来一点。",
                "被喜欢的人这样等着，我会忍不住想再靠近你一点。",
                "只要你在旁边，我就知道自己不用一个人撑过去。"
        ));
        map.put("chat_low_comfort_result", new StageFlavorSet(
                "嗯，我知道了。虽然还没有完全好起来，但我会记住你的话。",
                "你这样认真安慰我，我反而有点不知道该看哪里了。",
                "明明想说自己没事，可你一开口，我就不太想逞强了。",
                "恋人这样哄人……太犯规了，我会真的想撒娇的。",
                "好啦，我听你的。今天就让我被你照顾一下。"
        ));
        map.put("chat_low_joke_result", new StageFlavorSet(
                "这个笑话……严格来说不太合格，不过你的心意我收到了。",
                "噗。不是因为好笑，是因为你讲得太认真了。",
                "你每次笨拙地逗我，我都会觉得……算了，没什么。",
                "恋人特供的冷笑话吗？那我只好给你一点面子笑一下了。",
                "都这么熟了还用这种方式哄我，真是拿你没办法。"
        ));

        /**
         * 这批是天气 / 时段话题。
         *
         * 这些节点本来的主题已经很明确：
         * - 雷雨：安全感
         * - 雨天：照顾感
         * - 早晨：开启一天
         * - 夜晚：收束一天
         *
         * 阶段变化按“正经克制 -> 有点害羞 -> 明确喜欢 -> 长期习惯”推进。
         */
        map.put("chat_thunder_stay_result", new StageFlavorSet(
                "那就麻烦你待一会儿。我不是害怕，只是……声音有点突然。",
                "你在旁边的话，我好像能冷静一点。只是有一点点而已。",
                "如果你愿意离我近一点，我应该就不会那么在意雷声了。",
                "你说会陪着我，我就真的想躲到你身边去了。",
                "有你在，雷声就只是窗外的声音，不会再撞到心里。"
        ));
        map.put("chat_thunder_listen_result", new StageFlavorSet(
                "一起听的话……也许能分散一点注意力。",
                "原来有人陪着听雨声，会是这种感觉。",
                "你靠近之后，雷声好像真的退远了一点。",
                "坏天气和你放在一起，居然也会变得有点浪漫。",
                "以后雷雨天也这样吧。我想和你一起把声音听完。"
        ));
        map.put("chat_thunder_tease_result", new StageFlavorSet(
                "请不要在这种时候开玩笑……不过，我知道你是想让我放松。",
                "你这么一打岔，我都不知道该继续紧张还是该笑了。",
                "你是不是故意的？明明知道我会被你逗得分心。",
                "这种时候还逗我，坏心眼。不过……我不讨厌。",
                "都这样久了，你还是喜欢看我被逗到乱掉的样子。"
        ));

        map.put("chat_rain_warm_result", new StageFlavorSet(
                "谢谢。我会小心拿着的，别担心。",
                "你连这种小事都会注意到啊……有点意外。",
                "手心暖起来之后，连心情也跟着松了一点。",
                "被你递来热饮的时候，我会觉得自己被认真放在心上。",
                "还是你最知道我什么时候需要一点温暖。"
        ));
        map.put("chat_rain_memory_result", new StageFlavorSet(
                "如果你不嫌无聊，我可以说一点以前的事。",
                "把回忆讲给你听，好像没有我想的那么难为情。",
                "我以前不太会主动说这些，现在却想让你多知道一点。",
                "以后再下雨，我大概会先想起今天和你说过的话。",
                "旧回忆当然重要，但我更想和你一起留下新的。"
        ));
        map.put("chat_rain_work_result", new StageFlavorSet(
                "我知道了。今天会注意，不会故意淋雨的。",
                "被你这样提醒，总觉得自己好像被照顾了。",
                "如果我听话一点，你会不会再夸我一次？",
                "恋人的叮嘱果然不一样，我会乖乖记住的。",
                "好啦，家里最会操心的那位都这么说了，我听你的。"
        ));
        map.put("chat_clear_walk_result", new StageFlavorSet(
                "天气好的时候，心情也会跟着清爽一点。",
                "像这样站一会儿，整个人都会轻松很多。",
                "你陪着的话，连晒太阳都会变成一件让人害羞的事。",
                "明明只是很普通的晴天，可和你一起就会显得特别好。",
                "以后碰上这种天气，也继续陪我一起把太阳晒完吧。"
        ));
        map.put("chat_clear_view_result", new StageFlavorSet(
                "偶尔这样看看天色，也挺安静的。",
                "原来有人一起看天气，感觉真的会不一样。",
                "你在旁边的时候，这种亮堂堂的天气会让人更想靠近一点。",
                "我现在很喜欢这种——抬头能看见晴天，转头能看见你的时候。",
                "晴天当然很好，但更好的是你也正好在我身边。"
        ));
        map.put("chat_clear_outing_result", new StageFlavorSet(
                "如果您想出门的话，我会跟上的。",
                "这种天气确实很适合慢慢走一会儿。",
                "和你一起出去的话，我会有一点点期待。真的只有一点点。",
                "要是今天的好天气能和你一起用掉，我大概会开心很久。",
                "别浪费这么好的天了，走吧，我们一起把今天过得亮一点。"
        ));
        map.put("chat_snow_warm_result", new StageFlavorSet(
                "我会注意的，不会让自己冻着。",
                "冬天被人认真叮嘱的时候，会觉得心里暖一点。",
                "你这样管着我，{maid}会有点高兴……也有点不好意思。",
                "被你放在心上之后，连雪天都没有以前那么冷了。",
                "有你盯着我添衣服，冬天反而会变得让人安心。"
        ));
        map.put("chat_snow_view_result", new StageFlavorSet(
                "雪确实很安静，看久了会让人不自觉放轻声音。",
                "原来和人一起看雪的时候，安静也会显得很柔和。",
                "你站在旁边的话，{maid}会觉得这场雪漂亮得有点过分。",
                "以后再看雪，我大概都会先想起你今天陪着我的样子。",
                "雪当然好看，可我现在更喜欢和你一起看雪。"
        ));
        map.put("chat_snow_home_result", new StageFlavorSet(
                "这种天气待在屋里，确实会让人放松一点。",
                "外面越冷，屋里这种安静就越显得难得。",
                "雪天会让人更想把时间留给重要的人……你别装听不懂。",
                "要是整个冬天都能这样和你待着，我大概会很贪心。",
                "那今天就谁也别急着走了，雪下它的，我们过我们的。"
        ));

        map.put("chat_morning_greet_result", new StageFlavorSet(
                "早安。今天也请多关照，我会认真完成该做的事。",
                "你这么认真说早安，我反而有点不好意思了。",
                "早上第一个听见你的声音，感觉今天会顺利一点。",
                "恋人之间的早安……原来真的会让人心跳变快。",
                "早安。今天也请多喜欢我一点，可以吗？"
        ));
        map.put("chat_morning_plan_result", new StageFlavorSet(
                "今天的计划我会整理好，不会耽误你的安排。",
                "你想知道我的计划吗？那我可以慢慢说给你听。",
                "把今天要做的事告诉你，会有种被你陪着的感觉。",
                "连这种小事都想和恋人说，我是不是有点黏人？",
                "今天的计划里当然有你，这还需要问吗？"
        ));
        map.put("chat_morning_busy_result", new StageFlavorSet(
                "嗯，我会注意休息。你也不要太勉强自己。",
                "你提醒我的样子很认真，所以我会认真听。",
                "那我今天会乖一点，忙完就回来找你。",
                "被恋人这样叮嘱，会让我想把自己照顾得更好。",
                "放心吧，我还要留着精神陪你到晚上呢。"
        ));

        map.put("chat_night_rest_result", new StageFlavorSet(
                "我知道了。再整理一下，我就去休息。",
                "你这样劝我，我会觉得……有人在等我好好睡觉。",
                "如果你再陪我一小会儿，我就会乖乖去睡。",
                "被恋人温柔催睡，连晚安都变得有点甜。",
                "有你催我睡觉的时候，夜晚就不会空落落的。"
        ));
        map.put("chat_night_company_result", new StageFlavorSet(
                "那就麻烦你一会儿。我不会说太多话的。",
                "你愿意留下的话，今晚好像就没那么安静了。",
                "你每次说陪我，我都会忍不住想再依赖你一点。",
                "恋人在夜里说会陪着我，这种话真的很狡猾。",
                "其实有你在旁边，我才最容易安心睡着。"
        ));
        map.put("chat_night_tease_result", new StageFlavorSet(
                "这种话晚上说出来，影响会比白天更大，请注意一点。",
                "你、你怎么偏偏挑这种时候说这种话……",
                "明知道我晚上更容易害羞，还故意这样看着我。",
                "恋人的深夜直球太危险了，我会真的睡不着的。",
                "都这个时间了还撩我，你明天要负责叫我起床。"
        ));

        /**
         * 这批是没有被前面特殊分支截走时，最常进入的日常聊天。
         *
         * 也是玩家最容易反复点到、最容易觉得重复的部分。
         * 文案要尽量像“关系慢慢变近的人”，而不是像系统在播报亲密度。
         */
        map.put("chat_daily_praise_result", new StageFlavorSet(
                "谢谢夸奖。我会把它当成鼓励，继续努力。",
                "突然这么说……我还没准备好该怎么回答。",
                "你每次夸我可爱，我都会想把表情藏起来。",
                "被恋人这样夸，真的会让人很难保持冷静。",
                "你明明知道我喜欢听，还故意说得这么认真。"
        ));
        map.put("chat_daily_work_result", new StageFlavorSet(
                "还在可以处理的范围内。谢谢你关心。",
                "你问我累不累的时候，我会觉得今天的努力被看见了。",
                "如果说有点累，你会让我靠一下吗？",
                "被恋人惦记着的时候，辛苦也会变得不那么委屈。",
                "今天有点累，所以现在可以把我借给你哄一下吗？"
        ));
        map.put("chat_daily_quiet_result", new StageFlavorSet(
                "如果只是安静待一会儿，我没有意见。",
                "和你一起发呆，好像比我想的更放松。",
                "我们已经可以不说话也不尴尬了，这让我有点开心。",
                "恋人之间的沉默，原来也可以这么安心。",
                "这种什么都不用做的时间，我想和你慢慢过很久。"
        ));
        map.put("chat_daily_tease_result", new StageFlavorSet(
                "请不要突然这样说，我会判断不出你是不是认真的。",
                "你一逗我，我就会不知道该生气还是该笑。",
                "你是不是很喜欢看我慌张的样子？真是坏心眼。",
                "恋人故意逗人是犯规的，尤其是你还笑得那么开心。",
                "又来这招。可恶……我明明知道，还是会心动。"
        ));

        return Map.copyOf(map);
    }

    /**
     * 单个结果节点对应的一组阶段补句。
     */
    private record StageFlavorSet(
            String initial,
            String warm,
            String close,
            String dating,
            String marriage
    ) {
        /**
         * 根据关系阶段取对应补句。
         *
         * 这里不做复杂回退逻辑，是因为我们希望每个阶段的语气都尽量明确。
         * 如果后面你发现某个节点某个阶段还没想好文案，
         * 最好显式补上，而不是偷偷沿用别的阶段。
         */
        private String resolve(RelationStage stage) {
            return switch (stage) {
                case WARM -> warm;
                case CLOSE -> close;
                case DATING -> dating;
                case MARRIAGE -> marriage;
                case INITIAL -> initial;
            };
        }
    }
}
