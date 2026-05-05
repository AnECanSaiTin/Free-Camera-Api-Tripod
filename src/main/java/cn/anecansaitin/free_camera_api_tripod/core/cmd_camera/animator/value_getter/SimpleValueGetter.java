package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter;

public class SimpleValueGetter<T> implements ValueGetter<T> {
    private T value;

    public SimpleValueGetter(T value) {
        this.value = value;
    }

    @Override
    public T get(int selfTick, int timeTick, float deltaTick) {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }
}