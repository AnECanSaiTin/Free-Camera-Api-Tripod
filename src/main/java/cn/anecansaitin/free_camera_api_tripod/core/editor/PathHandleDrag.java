package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathMode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNodec;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.Selected;
import cn.anecansaitin.free_camera_api_tripod.core.editor.layout.UiRect;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/// 视口里的路径点与贝塞尔控制点拖拽：命中判定、屏幕位移到世界位移的换算与写回。
///
/// 视口画面是「整帧抓取贴回」的画中画，所以世界点得自己投影到贴回的那块矩形上：
/// 相机姿态取当前实际生效的一组值（预览模式由动画驱动，自由视角来自自由姿态），
/// 投影结果与画面矩形一一对应，命中判定与拖拽标记都用它换算。
///
/// 拖拽在「抓取时刻的视空间深度平面」上做反投影：光标加上抓取偏差就是目标点该落到的屏幕位置，
/// 再由 NDC 反算世界坐标。这样画面中心与边缘的手感一致——早先按视线距离缩放的做法越靠屏幕边缘
/// 偏离越大（边缘点的视线比它到视平面的深度长）。
///
/// 命中优先规则：光标落在路径点命中圈内、且某个控制点与路径点在屏幕上几乎重合时，
/// 用修饰键区分——Shift 抓入切线、Ctrl 抓出切线、都不按则抓路径点本身（与当前选中类型无关）；
/// 不重合时控制点只在比路径点更靠近光标时才优先，保证节点旁边挂着控制点时仍抓得住节点。
///
/// 控制点只有贝塞尔节点才有，非贝塞尔节点只做路径点的命中判定，与其渲染保持一致。
public final class PathHandleDrag {
    /// 路径点的命中半径（像素）
    private static final float NODE_HIT_RADIUS = 7f;
    /// 控制点的命中半径（像素）：比路径点略大，因为控制点绘制的方块更小、更难精确点中
    private static final float HANDLE_HIT_RADIUS = 9f;
    /// 控制点与路径点的投影距离小于它时视为重合，改用修饰键区分
    private static final float COINCIDENT_RADIUS = 5f;
    /// 相机到拖拽平面的深度下限：几乎贴脸或位于相机后方时不做换算
    private static final float MIN_DEPTH = 1.0E-3f;
    /// FOV 缺失或异常时的回退值（度）
    private static final float FALLBACK_FOV = 70f;

    private final EditorContext context;
    /// 正在拖拽的目标；为 null 表示未在拖拽
    private Selected.@Nullable Type handle;
    /// 拖拽目标所在的路径点下标
    private int index = -1;
    /// 抓取时刻目标点到相机的视空间深度，整个拖拽都在这个深度的平面上进行
    private float depth;
    /// 抓取时鼠标与目标投影点的屏幕偏差，避免抓取瞬间目标跳到光标正下方
    private float grabOffsetX;
    private float grabOffsetY;

    /// 相机旋转矩阵与由此得到的视线基向量，复用避免每次事件都分配
    private final Matrix3f rotationMatrix = new Matrix3f();
    private final Vector3f forward = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector3f up = new Vector3f();

    public PathHandleDrag(EditorContext context) {
        this.context = context;
    }

    public boolean dragging() {
        return handle != null;
    }

    // region 拖拽

    /// 命中判定：鼠标落在画面内且选中路径点（或它的入 / 出控制点）的命中范围内时开始拖拽。
    ///
    /// 返回 true 表示本次左键由拖拽接管，调用方不要再接管视角。
    public boolean begin(double mouseX, double mouseY, UiRect frame) {
        cancel();

        if (!frame.contains(mouseX, mouseY)) {
            return false;
        }

        Path path = context.editor().path();
        Selected selected = context.editor().selectedPathNode();
        int selectedIndex = selected.index();

        if (selectedIndex < 0 || selectedIndex >= path.size()) {
            return false;
        }

        PathNodec node = path.node(selectedIndex);
        Vector2f nodeScreen = project(node.position(), frame);

        if (nodeScreen == null) {
            return false;
        }

        // 控制点只有贝塞尔节点才有：其它模式下世界渲染与视口都不画控制点，
        // 命中测试也必须一起跳过，否则会点到看不见的东西（表现为凭空拖动）
        boolean bezier = node.pathMode() == PathMode.BEZIER;
        Vector2f inScreen = bezier ? project(handlePosition(node, Selected.Type.IN), frame) : null;
        Vector2f outScreen = bezier ? project(handlePosition(node, Selected.Type.OUT), frame) : null;
        Selected.Type type = pickType(nodeScreen, inScreen, outScreen, selected.type(), mouseX, mouseY);

        if (type == null) {
            return false;
        }

        Vector2f target = switch (type) {
            case NODE -> nodeScreen;
            case IN -> inScreen;
            case OUT -> outScreen;
        };

        if (target == null) {
            return false;
        }

        // 拖拽平面取抓取时刻目标点的视空间深度，之后整个拖拽都保持这个深度
        viewBasis();
        float grabbedDepth = new Vector3f(handlePosition(node, type)).sub(context.currentCameraPosition()).dot(forward);

        if (!Float.isFinite(grabbedDepth) || grabbedDepth <= MIN_DEPTH) {
            return false;
        }

        handle = type;
        index = selectedIndex;
        depth = grabbedDepth;
        grabOffsetX = (float) (target.x - mouseX);
        grabOffsetY = (float) (target.y - mouseY);
        return true;
    }

    /// 拖拽中：把光标位置反投影到拖拽平面上，得到目标点应有的世界坐标。
    ///
    /// 返回是否真的写入了路径数据；目标失效或结果非有限时都直接放弃本次更新。
    public boolean update(double mouseX, double mouseY, UiRect frame) {
        Selected.Type type = handle;

        if (type == null || frame.width() <= 0 || frame.height() <= 0) {
            return false;
        }

        Path path = context.editor().path();

        if (index < 0 || index >= path.size()) {
            cancel();
            return false;
        }

        // 光标加上抓取偏差，就是目标点此刻应该落在的屏幕位置；
        // 画面之外没有对应的世界点，因此夹在画面内，避免把路径点拖到视口显示的画面之外
        double targetX = clamp(mouseX + grabOffsetX, frame.x() + 1, frame.right() - 1);
        double targetY = clamp(mouseY + grabOffsetY, frame.y() + 1, frame.bottom() - 1);
        float ndcX = (float) ((targetX - frame.centerX()) * 2.0 / frame.width());
        float ndcY = (float) (-(targetY - frame.centerY()) * 2.0 / frame.height());

        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
            return false;
        }

        viewBasis();
        float tanHalf = tanHalfFov();
        float aspect = (float) frame.width() / frame.height();
        // 视空间反投影：深度固定，横向按 NDC 与视锥张角还原，再叠加相机位置回到世界坐标
        Vector3f target = new Vector3f(forward).mul(depth)
                .add(new Vector3f(right).mul(ndcX * depth * tanHalf * aspect))
                .add(new Vector3f(up).mul(ndcY * depth * tanHalf))
                .add(context.currentCameraPosition());

        if (!isFinite(target)) {
            return false;
        }

        context.editor().updatePathNode(index, updated -> {
            switch (type) {
                // 拖路径点：整点平移，切线是相对偏移因此原样保留
                case NODE -> updated.position(target.x, target.y, target.z);
                // 拖控制点：切线 = 控制点世界位置 - 路径点世界位置
                case IN -> updated.inTangent(target.x - updated.position().x(),
                        target.y - updated.position().y(), target.z - updated.position().z());
                case OUT -> updated.outTangent(target.x - updated.position().x(),
                        target.y - updated.position().y(), target.z - updated.position().z());
            }
        });
        return true;
    }

    /// 结束拖拽并清理状态
    public void cancel() {
        handle = null;
        index = -1;
        grabOffsetX = 0f;
        grabOffsetY = 0f;
    }

    // endregion

    // region 命中

    /// 决定这次拖谁。
    ///
    /// 控制点与路径点在屏幕上几乎重合时（贝塞尔切线为零），光标落在路径点命中圈内改由修饰键决定：
    /// Shift 抓入切线、Ctrl 抓出切线，都不按则按当前选中类型，再不然抓路径点本身。
    /// 其余情况控制点只在比路径点更近时才优先，避免节点旁挂着控制点时抓不住节点。
    private static Selected.@Nullable Type pickType(Vector2f nodeScreen, @Nullable Vector2f inScreen,
                                                    @Nullable Vector2f outScreen, Selected.Type selectedType,
                                                    double mouseX, double mouseY) {
        float nodeDistance = distance(nodeScreen, mouseX, mouseY);
        boolean nodeHit = nodeDistance <= NODE_HIT_RADIUS;
        boolean inCoincident = inScreen != null && nodeScreen.distance(inScreen) < COINCIDENT_RADIUS;
        boolean outCoincident = outScreen != null && nodeScreen.distance(outScreen) < COINCIDENT_RADIUS;

        if (nodeHit && (inCoincident || outCoincident)) {
            if (modifierDown(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                if (inCoincident) {
                    return Selected.Type.IN;
                }

                if (outCoincident) {
                    return Selected.Type.OUT;
                }
            }

            if (modifierDown(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL) && outCoincident) {
                return Selected.Type.OUT;
            }

            if (selectedType == Selected.Type.IN && inCoincident) {
                return Selected.Type.IN;
            }

            if (selectedType == Selected.Type.OUT && outCoincident) {
                return Selected.Type.OUT;
            }

            return Selected.Type.NODE;
        }

        Selected.Type best = null;
        float bestDistance = Float.MAX_VALUE;

        for (Selected.Type type : new Selected.Type[]{Selected.Type.IN, Selected.Type.OUT}) {
            Vector2f screen = type == Selected.Type.IN ? inScreen : outScreen;

            if (screen == null) {
                continue;
            }

            float candidate = distance(screen, mouseX, mouseY);

            if (candidate > HANDLE_HIT_RADIUS || (nodeHit && candidate >= nodeDistance)) {
                continue;
            }

            if (candidate > bestDistance || (candidate == bestDistance && type != selectedType)) {
                continue;
            }

            best = type;
            bestDistance = candidate;
        }

        if (best != null) {
            return best;
        }

        return nodeHit ? Selected.Type.NODE : null;
    }

    private static float distance(Vector2f screen, double mouseX, double mouseY) {
        float deltaX = (float) (mouseX - screen.x);
        float deltaY = (float) (mouseY - screen.y);
        return Mth.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    /// 夹取到 [min, max]。写成 max/min 组合而不是 Math.clamp：调用方可能在画面被压扁时给出 min > max，
    /// 那种情况下 clamp 会直接抛异常
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean modifierDown(int leftKey, int rightKey) {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, leftKey) || InputConstants.isKeyDown(window, rightKey);
    }

    // endregion

    // region 几何

    /// 正在拖拽的目标在画面矩形内的屏幕位置；未拖拽或不可投影时为 null
    public @Nullable Vector2f screenPosition(UiRect frame) {
        Selected.Type type = handle;

        if (type == null) {
            return null;
        }

        Path path = context.editor().path();

        if (index < 0 || index >= path.size()) {
            return null;
        }

        return project(handlePosition(path.node(index), type), frame);
    }

    /// 拖拽目标的世界坐标：路径点就是它自身，控制点是「路径点 + 该侧切线」
    private static Vector3f handlePosition(PathNodec node, Selected.Type type) {
        return switch (type) {
            case NODE -> new Vector3f(node.position());
            case IN -> new Vector3f(node.position()).add(node.inTangent());
            case OUT -> new Vector3f(node.position()).add(node.outTangent());
        };
    }

    /// 世界点投影到画面矩形内的屏幕坐标；位于相机后方或结果非有限时返回 null
    private @Nullable Vector2f project(Vector3fc world, UiRect frame) {
        if (frame.width() <= 0 || frame.height() <= 0) {
            return null;
        }

        viewBasis();
        Vector3f offset = new Vector3f(world).sub(context.currentCameraPosition());
        float depth = offset.dot(forward);

        if (!Float.isFinite(depth) || depth <= MIN_DEPTH) {
            return null;
        }

        float tanHalf = tanHalfFov();
        // 画面矩形与整帧等比，纵横比直接用矩形自身的比例
        float aspect = (float) frame.width() / frame.height();
        float ndcX = offset.dot(right) / (depth * tanHalf * aspect);
        float ndcY = offset.dot(up) / (depth * tanHalf);

        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
            return null;
        }

        return new Vector2f(
                frame.centerX() + ndcX * frame.width() / 2f,
                frame.centerY() - ndcY * frame.height() / 2f);
    }

    /// 由当前相机旋转（度，YXZ：俯仰 / 偏航 / 翻滚）构造视线基向量。
    ///
    /// 与相机自身 {@code Camera.setRotation(yRot, xRot, roll)} 的换算式一致，翻滚也会一并算进去，
    /// 投影结果与抓帧画面严格对齐；右 / 上方向由视线基向量给出，俯仰接近 ±90° 时不会退化成零向量。
    private void viewBasis() {
        Vector3fc rotation = context.currentCameraRotation();
        rotationMatrix.rotationYXZ(
                (float) Math.PI - rotation.y() * Mth.DEG_TO_RAD,
                -rotation.x() * Mth.DEG_TO_RAD,
                -rotation.z() * Mth.DEG_TO_RAD);
        forward.set(0f, 0f, -1f);
        rotationMatrix.transform(forward);
        up.set(0f, 1f, 0f);
        rotationMatrix.transform(up);
        right.set(1f, 0f, 0f);
        rotationMatrix.transform(right);
    }

    /// tan(fov / 2)，FOV 缺失或异常时回退到默认值
    private float tanHalfFov() {
        float fov = context.currentCameraFov();

        if (!Float.isFinite(fov) || fov <= 1f || fov >= 179f) {
            fov = FALLBACK_FOV;
        }

        return (float) Math.tan(fov * 0.5f * Mth.DEG_TO_RAD);
    }

    private static boolean isFinite(Vector3fc vec) {
        return Float.isFinite(vec.x()) && Float.isFinite(vec.y()) && Float.isFinite(vec.z());
    }

    // endregion
}
