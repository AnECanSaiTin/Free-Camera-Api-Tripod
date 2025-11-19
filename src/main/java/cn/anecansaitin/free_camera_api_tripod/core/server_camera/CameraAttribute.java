package cn.anecansaitin.free_camera_api_tripod.core.server_camera;

import org.joml.Vector3f;

public class CameraAttribute {
    private final Vector3f pos = new Vector3f();
    private final Vector3f rot = new Vector3f();
    private float fov;

    public CameraAttribute pos(Vector3f pos) {
        this.pos.set(pos);
        return this;
    }

    public CameraAttribute rot(Vector3f rot) {
        this.rot.set(rot);
        return this;
    }

    public CameraAttribute fov(float fov) {
        this.fov = fov;
        return this;
    }

    public Vector3f pos() {
        return pos;
    }

    public Vector3f rot() {
        return rot;
    }

    public float fov() {
        return fov;
    }
}
