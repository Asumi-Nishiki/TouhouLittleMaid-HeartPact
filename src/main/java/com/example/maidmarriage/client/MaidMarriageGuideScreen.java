package com.example.maidmarriage.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 游戏内玩家指南。
 *
 * <p>这不是开发调试页，而是正式版给玩家看的流程说明：
 * 从按键配置开始，按“培养关系 -> 表白结婚 -> 怀孕与小女仆 -> 常见问题”的顺序整理。
 */
public class MaidMarriageGuideScreen extends Screen {
    private static final int PANEL_WIDTH = 372;
    private static final int CONTENT_PADDING = 18;
    private static final List<GuideSection> SECTIONS = List.of(
            new GuideSection("先从按键开始",
                    List.of(
                            "打开“选项 -> 控制 -> 心契同眠”，先看三个键：交互按键、摸头/举高高、节奏判定键。按键绑定以 Minecraft 原版控制设置为准，不在本页里改。",
                            "交互按键：默认 Alt + J。对成年女仆打开恋爱互动；对小女仆打开小女仆互动。",
                            "摸头/举高高：默认 Alt + K。对坐下的女仆摸头；对站着的小女仆举高高。已经举起时再按一次会放下。鞘翅滑翔时可以继续带着小女仆飞。",
                            "节奏判定键：默认 J。同眠剧情进入节奏玩法时使用。如果不想玩，可以在节奏游戏设置里开启跳过。",
                            "模组设置里可以调整举高高高度和拥抱距离：举高高高度偏移默认 0.10，可调 -0.20 到 1.50；拥抱锁定距离默认 0.80，可调 0.10 到 2.00。")),
            new GuideSection("好感阶段和解锁",
                    List.of(
                            "0-31：初始。先聊天、送礼、照顾她，亲密选项会比较少。",
                            "32 起：熟络。摸头开始成为稳定互动，也会触发阶段进度。",
                            "64 起：暧昧。拥抱解锁，关系会明显靠近。",
                            "128 起：依恋。进入表白/交往门槛，亲吻与恋人相关剧情开始出现。",
                            "192 起：相守。结婚门槛。准备好戒指后，可以在互动里交换誓约。",
                            "未达到门槛的亲密行为不会显示，不是坏了，是她还没准备好。")),
            new GuideSection("成年女仆互动",
                    List.of(
                            "沟通：普通聊天、天气、生活、心事、休息、未来等话题。不同心情下回答会不一样。",
                            "摸头：轻量亲密互动。她坐下时也可以用快捷键摸头。",
                            "拥抱：64 好感后出现。进入拥抱姿态后，界面右侧会出现亲吻、继续拥抱、先松开等操作。",
                            "亲吻：128 好感后出现。亲吻、过度亲近和冒犯选项都会受关系与心情影响。",
                            "表白：进入交往门槛后出现。接受心意后进入恋人关系，并获得“心心相印”进度。",
                            "结婚：192 好感后出现。需要主手和副手各准备一枚求婚戒指，并在剧情中为她戴上戒指。",
                            "赠送礼物：打开礼物面板，从背包里挑物品送她。每天最多两次。")),
            new GuideSection("亲吻角度校准",
                    List.of(
                            "不同女仆模型的身高、头部骨骼和镜头位置可能不一样。如果拥抱或亲吻时画面对不到脸，可以在互动界面里校准镜头。",
                            "对成年女仆打开互动界面，进入拥抱/亲吻相关界面后，看右下角退出按钮附近的铅笔按钮。点开它会出现镜头校准小面板。",
                            "“缩放”用来调远近：画面太远就拉近，脸贴得太满就拉远一点。鼠标滚轮也可以临时调整缩放。",
                            "“上下”用来调俯仰：女仆脸偏高就往上调，女仆脸偏低就往下调。建议切第一人称，一边看画面一边微调。",
                            "调好后点保存。这个设置只保存在你自己的客户端 config/maidmarriage/hug-camera.json，不会影响服务器，也不会影响其他玩家。")),
            new GuideSection("心情到底影响什么",
                    List.of(
                            "心情有五档：沮丧、一般、普通、开心、狗修金LOVE。数值范围大致是 0-25，普通是 15。",
                            "好感收益倍率：沮丧/一般不吃正向好感，普通 x1，开心 x1.5，LOVE x2。",
                            "聊天选择会按当前心情结算：心情好时正反馈更明显；心情差时，玩笑、冒犯、过度互动会直接扣好感。",
                            "摸头、拥抱、亲吻、送礼、同眠等有效互动会刷新“最近互动”。恋人或妻子如果连续几天没有被好好陪伴，会进入忍耐/想被陪伴的状态。",
                            "小女仆学习会消耗心情。心情低到 0 左右还继续学习，结算会扣好感，奖励品质也会明显变差。探险在太累时会停下。")),
            new GuideSection("礼物怎么送",
                    List.of(
                            "礼物入口在互动界面的“讨好/赠送礼物”。送出成功会消耗物品，每个玩家每天最多送两次。",
                            "花束：主要拉近关系。普通花和五彩花束都算花束，五彩花束可以重复送。",
                            "甜食：曲奇、蛋糕、南瓜派、蜂蜜瓶、甜浆果、苹果、金苹果等，偏向安抚心情。她心情差时更有用。",
                            "正餐：面包、牛奶、熟肉、熟鱼、炖菜、酱板鸭等，偏稳定恢复心情，恋人/婚后可能有少量好感。",
                            "贵重物：钻石、绿宝石、金锭、铁锭、紫水晶、石英、青金石、下界合金碎片等。初期她会犹豫，关系近了更容易接受。",
                            "奇怪/冒犯礼物：腐肉、蜘蛛眼、毒马铃薯、河豚、骨头等可能扣心情或好感。腐肉、发酵蛛眼、河豚、毒马铃薯属于很冒犯的礼物。",
                            "求婚戒指、YES 枕头、发卡、调试工具、婚姻同意申请书不能当普通礼物送。")),
            new GuideSection("物品和配方",
                    List.of(
                            "求婚戒指：钻石 + 铁粒合成。它可以放进女仆饰品栏；结婚时需要两枚，玩家主手和副手各一枚。",
                            "誓约之环：结婚流程中由求婚戒指刻上双方名字生成，本质是刻过的女仆戒指饰品，不是普通合成物。绑定过誓约后不能再用于向别人求婚。",
                            "YES 枕头：白羊毛和红羊毛合成，也会在结婚成功后交给女仆。婚后用于同眠相关流程。",
                            "五彩花束：滨菊、虞美人、矢车菊、粉色郁金香、蒲公英无序合成。适合送礼。",
                            "金蒲公英发卡：时钟 + 金锭 + 蒲公英无序合成。它是女仆饰品，放进小女仆饰品栏后会暂停成长，取下后恢复成长。",
                            "酱板鸭：熟鸡肉被可可豆围一圈合成。喂小女仆可缩短成长时间；喂产后女仆可缩短恢复剩余时间。",
                            "婚姻同意申请书：书 + 求婚戒指无序合成。用于成年子代女仆监护权移交，具体用法见下一节。",
                            "族谱查看器：骨粉 + 书无序合成。手持后右键女仆，聊天框会显示本人、母系、父系、祖辈、配偶和直系子代。",
                            "调试工具：成长、分娩、怀孕测试、怀孕结算、忍耐测试、花卉测试等工具主要用于测试和排查，普通生存流程不需要。")),
            new GuideSection("族谱和婚姻申请书",
                    List.of(
                            "族谱查看器：手持族谱查看器右键任意女仆。它会把资料发到聊天框，包括本人、母亲、父亲、祖辈、配偶，以及最多 8 个直系子代。",
                            "如果某个亲属不在当前世界或无法解析名字，会显示未知或 UUID 简写；这不影响关系记录本身。",
                            "婚姻同意申请书用于成年子代女仆的监护权移交。它不是直接结婚道具，而是把这位成年子代女仆交给另一个玩家重新培养。",
                            "使用步骤一：当前主人手持申请书，右键一位已经成年的子代女仆。申请书会绑定这位女仆，物品提示里会显示已绑定女仆。",
                            "使用步骤二：继续手持同一份申请书，右键目标玩家。目标不能是你自己，也不能是这位女仆两代内直系血亲。",
                            "移交成功后：女仆归目标玩家管理，婚姻数据清空，好感度重置为 0。目标玩家之后需要重新培养关系，才能继续表白或结婚。",
                            "如果重新右键另一位成年子代女仆，申请书会改绑新女仆，并清掉旧目标绑定，避免串线。")),
            new GuideSection("结婚、同眠和怀孕",
                    List.of(
                            "结婚前先确认：她是你的成年女仆，好感达到 192，背包有空间，玩家主手和副手各有一枚未绑定求婚戒指。",
                            "结婚成功后，女仆会收下 YES 枕头。她睡在女仆床上，玩家在相邻原版床旁满足条件时，可以触发同眠剧情。",
                            "普通同眠的怀孕概率不是每次固定值：第一次 20%，之后每次失败 +5%，最高 50%；如果连续失败 8 次，下一次必定怀孕。",
                            "按这条普通曲线计算，平均大约 3.4 次同眠会怀孕；约 58% 的情况会在 3 次内成功，约 91% 的情况会在 6 次内成功。",
                            "节奏玩法单独结算：按成绩从 0% 到 60% 换算怀孕率，不吃普通同眠的失败保底。跳过或超时按 0 分处理。",
                            "怀孕成功后有 2% 概率是双胞胎；普通同眠触发连续失败保底时，会直接按双胞胎处理。",
                            "分娩天数、孩子成长天数、产后恢复期由服务端 common 配置控制。多人服务器里请看服主设置。",
                            "孩子出生后，默认名字是“小女仆”。如果她还是婴儿且名字没改过，可以给她取名，只能取一次。",
                            "出生第一天不能把婴儿放地上。妈妈会抱着她，并给你对应反馈。")),
            new GuideSection("小女仆互动和任务",
                    List.of(
                            "小女仆互动包括：摸摸头、举高高、让妈妈抱抱、陪她说话、送她礼物。",
                            "让妈妈抱抱必须找到她的妈妈。成年妈妈抱着孩子时，普通拥抱选项会隐藏，避免动作冲突。",
                            "儿童阶段可以安排工作任务。任务只在女仆 WORK 日程中推进；睡觉、坐下、休息时间会暂停。",
                            "附魔学：主手或背包有书本。心情正常时给附魔相关奖励；太累时可能只有普通书或低级成果。",
                            "药剂学：主手或背包有玻璃瓶或药水。太累时产出会退化成水瓶、粗制药水等低品质结果。",
                            "战术学：主手或背包有武器，剑、斧、弓、弩、三叉戟都算。太累时只给低品质战斗物品。",
                            "探险：主手或背包有木棍。好感越高，空手而归和受伤概率越低，奖励数量和稀有发现概率越高。心情低到 0 会停止探险。")),
            new GuideSection("常见问题",
                    List.of(
                            "选项不显示：先看好感门槛、是否自己的女仆、是否成年/小女仆对象选错、是否坐下或睡觉、是否后宫模式限制。",
                            "结婚流程走不下去：检查两枚戒指是否都是未绑定求婚戒指。绑定过的戒指会被拦截。",
                            "小女仆任务不开始：她需要可用背包，需要处于 WORK 日程，不能坐下或睡觉，材料要在主手或背包里。",
                            "送礼没反应：每天最多两次，部分特殊物品不能送。背包格子和手上物品变化以服务端同步为准。",
                            "服务器和单人不一样：分娩天数、成长天数、产后恢复看服务端 common 配置；怀孕概率按本指南里的固定曲线结算；后宫模式是玩家本地设置同步到服务端。",
                            "调试按钮和倒计时：调试页里的 F7/F8、倒计时和 UI 动作提示只用于测试或排查，正式游玩可以关掉。"))
    );

    private final Screen parent;
    private int scroll;
    private int maxScroll;

    public MaidMarriageGuideScreen(Screen parent) {
        super(Component.translatable("guide.maidmarriage.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 38, this.height - 30, 76, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int panelLeft = this.width / 2 - PANEL_WIDTH / 2;
        int panelRight = this.width / 2 + PANEL_WIDTH / 2;
        int panelTop = 18;
        int panelBottom = this.height - 38;
        int contentWidth = PANEL_WIDTH - CONTENT_PADDING * 2;

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xDD14121E);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 22, 0xEE201A33);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 7, 0xFFFFE8B8);

        graphics.enableScissor(panelLeft + 8, panelTop + 28, panelRight - 8, panelBottom - 8);
        int y = panelTop + 32 - scroll;
        int contentStartY = y;
        for (GuideSection section : SECTIONS) {
            graphics.drawString(this.font, section.title(), panelLeft + CONTENT_PADDING, y, 0xFFFFD38B, false);
            y += 14;
            for (String paragraph : section.paragraphs()) {
                Component line = Component.literal(paragraph);
                graphics.drawWordWrap(this.font, line, panelLeft + CONTENT_PADDING, y, contentWidth, 0xFFE7E0F5);
                y += this.font.split(line, contentWidth).size() * this.font.lineHeight + 6;
            }
            y += 8;
        }
        graphics.disableScissor();

        maxScroll = Math.max(0, y - contentStartY - (panelBottom - panelTop - 44));
        scroll = Mth.clamp(scroll, 0, maxScroll);

        if (maxScroll > 0) {
            int trackTop = panelTop + 30;
            int trackBottom = panelBottom - 10;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(18, trackHeight * trackHeight / Math.max(trackHeight + maxScroll, 1));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(panelRight - 12, trackTop, panelRight - 8, trackBottom, 0x553D3752);
            graphics.fill(panelRight - 12, thumbY, panelRight - 8, thumbY + thumbHeight, 0xFFFFD38B);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.round(delta * 18.0D), 0, maxScroll);
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private record GuideSection(String title, List<String> paragraphs) {
    }
}
