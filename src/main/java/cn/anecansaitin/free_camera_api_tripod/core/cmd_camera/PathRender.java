package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Pathc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.CameraScreens;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;

@NullMarked
@EventBusSubscriber(value = Dist.CLIENT, modid = FreeCameraApiTripod.MODID)
public class PathRender {
    private static final ArrayList<Vector3f> PATH_LINE_VERTEX_CACHE = new ArrayList<>();
    private static final ArrayList<Vector3f> PATH_LINE_NORMAL_CACHE = new ArrayList<>();
    private static final ArrayList<Vector3f> PATH_BOX_VERTEX_CACHE = new ArrayList<>();
    private static final ArrayList<Vec3> PATH_NODE_POS_CACHE = new ArrayList<>();
    private static final ArrayList<Vector3f> CONTROL_POINT_LINE_VERTEX_CACHE = new ArrayList<>(4);
    private static final ArrayList<Vector3f> CONTROL_POINT_LINE_NORMAL_CACHE = new ArrayList<>(2);
    private static final ArrayList<Vector3f> CONTROL_POINT_VERTEX_CACHE = new ArrayList<>(24 * 4);
    private static Vec3 CONTROL_POINT_POS_IN = new Vec3(0, 0, 0);
    private static Vec3 CONTROL_POINT_POS_OUT = new Vec3(0, 0, 0);
    private static final ArrayList<Component> PATH_NODE_TEXT_CACHE = new ArrayList<>();
    private static final Component CONTROL_POINT_TEXT_IN = Component.literal("IN");
    private static final Component CONTROL_POINT_TEXT_OUT = Component.literal("OUT");
    /// 路径线段颜色
    private static final int PATH_LINE_COLOR = 0xFF00FF00;
    /// 线段宽度
    private static final int LINE_WIDTH = 5;
    /// 采样间隔（格），值越小曲线越平滑
    private static final float SAMPLE_INTERVAL = 0.25f;
    /// 最大采样点数
    private static final int MAX_SAMPLES = 10000;
    /// 路径点方块半边长
    private static final float PATH_CUBE_HALF_SIZE = 0.15f;
    /// 路径点方块颜色
    private static final int PATH_CUBE_COLOR = 0xFFFFFFFF;
    /// 路径点方块选中颜色
    private static final int PATH_CUBE_COLOR_SELECTED = 0xFF004EFF;
    /// 控制点线段颜色
    private static final int CONTROL_LINE_COLOR = 0xFFFFFFFF;
    /// 控制点线段宽度
    private static final int CONTROL_LINE_WIDTH = 3;
    /// 控制点方块半边长
    private static final float CONTROL_CUBE_HALF_SIZE = 0.05f;
    /// 控制点方块核心半边长
    private static final float CONTROL_CUBE_CORE_HALF_SIZE = 0.03f;
    /// 控制点方块颜色
    private static final int CONTROL_CUBE_COLOR = 0xFFFFFFFF;
    /// 入控制点方块颜色
    private static final int CONTROL_CUBE_COLOR_IN = 0xFF00FF00;
    /// 出控制点方块颜色
    private static final int CONTROL_CUBE_COLOR_OUT = 0xFFFF0000;
    /// 路径点索引字体颜色
    private static final int PATH_TEXT_COLOR = 0xFF000000;
    /// 路径点字体背景颜色
    private static final int PATH_TEXT_BACKGROUND_COLOR = 0xFFFFFFFF;
    /// 控制点索引字体颜色
    private static final int CONTROL_TEXT_COLOR = 0xFF000000;
    /// 控制点字体背景颜色
    private static final int CONTROL_TEXT_BACKGROUND_COLOR = 0xFFFFFFFF;
    private static boolean DIRTY = true;
    private static boolean DIRTY_SELECTED = false;

    /// 标记路径需要重新采样
    public static void markDirty() {
        DIRTY = true;
    }

    public static void markDirtySelected() {
        DIRTY_SELECTED = true;
    }

    private static boolean canRender() {
        // 只在编辑界面打开时参与世界渲染：视口是抓整帧画面贴回来的，路径只有画进世界才会出现在视口里；
        // 而界面关闭后不渲染，则游戏画面里不会残留路径线。
        // 编辑界面本身不透明，界面之外的区域被遮住，也就不会在 GUI 之外露出路径。
        return CameraScreens.editing() && CmdCamera.INSTANCE.editor().path().size() > 0;
    }

    /// 通过步进采样沿路径均匀取点，生成渲染缓存
    private static void reCache() {
        DIRTY = false;
        DIRTY_SELECTED = false;
        Pathc path = CmdCamera.INSTANCE.editor().path();
        pathBox(path);
        pathTexts();
        pathLine(path);
        controlPointVisuals(path);
    }

    private static void reCacheSelected() {
        DIRTY_SELECTED = false;
        controlPointVisuals(CmdCamera.INSTANCE.editor().path());
    }

    private static void pathLine(Pathc path) {
        int nodeCount = path.size();
        PATH_LINE_VERTEX_CACHE.clear();
        PATH_LINE_NORMAL_CACHE.clear();

        if (nodeCount < 2) {
            return;
        }

        double totalLength = path.totalLength();

        // 总长非正或非有限值时不采样：步数会退化成 0 或异常大的值，换算出来的步长也随之失效
        if (!(totalLength > 0) || !Double.isFinite(totalLength)) {
            return;
        }

        // 根据总弧长计算采样步数，确保每步不超过 SAMPLE_INTERVAL 格
        int steps = (int) Math.ceil(totalLength / SAMPLE_INTERVAL);
        steps = Math.min(steps, MAX_SAMPLES);
        float distanceStep = (float) (totalLength / steps);

        Vector3f prev = new Vector3f();
        Vector3f curr = new Vector3f();

        // 采样起点
        path.evaluate(0, prev);
        PATH_LINE_VERTEX_CACHE.add(new Vector3f(prev));

        for (int i = 1; i <= steps; i++) {
            path.evaluate(i * distanceStep, curr);

            // 计算当前线段的方向作为法线
            Vector3f normal = new Vector3f(curr).sub(prev);
            float len = normal.length();

            if (len > 1e-6f) {
                normal.mul(1.0f / len);
            } else {
                // 零长度回退
                normal.set(0, 1, 0);
            }

            PATH_LINE_NORMAL_CACHE.add(normal);
            PATH_LINE_VERTEX_CACHE.add(new Vector3f(curr));
            prev.set(curr);
        }
    }

    /// 缓存路径点方块顶点
    private static void pathBox(Pathc path) {
        PATH_BOX_VERTEX_CACHE.clear();
        PATH_NODE_POS_CACHE.clear();

        for (int i = 0; i < path.size(); i++) {
            PathNodec node = path.node(i);
            Vector3fc pos = node.position();
            PATH_NODE_POS_CACHE.add(new Vec3(pos));
            addCube(pos, PATH_CUBE_HALF_SIZE, PATH_BOX_VERTEX_CACHE);
        }
    }

    /// 缓存路径点名称文本
    private static void pathTexts() {
        int need = PATH_NODE_POS_CACHE.size();

        if (PATH_NODE_TEXT_CACHE.size() < need) {
            int num = PATH_NODE_TEXT_CACHE.isEmpty() ? 0
                    : Integer.parseInt(PATH_NODE_TEXT_CACHE.getLast().getString()) + 1;

            for (; num < need; num++) {
                PATH_NODE_TEXT_CACHE.add(Component.literal(String.valueOf(num)));
            }
        }
    }

    /// 缓存控制点方块顶点和控制线。
    ///
    /// 控制点（入/出切线）只有贝塞尔节点才有意义：其它模式下节点之间是直线或样条连接，
    /// 切线并不参与成形，画出来只会误导，因此这里直接不生成。
    private static void controlPointVisuals(Pathc path) {
        CONTROL_POINT_VERTEX_CACHE.clear();
        CONTROL_POINT_LINE_VERTEX_CACHE.clear();
        CONTROL_POINT_LINE_NORMAL_CACHE.clear();
        Selected selected = CmdCamera.INSTANCE.editor().selectedPathNode();
        int selectedIndex = selected.index();

        if (selectedIndex < 0 || selectedIndex >= path.size()) {
            return;
        }

        PathNodec node = path.node(selectedIndex);

        if (node.pathMode() != PathMode.BEZIER) {
            return;
        }

        Vector3fc pos = node.position();
        Vector3f in = new Vector3f(pos).add(node.inTangent());
        Vector3f out = new Vector3f(pos).add(node.outTangent());
        addNegativeCube(in, CONTROL_CUBE_HALF_SIZE, CONTROL_POINT_VERTEX_CACHE);
        addNegativeCube(out, CONTROL_CUBE_HALF_SIZE, CONTROL_POINT_VERTEX_CACHE);
        addCube(in, CONTROL_CUBE_CORE_HALF_SIZE, CONTROL_POINT_VERTEX_CACHE);
        addCube(out, CONTROL_CUBE_CORE_HALF_SIZE, CONTROL_POINT_VERTEX_CACHE);
        CONTROL_POINT_LINE_VERTEX_CACHE.add(new Vector3f(in));
        CONTROL_POINT_LINE_VERTEX_CACHE.add(new Vector3f(pos));
        CONTROL_POINT_LINE_VERTEX_CACHE.add(new Vector3f(out));
        CONTROL_POINT_LINE_NORMAL_CACHE.add(new Vector3f(pos).sub(in).normalize());
        CONTROL_POINT_LINE_NORMAL_CACHE.add(new Vector3f(pos).sub(out).normalize());
        CONTROL_POINT_POS_IN = new Vec3(in).subtract(0, 0.7, 0);
        CONTROL_POINT_POS_OUT = new Vec3(out).subtract(0, 0.7, 0);
    }

    private static void addCube(Vector3fc pos, float halfSize, ArrayList<Vector3f> list) {
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, -halfSize));// 北
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, -halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, -halfSize));// 西
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, -halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, -halfSize));// 下
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, +halfSize));// 南
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, -halfSize));// 东
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, +halfSize));// 上
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, -halfSize));
    }

    private static void addNegativeCube(Vector3f pos, float halfSize, ArrayList<Vector3f> list) {
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, -halfSize));// 北
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, -halfSize));// 西
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, -halfSize));// 下
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, -halfSize, +halfSize));// 南
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, -halfSize));
        list.add(new Vector3f(pos).add(+halfSize, -halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, -halfSize));// 东
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, -halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, -halfSize));
        list.add(new Vector3f(pos).add(+halfSize, +halfSize, +halfSize));
        list.add(new Vector3f(pos).add(-halfSize, +halfSize, +halfSize));// 上
    }

    /// 渲染路径线框
    @SubscribeEvent
    public static void render(SubmitCustomGeometryEvent event) {
        if (!canRender()) {
            return;
        }

        if (DIRTY) {
            reCache();
        }

        if (DIRTY_SELECTED) {
            reCacheSelected();
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        CameraRenderState cameraRenderState = event.getLevelRenderState().cameraRenderState;
        Vec3 cameraPos = cameraRenderState.pos;
        poseStack.translate(cameraPos.multiply(-1, -1, -1));
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.lines(),
                LineRenderer.INSTANCE
        );

        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.debugFilledBox(),
                BoxRenderer.INSTANCE
        );

        for (int i = 0; i < PATH_NODE_POS_CACHE.size(); i++) {
            Vec3 pos = PATH_NODE_POS_CACHE.get(i);
            collector.submitNameTag(poseStack, pos, 0, PATH_NODE_TEXT_CACHE.get(i), true, LightCoordsUtil.FULL_BRIGHT, cameraPos.distanceToSqr(pos), cameraRenderState, PATH_TEXT_COLOR, PATH_TEXT_BACKGROUND_COLOR);
        }

        // 控制点标签只在确实画了控制点时提交：
        if (hasControlPoints()) {
            collector.submitNameTag(poseStack, CONTROL_POINT_POS_IN, 0, CONTROL_POINT_TEXT_IN, true, LightCoordsUtil.FULL_BRIGHT, cameraPos.distanceToSqr(CONTROL_POINT_POS_IN), cameraRenderState, CONTROL_TEXT_COLOR, CONTROL_TEXT_BACKGROUND_COLOR);
            collector.submitNameTag(poseStack, CONTROL_POINT_POS_OUT, 0, CONTROL_POINT_TEXT_OUT, true, LightCoordsUtil.FULL_BRIGHT, cameraPos.distanceToSqr(CONTROL_POINT_POS_OUT), cameraRenderState, CONTROL_TEXT_COLOR, CONTROL_TEXT_BACKGROUND_COLOR);
        }

        poseStack.popPose();
    }

    /// 控制点缓存是否填好了。四块方块各 24 个顶点，缺一块就说明当前选中节点没有控制点
    private static boolean hasControlPoints() {
        return CONTROL_POINT_VERTEX_CACHE.size() >= 24 * 4;
    }

    private static class LineRenderer implements SubmitNodeCollector.CustomGeometryRenderer {
        private static final LineRenderer INSTANCE = new LineRenderer();

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer buffer) {
            // 路径线
            for (int i = 0; i < PATH_LINE_VERTEX_CACHE.size() - 1; i++) {
                buffer.addVertex(pose, PATH_LINE_VERTEX_CACHE.get(i))
                        .setColor(PATH_LINE_COLOR)
                        .setLineWidth(LINE_WIDTH)
                        .setNormal(pose, PATH_LINE_NORMAL_CACHE.get(i))
                        .addVertex(pose, PATH_LINE_VERTEX_CACHE.get(i + 1))
                        .setColor(PATH_LINE_COLOR)
                        .setLineWidth(LINE_WIDTH)
                        .setNormal(pose, PATH_LINE_NORMAL_CACHE.get(i));
            }

            // 控制点线
            for (int i = 0; i < CONTROL_POINT_LINE_VERTEX_CACHE.size() - 1; i++) {
                buffer.addVertex(pose, CONTROL_POINT_LINE_VERTEX_CACHE.get(i))
                        .setColor(CONTROL_LINE_COLOR)
                        .setLineWidth(CONTROL_LINE_WIDTH)
                        .setNormal(pose, CONTROL_POINT_LINE_NORMAL_CACHE.get(i))
                        .addVertex(pose, CONTROL_POINT_LINE_VERTEX_CACHE.get(i + 1))
                        .setColor(CONTROL_LINE_COLOR)
                        .setLineWidth(CONTROL_LINE_WIDTH)
                        .setNormal(pose, CONTROL_POINT_LINE_NORMAL_CACHE.get(i));
            }
        }
    }

    private static class BoxRenderer implements SubmitNodeCollector.CustomGeometryRenderer {
        private static final BoxRenderer INSTANCE = new BoxRenderer();

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer buffer) {
            Selected selected = CmdCamera.INSTANCE.editor().selectedPathNode();
            int selectedIndex = selected.index();

            // 路径点
            for (Vector3f pos : PATH_BOX_VERTEX_CACHE) {
                buffer.addVertex(pose, pos)
                        .setColor(PATH_CUBE_COLOR);
            }

            // 控制点：前两块是入/出的外层方块，后两块是它们的核心。
            // 非贝塞尔节点不生成控制点，缓存为空时这段自然什么都不画
            for (int i = 0; i < CONTROL_POINT_VERTEX_CACHE.size(); i++) {
                int color = i < 24 * 2 ? CONTROL_CUBE_COLOR
                        : i < 24 * 3 ? CONTROL_CUBE_COLOR_IN : CONTROL_CUBE_COLOR_OUT;
                buffer.addVertex(pose, CONTROL_POINT_VERTEX_CACHE.get(i))
                        .setColor(color);
            }

            // 选中路径点
            switch (selected.type()) {
                case NODE -> {
                    for (int i = selectedIndex * 24; i < selectedIndex * 24 + 24; i++) {
                        buffer.addVertex(pose, PATH_BOX_VERTEX_CACHE.get(i))
                                .setColor(PATH_CUBE_COLOR_SELECTED);
                    }
                }
                // 下面两块的缓存可能不存在（选中的节点不是贝塞尔模式），用缓存长度收住上界
                case IN -> {
                    for (int i = 0; i < Math.min(24, CONTROL_POINT_VERTEX_CACHE.size()); i++) {
                        buffer.addVertex(pose, CONTROL_POINT_VERTEX_CACHE.get(i))
                                .setColor(PATH_CUBE_COLOR_SELECTED);
                    }
                }
                case OUT -> {
                    for (int i = 24; i < Math.min(24 * 2, CONTROL_POINT_VERTEX_CACHE.size()); i++) {
                        buffer.addVertex(pose, CONTROL_POINT_VERTEX_CACHE.get(i))
                                .setColor(PATH_CUBE_COLOR_SELECTED);
                    }
                }
            }
        }
    }
}
