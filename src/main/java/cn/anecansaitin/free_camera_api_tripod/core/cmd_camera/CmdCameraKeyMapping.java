package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Clip;
import cn.anecansaitin.free_camera_api_tripod.core.animation.test.ClipTestGui;
import cn.anecansaitin.free_camera_api_tripod.core.animation.test.PathTestGui;
import cn.anecansaitin.freecameraapi.ClientUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID, value = Dist.CLIENT)
public class CmdCameraKeyMapping {
    public static final Lazy<KeyMapping> TEST1 = Lazy.of(() -> new KeyMapping("key." + FreeCameraApiTripod.MODID + ".test1", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, -1, new KeyMapping.Category(Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "test1"))));
    public static final Lazy<KeyMapping> TEST2 = Lazy.of(() -> new KeyMapping("key." + FreeCameraApiTripod.MODID + ".test2", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, -1, new KeyMapping.Category(Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "test2"))));
    public static final Lazy<KeyMapping> TEST3 = Lazy.of(() -> new KeyMapping("key." + FreeCameraApiTripod.MODID + ".test3", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, -1, new KeyMapping.Category(Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "test3"))));

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TEST1.get());
        event.register(TEST2.get());
        event.register(TEST3.get());
    }

    @SubscribeEvent
    public static void keyPress(ClientTickEvent.Post event) {
        while (TEST1.get().consumeClick()) {
//            CmdCamera cmdCamera = CmdCamera.INSTANCE;
//            Clip clip = cmdCamera.clip();
//            float duration = clip.duration();
//            Vec3 position = ClientUtil.camera().position();
//            cmdCamera.addPosKey(duration + 1, (float) position.x, (float) position.y, (float) position.z);
//            cmdCamera.addFovKey(0, 70);
//            PathTestGui screen = (PathTestGui) Minecraft.getInstance().screen;
//            screen.up();
        }

        while (TEST2.get().consumeClick()) {
//            CmdCamera cmdCamera = CmdCamera.INSTANCE;
//            cmdCamera.play();
            Minecraft.getInstance().setScreen(new PathTestGui());
        }

        while (TEST3.get().consumeClick()) {
//            CmdCamera cmdCamera = CmdCamera.INSTANCE;
//            cmdCamera.stop();
            Minecraft.getInstance().setScreen(new ClipTestGui());
        }
    }
}
