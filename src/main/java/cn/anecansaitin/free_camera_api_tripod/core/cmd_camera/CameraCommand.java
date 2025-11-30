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
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;
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
                                                                // region 获取
                                                                .then(Commands.literal("get")
                                                                        .executes(ctx -> {
                                                                                    String presets = ctx.getArgument("presets", String.class);
                                                                                    CameraState camera = CmdCamera.INSTANCE.get(presets);
                                                                                    CommandSourceStack source = ctx.getSource();

                                                                                    if (camera == null) {
                                                                                        source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
                                                                                        return 0;
                                                                                    }

                                                                                    int state = ctx.getArgument("state", Integer.class);
                                                                                    boolean value = isStateEnabled(camera.getState(), state);
                                                                                    source.sendSuccess(
                                                                                            () -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.get",
                                                                                                    Component.literal(presets).withStyle(ChatFormatting.GOLD),
                                                                                                    ModifierStates.getTranslation(state).withStyle(ChatFormatting.AQUA),
                                                                                                    Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)),
                                                                                            true
                                                                                    );
                                                                                    source.sendSuccess(() -> Component.literal("───────────"), true);
                                                                                    return 1;
                                                                                }
                                                                        )
                                                                )
                                                                // endregion
                                                                // region 设置
                                                                .then(Commands.literal("set")
                                                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                                                .executes(ctx -> {
                                                                                            String presets = ctx.getArgument("presets", String.class);
                                                                                            CameraState camera = CmdCamera.INSTANCE.get(presets);
                                                                                            CommandSourceStack source = ctx.getSource();

                                                                                            if (camera == null) {
                                                                                                camera = CmdCamera.INSTANCE.create(presets);
                                                                                            }

                                                                                            int state = ctx.getArgument("state", Integer.class);
                                                                                            boolean value = ctx.getArgument("value", Boolean.class);
                                                                                            camera.setBitState(state, value);
                                                                                            source.sendSuccess(
                                                                                                    () -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set",
                                                                                                            Component.literal(presets).withStyle(ChatFormatting.GOLD),
                                                                                                            ModifierStates.getTranslation(state).withStyle(ChatFormatting.AQUA),
                                                                                                            Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)),
                                                                                                    true
                                                                                            );
                                                                                            source.sendSuccess(() -> Component.literal("───────────"), true);
                                                                                            return 1;
                                                                                        }
                                                                                )
                                                                        )
                                                                )
                                                                // endregion
                                                                // region 可用选项
                                                                .then(Commands.literal("options")
                                                                        .executes(ctx -> {
                                                                            String presets = ctx.getArgument("presets", String.class);
                                                                            CameraState camera = CmdCamera.INSTANCE.get(presets);
                                                                            CommandSourceStack source = ctx.getSource();

                                                                            if (camera == null) {
                                                                                source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
                                                                                return 0;
                                                                            }

                                                                            int state = ctx.getArgument("state", Integer.class);
                                                                            source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.title", ModifierStates.getTranslation(state).withStyle(ChatFormatting.AQUA)), true);
                                                                            source.sendSuccess(() -> {
                                                                                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
                                                                                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " " + ModifierStates.getName(state) + " set true")));
                                                                                return Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.true").withStyle(ChatFormatting.GREEN).append(" ").append(click);
                                                                            }, true);
                                                                            source.sendSuccess(() -> {
                                                                                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
                                                                                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " " + ModifierStates.getName(state) + " set false")));
                                                                                return Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.false").withStyle(ChatFormatting.RED).append(" ").append(click);
                                                                            }, true);
                                                                            source.sendSuccess(() -> Component.literal("───────────"), true);
                                                                            return 1;
                                                                        })
                                                                )
                                                        // endregion
                                                )
                                                // endregion
                                                // region 控制模式
                                                .then(Commands.literal("control_scheme")
                                                                // region 获取
                                                                .then(Commands.literal("get")
                                                                        .executes(
                                                                                ctx -> {
                                                                                    String presets = ctx.getArgument("presets", String.class);
                                                                                    CameraState camera = CmdCamera.INSTANCE.get(presets);

                                                                                    if (camera == null) {
                                                                                        ctx.getSource().sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
                                                                                        return 0;
                                                                                    }

                                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.get", presets, camera.getScheme().getTranslation().withStyle(ChatFormatting.GREEN)), true);
                                                                                    return 1;
                                                                                }
                                                                        )
                                                                )
                                                                // endregion
                                                                // region 设置
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
                                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.set", presets, scheme.getTranslation().withStyle(ChatFormatting.GREEN)), true);
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
                                                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.set", presets, scheme.getTranslation().withStyle(ChatFormatting.GREEN)), true);
                                                                                                    return 1;
                                                                                                }
                                                                                        )
                                                                                )
                                                                        )

                                                                )
                                                                // endregion
                                                                // region 可用选项
                                                                .then(Commands.literal("options")
                                                                        .executes(ctx -> {
                                                                            String presets = ctx.getArgument("presets", String.class);
                                                                            CameraState camera = CmdCamera.INSTANCE.get(presets);
                                                                            CommandSourceStack source = ctx.getSource();

                                                                            if (camera == null) {
                                                                                source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
                                                                                return 0;
                                                                            }

                                                                            source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.options.title"), true);
                                                                            source.sendSuccess(() -> {
                                                                                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
                                                                                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set vanilla")));
                                                                                return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.vanilla").withStyle(ChatFormatting.GREEN).append(" ").append(click);
                                                                            }, true);
                                                                            source.sendSuccess(() -> {
                                                                                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
                                                                                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set camera_relative")));
                                                                                return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.camera_relative").withStyle(ChatFormatting.GREEN).append(" ").append(click);
                                                                            }, true);
                                                                            source.sendSuccess(() -> {
                                                                                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
                                                                                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set camera_relative_strafe")));
                                                                                return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.camera_relative_strafe").withStyle(ChatFormatting.GREEN).append(" ").append(click);
                                                                            }, true);
                                                                            source.sendSuccess(() -> {
                                                                                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
                                                                                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.Custom(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "player_relative_setting"), Optional.of(StringTag.valueOf(presets)))));
                                                                                return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.player_relative", "0").withStyle(ChatFormatting.GREEN).append(" ").append(click);
                                                                            }, true);
                                                                            source.sendSuccess(() -> {
                                                                                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
                                                                                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set player_relative_strafe")));
                                                                                return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.player_relative_strafe").withStyle(ChatFormatting.GREEN).append(" ").append(click);
                                                                            }, true);
                                                                            source.sendSuccess(() -> Component.literal("───────────"), true);
                                                                            return 1;
                                                                        })
                                                                )
                                                        // endregion
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
                                                                                    .append(Component.literal(id).withStyle(ChatFormatting.GOLD))
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
                                        // region 预设属性查看
                                        .then(Commands.literal("view")
                                                .then(Commands.argument("preset", StringArgumentType.string())
                                                        .suggests(CAMERA_ID)
                                                        .executes(ctx -> {
                                                                    String preset = ctx.getArgument("preset", String.class);
                                                                    CameraState camera = CmdCamera.INSTANCE.get(preset);
                                                                    CommandSourceStack source = ctx.getSource();

                                                                    if (camera == null) {
                                                                        source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", Component.literal(preset).withStyle(ChatFormatting.GOLD)));
                                                                        return 0;
                                                                    }

                                                                    source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.view.title", Component.literal(preset).withStyle(ChatFormatting.GOLD)), true);
                                                                    int state = camera.getState();

                                                                    for (Object2IntMap.Entry<String> entry : ModifierStates.NAME_STATE.object2IntEntrySet()) {
                                                                        source.sendSuccess(() -> {
                                                                            boolean value = isStateEnabled(state, entry.getIntValue());
                                                                            String id = entry.getKey();
                                                                            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.view.click");
                                                                            click.withStyle(ChatFormatting.BLUE);
                                                                            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + preset + " " + id + " options")));
                                                                            return ModifierStates.getTranslation(id)
                                                                                    .withStyle(ChatFormatting.GREEN)
                                                                                    .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                                                                                    .append(Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED))
                                                                                    .append(" ")
                                                                                    .append(click);
                                                                        }, true);
                                                                    }

                                                                    MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.view.click");
                                                                    click.withStyle(ChatFormatting.BLUE);
                                                                    click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + preset + " control_scheme options")));
                                                                    source.sendSuccess(() -> ModifierStates.getTranslation("control_scheme")
                                                                            .withStyle(ChatFormatting.GREEN)
                                                                            .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                                                                            .append(camera.getScheme().getTranslation().withStyle(ChatFormatting.AQUA))
                                                                            .append(" ")
                                                                            .append(click), true);
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