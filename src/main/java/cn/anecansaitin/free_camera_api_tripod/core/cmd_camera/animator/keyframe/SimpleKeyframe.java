package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator.Interpolator;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter.ValueGetter;
import org.jspecify.annotations.Nullable;

public class SimpleKeyframe<G extends ValueGetter<V>, V> implements Keyframe<G, V>{
    private int selfTick;
    private G getter;
    private Interpolator<V> interpolator;

    public SimpleKeyframe(G getter, Interpolator<V> interpolator) {
        this.getter = getter;
        this.interpolator = interpolator;
    }

    @Override
    public int selfTick() {
        return selfTick;
    }

    @Override
    public void selfTick(int selfTick) {
        this.selfTick = selfTick;
    }

    @Override
    public V value(@Nullable Keyframe<?, V> nextKey, int timeTick, float deltaTick) {
        V pre = getter.get(selfTick, timeTick, deltaTick);

        if (nextKey == null) {
            return pre;
        }

        V next = nextKey.value(null, timeTick, deltaTick);
        int preTick = selfTick;
        int nextTick = nextKey.selfTick();
        float delta = (float)(timeTick - preTick) / (nextTick - preTick);
        delta = Math.max(0, Math.min(1, delta + deltaTick));
        return interpolator.calculate(pre, next, delta);
    }

    @Override
    public void getter(G getter) {
        this.getter = getter;
    }

    @Override
    public void interpolator(Interpolator<V> interpolator) {
        this.interpolator = interpolator;
    }
}
