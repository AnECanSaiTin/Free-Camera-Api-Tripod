package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.control_scheme.ControlScheme;
import cn.anecansaitin.free_camera_api_tripod.commands.argument.SchemeArgument;
import cn.anecansaitin.free_camera_api_tripod.commands.argument.StateArgument;
import cn.anecansaitin.freecameraapi.api.CameraStates;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
                        .requires(source -> source.hasPermission(Commands.LEVEL_ADMINS))
                        // region 状态
                        .then(Commands.literal("state")
                                .then(Commands.argument("presets", StringArgumentType.string())
                                                .suggests(CAMERA_ID)
                                                // region 基础状态
                                                .then(Commands.argument("state", new StateArgument())
                                                                // region 获取
                                                                .then(Commands.literal("get").executes(CameraCommand::stateGet))
                                                                // endregion
                                                                // region 设置
                                                                .then(Commands.literal("set").then(Commands.argument("value", BoolArgumentType.bool()).executes(CameraCommand::stateSet)))
                                                                // endregion
                                                                // region 可用选项
                                                                .then(Commands.literal("options").executes(CameraCommand::stateOptions))
                                                        // endregion
                                                )
                                                // endregion
                                                // region 控制模式
                                                .then(Commands.literal("control_scheme")
                                                                // region 获取
                                                                .then(Commands.literal("get").executes(CameraCommand::schemeGet))
                                                                // endregion
                                                                // region 设置
                                                                .then(Commands.literal("set")
                                                                        .then(Commands.argument("scheme", new SchemeArgument())
                                                                                .executes(CameraCommand::schemeSet)
                                                                        )
                                                                        // 该模式需要参数，因此独立分支
                                                                        .then(Commands.literal("player_relative")
                                                                                .then(Commands.argument("angle", IntegerArgumentType.integer())
                                                                                        .executes(CameraCommand::playerRelativeSet)
                                                                                )
                                                                        )

                                                                )
                                                                // endregion
                                                                // region 可用选项
                                                                .then(Commands.literal("options")
                                                                        .executes(CameraCommand::schemeOptions)
                                                                )
                                                        // endregion
                                                )
                                        // endregion
                                )
                        )
                        // endregion
                        // region 相机预设
                        .then(Commands.literal("presets")
                                        // region 预设列表查看
                                        .then(Commands.literal("list")
                                                .executes(CameraCommand::presetsList)
                                        )
                                        // endregion
                                        // region 预设属性查看
                                        .then(Commands.literal("view")
                                                .then(Commands.argument("preset", StringArgumentType.string())
                                                        .suggests(CAMERA_ID)
                                                        .executes(CameraCommand::presetsView)
                                                )
                                        )
                                        // endregion
                                        // region 预设创建
                                        .then(Commands.literal("create")
                                                .then(Commands.argument("presets", StringArgumentType.string())
                                                        .executes(CameraCommand::presetsCreate)
                                                )
                                        )
                                        // endregion
                                        // region 预设删除
                                        .then(Commands.literal("delete")
                                                .then(Commands.argument("presets", StringArgumentType.string())
                                                        .suggests(CAMERA_ID)
                                                        .executes(CameraCommand::presetsDelete)
                                                )
                                        )
                                // endregion
                        )
                // endregion
        );
    }

    private static int stateGet(CommandContext<CommandSourceStack> ctx) {
        String presets = ctx.getArgument("presets", String.class);
        CameraState camera = CmdCamera.instance().get(presets);
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
                        CameraStates.getTranslation(state).withStyle(ChatFormatting.AQUA),
                        Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)),
                false
        );
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int stateSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String presets = ctx.getArgument("presets", String.class);
        int state = ctx.getArgument("state", Integer.class);
        boolean value = ctx.getArgument("value", Boolean.class);
        CmdCamera.instance().updateOrCreate(presets, camera -> camera.setBitState(state, value));
        source.sendSuccess(
                () -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set",
                        Component.literal(presets).withStyle(ChatFormatting.GOLD),
                        CameraStates.getTranslation(state).withStyle(ChatFormatting.AQUA),
                        Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)),
                true
        );
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera presets view " + presets)));
            return click;
        }, false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int stateOptions(CommandContext<CommandSourceStack> ctx) {
        String presets = ctx.getArgument("presets", String.class);
        CameraState camera = CmdCamera.instance().get(presets);
        CommandSourceStack source = ctx.getSource();

        if (camera == null) {
            source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
            return 0;
        }

        int state = ctx.getArgument("state", Integer.class);
        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.title", CameraStates.getTranslation(state).withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " " + CameraStates.getName(state) + " set true")));
            return Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.true").withStyle(ChatFormatting.GREEN).append(" ").append(click);
        }, false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " " + CameraStates.getName(state) + " set false")));
            return Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.false").withStyle(ChatFormatting.RED).append(" ").append(click);
        }, false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int schemeGet(CommandContext<CommandSourceStack> ctx) {
        String presets = ctx.getArgument("presets", String.class);
        CameraState camera = CmdCamera.instance().get(presets);
        CommandSourceStack source = ctx.getSource();

        if (camera == null) {
            source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.get",
                        Component.literal(presets).withStyle(ChatFormatting.GOLD),
                        camera.getScheme().translation().withStyle(ChatFormatting.GREEN)),
                false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int schemeSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String presets = ctx.getArgument("presets", String.class);
        ControlScheme scheme = ctx.getArgument("scheme", ControlScheme.class);
        CmdCamera.instance().updateOrCreate(presets, camera -> camera.setScheme(scheme));
        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.set",
                        Component.literal(presets).withStyle(ChatFormatting.GOLD),
                        scheme.translation().withStyle(ChatFormatting.GREEN)),
                true);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera presets view " + presets)));
            return click;
        }, false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int playerRelativeSet(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String presets = ctx.getArgument("presets", String.class);
        int angle = ctx.getArgument("angle", Integer.class);
        ControlScheme.PLAYER_RELATIVE scheme = ControlScheme.PLAYER_RELATIVE(angle);
        CmdCamera.instance().updateOrCreate(presets, camera -> camera.setScheme(scheme));
        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.set",
                        Component.literal(presets).withStyle(ChatFormatting.GOLD),
                        scheme.translation().withStyle(ChatFormatting.GREEN)),
                true);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera presets view " + presets)));
            return click;
        }, false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int schemeOptions(CommandContext<CommandSourceStack> ctx) {
        String presets = ctx.getArgument("presets", String.class);
        CameraState camera = CmdCamera.instance().get(presets);
        CommandSourceStack source = ctx.getSource();

        if (camera == null) {
            source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.control_scheme.options.title"), false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set vanilla")));
            return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.vanilla").withStyle(ChatFormatting.GREEN).append(" ").append(click);
        }, false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set camera_relative")));
            return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.camera_relative").withStyle(ChatFormatting.GREEN).append(" ").append(click);
        }, false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set camera_relative_strafe")));
            return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.camera_relative_strafe").withStyle(ChatFormatting.GREEN).append(" ").append(click);
        }, false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.Custom(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "player_relative_setting"), Optional.of(StringTag.valueOf(presets)))));
            return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.player_relative", "0").withStyle(ChatFormatting.GREEN).append(" ").append(click);
        }, false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.options.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + presets + " control_scheme set player_relative_strafe")));
            return Component.translatable(FreeCameraApiTripod.MODID + ".control_scheme.player_relative_strafe").withStyle(ChatFormatting.GREEN).append(" ").append(click);
        }, false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int presetsList(CommandContext<CommandSourceStack> ctx) {
        Set<String> allId = CmdCamera.instance().getAllId();
        CommandSourceStack source = ctx.getSource();

        if (allId.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.list.empty"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.list.title"), false);
        int i = 1;

        for (String presets : allId) {
            int finalI = i;
            source.sendSuccess(() -> {
                        MutableComponent viewClick = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.list.click.view");
                        viewClick.withStyle(ChatFormatting.BLUE);
                        viewClick.setStyle(viewClick.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera presets view " + presets)));

                        MutableComponent deleteClick = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.list.click.delete");
                        deleteClick.withStyle(ChatFormatting.RED);
                        deleteClick.setStyle(deleteClick.getStyle().withClickEvent(new ClickEvent.Custom(ResourceLocation.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "presets_delete"), Optional.of(StringTag.valueOf(presets)))));

                        return Component.literal(String.valueOf(finalI))
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(presets).withStyle(ChatFormatting.GOLD))
                                .append(" ")
                                .append(viewClick)
                                .append(" ")
                                .append(deleteClick);
                    },
                    false);
            i++;
        }

        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int presetsView(CommandContext<CommandSourceStack> ctx) {
        String preset = ctx.getArgument("preset", String.class);
        CameraState camera = CmdCamera.instance().get(preset);
        CommandSourceStack source = ctx.getSource();

        if (camera == null) {
            source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", Component.literal(preset).withStyle(ChatFormatting.GOLD)));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.view.title", Component.literal(preset).withStyle(ChatFormatting.GOLD)), false);
        int state = camera.getState();

        for (Object2IntMap.Entry<String> entry : CameraStates.NAME_STATE.object2IntEntrySet()) {
            source.sendSuccess(() -> {
                boolean value = isStateEnabled(state, entry.getIntValue());
                String id = entry.getKey();
                MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.view.click");
                click.withStyle(ChatFormatting.BLUE);
                click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + preset + " " + id + " options")));
                return CameraStates.getTranslation(id)
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(Boolean.toString(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .append(" ")
                        .append(click);
            }, false);
        }

        MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.view.click");
        click.withStyle(ChatFormatting.BLUE);
        click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera state " + preset + " control_scheme options")));
        source.sendSuccess(() -> CameraStates.getTranslation("control_scheme")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" | ").withStyle(ChatFormatting.WHITE))
                .append(camera.getScheme().translation().withStyle(ChatFormatting.AQUA))
                .append(" ")
                .append(click), false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int presetsCreate(CommandContext<CommandSourceStack> ctx) {
        String presets = ctx.getArgument("presets", String.class);
        CommandSourceStack source = ctx.getSource();

        if (CmdCamera.instance().has(presets)) {
            source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.create.exists", Component.literal(presets).withStyle(ChatFormatting.GOLD)));
            source.sendSuccess(() -> Component.literal("───────────"), false);
            return 0;
        }

        CmdCamera.instance().create(presets);
        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.create.success", Component.literal(presets).withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> {
            MutableComponent click = Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.state.set.click").withStyle(ChatFormatting.BLUE);
            click.setStyle(click.getStyle().withClickEvent(new ClickEvent.RunCommand("/cmd_camera presets view " + presets)));
            return click;
        }, false);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static int presetsDelete(CommandContext<CommandSourceStack> ctx) {
        String presets = ctx.getArgument("presets", String.class);
        CameraState camera = CmdCamera.instance().get(presets);
        CommandSourceStack source = ctx.getSource();

        if (camera == null) {
            source.sendFailure(Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.not_found", presets));
            return 0;
        }

        CmdCamera.instance().remove(presets);
        source.sendSuccess(() -> Component.translatable("commands." + FreeCameraApiTripod.MODID + ".cmd_camera.presets.delete", Component.literal(presets).withStyle(ChatFormatting.GOLD)), true);
        source.sendSuccess(() -> Component.literal("───────────"), false);
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> CAMERA_ID = (ctx, builder) -> {
        for (String id : CmdCamera.instance().getAllId()) {
            builder.suggest(id);
        }

        return builder.buildFuture();
    };

    private static boolean isStateEnabled(int cameraState, int state) {
        return (cameraState & state) != 0;
    }
}