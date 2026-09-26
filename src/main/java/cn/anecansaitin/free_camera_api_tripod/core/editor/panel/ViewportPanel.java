package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CameraPose;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.PathHandleDrag;
import cn.anecansaitin.free_camera_api_tripod.core.editor.ViewportTakeover;
import cn.anecansaitin.free_camera_api_tripod.core.editor.WorldViewScreen;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.render.ViewportPipRenderer;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// 视口面板：把整帧游戏画面等比缩放进面板内显示，并可接管鼠标直接在里面操作。
///
/// 预览模式显示播放头所在帧的相机姿态；自由模式可飞行取景。
/// 内容区最上方是一行机位标签（用于保存与切换取景位置），其下是状态行与可收起的操作提示；
/// 其余操作都收进右键菜单。
public class ViewportPanel extends EditorPanel {
    public static final String ID = "viewport";

    /// 提示文本行高
    private static final int HINT_LINE_HEIGHT = 10;
    /// 可收起的提示行数
    private static final int HINT_LINES = 2;
    /// 机位标签行高度，位于内容区最上方、状态行之上。
    /// 标签自己就是按钮，行高按统一的控件高度上下各留 1 像素，与别处的按钮一样高
    private static final int TAB_ROW_HEIGHT = ROW_HEIGHT;
    /// 单个机位标签的宽度范围；标签多了就一起压窄
    private static final int TAB_MIN_WIDTH = 26;
    private static final int TAB_MAX_WIDTH = 52;
    /// 标签之间的间隙
    private static final int TAB_GAP = 1;
    /// 标签内右侧删除按钮的宽度
    private static final int TAB_CLOSE_WIDTH = 9;
    /// 标签文字的左右留白
    private static final int TAB_TEXT_PADDING = 3;
    /// 新增标签按钮的宽度
    private static final int TAB_ADD_WIDTH = 13;
    /// 机位标签数量上限
    private static final int MAX_TABS = 8;
    /// 删除按钮悬停时的底色
    private static final int TAB_CLOSE_HOVER = 0x60FF5252;

    private final EditorContext context;
    private final CameraPose poseCache = new CameraPose();
    /// 接管视窗所需的鼠标锁定与自由飞行手感
    private final ViewportTakeover takeover;
    /// 路径编辑器里拖拽贝塞尔控制点
    private final PathHandleDrag handleDrag;
    /// 画面区域顶部（状态行与提示之下）
    private int videoTop;
    /// 提示区是否收起，收起后整块高度都给预览画面
    private boolean hintsCollapsed;
    /// 是否作为路径编辑器的视口使用：决定右键菜单里出现哪一组操作
    private boolean pathEditing;
    private @Nullable ButtonWidget hintToggle;
    /// 机位标签：每个标签保存一套自由视角姿态（位置 / 旋转 / FOV），还没有姿态的标签存 null
    private final List<@Nullable CameraPose> cameraTabs = new ArrayList<>();
    /// 当前选中的机位标签下标
    private int activeTab;
    /// 控件是按多少个标签创建的；标签数量变化时下一帧重建（新增按钮的位置与可用状态都跟着标签数量走）
    private int builtTabCount = -1;

    public ViewportPanel(EditorContext context) {
        super(ID, EditorLang.t("panel.viewport"));
        this.context = context;
        this.takeover = new ViewportTakeover(context);
        this.handleDrag = new PathHandleDrag(context);
        // 至少保留一个机位标签；它一开始没有保存的姿态，切换时以当前相机姿态兜底
        cameraTabs.add(null);
    }

    @Override
    protected void layoutWidgets(UiRect content) {
        updateVideoTop();

        // 标签行占了内容区最上沿，折叠按钮挪到状态行右侧的留白里
        hintToggle = new ButtonWidget(new UiRect(content.right() - 14, content.y() + TAB_ROW_HEIGHT + 1, 13, 10),
                Component.literal(hintsCollapsed ? Icons.EXPAND : Icons.COLLAPSE), this::toggleHints);
        hintToggle.tooltip(EditorLang.t(hintsCollapsed ? "viewport.hints.show" : "viewport.hints.hide"));
        widgets.add(hintToggle);

        ButtonWidget addTab = new ButtonWidget(addTabRect(content), Component.literal(Icons.ADD), this::addTab);
        addTab.tooltip(EditorLang.t("viewport.tab.add"));
        addTab.enabled(cameraTabs.size() < MAX_TABS);
        widgets.add(addTab);
        builtTabCount = cameraTabs.size();
    }

    /// 标签数量变化后重建面板控件；只在绘制内容之前调用，此时清空控件不会打断正在派发的事件
    private void rebuildTabWidgets() {
        widgets.clear();
        layoutWidgets(contentRect());
    }

    /// 标签行占 12 像素，其下是状态行（12 像素）与提示区（可收起）；可拖路径点时多一行操作说明
    private void updateVideoTop() {
        int hints = hintsCollapsed ? 0 : hintLines() * HINT_LINE_HEIGHT + 2;
        videoTop = contentRect().y() + TAB_ROW_HEIGHT + 12 + hints;
    }

    private int hintLines() {
        return pathDragging() ? HINT_LINES + 1 : HINT_LINES;
    }

    public boolean hintsCollapsed() {
        return hintsCollapsed;
    }

    public void hintsCollapsed(boolean collapsed) {
        if (this.hintsCollapsed == collapsed) {
            return;
        }

        this.hintsCollapsed = collapsed;
        updateVideoTop();

        if (hintToggle != null) {
            hintToggle.label(Component.literal(collapsed ? Icons.EXPAND : Icons.COLLAPSE));
            hintToggle.tooltip(EditorLang.t(collapsed ? "viewport.hints.show" : "viewport.hints.hide"));
        }
    }

    private void toggleHints() {
        hintsCollapsed(!hintsCollapsed);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        // 标签数量变化后新增按钮的位置与可用状态都要跟着变，绘制前补一次重建
        if (builtTabCount != cameraTabs.size()) {
            rebuildTabWidgets();
        }

        // 提示行数随是否可拖路径点变化，每帧重算，避免模式切换后画面区域错位
        updateVideoTop();
        renderTabs(graphics, content, mouseX, mouseY);
        UiRect frame = frameRect();
        renderFrame(graphics, frame);
        Draw.textEllipsized(graphics, statusText().getString(), content.x() + 5, content.y() + TAB_ROW_HEIGHT + 3,
                content.width() - 20, takingOver() ? Draw.ACCENT : Draw.TEXT_DIM);

        if (!hintsCollapsed) {
            int y = content.y() + TAB_ROW_HEIGHT + 12;
            Draw.textEllipsized(graphics, (takingOver() ? EditorLang.t("viewport.hint.taken_over") : EditorLang.t("viewport.hint.takeover")).getString(),
                    content.x() + 5, y, content.width() - 10, Draw.TEXT_DISABLED);
            y += HINT_LINE_HEIGHT;
            Draw.textEllipsized(graphics, EditorLang.t("viewport.hint.keys").getString(), content.x() + 5, y, content.width() - 10, Draw.TEXT_DISABLED);

            if (pathDragging()) {
                y += HINT_LINE_HEIGHT;
                Draw.textEllipsized(graphics, EditorLang.t("viewport.hint.path_drag").getString(), content.x() + 5, y,
                        content.width() - 10, Draw.TEXT_DISABLED);
            }
        }

        if (handleDrag.dragging()) {
            renderHandleMarker(graphics, frame);
        }

        Draw.textEllipsized(graphics, context.info().poseText(currentPose()).getString(),
                content.x() + 5, content.bottom() - 11, content.width() - 10, Draw.TEXT_DIM);
    }

    /// 状态行只报模式；接管状态靠强调色与下方提示表达，避免一行里重复两遍
    private Component statusText() {
        return context.editor().viewMode() == CameraEditorModel.ViewMode.FREE
                ? EditorLang.t("viewport.status.free", Draw.num(context.flySpeed(), 1))
                : EditorLang.t("viewport.status.preview");
    }

    /// 画面区域：整帧游戏画面等比缩放到面板内，多余部分露出面板底色。
    ///
    /// 命中判定与拖拽标记都基于同一个矩形，因此这里算出来的几何是视口里世界画面的唯一来源。
    /// 窗口尺寸异常（尚未初始化）时返回 null，此时既不贴画面也不做任何换算。
    private @Nullable UiRect frameRect() {
        UiRect content = contentRect();
        // 上沿取 videoTop，但不越过内容区底边（面板太矮时抬到内容区上沿）。
        // videoTop 为 0 表示本次布局还没算出画面顶边（首帧），直接退到内容区上沿；
        // 这里用 min/max 组合而不是 Math.clamp：clamp 要求下限不大于上限，边界颠倒会直接抛异常
        int top = videoTop > 0 ? Math.min(videoTop, Math.max(content.y(), content.bottom() - 14)) : content.y();
        UiRect area = new UiRect(content.x() + 1, top, Math.max(1, content.width() - 2), Math.max(1, content.bottom() - 14 - top));

        int windowWidth = Minecraft.getInstance().getWindow().getWidth();
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();

        if (windowWidth <= 0 || windowHeight <= 0) {
            return null;
        }

        float scale = Math.min((float) area.width() / windowWidth, (float) area.height() / windowHeight);
        int width = Math.max(1, Math.round(windowWidth * scale));
        int height = Math.max(1, Math.round(windowHeight * scale));
        return new UiRect(area.centerX() - width / 2, area.centerY() - height / 2, width, height);
    }

    private void renderFrame(GuiGraphicsExtractor graphics, @Nullable UiRect frame) {
        if (frame == null) {
            return;
        }

        // 离屏抓帧后再贴回视窗：PiP 的准备阶段位于世界之后、GUI 通道之前，不会把编辑器自身拍进去
        graphics.submitPictureInPictureRenderState(new ViewportPipRenderer.State(
                frame.x(), frame.y(), frame.right(), frame.bottom(), 1.0f, null));
    }

    /// 拖拽中的控制点：在画面上用强调色画一个十字加方框，和世界里白色的控制点方块区分开
    private void renderHandleMarker(GuiGraphicsExtractor graphics, @Nullable UiRect frame) {
        if (frame == null) {
            return;
        }

        Vector2f marker = handleDrag.screenPosition(frame);

        if (marker == null || !Float.isFinite(marker.x) || !Float.isFinite(marker.y)) {
            return;
        }

        int x = Math.round(marker.x);
        int y = Math.round(marker.y);
        Draw.hLine(graphics, x - 6, x + 7, y, Draw.ACCENT);
        Draw.vLine(graphics, x, y - 6, y + 7, Draw.ACCENT);
        Draw.border(graphics, new UiRect(x - 4, y - 4, 9, 9), Draw.ACCENT);
    }

    private CameraPose currentPose() {
        if (context.editor().viewMode() == CameraEditorModel.ViewMode.FREE) {
            return poseCache.set(context.editor().freePose());
        }

        context.player().evaluatePose(poseCache);

        // 路径为空时没有位置信息，回退到当前相机位置以免读数显示为零
        if (!poseCache.positionValid()) {
            poseCache.position().set(context.currentCameraPosition());
            poseCache.positionValid(true);
        }

        // FOV 没有关键帧时相机沿用玩家原本的 FOV，读数也换成实际值
        if (!poseCache.fovValid()) {
            poseCache.fov(context.currentCameraFov());
            poseCache.fovValid(true);
        }

        return poseCache;
    }

    private ContextMenu buildMenu() {
        boolean free = context.editor().viewMode() == CameraEditorModel.ViewMode.FREE;
        ContextMenu menu = new ContextMenu();
        menu.toggle(Icons.VIEW, EditorLang.t("viewport.preview"),
                () -> !free, () -> context.editor().viewMode(CameraEditorModel.ViewMode.PREVIEW));
        menu.toggle(Icons.VIEW, EditorLang.t("viewport.free"), () -> free, this::enterFreeView);
        menu.separator();

        if (pathEditing) {
            // 路径编辑器的视口只做路径点相关的事，录制旋转 / FOV 属于动画编辑，不放在这里
            menu.item(Icons.ADD, EditorLang.t("viewport.add_path_node"), this::addPathNode);
            menu.item(Icons.REMOVE, EditorLang.t("inspector.path.remove"), this::removeSelectedPathNode);
        } else {
            menu.item(Icons.ROTATE, EditorLang.t("viewport.capture_rotation"), this::captureRotation);
            menu.item(Icons.FOV, EditorLang.t("viewport.capture_fov"), this::captureFov);

            // 直接坐标模式下位置由三个坐标关键帧给出，取点方式也要跟着换成记录相机位置
            if (context.animation().motionMode() == CameraAnimation.MotionMode.COORDINATE) {
                menu.item(Icons.ADD, EditorLang.t("viewport.apply_recorded_position"), this::applyRecordedPosition);
            } else {
                menu.item(Icons.ADD, EditorLang.t("viewport.add_path_node"), this::addPathNode);
            }
        }

        menu.separator();
        menu.item(Icons.SPEED_UP, EditorLang.t("viewport.fly_speed_up"), () -> context.flySpeed(context.flySpeed() * 1.5f));
        menu.item(Icons.SPEED_DOWN, EditorLang.t("viewport.fly_speed_down"), () -> context.flySpeed(context.flySpeed() / 1.5f));
        menu.separator();
        menu.toggle(Icons.EXPAND, EditorLang.t("viewport.hints.toggle"), () -> !hintsCollapsed, this::toggleHints);
        menu.separator();
        menu.item(Icons.FIT, EditorLang.t("viewport.world_view"), this::openWorldView);
        return menu;
    }

    /// 作为路径编辑器的视口使用；此时菜单只保留路径点相关的操作
    public void pathEditing(boolean pathEditing) {
        this.pathEditing = pathEditing;
    }

    private void removeSelectedPathNode() {
        if (context.editor().removePathNode(context.editor().selectedPathNode().index())) {
            context.notify(EditorLang.t("notify.path_node_removed"));
        }
    }

    /// 世界内查看：切到专属的独立界面，让世界按原始尺寸铺满屏幕。
    ///
    /// 不再复用编辑界面加一个模式开关，边界情况（面板命中、按钮点击）都由新界面自己处理。
    public void openWorldView() {
        releaseViewport();
        Minecraft.getInstance().setScreen(new WorldViewScreen(context, Minecraft.getInstance().screen));
    }

    private void enterFreeView() {
        context.syncFreePoseFromCamera();
        context.editor().viewMode(CameraEditorModel.ViewMode.FREE);
    }

    private void captureRotation() {
        context.editor().captureRotation(context.player().time(), context.currentCameraRotation());
        context.notify(EditorLang.t("notify.rotation_captured"));
    }

    private void captureFov() {
        context.editor().captureFov(context.player().time(), context.currentCameraFov());
        context.notify(EditorLang.t("notify.fov_captured"));
    }

    private void addPathNode() {
        context.recordPathNode();
    }

    /// 直接坐标模式下的取点：把当前相机位置记到三个坐标通道的播放头处
    private void applyRecordedPosition() {
        context.editor().applyRecordedPosition(context.currentCameraPosition(), context.player().time());
        context.notify(EditorLang.t("notify.position_recorded"));
    }

    // region 机位标签

    /// 标签行：内容区最上方的一行，每个标签是一个机位
    private UiRect tabRowRect(UiRect content) {
        return new UiRect(content.x(), content.y(), content.width(), TAB_ROW_HEIGHT);
    }

    /// 标签宽度：按可用宽度与标签数量自适应，标签多了就一起压窄
    private int tabWidth(UiRect content) {
        int count = Math.max(1, cameraTabs.size());
        int available = content.width() - 2 - TAB_ADD_WIDTH - TAB_GAP * 2 - (count - 1) * TAB_GAP;
        return Mth.clamp(available / count, TAB_MIN_WIDTH, TAB_MAX_WIDTH);
    }

    private UiRect tabRect(UiRect content, int index) {
        int width = tabWidth(content);
        return new UiRect(content.x() + 1 + index * (width + TAB_GAP), content.y() + 1, width, TAB_ROW_HEIGHT - 2);
    }

    /// 新增按钮排在最后一个标签之后；挤不下时贴到内容区右端，保证始终点得到
    private UiRect addTabRect(UiRect content) {
        int x = content.x() + 1 + cameraTabs.size() * (tabWidth(content) + TAB_GAP);
        return new UiRect(Math.min(x, content.right() - TAB_ADD_WIDTH - 1), content.y() + 1, TAB_ADD_WIDTH, TAB_ROW_HEIGHT - 2);
    }

    /// 标签内右侧的删除热点
    private UiRect closeRect(UiRect tab) {
        return new UiRect(tab.right() - TAB_CLOSE_WIDTH, tab.y(), TAB_CLOSE_WIDTH, tab.height());
    }

    /// 鼠标落在哪个标签上；不在任何标签上时返回 -1
    private int tabIndexAt(double mouseX, double mouseY) {
        UiRect content = contentRect();

        for (int i = 0; i < cameraTabs.size(); i++) {
            if (tabRect(content, i).contains(mouseX, mouseY)) {
                return i;
            }
        }

        return -1;
    }

    /// 鼠标是否落在某个标签的删除按钮上；只剩一个标签时删除不可用
    private boolean tabCloseHit(int index, double mouseX, double mouseY) {
        return cameraTabs.size() > 1 && closeRect(tabRect(contentRect(), index)).contains(mouseX, mouseY);
    }

    private void renderTabs(GuiGraphicsExtractor graphics, UiRect content, int mouseX, int mouseY) {
        UiRect row = tabRowRect(content);
        Draw.canvas(graphics, row, Draw.PANEL_HEADER_BG);
        Draw.hLine(graphics, row.x(), row.right(), row.bottom() - 1, Draw.BORDER);

        for (int i = 0; i < cameraTabs.size(); i++) {
            UiRect tab = tabRect(content, i);
            boolean active = i == activeTab;
            Draw.button(graphics, tab, tab.contains(mouseX, mouseY), active);
            int textWidth = tab.width() - TAB_CLOSE_WIDTH - TAB_TEXT_PADDING * 2;

            if (textWidth > 0) {
                Draw.textEllipsized(graphics, EditorLang.t("viewport.tab.name", i + 1).getString(),
                        tab.x() + TAB_TEXT_PADDING, tab.centerY() - 4, textWidth, active ? Draw.TEXT : Draw.TEXT_DIM);
            }

            renderTabClose(graphics, tab, mouseX, mouseY);
        }
    }

    /// 标签右侧的删除按钮；只剩一个标签时置灰，鼠标悬停时给出提示
    private void renderTabClose(GuiGraphicsExtractor graphics, UiRect tab, int mouseX, int mouseY) {
        UiRect close = closeRect(tab);
        boolean usable = cameraTabs.size() > 1;
        boolean hovered = usable && close.contains(mouseX, mouseY);

        if (hovered) {
            Draw.canvas(graphics, close, TAB_CLOSE_HOVER);
        }

        Draw.textCentered(graphics, Icons.CLOSE, close.centerX(), tab.centerY() - 4,
                hovered ? Draw.TEXT : usable ? Draw.TEXT_DIM : Draw.TEXT_DISABLED);

        if (hovered) {
            graphics.setTooltipForNextFrame(Draw.font(), EditorLang.t("viewport.tab.remove"), mouseX, mouseY);
        }
    }

    /// 切换机位：先把当前姿态存进当前标签，再套用目标标签保存的姿态
    private void switchToTab(int index) {
        if (index < 0 || index >= cameraTabs.size() || index == activeTab) {
            return;
        }

        cameraTabs.set(activeTab, currentPoseSnapshot());
        activeTab = index;
        applyTabPose(cameraTabs.get(index));
    }

    /// 新增机位：以当前相机姿态为初始值，并切换到这个新标签
    private void addTab() {
        if (cameraTabs.size() >= MAX_TABS) {
            return;
        }

        // 新增前先把当前姿态留在原标签上，新标签也从同一个姿态起步
        CameraPose current = currentPoseSnapshot();
        cameraTabs.set(activeTab, current);
        cameraTabs.add(current == null ? null : new CameraPose().set(current));
        activeTab = cameraTabs.size() - 1;
    }

    /// 删除机位；只剩一个标签时不可删除
    private void removeTab(int index) {
        if (cameraTabs.size() <= 1 || index < 0 || index >= cameraTabs.size()) {
            return;
        }

        cameraTabs.remove(index);

        if (activeTab > index) {
            activeTab--;
            return;
        }

        if (activeTab == index) {
            // 删掉的是当前机位：落到前一个（或新的首个）上，并套用它的姿态
            activeTab = Math.max(0, index - 1);
            applyTabPose(cameraTabs.get(activeTab));
        }
    }

    /// 把标签保存的姿态套用到自由视角；标签还没有姿态时以当前相机姿态兜底
    private void applyTabPose(@Nullable CameraPose pose) {
        if (context.editor().viewMode() != CameraEditorModel.ViewMode.FREE) {
            context.editor().viewMode(CameraEditorModel.ViewMode.FREE);
        }

        if (pose == null) {
            context.syncFreePoseFromCamera();
            return;
        }

        context.editor().syncFreePose(pose.position(), pose.rotation(), pose.fov());
    }

    /// 当前相机姿态的快照；姿态里出现非有限值时返回 null，避免把坏值存进标签
    private @Nullable CameraPose currentPoseSnapshot() {
        Vector3f position = context.currentCameraPosition();
        Vector3f rotation = context.currentCameraRotation();
        float fov = context.currentCameraFov();

        if (!finite(position) || !finite(rotation) || !Float.isFinite(fov)) {
            return null;
        }

        return new CameraPose().set(position, rotation, fov);
    }

    private static boolean finite(Vector3f vec) {
        return Float.isFinite(vec.x()) && Float.isFinite(vec.y()) && Float.isFinite(vec.z());
    }

    /// 标签的右键菜单：只放删除，点标签时的机位切换靠左键
    private ContextMenu buildTabMenu(int index) {
        ContextMenu menu = new ContextMenu();
        menu.item(Icons.REMOVE, EditorLang.t("viewport.tab.remove"), () -> removeTab(index));
        return menu;
    }

    // endregion

    // region 交互

    @Override
    protected boolean contentMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 标签行在接管区域之上：点在这里只切机位 / 删机位，不接管视窗。
        // 接管视窗时鼠标已被锁定，屏幕会把点击先用来释放接管，此处不会再收到事件
        if (tabRowRect(contentRect()).contains(event.x(), event.y())) {
            int tab = tabIndexAt(event.x(), event.y());

            // 右键：只剩一个标签时删除不可用，此时等同于在视口上右键
            if (event.button() == 1) {
                openMenu(tab >= 0 && cameraTabs.size() > 1 ? buildTabMenu(tab) : buildMenu(), event.x(), event.y());
                return true;
            }

            if (event.button() == 0 && tab >= 0) {
                // 标签右侧的 × 删除该机位，标签其余位置切换机位
                if (tabCloseHit(tab, event.x(), event.y())) {
                    removeTab(tab);
                } else {
                    switchToTab(tab);
                }
            }

            return true;
        }

        // 左键点击接管视窗：锁定鼠标，之后移动鼠标即可转视角
        if (event.button() == 0) {
            // 先给路径点 / 控制点拖拽一次机会：抓住就不接管视角，避免拖点时视角一起转
            if (pathDragging() && handleDrag.begin(event.x(), event.y(), frameOrEmpty())) {
                return true;
            }

            takeOver();
            return true;
        }

        // 右键打开操作菜单
        if (event.button() == 1) {
            openMenu(buildMenu(), event.x(), event.y());
            return true;
        }

        return true;
    }

    /// 视口里是否可以拖路径点 / 控制点：路径编辑器的视口总是可以；
    /// 关键帧编辑器的视口只在路径模式下可以（直接坐标模式没有路径节点可拖）。
    private boolean pathDragging() {
        return pathEditing || context.animation().motionMode() == CameraAnimation.MotionMode.PATH;
    }

    @Override
    protected boolean contentMouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!handleDrag.dragging()) {
            return false;
        }

        // 拖拽中鼠标移出画面矩形也要继续跟随，因此这里只吞掉拖动事件、不做命中限制
        handleDrag.update(event.x(), event.y(), frameOrEmpty());
        return true;
    }

    /// 画面矩形；尚未算出时给一个零尺寸矩形，让依赖长度的换算自行放弃
    private UiRect frameOrEmpty() {
        UiRect frame = frameRect();
        return frame == null ? new UiRect(0, 0, 0, 0) : frame;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // 松手即结束控制点拖拽；面板基类没有内容区的松手回调，所以在这里收尾
        if (event.button() == 0) {
            handleDrag.cancel();
        }

        return super.mouseReleased(event);
    }

    @Override
    protected boolean contentKeyPressed(KeyEvent event) {
        // Esc 取消拖拽，且不再继续冒泡到界面上（否则会顺手关掉界面）
        if (handleDrag.dragging() && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            handleDrag.cancel();
            return true;
        }

        return false;
    }

    @Override
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 未接管视窗时不响应滚轮，避免顺手滚动就改掉了相机坐标
        if (!takingOver()) {
            return false;
        }

        context.editor().dolly((float) scrollY * 1.5f);
        return true;
    }

    // endregion

    // region 视窗接管

    public boolean takingOver() {
        return takeover.takingOver();
    }

    /// 由屏幕每帧驱动：鼠标转视角 + WASD 飞行；未接管时什么也不做。
    ///
    /// 同时把视窗画面中心交给接管器，让它把光标钉在视窗正中。
    public void tickTakeover(Set<Integer> pressedKeys) {
        UiRect frame = frameRect();

        if (frame == null) {
            takeover.tick(pressedKeys, Double.NaN, Double.NaN);
        } else {
            takeover.tick(pressedKeys, frame.centerX(), frame.centerY());
        }
    }

    /// 接管视窗：锁定鼠标，移动鼠标即可转动视角
    public void takeOver() {
        takeover.takeOver();
    }

    /// 释放接管，恢复鼠标；同时丢掉可能还在进行的控制点拖拽
    public void releaseViewport() {
        handleDrag.cancel();
        takeover.release();
    }

    // endregion
}
