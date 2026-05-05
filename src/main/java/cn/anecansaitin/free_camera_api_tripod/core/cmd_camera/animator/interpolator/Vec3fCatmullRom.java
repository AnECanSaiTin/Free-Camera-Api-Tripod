package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator;

import org.joml.Vector3f;

public class Vec3fCatmullRom implements Interpolator<Vector3f>{
    private final int[] argIndex = new int[]{0};
    private final Vector3f cache = new Vector3f();

    @Override
    public Vector3f calculate(float delta, Vector3f... args) {
        return null;
    }

    @Override
    public int[] argIndex() {
        return new int[0];
    }
}
