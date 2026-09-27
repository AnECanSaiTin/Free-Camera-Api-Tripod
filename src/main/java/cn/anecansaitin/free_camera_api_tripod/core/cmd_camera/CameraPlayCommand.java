package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationSavedData;
import cn.anecansaitin.free_camera_api_tripod.registry.CameraPlaybackPayload;
import cn.anecansaitin.free_camera_api_tripod.util.CommandBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/// 播放类指令：让指定玩家播放一段动画。
///
/// 编辑类指令都注册在客户端（它们操作客户端的编辑器状态），而「指定玩家」这件事只能在服务端做：
/// 服务端挑人、把播放意图发过去，相机由各客户端自己驱动。
///
/// 两种来源分得清清楚楚：
/// - {@code local}：本地文件在玩家自己的游戏目录里，服务端读不到，因此只把名字发给客户端，由它自己读
/// - {@code storage}：存档数据服务端读得到，读出来连 JSON 一起发过去，客户端不需要有同名文件
///
/// 动画 JSON 自带路径数据，需要路径的动画与纯动画走同一条路，不需要额外参数。
@NullMarked
@EventBusSubscriber(modid = FreeCameraApiTripod.MODID)
public final class CameraPlayCommand {
    /// 参数字面量：包类型与动画名
    private static final String ARG_TARGETS = "targets";
    private static final String ARG_NAME = "name";

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandBuilder builder = new CommandBuilder();
        // 选择器参数：CommandBuilder 的内置解析器里没有实体选择器，这里补一个
        builder.addParser("players", _ -> EntityArgument.players());
        builder.requires(Commands.hasPermission(Commands.LEVEL_ADMINS));
        builder.add("cmd_camera play targets<players> local name<string(word)>", CameraPlayCommand::playLocal);
        builder.add("cmd_camera play targets<players> storage name<string(word)>", CameraPlayCommand::playStorage);
        builder.add("cmd_camera stop targets<players>", CameraPlayCommand::stop);

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(builder.build());
    }

    /// 播玩家本地文件里的动画：只把名字送过去，客户端自己去读
    private static int playLocal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, ARG_TARGETS);
        String name = StringArgumentType.getString(context, ARG_NAME);

        for (ServerPlayer player : targets) {
            player.connection.send(CameraPlaybackPayload.local(name));
        }

        return report(context, targets, "play.local", name);
    }

    /// 播存档里的动画：服务端读出来连 JSON 一起送过去
    private static int playStorage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, ARG_TARGETS);
        String name = StringArgumentType.getString(context, ARG_NAME);
        String json = AnimationSavedData.loadAnimation(
                AnimationSavedData.of(context.getSource().getLevel()), name);

        if (json == null) {
            context.getSource().sendFailure(Component.translatable(key("play.missing"), name));
            return 0;
        }

        CameraPlaybackPayload payload = CameraPlaybackPayload.storage(name, json);

        for (ServerPlayer player : targets) {
            player.connection.send(payload);
        }

        return report(context, targets, "play.storage", name);
    }

    /// 停止播放
    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, ARG_TARGETS);
        CameraPlaybackPayload payload = CameraPlaybackPayload.stop();

        for (ServerPlayer player : targets) {
            player.connection.send(payload);
        }

        context.getSource().sendSuccess(
                () -> Component.translatable(key("stop"), targets.size()), true);
        return targets.size();
    }

    /// 统一的成功回显；返回受影响的玩家数，便于比较器取用
    private static int report(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets,
                              String messageKey, String name) {
        context.getSource().sendSuccess(
                () -> Component.translatable(key(messageKey), targets.size(), name), true);
        return targets.size();
    }

    /// 指令文本的键：与参数报错共用同一前缀，翻译文件里挨在一起好找
    private static String key(String tail) {
        return "commands." + FreeCameraApiTripod.MODID + "." + tail;
    }

    private CameraPlayCommand() {
    }
}
