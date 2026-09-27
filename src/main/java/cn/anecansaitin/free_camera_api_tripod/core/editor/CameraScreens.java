package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/// 判定当前是否处于某个相机编辑界面（主编辑器 / 路径编辑 / 世界内查看）。
///
/// 相机在这些界面打开期间由编辑器持有的姿态驱动，因此这里直接按当前界面判断：
/// 界面切换不需要额外维护标记，也就不受 init / removed 的调用顺序影响，
/// 更不会因为窗口缩放重新执行 init 而把标记弄乱。
@EventBusSubscriber(modid = FreeCameraApiTripod.MODID, value = Dist.CLIENT)
public final class CameraScreens {
    public static boolean editing() {
        Screen screen = Minecraft.getInstance().screen;

        return screen instanceof CameraEditorScreen
                || screen instanceof PathEditorScreen
                || screen instanceof WorldViewScreen;
    }

    /// 编辑界面里不画玩家自己的手。
    ///
    /// 相机这会儿已经交给动画姿态驱动，手还挂在玩家身上，取景时会跟着身体乱晃、糊在画面下沿；
    /// 视口预览与世界内查看都是要"看画面"的地方，这里直接把手取消掉。
    /// 不用 F1 的 hideGui：那会把 HUD 一起藏了，而 HUD 与编辑界面本身还有用。
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (editing()) {
            event.setCanceled(true);
        }
    }

    private CameraScreens() {
    }
}
