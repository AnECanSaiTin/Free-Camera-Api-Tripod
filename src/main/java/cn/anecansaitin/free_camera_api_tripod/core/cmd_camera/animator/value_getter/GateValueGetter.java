package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter;

public class GateValueGetter<T> implements ValueGetter<T> {
    private T pre;
    private T post;

    public GateValueGetter(T pre, T post) {
        this.pre = pre;
        this.post = post;
    }

    @Override
    public T get(int selfTick, int timeTick, float deltaTick) {
        return timeTick < selfTick ? pre : post;
    }

    public void setPre(T pre) {
        this.pre = pre;
    }

    public void setPost(T post) {
        this.post = post;
    }
}
