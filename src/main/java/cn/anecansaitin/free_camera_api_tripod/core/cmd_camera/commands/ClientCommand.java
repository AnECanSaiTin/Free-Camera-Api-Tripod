package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.commands;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID, value = Dist.CLIENT)
public class ClientCommand {
    @SubscribeEvent
    public static void init(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("cmd_camera")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then()
        );
    }
}
