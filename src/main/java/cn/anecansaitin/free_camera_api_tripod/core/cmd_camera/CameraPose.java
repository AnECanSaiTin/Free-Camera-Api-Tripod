package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

/// 相机姿态：位置、旋转（YXZ）与视场角。
///
/// 播放器与编辑器自由视角都以该结构产出结果，由 {@link CmdCamera} 统一应用到相机修饰器。
@NullMarked
public final class CameraPose {
    private final Vector3f position = new Vector3f();
    private final Vector3f rotation = new Vector3f();
    private float fov = 70f;
    /// 为 false 时表示本次姿态没有位置信息（例如路径为空），应用时跳过位置写入
    private boolean positionValid = true;
    /// 为 false 时表示本次姿态没有 FOV 信息（例如 fov 通道没有关键帧），应用时跳过 FOV 写入
    private boolean fovValid = true;

    public Vector3f position() {
        return position;
    }

    public Vector3f rotation() {
        return rotation;
    }

    public float fov() {
        return fov;
    }

    public CameraPose fov(float fov) {
        this.fov = fov;
        return this;
    }

    public boolean positionValid() {
        return positionValid;
    }

    public CameraPose positionValid(boolean positionValid) {
        this.positionValid = positionValid;
        return this;
    }

    public boolean fovValid() {
        return fovValid;
    }

    public CameraPose fovValid(boolean fovValid) {
        this.fovValid = fovValid;
        return this;
    }

    public CameraPose set(Vector3fc position, Vector3fc rotation, float fov) {
        this.position.set(position);
        this.rotation.set(rotation);
        this.fov = fov;
        this.positionValid = true;
        this.fovValid = true;
        return this;
    }

    public CameraPose set(CameraPose other) {
        return set(other.position, other.rotation, other.fov)
                .positionValid(other.positionValid)
                .fovValid(other.fovValid);
    }
}
