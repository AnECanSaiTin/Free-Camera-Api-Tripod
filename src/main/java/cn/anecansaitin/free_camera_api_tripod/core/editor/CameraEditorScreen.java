package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.EditorConfig;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CmdCamera;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.DockLayout;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.EditorPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.GraphPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.InspectorPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.TimelinePanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.panel.ViewportPanel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
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
import java.util.function.BooleanSupplier;

/// 相机动画编辑器主界面。
///
/// 采用四分区可拖拽布局：上排为「视口 / 曲线图 / Inspector」，下排为时间轴。
/// 背景透明，游戏世界直接作为预览画面。
public class CameraEditorScreen extends Screen {
    private static final int TOOLBAR_BUTTON_HEIGHT = 16;

    private final EditorContext context;
    private final DockLayout layout = new DockLayout();
    private final ViewportPanel viewportPanel;
    private final GraphPanel graphPanel;
    private final InspectorPanel inspectorPanel;
    private final TimelinePanel timelinePanel;
    private final WidgetHost toolbar = new WidgetHost();
    private final List<Runnable> toolbarRefreshers = new ArrayList<>();
    private final List<EditorPanel> panels = new ArrayList<>();

    private int dragSplitter = -1;
    private boolean dragHorizontalSplitter;
    private @Nullable EditorPanel dragPanelHeader;
    private double lastMouseX;
    private double lastMouseY;
    private long lastNanos;

    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean firstInit = true;

    public CameraEditorScreen() {
        super(Component.empty());
        CmdCamera camera = CmdCamera.INSTANCE;
        this.context = new EditorContext(camera.editor(), camera.player(), camera.animation(), camera.info());
        this.viewportPanel = new ViewportPanel(context);
        this.graphPanel = new GraphPanel(context);
        this.inspectorPanel = new InspectorPanel(context);
        this.timelinePanel = new TimelinePanel(context);

        layout.addColumn(viewportPanel, 0.34f);
        layout.addColumn(graphPanel, 0.36f);
        layout.addColumn(inspectorPanel, 0.30f);
        layout.bottom(timelinePanel);

        panels.add(viewportPanel);
        panels.add(graphPanel);
        panels.add(inspectorPanel);
        panels.add(timelinePanel);
    }

    @Override
    protected void init() {
        context.editor().open(true);

        if (firstInit) {
            context.syncFreePoseFromCamera();
            firstInit = false;
            restoreLayout();
        }

        layout.update(width, height);
        buildToolbar();
    }

    @Override
    public void removed() {
        persistLayout();
        viewportPanel.releaseViewport();
        context.editor().open(false);
        context.editor().viewMode(CameraEditorModel.ViewMode.PREVIEW);
        // 关闭编辑器后不再接管相机，否则视角会停在暂停时的动画姿态上（播放中除外）
        context.player().release();
        super.removed();
    }

    // region 布局持久化

    /// 打开编辑器时恢复上次的窗口划分，避免每次都要重新拖一遍
    private void restoreLayout() {
        layout.restoreColumns(EditorConfig.LAYOUT_COLUMNS.get());
        layout.bottomWeight(EditorConfig.LAYOUT_BOTTOM_HEIGHT.get().floatValue());
        Set<String> collapsed = new HashSet<>(List.of(EditorConfig.LAYOUT_COLLAPSED.get().split(",")));

        for (EditorPanel panel : panels) {
            panel.collapsed(collapsed.contains(panel.id()));
        }

        viewportPanel.hintsCollapsed(EditorConfig.VIEWPORT_HINTS_COLLAPSED.get());
    }

    private void persistLayout() {
        EditorConfig.LAYOUT_COLUMNS.set(String.join(",", layout.serializeColumns()));
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

    /// 不绘制任何背景，让游戏世界成为视口
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
        layout.update(width, height);
        tickFreeFly();

        // 视窗接管中鼠标已被锁定，屏蔽面板 hover 以免误高亮
        int hoverX = viewportPanel.takingOver() ? -10000 : mouseX;
        int hoverY = viewportPanel.takingOver() ? -10000 : mouseY;

        // 世界内查看：面板全部收起，只留工具栏、提示与右键菜单，世界按原始尺寸铺满
        if (context.worldView()) {
            viewportPanel.tickMouseLook();
            renderToolbar(graphics, hoverX, hoverY);
            renderWorldViewHint(graphics);

            for (EditorPanel panel : panels) {
                panel.renderMenu(graphics, hoverX, hoverY);
            }

            return;
        }

        for (EditorPanel column : layout.columns()) {
            column.render(graphics, hoverX, hoverY);
        }

        EditorPanel bottom = layout.bottom();

        if (bottom != null) {
            bottom.render(graphics, hoverX, hoverY);
        }

        renderSplitters(graphics, hoverX, hoverY);
        renderToolbar(graphics, hoverX, hoverY);

        // 右键菜单最后绘制，保证盖在其它面板之上
        for (EditorPanel panel : panels) {
            panel.renderMenu(graphics, hoverX, hoverY);
        }
    }

    private void renderSplitters(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < layout.verticalSplitters().size(); i++) {
            UiRect rect = layout.verticalSplitters().get(i);
            boolean active = dragSplitter == i || rect.contains(mouseX, mouseY);
            Draw.canvas(graphics, rect, active ? Draw.SPLITTER_HOVER : Draw.SPLITTER);
        }

        UiRect horizontal = layout.horizontalSplitter();
        boolean active = dragHorizontalSplitter || horizontal.contains(mouseX, mouseY);
        Draw.canvas(graphics, horizontal, active ? Draw.SPLITTER_HOVER : Draw.SPLITTER);
    }

    /// 世界内查看时屏幕底部的提示条
    private void renderWorldViewHint(GuiGraphicsExtractor graphics) {
        String hint = EditorLang.t("world_view.hint").getString();
        int textWidth = Draw.font().width(hint);
        int x = (width - textWidth) / 2;
        int y = height - 44;
        Draw.canvas(graphics, new UiRect(x - 6, y - 4, textWidth + 12, 16), 0xC0141418);
        Draw.text(graphics, hint, x, y, Draw.TEXT_DIM);
    }

    private void renderToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        UiRect rect = layout.toolbarRect();
        Draw.canvas(graphics, rect, Draw.TOOLBAR_BG);
        Draw.hLine(graphics, rect.x(), rect.right(), rect.bottom() - 1, Draw.BORDER);

        for (Runnable refresher : toolbarRefreshers) {
            refresher.run();
        }

        toolbar.render(graphics, mouseX, mouseY);

        String readout = context.info().stateText().getString() + "  " + context.info().timeText().getString();
        int readoutX = rect.right() - 6 - Draw.font().width(readout);
        Component status = context.statusMessage();

        if (status != null) {
            String statusText = status.getString();

            if (Draw.font().width(statusText) <= readoutX - 4 - (toolbarEndX + 8)) {
                Draw.text(graphics, statusText, toolbarEndX + 8, rect.y() + 7, Draw.ACCENT);
            } else {
                // 工具栏一行已被按钮与状态读数占满，通知改在工具栏下方单独占一行，避免两者重叠
                Draw.canvas(graphics, new UiRect(4, rect.bottom() + 2, Draw.font().width(statusText) + 8, 13), Draw.PANEL_HEADER_BG);
                Draw.text(graphics, statusText, 8, rect.bottom() + 6, Draw.ACCENT);
            }
        }

        Draw.text(graphics, readout, readoutX, rect.y() + 7, Draw.TEXT_DIM);
    }

    private int toolbarEndX;

    private void buildToolbar() {
        toolbar.clear();
        toolbarRefreshers.clear();
        int[] x = {6};
        int y = 3;

        ButtonWidget playPause = addToolbarButton(x, y, 20, Component.literal(Icons.PLAY), this::togglePlay,
                () -> context.player().playing(), EditorLang.t("toolbar.play_pause"));
        // 播放中把图标切成暂停，让同一个按钮同时表达两个状态
        toolbarRefreshers.add(() -> playPause.label(Component.literal(context.player().playing() ? Icons.PAUSE : Icons.PLAY)));

        addToolbarButton(x, y, 20, Component.literal(Icons.STOP), () -> context.player().stop(), null, EditorLang.t("toolbar.stop"));
        x[0] += 6;
        addToolbarButton(x, y, 20, Component.literal(Icons.ADD), this::addKeyAtPlayhead, null, EditorLang.t("toolbar.add_key"));
        addToolbarButton(x, y, 20, Component.literal(Icons.REMOVE), this::removeSelectedKey, null, EditorLang.t("toolbar.remove_key"));
        addToolbarButton(x, y, 20, Component.literal(Icons.SNAP), () -> context.snapEnabled(!context.snapEnabled()),
                () -> context.snapEnabled(), EditorLang.t("toolbar.snap"));
        addToolbarButton(x, y, 20, Component.literal(Icons.VIEW), this::toggleViewMode, () -> isFreeView(), EditorLang.t("toolbar.view_mode"));
        addToolbarButton(x, y, 20, Component.literal(Icons.RESET), layout::resetLayout, null, EditorLang.t("toolbar.reset_layout"));
        addToolbarButton(x, y, 20, Component.literal(Icons.CLOSE), this::onClose, null, EditorLang.t("toolbar.close"));

        toolbarEndX = x[0];
    }

    private ButtonWidget addToolbarButton(int[] cursor, int y, int width, Component label, Runnable action,
                                          @Nullable BooleanSupplier toggled, Component tooltip) {
        ButtonWidget button = new ButtonWidget(new UiRect(cursor[0], y, width, TOOLBAR_BUTTON_HEIGHT), label, action);
        button.tooltip(tooltip);

        if (toggled != null) {
            toolbarRefreshers.add(() -> button.toggled(toggled.getAsBoolean()));
        }

        toolbar.add(button);
        cursor[0] += width + 3;
        return button;
    }

    // endregion

    // region 操作

    private void togglePlay() {
        if (!isFreeView()) {
            context.player().toggle();
        }
    }

    private void toggleViewMode() {
        if (isFreeView()) {
            context.editor().viewMode(CameraEditorModel.ViewMode.PREVIEW);
        } else {
            context.syncFreePoseFromCamera();
            context.editor().viewMode(CameraEditorModel.ViewMode.FREE);
        }
    }

    private boolean isFreeView() {
        return context.editor().viewMode() == CameraEditorModel.ViewMode.FREE;
    }

    private void addKeyAtPlayhead() {
        var track = context.editor().selectedTrack();

        if (track == null) {
            context.notify(EditorLang.t("notify.no_track_selected"));
            return;
        }

        float time = context.snapTime(context.player().time());

        if (context.editor().addKey(track, time) >= 0) {
            context.notify(EditorLang.t("notify.key_added", Draw.num(time, 2)));
        }
    }

    private void removeSelectedKey() {
        if (context.editor().removeSelectedKey()) {
            context.notify(EditorLang.t("notify.key_removed"));
        }
    }

    // endregion

    // region 自由飞行

    private void tickFreeFly() {
        float deltaSeconds = deltaSeconds();

        if (!isFreeView() || deltaSeconds <= 0) {
            return;
        }

        float forward = axis(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S);
        float strafe = axis(GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_A);
        float vertical = axis(GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT);

        if (forward == 0 && strafe == 0 && vertical == 0) {
            return;
        }

        float speed = context.flySpeed();

        if (isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL)) {
            speed *= 4f;
        }

        context.editor().moveView(forward, strafe, vertical, speed, deltaSeconds);
    }

    private float axis(int positiveKey, int negativeKey) {
        return (isKeyDown(positiveKey) ? 1f : 0f) - (isKeyDown(negativeKey) ? 1f : 0f);
    }

    private boolean isKeyDown(int key) {
        return pressedKeys.contains(key);
    }

    private float deltaSeconds() {
        long now = System.nanoTime();

        if (lastNanos == 0) {
            lastNanos = now;
            return 0f;
        }

        float delta = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        return Mth.clamp(delta, 0f, 0.25f);
    }

    // endregion

    // region 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        lastMouseX = event.x();
        lastMouseY = event.y();

        // 有菜单打开时，点击先交给菜单，避免穿透到面板
        for (EditorPanel panel : panels) {
            if (panel.contextMenu() != null) {
                panel.menuMouseClicked(event);
                return true;
            }
        }

        // 世界内查看：面板不可见，点击只在接管/释放与右键菜单之间切换
        if (context.worldView()) {
            if (event.button() == 1) {
                EditorPanel panel = layout.panelAt(event.x(), event.y());

                if (panel != null) {
                    panel.mouseClicked(event, doubleClick);
                }

                return true;
            }

            if (viewportPanel.takingOver()) {
                viewportPanel.releaseViewport();
            } else {
                viewportPanel.takeOver();
            }

            return true;
        }

        // 视窗接管中：任意点击先释放接管
        if (viewportPanel.takingOver()) {
            viewportPanel.releaseViewport();
            return true;
        }

        if (layout.toolbarRect().contains(event.x(), event.y())) {
            toolbar.mouseClicked(event, doubleClick);
            return true;
        }

        int splitter = layout.verticalSplitterAt(event.x(), event.y());

        if (splitter >= 0) {
            dragSplitter = splitter;
            return true;
        }

        if (layout.horizontalSplitterAt(event.x(), event.y())) {
            dragHorizontalSplitter = true;
            return true;
        }

        EditorPanel header = layout.panelHeaderAt(event.x(), event.y());

        if (header != null) {
            dragPanelHeader = header;
            // 标题栏不属于面板内容区，需在此转发点击，双击才能折叠
            header.mouseClicked(event, doubleClick);
            return true;
        }

        EditorPanel panel = layout.panelAt(event.x(), event.y());

        if (panel != null) {
            panel.mouseClicked(event, doubleClick);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragPanelHeader != null) {
            EditorPanel target = layout.panelHeaderAt(event.x(), event.y());

            if (target != null && target != dragPanelHeader) {
                int from = layout.columns().indexOf(dragPanelHeader);
                int to = layout.columns().indexOf(target);

                if (from >= 0 && to >= 0) {
                    layout.swapColumns(from, to);
                }
            }

            dragPanelHeader = null;
        }

        dragSplitter = -1;
        dragHorizontalSplitter = false;

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

        if (dragHorizontalSplitter) {
            layout.resizeHorizontal(Mth.floor(event.y()));
            return true;
        }

        for (EditorPanel panel : panels) {
            panel.mouseDragged(event, deltaX, deltaY);
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        EditorPanel panel = layout.panelAt(mouseX, mouseY);

        if (panel != null) {
            return panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // 有菜单打开时：Esc 只关菜单，其它按键也不落到面板上
        for (EditorPanel panel : panels) {
            if (panel.contextMenu() != null) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    panel.closeMenu();
                }

                return true;
            }
        }

        // 世界内查看：Esc 先释放接管，再退出该模式
        if (context.worldView() && key == GLFW.GLFW_KEY_ESCAPE) {
            if (viewportPanel.takingOver()) {
                viewportPanel.releaseViewport();
            } else {
                viewportPanel.exitWorldView();
            }

            return true;
        }

        // 视窗接管中：Esc 只释放接管，不关闭编辑器
        if (viewportPanel.takingOver() && key == GLFW.GLFW_KEY_ESCAPE) {
            viewportPanel.releaseViewport();
            return true;
        }

        // 世界内查看时面板不可见，跳过它们的按键处理
        if (!context.worldView()) {
            for (EditorPanel panel : panels) {
                if (panel.keyPressed(event)) {
                    return true;
                }
            }
        }

        if (key == GLFW.GLFW_KEY_SPACE && !isFreeView()) {
            context.player().toggle();
            return true;
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
        for (EditorPanel panel : panels) {
            if (panel.charTyped(event)) {
                return true;
            }
        }

        return false;
    }

    private boolean isMovementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
                || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_SHIFT;
    }

    // endregion

    /// 供外部（按键绑定）打开编辑器
    public static void open() {
        if (CmdCamera.INSTANCE == null) {
            return;
        }

        Minecraft.getInstance().setScreen(new CameraEditorScreen());
    }
}
