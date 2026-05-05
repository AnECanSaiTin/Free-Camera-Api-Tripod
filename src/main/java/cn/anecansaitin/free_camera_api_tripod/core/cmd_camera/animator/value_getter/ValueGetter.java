package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter;

public interface ValueGetter<T> {
    T get(int selfTick, int timeTick, float deltaTick);
}
