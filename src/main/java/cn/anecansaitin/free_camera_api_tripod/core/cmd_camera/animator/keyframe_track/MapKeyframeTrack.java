package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe_track;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.interpolator.Interpolator;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter.ValueGetter;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MapKeyframeTrack<T> implements KeyframeTrack<T> {
    private final TreeMap<Integer, Keyframe<ValueGetter<T>, T>> keyframes;
    private int timeTick;
    private Keyframe<ValueGetter<T>, T> current;
    private Map.Entry<Integer, Keyframe<ValueGetter<T>, T>> pre;
    private Map.Entry<Integer, Keyframe<ValueGetter<T>, T>> next;

    public MapKeyframeTrack() {
        keyframes = new TreeMap<>();
    }

    public MapKeyframeTrack(TreeMap<Integer, Keyframe<ValueGetter<T>, T>> keyframes) {
        this.keyframes = keyframes;
    }

    @Override
    public T getValue(int timeTick, float deltaTick) {
        if (this.timeTick == timeTick) {
            return getFromCache(deltaTick);
        }

        this.timeTick = timeTick;
        var current = keyframes.get(timeTick);
        var next = keyframes.higherEntry(timeTick);
        this.current = current;
        this.next = next;

        if (current != null) {
            return current.value(next == null ? null : next.getValue(), timeTick, deltaTick);
        }

        var pre = keyframes.lowerEntry(timeTick);
        this.pre = pre;

        if (pre != null) {
            return pre.getValue().value(next == null ? null : next.getValue(), timeTick, deltaTick);
        }

        if (next != null) {
            return next.getValue().value(null, timeTick, deltaTick);
        }

        throw new RuntimeException("No keyframes have been added to the track");
    }

    private T getFromCache(float deltaTick) {
        if (current != null) {
            return current.value(next == null ? null : next.getValue(), timeTick, deltaTick);
        }

        if (pre != null) {
            return pre.getValue().value(next == null ? null : next.getValue(), timeTick, deltaTick);
        }

        if (next != null) {
            return next.getValue().value(null, timeTick, deltaTick);
        }

        throw new RuntimeException("Failed to retrieve keyframe value from cache: both current and pre are null");
    }

    public void putKeyframe(int selfTick, Keyframe<ValueGetter<T>, T> keyframe) {
        keyframes.put(selfTick, keyframe);
        keyframe.selfTick(selfTick);
        timeDirty();
    }

    public void removeKeyframe(int selfTick) {
        if (keyframes.remove(selfTick) != null) {
            timeDirty();
        }
    }

    public void moveKeyframe(int selfTick, int newSelfTick) {
        var keyframe = keyframes.remove(selfTick);

        if (keyframe != null) {
            keyframe.selfTick(newSelfTick);
            keyframes.put(newSelfTick, keyframe);
            timeDirty();
        }
    }

    public void setInterpolator(int selfTick, Interpolator<T> interpolator) {
        var keyframe = keyframes.get(selfTick);

        if (keyframe != null) {
            keyframe.interpolator(interpolator);
        }
        Set<Map.Entry<Integer, Keyframe<ValueGetter<T>, T>>> entries = keyframes.entrySet();
    }

    private void timeDirty() {
        timeTick = -1;
        current = null;
        pre = null;
        next = null;
    }

    public Set<Map.Entry<Integer, Keyframe<ValueGetter<T>, T>>> getKeyframes() {
        return keyframes.entrySet();
    }
}
