package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator;

import net.minecraft.util.Mth;

public class FloatLerp implements Interpolator<Float> {
    private final int[] argIndex = new int[]{-1, 1};

    @Override
    public Float calculate(float delta, Float... args) {
        return Mth.lerp(delta, args[0], args[1]);
    }

    @Override
    public int[] argIndex() {
        return argIndex;
    }
}
