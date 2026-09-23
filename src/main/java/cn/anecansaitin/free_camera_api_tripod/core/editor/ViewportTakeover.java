package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.util.Set;

/// 视窗接管：锁定鼠标后由鼠标位移转视角，并支持 WASD 自由飞行。
///
/// 编辑器里的视口面板、世界内查看界面与路径编辑界面共用这一套手感，
/// 避免鼠标灵敏度换算与飞行逻辑在多处各写一份。
///
/// 锁定光标用的是 GLFW 的禁用光标模式：它只是隐藏光标并允许无限位移，
/// 系统光标本身会一直往外跑。因此每帧都要把光标钉回视窗正中间，
/// 否则界面会以为鼠标在别的面板上（滚轮与悬停都会落到别的面板）。
public final class ViewportTakeover {
    private final EditorContext context;
    private boolean takenOver;
    private double lastCursorX;
    private double lastCursorY;
    private long lastNanos;
    /// 要钉住的光标位置（GUI 逻辑坐标）；未提供时为 NaN，此时不做回中
    private double pinX = Double.NaN;
    private double pinY = Double.NaN;

    public ViewportTakeover(EditorContext context) {
        this.context = context;
    }

    public boolean takingOver() {
        return takenOver;
    }

    /// 接管：先切到自由视角，再锁定鼠标
    public void takeOver() {
        if (takenOver) {
            return;
        }

        if (context.editor().viewMode() != CameraEditorModel.ViewMode.FREE) {
            context.syncFreePoseFromCamera();
            context.editor().viewMode(CameraEditorModel.ViewMode.FREE);
        }

        takenOver = true;
        lastNanos = 0;
        lockCursor();
        pinCursor();
        context.notify(EditorLang.t("notify.viewport_taken_over"));
    }

    /// 释放接管，恢复鼠标
    public void release() {
        if (!takenOver) {
            return;
        }

        takenOver = false;
        unlockCursor();
    }

    /// 每帧调用：读取鼠标位移转视角，按按下的按键做自由飞行，并把光标钉回视窗正中。
    ///
    /// centerX / centerY 是视窗画面中心（GUI 逻辑坐标）；为 NaN 表示这一帧拿不到画面矩形，
    /// 此时只转视角、不做回中。
    public void tick(Set<Integer> pressedKeys, double centerX, double centerY) {
        if (!takenOver) {
            return;
        }

        pinX = centerX;
        pinY = centerY;
        applyMouseLook();
        pinCursor();

        if (context.editor().viewMode() != CameraEditorModel.ViewMode.FREE) {
            return;
        }

        float deltaSeconds = deltaSeconds();

        if (deltaSeconds <= 0) {
            return;
        }

        float forward = axis(pressedKeys, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S);
        float strafe = axis(pressedKeys, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_A);
        float vertical = axis(pressedKeys, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT);

        if (forward == 0 && strafe == 0 && vertical == 0) {
            return;
        }

        float speed = context.flySpeed();

        if (pressedKeys.contains(GLFW.GLFW_KEY_LEFT_CONTROL) || pressedKeys.contains(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            speed *= 4f;
        }

        context.editor().moveView(forward, strafe, vertical, speed, deltaSeconds);
    }

    private static float axis(Set<Integer> pressedKeys, int positiveKey, int negativeKey) {
        return (pressedKeys.contains(positiveKey) ? 1f : 0f) - (pressedKeys.contains(negativeKey) ? 1f : 0f);
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

    /// 每帧读取鼠标位移并转动视角（鼠标已被锁定）。
    ///
    /// 换算与游戏原生一致：MC 把鼠标像素位移乘以「灵敏度^3 × 8 × 0.15」得到角度，
    /// 默认灵敏度 0.5 时即每像素 0.15 度。
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

        context.editor().rotateView(deltaX * lookFactor(), deltaY * lookFactor());
    }

    /// 每像素对应的转动角度，与游戏内置的鼠标灵敏度换算保持一致
    private static float lookFactor() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double value = sensitivity * 0.6 + 0.2;
        return (float) (value * value * value * 8.0 * 0.15);
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

    /// 把光标钉回视窗正中。
    ///
    /// GLFW 的光标坐标是窗口像素，而这里拿到的是界面逻辑坐标，两者按 GUI 缩放比例换算
    /// （界面逻辑尺寸取当前界面的 width / height，窗口像素尺寸取窗口自身的值）。
    /// 顺手把上一次读数对齐到钉住的位置，避免接管那一帧算出一次大跳变。
    private void pinCursor() {
        if (!takenOver || !Double.isFinite(pinX) || !Double.isFinite(pinY)) {
            return;
        }

        Window window = Minecraft.getInstance().getWindow();
        Screen screen = Minecraft.getInstance().screen;

        if (screen == null || screen.width <= 0 || screen.height <= 0) {
            return;
        }

        double pixelX = pinX * window.getScreenWidth() / (double) screen.width;
        double pixelY = pinY * window.getScreenHeight() / (double) screen.height;
        GLFW.glfwSetCursorPos(window.handle(), pixelX, pixelY);
        lastCursorX = pixelX;
        lastCursorY = pixelY;
    }

    private void unlockCursor() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }
}
