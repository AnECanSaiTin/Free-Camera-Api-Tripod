package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator.Interpolator;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter.ValueGetter;
import org.jspecify.annotations.Nullable;

public interface Keyframe<G extends ValueGetter<V>, V> {
    int selfTick();
    void selfTick(int selfTick);
    V value(@Nullable Keyframe<?, V> next, int timeTick, float deltaTick);
    void getter(G getter);
    void interpolator(Interpolator<V> interpolator);
}
