package cn.anecansaitin.free_camera_api_tripod.core.animation;

import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PathNode {
    private final Vector3f position;
    // 控制点相对position的偏移量
    private final Vector3f inTangent;
    private final Vector3f outTangent;
    private PathMode pathMode;

    public PathNode(Vector3f position, Vector3f inTangent, Vector3f outTangent, PathMode pathMode) {
        this.position = position;
        this.inTangent = inTangent;
        this.outTangent = outTangent;
        this.pathMode = pathMode;
    }

    public Vector3f position() {
        return position;
    }

    public PathNode position(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    public Vector3f inTangent() {
        return inTangent;
    }

    public PathNode inTangent(float x, float y, float z) {
        inTangent.set(x, y, z);
        return this;
    }

    public Vector3f outTangent() {
        return outTangent;
    }

    public PathNode outTangent(float x, float y, float z) {
        outTangent.set(x, y, z);
        return this;
    }

    public PathMode pathMode() {
        return pathMode;
    }

    public PathNode pathMode(PathMode pathMode) {
        this.pathMode = pathMode;
        return this;
    }
}
