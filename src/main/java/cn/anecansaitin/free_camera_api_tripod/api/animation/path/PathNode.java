package cn.anecansaitin.free_camera_api_tripod.api.animation.path;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

/// 路径节点：坐标与两侧切线，都是固定向量。
///
/// 路径是**纯几何**，节点不参与表达式求值（曲线通道负责随时间变化），
/// 所以这里只有最普通的读写，几何计算、渲染与播放取点读的是同一份坐标。
///
/// 自动平滑（[#smooth]）打开时两侧切线互为反向：改一侧会同时把另一侧写回去。
@NullMarked
public class PathNode implements PathNodec {
    private final Vector3f position;
    private final Vector3f inTangent;
    private final Vector3f outTangent;
    private PathMode pathMode;
    /// 自动平滑：默认开启，开启时打开开关的瞬间以及之后任一侧切线变动，另一侧都会跟着镜像
    private boolean smooth = true;

    public PathNode(Vector3f position) {
        this(position, new Vector3f(), new Vector3f(), PathMode.LINEAR);
    }

    public PathNode(Vector3f position, Vector3f inTangent, Vector3f outTangent, PathMode pathMode) {
        this.position = new Vector3f(position);
        this.inTangent = new Vector3f(inTangent);
        this.outTangent = new Vector3f(outTangent);
        this.pathMode = pathMode;
    }

    public PathNode(Vector3f position, Vector3f inTangent, PathMode pathMode) {
        this(position, inTangent, new Vector3f(inTangent).mul(-1), pathMode);
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

    /// 只改开关、不动切线（供反序列化：文件里两侧切线是分开存的，读的时候不能被镜像覆盖）
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
