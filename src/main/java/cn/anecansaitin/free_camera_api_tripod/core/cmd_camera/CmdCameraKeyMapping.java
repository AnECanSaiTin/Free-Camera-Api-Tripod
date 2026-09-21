package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.editor.CameraEditorScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID, value = Dist.CLIENT)
public class CmdCameraKeyMapping {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "editor"));

    /// 打开相机动画编辑器
    public static final Lazy<KeyMapping> OPEN_EDITOR = Lazy.of(() -> new KeyMapping(
            "key." + FreeCameraApiTripod.MODID + ".open_editor", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY));
    /// 播放 / 暂停
    public static final Lazy<KeyMapping> PLAY_PAUSE = Lazy.of(() -> new KeyMapping(
            "key." + FreeCameraApiTripod.MODID + ".play_pause", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY));
    /// 停止
    public static final Lazy<KeyMapping> STOP = Lazy.of(() -> new KeyMapping(
            "key." + FreeCameraApiTripod.MODID + ".stop", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY));

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR.get());
        event.register(PLAY_PAUSE.get());
        event.register(STOP.get());
    }

    @SubscribeEvent
    public static void keyPress(ClientTickEvent.Post event) {
        while (OPEN_EDITOR.get().consumeClick()) {
            if (Minecraft.getInstance().screen == null) {
                CameraEditorScreen.open();
            }
        }

        while (PLAY_PAUSE.get().consumeClick()) {
            if (CmdCamera.INSTANCE != null) {
                CmdCamera.INSTANCE.player().toggle();
            }
        }

        while (STOP.get().consumeClick()) {
            if (CmdCamera.INSTANCE != null) {
                CmdCamera.INSTANCE.player().stop();
            }
        }
    }
}
