package com.example.maidmarriage.client.dialoguesystem.runtime;

import com.example.maidmarriage.client.HugClientState;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenario;
import com.example.maidmarriage.client.dialoguesystem.DialogueScenarioLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/**
 * 拥抱界面与新剧情系统之间的桥接层。
 *
 * <p>这一层的职责非常明确：
 * 1. 读取指定剧情场景；
 * 2. 启动并持有 {@link DialogueSessionController}；
 * 3. 负责把玩家名、女仆名这些运行时变量喂给剧情；
 * 4. 对外暴露“当前应该显示哪一帧”、“推进剧情”、“选择选项”、“取出动作请求”这些高层接口。
 *
 * <p>这样做的目的，是让旧的 {@code HugActionScreen} 不再继续承担：
 * - 文本分页；
 * - 选项跳转；
 * - 节点状态机；
 * - 事件分发；
 * 这些本来就不应该由 Screen 自己硬写的逻辑。
 *
 * <p>同时这里仍然刻意不碰旧动作执行器，
 * 因为当前阶段我们的目标是“先把 UI 和剧情状态机接起来”，
 * 而不是又把旧的亲吻/摸头/拥抱网络发包逻辑重新塞回来。
 */
public final class HugDialogueRuntimeBridge {
    /**
     * 约定好的变量名：剧情 JSON 里可以直接写 `${maid}`。
     */
    private static final String VARIABLE_MAID = "maid";

    /**
     * 约定好的变量名：剧情 JSON 里可以直接写 `${player}`。
     */
    private static final String VARIABLE_PLAYER = "player";

    /**
     * 当前交互会话是否已经切到了拥抱姿态。
     *
     * <p>剧情 JSON 可以直接用：
     * - `hugging`：当前已在拥抱中；
     * - `!hugging`：当前只是站立锁定。
     */
    private static final String VARIABLE_HUGGING = "hugging";

    /**
     * 当前是否仍处于这份亲密交互会话里。
     *
     * <p>虽然目前 HugActionScreen 本身只会在交互会话中打开，
     * 但保留这个变量后，后面如果我们把同一套剧情系统拓到别的界面，也还能直接复用。
     */
    private static final String VARIABLE_INTERACTING = "interacting";

    /**
     * 当前桥接层服务的剧情资源 ID。
     */
    private final ResourceLocation scenarioId;

    /**
     * 女仆名字的提供器。
     *
     * <p>之所以不在构造时直接传字符串，
     * 是因为屏幕打开后，实体名、语言环境、占位符上下文都可能变化。
     * 用 Supplier 可以保证每次刷新变量时取到当前值。
     */
    private final Supplier<String> maidNameSupplier;

    /**
     * 玩家名字的提供器。
     */
    private final Supplier<String> playerNameSupplier;

    /**
     * 已加载的剧情场景数据。
     */
    private DialogueScenario scenario;

    /**
     * 场景运行时控制器。
     */
    private DialogueSessionController controller;

    /**
     * 当前剧情实际使用的主题资源 ID。
     */
    private ResourceLocation activeThemeId;

    public HugDialogueRuntimeBridge(ResourceLocation scenarioId,
                                    Supplier<String> maidNameSupplier,
                                    Supplier<String> playerNameSupplier) {
        this.scenarioId = Objects.requireNonNull(scenarioId, "scenarioId");
        this.maidNameSupplier = maidNameSupplier == null ? () -> "" : maidNameSupplier;
        this.playerNameSupplier = playerNameSupplier == null ? () -> "" : playerNameSupplier;
    }

    /**
     * 初始化剧情运行时。
     *
     * <p>屏幕每次真正打开时调用一次；
     * resize 不需要重建整个桥接层，只需要重新按当前帧刷新 UI。
     */
    public void initialize() {
        prepare();
        start();
    }

    /**
     * 只加载剧情和创建控制器，暂不结算起始节点。
     *
     * <p>入口节点可能依赖屏幕上下文变量，例如“妈妈是否抱着尚未命名的小女仆”。
     * 这些变量必须由 Screen 写入后才能启动剧情，否则起始分支会过早落到普通菜单。
     */
    public void prepare() {
        this.scenario = DialogueScenarioLoader.load(scenarioId);
        this.controller = new DialogueSessionController(
                scenario,
                DialogueEventRegistry.createDefault(),
                new SimpleDialogueConditionEvaluator()
        );
        this.activeThemeId = parseThemeId(scenario.theme);
        updateVariables();
    }

    /**
     * 在完整上下文变量写入后启动剧情。
     */
    public void start() {
        ensureLoaded();
        updateVariables();
        this.controller.start();
    }

    /**
     * 当玩家名/女仆名发生变化时，刷新剧情变量。
     *
     * <p>这里不重置节点状态，只更新上下文变量，
     * 避免界面运行到一半时突然跳回开头。
     */
    public void updateVariables() {
        if (controller == null) {
            return;
        }
        controller.runtimeContext().setVariable(VARIABLE_MAID, safeValue(maidNameSupplier.get()));
        controller.runtimeContext().setVariable(VARIABLE_PLAYER, safeValue(playerNameSupplier.get()));
        controller.runtimeContext().setVariable(VARIABLE_HUGGING, Boolean.toString(HugClientState.isLocalPlayerHugging()));
        controller.runtimeContext().setVariable(VARIABLE_INTERACTING, Boolean.toString(HugClientState.isLocalPlayerInteracting()));
    }

    /**
     * 给外部 UI 层补充运行时变量。
     *
     * <p>这类变量通常来自当前屏幕上下文，例如：
     * - 当前目标是否属于玩家子代；
     * - 当前应使用哪套称呼文本；
     * - 某个选项是否应在 UI 层直接隐藏/替换。
     *
     * <p>这层只负责把变量写进运行时上下文，不参与条件判断本身。
     */
    public void setVariable(String key, String value) {
        ensureLoaded();
        controller.runtimeContext().setVariable(key, safeValue(value));
    }

    /**
     * 返回当前剧情主题 ID。
     *
     * <p>这样旧 UI 就能继续沿用现有的 theme loader / theme file store，
     * 只是主题来源从“写死常量”改成了“场景数据声明”。
     */
    public ResourceLocation activeThemeId() {
        return activeThemeId == null ? new ResourceLocation("maidmarriage", "hug_default") : activeThemeId;
    }

    /**
     * 取得当前要显示的剧情帧。
     */
    public DialogueFrameView currentFrame() {
        ensureStarted();
        updateVariables();
        /**
         * 当前帧在返回给 UI 之前，会先经过一次“阶段化文案增强”。
         *
         * 这样可以把“台本主结构”和“关系阶段细化”拆开：
         * - 主结构仍由 JSON 决定；
         * - 阶段差异由这里做最后一层补强。
         *
         * UI 层完全不需要知道这件事，只拿最终可显示帧即可。
         */
        return HugDialogueStageFlavorComposer.apply(controller.currentFrame(), controller.runtimeContext());
    }

    /**
     * 推进到下一句。
     *
     * <p>只有连续文本节点会真正前进；
     * 选项节点如果调用这里，不会误跳转。
     */
    public boolean advance() {
        ensureStarted();
        updateVariables();
        return controller.advance();
    }

    /**
     * 点击某个选项。
     */
    public boolean choose(String choiceId) {
        ensureStarted();
        updateVariables();
        return controller.choose(choiceId);
    }

    public void jumpToNode(String nodeId) {
        ensureStarted();
        controller.jumpToNodeAndSettle(nodeId);
    }

    public String currentNodeId() {
        ensureStarted();
        return controller.currentNodeId();
    }

    /**
     * 取出剧情层发出的语义动作请求。
     *
     * <p>当前阶段这些请求只用于观察和调试，
     * 后续再接到旧的网络发包/动作管理器。
     */
    public List<DialogueActionRequest> drainActionRequests() {
        ensureStarted();
        List<DialogueActionRequest> requests = new ArrayList<>();
        DialogueActionRequest request;
        while ((request = controller.runtimeContext().pollAction()) != null) {
            requests.add(request);
        }
        return requests;
    }

    /**
     * 将带 `${}` 占位符的文本渲染成当前 UI 应该显示的字符串。
     *
     * <p>目前先支持最常用的两个变量：
     * - `${maid}`
     * - `${player}`
     *
     * <p>后续如果剧情变量继续扩展，
     * 这里可以很容易改成通用模板替换，而不需要动 Screen。
     */
    public String renderTemplate(String raw) {
        ensureLoaded();
        String rendered = safeValue(raw);
        for (int pass = 0; pass < 3; pass++) {
            String before = rendered;
            for (var entry : controller.runtimeContext().variablesView().entrySet()) {
                String key = entry.getKey();
                String value = safeValue(entry.getValue());
                rendered = rendered.replace("${" + key + "}", value);
                rendered = rendered.replace("{" + key + "}", value);
            }
            if (before.equals(rendered)) {
                break;
            }
        }
        return rendered;
    }

    private void ensureInitialized() {
        if (controller == null || scenario == null) {
            initialize();
        }
    }

    private void ensureLoaded() {
        if (controller == null || scenario == null) {
            prepare();
        }
    }

    private void ensureStarted() {
        ensureLoaded();
        controller.start();
    }

    private ResourceLocation parseThemeId(String rawThemeId) {
        ResourceLocation parsed = ResourceLocation.tryParse(rawThemeId);
        return parsed == null ? new ResourceLocation("maidmarriage", "hug_default") : parsed;
    }

    private String safeValue(String raw) {
        return raw == null ? "" : raw;
    }
}
