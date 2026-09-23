package cn.anecansaitin.free_camera_api_tripod.core.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/// 判定当前是否处于某个相机编辑界面（主编辑器 / 路径编辑 / 世界内查看）。
///
/// 相机在这些界面打开期间由编辑器持有的姿态驱动，因此这里直接按当前界面判断：
/// 界面切换不需要额外维护标记，也就不受 init / removed 的调用顺序影响，
/// 更不会因为窗口缩放重新执行 init 而把标记弄乱。
public final class CameraScreens {
    public static boolean editing() {
        Screen screen = Minecraft.getInstance().screen;

        return screen instanceof CameraEditorScreen
                || screen instanceof PathEditorScreen
                || screen instanceof WorldViewScreen;
    }

    private CameraScreens() {
    }
}
