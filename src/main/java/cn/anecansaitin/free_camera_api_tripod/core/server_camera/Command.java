package cn.anecansaitin.free_camera_api_tripod.core.server_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID)
public class Command {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
//        dispatcher.register(
//
//        );
    }
}
