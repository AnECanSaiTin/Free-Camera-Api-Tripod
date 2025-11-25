package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.commands.argument.SchemeArgument;
import cn.anecansaitin.free_camera_api_tripod.commands.argument.StateArgument;
import cn.anecansaitin.freecameraapi.api.ModifierStates;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID)
public class CameraCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("cmd_camera")
                        // region 状态
                        .then(Commands.literal("state")
                                .then(Commands.argument("presets", StringArgumentType.string())
                                                .suggests(CAMERA_ID)
                                                // region 基础状态
                                                .then(Commands.argument("state", new StateArgument())
                                                        .then(Commands.literal("get")
                                                                .executes(ctx -> {
                                                                            String presets = ctx.getArgument("presets", String.class);
                                                                            CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                                            if (camera == null) {
                                                                                ctx.getSource().sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
                                                                                return 0;
                                                                            }

                                                                            int state = ctx.getArgument("state", Integer.class);
                                                                            boolean value = isStateEnabled(camera.getState(), state);
                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.get", presets, ModifierStates.getName(state), Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                                                                            return 1;
                                                                        }
                                                                )
                                                        )
                                                        .then(Commands.literal("set")
                                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                                        .executes(ctx -> {
                                                                                    String presets = ctx.getArgument("presets", String.class);
                                                                                    CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                                                    if (camera == null) {
                                                                                        camera = CmdCamera.INSTANCE.create(presets);
                                                                                    }

                                                                                    int state = ctx.getArgument("state", Integer.class);
                                                                                    boolean value = ctx.getArgument("value", Boolean.class);
                                                                                    camera.setBitState(state, value);
                                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set", presets, ModifierStates.getName(state), Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                                                                                    return 1;
                                                                                }
                                                                        )
                                                                )
                                                        )
                                                )
                                                // endregion
                                                // region 控制模式
                                                .then(Commands.literal("control_scheme")
                                                        .then(Commands.literal("get")
                                                                .executes(
                                                                        ctx -> {
                                                                            String presets = ctx.getArgument("presets", String.class);
                                                                            CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                                            if (camera == null) {
                                                                                ctx.getSource().sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
                                                                                return 0;
                                                                            }

                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.get", presets, camera.getScheme().getTranslation()), true);
                                                                            return 1;
                                                                        }
                                                                )
                                                        )
                                                        .then(Commands.literal("set")
                                                                .then(Commands.argument("scheme", new SchemeArgument())
                                                                        .executes(
                                                                                ctx -> {
                                                                                    String presets = ctx.getArgument("presets", String.class);
                                                                                    CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                                                    if (camera == null) {
                                                                                        camera = CmdCamera.INSTANCE.create(presets);
                                                                                    }

                                                                                    ControlScheme scheme = ctx.getArgument("scheme", ControlScheme.class);
                                                                                    camera.setScheme(scheme);
                                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.set", presets, scheme.getTranslation()), true);
                                                                                    return 1;
                                                                                }
                                                                        )
                                                                )
                                                                // 该模式需要参数，因此独立分支
                                                                .then(Commands.literal("player_relative")
                                                                        .then(Commands.argument("angle", IntegerArgumentType.integer())
                                                                                .executes(
                                                                                        ctx -> {
                                                                                            String presets = ctx.getArgument("presets", String.class);
                                                                                            CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                                                            if (camera == null) {
                                                                                                camera = CmdCamera.INSTANCE.create(presets);
                                                                                            }

                                                                                            int angle = ctx.getArgument("angle", Integer.class);
                                                                                            ControlScheme.PLAYER_RELATIVE scheme = ControlScheme.PLAYER_RELATIVE(angle);
                                                                                            camera.setScheme(scheme);
                                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.set", presets, scheme.getTranslation()), true);
                                                                                            return 1;
                                                                                        }
                                                                                )
                                                                        )
                                                                )

                                                        )
                                                )
                                        // endregion
                                )
                        )
                        // endregion
                        // region 相机预设
                        .then(Commands.literal("preset")
                                // region 预设列表查看
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                                    Set<String> allId = CmdCamera.INSTANCE.getAllId();
                                                    CommandSourceStack source = ctx.getSource();

                                                    if (allId.isEmpty()) {
                                                        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.list.empty"), true);
                                                        return 1;
                                                    }

                                                    source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.list.title"), true);
                                                    int i = 1;

                                                    for (String id : allId) {
                                                        int finalI = i;
                                                        source.sendSuccess(() -> {
                                                                    MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.list.click");
                                                                    click.withStyle(ChatFormatting.BLUE);
                                                                    click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera preset view " + id)));
                                                                    return Component.literal(String.valueOf(finalI))
                                                                            .withStyle(ChatFormatting.GREEN)
                                                                            .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                                                                            .append(Component.literal(id).withStyle(ChatFormatting.AQUA))
                                                                            .append(" ")
                                                                            .append(click);
                                                                },
                                                                true);
                                                        i++;
                                                    }

                                                    source.sendSuccess(() -> Component.literal("───────────"), true);
                                                    return 1;
                                                }
                                        )
                                )
                                // endregion
                                // region 预设查看
                                .then(Commands.literal("view")
                                        .then(Commands.argument("preset", StringArgumentType.string())
                                                .suggests(CAMERA_ID)
                                                .executes(ctx -> {
                                                            String preset = ctx.getArgument("preset", String.class);
                                                            CameraState camera = CmdCamera.INSTANCE.get(preset);
                                                            CommandSourceStack source = ctx.getSource();

                                                            if (camera == null) {
                                                                source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", preset));
                                                                return 0;
                                                            }

                                                            source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.view.title", preset), true);
                                                            int state = camera.getState();

                                                            for (Object2IntMap.Entry<String> entry : ModifierStates.NAME_STATE.object2IntEntrySet()) {
                                                                source.sendSuccess(() -> {
                                                                    boolean value = isStateEnabled(state, entry.getIntValue());
                                                                    return Component.literal(entry.getKey())
                                                                            .withStyle(ChatFormatting.GREEN)
                                                                            .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                                                                            .append(Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
                                                                }, true);
                                                            }

                                                            source.sendSuccess(() -> Component.literal("control_scheme")
                                                                    .withStyle(ChatFormatting.GREEN)
                                                                    .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                                                                    .append(camera.getScheme().getTranslation()), true);
                                                            source.sendSuccess(() -> Component.literal("───────────"), true);
                                                            return 1;
                                                        }
                                                )
                                        )
                                )
                                // endregion
                        )
                // endregion
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