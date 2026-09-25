package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PathNode implements PathNodec {
    private final Vector3f position;
    // 控制点相对position的偏移量
    private final Vector3f inTangent;
    private final Vector3f outTangent;
    private PathMode pathMode;
    /// 自动平滑：默认开启，开启时打开开关的瞬间以及之后任一侧切线变动，另一侧都会跟着镜像
    private boolean smooth = true;

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

    /// 自动平滑开关：开启时立即把出切线对齐到入切线的反向（出 = -入），
    /// 之后任一侧切线变动都会带动另一侧，节点两侧的切线始终共线。
    ///
    /// 只开关不再改切线的话，之前手动拖歪的一对切线会一直留在那里，
    /// 「自动平滑」看起来就像没生效；关闭时不动任何切线，让两侧可以各自独立调整。
    public PathNode smooth(boolean smooth) {
        this.smooth = smooth;

        if (smooth) {
            outTangent.set(-inTangent.x, -inTangent.y, -inTangent.z);
        }

        return this;
    }

    /// 只改开关、不动切线：供反序列化使用。
    ///
    /// 与 {@link #smooth(boolean)} 的区别是不执行「出切线跟随入切线」的对齐：
    /// 读档时文件里的两侧切线按原样恢复，是否共线由文件自己决定（照旧走对齐会把文件里的值改掉）。
    public PathNode restoreSmooth(boolean smooth) {
        this.smooth = smooth;
        return this;
    }

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
