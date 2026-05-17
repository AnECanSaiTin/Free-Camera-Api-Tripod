package cn.anecansaitin.free_camera_api_tripod.registry;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.commands.argument.SchemeArgument;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.commands.argument.StateArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
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
