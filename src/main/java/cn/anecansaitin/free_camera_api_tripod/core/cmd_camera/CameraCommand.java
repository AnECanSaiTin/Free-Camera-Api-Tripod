package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.animation.PathMode;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.PathNode;
import cn.anecansaitin.free_camera_api_tripod.core.animation.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.util.CommandBuilder;
import com.mojang.brigadier.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

@EventBusSubscriber(modid = FreeCameraApiTripod.MODID, value = Dist.CLIENT)
public class CameraCommand {
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandBuilder builder = new CommandBuilder();
        builder.requires(Commands.hasPermission(Commands.LEVEL_ADMINS));
        builder.registerEnumClass("Selected.Type", Selected.Type.class);
        builder.registerEnumClass("PathMode", PathMode.class);

        builder.add("cmd_camera path create name<string(word)>", createPath());
        builder.add("cmd_camera path clear", cleanPath());
        builder.add("cmd_camera path name set name<string(word)>", setPathName());
        builder.add("cmd_camera path name get", getPathName());
        builder.add("cmd_camera path select type<enum<Selected.Type>>", selectPathNodeWithType());
        builder.add("cmd_camera path select index<int>", selectPathNode());
        builder.add("cmd_camera path select index<int> type<enum<Selected.Type>>", selectPathNodeWithIndexAndType());
        builder.add("cmd_camera path add", addPathNode());
        builder.add("cmd_camera path add pos<vec3>", addPathNodeWithPos());
        builder.add("cmd_camera path add pos<vec3> index<int>", addPathNodeWithPosAndIndex());
        builder.add("cmd_camera path remove", removePathNode());
        builder.add("cmd_camera path remove index<int>", removePathNodeWithIndex());
        builder.add("cmd_camera path pos get", getPathPos());
        builder.add("cmd_camera path pos get index<int>", getPathPosWithIndex());
        builder.add("cmd_camera path pos get type<enum<Selected.Type>>", getPathPosWithType());
        builder.add("cmd_camera path pos get type<enum<Selected.Type>> index<int>", getPathPosWithTypeAndIndex());
        builder.add("cmd_camera path pos set", setPathPos());
        builder.add("cmd_camera path pos set pos<vec3>", setPathPosWithPos());
        builder.add("cmd_camera path pos set type<enum<Selected.Type>>", setPathPosWithType());
        builder.add("cmd_camera path pos set type<enum<Selected.Type>> pos<vec3>", setPathPosWithTypeAndPos());
        builder.add("cmd_camera path pos set type<enum<Selected.Type>> pos<vec3> index<int>", setPathPosWithTypeAndPosAndIndex());
        builder.add("cmd_camera path mode get", getPathMode());
        builder.add("cmd_camera path mode set mode<enum<PathMode>>", setPathMode());
        builder.add("cmd_camera path mode set mode<enum<PathMode>> index<int>", setPathModeWithIndex());
        builder.add("cmd_camera path auto_smooth get", getPathSmooth());
        builder.add("cmd_camera path auto_smooth set boolean<bool>", setPathSmooth());
        builder.add("cmd_camera path auto_smooth set boolean<bool> index<int>", setPathSmoothWithIndex());

        event.getDispatcher().register(builder.build());
    }

    private static Command<CommandSourceStack> createPath() {
        return (context) -> {
            String name = context.getArgument("name", String.class);
            CmdCamera.INSTANCE.path(new Path(name));
            context.getSource().sendSuccess(() -> Component.literal("Path created"), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> cleanPath() {
        return (context) -> {
            CmdCamera.INSTANCE.path().clear();
            CmdCamera.INSTANCE.selectedPathNode(new Selected(0, Selected.Type.NODE));
            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Path cleared"), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathName() {
        return (context) -> {
            String name = context.getArgument("name", String.class);
            CmdCamera.INSTANCE.path().name(name);
            context.getSource().sendSuccess(() -> Component.literal("Path name set to " + name), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> getPathName() {
        return (context) -> {
            context.getSource().sendSuccess(() -> Component.literal("Path name: " + CmdCamera.INSTANCE.path().name()), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> selectPathNode() {
        return (context) -> {
            int index = context.getArgument("index", Integer.class);
            boolean result = CmdCamera.INSTANCE.selectedPathNode(new Selected(index, Selected.Type.NODE));
            PathRender.markDirtySelected();

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
            } else {
                context.getSource().sendSuccess(() -> Component.literal("Selected path node " + index), false);
            }

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> selectPathNodeWithType() {
        return (context) -> {
            int index = CmdCamera.INSTANCE.selectedPathNode().index();
            Selected.Type type = context.getArgument("type", Selected.Type.class);
            boolean result = CmdCamera.INSTANCE.selectedPathNode(new Selected(index, type));
            PathRender.markDirty();

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
            } else {
                context.getSource().sendSuccess(() -> Component.literal("Selected path node " + index + " with type " + type), false);
            }

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> selectPathNodeWithIndexAndType() {
        return (context) -> {
            int index = context.getArgument("index", Integer.class);
            Selected.Type type = context.getArgument("type", Selected.Type.class);
            boolean result = CmdCamera.INSTANCE.selectedPathNode(new Selected(index, type));
            PathRender.markDirty();

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
            } else {
                context.getSource().sendSuccess(() -> Component.literal("Selected path node " + index + " with type " + type), false);
            }

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> addPathNode() {
        return context -> {
            Vector3f pos = context.getSource().getPosition().toVector3f();
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            cmdCamera.addPosPath(PathNode.catmullRom(pos));
            cmdCamera.selectedPathNode(new Selected(cmdCamera.path().size() - 1, Selected.Type.NODE));
            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Added path node " + pos), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> addPathNodeWithPos() {
        return context -> {
            Vec3 pos = context.getArgument("pos", Coordinates.class).getPosition(context.getSource());
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            cmdCamera.addPosPath(PathNode.catmullRom(pos.toVector3f()));
            cmdCamera.selectedPathNode(new Selected(cmdCamera.path().size() - 1, Selected.Type.NODE));
            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Added path node " + pos), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> addPathNodeWithPosAndIndex() {
        return context -> {
            Vec3 pos = context.getArgument("pos", Coordinates.class).getPosition(context.getSource());
            int index = context.getArgument("index", Integer.class);
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            cmdCamera.insertPosPath(index, PathNode.catmullRom(pos.toVector3f()));
            cmdCamera.selectedPathNode(new Selected(index, Selected.Type.NODE));
            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Added path node " + pos + " at index " + index), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> removePathNode() {
        return context -> {
            int index = CmdCamera.INSTANCE.selectedPathNode().index();
            boolean result = CmdCamera.INSTANCE.removePosPath(index);

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
            } else {
                PathRender.markDirty();
                context.getSource().sendSuccess(() -> Component.literal("Removed path node " + index), false);
            }

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> removePathNodeWithIndex() {
        return context -> {
            int index = context.getArgument("index", Integer.class);
            boolean result = CmdCamera.INSTANCE.removePosPath(index);
            PathRender.markDirty();

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
            } else {
                context.getSource().sendSuccess(() -> Component.literal("Removed path node " + index), false);
            }

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> getPathPos() {
        return context -> {
            Selected selected = CmdCamera.INSTANCE.selectedPathNode();
            Path path = CmdCamera.INSTANCE.path();
            PathNodec node = path.node(selected.index());
            Vector3fc pos = switch (selected.type()) {
                case NODE -> node.position();
                case IN -> node.inTangent().add(node.position(), new Vector3f());
                case OUT -> node.outTangent().add(node.position(), new Vector3f());
            };

            context.getSource().sendSuccess(() -> Component.literal("Path node " + selected.index() + " " + selected.type() + ": " + pos), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> getPathPosWithIndex() {
        return context -> {
            Path path = CmdCamera.INSTANCE.path();
            int index = context.getArgument("index", Integer.class);
            PathNodec node = path.node(index);
            Vector3fc pos = node.position();
            context.getSource().sendSuccess(() -> Component.literal("Path node " + index + " " + Selected.Type.NODE + ": " + pos), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> getPathPosWithType() {
        return context -> {
            Path path = CmdCamera.INSTANCE.path();
            int index = CmdCamera.INSTANCE.selectedPathNode().index();
            Selected.Type type = context.getArgument("type", Selected.Type.class);
            PathNodec node = path.node(index);
            Vector3fc pos = switch (type) {
                case NODE -> node.position();
                case IN -> node.inTangent().add(node.position(), new Vector3f());
                case OUT -> node.outTangent().add(node.position(), new Vector3f());
            };

            context.getSource().sendSuccess(() -> Component.literal("Path node " + index + " " + type + ": " + pos), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> getPathPosWithTypeAndIndex() {
        return context -> {
            Path path = CmdCamera.INSTANCE.path();
            int index = context.getArgument("index", Integer.class);
            Selected.Type type = context.getArgument("type", Selected.Type.class);
            PathNodec node = path.node(index);
            Vector3fc pos = switch (type) {
                case NODE -> node.position();
                case IN -> node.inTangent().add(node.position(), new Vector3f());
                case OUT -> node.outTangent().add(node.position(), new Vector3f());
            };

            context.getSource().sendSuccess(() -> Component.literal("Path node " + index + " " + type + ": " + pos), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathPos() {
        return context -> {
            Vector3f position = context.getSource().getPosition().toVector3f();
            Path path = CmdCamera.INSTANCE.path();
            Selected selected = CmdCamera.INSTANCE.selectedPathNode();
            boolean result = path.updateNode(selected.index(), NODE_POS_UPDATER.set(selected.type(), position));

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
                return Command.SINGLE_SUCCESS;
            }

            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + selected.index() + " " + selected.type() + " to " + position), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathPosWithPos() {
        return context -> {
            Vector3f position = context.getArgument("pos", Coordinates.class).getPosition(context.getSource()).toVector3f();
            Path path = CmdCamera.INSTANCE.path();
            Selected selected = CmdCamera.INSTANCE.selectedPathNode();
            boolean result = path.updateNode(selected.index(), NODE_POS_UPDATER.set(selected.type(), position));

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
                return Command.SINGLE_SUCCESS;
            }

            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + selected.index() + " " + selected.type() + " to " + position), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathPosWithType() {
        return context -> {
            Vector3f position = context.getSource().getPosition().toVector3f();
            int index = CmdCamera.INSTANCE.selectedPathNode().index();
            Selected.Type type = context.getArgument("type", Selected.Type.class);
            Path path = CmdCamera.INSTANCE.path();
            boolean result = path.updateNode(index, NODE_POS_UPDATER.set(type, position));

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
                return Command.SINGLE_SUCCESS;
            }

            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + index + " " + type + " to " + position), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathPosWithTypeAndPos() {
        return context -> {
            Vector3f position = context.getArgument("pos", Coordinates.class).getPosition(context.getSource()).toVector3f();
            int index = CmdCamera.INSTANCE.selectedPathNode().index();
            Selected.Type type = context.getArgument("type", Selected.Type.class);
            Path path = CmdCamera.INSTANCE.path();
            boolean result = path.updateNode(index, NODE_POS_UPDATER.set(type, position));

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
                return Command.SINGLE_SUCCESS;
            }

            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + index + " " + type + " to " + position), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathPosWithTypeAndPosAndIndex() {
        return context -> {
            Vector3f position = context.getArgument("pos", Coordinates.class).getPosition(context.getSource()).toVector3f();
            Selected.Type type = context.getArgument("type", Selected.Type.class);
            int index = context.getArgument("index", Integer.class);
            Path path = CmdCamera.INSTANCE.path();
            boolean result = path.updateNode(index, NODE_POS_UPDATER.set(type, position));

            if (!result) {
                context.getSource().sendFailure(Component.literal("Path node index out of range"));
                return Command.SINGLE_SUCCESS;
            }

            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + index + " " + type + " to " + position), false);

            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> getPathMode() {
        return context -> {
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            Selected selected = cmdCamera.selectedPathNode();
            PathNodec node = cmdCamera.path().node(selected.index());
            context.getSource().sendSuccess(() -> Component.literal("Path node " + selected.index() + " mode: " + node.pathMode()), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathMode() {
        return context -> {
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            Selected selected = cmdCamera.selectedPathNode();
            PathMode mode = context.getArgument("mode", PathMode.class);
            cmdCamera.path().updateNode(selected.index(), NODE_MODE_UPDATER.set(mode));
            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + selected.index() + " mode to " + mode), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathModeWithIndex() {
        return context -> {
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            int index = context.getArgument("index", Integer.class);
            PathMode mode = context.getArgument("mode", PathMode.class);
            cmdCamera.path().updateNode(index, NODE_MODE_UPDATER.set(mode));
            PathRender.markDirty();
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + index + " mode to " + mode), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> getPathSmooth() {
        return context -> {
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            Selected selected = cmdCamera.selectedPathNode();
            PathNodec node = cmdCamera.path().node(selected.index());
            context.getSource().sendSuccess(() -> Component.literal("Path node " + selected.index() + " smooth: " + node.smooth()), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathSmooth() {
        return context -> {
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            Selected selected = cmdCamera.selectedPathNode();
            boolean value = context.getArgument("boolean", Boolean.class);
            cmdCamera.path().updateNode(selected.index(), NODE_SMOOTH_UPDATER.set(value));
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + selected.index() + " smooth to " + value), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Command<CommandSourceStack> setPathSmoothWithIndex() {
        return context -> {
            CmdCamera cmdCamera = CmdCamera.INSTANCE;
            boolean value = context.getArgument("boolean", Boolean.class);
            int index = context.getArgument("index", Integer.class);
            cmdCamera.path().updateNode(index, NODE_SMOOTH_UPDATER.set(value));
            context.getSource().sendSuccess(() -> Component.literal("Set path node " + index + " smooth to " + value), false);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static final NodePosUpdater NODE_POS_UPDATER = new NodePosUpdater();

    private static class NodePosUpdater implements Path.NodeUpdater {
        private Selected.Type type;
        private Vector3f position;

        @Override
        public void update(@NonNull PathNode node) {
            switch (type) {
                case NODE -> node.position(position.x, position.y, position.z);
                case IN -> {
                    position.sub(node.position());
                    node.inTangent(position.x, position.y, position.z);
                }
                case OUT -> {
                    position.sub(node.position());
                    node.outTangent(position.x, position.y, position.z);
                }
            }

            type = null;
            position = null;
        }

        public NodePosUpdater set(Selected.Type type, Vector3f position) {
            this.type = type;
            this.position = position;
            return this;
        }
    }

    private static final NodeModeUpdater NODE_MODE_UPDATER = new NodeModeUpdater();

    private static class NodeModeUpdater implements Path.NodeUpdater {
        private PathMode mode;

        @Override
        public void update(PathNode node) {
            node.pathMode(mode);
            mode = null;
        }

        public NodeModeUpdater set(PathMode mode) {
            this.mode = mode;
            return this;
        }
    }

    private static final NodeSmoothUpdater NODE_SMOOTH_UPDATER = new NodeSmoothUpdater();

    private static class NodeSmoothUpdater implements Path.NodeUpdater {
        private boolean smooth;

        @Override
        public void update(PathNode node) {
            node.smooth(smooth);
        }

        public NodeSmoothUpdater set(boolean smooth) {
            this.smooth = smooth;
            return this;
        }
    }
}
