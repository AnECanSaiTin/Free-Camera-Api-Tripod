package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.DynamicField;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

@NullMarked
public class PathNode implements PathNodec {
    private final Vector3f position;
    // 控制点相对position的偏移量
    private final Vector3f inTangent;
    private final Vector3f outTangent;
    private PathMode pathMode;
    /// 自动平滑：默认开启，开启时打开开关的瞬间以及之后任一侧切线变动，另一侧都会跟着镜像
    private boolean smooth = true;
    /// 挂了公式的坐标 / 切线分量：播放时按公式求值，没挂公式的用上面的固定数值
    private final Map<DynamicField, String> expressions = new EnumMap<>(DynamicField.class);

    public PathNode(Vector3f position) {
        this(position, new Vector3f(), new Vector3f(), PathMode.LINEAR);
    }

    public PathNode(Vector3f position, Vector3f inTangent, Vector3f outTangent, PathMode pathMode) {
        this.position = position;
        this.inTangent = inTangent;
        this.outTangent = outTangent;
        this.pathMode = pathMode;
    }

    public PathNode(Vector3f position, Vector3f inTangent, PathMode pathMode) {
        this.position = position;
        this.inTangent = inTangent;
        this.outTangent = new Vector3f(inTangent).mul(-1);
        this.pathMode = pathMode;
    }

    @Override
    public Vector3fc position() {
        return position;
    }

    public PathNode position(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    @Override
    public Vector3fc inTangent() {
        return inTangent;
    }

    public PathNode inTangent(float x, float y, float z) {
        inTangent.set(x, y, z);

        if (smooth) {
            outTangent.set(-x, -y, -z);
        }

        return this;
    }

    @Override
    public Vector3fc outTangent() {
        return outTangent;
    }

    public PathNode outTangent(float x, float y, float z) {
        outTangent.set(x, y, z);

        if (smooth) {
            inTangent.set(-x, -y, -z);
        }

        return this;
    }

    @Override
    public PathMode pathMode() {
        return pathMode;
    }

    public PathNode pathMode(PathMode pathMode) {
        this.pathMode = pathMode;
        return this;
    }

    @Override
    public boolean smooth() {
        return smooth;
    }

    public PathNode smooth(boolean smooth) {
        this.smooth = smooth;

        if (smooth) {
            outTangent.set(-inTangent.x, -inTangent.y, -inTangent.z);
        }

        return this;
    }

    public PathNode restoreSmooth(boolean smooth) {
        this.smooth = smooth;
        return this;
    }

    // region 动态字段

    /// 该分量挂的公式；没挂返回 null
    public @Nullable String expression(DynamicField field) {
        return expressions.get(field);
    }

    /// 给分量挂公式（null 或空白表示回到固定数值）
    public PathNode expression(DynamicField field, @Nullable String expression) {
        if (expression == null || expression.isBlank()) {
            expressions.remove(field);
        } else {
            expressions.put(field, expression.strip());
        }

        return this;
    }

    /// 该分量是否挂了公式
    public boolean dynamic(DynamicField field) {
        return expressions.containsKey(field);
    }

    /// 公式表副本，供序列化与界面判断使用
    @Override
    public Map<DynamicField, String> expressions() {
        return Map.copyOf(expressions);
    }

    // endregion

    public static PathNode linear(Vector3f position) {
        return new PathNode(position);
    }

    public static PathNode bezier(Vector3f position, Vector3f inTangent, Vector3f outTangent) {
        return new PathNode(position, inTangent, outTangent, PathMode.BEZIER);
    }

    public static PathNode bezier(Vector3f position, Vector3f inTangent) {
        return new PathNode(position, inTangent, PathMode.BEZIER);
    }

    public static PathNode catmullRom(Vector3f position) {
        return new PathNode(position, new Vector3f(), new Vector3f(), PathMode.CATMULL_ROM);
    }
}
