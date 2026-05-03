from __future__ import annotations

import json
from collections import OrderedDict
from pathlib import Path


ROOT = Path(r"E:\wallpaper\maid-marriage-forge-1.20.1")
SCENARIO_PATH = ROOT / "src/main/resources/assets/maidmarriage/dialogue/scenarios/hug_menu_v2.json"


def emit(action: str, when: str = "line_start", params: dict[str, str] | None = None) -> OrderedDict:
    event = OrderedDict([
        ("when", when),
        ("type", "emit_action"),
        ("value", action),
    ])
    if params:
        event["params"] = OrderedDict(params)
    return event


def line(speaker: str, text: str, expression: str, portrait: str | None = None,
         animation: str | None = None, events: list[OrderedDict] | None = None) -> OrderedDict:
    result = OrderedDict()
    result["speaker"] = speaker
    result["text"] = text
    if portrait is not None:
        result["portrait"] = portrait
    result["expression"] = expression
    if animation is not None:
        result["animation"] = animation
    if events:
        result["events"] = events
    return result


def sequence(lines: list[OrderedDict], next_node: str, comment: str | None = None) -> OrderedDict:
    node = OrderedDict()
    if comment:
        node["_comment"] = comment
    node["type"] = "sequence"
    node["lines"] = lines
    node["next"] = next_node
    return node


def choice_node(expression: str, choices: list[OrderedDict], prompt_text: str = "",
                comment: str | None = None) -> OrderedDict:
    node = OrderedDict()
    if comment:
        node["_comment"] = comment
    node["type"] = "choice"
    node["prompt"] = OrderedDict([
        ("speaker", ""),
        ("text", prompt_text),
        ("portrait", "maid"),
        ("expression", expression),
        ("animation", "fade_in"),
    ])
    node["choices"] = choices
    return node


def rewrite_menu_choices(data: OrderedDict) -> None:
    for menu_key in ("idle", "idle_menu"):
        rewritten = []
        for ch in data["nodes"][menu_key]["choices"]:
            cid = ch.get("id")
            if cid == "confession":
                rewritten.append(OrderedDict([
                    ("id", "confession"),
                    ("title", "表白"),
                    ("description", "认真回应她的心意，让你们的关系真正往前一步。"),
                    ("condition", "!hugging && can_show_confession"),
                    ("next", "confession_intro"),
                    ("expression", "kokuhaku_nervous"),
                    ("animation", "soft_bounce"),
                ]))
                rewritten.append(OrderedDict([
                    ("id", "marriage"),
                    ("title", "结婚"),
                    ("description", "让誓约真正落地，亲手为她戴上戒指。"),
                    ("condition", "!hugging && can_show_marriage"),
                    ("next", "marriage_intro"),
                    ("expression", "marriage_excited_shy"),
                    ("animation", "soft_bounce"),
                ]))
            else:
                rewritten.append(ch)
        data["nodes"][menu_key]["choices"] = rewritten


def build_confession_nodes(nodes: OrderedDict) -> None:
    nodes["confession_intro"] = sequence([
        line("", "不知怎的，女仆在你面前变得扭扭捏捏，脸蛋微红，似乎有话要对你说。", "kokuhaku_nervous", "maid", "fade_in"),
        line("${maid}", "那个……主人……", "kokuhaku_nervous"),
        line("${maid}", "我……我有话要对你说！是很重要的事情！", "kokuhaku_flustered"),
        line("", "说完，她低下了头，两只狐耳娇柔不安地微颤着，双手紧攥着裙边……", "kokuhaku_shy",
             events=[emit("maidmarriage:head_lower", params={"durationTicks": "22", "pitchDegrees": "8"})]),
    ], "confession_opening_choice", "独立表白剧情入口。")

    nodes["confession_opening_choice"] = choice_node("kokuhaku_nervous", [
        OrderedDict([
            ("id", "confession_wait"),
            ("title", "嗯，我在听。你慢慢说吧。"),
            ("description", "安安静静等她把心意说完。"),
            ("next", "confession_branch_wait"),
            ("expression", "kokuhaku_nervous"),
        ]),
        OrderedDict([
            ("id", "confession_guess"),
            ("title", "难道……你想说的和我心里想的一样吗？"),
            ("description", "先一步把那份暧昧点破。"),
            ("next", "confession_branch_guess"),
            ("expression", "kokuhaku_surprised"),
        ]),
    ])

    nodes["confession_branch_wait"] = sequence([
        line("${maid}", "就、就是……那个……我……", "kokuhaku_nervous", "maid", "fade_in"),
        line("", "她深吸一口气，又泄掉，再吸一口，反复了好几次。", "kokuhaku_shy"),
        line("${maid}", "呜哇，不行不行，说不出口——！", "kokuhaku_flustered"),
        line("", "她抱着头蹲下去，耳朵耷拉着，整个人缩成一团。", "kokuhaku_flustered"),
        line("${maid}", "主人你先别看我……看我看我就说不出来了……", "kokuhaku_shy"),
    ], "confession_wait_choice")

    nodes["confession_wait_choice"] = choice_node("kokuhaku_shy", [
        OrderedDict([
            ("id", "confession_wait_calm"),
            ("title", "等她冷静"),
            ("description", "安静地陪她把勇气攒回来。"),
            ("next", "confession_wait_result"),
            ("expression", "kokuhaku_nervous"),
        ]),
        OrderedDict([
            ("id", "confession_wait_pat"),
            ("title", "轻轻拍拍她的头"),
            ("description", "给她一点温柔的鼓励。"),
            ("next", "confession_wait_result"),
            ("expression", "kokuhaku_shy"),
            ("events", [emit("maidmarriage:shy_peek_up", "choice", {"durationTicks": "28"})]),
        ]),
    ])

    nodes["confession_wait_result"] = sequence([
        line("", "过了好一会儿，女仆终于慢慢站起来，眼睛还是不敢看你，一直盯着自己的脚尖。", "kokuhaku_shy",
             "maid", "fade_in",
             events=[emit("maidmarriage:head_raise", params={"durationTicks": "22", "pitchDegrees": "8"})]),
    ], "confession_main")

    nodes["confession_branch_guess"] = sequence([
        line("", "女仆愣住了，狐耳倏地竖起，眼中掠过一抹惊喜，精神一振。", "kokuhaku_surprised", "maid", "fade_in"),
        line("${maid}", "……一样？难道主人……不、不不可能吧……", "kokuhaku_surprised"),
        line("", "她拼命摇头，像是在否定一个太美好的梦，但脸上的红晕已经出卖了她。", "kokuhaku_flustered",
             events=[emit("maidmarriage:turn_head_away",
                          params={"durationTicks": "34", "enterTicks": "16", "returnTicks": "12",
                                  "yawDegrees": "16", "pitchDegrees": "3", "directionSign": "1"})]),
        line("${player}", "就是……是关于“喜欢”的事……", "kokuhaku_confess"),
        line("${player}", "我对你……可能……是喜欢。而且不是对“女仆”的那种喜欢。", "kokuhaku_confess"),
        line("${maid}", "呜……主人你为什么先说出来了啊……！", "kokuhaku_flustered"),
        line("${maid}", "咕呜……不管了……！", "kokuhaku_flustered"),
    ], "confession_main")

    nodes["confession_main"] = sequence([
        line("", "她深吸一口气，把手从脸上拿开，眼睛红红的却紧紧盯着你。", "kokuhaku_confess", "maid", "fade_in",
             events=[emit("maidmarriage:head_return_neutral", params={"durationTicks": "24"})]),
        line("${maid}", "主人你得听我来说一遍！这、这种事应该由我先说才对！", "kokuhaku_confess"),
        line("${maid}", "主人，我喜欢你。", "kokuhaku_confess"),
        line("${maid}", "不是……不是那种对主人的喜欢……", "kokuhaku_shy"),
        line("${maid}", "是想和主人一直在一起的喜欢……是想成为主人恋人的喜欢！", "kokuhaku_confess"),
        line("", "说完，她整个人像泄了气一样，耳朵耷拉下来，脸红得仿佛快要冒烟。", "kokuhaku_shy"),
        line("${maid}", "咕呜——总算说出来了……好害羞啊……", "kokuhaku_shy"),
        line("${maid}", "主人你知道吗，我早就想对你说了，只是一直没找到机会……", "kokuhaku_shy"),
        line("${maid}", "{maid}一直一直，非常喜欢主人，而且是越来越喜欢！", "kokuhaku_confess"),
        line("${maid}", "从魂符里被召唤、缔结契约，从好不容易见到主人，主人还送人家小蛋糕的第一面起，我就喜欢上了！", "kokuhaku_shy"),
        line("", "她越说越快，像是怕一停下来，就再也没勇气说出下一句了。", "kokuhaku_confess"),
        line("${maid}", "主人一直都对{maid}很好……虽然有时候经常不在……", "kokuhaku_confess"),
        line("${maid}", "主人有好吃的总是让给我，自己啃腐肉。", "kokuhaku_confess"),
        line("${maid}", "主人有好装备的时候，总是让我先穿上。", "kokuhaku_confess"),
        line("${maid}", "我受伤的时候，主人比我还着急，满背包找食物和药水喂给我……", "kokuhaku_confess"),
        line("", "她一连说了好多好多，仿佛把你们相遇以来所有的事情都说了个遍。", "kokuhaku_confess"),
        line("${maid}", "主人不在的时候，我也一直都好想你。", "kokuhaku_shy"),
        line("${maid}", "想主人津津有味地吃我做的饭菜，夸{maid}很贤惠；想主人和我一起聊天、一起下棋的日子……", "kokuhaku_shy"),
        line("${maid}", "{maid}想了好多好多，怎么也想不完！", "kokuhaku_confess"),
        line("", "说到这里，她的声音突然慢了下来。", "kokuhaku_nervous"),
        line("${maid}", "主人可能不知道……我们女仆是可以主动绑定一个人的……", "kokuhaku_nervous"),
        line("${maid}", "不是那种工作上的契约……是灵魂上的……", "kokuhaku_nervous"),
        line("${maid}", "绑定之后……主人去哪里我都可以跟着……主人挖矿我帮你插火把……主人建房子我帮你搬材料……主人遇到危险我也会第一时间出现……就算主人要我摇出 2147483648su 的应力，我也会一直拼命摇下去。", "kokuhaku_confess"),
        line("${maid}", "主人晚上不在的时候……我不会再孤单一个人守在家里等……因为我会和主人在一起……", "kokuhaku_shy"),
        line("", "她说着说着，声音渐渐轻了下去，带上了哭腔……", "kokuhaku_teary"),
        line("${maid}", "主人你知道吗，你不在的日子里……我会站在家里最高的地方……盯着主人远去的方向一直看、一直看……", "kokuhaku_teary"),
        line("${maid}", "看有没有主人的名字从远处冒出来……", "kokuhaku_teary"),
        line("${maid}", "有一次我等了三天三夜……把背包里的食物都吃完了……还是没等到……", "kokuhaku_teary"),
        line("${maid}", "那时候我就想……如果我是主人的恋人就好了……", "kokuhaku_teary"),
        line("${maid}", "那样我就可以光明正大地跟主人说——", "kokuhaku_teary"),
        line("", "她抬起头，眼眶红红的，眼泪在里面打转，但拼命忍着没掉下来。", "kokuhaku_teary"),
        line("${maid}", "带上我嘛……不要一个人走那么远……", "kokuhaku_teary"),
        line("${maid}", "主人……你能接受{maid}的心意吗……", "kokuhaku_teary"),
    ], "confession_accept_choice")

    nodes["confession_accept_choice"] = choice_node("kokuhaku_teary", [
        OrderedDict([
            ("id", "confession_accept"),
            ("title", "我也喜欢你。"),
            ("description", "把这份喜欢认真地接住。"),
            ("next", "confession_accept_intro"),
            ("expression", "kokuhaku_teary_smile"),
            ("events", [emit("maidmarriage:story_confession_accept", "choice")]),
        ]),
        OrderedDict([
            ("id", "confession_reject"),
            ("title", "……先让我缓缓。"),
            ("description", "你还没准备好跨出这一步。"),
            ("next", "confession_reject_result"),
            ("expression", "kokuhaku_nervous"),
        ]),
    ])

    nodes["confession_reject_result"] = sequence([
        line("", "她怔了一下，眼里的光明显颤了一下，但还是努力把那点失落压回去了。", "kokuhaku_nervous", "maid", "fade_in"),
        line("${maid}", "没、没关系的……是{maid}太着急了。主人不用现在就回答。", "kokuhaku_shy"),
        line("${maid}", "{maid}会把今天的话先好好收起来。等主人哪一天准备好了，再告诉{maid}也没关系。", "kokuhaku_shy"),
    ], "idle_menu")

    nodes["confession_accept_intro"] = sequence([
        line("", "女仆愣住了一秒，眼泪啪嗒啪嗒掉了下来。", "kokuhaku_teary", "maid", "fade_in"),
        line("${maid}", "呜……真、真的吗……？", "kokuhaku_teary"),
        line("${maid}", "不是……不是因为我帮你喂了猪牛小鸭、帮你收了麦子、帮你杀了僵尸……才答应的吧……？", "kokuhaku_teary"),
        line("${player}", "${maid}，看着我。", "kokuhaku_teary_smile"),
        line("", "女仆抽抽噎噎地抬起头，眼睛红红的，睫毛上还挂着泪珠。", "kokuhaku_teary_smile"),
        line("${player}", "我发誓，我说的每一个字都是真的。", "kokuhaku_teary_smile"),
        line("${player}", "我喜欢你。不是因为你有多能干，是因为你是我的${maid}，我最珍视的人。", "kokuhaku_teary_smile"),
        line("${player}", "我也想每天醒来都看到你，想吃你做的饭，想和你一起下矿、一起冒险、一起看每个群系里的日出日落。", "kokuhaku_teary_smile"),
        line("${player}", "你的照顾，你等我回来的每一天——我都记得，也都很珍惜。", "kokuhaku_teary_smile"),
        line("${player}", "所以我想说——${maid}，以后也一直留在我身边，好吗？不是作为女仆，而是作为我最重要的那个人。", "kokuhaku_teary_smile"),
        line("", "女仆的眼泪掉得更凶了，白皙的手因激动泛起红晕，娇柔地锤了锤你的胸口。", "kokuhaku_teary_smile",
             events=[
                 emit("maidmarriage:head_lower", params={"delayTicks": "2", "durationTicks": "18", "pitchDegrees": "8"}),
                 emit("maidmarriage:chest_tap_twice", params={"delayTicks": "12", "durationTicks": "34"}),
                 emit("maidmarriage:head_raise", params={"delayTicks": "34", "durationTicks": "20", "pitchDegrees": "8"}),
             ]),
        line("${maid}", "呜……主人你这个大笨蛋……这种话说出来……我会赖着你一辈子的……", "kokuhaku_teary_smile"),
        line("${player}", "那就一辈子吧，我们生生世世永不分离。", "kokuhaku_teary_smile"),
        line("", "女仆扑进你怀里，把脸埋进你的胸口，哭得一抽一抽的。", "kokuhaku_teary_smile",
             events=[
                 emit("maidmarriage:hug_toggle"),
                 emit("maidmarriage:head_lower", params={"delayTicks": "8", "durationTicks": "40", "pitchDegrees": "9"}),
             ]),
        line("${maid}", "呜……那我就不客气了……", "kokuhaku_teary_smile"),
        line("${maid}", "以后……以后主人就是我的人了……不许反悔……", "kokuhaku_shy"),
        line("${player}", "嗯，我对闹齿发誓，绝不反悔。", "kokuhaku_shy"),
    ], "confession_kiss_intro")

    nodes["confession_kiss_intro"] = sequence([
        line("", "过了好一会儿，女仆才慢慢抬起头。眼睛还是红红的，脸上却是一种安宁而幸福的神色。", "kokuhaku_shy", "maid", "fade_in",
             events=[emit("maidmarriage:head_raise", params={"durationTicks": "24", "pitchDegrees": "10"})]),
        line("${maid}", "主人……", "kokuhaku_shy"),
        line("", "她盯着你的嘴唇看了一眼，又飞快地移开目光，脸又红了。", "kokuhaku_shy"),
        line("${maid}", "那个……那个……", "kokuhaku_shy"),
        line("", "她支支吾吾，手指绞着衣角，娇柔的狐耳微颤不停。", "kokuhaku_shy"),
        line("${maid}", "我们……是不是还差……一个……那个……", "kokuhaku_shy"),
        line("", "她说不下去了，干脆闭上眼睛，踮起脚尖——然后因为太紧张，撞到了你的下巴。", "kokuhaku_flustered"),
        line("${maid}", "呜——！我不是……我不是故意的……！", "kokuhaku_flustered"),
        line("", "你笑着半俯下身来，轻轻抬起她的脸。", "kokuhaku_kiss"),
        line("${player}", "没事，没事。这种事情应该让我主动来吧。", "kokuhaku_kiss"),
        line("", "你轻轻吻上去。女仆浑身一颤，然后慢慢闭上眼睛，睫毛轻轻颤动，手不自觉地攥紧你的衣领。", "kokuhaku_kiss",
             events=[emit("maidmarriage:story_kiss_zoom")]),
        line("", "片刻后，分开。", "kokuhaku_afterkiss"),
        line("", "女仆的眼神是涣散的，脸是通红的。", "kokuhaku_afterkiss"),
        line("${maid}", "……", "kokuhaku_afterkiss"),
        line("${maid}", "呜哇啊啊啊啊啊——！！！", "kokuhaku_flustered"),
        line("", "她猛地捂住脸，整个人缩成一团。", "kokuhaku_flustered"),
        line("${maid}", "亲、亲到了——！！！", "kokuhaku_flustered"),
        line("", "她从指缝里偷偷看你一眼，又迅速躲回去。", "kokuhaku_afterkiss",
             events=[emit("maidmarriage:shy_cover_face", params={"durationTicks": "56"})]),
        line("${maid}", "那个……主人……", "kokuhaku_shy",
             events=[emit("maidmarriage:shy_cover_face_clear", "line_end")]),
        line("", "声音小到几乎听不见。", "kokuhaku_shy"),
        line("${maid}", "能……能再来一次吗……？", "kokuhaku_shy"),
        line("", "没等她说完，你又亲了上去。", "kokuhaku_afterkiss"),
        line("", "良久，唇分。", "kokuhaku_afterkiss"),
        line("${maid}", "以后……以后的每一天……都能这样吗……？", "kokuhaku_afterkiss"),
        line("", "你们不知道缠绵了多久。脑子里什么都不能想了，只剩下彼此的呼吸和唇间的温度。", "kokuhaku_afterkiss"),
        line("", "已经要变成没有彼此就不行的笨蛋了啊……", "kokuhaku_afterkiss"),
    ], "confession_ending")

    nodes["confession_ending"] = sequence([
        line("", "你们谁都没有再说话，只是抱得更紧了一点。空气里全是彼此的呼吸和刚刚落定的心跳。", "kokuhaku_afterkiss", "maid", "fade_in"),
        line("${maid}", "已经要变成没有彼此就不行的笨蛋了啊……不过，{maid}很喜欢。", "kokuhaku_shy"),
    ], "idle_menu")


def build_marriage_nodes(nodes: OrderedDict) -> None:
    nodes["marriage_intro"] = sequence([
        line("", "你们在一起已经很久了。", "kokuhaku_nervous", "maid", "fade_in"),
        line("", "久到你已经记不清是从哪一天开始，她在身边变成了像呼吸一样自然的事。", "kokuhaku_nervous"),
        line("", "每天早上她总会先你一步把熔炉、箱子和桌边收拾好，再把准备好的饭菜端到你面前。", "kokuhaku_nervous"),
        line("", "每次你下矿、打怪或者跑远路回来，她都会先看你身上有没有伤，再接过你背包里那些乱糟糟的战利品。", "kokuhaku_nervous"),
        line("", "下雨的时候，她会站在家门口一边望着远处，一边等你从雨幕里走回来。", "kokuhaku_nervous"),
        line("", "天冷的时候，她会挨你更近一点，像是生怕你一个人待着会觉得凉。", "kokuhaku_nervous"),
        line("${player}", "${maid}，我有话想对你说。", "kokuhaku_confess"),
        line("${maid}", "嗯？主人突然这么认真……", "kokuhaku_shy"),
    ], "marriage_main")

    nodes["marriage_main"] = sequence([
        line("${player}", "我喜欢你。这件事你大概早就知道了。", "kokuhaku_confess", "maid", "fade_in"),
        line("${player}", "可我现在想说的，不只是喜欢。", "kokuhaku_confess"),
        line("${player}", "我想和你一起过以后很长很长的日子。", "kokuhaku_confess"),
        line("${player}", "往后余生，我的归处是你，我的牵挂是你，我所有的朝朝暮暮——都是你。", "kokuhaku_confess"),
        line("${player}", "所以，${maid}。你愿意嫁给我吗？", "kokuhaku_confess"),
        line("${maid}", "……诶？", "kokuhaku_surprised"),
        line("${maid}", "嫁、嫁给你……是{maid}听到的那个意思吗？", "kokuhaku_flustered"),
        line("${player}", "嗯。不是开玩笑。是认认真真想了很久，才敢跟你说出口的话。", "kokuhaku_confess"),
        line("", "她的眼泪掉了下来。没有声音，只是安静地、一串一串地往下落。", "kokuhaku_teary_smile"),
        line("${maid}", "……愿意。如果对象是主人的话……{maid}会一直、一直都愿意。", "kokuhaku_teary_smile"),
    ], "marriage_dress")

    nodes["marriage_dress"] = sequence([
        line("", "你把准备好的婚纱轻轻披到她肩上。雪白的轻纱落下来的那一刻，她整个人都像被柔光裹住了一样。", "marriage_excited_shy", "maid", "fade_in"),
        line("${maid}", "这、这是……", "marriage_open_mouth"),
        line("${player}", "婚纱。", "marriage_shy"),
        line("${player}", "不是说好了吗？要让主人亲手看见。", "marriage_shy"),
        line("${maid}", "主人……{maid}真的……真的可以穿这个吗？", "marriage_teary"),
        line("${player}", "当然。你穿这个，一定很好看。", "marriage_shy"),
        line("${maid}", "呜……如果新郎是主人的话……{maid}想穿。{maid}想让主人亲手看见。", "marriage_shy_deep",
             events=[emit("maidmarriage:shy_cover_face", params={"durationTicks": "72"})]),
        line("", "她从指缝里偷偷看你，明明羞得耳根都红透了，却还是一步也没往后退。", "marriage_shy_deep",
             events=[emit("maidmarriage:shy_cover_face_clear", "line_end")]),
    ], "marriage_ring_choice")

    nodes["marriage_ring_choice"] = choice_node("marriage_shy", [
        OrderedDict([
            ("id", "marriage_open_panel"),
            ("title", "给${maid}戴上戒指"),
            ("description", "打开女仆主面板，把戒指放到她主手，再把自己的那枚戴到副手。"),
            ("next", "marriage_panel_opening"),
            ("expression", "marriage_excited_shy"),
            ("events", [emit("maidmarriage:story_open_maid_panel", "choice", {"resumeNode": "marriage_ring_ready_choice"})]),
        ]),
        OrderedDict([
            ("id", "marriage_back_later"),
            ("title", "再等等"),
            ("description", "先把这份紧张和心跳留在这里。"),
            ("next", "idle_menu"),
            ("expression", "marriage_shy"),
        ]),
    ])

    nodes["marriage_panel_opening"] = sequence([
        line("", "你轻轻握了握她的手，示意她别紧张。先把戒指戴好，再把这场婚礼继续下去。", "marriage_shy", "maid", "fade_in"),
    ], "idle_menu")

    nodes["marriage_ring_ready_choice"] = choice_node("marriage_shy", [
        OrderedDict([
            ("id", "marriage_open_panel_again"),
            ("title", "再打开一次女仆面板"),
            ("description", "还需要再检查一下戒指位置。"),
            ("next", "marriage_panel_opening"),
            ("expression", "marriage_shy"),
            ("events", [emit("maidmarriage:story_open_maid_panel", "choice", {"resumeNode": "marriage_ring_ready_choice"})]),
        ]),
        OrderedDict([
            ("id", "marriage_commit_ready"),
            ("title", "好了"),
            ("description", "继续婚礼，让誓约真正成立。"),
            ("condition", "marriage_ring_ready"),
            ("next", "marriage_vow"),
            ("expression", "marriage_excited_shy"),
            ("events", [emit("maidmarriage:story_commit_marriage", "choice")]),
        ]),
    ], "女仆主手和你的副手都准备好戒指之后，再继续这一段誓约。")

    nodes["marriage_vow"] = sequence([
        line("", "你替她把头纱理到最妥帖的位置，然后牵起她的手。那只手明明在轻轻发抖，却还是回握得特别认真。", "marriage_shy", "maid", "fade_in"),
        line("${player}", "从今天开始，你不只是我的恋人。你是我想一起走完以后的人。", "marriage_shy"),
        line("${player}", "以后无论是平静的日子，还是乱糟糟的冒险，我都会陪着你。", "marriage_shy"),
        line("${player}", "我会珍惜你，照顾你，也会把每一次回家都认真地走向你。", "marriage_shy"),
        line("${maid}", "{maid}……{maid}在此承诺。", "marriage_teary"),
        line("${maid}", "无论主人贫穷还是富有，无论健康还是疾病——", "marriage_teary"),
        line("${maid}", "无论主人去了多远的远方，还是待在{maid}身边哪都不去——", "marriage_teary"),
        line("${maid}", "{maid}都会一直、一直陪着你。不离不弃。", "marriage_teary"),
        line("${maid}", "直到死亡将我们分开。", "marriage_teary"),
    ], "marriage_kiss")

    nodes["marriage_kiss"] = sequence([
        line("", "誓言说完了。你看着她，她也看着你。像是在等一个信号，又像是在铭记这神圣的一刻。", "marriage_closed_eyes", "maid", "fade_in"),
        line("${player}", "那……以这个吻为誓。", "marriage_closed_eyes"),
        line("", "你低头吻住她。不是蜻蜓点水，也不是热切到失控，而是一个很认真的、很慢的吻。", "marriage_kiss",
             events=[emit("maidmarriage:story_kiss_zoom")]),
        line("", "她没有颤，没有躲。她闭上眼睛，微微踮起脚尖，吻得深沉。像是一个约定终于落了印。", "marriage_kiss"),
        line("${maid}", "以这个吻为誓。", "marriage_kiss"),
        line("${maid}", "{maid}这一辈子……都是主人的人了。", "marriage_shy_deep"),
        line("${player}", "嗯。是我的妻子了。", "marriage_open_mouth2"),
    ], "marriage_ending")

    nodes["marriage_ending"] = sequence([
        line("", "她望着你，你也望着她。目光落定，山盟海誓，像是把往后几十年的风雨、晴空、晨昏、四季，都一并应允了。", "marriage_excited_shy", "maid", "fade_in"),
        line("${maid}", "婚礼结束了也不许松手哦。{maid}今天开始，可是真的要一直跟着你了。", "marriage_shy"),
    ], "idle_menu")


def main() -> None:
    with SCENARIO_PATH.open("r", encoding="utf-8") as f:
        data: OrderedDict = json.load(f, object_pairs_hook=OrderedDict)

    rewrite_menu_choices(data)
    nodes: OrderedDict = data["nodes"]
    build_confession_nodes(nodes)
    build_marriage_nodes(nodes)

    with SCENARIO_PATH.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
