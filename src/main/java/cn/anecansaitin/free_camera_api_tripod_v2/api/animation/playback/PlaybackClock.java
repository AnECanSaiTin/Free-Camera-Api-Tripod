package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 共享动画时钟的不可变读数（主设计 §7.1、§7.2）。
///
/// 同文档多机位共享同一时钟；正式播放、编辑预览与参考音频同步都读取同一时间轴，
/// 不能让各音轨或各机位自行驱动时间。暂停不等于世界冻结；定位不触发事件。
///
/// @param time     当前动画时间，秒；有限且非负
/// @param speed    播放速度，有限且 `> 0`；第一版不支持反向播放
/// @param loop     是否循环
/// @param duration 文档可播放时长，秒；有限且非负
@NullMarked
public record PlaybackClock(float time, float speed, boolean loop, float duration) {

    public PlaybackClock {
        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("PlaybackClock time must be finite and non-negative: " + time);
        }

        if (!Float.isFinite(speed) || speed <= 0f) {
            throw new IllegalArgumentException("PlaybackClock speed must be finite and positive: " + speed);
        }

        if (!Float.isFinite(duration) || duration < 0f) {
            throw new IllegalArgumentException(
                    "PlaybackClock duration must be finite and non-negative: " + duration);
        }
    }

    /// 非循环时是否已到达 / 越过结尾。
    public boolean isFinished() {
        return !loop && time >= duration;
    }
}
