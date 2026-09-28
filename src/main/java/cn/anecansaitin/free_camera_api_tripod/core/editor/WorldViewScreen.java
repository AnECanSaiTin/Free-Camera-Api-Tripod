package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.DockLayout;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.KeyframePanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.WidgetHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 世界内查看：编辑界面之外的专属界面。
///
/// 面板全部收起，世界按原始尺寸铺满屏幕，顶部不再有任何工具条；屏幕底部是按来源界面
/// 组织的一条操作栏（播放控制 + 路径点/关键帧操作 + 返回编辑界面）。
/// 左键点击世界进入环视（锁定鼠标、移动转向、WASD 飞行），Esc 先释放环视、再按一次返回编辑界面。
///
/// 之所以独立成一个界面，而不是在主编辑器里加一个模式开关，是为了让点击判定、面板命中等
/// 边界情况只在本界面内处理，不必在复杂的主界面里到处分支。
public class WorldViewScreen extends Screen {
    private static final int BUTTON_WIDTH = 20;
    /// 底栏按钮高度：取全局统一值，与其它界面的按钮同高
    private static final int BUTTON_HEIGHT = DockLayout.TOOL_BUTTON_HEIGHT;
    private static final int BACK_BUTTON_WIDTH = 88;
    /// 按钮之间的水平与换行间隔
    private static final int BUTTON_GAP = 3;
    /// 底部操作栏的内边距
    private static final int BAR_PAD = 4;
    /// 操作栏上方提示条的高度
    private static final int HUD_HEIGHT = 14;
    /// 关键帧属性面板的默认尺寸，超出屏幕时会被压缩
    private static final int KEYFRAME_PANEL_WIDTH = 220;
    private static final int KEYFRAME_PANEL_HEIGHT = 240;

    private final EditorContext context;
    /// 返回目标：打开本界面的那个界面
    private final @Nullable Screen parent;
    /// 来源界面是否是路径编辑器：决定底栏出现哪一组路径点 / 关键帧操作
    private final boolean pathMode;
    private final ViewportTakeover takeover;
    /// 在世界里直接拖动路径点与贝塞尔控制点；与视口面板共用同一套交互
    private final PathHandleDrag handleDrag;
    private final WidgetHost playBar = new WidgetHost();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final Set<Integer> pressedKeys = new HashSet<>();
    /// 就地弹出的关键帧属性面板；为 null 表示没弹出
    private @Nullable KeyframePanel keyframePanel;
    /// 正在拖面板标题栏，以及按下时鼠标相对面板左上角的偏移
    private boolean panelDragging;
    private double panelGrabX;
    private double panelGrabY;
    /// 底部操作栏高度，按按钮行数自适应
    private int playBarHeight = BAR_PAD * 2 + BUTTON_HEIGHT;

    public WorldViewScreen(EditorContext context, @Nullable Screen parent) {
        super(EditorLang.t("world_view.title"));
        this.context = context;
        this.parent = parent;
        this.pathMode = parent instanceof PathEditorScreen;
        this.takeover = new ViewportTakeover(context);
        this.handleDrag = new PathHandleDrag(context);

        // 自由视角下相机已经由编辑器驱动，这里再同步一次当前姿态，保证进入瞬间不跳变；
        // 预览模式下播放头姿态本就由播放器给出，不做额外处理
        if (context.editor().viewMode() == CameraEditorModel.ViewMode.FREE) {
            context.syncFreePoseFromCamera();
        }
    }

    @Override
    protected void init() {
        buildPlayBar();
        context.notify(EditorLang.t("notify.world_view_entered"));
    }

    @Override
    public void removed() {
        // 离开界面时务必放开鼠标，否则光标会被一直锁住
        takeover.release();
        // 同理要把播放器的暂停态放掉，否则退出后相机继续被动画姿态驱动
        context.player().release();
        super.removed();
    }

    // region 几何

    private UiRect playBarRect() {
        return new UiRect(0, Math.max(0, height - playBarHeight), width, playBarHeight);
    }

    // endregion

    // region 绘制

    /// 不绘制背景，世界按原始尺寸铺满整屏
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    /// 世界内查看不暂停游戏：它就是用来一边看世界一边调相机的
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Draw.beginFrame();
        // 整屏查看时视窗就是整个屏幕，光标钉在屏幕正中
        takeover.tick(pressedKeys, width / 2.0, height / 2.0);

        // 环视时鼠标已被锁定，不参与 hover，避免底部按钮凭空高亮
        int hoverX = takeover.takingOver() ? -10000 : mouseX;
        int hoverY = takeover.takingOver() ? -10000 : mouseY;

        renderPlayBar(graphics, hoverX, hoverY);
        renderHud(graphics);

        // 关键帧属性面板浮在世界之上；它的右键菜单再压一层
        Draw.layer(Draw.LAYER_FLOATING);

        if (keyframePanel != null) {
            keyframePanel.render(graphics, hoverX, hoverY);
        }

        Draw.layer(Draw.LAYER_MENU);

        if (keyframePanel != null) {
            keyframePanel.renderMenu(graphics, hoverX, hoverY);
        }

        ExpressionEditorWindow expressionEditor = context.expressionEditor();

        if (expressionEditor != null) {
            expressionEditor.update(width, height);
            expressionEditor.render(graphics, mouseX, mouseY);
        }

        // 只提示鼠标当前所在那一层的文字：被属性面板或菜单盖住的文字不该弹出提示
        Draw.TruncatedText truncated = Draw.truncatedAt(mouseX, mouseY, surfaceAt(mouseX, mouseY));

        if (truncated != null) {
            Draw.tooltip(graphics, truncated.text(), mouseX, mouseY, width, height);
        }
    }

    /// 鼠标当前压在哪一层界面上：右键菜单最上，其次是就地弹出的属性面板，其余都算底栏那一层
    private int surfaceAt(double mouseX, double mouseY) {
        if (keyframePanel == null) {
            return Draw.LAYER_DOCKED;
        }

        ContextMenu menu = keyframePanel.contextMenu();

        if (menu != null && menu.contains(mouseX, mouseY)) {
            return Draw.LAYER_MENU;
        }

        return keyframePanel.rect().contains(mouseX, mouseY) ? Draw.LAYER_FLOATING : Draw.LAYER_DOCKED;
    }

    /// 属性面板能否接收输入：环视时鼠标已被锁定，点击一律让给"释放环视"
    private boolean panelInteractive() {
        return keyframePanel != null && !takeover.takingOver();
    }

    private void renderPlayBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect rect = playBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.y(), Draw.BORDER);

        for (Runnable refresher : refreshers) {
            refresher.run();
        }

        playBar.render(graphics, mouseX, mouseY);
    }

    /// 操作栏上方的提示条：左侧是状态提示或操作说明，右侧是播放状态与时间读数
    private void renderHud(GuiGraphicsExtractor graphics) {
        String readout = context.info().stateText().getString() + "  " + context.info().timeText().getString();
        int readoutX = width - 6 - Draw.font().width(readout);
        int y = playBarRect().y() - HUD_HEIGHT;

        Draw.canvas(graphics, new UiRect(0, y - 4, width, HUD_HEIGHT), 0xB0141418);

        Component status = context.statusMessage();
        Component left = status != null
                ? status
                : EditorLang.t(takeover.takingOver() ? "world_view.controlling" : "world_view.hint");
        Draw.textEllipsized(graphics, left.getString(), 6, y, Math.max(8, readoutX - 12),
                status != null ? Draw.ACCENT : Draw.TEXT_DIM);
        Draw.text(graphics, readout, readoutX, y, Draw.TEXT_DIM);
    }

    // endregion

    // region 控件

    /// 构建底部操作栏：播放控制只在关键帧编辑里出现，路径点 / 关键帧操作按来源界面分组加入。
    ///
    /// 路径编辑器进来时不放播放控制：那条流程只关心路径本身，动画什么时候走到哪一段在时间轴上排，
    /// 底栏留出位置给路径点操作。
    private void buildPlayBar() {
        refreshers.clear();
        playBar.clear();
        List<ButtonWidget> buttons = new ArrayList<>();

        if (pathMode) {
            buttons.add(barButton(Component.literal(Icons.ADD), EditorLang.t("world_view.add_node"), BUTTON_WIDTH,
                    this::addPathNode));
            buttons.add(barButton(Component.literal(Icons.REMOVE), EditorLang.t("world_view.remove_node"), BUTTON_WIDTH,
                    this::removePathNode));
            buttons.add(barButton(EditorLang.t("world_view.prev_node"), EditorLang.t("world_view.prev_node"),
                    BUTTON_WIDTH, () -> stepPathNode(-1)));
            buttons.add(barButton(EditorLang.t("world_view.next_node"), EditorLang.t("world_view.next_node"),
                    BUTTON_WIDTH, () -> stepPathNode(1)));
        } else {
            ButtonWidget playPause = barButton(Component.literal(context.player().playing() ? Icons.PAUSE : Icons.PLAY),
                    EditorLang.t("toolbar.play_pause"), BUTTON_WIDTH, this::togglePlay);
            refreshers.add(() -> playPause.label(Component.literal(context.player().playing() ? Icons.PAUSE : Icons.PLAY)));
            buttons.add(playPause);
            buttons.add(barButton(Component.literal(Icons.STOP), EditorLang.t("toolbar.stop"), BUTTON_WIDTH,
                    () -> context.player().stop()));

            buttons.add(barButton(EditorLang.t("world_view.prev_key"), EditorLang.t("world_view.prev_key"),
                    BUTTON_WIDTH, () -> stepKey(-1)));
            buttons.add(barButton(EditorLang.t("world_view.next_key"), EditorLang.t("world_view.next_key"),
                    BUTTON_WIDTH, () -> stepKey(1)));
            buttons.add(barButton(Component.literal(Icons.ADD), EditorLang.t("world_view.add_key"), BUTTON_WIDTH,
                    this::addKey));
            buttons.add(barButton(Component.literal(Icons.REMOVE), EditorLang.t("world_view.remove_key"), BUTTON_WIDTH,
                    this::removeKey));
            // 关键帧属性就地弹出：世界内查看本来就是"边看边调"的地方，退回编辑界面反而打断
            buttons.add(barButton(EditorLang.t("world_view.key_props"), EditorLang.t("world_view.key_props_hint"),
                    BUTTON_WIDTH, this::toggleKeyframePanel));
        }

        buttons.add(barButton(EditorLang.t("world_view.back"), EditorLang.t("world_view.back"), BACK_BUTTON_WIDTH,
                this::onClose));

        layoutPlayBar(buttons);
    }

    /// 按文字长度自适应宽度，图片标按钮取最小宽度
    private static ButtonWidget barButton(Component label, Component tooltip, int minWidth, Runnable action) {
        int width = Math.max(minWidth, Draw.font().width(label.getString()) + 10);
        return new ButtonWidget(new UiRect(0, 0, width, BUTTON_HEIGHT), label, action).tooltip(tooltip);
    }

    /// 排布底部按钮：一行放不下就折到下一行，栏高随行数自适应，避免按钮溢出屏幕
    private void layoutPlayBar(List<ButtonWidget> buttons) {
        int available = Math.max(24, width - BAR_PAD * 2);
        List<List<ButtonWidget>> rows = new ArrayList<>();
        List<ButtonWidget> current = new ArrayList<>();
        int used = 0;

        for (ButtonWidget button : buttons) {
            int buttonWidth = Math.min(button.rect().width(), available);
            int needed = current.isEmpty() ? buttonWidth : used + BUTTON_GAP + buttonWidth;

            if (!current.isEmpty() && needed > available) {
                rows.add(current);
                current = new ArrayList<>();
                used = 0;
            }

            button.rect(new UiRect(0, 0, buttonWidth, BUTTON_HEIGHT));
            current.add(button);
            used += current.size() == 1 ? buttonWidth : buttonWidth + BUTTON_GAP;
        }

        if (!current.isEmpty()) {
            rows.add(current);
        }

        playBarHeight = BAR_PAD * 2 + rows.size() * BUTTON_HEIGHT + (rows.size() - 1) * BUTTON_GAP;
        int y = height - playBarHeight + BAR_PAD;

        for (List<ButtonWidget> row : rows) {
            int x = BAR_PAD;

            for (ButtonWidget button : row) {
                button.rect(new UiRect(x, y, button.rect().width(), BUTTON_HEIGHT));
                playBar.add(button);
                x += button.rect().width() + BUTTON_GAP;
            }

            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
    }

    /// 就地弹出 / 收起关键帧属性面板。
    ///
    /// 世界内查看没有停靠布局，所以面板固定浮在右上角，拖标题栏可以挪开，免得挡住要看的世界；
    /// 面板本身与编辑界面里那个关键帧面板是同一个实现，选中状态、改动都直接落在同一份数据上
    private void toggleKeyframePanel() {
        if (keyframePanel != null) {
            keyframePanel = null;
            return;
        }

        KeyframePanel panel = new KeyframePanel(context);
        int panelWidth = Math.min(KEYFRAME_PANEL_WIDTH, Math.max(60, width - 12));
        int panelHeight = Math.min(KEYFRAME_PANEL_HEIGHT, Math.max(40, height - playBarHeight - HUD_HEIGHT - 12));
        panel.rect(new UiRect(width - panelWidth - 6, 6, panelWidth, panelHeight));
        keyframePanel = panel;
    }

    /// 拖动属性面板：位置夹在屏幕内，标题栏不会被拖出画面
    private void moveKeyframePanel(double x, double y) {
        if (keyframePanel == null) {
            return;
        }

        UiRect rect = keyframePanel.rect();
        int left = (int) Mth.clamp(x, 0, Math.max(0, width - rect.width()));
        int top = (int) Mth.clamp(y, 0, Math.max(0, height - rect.height()));
        keyframePanel.rect(new UiRect(left, top, rect.width(), rect.height()));
    }

    private void togglePlay() {
        // 自由视角下播放没有意义：先切回预览视角再播放，保证按钮始终有效
        if (context.editor().viewMode() == CameraEditorModel.ViewMode.FREE) {
            takeover.release();
            context.editor().viewMode(CameraEditorModel.ViewMode.PREVIEW);
        }

        context.player().toggle();
    }

    // region 路径点操作

    private void addPathNode() {
        context.recordPathNode();
    }

    private void removePathNode() {
        if (context.editor().removePathNode(context.editor().selectedPathNode().index())) {
            context.notify(EditorLang.t("notify.path_node_removed"));
        }
    }

    /// 在路径点的 0..size-1 之间循环切换选中
    private void stepPathNode(int delta) {
        int size = context.editor().path().size();

        if (size <= 0) {
            return;
        }

        int index = Math.floorMod(context.editor().selectedPathNode().index() + delta, size);
        context.editor().selectPathNode(new Selected(index, Selected.Type.NODE));
    }

    // endregion

    // region 关键帧操作

    /// 在当前选中轨道的播放头时间插入关键帧；没有选中轨道时只提示，不崩
    private void addKey() {
        AnimationTrack track = context.editor().selectedTrack();

        if (track == null) {
            context.notify(EditorLang.t("notify.no_track_selected"));
            return;
        }

        float time = context.player().time();

        if (context.editor().addKey(track, time, context.scope()) >= 0) {
            context.notify(EditorLang.t("notify.key_added", Draw.num(time, 2)));
        }
    }

    private void removeKey() {
        if (context.editor().removeSelectedKey()) {
            context.notify(EditorLang.t("notify.key_removed"));
        }
    }

    /// 在选中轨道的关键帧之间循环切换选中
    private void stepKey(int delta) {
        AnimationTrack track = context.editor().selectedTrack();
        int count = track == null ? 0 : track.keyCount();

        if (count <= 0) {
            return;
        }

        int index = context.editor().selectedKeyIndex();
        index = index < 0 ? (delta > 0 ? 0 : count - 1) : Math.floorMod(index + delta, count);
        context.editor().selectKey(index);
    }

    // endregion

    // endregion

    // region 输入

    /// 返回编辑界面：先把世界内查看里最终调整到的姿态写回编辑器，保证退回后视口画面不跳变。
    ///
    /// 自由视角下相机姿态就存在 freePose 里，从当前相机读回即可；预览模式的姿态由播放头决定，
    /// 不额外改动动画数据。
    @Override
    public void onClose() {
        // 表达式编辑窗口是模态的，留到退回之后会盖在编辑界面上，这里顺手关掉
        context.closeExpressionEditor();

        if (context.editor().viewMode() == CameraEditorModel.ViewMode.FREE) {
            context.syncFreePoseFromCamera();
        }

        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    /// 世界内查看的点击分两步：底部操作栏 -> 路径点/控制点拖拽 -> 世界本体（切换环视）
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 表达式编辑窗口是模态的：打开时点击全归它，免得点穿到底下的面板与世界
        ExpressionEditorWindow expressionEditor = context.expressionEditor();

        if (expressionEditor != null) {
            ExpressionEditorWindow current = expressionEditor;
            expressionEditor.mouseClicked(event, doubleClick);

            if (expressionEditor.finished() && context.expressionEditor() == current) {
                context.closeExpressionEditor();
            }

            return true;
        }

        // 属性面板压在世界之上：菜单 -> 标题栏拖动 -> 面板控件
        KeyframePanel panel = panelInteractive() ? keyframePanel : null;

        // 菜单打开时它优先吃掉点击（点在菜单外就是关掉菜单）
        if (panel != null && panel.menuMouseClicked(event)) {
            return true;
        }

        if (panel != null && panel.rect().contains(event.x(), event.y())) {
            if (panel.headerRect().contains(event.x(), event.y())) {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    panelDragging = true;
                    panelGrabX = event.x() - panel.rect().x();
                    panelGrabY = event.y() - panel.rect().y();
                }

                return true;
            }

            panel.mouseClicked(event, doubleClick);
            return true;
        }

        // 底部操作栏优先响应，点按钮不会误触发环视接管
        if (playBarRect().contains(event.x(), event.y())) {
            playBar.mouseClicked(event, doubleClick);
            return true;
        }

        // 路径模式下左键先尝试抓路径点 / 控制点，抓住就不接管环视，避免拖点时视角一起转
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && pathDragging()
                && handleDrag.begin(event.x(), event.y(), fullScreenRect())) {
            return true;
        }

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (takeover.takingOver()) {
                takeover.release();
            } else {
                takeover.takeOver();
            }
        }

        return true;
    }

    /// 世界铺满整屏，投影换算用的画面矩形就是整块屏幕
    private UiRect fullScreenRect() {
        return new UiRect(0, 0, width, height);
    }

    /// 是否可以拖路径点 / 控制点：路径来源总会可以；关键帧来源只在路径模式下可以
    private boolean pathDragging() {
        return pathMode || context.animation().motionMode() == CameraAnimation.MotionMode.PATH;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        // 表达式编辑窗口是模态的，拖拽不落到面板或世界上
        if (context.expressionEditor() != null) {
            return true;
        }

        if (panelDragging) {
            moveKeyframePanel(event.x() - panelGrabX, event.y() - panelGrabY);
            return true;
        }

        KeyframePanel panel = panelInteractive() ? keyframePanel : null;

        if (panel != null && panel.rect().contains(event.x(), event.y())) {
            panel.mouseDragged(event, dragX, dragY);
            return true;
        }

        if (handleDrag.dragging()) {
            handleDrag.update(event.x(), event.y(), fullScreenRect());
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (panelDragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            panelDragging = false;
            return true;
        }

        ExpressionEditorWindow expressionEditor = context.expressionEditor();

        if (expressionEditor != null) {
            ExpressionEditorWindow current = expressionEditor;
            expressionEditor.mouseReleased(event);

            if (expressionEditor.finished() && context.expressionEditor() == current) {
                context.closeExpressionEditor();
            }

            return true;
        }

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            handleDrag.cancel();
        }

        KeyframePanel panel = panelInteractive() ? keyframePanel : null;

        if (panel != null) {
            panel.mouseReleased(event);
        }

        playBar.mouseReleased(event);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ExpressionEditorWindow expressionEditor = context.expressionEditor();

        if (expressionEditor != null) {
            return expressionEditor.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        KeyframePanel panel = panelInteractive() ? keyframePanel : null;

        if (panel != null && panel.rect().contains(mouseX, mouseY)) {
            return panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 表达式编辑窗口打开时按键全部归它（Esc 关窗且不写回）
        ExpressionEditorWindow expressionEditor = context.expressionEditor();

        if (expressionEditor != null) {
            ExpressionEditorWindow current = expressionEditor;
            expressionEditor.keyPressed(event);

            if (expressionEditor.finished() && context.expressionEditor() == current) {
                context.closeExpressionEditor();
            }

            return true;
        }

        KeyframePanel panel = panelInteractive() ? keyframePanel : null;

        if (panel != null && panel.keyPressed(event)) {
            return true;
        }

        int key = event.key();

        // Esc：先释放环视，未环视时返回编辑界面
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (takeover.takingOver()) {
                takeover.release();
            } else {
                onClose();
            }

            return true;
        }

        // 环视中 WASD 属于移动键，不能拿去触发其它快捷键
        if (takeover.takingOver() && isMovementKey(key)) {
            pressedKeys.add(key);
            return true;
        }

        if (key == GLFW.GLFW_KEY_SPACE) {
            togglePlay();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        pressedKeys.remove(event.key());
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        ExpressionEditorWindow expressionEditor = context.expressionEditor();

        if (expressionEditor != null) {
            return expressionEditor.charTyped(event);
        }

        KeyframePanel panel = panelInteractive() ? keyframePanel : null;
        return panel != null && panel.charTyped(event);
    }

    private static boolean isMovementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
                || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL;
    }

    // endregion
}
