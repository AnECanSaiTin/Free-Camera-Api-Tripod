package cn.anecansaitin.free_camera_api_tripod;

import cn.anecansaitin.free_camera_api_tripod.api.animation.track.TrackTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod.core.animation.track.CommandTrack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = FreeCameraApiTripod.MODID, dist = Dist.CLIENT)
public class FreeCameraApiTripodClient {

    public FreeCameraApiTripodClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, EditorConfig.SPEC);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // 命令轨道的类型登记放在客户端入口：它要执行指令，只可能在客户端实例化
        TrackTypeRegistry.register(CommandTrack.TYPE);
    }
}
