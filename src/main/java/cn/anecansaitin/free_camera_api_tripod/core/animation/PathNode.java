package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.PathMode;
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
    // 自动平滑
    private boolean smooth = true;

    public PathNode(Vector3f position) {
        this(position, new Vector3f(), new Vector3f(), PathMode.LINEAR);
    }

    public PathNode(Vector3f position, Vector3f inTangent, Vector3f outTangent, PathMode pathMode) {
        this.position = position;
        this.inTangent = inTangent;
        this.outTangent = outTangent;
        this.pathMode = pathMode;
        smooth = false;
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
        return new PathNode(position,new Vector3f(), new Vector3f(), PathMode.CATMULL_ROM);
    }
}
