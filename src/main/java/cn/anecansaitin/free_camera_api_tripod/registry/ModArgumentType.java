package cn.anecansaitin.free_camera_api_tripod.registry;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModArgumentType {
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = DeferredRegister.create(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, FreeCameraApiTripod.MODID);

    static {
    }

    public static void register(IEventBus bus) {
        ARGUMENT_TYPES.register(bus);
    }
}
