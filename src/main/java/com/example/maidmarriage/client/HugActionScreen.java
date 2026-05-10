package com.example.maidmarriage.client;

import com.example.maidmarriage.MaidMarriageMod;
import com.example.maidmarriage.config.ModConfigs;
import com.example.maidmarriage.client.dialogueui.DialogueBoxComponent;
import com.example.maidmarriage.client.dialogueui.DialogueIconButtonComponent;
import com.example.maidmarriage.client.dialogueui.DialogueOptionComponent;
import com.example.maidmarriage.client.dialogueui.DialoguePortraitComponent;
import com.example.maidmarriage.client.dialogueui.DialogueState;
import com.example.maidmarriage.client.dialogueui.DialogueTheme;
import com.example.maidmarriage.client.dialogueui.DialogueThemeFileStore;
import com.example.maidmarriage.client.dialogueui.DialogueThemeLoader;
import com.example.maidmarriage.client.dialogueui.DialogueUiRender;
import com.example.maidmarriage.client.dialoguesystem.runtime.HugDialogueActionDispatcher;
import com.example.maidmarriage.client.dialoguesystem.runtime.HugDialogueContextVariables;
import com.example.maidmarriage.client.dialoguesystem.runtime.DialogueChoiceView;
import com.example.maidmarriage.client.dialoguesystem.runtime.DialogueFrameView;
import com.example.maidmarriage.client.dialoguesystem.runtime.HugDialogueRuntimeBridge;
import com.example.maidmarriage.client.dialoguesystem.runtime.HugStoryResumeState;
import com.example.maidmarriage.client.dialoguesystem.runtime.HugDialogueStageFlavorComposer;
import com.example.maidmarriage.compat.MaidMoodManager;
import com.example.maidmarriage.compat.MaidRelationshipManager;
import com.example.maidmarriage.compat.RelationStage;
import com.example.maidmarriage.config.DialogueScriptManager;
import com.example.maidmarriage.network.payload.GiftResultPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 拥抱界面。
 *
 * <p>这次重写的目标非常明确：
 * 1. 保留你原来这套拥抱 UI 的视觉骨架、调试方式、主题系统；
 * 2. 去掉旧版 Screen 自己手搓的剧情状态机；
 * 3. 改成由新的 `DialogueSessionController` 提供当前帧数据；
 * 4. 剧情动作只通过本类的白名单映射接回旧动作触发器。
 *
 * <p>也就是说：
 * - 现在这个 Screen 负责“渲染”和“交互转发”；
 * - 新剧情运行时负责“节点推进”“连续文本”“选项跳转”“动作事件排队”。
 *
 * <p>这样后续继续扩动作、扩剧情、扩表情和 UI 动画时，
 * 我们就不需要再往这个类里继续堆 switch-case 了。
 */
@Mod.EventBusSubscriber(modid = MaidMarriageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HugActionScreen extends Screen {
    private static final Pattern STRUCTURED_DIALOGUE_LINE = Pattern.compile("^(女仆|旁白|玩家)\\s*[：:]\\s*(.+)$");
    /**
     * 旧拥抱 UI 的默认主题。
     *
     * <p>如果新剧情没有声明自己的主题，
     * 或者声明的主题资源写错了，就自动回退到这里。
     */
    private static final ResourceLocation DEFAULT_THEME_ID = new ResourceLocation(MaidMarriageMod.MOD_ID, "hug_default");

    /**
     * 当前接入旧拥抱 UI 的新剧情场景。
     *
     * <p>这里故意写成常量，
     * 方便后面逐步把旧的拥抱菜单完整迁到新的场景 JSON 上。
     */
    private static final ResourceLocation HUG_SCENARIO_ID = new ResourceLocation(MaidMarriageMod.MOD_ID, "hug_menu_v2");
    private static final ResourceLocation CHILD_SCENARIO_ID = new ResourceLocation(MaidMarriageMod.MOD_ID, "child_interaction_v1");

    /**
     * 旧界面右上角的“隐藏 UI”按钮图标。
     */
    private static final ResourceLocation HIDE_ICON = new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/hug_hide_icon.png");

    /**
     * 旧界面右上角的“退出拥抱”按钮图标。
     */
    private static final ResourceLocation EXIT_ICON = new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/hug_exit_icon.png");

    /**
     * 当剧情没有提供可解析的表情贴图时使用的兜底贴图。
     */
    private static final ResourceLocation SOFT_SMILE = new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/soft_smile.png");

    /**
     * 旧资源里常用的“脸红/热笑”贴图。
     *
     * <p>当前虽然不再由 Screen 自己写死切换逻辑，
     * 但这个资源仍然有保留价值，后续剧情和表情系统都还能继续复用。
     */
    private static final ResourceLocation HOT_SMILE = new ResourceLocation(MaidMarriageMod.MOD_ID, "textures/gui/emotion/hot_smile.png");

    /**
     * 旧 UI 的紧凑显示开关。
     *
     * <p>这一行为保留不动，避免把用户已经习惯的交互打断。
     */
    private static boolean compactMode = false;

    private static final int CAMERA_PANEL_WIDTH = 142;
    private static final int CAMERA_PANEL_HEIGHT = 74;
    private static final int CAMERA_PANEL_MARGIN = 8;
    private static final int CAMERA_SLIDER_WIDTH = 74;
    private static final int CAMERA_SLIDER_HEIGHT = 5;

    /**
     * 主对话框组件。
     */
    private final DialogueBoxComponent dialogueBox = new DialogueBoxComponent();

    /**
     * 女仆头像组件。
     */
    private final DialoguePortraitComponent portrait = new DialoguePortraitComponent();

    /**
     * 隐藏按钮组件。
     */
    private final DialogueIconButtonComponent hideButton = new DialogueIconButtonComponent();

    /**
     * 退出按钮组件。
     */
    private final DialogueIconButtonComponent exitButton = new DialogueIconButtonComponent();

    /**
     * 当前界面上实际可见的选项组件列表。
     *
     * <p>注意它现在不再是固定的 kiss/hug/pet 三个，
     * 而是每次根据当前剧情帧里的 choices 动态重建。
     */
    private final List<DialogueOptionComponent> options = new ArrayList<>();

    /**
     * 旧 UI 使用的展示态数据对象。
     *
     * <p>我们不重写这套展示态，而是继续喂它数据，
     * 这样渲染层改动最小，也方便你对照理解。
     */
    private final DialogueState dialogueState = new DialogueState();

    /**
     * 新剧情运行时桥接层。
     *
     * <p>它对这个 Screen 暴露的是“当前帧视图”，
     * 而不是底层节点树细节。
     */
    private final HugDialogueRuntimeBridge dialogueRuntime;

    @Nullable
    private final UUID fixedMaidUuid;

    private final boolean childInteractionMode;

    /**
     * 当前正在使用的 UI 主题数据。
     */
    private DialogueTheme theme;

    /**
     * 当前剧情声明出来的主题 ID。
     */
    private ResourceLocation activeThemeId = DEFAULT_THEME_ID;

    /**
     * 旧调试系统当前正在编辑哪个布局块。
     */
    private DebugTarget debugTarget = DebugTarget.DIALOG_BOX;

    /**
     * 旧调试开关。
     */
    private boolean debugLayout;

    /**
     * 右下角镜头微调面板是否展开。
     *
     * <p>这个面板只服务当前客户端视觉参数：
     * 一个控制默认缩放，一个控制上下视角偏移。
     */
    private boolean cameraAdjustPanelOpen;

    /**
     * 当前正在拖动哪个镜头滑块。
     */
    private CameraSliderDragTarget cameraSliderDragTarget = CameraSliderDragTarget.NONE;

    /**
     * 打开面板时记录一份快照。
     *
     * <p>如果玩家点铅笔关闭但没有点“保存”，就恢复到打开前的运行时参数；
     * 如果点保存，则把当前值写入 config。
     */
    private double cameraPanelSnapshotFovScale;
    private float cameraPanelSnapshotPitchOffset;

    /**
     * 屏幕左上角短提示文案。
     */
    private String debugMessage = "";

    /**
     * 短提示剩余显示 tick。
     */
    private int debugMessageTicks;

    /**
     * 当前被拥抱女仆的显示名缓存。
     *
     * <p>缓存的原因不是性能，而是让 `${maid}` 这类模板变量在界面运行期间始终稳定。
     */
    private String cachedMaidName = "";
    private List<StructuredDialogueLine> structuredDialogueLines = List.of();
    private int structuredDialogueIndex;
    private String structuredDialogueKey = "";
    private String currentHintText = "";
    private boolean giftResultDialogueActive;

    public HugActionScreen() {
        this(HUG_SCENARIO_ID, null, false, text("ui.maidmarriage.hug_action.title"));
    }

    public static HugActionScreen childInteraction(UUID maidUuid) {
        return new HugActionScreen(
                CHILD_SCENARIO_ID,
                maidUuid,
                true,
                text("ui.maidmarriage.child_interaction.title")
        );
    }

    private HugActionScreen(ResourceLocation scenarioId,
                            @Nullable UUID fixedMaidUuid,
                            boolean childInteractionMode,
                            Component title) {
        super(title);
        this.fixedMaidUuid = fixedMaidUuid;
        this.childInteractionMode = childInteractionMode;
        this.dialogueRuntime = new HugDialogueRuntimeBridge(scenarioId, this::safeMaidName, this::resolvePlayerName);
    }

    @Nullable
    public UUID targetMaidUuidForActions() {
        return fixedMaidUuid != null ? fixedMaidUuid : HugClientState.getLocalInteractionMaidUuid();
    }

    /**
     * 接收服务端确认后的送礼剧情反馈。
     *
     * <p>送礼面板会先回到互动 UI，随后服务端只在“真的收下礼物”时发回结果包。
     * 这里再把它临时插入对话框，玩家点一下对话框后回到原本的互动选项。
     */
    public static void handleGiftResult(GiftResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof HugActionScreen screen) || payload == null) {
            return;
        }
        UUID targetUuid = screen.targetMaidUuidForActions();
        if (targetUuid == null || !targetUuid.equals(payload.maidUuid())) {
            return;
        }
        screen.showGiftResultDialogue(payload.category(), payload.reaction());
    }

    /**
     * 当前界面是否是“小女仆互动页”。
     *
     * <p>客户端自动开屏逻辑会用这个方法区分：
     * - 成年女仆互动会话打开普通拥抱页；
     * - 小女仆互动会话打开小女仆互动页；
     * 避免两个入口互相误判成同一个 `HugActionScreen`。
     */
    public boolean isChildInteractionScreen() {
        return childInteractionMode;
    }

    @Override
    protected void init() {
        // 先缓存当前女仆名字，供剧情变量 `${maid}` 使用。
        this.cachedMaidName = resolveMaidName();

        // 初始化新的剧情状态机。
        //
        // 注意：入口路由会读取“妈妈是否抱着未命名小女仆”等屏幕上下文变量，
        // 所以这里必须先加载台本、写完整变量，再启动剧情。否则起始分支会提前落到普通菜单。
        this.dialogueRuntime.prepare();
        updateDialogueContextVariables();
        this.dialogueRuntime.start();
        HugStoryResumeState.PendingResume pendingResume = HugStoryResumeState.consumeIfMatches(
                targetMaidUuidForActions(),
                HUG_SCENARIO_ID
        );
        if (pendingResume != null) {
            this.dialogueRuntime.jumpToNode(pendingResume.nodeId());
        }

        // 拥抱面板的布局必须继续使用旧 UI 的默认主题。
        //
        // 注意：剧情 JSON 以后可以管理“文本、选项、表情、动作”，
        // 但不能在这里改布局主题，否则会覆盖你之前花时间调好的默认坐标。
        // 之前 UI 乱掉就是因为这里误用了场景里的 theme=maidmarriage:hug_gal。
        this.activeThemeId = DEFAULT_THEME_ID;

        // 视觉层继续使用旧的主题 loader / file store。
        // 这样你之前写好的布局调试能力、覆写文件能力都不需要推倒重来。
        this.theme = DialogueThemeFileStore.loadOrDefault(DialogueThemeLoader.load(activeThemeId));

        // 先根据主题把组件摆好。
        rebuildComponents();

        // 再把当前剧情帧同步到旧 UI。
        refreshDialogueState(true);
    }

    @Override
    public void tick() {
        super.tick();

        // 现在界面的生存条件改成“仍处于交互会话中”。
        // 这样玩家可以先站立锁定进入面板，再在面板内部决定是否切到拥抱姿态。
        if (!isScreenTargetStillValid()) {
            onClose();
            return;
        }

        // 更新短提示的剩余显示时间。
        if (debugMessageTicks > 0) {
            debugMessageTicks--;
        }

        if (Screen.hasControlDown()) {
            fastForwardDialogueUntilChoice();
        } else {
            dialogueBox.tickTypewriter();
        }

        // 如果运行中的女仆显示名变化了，就把变量同步给新剧情系统，并刷新当前帧。
        String resolvedMaidName = resolveMaidName();
        if (!resolvedMaidName.equals(cachedMaidName)) {
            cachedMaidName = resolvedMaidName;
            refreshDialogueState(false);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);

        // resize 时只重算布局，不重置剧情状态。
        rebuildComponents();
        refreshDialogueState(false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 紧凑模式下只保留调试覆盖层。
        if (compactMode) {
            renderTopRightStatus(graphics);
            renderDebugOverlay(graphics);
            return;
        }

        renderSceneLine(graphics);
        dialogueBox.render(graphics, this.width, this.height, mouseX, mouseY);
        portrait.render(graphics, this.width, this.height, mouseX, mouseY);
        for (DialogueOptionComponent option : options) {
            option.render(graphics, this.width, this.height, mouseX, mouseY);
        }
        hideButton.render(graphics, this.width, this.height, mouseX, mouseY);
        exitButton.render(graphics, this.width, this.height, mouseX, mouseY);
        renderTopRightStatus(graphics);
        renderZoomLabel(graphics);
        renderCameraAdjustPanel(graphics, mouseX, mouseY);
        renderDebugOverlay(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 紧凑模式下，左键任意处点击恢复正常显示。
        if (compactMode) {
            compactMode = false;
            return true;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (handleCameraAdjustMouseClick(mouseX, mouseY)) {
            return true;
        }

        // 点击隐藏按钮：只隐藏 UI，不改变剧情状态。
        if (hideButton.contains(mouseX, mouseY, this.width, this.height)) {
            compactMode = true;
            return true;
        }

        // 点击退出按钮：结束整份交互会话，而不是只切换拥抱姿态。
        if (exitButton.contains(mouseX, mouseY, this.width, this.height)) {
            if (childInteractionMode) {
                PetHeadClientHandler.triggerChildInteraction(minecraft, fixedMaidUuid);
            } else {
                PetHeadClientHandler.triggerInteraction(minecraft);
            }
            onClose();
            return true;
        }

        // 点击选项：不再由 Screen 自己手写 kiss/hug/pet 分支，
        // 而是把 choiceId 交给新的剧情运行时决定该怎么跳。
        for (DialogueOptionComponent option : options) {
            if (option.hidden) {
                continue;
            }
            if (!option.contains(mouseX, mouseY, this.width, this.height)) {
                continue;
            }

            // 如果文本还没打完，就先补全文字，避免误点。
            if (!dialogueBox.isComplete()) {
                dialogueBox.revealAll();
                return true;
            }

            boolean accepted = dialogueRuntime.choose(option.id());
            drainScenarioActionRequests();

            if (accepted) {
                refreshDialogueState(true);
            } else {
                showDebugMessage("该选项当前不可用或未命中剧情节点");
            }
            return true;
        }

        // 点击对话框：推进连续文本。
        if (dialogueBox.contains(mouseX, mouseY, this.width, this.height)) {
            advanceDialogue();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (cameraSliderDragTarget != CameraSliderDragTarget.NONE) {
            updateCameraSliderValue(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (cameraSliderDragTarget != CameraSliderDragTarget.NONE) {
            cameraSliderDragTarget = CameraSliderDragTarget.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F8) {
            debugLayout = !debugLayout;
            showDebugMessage(debugLayout ? "Hug UI Debug: ON" : "Hug UI Debug: OFF");
            return true;
        }
        if (debugLayout && handleDebugKey(keyCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_H) {
            compactMode = !compactMode;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            advanceDialogue();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        HugCameraZoom.adjustHugZoom(delta);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        closeCameraAdjustPanelWithoutSaving();
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.minecraft.setScreen(null);
        }
    }

    /**
     * 在拥抱 UI 打开时，继续隐藏原版热键栏和聊天栏。
     */
    @SubscribeEvent
    public static void hideVanillaHudWhenHugUiActive(RenderGuiOverlayEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof HugActionScreen screen) || !screen.shouldHideVanillaHud()) {
            return;
        }
        if (event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())
                || event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            event.setCanceled(true);
        }
    }

    /**
     * 按当前主题重建所有静态组件。
     *
     * <p>这里负责的只是“布局”和“皮肤”，
     * 不负责决定当前该显示哪句文本、哪些选项。
     */
    private void rebuildComponents() {
        dialogueBox.applyTheme(theme.dialogBox);

        portrait.setBounds(theme.portrait.x, theme.portrait.y, theme.portrait.width, theme.portrait.height);
        portrait.setAlign(theme.portrait.alignX, theme.portrait.alignY);
        portrait.setAlpha(theme.portrait.alpha);
        portrait.setTexture(DialogueTheme.parseTexture(theme.portrait.texture, SOFT_SMILE));
        portrait.hidden = !theme.portrait.visible;

        hideButton.applyTheme(theme.controlIcon).setIconTexture(HIDE_ICON).setChromeEnabled(false);
        exitButton.applyTheme(theme.controlIcon).setIconTexture(EXIT_ICON).setChromeEnabled(false);
        hideButton.setBounds(theme.controlIcon.x, theme.controlIcon.y, theme.controlIcon.width, theme.controlIcon.height);
        exitButton.setBounds(
                theme.controlIcon.x + theme.controlIcon.width,
                theme.controlIcon.y,
                theme.controlIcon.width,
                theme.controlIcon.height
        );

        // 选项列表根据当前剧情帧动态生成，不再在这里写死。
        rebuildOptionComponents(dialogueRuntime.currentFrame());
    }

    /**
     * 渲染旧 UI 中央那条柔和的分割线。
     */
    private void renderSceneLine(GuiGraphics graphics) {
        if (theme.background.lineWidth <= 0.0F) {
            return;
        }
        int y = Math.round(this.height * (theme.background.lineY / 100.0F));
        int x = Math.round(this.width * (theme.background.lineX / 100.0F));
        int width = Math.round(this.width * (theme.background.lineWidth / 100.0F));
        DialogueUiRender.fillHorizontalGradient(graphics, x, y, width, 2,
                0x00FFFFFF, 0x58FFEAF2, 0xDDFCEFF4, 0x58FFEAF2, 0x00FFFFFF);
    }

    /**
     * 渲染旧 UI 右下角的镜头缩放提示。
     */
    private void renderZoomLabel(GuiGraphics graphics) {
        int x = Math.round(this.width * (theme.zoomLabel.x / 100.0F));
        int y = Math.round(this.height * (theme.zoomLabel.y / 100.0F));
        DialogueUiRender.drawScaledText(graphics, this.font, Component.literal(compactZoomText()),
                x, y, theme.zoomLabel.scale, theme.zoomLabel.color, false);
    }

    private void renderTopRightStatus(GuiGraphics graphics) {
        EntityMaid maid = resolveTargetMaid();
        String moodName = moodLabel(maid);
        String relationName = relationLabel(maid);

        Component moodLine = Component.literal(text("ui.maidmarriage.hug_action.mood_label").getString() + moodName);
        Component relationLine = Component.literal(text("ui.maidmarriage.hug_action.relation_label").getString() + relationName);

        int right = this.width - 10;
        int lineHeight = this.font.lineHeight;
        int top = 10;

        graphics.drawString(this.font, relationLine, right - this.font.width(relationLine), top, 0xFFFFEEF8, true);
        graphics.drawString(this.font, moodLine, right - this.font.width(moodLine), top + lineHeight + 2, 0xFFFFEEF8, true);
    }

    /**
     * 渲染右下角的镜头微调按钮和小浮层。
     *
     * <p>这里故意不用主题布局文件，避免破坏你之前已经调好的主 UI 坐标。
     * 它始终固定在右下角，尺寸也控制得比较小，只做临时调参入口。
     */
    private void renderCameraAdjustPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int iconX = cameraPanelIconX();
        int iconY = cameraPanelIconY();
        int iconWidth = cameraPanelIconWidth();
        int iconHeight = cameraPanelIconHeight();
        boolean iconHovered = isInside(mouseX, mouseY, iconX, iconY, iconWidth, iconHeight);
        float pencilScale = 0.78F;
        int pencilColor = iconHovered || cameraAdjustPanelOpen ? 0xFFFFEEF8 : 0xFFD8C3D2;
        DialogueUiRender.drawScaledText(
                graphics,
                this.font,
                Component.literal("✎"),
                iconX + 1,
                iconY + 1,
                pencilScale,
                pencilColor,
                false
        );

        if (!cameraAdjustPanelOpen) {
            return;
        }

        int panelX = cameraPanelX();
        int panelY = cameraPanelY();
        graphics.fill(panelX, panelY, panelX + CAMERA_PANEL_WIDTH, panelY + CAMERA_PANEL_HEIGHT, 0xDD140C16);
        graphics.fill(panelX, panelY, panelX + CAMERA_PANEL_WIDTH, panelY + 1, 0xAAFFF2F8);
        graphics.fill(panelX, panelY, panelX + 1, panelY + CAMERA_PANEL_HEIGHT, 0x66FFF2F8);

        graphics.drawString(this.font, "镜头微调", panelX + 8, panelY + 6, 0xFFFFEEF8, false);
        renderCameraSlider(graphics, panelX + 8, panelY + 23, "缩放", HugCameraZoom.currentHugFovScale(),
                HugCameraZoom.minHugFovScale(), HugCameraZoom.maxHugFovScale(), HugCameraZoom.zoomLabel());
        renderCameraSlider(graphics, panelX + 8, panelY + 42, "上下", HugCameraZoom.currentCameraPitchOffsetDegrees(),
                HugCameraZoom.minCameraPitchOffsetDegrees(), HugCameraZoom.maxCameraPitchOffsetDegrees(), HugCameraZoom.pitchOffsetLabel());

        int saveX = cameraPanelSaveX();
        int saveY = cameraPanelSaveY();
        boolean saveHovered = isInside(mouseX, mouseY, saveX, saveY, 34, 13);
        graphics.fill(saveX, saveY, saveX + 34, saveY + 13, saveHovered ? 0xAA70415F : 0x88563349);
        graphics.drawString(this.font, "保存", saveX + 7, saveY + 3, 0xFFFFEEF8, false);
    }

    private void renderCameraSlider(GuiGraphics graphics, int x, int y, String label,
                                    double value, double min, double max, String valueLabel) {
        graphics.drawString(this.font, label, x, y - 3, 0xFFEEDFEB, false);
        graphics.drawString(this.font, valueLabel, x + 104, y - 3, 0xFFD9C3D1, false);

        int trackX = x + 30;
        int trackY = y + 2;
        graphics.fill(trackX, trackY, trackX + CAMERA_SLIDER_WIDTH, trackY + CAMERA_SLIDER_HEIGHT, 0x77473342);
        float progress = (float) ((value - min) / Math.max(0.0001D, max - min));
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        int knobX = trackX + Math.round(progress * CAMERA_SLIDER_WIDTH);
        graphics.fill(trackX, trackY, knobX, trackY + CAMERA_SLIDER_HEIGHT, 0xAAE8AFCB);
        graphics.fill(knobX - 2, trackY - 3, knobX + 2, trackY + CAMERA_SLIDER_HEIGHT + 3, 0xFFFFEEF8);
    }

    private boolean handleCameraAdjustMouseClick(double mouseX, double mouseY) {
        int iconX = cameraPanelIconX();
        int iconY = cameraPanelIconY();
        if (isInside(mouseX, mouseY, iconX, iconY, cameraPanelIconWidth(), cameraPanelIconHeight())) {
            toggleCameraAdjustPanel();
            return true;
        }
        if (!cameraAdjustPanelOpen) {
            return false;
        }

        if (isInside(mouseX, mouseY, cameraPanelSaveX(), cameraPanelSaveY(), 34, 13)) {
            boolean saved = HugCameraZoom.savePersistentSettings();
            cameraAdjustPanelOpen = false;
            cameraSliderDragTarget = CameraSliderDragTarget.NONE;
            showDebugMessage(saved
                    ? "镜头设置已保存"
                    : "镜头设置保存失败，请检查日志");
            return true;
        }

        if (isInside(mouseX, mouseY, cameraSliderTrackX(), cameraSliderZoomTrackY() - 4,
                CAMERA_SLIDER_WIDTH, CAMERA_SLIDER_HEIGHT + 8)) {
            cameraSliderDragTarget = CameraSliderDragTarget.ZOOM;
            updateCameraSliderValue(mouseX);
            return true;
        }
        if (isInside(mouseX, mouseY, cameraSliderTrackX(), cameraSliderPitchTrackY() - 4,
                CAMERA_SLIDER_WIDTH, CAMERA_SLIDER_HEIGHT + 8)) {
            cameraSliderDragTarget = CameraSliderDragTarget.PITCH;
            updateCameraSliderValue(mouseX);
            return true;
        }

        return isInside(mouseX, mouseY, cameraPanelX(), cameraPanelY(), CAMERA_PANEL_WIDTH, CAMERA_PANEL_HEIGHT);
    }

    private void toggleCameraAdjustPanel() {
        if (cameraAdjustPanelOpen) {
            closeCameraAdjustPanelWithoutSaving();
            return;
        }

        cameraPanelSnapshotFovScale = HugCameraZoom.currentHugFovScale();
        cameraPanelSnapshotPitchOffset = HugCameraZoom.currentCameraPitchOffsetDegrees();
        cameraSliderDragTarget = CameraSliderDragTarget.NONE;
        cameraAdjustPanelOpen = true;
    }

    private void closeCameraAdjustPanelWithoutSaving() {
        if (!cameraAdjustPanelOpen) {
            return;
        }
        HugCameraZoom.setHugFovScale(cameraPanelSnapshotFovScale);
        HugCameraZoom.setCameraPitchOffsetDegrees(cameraPanelSnapshotPitchOffset);
        cameraSliderDragTarget = CameraSliderDragTarget.NONE;
        cameraAdjustPanelOpen = false;
    }

    private void updateCameraSliderValue(double mouseX) {
        double progress = (mouseX - cameraSliderTrackX()) / (double) CAMERA_SLIDER_WIDTH;
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        if (cameraSliderDragTarget == CameraSliderDragTarget.ZOOM) {
            double min = HugCameraZoom.minHugFovScale();
            double max = HugCameraZoom.maxHugFovScale();
            HugCameraZoom.setHugFovScale(min + (max - min) * progress);
            return;
        }
        if (cameraSliderDragTarget == CameraSliderDragTarget.PITCH) {
            float min = HugCameraZoom.minCameraPitchOffsetDegrees();
            float max = HugCameraZoom.maxCameraPitchOffsetDegrees();
            HugCameraZoom.setCameraPitchOffsetDegrees((float) (min + (max - min) * progress));
        }
    }

    private int cameraPanelIconX() {
        return exitButton.x1(this.width) + exitButton.widthPx(this.width);
    }

    private int cameraPanelIconY() {
        return exitButton.y1(this.height) + Math.max(0, (exitButton.heightPx(this.height) - cameraPanelIconHeight()) / 2);
    }

    private int cameraPanelIconWidth() {
        return Math.max(10, Math.round(exitButton.widthPx(this.width) * 0.72F));
    }

    private int cameraPanelIconHeight() {
        return Math.max(10, Math.round(exitButton.heightPx(this.height) * 0.72F));
    }

    private int controlButtonGapPx() {
        return 0;
    }

    private int cameraPanelX() {
        return cameraPanelIconX() + cameraPanelIconWidth() - CAMERA_PANEL_WIDTH;
    }

    private int cameraPanelY() {
        return cameraPanelIconY() - 4 - CAMERA_PANEL_HEIGHT;
    }

    private int cameraSliderTrackX() {
        return cameraPanelX() + 8 + 30;
    }

    private int cameraSliderZoomTrackY() {
        return cameraPanelY() + 23 + 2;
    }

    private int cameraSliderPitchTrackY() {
        return cameraPanelY() + 42 + 2;
    }

    private int cameraPanelSaveX() {
        return cameraPanelX() + CAMERA_PANEL_WIDTH - 42;
    }

    private int cameraPanelSaveY() {
        return cameraPanelY() + CAMERA_PANEL_HEIGHT - 19;
    }

    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * 保留旧方法名，方便 resize / debug reload 这些现有调用点继续工作。
     */
    private void refreshDialogueState() {
        refreshDialogueState(false);
    }

    /**
     * 把“新剧情系统当前帧”同步到“旧 UI 组件树”。
     *
     * <p>这是这次重构真正的核心：
     * - 新系统提供 `DialogueFrameView`
     * - 旧界面继续使用 `DialogueState + DialogueBoxComponent + DialogueOptionComponent`
     *
     * <p>这样我们既能把剧情状态机抽出去，
     * 又不会一下子推翻你现在这套已经调好的 UI 布局。
     */
    private void refreshDialogueState(boolean resetTypewriter) {
        updateDialogueContextVariables();
        DialogueFrameView frame = dialogueRuntime.currentFrame();
        /*
         * 关键修复：
         * line_start 事件是在运行时 currentFrame() 之前就已经结算好的，
         * 但旧界面之前只有“点击选项 / 手动 advance”后才会真正排空动作队列。
         *
         * 结果就是：
         * - 当前句子已经显示出来了；
         * - 但和这句绑定的低头、捂脸、镜头拉近等动作还躺在队列里没执行。
         *
         * 这里在“刷新当前可见帧”时立刻排空一次，保证台词出现的同一拍就把演出打出去。
         */
        drainScenarioActionRequests();
        if (Minecraft.getInstance().screen != this) {
            return;
        }

        // 先按当前帧重建可见选项。
        rebuildOptionComponents(frame);

        // 再把模板文本里的 `${maid}` `${player}` 替换掉。
        String speaker = dialogueRuntime.renderTemplate(frame.speaker());
        String bodyText = dialogueRuntime.renderTemplate(frame.text());
        String hintText = resolveHintText(frame);
        currentHintText = hintText;

        boolean effectiveResetTypewriter = applyStructuredDialogueFrame(frame, speaker, bodyText, hintText, resetTypewriter);

        portrait.setTexture(dialogueState.portraitTexture());
        dialogueBox
                .setSpeaker(dialogueState.speaker())
                .setFullText(dialogueState.text(), effectiveResetTypewriter)
                .setHint(dialogueState.hint());
    }

    /**
     * 同步当前 UI 选项条件所需的运行时变量。
     *
     * <p>这一步专门服务于剧情 JSON 里的 `condition`：
     * 让“可不可亲吻”“该显示哪一条替代选项”这种判断，直接在 UI 选项层完成，
     * 而不是等玩家点击后再走额外拦截。
     */
    private void updateDialogueContextVariables() {
        HugDialogueContextVariables.refresh(
                dialogueRuntime,
                targetMaidUuidForActions(),
                ModConfigs.resolveMaidAddressing(resolvePlayerName()),
                ModConfigs.resolveChildMaidAddressing(resolvePlayerName()),
                dialogueRuntime.currentNodeId()
        );
    }

    /**
     * 推进剧情。
     *
     * <p>当前逻辑分成两步：
     * 1. 如果还在打字，就先补全文字；
     * 2. 如果当前是连续文本节点，再推进到下一句/下一节点。
     *
     * <p>选择节点不允许通过点文本框偷偷往前走。
     */
    private void advanceDialogue() {
        if (!dialogueBox.isComplete()) {
            dialogueBox.revealAll();
            return;
        }

        if (giftResultDialogueActive) {
            giftResultDialogueActive = false;
            refreshDialogueState(true);
            return;
        }

        DialogueFrameView frame = dialogueRuntime.currentFrame();
        if (structuredDialogueIndex + 1 < structuredDialogueLines.size()) {
            structuredDialogueIndex++;
            applyStructuredDialogueDisplay(structuredDialogueLines.get(structuredDialogueIndex), true);
            return;
        }

        if (frame.choiceNode()) {
            return;
        }

        dialogueRuntime.advance();
        drainScenarioActionRequests();
        refreshDialogueState(true);
    }

    /**
     * Ctrl 快进：连续跳过已经显示/正在打字的剧情，直到遇到选项节点才停。
     *
     * <p>这里故意不调用 {@link #advanceDialogue()} 做循环，因为手动推进逻辑遇到选项时只是静默返回；
     * 如果外层继续循环，就很容易在同一帧里空转。这个方法把“补全文字、推进结构化分段、
     * 推进剧情节点、遇到选项停止”拆开写，行为会更像 Galgame 的按住 Ctrl 快进。
     */
    private void fastForwardDialogueUntilChoice() {
        int guard = 0;
        while (guard++ < 24) {
            if (!dialogueBox.isComplete()) {
                dialogueBox.revealAll();
            }

            if (giftResultDialogueActive) {
                giftResultDialogueActive = false;
                refreshDialogueState(true);
                continue;
            }

            DialogueFrameView frame = dialogueRuntime.currentFrame();
            if (frame.choiceNode()) {
                return;
            }

            if (structuredDialogueIndex + 1 < structuredDialogueLines.size()) {
                structuredDialogueIndex++;
                applyStructuredDialogueDisplay(structuredDialogueLines.get(structuredDialogueIndex), true);
                continue;
            }

            boolean advanced = dialogueRuntime.advance();
            drainScenarioActionRequests();
            refreshDialogueState(true);
            if (!advanced) {
                return;
            }
        }
    }

    private boolean applyStructuredDialogueFrame(DialogueFrameView frame,
                                                 String speaker,
                                                 String bodyText,
                                                 String hintText,
                                                 boolean resetTypewriter) {
        ResourceLocation portraitTexture = resolvePortraitTexture(frame);
        dialogueState.setHint(Component.literal(hintText)).setPortraitTexture(portraitTexture);

        if (frame == null) {
            structuredDialogueLines = List.of();
            structuredDialogueIndex = 0;
            structuredDialogueKey = "";
            dialogueState
                    .setSpeaker(speaker)
                    .setText(Component.literal(bodyText))
                    .setPortraitTexture(portraitTexture);
            return resetTypewriter;
        }

        if (frame.choiceNode() && (bodyText == null || bodyText.isBlank())) {
            structuredDialogueLines = List.of();
            structuredDialogueIndex = 0;
            structuredDialogueKey = frame.nodeId() + "|blank_choice";
            if (dialogueState.text() == null || dialogueState.text().isBlank()) {
                dialogueState
                        .setSpeaker(speaker)
                        .setText(Component.literal(""))
                        .setPortraitTexture(portraitTexture);
            } else {
                dialogueState.setPortraitTexture(portraitTexture);
            }
            /*
             * 空白选项节点会沿用上一句文本继续停在屏幕上。
             * 如果这里还把 resetTypewriter=true 原样传给对话框，
             * 玩家点到“最后一句 -> 选项出现”这一拍时就会把同一句再播一遍。
             *
             * 因此 blank choice 明确禁止重置打字机，文本就稳定停在最后一句。
             */
            return false;
        }

        List<StructuredDialogueLine> parsedLines = parseStructuredDialogueLines(speaker, bodyText);
        String key = frame.nodeId() + "|" + speaker + "|" + bodyText;
        if (!Objects.equals(structuredDialogueKey, key)) {
            structuredDialogueKey = key;
            structuredDialogueLines = parsedLines;
            structuredDialogueIndex = 0;
        }

        if (structuredDialogueLines.isEmpty()) {
            dialogueState
                    .setSpeaker(speaker)
                    .setText(Component.literal(bodyText))
                    .setPortraitTexture(portraitTexture);
            return resetTypewriter;
        }

        int safeIndex = Math.max(0, Math.min(structuredDialogueLines.size() - 1, structuredDialogueIndex));
        applyStructuredDialogueDisplay(structuredDialogueLines.get(safeIndex), resetTypewriter);
        return resetTypewriter;
    }

    private void applyStructuredDialogueDisplay(StructuredDialogueLine line, boolean resetTypewriter) {
        dialogueState
                .setSpeaker(line.speaker())
                .setText(Component.literal(line.text()))
                .setHint(Component.literal(currentHintText));
        dialogueBox
                .setSpeaker(dialogueState.speaker())
                .setFullText(dialogueState.text(), resetTypewriter)
                .setHint(dialogueState.hint());
    }

    private List<StructuredDialogueLine> parseStructuredDialogueLines(String fallbackSpeaker, String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return List.of();
        }

        List<StructuredDialogueLine> result = new ArrayList<>();
        String[] rawLines = bodyText.split("\\R");
        for (String rawLine : rawLines) {
            String trimmed = rawLine == null ? "" : rawLine.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = STRUCTURED_DIALOGUE_LINE.matcher(trimmed);
            if (!matcher.matches()) {
                return List.of();
            }

            String role = matcher.group(1);
            String content = stripDialogueQuotes(matcher.group(2));
            String resolvedSpeaker = switch (role) {
                case "女仆" -> safeMaidName();
                case "玩家" -> resolvePlayerName();
                case "旁白" -> "";
                default -> fallbackSpeaker;
            };
            result.add(new StructuredDialogueLine(resolvedSpeaker, content));
        }
        return result;
    }

    private String stripDialogueQuotes(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() >= 2) {
            boolean chineseQuote = trimmed.startsWith("“") && trimmed.endsWith("”");
            boolean normalQuote = trimmed.startsWith("\"") && trimmed.endsWith("\"");
            if (chineseQuote || normalQuote) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    /**
     * 根据当前剧情帧里的 choice 列表动态重建按钮。
     *
     * <p>这一步替代了原来 Java 里写死 kiss / hug / pet 的做法。
     */
    private void rebuildOptionComponents(DialogueFrameView frame) {
        options.clear();
        if (frame == null || !frame.choiceNode()) {
            return;
        }

        int index = 0;
        for (DialogueChoiceView choice : frame.choices()) {
            if (!choice.available()) {
                continue;
            }
            DialogueOptionComponent option = new DialogueOptionComponent(
                    choice.id(),
                    Component.literal(dialogueRuntime.renderTemplate(choice.title())),
                    Component.literal(dialogueRuntime.renderTemplate(choice.description()))
            ).applyTheme(theme.option, index++);

            applySpecialChoiceColors(choice.id(), option);

            option.setLocked(false);
            option.setActive(false);
            option.hidden = false;
            options.add(option);
        }
    }

    private void applySpecialChoiceColors(String choiceId, DialogueOptionComponent option) {
        if (choiceId == null || option == null) {
            return;
        }
        switch (choiceId) {
            case "confession", "confession_accept" -> option.setTextColors(
                    0xFFFFC7DE,
                    0xFFFFF3F8,
                    0xFFE89AB8,
                    0xFFFFDDEA
            );
            case "marriage", "marriage_commit_ready", "marriage_open_panel" -> option.setTextColors(
                    0xFFFFE39A,
                    0xFFFFF7D6,
                    0xFFF0C66E,
                    0xFFFFE7B2
            );
            default -> {
            }
        }
    }

    /**
     * 给不同节点类型生成更符合语义的底部提示语。
     */
    private String resolveHintText(DialogueFrameView frame) {
        if (frame == null || frame.choiceNode()) {
            return text("ui.maidmarriage.hug_action.hint_idle").getString();
        }
        return text("ui.maidmarriage.hug_action.hint_continue").getString();
    }

    /**
     * 根据当前剧情帧解析头像贴图。
     *
     * <p>如果剧情帧没有给出合法贴图，就继续回退到主题默认头像。
     */
    private ResourceLocation resolvePortraitTexture(DialogueFrameView frame) {
        ResourceLocation fallback = DialogueTheme.parseTexture(theme.portrait.texture, SOFT_SMILE);
        if (frame == null || frame.portraitTexture() == null || frame.portraitTexture().isBlank()) {
            return fallback;
        }
        return DialogueTheme.parseTexture(frame.portraitTexture(), fallback);
    }

    /**
     * 取出剧情层发出的动作语义请求，并通过白名单映射执行。
     *
     * <p>这里是“剧情系统”和“旧动作系统”之间的安全边界：
     * JSON 里只能写类似 `maidmarriage:kiss` 这样的语义 ID；
     * 真正能不能执行、执行哪个旧触发器，都由这个方法里的 switch 决定。
     *
     * <p>这样以后剧情作者可以自由排文本和触发点，
     * 但不能绕过我们直接调用任意 Java 方法。
     */
    private void drainScenarioActionRequests() {
        HugDialogueActionDispatcher.drainAndExecute(
                dialogueRuntime,
                Minecraft.getInstance(),
                targetMaidUuidForActions(),
                fixedMaidUuid,
                childInteractionMode,
                this::showDebugMessage,
                this::onClose
        );
    }

    private String compactZoomText() {
        return text("ui.maidmarriage.hug_action.zoom_prefix").getString() + HugCameraZoom.zoomLabel();
    }

    /**
     * 在渲染世界实体中找到当前被拥抱的女仆，并解析显示名。
     */
    private String resolveMaidName() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID maidUuid = targetMaidUuidForActions();
        if (minecraft.level == null || maidUuid == null) {
            return text("ui.maidmarriage.hug_action.maid_fallback").getString();
        }
        Entity entity = ClientEntityLookup.findEntity(maidUuid);
        return entity == null
                ? text("ui.maidmarriage.hug_action.maid_fallback").getString()
                : entity.getDisplayName().getString();
    }

    @Nullable
    private EntityMaid resolveTargetMaid() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID maidUuid = targetMaidUuidForActions();
        if (minecraft.level == null || maidUuid == null) {
            return null;
        }
        Entity entity = ClientEntityLookup.findEntity(maidUuid);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private String moodLabel(@Nullable EntityMaid maid) {
        if (maid == null) {
            return moodLabelFromKey(dialogueRuntime.renderTemplate("${mood}"));
        }
        return switch (MaidMoodManager.state(maid)) {
            case DEPRESSED -> text("ui.maidmarriage.hug_action.mood_state_depressed").getString();
            case GENERAL -> text("ui.maidmarriage.hug_action.mood_state_general").getString();
            case NORMAL -> text("ui.maidmarriage.hug_action.mood_state_normal").getString();
            case HAPPY -> text("ui.maidmarriage.hug_action.mood_state_happy").getString();
            case LOVE -> text("ui.maidmarriage.hug_action.mood_state_love").getString();
        };
    }

    private String relationLabel(@Nullable EntityMaid maid) {
        RelationStage stage = maid == null
                ? RelationStage.fromKey(dialogueRuntime.renderTemplate("${favor_stage}"))
                : MaidRelationshipManager.resolveStage(maid);
        return switch (stage) {
            case INITIAL -> text("ui.maidmarriage.hug_action.relation_stage_initial").getString();
            case WARM -> text("ui.maidmarriage.hug_action.relation_stage_warm").getString();
            case CLOSE -> text("ui.maidmarriage.hug_action.relation_stage_close").getString();
            case DATING -> text("ui.maidmarriage.hug_action.relation_stage_dating").getString();
            case MARRIAGE -> text("ui.maidmarriage.hug_action.relation_stage_marriage").getString();
        };
    }

    private String moodLabelFromKey(String moodKey) {
        return switch (Objects.requireNonNullElse(moodKey, "").trim().toLowerCase(Locale.ROOT)) {
            case "depressed" -> text("ui.maidmarriage.hug_action.mood_state_depressed").getString();
            case "general" -> text("ui.maidmarriage.hug_action.mood_state_general").getString();
            case "happy" -> text("ui.maidmarriage.hug_action.mood_state_happy").getString();
            case "love" -> text("ui.maidmarriage.hug_action.mood_state_love").getString();
            default -> text("ui.maidmarriage.hug_action.mood_state_normal").getString();
        };
    }

    private boolean isScreenTargetStillValid() {
        if (!childInteractionMode) {
            return HugClientState.isLocalPlayerInteracting();
        }
        return fixedMaidUuid != null
                && fixedMaidUuid.equals(ChildInteractionClientState.getLocalInteractionMaidUuid())
                && ChildInteractionClientState.isLocalPlayerInteracting();
    }

    private boolean shouldHideVanillaHud() {
        return childInteractionMode || HugClientState.isLocalPlayerInteracting();
    }

    /**
     * 给模板变量 `${maid}` 使用的安全女仆名。
     */
    private String safeMaidName() {
        return cachedMaidName == null || cachedMaidName.isBlank()
                ? text("ui.maidmarriage.hug_action.maid_fallback").getString()
                : cachedMaidName;
    }

    /**
     * 给模板变量 `${player}` 使用的玩家名。
     */
    private String resolvePlayerName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return text("ui.maidmarriage.hug_action.player_fallback").getString();
        }
        String name = minecraft.player.getName().getString();
        return name == null || name.isBlank()
                ? text("ui.maidmarriage.hug_action.player_fallback").getString()
                : name;
    }

    /**
     * 旧布局调试系统入口。
     */
    private boolean handleDebugKey(int keyCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        float step = control ? 0.1F : 0.5F;

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            debugTarget = debugTarget.next(shift ? -1 : 1);
            showDebugMessage("Current target: " + debugTarget.label);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F9) {
            boolean saved = DialogueThemeFileStore.save(theme);
            showDebugMessage(saved
                    ? "Layout saved: " + DialogueThemeFileStore.path()
                    : "Layout save failed, check log");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C && control) {
            String dump = shift ? debugAllDump() : debugTargetDump();
            Minecraft.getInstance().keyboardHandler.setClipboard(dump);
            showDebugMessage(shift
                    ? "Copied all layout params to clipboard"
                    : "Copied current target params to clipboard");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R && control) {
            this.theme = DialogueThemeLoader.load(activeThemeId);
            rebuildComponents();
            refreshDialogueState(false);
            showDebugMessage("Reloaded builtin layout, press F9 to save override");
            return true;
        }

        float dx = 0.0F;
        float dy = 0.0F;
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            dx = -step;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            dx = step;
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            dy = -step;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            dy = step;
        } else {
            return false;
        }

        applyDebugDelta(dx, dy, shift, alt);
        rebuildComponents();
        refreshDialogueState(false);
        return true;
    }

    /**
     * 把调试输入映射到具体布局块。
     */
    private void applyDebugDelta(float dx, float dy, boolean resize, boolean alternate) {
        switch (debugTarget) {
            case DIALOG_BOX -> adjustBlock(theme.dialogBox, dx, dy, resize);
            case PORTRAIT -> adjustBlock(theme.portrait, dx, dy, resize);
            case OPTION_GROUP -> adjustBlock(theme.option, dx, dy, resize);
            case CONTROL_BUTTONS -> adjustBlock(theme.controlIcon, dx, dy, resize);
            case ZOOM_LABEL -> adjustBlock(theme.zoomLabel, dx, dy, resize);
            case DIALOG_NAME -> adjustDialogName(dx, dy, resize, alternate);
            case DIALOG_BODY -> adjustDialogBody(dx, dy, resize, alternate);
            case DIALOG_HINT -> adjustDialogHint(dx, dy, resize);
            case OPTION_TEXT -> adjustOptionText(dx, dy, resize, alternate);
            case ZOOM_TEXT -> adjustZoomText(dx, dy, resize);
        }
    }

    private void adjustBlock(DialogueTheme.LayoutBlock block, float dx, float dy, boolean resize) {
        if (resize) {
            block.width = Math.max(1.0F, block.width + dx);
            block.height = Math.max(1.0F, block.height + dy);
            showDebugMessage(debugTarget.label + " resize -> width=" + fmt(block.width) + ", height=" + fmt(block.height));
        } else {
            block.x += dx;
            block.y += dy;
            showDebugMessage(debugTarget.label + " move -> x=" + fmt(block.x) + ", y=" + fmt(block.y));
        }
    }

    private void adjustDialogName(float dx, float dy, boolean resize, boolean alternate) {
        if (resize) {
            if (alternate) {
                theme.dialogBox.lineWidth = Math.max(10.0F, theme.dialogBox.lineWidth + dx);
                showDebugMessage("Dialog name lineWidth=" + fmt(theme.dialogBox.lineWidth));
            } else {
                theme.dialogBox.speakerScale = Math.max(0.3F, theme.dialogBox.speakerScale + dx * 0.05F);
                showDebugMessage("Dialog name speakerScale=" + fmt(theme.dialogBox.speakerScale));
            }
            return;
        }
        theme.dialogBox.nameX += dx;
        theme.dialogBox.nameY += dy;
        showDebugMessage("Dialog name move -> nameX=" + fmt(theme.dialogBox.nameX) + ", nameY=" + fmt(theme.dialogBox.nameY));
    }

    private void adjustDialogBody(float dx, float dy, boolean resize, boolean alternate) {
        if (resize) {
            if (alternate) {
                theme.dialogBox.lineWidth = Math.max(10.0F, theme.dialogBox.lineWidth + dx);
                showDebugMessage("Dialog body lineWidth=" + fmt(theme.dialogBox.lineWidth));
            } else {
                theme.dialogBox.textScale = Math.max(0.3F, theme.dialogBox.textScale + dx * 0.05F);
                showDebugMessage("Dialog body textScale=" + fmt(theme.dialogBox.textScale));
            }
            return;
        }
        theme.dialogBox.textX += dx;
        theme.dialogBox.textY += dy;
        showDebugMessage("Dialog body move -> textX=" + fmt(theme.dialogBox.textX) + ", textY=" + fmt(theme.dialogBox.textY));
    }

    private void adjustDialogHint(float dx, float dy, boolean resize) {
        if (resize) {
            theme.dialogBox.hintScale = Math.max(0.3F, theme.dialogBox.hintScale + dx * 0.05F);
            showDebugMessage("Dialog hintScale=" + fmt(theme.dialogBox.hintScale));
            return;
        }
        theme.dialogBox.hintX += dx;
        theme.dialogBox.hintY += dy;
        showDebugMessage("Dialog hint move -> hintX=" + fmt(theme.dialogBox.hintX) + ", hintY=" + fmt(theme.dialogBox.hintY));
    }

    private void adjustOptionText(float dx, float dy, boolean resize, boolean alternate) {
        if (resize) {
            if (alternate) {
                theme.option.descriptionScale = Math.max(0.2F, theme.option.descriptionScale + dx * 0.05F);
                showDebugMessage("Option descriptionScale=" + fmt(theme.option.descriptionScale));
            } else {
                theme.option.titleScale = Math.max(0.2F, theme.option.titleScale + dx * 0.05F);
                showDebugMessage("Option titleScale=" + fmt(theme.option.titleScale));
            }
            return;
        }
        if (alternate) {
            theme.option.gapY = Math.max(0.0F, theme.option.gapY + dy);
            showDebugMessage("Option gapY=" + fmt(theme.option.gapY));
            return;
        }
        theme.option.titleX += dx;
        theme.option.titleY += dy;
        showDebugMessage("Option title move -> titleX=" + fmt(theme.option.titleX) + ", titleY=" + fmt(theme.option.titleY));
    }

    private void adjustZoomText(float dx, float dy, boolean resize) {
        if (resize) {
            theme.zoomLabel.scale = Math.max(0.2F, theme.zoomLabel.scale + dx * 0.05F);
            showDebugMessage("Zoom label scale=" + fmt(theme.zoomLabel.scale));
            return;
        }
        theme.zoomLabel.x += dx;
        theme.zoomLabel.y += dy;
        showDebugMessage("Zoom label move -> x=" + fmt(theme.zoomLabel.x) + ", y=" + fmt(theme.zoomLabel.y));
    }

    private void renderDebugOverlay(GuiGraphics graphics) {
        if (!debugLayout) {
            if (debugMessageTicks > 0 && !debugMessage.isBlank()) {
                drawDebugLine(graphics, 8, 8, debugMessage, 0xFFEFCFDE);
            }
            return;
        }

        int x = 8;
        int y = 8;
        drawDebugLine(graphics, x, y, "Hug UI Debug  F8 toggle / F9 save / Ctrl+R reload", 0xFFFFE08A);
        y += 11;
        drawDebugLine(graphics, x, y, "Tab target; arrows move; Shift resize/scale; Alt extra; Ctrl fine; Ctrl+C copy; Ctrl+Shift+C copy all", 0xFFEFCFDE);
        y += 11;
        drawDebugLine(graphics, x, y, "Current: " + debugTarget.label, 0xFF8BFF98);
        y += 11;
        drawDebugLine(graphics, x, y, debugTargetValue(), 0xFFFFFFFF);
        if (debugMessageTicks > 0 && !debugMessage.isBlank()) {
            y += 11;
            drawDebugLine(graphics, x, y, debugMessage, 0xFF9BD7FF);
        }
        drawDebugBoxes(graphics);
    }

    private void drawDebugBoxes(GuiGraphics graphics) {
        drawBox(graphics, dialogueBox.x1(width), dialogueBox.y1(height), dialogueBox.widthPx(width), dialogueBox.heightPx(height),
                debugTarget == DebugTarget.DIALOG_BOX ? 0xFFFFFF55 : 0x88FFFFFF);
        drawBox(graphics, portrait.x1(width), portrait.y1(height), portrait.widthPx(width), portrait.heightPx(height),
                debugTarget == DebugTarget.PORTRAIT ? 0xFF55FF55 : 0x8855FF55);
        for (DialogueOptionComponent option : options) {
            drawBox(graphics, option.x1(width), option.y1(height), option.widthPx(width), option.heightPx(height),
                    debugTarget == DebugTarget.OPTION_GROUP ? 0xFFFF88CC : 0x88FF88CC);
        }
        drawBox(graphics, hideButton.x1(width), hideButton.y1(height), hideButton.widthPx(width), hideButton.heightPx(height),
                debugTarget == DebugTarget.CONTROL_BUTTONS ? 0xFF88D7FF : 0x8888D7FF);
        drawBox(graphics, exitButton.x1(width), exitButton.y1(height), exitButton.widthPx(width), exitButton.heightPx(height),
                debugTarget == DebugTarget.CONTROL_BUTTONS ? 0xFF88D7FF : 0x8888D7FF);
    }

    private void drawBox(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawDebugLine(GuiGraphics graphics, int x, int y, String line, int color) {
        graphics.fill(x - 3, y - 2, x + this.font.width(line) + 4, y + 10, 0xAA120C16);
        graphics.drawString(this.font, line, x, y, color, false);
    }

    private String debugTargetValue() {
        return switch (debugTarget) {
            case DIALOG_BOX -> blockValue(theme.dialogBox);
            case PORTRAIT -> blockValue(theme.portrait);
            case OPTION_GROUP -> blockValue(theme.option) + ", gapY=" + fmt(theme.option.gapY);
            case CONTROL_BUTTONS -> blockValue(theme.controlIcon) + ", gapX=" + fmt(theme.controlIcon.gapX);
            case ZOOM_LABEL -> blockValue(theme.zoomLabel);
            case DIALOG_NAME -> "name=(" + fmt(theme.dialogBox.nameX) + "," + fmt(theme.dialogBox.nameY)
                    + "), speakerScale=" + fmt(theme.dialogBox.speakerScale)
                    + ", lineWidth=" + fmt(theme.dialogBox.lineWidth);
            case DIALOG_BODY -> "text=(" + fmt(theme.dialogBox.textX) + "," + fmt(theme.dialogBox.textY)
                    + "), textScale=" + fmt(theme.dialogBox.textScale)
                    + ", lineWidth=" + fmt(theme.dialogBox.lineWidth);
            case DIALOG_HINT -> "hint=(" + fmt(theme.dialogBox.hintX) + "," + fmt(theme.dialogBox.hintY)
                    + "), hintScale=" + fmt(theme.dialogBox.hintScale);
            case OPTION_TEXT -> "title=(" + fmt(theme.option.titleX) + "," + fmt(theme.option.titleY)
                    + "), titleScale=" + fmt(theme.option.titleScale)
                    + ", desc=(" + fmt(theme.option.descriptionX) + "," + fmt(theme.option.descriptionY)
                    + "), descriptionScale=" + fmt(theme.option.descriptionScale);
            case ZOOM_TEXT -> "zoom=(" + fmt(theme.zoomLabel.x) + "," + fmt(theme.zoomLabel.y)
                    + "), scale=" + fmt(theme.zoomLabel.scale);
        };
    }

    private String blockValue(DialogueTheme.LayoutBlock block) {
        return "x=" + fmt(block.x) + ", y=" + fmt(block.y)
                + ", width=" + fmt(block.width) + ", height=" + fmt(block.height)
                + ", align=" + block.alignX + "/" + block.alignY;
    }

    private String debugTargetDump() {
        return switch (debugTarget) {
            case DIALOG_BOX -> "dialogBox{x=" + fmt(theme.dialogBox.x)
                    + ", y=" + fmt(theme.dialogBox.y)
                    + ", width=" + fmt(theme.dialogBox.width)
                    + ", height=" + fmt(theme.dialogBox.height)
                    + ", nameX=" + fmt(theme.dialogBox.nameX)
                    + ", nameY=" + fmt(theme.dialogBox.nameY)
                    + ", textX=" + fmt(theme.dialogBox.textX)
                    + ", textY=" + fmt(theme.dialogBox.textY)
                    + ", hintX=" + fmt(theme.dialogBox.hintX)
                    + ", hintY=" + fmt(theme.dialogBox.hintY)
                    + "}";
            case PORTRAIT -> "portrait{x=" + fmt(theme.portrait.x)
                    + ", y=" + fmt(theme.portrait.y)
                    + ", width=" + fmt(theme.portrait.width)
                    + ", height=" + fmt(theme.portrait.height)
                    + "}";
            case OPTION_GROUP -> "option{x=" + fmt(theme.option.x)
                    + ", y=" + fmt(theme.option.y)
                    + ", width=" + fmt(theme.option.width)
                    + ", height=" + fmt(theme.option.height)
                    + ", gapY=" + fmt(theme.option.gapY)
                    + "}";
            case CONTROL_BUTTONS -> "controlIcon{x=" + fmt(theme.controlIcon.x)
                    + ", y=" + fmt(theme.controlIcon.y)
                    + ", width=" + fmt(theme.controlIcon.width)
                    + ", height=" + fmt(theme.controlIcon.height)
                    + ", gapX=" + fmt(theme.controlIcon.gapX)
                    + "}";
            case ZOOM_LABEL -> "zoomLabel{x=" + fmt(theme.zoomLabel.x)
                    + ", y=" + fmt(theme.zoomLabel.y)
                    + ", width=" + fmt(theme.zoomLabel.width)
                    + ", height=" + fmt(theme.zoomLabel.height)
                    + "}";
            case DIALOG_NAME -> "dialogName{nameX=" + fmt(theme.dialogBox.nameX)
                    + ", nameY=" + fmt(theme.dialogBox.nameY)
                    + ", speakerScale=" + fmt(theme.dialogBox.speakerScale)
                    + ", lineWidth=" + fmt(theme.dialogBox.lineWidth)
                    + "}";
            case DIALOG_BODY -> "dialogBody{textX=" + fmt(theme.dialogBox.textX)
                    + ", textY=" + fmt(theme.dialogBox.textY)
                    + ", textScale=" + fmt(theme.dialogBox.textScale)
                    + ", lineWidth=" + fmt(theme.dialogBox.lineWidth)
                    + "}";
            case DIALOG_HINT -> "dialogHint{hintX=" + fmt(theme.dialogBox.hintX)
                    + ", hintY=" + fmt(theme.dialogBox.hintY)
                    + ", hintScale=" + fmt(theme.dialogBox.hintScale)
                    + "}";
            case OPTION_TEXT -> "optionText{titleX=" + fmt(theme.option.titleX)
                    + ", titleY=" + fmt(theme.option.titleY)
                    + ", titleScale=" + fmt(theme.option.titleScale)
                    + ", descriptionX=" + fmt(theme.option.descriptionX)
                    + ", descriptionY=" + fmt(theme.option.descriptionY)
                    + ", descriptionScale=" + fmt(theme.option.descriptionScale)
                    + "}";
            case ZOOM_TEXT -> "zoomText{x=" + fmt(theme.zoomLabel.x)
                    + ", y=" + fmt(theme.zoomLabel.y)
                    + ", scale=" + fmt(theme.zoomLabel.scale)
                    + "}";
        };
    }

    private String debugAllDump() {
        return String.join(System.lineSeparator(),
                "portrait{x=" + fmt(theme.portrait.x)
                        + ", y=" + fmt(theme.portrait.y)
                        + ", width=" + fmt(theme.portrait.width)
                        + ", height=" + fmt(theme.portrait.height)
                        + "}",
                "option{x=" + fmt(theme.option.x)
                        + ", y=" + fmt(theme.option.y)
                        + ", width=" + fmt(theme.option.width)
                        + ", height=" + fmt(theme.option.height)
                        + ", gapY=" + fmt(theme.option.gapY)
                        + "}",
                "controlIcon{x=" + fmt(theme.controlIcon.x)
                        + ", y=" + fmt(theme.controlIcon.y)
                        + ", width=" + fmt(theme.controlIcon.width)
                        + ", height=" + fmt(theme.controlIcon.height)
                        + ", gapX=" + fmt(theme.controlIcon.gapX)
                        + "}",
                "zoomLabel{x=" + fmt(theme.zoomLabel.x)
                        + ", y=" + fmt(theme.zoomLabel.y)
                        + ", width=" + fmt(theme.zoomLabel.width)
                        + ", height=" + fmt(theme.zoomLabel.height)
                        + "}",
                "dialogName{nameX=" + fmt(theme.dialogBox.nameX)
                        + ", nameY=" + fmt(theme.dialogBox.nameY)
                        + ", speakerScale=" + fmt(theme.dialogBox.speakerScale)
                        + ", lineWidth=" + fmt(theme.dialogBox.lineWidth)
                        + "}",
                "dialogBody{textX=" + fmt(theme.dialogBox.textX)
                        + ", textY=" + fmt(theme.dialogBox.textY)
                        + ", textScale=" + fmt(theme.dialogBox.textScale)
                        + ", lineWidth=" + fmt(theme.dialogBox.lineWidth)
                        + "}",
                "dialogHint{hintX=" + fmt(theme.dialogBox.hintX)
                        + ", hintY=" + fmt(theme.dialogBox.hintY)
                        + ", hintScale=" + fmt(theme.dialogBox.hintScale)
                        + "}",
                "optionText{titleX=" + fmt(theme.option.titleX)
                        + ", titleY=" + fmt(theme.option.titleY)
                        + ", titleScale=" + fmt(theme.option.titleScale)
                        + ", descriptionX=" + fmt(theme.option.descriptionX)
                        + ", descriptionY=" + fmt(theme.option.descriptionY)
                        + ", descriptionScale=" + fmt(theme.option.descriptionScale)
                        + "}",
                "zoomText{x=" + fmt(theme.zoomLabel.x)
                        + ", y=" + fmt(theme.zoomLabel.y)
                        + ", scale=" + fmt(theme.zoomLabel.scale)
                        + "}",
                "dialogBox{x=" + fmt(theme.dialogBox.x)
                        + ", y=" + fmt(theme.dialogBox.y)
                        + ", width=" + fmt(theme.dialogBox.width)
                        + ", height=" + fmt(theme.dialogBox.height)
                        + ", nameX=" + fmt(theme.dialogBox.nameX)
                        + ", nameY=" + fmt(theme.dialogBox.nameY)
                        + ", textX=" + fmt(theme.dialogBox.textX)
                        + ", textY=" + fmt(theme.dialogBox.textY)
                        + ", hintX=" + fmt(theme.dialogBox.hintX)
                        + ", hintY=" + fmt(theme.dialogBox.hintY)
                        + "}");
    }

    private void showDebugMessage(String message) {
        this.debugMessage = message;
        this.debugMessageTicks = 80;
    }

    private String fmt(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component text(String key) {
        return DialogueScriptManager.component(key);
    }

    private void showGiftResultDialogue(String category, String reaction) {
        giftResultDialogueActive = true;
        structuredDialogueLines = List.of();
        structuredDialogueIndex = 0;
        structuredDialogueKey = "";
        options.clear();
        // 小女仆线的送礼反馈要使用“小女仆对你的称呼”，避免回落成玩家名或成年女仆称呼。
        String playerAddressing = childInteractionMode
                ? ModConfigs.resolveChildMaidAddressing(resolvePlayerName())
                : resolvePlayerName();
        Component line = Component.translatable(giftResultDialogueKey(category, reaction), playerAddressing);
        dialogueState
                .setSpeaker(safeMaidName())
                .setText(line)
                .setHint(Component.literal(text("ui.maidmarriage.hug_action.hint_continue").getString()));
        dialogueBox
                .setSpeaker(dialogueState.speaker())
                .setFullText(dialogueState.text(), true)
                .setHint(dialogueState.hint());
    }

    private String giftResultDialogueKey(String category, String reaction) {
        String normalizedCategory = category == null ? "" : category.toLowerCase(Locale.ROOT);
        String prefix = childInteractionMode ? "ui.maidmarriage.child_gift_story." : "ui.maidmarriage.gift_story.";
        return switch (normalizedCategory) {
            case "flower" -> prefix + "flower";
            case "sweet" -> prefix + "sweet";
            case "meal" -> prefix + "meal";
            case "valuable" -> prefix + "valuable";
            case "generic" -> prefix + "generic";
            case "odd" -> prefix + "odd";
            case "offensive" -> prefix + "offensive";
            case "daily_limit" -> prefix + "daily_limit";
            default -> {
                String normalizedReaction = reaction == null ? "" : reaction.toLowerCase(Locale.ROOT);
                yield "angry".equals(normalizedReaction) || "dislike".equals(normalizedReaction)
                        ? prefix + "offensive"
                        : prefix + "generic";
            }
        };
    }

    private record StructuredDialogueLine(String speaker, String text) {
    }

    /**
     * 旧布局调试目标枚举。
     */
    private enum DebugTarget {
        DIALOG_BOX("Dialog Box"),
        PORTRAIT("Portrait"),
        OPTION_GROUP("Options"),
        CONTROL_BUTTONS("Hide/Exit Buttons"),
        ZOOM_LABEL("Zoom Area"),
        DIALOG_NAME("Name"),
        DIALOG_BODY("Body"),
        DIALOG_HINT("Hint"),
        OPTION_TEXT("Option Text"),
        ZOOM_TEXT("Zoom Text");

        private final String label;

        DebugTarget(String label) {
            this.label = label;
        }

        private DebugTarget next(int delta) {
            DebugTarget[] values = values();
            int index = (ordinal() + delta) % values.length;
            if (index < 0) {
                index += values.length;
            }
            return values[index];
        }
    }

    private enum CameraSliderDragTarget {
        NONE,
        ZOOM,
        PITCH
    }
}
