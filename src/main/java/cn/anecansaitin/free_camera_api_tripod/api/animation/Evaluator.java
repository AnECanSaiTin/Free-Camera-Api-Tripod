package cn.anecansaitin.free_camera_api_tripod.api.animation;

public interface Evaluator<T> {
    String[] properties();
    T build(float... values);
}
