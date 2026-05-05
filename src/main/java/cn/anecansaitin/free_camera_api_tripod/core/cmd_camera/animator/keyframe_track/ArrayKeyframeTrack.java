package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe_track;

import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.keyframe.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.animator.value_getter.ValueGetter;
import com.mojang.datafixers.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArrayKeyframeTrack<T> implements KeyframeTrack<T> {
    private final Pair<Integer, Keyframe<ValueGetter<T>, T>>[] keyframes;
    private int timeTick = -1;
    private int index;

    public ArrayKeyframeTrack(Pair<Integer, Keyframe<ValueGetter<T>, T>>[] keyframes) {
        this.keyframes = keyframes;

        if (keyframes.length == 0) {
            throw new RuntimeException("No keyframes available");
        }
    }

    public ArrayKeyframeTrack(List<Pair<Integer, Keyframe<ValueGetter<T>, T>>> keyframes) {
        this(keyframes.toArray(new Pair[0]));
    }

    public ArrayKeyframeTrack(MapKeyframeTrack<T> track) {
        Set<Map.Entry<Integer, Keyframe<ValueGetter<T>, T>>> set = track.getKeyframes();

        if (set.isEmpty()) {
            throw new RuntimeException("No keyframes available");
        }

        keyframes = new Pair[set.size()];
        int i = 0;

        for (var entry : set) {
            keyframes[i++] = Pair.of(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public T getValue(int timeTick, float deltaTick) {
        // 只允许正向播放，确保时间不为负
        if (timeTick < 0) {
            timeTick = 0;
        }

        // 如果时间倒退了，重置索引
        if (timeTick < this.timeTick) {
            index = 0;
        }

        this.timeTick = timeTick;

        // 从上次的索引位置开始查找，避免重复遍历
        // 这里存在假设，pre总是小于等于输入的时间
        for (int i = index; i < keyframes.length; i++) {
            index = i;
            var pre = keyframes[i];
            var next = i + 1 < keyframes.length ? keyframes[i + 1] : null;

            if (next == null) {
                return pre.getSecond().value(null, timeTick, deltaTick);
            }

            if (next.getFirst() > timeTick) {
                return pre.getSecond().value(next.getSecond(), timeTick, deltaTick);
            }

            // 说明时间超过了下个关键帧，跳转到下一个时间片段
        }

        throw new RuntimeException("No keyframes available for time: " + timeTick);
    }
}
