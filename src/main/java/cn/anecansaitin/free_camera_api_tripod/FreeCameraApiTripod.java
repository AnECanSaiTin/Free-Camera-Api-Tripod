package cn.anecansaitin.free_camera_api_tripod;

import cn.anecansaitin.free_camera_api_tripod.attachment.ModAttachment;
import cn.anecansaitin.free_camera_api_tripod.registry.ModArgumentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(FreeCameraApiTripod.MODID)
public class FreeCameraApiTripod {
    public static final String MODID = "free_camera_api_tripod";

    public FreeCameraApiTripod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachment.ATTACHMENT_TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, CameraAdditionConfig.SPEC);
        ModArgumentType.register(modEventBus);
    }
}
