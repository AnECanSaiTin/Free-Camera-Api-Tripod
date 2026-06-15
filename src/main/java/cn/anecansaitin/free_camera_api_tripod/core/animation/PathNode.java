package cn.anecansaitin.free_camera_api_tripod.core.animation;

import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PathNode {
    private final Vector3f position;
    private final Vector3f inControl;
    private final Vector3f outControl;
    private PathMode pathMode;

    public PathNode(Vector3f position, Vector3f inControl, Vector3f outControl, PathMode pathMode) {
        this.position = position;
        this.inControl = inControl;
        this.outControl = outControl;
        this.pathMode = pathMode;
    }

    public Vector3f inPosition() {
        return position;
    }

    public PathNode inPosition(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    public Vector3f inControl() {
        return inControl;
    }

    public PathNode inControl(float x, float y, float z) {
        inControl.set(x, y, z);
        return this;
    }

    public Vector3f outControl() {
        return outControl;
    }

    public PathNode outControl(float x, float y, float z) {
        outControl.set(x, y, z);
        return this;
    }

    public PathMode evaluateMode() {
        return pathMode;
    }

    public PathNode evaluateMode(PathMode evaluateMode) {
        this.pathMode = evaluateMode;
        return this;
    }
}
