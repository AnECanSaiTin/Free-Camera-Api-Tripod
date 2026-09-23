package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.EditorConfig;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationCodec;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationFiles;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CmdCamera;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.DockLayout;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.EditorPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.PathNodeDetailPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.PathNodeListPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.PathInfoPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.ViewportPanel;
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

/// 路径编辑界面：路径与路径节点增删改的独立界面。
///
/// 结构与主编辑器一致：由可自由拖动、可上下叠放、也可右键转成悬浮窗口的子面板组成，
/// 默认是「视口 / 节点列表 / 节点详情」三列，其中视口让作者一边看世界里的路径一边取点。
/// 顶栏是独立的路径工具栏：路径名称输入框，新建 / 保存 / 加载 / 添加路径点，以及最右侧的关闭按钮。
/// Esc 关闭并返回打开本界面的原界面。
public class PathEditorScreen extends Screen {
    private static final int TOP_BAR_HEIGHT = DockLayout.FILE_BAR_HEIGHT;
    private static final int DRAG_THRESHOLD = 3;
    private static final int CLOSE_BUTTON_WIDTH = 16;
    private static final int CLOSE_BUTTON_HEIGHT = 13;
    private static final int DROP_INDICATOR = 0xFF4EA1FF;
    /// 工具栏按钮高度与最小宽度
    private static final int TOOL_BUTTON_HEIGHT = 13;
    private static final int TOOL_BUTTON_MIN_WIDTH = 30;
    /// 状态提示至少要有这么宽才绘制，否则整行都让给工具栏控件
    private static final int STATUS_MIN_WIDTH = 24;
    /// 工具栏左侧的标准菜单项，与关键帧编辑器保持一致
    private static final String[] MENU_KEYS = {"menu.file", "menu.edit", "menu.view"};

    private final EditorContext context;
    /// 打开本界面的原界面，关闭时返回；没有来源界面时为 null
    private final @Nullable Screen parent;
    private final DockLayout layout = new DockLayout();
    private final ViewportPanel viewportPanel;
    private final PathNodeListPanel listPanel;
    private final PathNodeDetailPanel detailPanel;
    /// 路径信息（名称 / 节点数量 / 总长度 / 距离口径）
    private final PathInfoPanel infoPanel;
    private final List<EditorPanel> panels = new ArrayList<>();
    private final WidgetHost topBar = new WidgetHost();
    private final Set<Integer> pressedKeys = new HashSet<>();
    private UiRect closeButtonRect = new UiRect(0, 0, 0, 0);
    /// 工具栏左侧控件组的右边界，其后到关闭按钮之间的空白留给状态提示
    private int toolbarControlsRight = 0;
    /// 工具栏下拉菜单（文件 / 编辑 / 视图），与关键帧编辑器同一套交互
    private @Nullable ContextMenu topMenu;
    /// 工具栏菜单按钮的矩形，键为菜单语言键；菜单已打开时鼠标移上去立即切换到该菜单
    private final Map<String, UiRect> menuButtons = new LinkedHashMap<>();
    /// 当前展开的工具栏菜单键；没有展开时为 null
    private @Nullable String openMenuKey;
    /// 当前路径对应的本地文件；新建或从存档读取时为 null，「保存」据此决定是直接写回还是转为另存为
    private java.nio.file.@Nullable Path pathFile;

    private int dragSplitter = -1;
    private int dragStackSplitter = -1;
    private @Nullable EditorPanel dragPanel;
    private boolean dragPanelFloating;
    private boolean dragMoved;
    private double dragStartX;
    private double dragStartY;
    private double dragGrabX;
    private double dragGrabY;
    private DockLayout.@Nullable DropTarget dropTarget;
    private @Nullable EditorPanel dragResizePanel;
    private double lastMouseX;
    private double lastMouseY;

    public PathEditorScreen(EditorContext context) {
        super(EditorLang.t("path_editor.title"));
        this.context = context;
        // 构造时当前界面仍是来源界面，因为 setScreen 在参数求值之后才切换
        this.parent = Minecraft.getInstance().screen;
        this.viewportPanel = new ViewportPanel(context);
        this.listPanel = new PathNodeListPanel(context);
        this.detailPanel = new PathNodeDetailPanel(context);
        this.infoPanel = new PathInfoPanel(context);

        layout.addColumn(viewportPanel, 0.30f);
        layout.addColumn(listPanel, 0.22f);
        layout.addColumn(detailPanel, 0.28f);
        layout.addColumn(infoPanel, 0.20f);
        layout.captureDefaults();

        panels.add(viewportPanel);
        panels.add(listPanel);
        panels.add(detailPanel);
        panels.add(infoPanel);
    }

    @Override
    protected void init() {
        layout.update(width, height);
        // 本界面只做路径编辑，视口右键菜单去掉「记录旋转 / 记录 FOV」
        viewportPanel.pathEditing(true);
        buildToolBar();
    }

    /// 构建顶栏工具栏：标准菜单（文件 / 编辑 / 视图）+ 路径名称输入框 + 添加路径点 + 关闭。
    ///
    /// 结构对齐关键帧编辑器：菜单在最左，路径相关的即时操作用按钮放在右侧，
    /// 中间剩余空间留给名称输入框与状态提示；空间不足时先压缩输入框，按钮保持完整宽度。
    private void buildToolBar() {
        topBar.clear();
        topMenu = null;
        menuButtons.clear();
        closeButtonRect = new UiRect(width - 6 - CLOSE_BUTTON_WIDTH, (TOP_BAR_HEIGHT - CLOSE_BUTTON_HEIGHT) / 2,
                CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);

        int y = (TOP_BAR_HEIGHT - TOOL_BUTTON_HEIGHT) / 2;
        int x = 4;

        for (String key : MENU_KEYS) {
            int menuWidth = Math.max(TOOL_BUTTON_MIN_WIDTH, Draw.font().width(EditorLang.t(key).getString()) + 12);
            UiRect rect = new UiRect(x, y, menuWidth, TOOL_BUTTON_HEIGHT);
            topBar.add(new ButtonWidget(rect, EditorLang.t(key), () -> openTopMenu(key, rect)));
            menuButtons.put(key, rect);
            x += menuWidth + 2;
        }

        // 工具栏只放菜单与关闭：路径名在「路径信息」面板里改，添加路径点在节点列表里点
        toolbarControlsRight = x;
        topBar.add(new ButtonWidget(closeButtonRect, Component.literal(Icons.CLOSE), this::onClose)
                .tooltip(EditorLang.t("path_editor.close")));
    }

    private void openTopMenu(String key, UiRect anchor) {
        ContextMenu menu = switch (key) {
            case "menu.edit" -> buildEditMenu();
            case "menu.view" -> buildViewMenu();
            default -> buildFileMenu();
        };

        topMenu = menu.at(anchor.x(), anchor.bottom() + 1, width, height);
        openMenuKey = key;
    }

    /// 已经展开某个菜单时，鼠标移到另一个菜单按钮上立即展开它
    private void switchMenuOnHover(int mouseX, int mouseY) {
        if (topMenu == null) {
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

    /// 文件菜单：路径的新建 / 打开 / 保存 / 另存为，后三者各有本地文件与存档两条路线
    private ContextMenu buildFileMenu() {
        return new ContextMenu()
                .item("", EditorLang.t("path_editor.new"), this::newPath)
                .separator()
                .submenu("", EditorLang.t("menu.file.open"),
                        new ContextMenu()
                                .item("", EditorLang.t("menu.file.local"), this::browsePath)
                                .item("", EditorLang.t("menu.file.storage"), this::openStorageMenu))
                .submenu("", EditorLang.t("menu.file.save"),
                        new ContextMenu()
                                .item("", EditorLang.t("menu.file.local"), this::savePath)
                                .item("", EditorLang.t("menu.file.storage"), this::savePathToStorage))
                .submenu("", EditorLang.t("menu.file.save_as"),
                        new ContextMenu()
                                .item("", EditorLang.t("menu.file.local"), this::savePathAs)
                                .item("", EditorLang.t("menu.file.storage"), this::savePathToStorage))
                .separator()
                .item("", EditorLang.t("path_editor.close"), this::onClose);
    }

    /// 编辑菜单：目前与路径相关的只有删除节点，撤销 / 重做与主编辑器一样留待实现
    private ContextMenu buildEditMenu() {
        return new ContextMenu()
                .item("", EditorLang.t("menu.edit.undo"), this::notImplemented)
                .item("", EditorLang.t("menu.edit.redo"), this::notImplemented)
                .separator()
                .item(Icons.REMOVE, EditorLang.t("inspector.path.remove"), this::removePathNode);
    }

    /// 视图菜单：工具窗口开关（关掉即从布局移除，再开以浮动窗口回来）与布局操作
    private ContextMenu buildViewMenu() {
        ContextMenu toolWindows = new ContextMenu();

        for (EditorPanel panel : panels) {
            toolWindows.toggle(Icons.VIEW, panel.title(), () -> layout.containsPanel(panel),
                    () -> togglePanelWindow(panel));
        }

        return new ContextMenu()
                .item("", EditorLang.t("menu.view.expand_all"), () -> setAllCollapsed(false))
                .separator()
                .toggle(Icons.VIEW, EditorLang.t("menu.view.dark_mode"), Draw::darkMode, Draw::toggleTheme)
                .separator()
                .submenu("", EditorLang.t("menu.view.tool_windows"), toolWindows)
                .separator()
                .item("", EditorLang.t("menu.file.reset_layout"), layout::resetLayout);
    }

    private void togglePanelWindow(EditorPanel panel) {
        if (layout.containsPanel(panel)) {
            layout.closePanel(panel);
        } else {
            layout.openFloating(panel);
        }
    }

    private void setAllCollapsed(boolean collapsed) {
        for (EditorPanel panel : panels) {
            panel.collapsed(collapsed);
        }
    }

    private void notImplemented() {
        context.notify(EditorLang.t("notify.not_implemented"));
    }

    private void removePathNode() {
        if (context.editor().removePathNode(context.editor().selectedPathNode().index())) {
            context.notify(EditorLang.t("notify.path_node_removed"));
        }
    }

    // region 文件操作

    /// 新建路径：清空当前路径的节点，名称与动画数据保持不变
    private void newPath() {
        context.editor().path().clear();
        context.editor().pathReplaced();
        // 新路径还没有对应的本地文件
        pathFile = null;
    }

    /// 保存当前路径：已知对应的本地文件时直接写回，还没有对应文件时转为另存为
    private void savePath() {
        if (pathFile == null) {
            savePathAs();
            return;
        }

        savePathTo(pathFile);
    }

    /// 另存为：弹出文件浏览界面挑文件（只列 .path），写出后同时往存档留一份
    private void savePathAs() {
        FileBrowserScreen.open(true, AnimationFiles.pathDir(), context.editor().path().name(),
                AnimationFiles.PATH_SUFFIX, this::savePathTo);
    }

    private void savePathTo(java.nio.file.Path file) {
        file = AnimationFiles.withSuffix(file, AnimationFiles.PATH_SUFFIX);
        String json = AnimationCodec.pathToJson(context.editor().path());
        String name = AnimationFiles.stem(file);

        if (!AnimationFiles.saveTo(file, json)) {
            context.notify(EditorLang.t("notify.path_save_failed", name));
            return;
        }

        // 文件名即路径名，保存后让两者保持一致
        pathFile = file;
        context.editor().path().name(name);
        buildToolBar();

        if (AnimationSavedData.get() != null) {
            AnimationSavedData.savePath(name, json);
        }

        context.notify(EditorLang.t("notify.path_saved", name));
    }

    /// 保存到存档：先列出存档里已有的全部路径（同名会先确认覆盖），选定名字后写入
    private void savePathToStorage() {
        if (AnimationSavedData.get() == null) {
            context.notify(EditorLang.t("notify.no_storage"));
            return;
        }

        StorageBrowserScreen.open(true, true, context.editor().path().name(), this::savePathToStorage);
    }

    private void savePathToStorage(String name) {
        AnimationSavedData.savePath(name, AnimationCodec.pathToJson(context.editor().path()));
        context.editor().path().name(name);
        context.notify(EditorLang.t("notify.path_saved", name));
    }

    /// 用文件浏览界面挑一个路径文件（只列 .path）；选中后读取，不受目录约定限制
    private void browsePath() {
        FileBrowserScreen.open(false, AnimationFiles.pathDir(), null, AnimationFiles.PATH_SUFFIX, file -> {
            if (applyPath(AnimationFiles.loadPathFrom(file), AnimationFiles.stem(file))) {
                // 记住文件，之后「保存」就能直接写回它
                pathFile = file;
            }
        });
    }

    /// 打开存档里的路径：列出存档内已有的全部路径供选择
    private void openStorageMenu() {
        if (AnimationSavedData.get() == null) {
            context.notify(EditorLang.t("notify.no_storage"));
            return;
        }

        StorageBrowserScreen.open(false, true, null, name -> applyPath(AnimationSavedData.loadPath(name), name));
    }

    /// 读取成功返回 true；失败时保留当前路径与它的本地文件对应关系
    private boolean applyPath(@Nullable String json, String name) {
        Path loaded = json == null ? null : AnimationCodec.pathFromJson(json);

        if (loaded == null) {
            context.notify(EditorLang.t("notify.path_load_failed", name));
            return false;
        }

        loaded.name(name);
        context.editor().path(loaded);
        context.editor().pathReplaced();
        // 换了一条路径，本地文件的对应关系随之失效；知道来源文件的调用方在成功后再补上
        pathFile = null;

        context.notify(EditorLang.t("notify.path_bound", name));
        return true;
    }

    // endregion

    @Override
    public void removed() {
        viewportPanel.releaseViewport();
        super.removed();
    }

    // region 绘制

    /// 不绘制背景，界面底衬由 extractRenderState 统一铺
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    /// 暂停世界，避免编辑期间路径随动画移动
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Draw.beginFrame();
        // 铺一层不透明底：路径只在视口内可见，界面之外不再露出世界与路径
        graphics.fill(0, 0, width, height, Draw.SCREEN_BG);

        layout.update(width, height);
        viewportPanel.tickTakeover(pressedKeys);

        int hoverX = viewportPanel.takingOver() ? -10000 : mouseX;
        int hoverY = viewportPanel.takingOver() ? -10000 : mouseY;

        for (EditorPanel panel : layout.dockedPanels()) {
            panel.render(graphics, hoverX, hoverY);
        }

        renderSplitters(graphics, hoverX, hoverY);
        renderDragFeedback(graphics);

        for (EditorPanel panel : layout.floatingPanels()) {
            panel.render(graphics, hoverX, hoverY);
        }

        renderTopBar(graphics, hoverX, hoverY);

        for (EditorPanel panel : panels) {
            panel.renderMenu(graphics, hoverX, hoverY);
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
    }

    private void renderDragFeedback(GuiGraphicsExtractor graphics) {
        if (dragPanel == null || !dragMoved || dragPanelFloating || dropTarget == null) {
            return;
        }

        Draw.canvas(graphics, dropTarget.indicator(), DROP_INDICATOR);
    }

    /// 顶栏：路径工具栏与状态提示
    private void renderTopBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect rect = layout.fileBarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.bottom() - 1, Draw.BORDER);

        topBar.render(graphics, mouseX, mouseY);
        switchMenuOnHover(mouseX, mouseY);

        // 控件右侧到关闭按钮之间的空档显示状态提示，没有空档就整行让给按钮
        int statusX = toolbarControlsRight + 6;
        int statusWidth = closeButtonRect.x() - 6 - statusX;

        if (statusWidth >= STATUS_MIN_WIDTH) {
            Component status = context.statusMessage();

            if (status != null) {
                Draw.textEllipsized(graphics, status.getString(), statusX, rect.y() + 5, statusWidth, Draw.ACCENT);
            } else {
                Draw.textEllipsized(graphics, EditorLang.t("path_editor.hint").getString(), statusX, rect.y() + 5,
                        statusWidth, Draw.TEXT_DISABLED);
            }
        }

        if (topMenu != null) {
            topMenu.render(graphics, mouseX, mouseY);
        }
    }

    // endregion

    // region 输入

    private void clearWidgetFocus() {
        for (EditorPanel panel : panels) {
            panel.widgets().focus(null);
        }

        // 顶栏的名称输入框与面板控件同一套规则：点到别处即失焦并提交
        topBar.focus(null);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        lastMouseX = event.x();
        lastMouseY = event.y();
        clearWidgetFocus();

        // 工具栏菜单打开时，点击先交给它，避免穿透到面板；
        // 点到二级菜单的父项时只是展开子菜单，菜单整体保持打开
        if (topMenu != null) {
            ContextMenu current = topMenu;
            current.mouseClicked(event);

            if (topMenu == current && !current.keepOpen()) {
                topMenu = null;
            }

            return true;
        }

        for (EditorPanel panel : panels) {
            if (panel.contextMenu() != null) {
                panel.menuMouseClicked(event);
                return true;
            }
        }

        if (viewportPanel.takingOver()) {
            viewportPanel.releaseViewport();
            return true;
        }

        if (layout.fileBarRect().contains(event.x(), event.y())) {
            topBar.mouseClicked(event, doubleClick);
            return true;
        }

        EditorPanel floatPanel = layout.floatingPanelAt(event.x(), event.y());

        if (floatPanel != null) {
            layout.raiseFloating(floatPanel);

            if (!floatPanel.collapsed() && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && floatPanel.resizeGripRect().contains(event.x(), event.y())) {
                dragResizePanel = floatPanel;
            } else if (floatPanel.headerRect().contains(event.x(), event.y())) {
                beginPanelDrag(floatPanel, event, doubleClick);
            } else {
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

        EditorPanel header = layout.panelHeaderAt(event.x(), event.y());

        if (header != null) {
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

    /// 处理标题栏上的按下：折叠按钮照常折叠，其余位置记录拖拽起点
    private void beginPanelDrag(EditorPanel panel, MouseButtonEvent event, boolean doubleClick) {
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

        panel.mouseClicked(event, doubleClick);
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

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragPanel != null) {
            EditorPanel panel = dragPanel;

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

        // 工具栏按钮的动作在松开时执行，因此这里必须把松开事件派发下去
        topBar.mouseReleased(event);

        for (EditorPanel panel : panels) {
            panel.mouseReleased(event);
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
                    layout.moveFloating(dragPanel, (int) Math.round(event.x() - dragGrabX), (int) Math.round(event.y() - dragGrabY));
                } else {
                    dropTarget = layout.dropTargetAt(event.x(), event.y());
                }
            }

            return true;
        }

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

        // 工具栏菜单打开时：Esc 只关菜单
        if (topMenu != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                topMenu = null;
            }

            return true;
        }

        for (EditorPanel panel : panels) {
            if (panel.contextMenu() != null) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    panel.closeMenu();
                }

                return true;
            }
        }

        if (viewportPanel.takingOver() && key == GLFW.GLFW_KEY_ESCAPE) {
            viewportPanel.releaseViewport();
            return true;
        }

        // 开发用测试按键：默认关闭，只有配置里打开 dev.test_keys 后才识别
        if (EditorConfig.DEV_TEST_KEYS.get() && handleTestKey(key)) {
            return true;
        }

        // 工具栏里的名称输入框优先接收按键，避免输入内容触发面板快捷键
        if (topBar.keyPressed(event)) {
            return true;
        }

        for (EditorPanel panel : panels) {
            if (panel.keyPressed(event)) {
                return true;
            }
        }

        if (isMovementKey(key)) {
            pressedKeys.add(key);
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
        if (topBar.charTyped(event)) {
            return true;
        }

        for (EditorPanel panel : panels) {
            if (panel.charTyped(event)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isMovementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
                || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_SHIFT;
    }

    /// 开发用测试按键：F10 在鼠标位置补一次右键。
    ///
    /// 自动化脚本发不出右键，视口菜单只能靠它触发。
    private boolean handleTestKey(int key) {
        if (key != GLFW.GLFW_KEY_F10) {
            return false;
        }

        MouseButtonInfo buttonInfo = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0);
        mouseClicked(new MouseButtonEvent(lastMouseX, lastMouseY, buttonInfo), false);
        return true;
    }

    // endregion

    /// 关闭并返回打开本界面的原界面；没有来源界面时退回游戏
    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    /// 供外部（按键绑定）打开路径编辑器，与 {@link CameraEditorScreen#open()} 同一套上下文取法
    public static void open() {
        CmdCamera camera = CmdCamera.INSTANCE;

        if (camera == null) {
            return;
        }

        Minecraft.getInstance().setScreen(new PathEditorScreen(
                new EditorContext(camera.editor(), camera.player(), camera.animation(), camera.info())));
    }
}
