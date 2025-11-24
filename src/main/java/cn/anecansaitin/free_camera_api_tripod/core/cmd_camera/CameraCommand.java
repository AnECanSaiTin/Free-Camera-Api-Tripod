package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID)
public class CameraCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("cmd_camera")
                        .then(Commands.literal("state")
                                .then(Commands.argument("presets", StringArgumentType.string())
                                        .suggests(CAMERA_ID)
                                        .then(Commands.argument("state", StringArgumentType.string())
                                                .suggests(CAMERA_STATE)
                                        )
                                )
                        )
        );
    }

    private static final SuggestionProvider<CommandSourceStack> CAMERA_ID = (ctx, builder) -> {
        for (String id : CmdCamera.INSTANCE.getAllId()) {
            builder.suggest(id);
        }

        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> CAMERA_STATE = (ctx, builder) -> builder
            .suggest("pos")
            .suggest("rot")
            .suggest("fov")
            .suggest("obstacle")
            .suggest("global_mode")
            .suggest("chunk_loader")
            .suggest("control_scheme")
            .buildFuture();
}

//.then(Commands.literal("get")
//                                                .executes(context -> {
//String id = context.getArgument("presets", String.class);
//CameraState state = CmdCamera.INSTANCE.get(id);
//
//                                                    if (state == null) {
//        context.getSource().sendFailure(Component.literal("id 不存在"));
//        return 0;
//        }
//
//state.
//                                                    return 1;
//                                                            })
//                                                            )