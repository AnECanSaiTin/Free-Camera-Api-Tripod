package cn.anecansaitin.free_camera_api_tripod.core.editor.panel;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CameraPose;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorContext;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import cn.anecansaitin.free_camera_api_tripod.core.editor.render.ViewportPipRenderer;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Draw;
import cn.anecansaitin.free_camera_api_tripod.core.editor.theme.Icons;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ButtonWidget;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ContextMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

/// 视口面板：把整帧游戏画面等比缩放进面板内显示，并可接管鼠标直接在里面操作。
///
/// 预览模式显示播放头所在帧的相机姿态；自由模式可飞行取景。
/// 所有操作都收进右键菜单，面板顶部只留一行状态与一行可收起的操作提示。
public class ViewportPanel extends EditorPanel {
    public static final String ID = "viewport";

    private static final int LOOK_SENSITIVITY = 4;
    /// 提示文本行高
    private static final int HINT_LINE_HEIGHT = 10;
    /// 可收起的提示行数
    private static final int HINT_LINES = 2;

    private final EditorContext context;
    private final CameraPose poseCache = new CameraPose();
    /// 画面区域顶部（状态行与提示之下）
    private int videoTop;
    /// 提示区是否收起，收起后整块高度都给预览画面
    private boolean hintsCollapsed;
    private @Nullable ButtonWidget hintToggle;

    /// 是否已接管视窗（鼠标被锁定，移动鼠标转动视角）
    private boolean takenOver;
    private double lastCursorX;
    private double lastCursorY;

    public ViewportPanel(EditorContext context) {
        super(ID, EditorLang.t("panel.viewport"));
        this.context = context;
    }

    @Override
    protected void layoutWidgets(UiRect content) {
        updateVideoTop();

        hintToggle = new ButtonWidget(new UiRect(content.right() - 14, content.y() + 1, 13, 10),
                Component.literal(hintsCollapsed ? Icons.EXPAND : Icons.COLLAPSE), this::toggleHints);
        hintToggle.tooltip(EditorLang.t(hintsCollapsed ? "viewport.hints.show" : "viewport.hints.hide"));
        widgets.add(hintToggle);
    }

    /// 状态行占 12 像素，其下是提示区（可收起）
    private void updateVideoTop() {
        int hints = hintsCollapsed ? 0 : HINT_LINES * HINT_LINE_HEIGHT + 2;
        videoTop = contentRect().y() + 12 + hints;
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
        if (takenOver) {
            applyMouseLook();
        }

        renderFrame(graphics, content);
        Draw.textEllipsized(graphics, statusText().getString(), content.x() + 5, content.y() + 3,
                content.width() - 20, takenOver ? Draw.ACCENT : Draw.TEXT_DIM);

        if (!hintsCollapsed) {
            int y = content.y() + 12;
            Draw.textEllipsized(graphics, (takenOver ? EditorLang.t("viewport.hint.taken_over") : EditorLang.t("viewport.hint.takeover")).getString(),
                    content.x() + 5, y, content.width() - 10, Draw.TEXT_DISABLED);
            y += HINT_LINE_HEIGHT;
            Draw.textEllipsized(graphics, EditorLang.t("viewport.hint.keys").getString(), content.x() + 5, y, content.width() - 10, Draw.TEXT_DISABLED);
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

    /// 画面区域：整帧游戏画面等比缩放到面板内，多余部分露出面板底色
    private void renderFrame(GuiGraphicsExtractor graphics, UiRect content) {
        int top = Math.min(videoTop, Math.max(content.y(), content.bottom() - 14));
        UiRect area = new UiRect(content.x() + 1, top, Math.max(1, content.width() - 2), Math.max(1, content.bottom() - 14 - top));

        int windowWidth = Minecraft.getInstance().getWindow().getWidth();
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();

        if (windowWidth <= 0 || windowHeight <= 0) {
            return;
        }

        float scale = Math.min((float) area.width() / windowWidth, (float) area.height() / windowHeight);
        int width = Math.max(1, Math.round(windowWidth * scale));
        int height = Math.max(1, Math.round(windowHeight * scale));
        UiRect fit = new UiRect(area.centerX() - width / 2, area.centerY() - height / 2, width, height);

        // 离屏抓帧后再贴回视窗：PiP 的准备阶段位于世界之后、GUI 通道之前，不会把编辑器自身拍进去
        graphics.submitPictureInPictureRenderState(new ViewportPipRenderer.State(
                fit.x(), fit.y(), fit.right(), fit.bottom(), 1.0f, null));
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
        menu.item(Icons.ROTATE, EditorLang.t("viewport.capture_rotation"), this::captureRotation);
        menu.item(Icons.FOV, EditorLang.t("viewport.capture_fov"), this::captureFov);
        menu.item(Icons.ADD, EditorLang.t("viewport.add_path_node"), this::addPathNode);
        menu.separator();
        menu.item(Icons.SPEED_UP, EditorLang.t("viewport.fly_speed_up"), () -> context.flySpeed(context.flySpeed() * 1.5f));
        menu.item(Icons.SPEED_DOWN, EditorLang.t("viewport.fly_speed_down"), () -> context.flySpeed(context.flySpeed() / 1.5f));
        menu.separator();
        menu.toggle(Icons.EXPAND, EditorLang.t("viewport.hints.toggle"), () -> !hintsCollapsed, this::toggleHints);
        menu.separator();
        menu.toggle(Icons.FIT, EditorLang.t("viewport.world_view"), context::worldView, this::toggleWorldView);
        return menu;
    }

    /// 世界内查看：收起编辑界面让世界铺满屏幕，同时接管鼠标以便直接环视
    private void toggleWorldView() {
        boolean enter = !context.worldView();
        context.worldView(enter);

        if (enter) {
            takeOver();
            context.notify(EditorLang.t("notify.world_view_entered"));
        } else {
            releaseViewport();
            context.notify(EditorLang.t("notify.world_view_exited"));
        }
    }

    /// 供屏幕在 Esc 退出世界内查看时调用
    public void exitWorldView() {
        if (!context.worldView()) {
            return;
        }

        context.worldView(false);
        releaseViewport();
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
        context.editor().addPathNodeAt(context.currentCameraPosition(), context.player().time());
        context.notify(EditorLang.t("notify.path_node_added"));
    }

    // region 交互

    @Override
    protected boolean contentMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 左键点击接管视窗：锁定鼠标，之后移动鼠标即可转视角
        if (event.button() == 0) {
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

    @Override
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (context.editor().viewMode() != CameraEditorModel.ViewMode.FREE) {
            return false;
        }

        context.editor().dolly((float) scrollY * 1.5f);
        return true;
    }

    // endregion

    // region 视窗接管

    public boolean takingOver() {
        return takenOver;
    }

    /// 世界内查看时面板不参与绘制，鼠标环视改由屏幕每帧驱动
    public void tickMouseLook() {
        if (takenOver) {
            applyMouseLook();
        }
    }

    /// 接管视窗：锁定鼠标，移动鼠标即可转动视角。
    /// 预览模式下会先切到自由视角，符合「一进视窗就自己开拍」的直觉。
    public void takeOver() {
        if (takenOver) {
            return;
        }

        if (context.editor().viewMode() != CameraEditorModel.ViewMode.FREE) {
            enterFreeView();
        }

        takenOver = true;
        lockCursor();
        context.notify(EditorLang.t("notify.viewport_taken_over"));
    }

    /// 释放接管，恢复鼠标
    public void releaseViewport() {
        if (!takenOver) {
            return;
        }

        takenOver = false;
        unlockCursor();
    }

    private void lockCursor() {
        long handle = Minecraft.getInstance().getWindow().handle();
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer cursorX = stack.mallocDouble(1);
            DoubleBuffer cursorY = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(handle, cursorX, cursorY);
            lastCursorX = cursorX.get(0);
            lastCursorY = cursorY.get(0);
        }
    }

    private void unlockCursor() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    /// 每帧读取鼠标位移并转动视角（鼠标已被锁定）
    private void applyMouseLook() {
        long handle = Minecraft.getInstance().getWindow().handle();
        double cursorX;
        double cursorY;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer bufferX = stack.mallocDouble(1);
            DoubleBuffer bufferY = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(handle, bufferX, bufferY);
            cursorX = bufferX.get(0);
            cursorY = bufferY.get(0);
        }

        float deltaX = (float) (cursorX - lastCursorX);
        float deltaY = (float) (cursorY - lastCursorY);
        lastCursorX = cursorX;
        lastCursorY = cursorY;

        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        double guiScale = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        context.editor().rotateView((float) (deltaX / guiScale * LOOK_SENSITIVITY), (float) (deltaY / guiScale * LOOK_SENSITIVITY));
    }

    // endregion
}
