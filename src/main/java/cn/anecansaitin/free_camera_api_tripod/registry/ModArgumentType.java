package cn.anecansaitin.free_camera_api_tripod.registry;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.commands.argument.SchemeArgument;
import cn.anecansaitin.free_camera_api_tripod.commands.argument.StateArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModArgumentType {
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = DeferredRegister.create(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, FreeCameraApiTripod.MODID);

    static {
        ARGUMENT_TYPES.register("camera_state", () -> ArgumentTypeInfos.registerByClass(StateArgument.class, SingletonArgumentInfo.contextFree(StateArgument::new)));
        ARGUMENT_TYPES.register("camera_control_scheme", () -> ArgumentTypeInfos.registerByClass(SchemeArgument.class, SingletonArgumentInfo.contextFree(SchemeArgument::new)));
    }

    public static void register(IEventBus bus) {
        ARGUMENT_TYPES.register(bus);
    }
}
