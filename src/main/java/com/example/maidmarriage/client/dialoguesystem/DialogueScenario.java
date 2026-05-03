package com.example.maidmarriage.client.dialoguesystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * 可配置剧情场景定义。
 *
 * <p>这一层只描述“剧情数据长什么样”，不直接执行旧的拥抱/亲吻/摸头逻辑。
 * 这样后续无论是拥抱菜单、连续剧情、分支事件，还是以后更完整的 Gal UI，
 * 都能先落在同一套场景格式上，再由运行时控制器解释执行。
 *
 * <p>设计目标：
 * 1. 一段剧情可以由多个节点组成，而不是“一种动作只对应一句话”；
 * 2. 每个节点可以是连续文本、选项、条件跳转、动作、结束；
 * 3. 每一行文本都可以单独切换表情、头像槽位和 UI 动画；
 * 4. 动作只暴露语义 ID，不直接在 JSON 里写 Java 细节。
 */
public final class DialogueScenario {
    public String id = "";
    public String theme = "maidmarriage:hug_gal";
    public String start = "start";
    public String animationLibrary = "maidmarriage:default";
    public Map<String, PortraitProfile> portraits = new LinkedHashMap<>();
    public Map<String, Node> nodes = new LinkedHashMap<>();

    /**
     * 根据节点字符串解析统一的节点类型。
     *
     * <p>这里故意对大小写和空值做了兜底，
     * 这样未来剧情 JSON 即便手写，也不容易因为一个单词大小写写错直接崩掉。
     */
    public static NodeType resolveNodeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return NodeType.SEQUENCE;
        }
        return switch (rawType.toLowerCase(Locale.ROOT)) {
            case "choice" -> NodeType.CHOICE;
            case "action" -> NodeType.ACTION;
            case "branch" -> NodeType.BRANCH;
            case "end" -> NodeType.END;
            default -> NodeType.SEQUENCE;
        };
    }

    /**
     * 对场景做轻量规范化，保证运行时可以用统一的空对象/空集合思路处理。
     */
    public DialogueScenario normalize(ResourceLocation fallbackId) {
        if (id == null || id.isBlank()) {
            id = fallbackId == null ? "" : fallbackId.toString();
        }
        if (theme == null || theme.isBlank()) {
            theme = "maidmarriage:hug_gal";
        }
        if (start == null || start.isBlank()) {
            start = "start";
        }
        if (animationLibrary == null || animationLibrary.isBlank()) {
            animationLibrary = "maidmarriage:default";
        }
        if (portraits == null) {
            portraits = new LinkedHashMap<>();
        }
        if (nodes == null) {
            nodes = new LinkedHashMap<>();
        }

        portraits.values().forEach(PortraitProfile::normalize);
        nodes.values().forEach(Node::normalize);
        return this;
    }

    public enum NodeType {
        SEQUENCE,
        CHOICE,
        ACTION,
        BRANCH,
        END
    }

    /**
     * 头像槽位定义。
     *
     * <p>这里不强制要求立刻接入现有 UI，
     * 先把“一个槽位有哪些表情贴图”描述清楚。
     * 后续不管是单张贴图头像、Live2D 风格立绘，还是多槽位并存，
     * 都可以从这个结构继续演进。
     */
    public static final class PortraitProfile {
        public String texture = "";
        public String defaultExpression = "default";
        public Map<String, String> expressions = new LinkedHashMap<>();

        public void normalize() {
            if (texture == null) {
                texture = "";
            }
            if (defaultExpression == null || defaultExpression.isBlank()) {
                defaultExpression = "default";
            }
            if (expressions == null) {
                expressions = new LinkedHashMap<>();
            }
            if (!texture.isBlank()) {
                expressions.putIfAbsent(defaultExpression, texture);
            }
        }
    }

    /**
     * 单个剧情节点。
     *
     * <p>节点本身不依赖具体 UI 组件。
     * UI 层只需要根据当前节点+当前页渲染内容；
     * 运行时控制器只负责决定“现在该看到哪一句、该往哪跳”。
     */
    public static final class Node {
        public String type = "sequence";
        public Prompt prompt = new Prompt();
        public List<Line> lines = new ArrayList<>();
        public List<Choice> choices = new ArrayList<>();
        public List<Branch> branches = new ArrayList<>();
        public List<Event> events = new ArrayList<>();
        public String action = "";
        public String next = "";

        public void normalize() {
            if (type == null || type.isBlank()) {
                type = "sequence";
            }
            if (prompt == null) {
                prompt = new Prompt();
            }
            if (lines == null) {
                lines = new ArrayList<>();
            }
            if (choices == null) {
                choices = new ArrayList<>();
            }
            if (branches == null) {
                branches = new ArrayList<>();
            }
            if (events == null) {
                events = new ArrayList<>();
            }
            if (action == null) {
                action = "";
            }
            if (next == null) {
                next = "";
            }

            prompt.normalize();
            lines.forEach(Line::normalize);
            choices.forEach(Choice::normalize);
            branches.forEach(Branch::normalize);
            events.forEach(Event::normalize);
        }
    }

    /**
     * 用于选择节点的开场文案。
     *
     * <p>连续文本节点用 {@link Line} 列表，
     * 选择节点通常只需要一句提示语，因此单独拆成 prompt 更清晰。
     */
    public static class Prompt {
        public String speaker = "";
        public String text = "";
        public String portrait = "";
        public String expression = "";
        public String animation = "";
        public List<Event> events = new ArrayList<>();

        public void normalize() {
            if (speaker == null) {
                speaker = "";
            }
            if (text == null) {
                text = "";
            }
            if (portrait == null) {
                portrait = "";
            }
            if (expression == null) {
                expression = "";
            }
            if (animation == null) {
                animation = "";
            }
            if (events == null) {
                events = new ArrayList<>();
            }
            events.forEach(Event::normalize);
        }
    }

    /**
     * 一条具体文本页。
     *
     * <p>这里刻意使用“行/页”这个粒度，
     * 是为了支持一段连续剧情里每一句都切不同表情、不同动画、不同事件。
     */
    public static final class Line extends Prompt {
    }

    /**
     * 玩家可选项。
     *
     * <p>选项本身可以带动作，也可以只是跳到另一个节点。
     * 这样后续剧情设计既能做“纯文本分支”，也能做“点击后触发某个动作演出”。
     */
    public static final class Choice {
        public String id = "";
        public String title = "";
        public String description = "";
        public String next = "";
        public String action = "";
        public String condition = "";
        public String portrait = "";
        public String expression = "";
        public String animation = "";
        public List<Event> events = new ArrayList<>();

        public void normalize() {
            if (id == null) {
                id = "";
            }
            if (title == null) {
                title = "";
            }
            if (description == null) {
                description = "";
            }
            if (next == null) {
                next = "";
            }
            if (action == null) {
                action = "";
            }
            if (condition == null) {
                condition = "";
            }
            if (portrait == null) {
                portrait = "";
            }
            if (expression == null) {
                expression = "";
            }
            if (animation == null) {
                animation = "";
            }
            if (events == null) {
                events = new ArrayList<>();
            }
            events.forEach(Event::normalize);
        }
    }

    /**
     * 条件跳转项。
     *
     * <p>branch 节点会按顺序判断条件，
     * 命中第一条就跳过去，没有命中则走节点自身的 next 兜底。
     */
    public static final class Branch {
        public String condition = "";
        public String next = "";

        public void normalize() {
            if (condition == null) {
                condition = "";
            }
            if (next == null) {
                next = "";
            }
        }
    }

    /**
     * 剧情运行时事件描述。
     *
     * <p>这里只保留安全的、语义化的字段：
     * - when：在什么时机触发；
     * - type：要做哪一类事；
     * - target/value：具体参数；
     *
     * <p>我们刻意不在这里塞“任意执行 Java/MVEL/命令”的能力，
     * 避免未来剧情数据把客户端逻辑又拖回不可维护状态。
     */
    public static final class Event {
        public String when = "enter";
        public String type = "";
        public String target = "";
        public String value = "";
        public String next = "";
        public Map<String, String> params = new LinkedHashMap<>();

        public void normalize() {
            if (when == null || when.isBlank()) {
                when = "enter";
            }
            if (type == null) {
                type = "";
            }
            if (target == null) {
                target = "";
            }
            if (value == null) {
                value = "";
            }
            if (next == null) {
                next = "";
            }
            if (params == null) {
                params = new LinkedHashMap<>();
            }
        }
    }
}
