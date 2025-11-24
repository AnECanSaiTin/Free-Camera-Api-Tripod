package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.commands.argument.StateArgument;
import cn.anecansaitin.freecameraapi.api.ModifierStates;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
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
                                        .then(Commands.argument("state", new StateArgument())
                                                .then(Commands.literal("get")
                                                        .executes(ctx -> {
                                                            String presets = ctx.getArgument("presets", String.class);
                                                            CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                            if (camera == null) {
                                                                ctx.getSource().sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
                                                                return 0;
                                                            }

                                                            Integer state = ctx.getArgument("state", Integer.class);

                                                            ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.get", presets, ModifierStates.getName(state), Boolean.toString(isStateEnabled(camera.getState(), state))), true);
                                                            return 1;
                                                        })
                                                )
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                                .executes(ctx -> {
                                                                    String presets = ctx.getArgument("presets", String.class);
                                                                    CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                                    if (camera == null) {
                                                                        camera = CmdCamera.INSTANCE.create(presets);
                                                                    }

                                                                    Integer state = ctx.getArgument("state", Integer.class);
                                                                    Boolean value = ctx.getArgument("value", Boolean.class);
                                                                    camera.setBitState(state, value);
                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set", presets, ModifierStates.getName(state), value.toString()), true);
                                                                    return 1;
                                                                })
                                                        )
                                                )
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

    private static boolean isStateEnabled(int cameraState, int state) {
        return (cameraState & state) != 0;
    }
}