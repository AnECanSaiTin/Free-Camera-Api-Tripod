package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator;

public interface Interpolator<T> {
    T calculate(float delta, T... args);

    /// -2:前第二个
    /// -1:前一个或当前
    /// 1:后一个
    /// 2:后第二个
    /// 以此类推
    int[] argIndex();
    int
}
