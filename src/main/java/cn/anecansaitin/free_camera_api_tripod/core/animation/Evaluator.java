package cn.anecansaitin.free_camera_api_tripod.core.animation;

public interface Evaluator<T> {
    String[] properties();
    T build(float... values);
}
