package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.EditorConfig;
import cn.anecansaitin.free_camera_api_tripod.api.animation.EvaluateMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.WeightedMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationCodec;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationFiles;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CmdCamera;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.DockLayout;
import cn.anecansaitin.free_camera_api_tripod.api.editor.EditorUiHost;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.AnimationPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.EditorPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.GraphPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.KeyframePanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.PathNodePanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.TimelinePanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.ViewportPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.WidgetHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 相机动画编辑器主界面。
///
/// 默认采用分区可拖拽布局：视口独占一列，动画 / 路径 / 曲线图在同一列里纵向排列，
/// 关键帧独占一列，时间轴停靠在底部。面板标题栏可以拖到任意单元的左侧、右侧、上方或下方重新停靠；
/// 需要独立成窗口时，在标题栏右键选择「悬浮窗口」，再选「取消悬浮」即可回到停靠布局。
/// 背景铺一层不透明遮罩，收起面板时也不会露出游戏画面。
public class CameraEditorScreen extends Screen {
    /// 面板标题栏需要拖出这么多像素才算一次真正的拖拽，避免点一下标题栏就打乱布局
    private static final int DRAG_THRESHOLD = 3;
    /// 顶部工具栏按钮高度（取全局统一值）与最小宽度
    private static final int TOP_BUTTON_HEIGHT = DockLayout.TOOL_BUTTON_HEIGHT;
    private static final int TOP_BUTTON_MIN_WIDTH = 30;
    /// 拖拽落点提示条
    private static final int DROP_INDICATOR = 0xFF4EA1FF;

    private final EditorContext context;
    private final DockLayout layout = new DockLayout();
    private final ViewportPanel viewportPanel;
    private final GraphPanel graphPanel;
    private final AnimationPanel animationPanel;
    private final KeyframePanel keyframePanel;
    private final PathNodePanel pathNodePanel;
    private final TimelinePanel timelinePanel;
    private final WidgetHost fileBar = new WidgetHost();
    private final List<EditorPanel> panels = new ArrayList<>();
    private @Nullable ContextMenu fileMenu;
    /// 顶栏菜单按钮的矩形，键为菜单语言键；菜单已打开时鼠标移上去立即切换到该菜单
    private final Map<String, UiRect> menuButtons = new LinkedHashMap<>();
    /// 当前展开的顶栏菜单键；没有展开时为 null
    private @Nullable String openMenuKey;
    /// 当前动画对应的本地文件；新建或从存档读取时为 null，「保存」据此决定是直接写回还是转为另存为
    private java.nio.file.@Nullable Path animationFile;
    /// 撤销 / 重做用的动画快照栈；由渲染循环每帧喂入最新内容
    private final EditorHistory history = new EditorHistory();
    /// 复制出的关键帧；为空表示还没复制过
    private @Nullable KeyClip clipboard;

    private int dragSplitter = -1;
    private boolean dragHorizontalSplitter;
    private int dragStackSplitter = -1;
    /// 拖拽中的面板：可能是停靠面板（重排），也可能是悬浮窗口（移动位置）
    private @Nullable EditorPanel dragPanel;
    /// 拖拽开始时该面板是否为悬浮窗口
    private boolean dragPanelFloating;
    /// 是否已经超过拖拽阈值，没有超过就只当普通点击处理
    private boolean dragMoved;
    private double dragStartX;
    private double dragStartY;
    private double dragGrabX;
    private double dragGrabY;
    /// 当前拖拽的落点；为空表示保持在原处
    private DockLayout.@Nullable DropTarget dropTarget;
    /// 正在拖右下角缩放的悬浮窗口
    private @Nullable EditorPanel dragResizePanel;
    private double lastMouseX;
    private double lastMouseY;

    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean firstInit = true;

    public CameraEditorScreen() {
        this(createContext());
    }

    public CameraEditorScreen(EditorContext context) {
        super(Component.empty());
        this.context = context;
        this.viewportPanel = new ViewportPanel(context);
        this.graphPanel = new GraphPanel(context);
        this.animationPanel = new AnimationPanel(context);
        this.keyframePanel = new KeyframePanel(context);
        this.pathNodePanel = new PathNodePanel(context);
        this.timelinePanel = new TimelinePanel(context);

        // 默认布局：视口 / 「动画 + 路径 + 曲线图」/ 关键帧 三列，时间轴在底部
        layout.addColumn(viewportPanel, 0.30f);
        layout.addColumn(animationPanel, 0.40f);
        layout.stackUnder(animationPanel, pathNodePanel, 0.34f);
        layout.stackUnder(animationPanel, graphPanel, 0.66f);
        layout.addColumn(keyframePanel, 0.30f);
        layout.bottom(timelinePanel);
        // 记住初始结构，供文件菜单的「重置布局」恢复
        layout.captureDefaults();

        panels.add(viewportPanel);
        panels.add(graphPanel);
        panels.add(animationPanel);
        panels.add(keyframePanel);
        panels.add(pathNodePanel);
        panels.add(timelinePanel);
    }

    @Override
    protected void init() {
        if (firstInit) {
            context.syncFreePoseFromCamera();
            firstInit = false;
            restoreLayout();
        }

        layout.update(width, height);
        buildFileBar();
    }

    @Override
    public void removed() {
        persistLayout();
        viewportPanel.releaseViewport();
        context.editor().viewMode(CameraEditorModel.ViewMode.PREVIEW);
        // 关闭编辑器后不再接管相机，否则视角会停在暂停时的动画姿态上（播放中除外）
        context.player().release();
        super.removed();
    }

    // region 布局持久化

    /// 打开编辑器时恢复上次的窗口划分，避免每次都要重新拖一遍
    private void restoreLayout() {
        layout.restore(EditorConfig.LAYOUT.get());
        layout.bottomWeight(EditorConfig.LAYOUT_BOTTOM_HEIGHT.get().floatValue());
        Set<String> collapsed = new HashSet<>(List.of(EditorConfig.LAYOUT_COLLAPSED.get().split(",")));

        for (EditorPanel panel : panels) {
            panel.collapsed(collapsed.contains(panel.id()));
        }

        viewportPanel.hintsCollapsed(EditorConfig.VIEWPORT_HINTS_COLLAPSED.get());
    }

    private void persistLayout() {
        EditorConfig.LAYOUT.set(layout.serialize());
        EditorConfig.LAYOUT_BOTTOM_HEIGHT.set((double) layout.bottomWeight());

        List<String> collapsed = new ArrayList<>();

        for (EditorPanel panel : panels) {
            if (panel.collapsed()) {
                collapsed.add(panel.id());
            }
        }

        EditorConfig.LAYOUT_COLLAPSED.set(String.join(",", collapsed));
        EditorConfig.VIEWPORT_HINTS_COLLAPSED.set(viewportPanel.hintsCollapsed());
        EditorConfig.save();
    }

    // endregion

    // region 渲染

    /// 不绘制任何背景，世界画面由下面的遮罩层统一盖住
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    /// 编辑期间暂停世界：避免世界在作者编辑时继续变化，同时也不会因持续存档而打扰。
    /// 动画预览与播放使用真实时间推进，因此仍然可以正常预览。
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Draw.beginFrame();
        // 每帧喂一次当前动画内容，内部按时间节流并只在内容变化时压栈
        history.capture(context.animation());
        // 先铺一层不透明底：面板收起或留缝时露出的也是界面底色，而不是游戏画面
        graphics.fill(0, 0, width, height, Draw.SCREEN_BG);

        layout.update(width, height);
        viewportPanel.tickTakeover(pressedKeys);

        // 视窗接管中鼠标已被锁定，屏蔽面板 hover 以免误高亮
        int hoverX = viewportPanel.takingOver() ? -10000 : mouseX;
        int hoverY = viewportPanel.takingOver() ? -10000 : mouseY;

        for (EditorPanel panel : layout.dockedPanels()) {
            panel.render(graphics, hoverX, hoverY);
        }

        renderSplitters(graphics, hoverX, hoverY);
        renderDragFeedback(graphics);

        // 悬浮窗口画在停靠面板与分隔条之上，但文件栏仍保持可见
        for (EditorPanel panel : layout.floatingPanels()) {
            panel.render(graphics, hoverX, hoverY);
        }

        renderFileBar(graphics, hoverX, hoverY);

        // 右键菜单最后绘制，保证盖在其它面板之上
        for (EditorPanel panel : panels) {
            panel.renderMenu(graphics, hoverX, hoverY);
        }

        renderOverlays(graphics, mouseX, mouseY);

        // 二次确认弹窗必须盖在所有东西之上
        ConfirmDialog dialog = context.dialog();

        if (dialog != null) {
            dialog.update(width, height);
            dialog.render(graphics, mouseX, mouseY);
        }
    }

    /// 浮层：文件菜单与被截断文本的悬停提示
    private void renderOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (fileMenu != null) {
            fileMenu.render(graphics, mouseX, mouseY);
        }

        Draw.TruncatedText truncated = Draw.truncatedAt(mouseX, mouseY);

        if (truncated != null) {
            Draw.tooltip(graphics, truncated.text(), mouseX, mouseY, width, height);
        }
    }

    private void renderSplitters(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < layout.verticalSplitters().size(); i++) {
            UiRect rect = layout.verticalSplitters().get(i);
            boolean active = dragSplitter == i || rect.contains(mouseX, mouseY);
            Draw.canvas(graphics, rect, active ? Draw.SPLITTER_HOVER : Draw.SPLITTER);
        }

        for (int i = 0; i < layout.stackSplitterRects().size(); i++) {
            UiRect rect = layout.stackSplitterRects().get(i);
            boolean active = dragStackSplitter == i || rect.contains(mouseX, mouseY);
            Draw.canvas(graphics, rect, active ? Draw.SPLITTER_HOVER : Draw.SPLITTER);
        }

        if (layout.hasBottomRow()) {
            UiRect horizontal = layout.horizontalSplitter();
            boolean active = dragHorizontalSplitter || horizontal.contains(mouseX, mouseY);
            Draw.canvas(graphics, horizontal, active ? Draw.SPLITTER_HOVER : Draw.SPLITTER);
        }
    }

    /// 拖拽反馈：命中停靠区时画一条插入指示条；悬浮窗口只移动位置，不参与停靠判定
    private void renderDragFeedback(GuiGraphicsExtractor graphics) {
        if (dragPanel == null || !dragMoved || dragPanelFloating || dropTarget == null) {
            return;
        }

        Draw.canvas(graphics, dropTarget.indicator(), DROP_INDICATOR);
    }

    /// 顶部文件菜单栏
    private void renderFileBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect rect = layout.fileBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.bottom() - 1, Draw.BORDER);
        fileBar.render(graphics, mouseX, mouseY);
        switchMenuOnHover(mouseX, mouseY);
    }

    /// 已经展开某个菜单时，鼠标移到另一个菜单按钮上立即展开它（不必先点一下关掉）
    private void switchMenuOnHover(int mouseX, int mouseY) {
        if (fileMenu == null) {
            return;
        }

        for (Map.Entry<String, UiRect> entry : menuButtons.entrySet()) {
            if (!entry.getValue().contains(mouseX, mouseY)) {
                continue;
            }

            if (!entry.getKey().equals(openMenuKey)) {
                openTopMenu(entry.getKey(), entry.getValue());
            }

            return;
        }
    }

    private void buildFileBar() {
        fileBar.clear();
        menuButtons.clear();
        int[] x = {4};

        for (String key : new String[]{"menu.file", "menu.edit", "menu.view", "menu.playback", "menu.help"}) {
            Component label = EditorLang.t(key);
            int buttonWidth = Math.max(TOP_BUTTON_MIN_WIDTH, Draw.font().width(label) + 12);
            UiRect rect = new UiRect(x[0], (DockLayout.FILE_BAR_HEIGHT - TOP_BUTTON_HEIGHT) / 2, buttonWidth, TOP_BUTTON_HEIGHT);
            fileBar.add(new ButtonWidget(rect, label, () -> openTopMenu(key, rect)));
            menuButtons.put(key, rect);
            x[0] += buttonWidth + 2;
        }

        // 右侧只留关闭：世界内查看与工具窗口都收进「视图」菜单，省得误点跳走
        int y = (DockLayout.FILE_BAR_HEIGHT - TOP_BUTTON_HEIGHT) / 2;
        UiRect close = new UiRect(width - 4 - TOP_BUTTON_HEIGHT, y, TOP_BUTTON_HEIGHT, TOP_BUTTON_HEIGHT);
        fileBar.add(new ButtonWidget(close, Component.literal(Icons.CLOSE), this::onClose).tooltip(EditorLang.t("toolbar.close")));
    }

    private void openTopMenu(String key, UiRect anchor) {
        ContextMenu menu = switch (key) {
            case "menu.file" -> buildFileMenu();
            case "menu.edit" -> buildEditMenu();
            case "menu.view" -> buildViewMenu();
            case "menu.playback" -> buildPlaybackMenu();
            default -> buildHelpMenu();
        };

        fileMenu = menu.at(anchor.x(), anchor.bottom() + 1, width, height);
        openMenuKey = key;
    }

    /// 工具窗口菜单：每个面板一项，勾选表示它当前在布局里。
    /// 关掉的面板直接从布局中移除，再次勾选时以浮动窗口的形式回来。
    private ContextMenu buildToolWindowMenu() {
        ContextMenu menu = new ContextMenu();

        for (EditorPanel panel : panels) {
            menu.toggle(Icons.VIEW, panel.title(), () -> layout.containsPanel(panel), () -> togglePanelWindow(panel));
        }

        return menu;
    }

    private void togglePanelWindow(EditorPanel panel) {
        if (layout.containsPanel(panel)) {
            layout.closePanel(panel);
        } else {
            layout.openFloating(panel);
        }
    }

    /// 文件菜单：打开 / 保存 / 另存为各是一个二级菜单，每个下面都有「本地文件」与「存档」两条路线
    private ContextMenu buildFileMenu() {
        return new ContextMenu()
                .item("", EditorLang.t("menu.file.new"), this::notImplemented)
                .separator()
                .submenu("", EditorLang.t("menu.file.open"),
                        new ContextMenu()
                                .item("", EditorLang.t("menu.file.local"), this::openAnimationFile)
                                .item("", EditorLang.t("menu.file.storage"), this::openStorageAnimation))
                .submenu("", EditorLang.t("menu.file.save"),
                        new ContextMenu()
                                .item("", EditorLang.t("menu.file.local"), this::saveAnimation)
                                .item("", EditorLang.t("menu.file.storage"), this::saveAnimationToStorage))
                .submenu("", EditorLang.t("menu.file.save_as"),
                        new ContextMenu()
                                .item("", EditorLang.t("menu.file.local"), this::saveAnimationAs)
                                .item("", EditorLang.t("menu.file.storage"), this::saveAnimationToStorage))
                .separator()
                .item("", EditorLang.t("menu.file.storage_manage"), this::openStorageManager)
                .separator()
                .item("", EditorLang.t("menu.file.reset_layout"), layout::resetLayout)
                .separator()
                .item("", EditorLang.t("menu.file.exit"), this::onClose);
    }

    // region 存读档

    /// 打开：用文件浏览界面挑一个动画文件（只列 .animation.json）
    private void openAnimationFile() {
        FileBrowserScreen.open(false, AnimationFiles.animationDir(), null, AnimationFiles.ANIMATION_SUFFIX, file -> {
            if (applyAnimation(AnimationFiles.loadAnimationFrom(file), AnimationFiles.stem(file))) {
                // 记住文件，之后「保存」就能直接写回它
                animationFile = file;
            }
        });
    }

    /// 保存：已知对应的本地文件时直接写回，还没有对应文件时转为另存为
    private void saveAnimation() {
        if (animationFile == null) {
            saveAnimationAs();
            return;
        }

        saveAnimationTo(animationFile);
    }

    /// 另存为：用文件浏览界面选文件写出
    private void saveAnimationAs() {
        FileBrowserScreen.open(true, AnimationFiles.animationDir(), context.animation().name(),
                AnimationFiles.ANIMATION_SUFFIX, this::saveAnimationTo);
    }

    private void saveAnimationTo(java.nio.file.Path file) {
        file = AnimationFiles.withSuffix(file, AnimationFiles.ANIMATION_SUFFIX);
        String name = AnimationFiles.stem(file);
        String json = AnimationCodec.animationToJson(context.animation());

        if (!AnimationFiles.saveTo(file, json)) {
            context.notify(EditorLang.t("notify.animation_save_failed", name));
            return;
        }

        // 文件名即动画名，保存后让两者保持一致
        animationFile = file;
        context.animation().name(name);
        context.notify(EditorLang.t("notify.animation_saved", name));
    }

    /// 保存到存档：先列出存档里已有的全部动画（同名会先确认覆盖），选定名字后写入
    private void saveAnimationToStorage() {
        if (AnimationSavedData.get() == null) {
            context.notify(EditorLang.t("notify.no_storage"));
            return;
        }

        StorageBrowserScreen.open(true, false, context.animation().name(), this::saveAnimationToStorage);
    }

    private void saveAnimationToStorage(String name) {
        AnimationSavedData.saveAnimation(name, AnimationCodec.animationToJson(context.animation()));
        context.animation().name(name);
        context.notify(EditorLang.t("notify.animation_saved", name));
    }

    /// 打开存档里的动画：列出存档内已有的全部动画供选择
    private void openStorageAnimation() {
        if (AnimationSavedData.get() == null) {
            context.notify(EditorLang.t("notify.no_storage"));
            return;
        }

        StorageBrowserScreen.open(false, false, null, name -> loadAnimation(name, true));
    }

    /// 存档数据管理：查看并删除存档里保存的动画与路径
    private void openStorageManager() {
        if (AnimationSavedData.get() == null) {
            context.notify(EditorLang.t("notify.no_storage"));
            return;
        }

        StorageManagerScreen.open();
    }

    private void loadAnimation(String name, boolean fromStorage) {
        applyAnimation(fromStorage ? AnimationSavedData.loadAnimation(name) : AnimationFiles.loadAnimation(name), name);
    }

    /// 读取成功返回 true；失败时保留当前动画与它的本地文件对应关系
    private boolean applyAnimation(@Nullable String json, String name) {
        CameraAnimation loaded = json == null ? null : AnimationCodec.animationFromJson(json);

        if (loaded == null) {
            context.notify(EditorLang.t("notify.animation_load_failed", name));
            return false;
        }

        // 动画实例被播放器与各处持有，只能原地替换内容；随后清掉指向旧轨道的选中状态
        context.animation().copyFrom(loaded);
        // 换了一份动画，本地文件的对应关系随之失效；知道来源文件的调用方在成功后再补上
        animationFile = null;
        context.editor().selectTrack(null);
        context.editor().selectKey(-1);
        context.editor().selectPathNode(new Selected(0, Selected.Type.NODE));
        context.editor().pathReplaced();
        context.player().stop();
        context.notify(EditorLang.t("notify.animation_loaded", name));
        // 路径模式的动画不带路径本身（路径是独立保存的），读完先问这条路径从哪来
        context.choosePathAfterLoad();
        return true;
    }

    // endregion

    private ContextMenu buildEditMenu() {
        return new ContextMenu()
                .item("", EditorLang.t("menu.edit.undo"), this::undo)
                .item("", EditorLang.t("menu.edit.redo"), this::redo)
                .separator()
                .item("", EditorLang.t("menu.edit.copy"), this::copySelectedKey)
                .item("", EditorLang.t("menu.edit.paste"), this::pasteKey)
                .item("", EditorLang.t("menu.edit.delete_key"), this::deleteSelectedKey);
    }

    // region 编辑菜单

    /// 撤销：回退到上一个快照，并清掉指向旧数据的选中状态
    private void undo() {
        if (!history.undo(context.animation())) {
            context.notify(EditorLang.t("notify.nothing_to_undo"));
            return;
        }

        clearSelectionAfterRestore();
        context.notify(EditorLang.t("notify.undo_done"));
    }

    /// 重做：前进到下一个快照，并清掉指向旧数据的选中状态
    private void redo() {
        if (!history.redo(context.animation())) {
            context.notify(EditorLang.t("notify.nothing_to_redo"));
            return;
        }

        clearSelectionAfterRestore();
        context.notify(EditorLang.t("notify.redo_done"));
    }

    /// 撤销 / 重做后动画内容被整体替换，原先选中的轨道与关键帧下标都可能失效，统一清空
    private void clearSelectionAfterRestore() {
        context.editor().selectTrack(null);
        context.editor().selectKey(-1);
        context.editor().selectPathNode(new Selected(0, Selected.Type.NODE));
        context.editor().pathReplaced();
    }

    /// 复制：把当前选中的关键帧连同其轨道 id 与全部插值参数放进剪贴板
    private void copySelectedKey() {
        TrackKey selected = context.editor().selectedKey();
        AnimationTrack track = context.editor().selectedTrack();

        if (track == null || !(selected instanceof Keyframe keyframe)) {
            context.notify(EditorLang.t("notify.no_key_selected"));
            return;
        }

        clipboard = new KeyClip(track.id(), keyframe.time(), keyframe.value(),
                keyframe.inTangent(), keyframe.outTangent(), keyframe.inWeight(), keyframe.outWeight(),
                keyframe.evaluateMode(), keyframe.weightedMode());
        context.notify(EditorLang.t("notify.key_copied"));
    }

    /// 粘贴：把剪贴板里的关键帧插到当前选中轨道的播放头时间，并选中新键
    private void pasteKey() {
        KeyClip clip = clipboard;

        if (clip == null) {
            context.notify(EditorLang.t("notify.clipboard_empty"));
            return;
        }

        AnimationTrack selected = context.editor().selectedTrack();
        // 优先粘到当前选中的曲线轨道；没有选中轨道就回到复制时的轨道，轨道已不存在时按 id 重新建出来
        CurveTrack target = selected instanceof CurveTrack curveTrack ? curveTrack : resolveClipTrack(clip.trackId());
        float time = context.snapTime(context.player().time());
        Keyframe key = Keyframe.create(time, clip.value())
                .inTangent(clip.inTangent())
                .outTangent(clip.outTangent())
                .inWeight(clip.inWeight())
                .outWeight(clip.outWeight())
                .evaluateMode(clip.evaluateMode())
                .weightedMode(clip.weightedMode());
        int index = target.curve().key(key);

        if (index < 0) {
            return;
        }

        context.editor().selectTrack(target.id());
        context.editor().selectKey(index);
    }

    /// 删除当前选中轨道上的当前选中关键帧
    private void deleteSelectedKey() {
        AnimationTrack track = context.editor().selectedTrack();

        if (track == null || context.editor().selectedKeyIndex() < 0 || !context.editor().removeSelectedKey()) {
            context.notify(EditorLang.t("notify.no_key_selected"));
        }
    }

    /// 剪贴板记录的轨道：不存在时新建一条同名曲线通道
    private CurveTrack resolveClipTrack(String trackId) {
        CurveTrack track = context.animation().track(trackId);
        return track != null ? track : context.animation().addChannel(trackId);
    }

    /// 复制出的关键帧内容：来源轨道 id 与关键帧的全部字段
    private record KeyClip(String trackId, float time, float value, float inTangent, float outTangent,
                           float inWeight, float outWeight, EvaluateMode evaluateMode, WeightedMode weightedMode) {
    }

    // endregion

    private ContextMenu buildViewMenu() {
        return new ContextMenu()
                .item("", EditorLang.t("menu.view.expand_all"), () -> setAllCollapsed(false))
                .separator()
                .toggle(Icons.VIEW, EditorLang.t("menu.view.dark_mode"), Draw::darkMode, Draw::toggleTheme)
                .separator()
                .submenu("", EditorLang.t("menu.view.tool_windows"), buildToolWindowMenu())
                .separator()
                .item(Icons.FIT, EditorLang.t("viewport.world_view"), viewportPanel::openWorldView)
                .separator()
                .item("", EditorLang.t("menu.file.reset_layout"), layout::resetLayout);
    }

    private ContextMenu buildPlaybackMenu() {
        return new ContextMenu()
                .item(Icons.PLAY, EditorLang.t("toolbar.play_pause"), timelinePanel::togglePlay)
                .item(Icons.STOP, EditorLang.t("toolbar.stop"), () -> context.player().stop())
                .separator()
                .toggle(Icons.SNAP, EditorLang.t("toolbar.snap"), context::snapEnabled,
                        () -> context.snapEnabled(!context.snapEnabled()))
                .separator()
                .item(Icons.FIT, EditorLang.t("timeline.fit"), timelinePanel::fitView)
                .item(Icons.ZOOM_IN, EditorLang.t("timeline.zoom_in"), timelinePanel::zoomIn)
                .item(Icons.ZOOM_OUT, EditorLang.t("timeline.zoom_out"), timelinePanel::zoomOut);
    }

    private ContextMenu buildHelpMenu() {
        return new ContextMenu()
                .item("", EditorLang.t("menu.help.shortcuts"), this::notImplemented)
                .item("", EditorLang.t("menu.help.about"), this::notImplemented);
    }

    private void notImplemented() {
        context.notify(EditorLang.t("notify.not_implemented"));
    }

    private void setAllCollapsed(boolean collapsed) {
        for (EditorPanel panel : panels) {
            panel.collapsed(collapsed);
        }
    }

    /// 标题栏右键菜单：悬浮窗口 / 取消悬浮
    private void openPanelHeaderMenu(EditorPanel panel, MouseButtonEvent event) {
        ContextMenu menu = new ContextMenu();

        if (panel.floating()) {
            menu.item(Icons.COLLAPSE, EditorLang.t("panel.float.dock"), () -> layout.dockPanel(panel));
        } else {
            menu.item(Icons.EXPAND, EditorLang.t("panel.float.detach"), () -> layout.floatPanel(panel, panel.rect()));
        }

        panel.openMenu(menu, event.x(), event.y());
    }

    // endregion

    // region 输入

    /// 清空所有面板的控件焦点。
    ///
    /// 面板拆分后各自持有自己的控件焦点，若不清空，已点击别处但仍在编辑的输入框会继续接收键盘输入，
    /// 输入就会落到错误的面板上。点击时统一清空，再由命中的面板重新聚焦即可。
    private void clearWidgetFocus() {
        for (EditorPanel panel : panels) {
            panel.widgets().focus(null);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        lastMouseX = event.x();
        lastMouseY = event.y();

        clearWidgetFocus();

        // 文件菜单打开时，点击先交给它
        if (fileMenu != null) {
            ContextMenu current = fileMenu;
            current.mouseClicked(event);
            // 菜单项里可能又开了一个菜单（例如读取列表），此时不能把它一起关掉；
            // 点到二级菜单的父项时也只是展开子菜单，同样要保持打开
            if (fileMenu == current && !current.keepOpen()) {
                fileMenu = null;
            }

            return true;
        }

        // 确认弹窗盖住一切，先把它处理完
        ConfirmDialog dialog = context.dialog();

        if (dialog != null) {
            ConfirmDialog current = dialog;
            dialog.mouseClicked(event, doubleClick);

            if (dialog.finished() && context.dialog() == current) {
                context.closeDialog();
            }

            return true;
        }

        // 有菜单打开时，点击先交给菜单，避免穿透到面板
        for (EditorPanel panel : panels) {
            if (panel.contextMenu() != null) {
                panel.menuMouseClicked(event);
                return true;
            }
        }

        // 视窗接管中：右键先释放接管再继续往下派发，让视口的右键菜单照常打开（接管时鼠标被锁，
        // 不这样放行就再也点不到菜单）；左键只负责释放接管，不落到别的面板上
        if (viewportPanel.takingOver()) {
            viewportPanel.releaseViewport();

            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                return true;
            }
        }

        if (layout.fileBarRect().contains(event.x(), event.y())) {
            fileBar.mouseClicked(event, doubleClick);
            return true;
        }

        // 悬浮窗口盖在所有停靠面板之上，命中优先级最高
        EditorPanel floatPanel = layout.floatingPanelAt(event.x(), event.y());

        if (floatPanel != null) {
            layout.raiseFloating(floatPanel);

            if (!floatPanel.collapsed() && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && floatPanel.resizeGripRect().contains(event.x(), event.y())) {
                dragResizePanel = floatPanel;
            } else if (floatPanel.headerRect().contains(event.x(), event.y())) {
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    openPanelHeaderMenu(floatPanel, event);
                } else {
                    beginPanelDrag(floatPanel, event, doubleClick);
                }
            } else {
                // 面板内部照常交互，只有标题栏负责移动窗口
                floatPanel.mouseClicked(event, doubleClick);
            }

            return true;
        }

        int splitter = layout.verticalSplitterAt(event.x(), event.y());

        if (splitter >= 0) {
            dragSplitter = splitter;
            return true;
        }

        int stackSplitter = layout.stackSplitterAt(event.x(), event.y());

        if (stackSplitter >= 0) {
            dragStackSplitter = stackSplitter;
            return true;
        }

        if (layout.horizontalSplitterAt(event.x(), event.y())) {
            dragHorizontalSplitter = true;
            return true;
        }

        EditorPanel header = layout.panelHeaderAt(event.x(), event.y());

        if (header != null) {
            // 悬浮 / 取消悬浮都走标题栏右键菜单，拖动只负责重新停靠
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                openPanelHeaderMenu(header, event);
            } else {
                beginPanelDrag(header, event, doubleClick);
            }

            return true;
        }

        EditorPanel panel = layout.panelAt(event.x(), event.y());

        if (panel != null) {
            panel.mouseClicked(event, doubleClick);
        }

        return true;
    }

    /// 处理标题栏上的按下：折叠按钮照常折叠，其余位置记录拖拽起点。
    ///
    /// 松手时再按落点决定停靠位置，所以这里什么都不改。
    private void beginPanelDrag(EditorPanel panel, MouseButtonEvent event, boolean doubleClick) {
        // 只有标题栏右侧的箭头负责折叠，点它不参与拖拽
        if (!panel.collapseButtonRect().contains(event.x(), event.y())
                && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragPanel = panel;
            dragPanelFloating = panel.floating();
            dragMoved = false;
            dragStartX = event.x();
            dragStartY = event.y();
            dragGrabX = event.x() - panel.rect().x();
            dragGrabY = event.y() - panel.rect().y();
            dropTarget = null;
        }

        // 标题栏不属于面板内容区，需在此转发点击
        panel.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ConfirmDialog dialog = context.dialog();

        if (dialog != null) {
            ConfirmDialog current = dialog;
            dialog.mouseReleased(event);

            // 按钮的动作是在「松开」时执行的，所以关闭判断也必须在这里做一遍：
            // 只在 mouseClicked 里关的话，确认之后弹窗会继续留在屏幕上，
            // 再点一次它的按钮就会把同一个动作又执行一遍
            if (dialog.finished() && context.dialog() == current) {
                context.closeDialog();
            }

            return true;
        }

        if (dragPanel != null) {
            EditorPanel panel = dragPanel;

            // 没超过阈值就只是一次点击，布局保持原样；
            // 悬浮窗口只跟着鼠标走，落点无效的停靠面板也保持原样（改用标题栏右键菜单切换悬浮）
            if (dragMoved && !dragPanelFloating) {
                DockLayout.DropTarget target = layout.dropTargetAt(event.x(), event.y());

                if (target != null) {
                    layout.applyDrop(panel, target, panel.rect());
                }
            }

            dragPanel = null;
            dragPanelFloating = false;
            dragMoved = false;
            dropTarget = null;
        }

        dragResizePanel = null;
        dragStackSplitter = -1;
        dragSplitter = -1;
        dragHorizontalSplitter = false;

        // 接管中面板不可见，不接收事件
        if (!viewportPanel.takingOver()) {
            // 顶栏按钮的动作在松开时执行，因此这里必须把松开事件派发下去
            fileBar.mouseReleased(event);

            for (EditorPanel panel : panels) {
                panel.mouseReleased(event);
            }
        }

        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double deltaX = event.x() - lastMouseX;
        double deltaY = event.y() - lastMouseY;
        lastMouseX = event.x();
        lastMouseY = event.y();

        if (dragSplitter >= 0) {
            layout.resizeVertical(dragSplitter, Mth.floor(event.x()));
            return true;
        }

        if (dragHorizontalSplitter) {
            layout.resizeHorizontal(Mth.floor(event.y()));
            return true;
        }

        if (dragStackSplitter >= 0) {
            layout.resizeStack(dragStackSplitter, Mth.floor(event.y()));
            return true;
        }

        if (dragResizePanel != null) {
            UiRect rect = dragResizePanel.rect();
            layout.resizeFloating(dragResizePanel, Mth.floor(event.x()) - rect.x(), Mth.floor(event.y()) - rect.y());
            return true;
        }

        if (dragPanel != null) {
            if (!dragMoved && (Math.abs(event.x() - dragStartX) > DRAG_THRESHOLD || Math.abs(event.y() - dragStartY) > DRAG_THRESHOLD)) {
                dragMoved = true;
            }

            if (dragMoved) {
                if (dragPanelFloating) {
                    // 悬浮窗口贴着鼠标走，并且不参与停靠判定，避免被自动排列吸走
                    layout.moveFloating(dragPanel, (int) Math.round(event.x() - dragGrabX), (int) Math.round(event.y() - dragGrabY));
                } else {
                    dropTarget = layout.dropTargetAt(event.x(), event.y());
                }
            }

            return true;
        }

        // 接管中鼠标已锁定，拖拽不落到面板上触发误操作
        if (viewportPanel.takingOver()) {
            return true;
        }

        for (EditorPanel panel : panels) {
            panel.mouseDragged(event, deltaX, deltaY);
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        EditorPanel floating = layout.floatingPanelAt(mouseX, mouseY);

        if (floating != null) {
            return floating.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        EditorPanel panel = layout.panelAt(mouseX, mouseY);

        if (panel != null) {
            return panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // 文件菜单打开时：Esc 只关菜单
        if (fileMenu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                fileMenu = null;
            }

            return true;
        }

        // 二次确认弹窗：Esc 等同于取消
        if (context.dialog() != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                context.closeDialog();
            }

            return true;
        }

        // 有菜单打开时：Esc 只关菜单，其它按键也不落到面板上
        for (EditorPanel panel : panels) {
            if (panel.contextMenu() != null) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    panel.closeMenu();
                }

                return true;
            }
        }

        // 视窗接管中：Esc 只释放接管，不关闭编辑器；其余属于飞行的按键（W/A/S/D、Shift、Ctrl、空格）
        // 一律先由视口消费掉。接管时鼠标被锁定、点不到播放条，若让空格落到下面的播放快捷键分支，
        // 「上升」就会变成「释放接管并开始播放」，所以这里必须排在面板与快捷键之前。
        if (viewportPanel.takingOver()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                viewportPanel.releaseViewport();
                return true;
            }

            if (isMovementKey(key)) {
                pressedKeys.add(key);
                return true;
            }
        }

        // 开发用测试按键：默认关闭，只有配置里打开 dev.test_keys 后才识别
        if (EditorConfig.DEV_TEST_KEYS.get() && handleTestKey(key)) {
            return true;
        }

        for (EditorPanel panel : panels) {
            if (panel.keyPressed(event)) {
                return true;
            }
        }

        // 正在编辑文本时空格属于输入字符，不能同时触发播放/暂停快捷键。
        // 接管视窗时空格已在上面被视口当作「上升」消费掉，走不到这里。
        if (key == GLFW.GLFW_KEY_SPACE && !hasFocusedWidget()) {
            timelinePanel.togglePlay();
            return true;
        }

        if (isMovementKey(key)) {
            pressedKeys.add(key);
            return true;
        }

        return super.keyPressed(event);
    }

    /// 开发用测试按键；返回 true 表示这次按键已被消费。
    ///
    /// 这些按键存在的唯一目的是让脚本能驱动那些只能用鼠标完成的操作，
    /// 默认关闭，需要在配置里打开 {@code dev.test_keys}。
    private boolean handleTestKey(int key) {
        switch (key) {
            // 时间轴左移一秒：用来验证 0 秒处的左边界限制
            case GLFW.GLFW_KEY_F9 -> {
                context.viewStartTime(context.viewStartTime() - 1f);
                return true;
            }
            // 在鼠标位置补一次右键：脚本发不出右键，各种右键菜单都靠它触发
            case GLFW.GLFW_KEY_F10 -> {
                MouseButtonInfo buttonInfo = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0);
                mouseClicked(new MouseButtonEvent(lastMouseX, lastMouseY, buttonInfo), false);
                return true;
            }
            // 世界内查看
            case GLFW.GLFW_KEY_F12 -> {
                viewportPanel.openWorldView();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /// 是否有面板控件持有焦点（例如已经点开正在编辑的输入框）
    private boolean hasFocusedWidget() {
        for (EditorPanel panel : panels) {
            if (panel.widgets().focused() != null) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        pressedKeys.remove(event.key());
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (EditorPanel panel : panels) {
            if (panel.charTyped(event)) {
                return true;
            }
        }

        return false;
    }

    /// 视口飞行的按键集合：视窗接管时按这些键由视口驱动相机。
    ///
    /// 顺序上必须让视口先消费（见 {@link #keyPressed}）；Shift / Ctrl 也一并算进来，
    /// 否则 Ctrl 加速（{@code ViewportTakeover} 读的是这一份按键集合）永远拿不到按键。
    private static boolean isMovementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
                || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL;
    }

    // endregion

    /// 供外部（按键绑定）打开编辑器
    public static void open() {
        if (CmdCamera.INSTANCE == null) {
            return;
        }

        EditorContext context = createContext();

        // 有界面后端注册（例如用 Modern UI 渲染的附属 mod）时由它接管界面；
        // 配置里关掉「现代化界面兼容」则不询问任何后端，直接用内置界面。
        if (EditorConfig.MODERN_UI_COMPAT.get() && EditorUiHost.open(new BuiltinEditorSession(context))) {
            return;
        }

        Minecraft.getInstance().setScreen(new CameraEditorScreen(context));
    }

    /// 从当前相机命令构造编辑器上下文
    private static EditorContext createContext() {
        CmdCamera camera = CmdCamera.INSTANCE;
        return new EditorContext(camera.editor(), camera.player(), camera.animation(), camera.info());
    }
}
