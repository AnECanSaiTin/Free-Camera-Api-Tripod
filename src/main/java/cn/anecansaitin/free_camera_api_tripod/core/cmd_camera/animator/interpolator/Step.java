package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator;

import net.minecraft.util.Mth;

public class Step<T> implements Interpolator<T> {
    private final int[] argIndex = new int[]{-1};

    @Override
    public T calculate(float delta, T... args) {
        return args[0];
    }

    @Override
    public int[] argIndex() {
        return argIndex;
    }
}
