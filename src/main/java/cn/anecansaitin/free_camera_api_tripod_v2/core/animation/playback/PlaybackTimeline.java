package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import org.jspecify.annotations.NullMarked;

/// 纯时间推进器：把“动画时间 + 速度 + 循环 + 时长”从播放生命周期中分离出来。
///
/// 本类不读取文档、不触发事件、不执行相机；它只回答“推进 delta 秒后时间是多少”。
/// 正式播放服务在准备帧时使用同一口径，定位不触发事件、暂停不推进时间。
@NullMarked
public final class PlaybackTimeline {

    private final PlaybackClock clock;

    private PlaybackTimeline(PlaybackClock clock) {
        this.clock = clock;
    }

    /// 建立初始时间轴。
    ///
    /// @param duration  可播放时长，秒；有限非负
    /// @param speed     速度，有限正数
    /// @param loop      是否循环
    /// @param startTime 起始时间，秒；必须位于 `[0, duration]`，空动画只接受 0
    public static PlaybackTimeline start(float duration, float speed, boolean loop, float startTime) {
        PlaybackClock clock = new PlaybackClock(startTime, speed, loop, duration);

        if (duration == 0f && startTime != 0f) {
            throw new IllegalArgumentException("Empty animation only accepts startTime 0");
        }

        if (startTime > duration) {
            throw new IllegalArgumentException(
                    "startTime " + startTime + " is outside [0, " + duration + "]");
        }

        return new PlaybackTimeline(clock);
    }

    /// 当前读数。
    public PlaybackClock clock() {
        return clock;
    }

    /// 当前动画时间，秒。
    public float time() {
        return clock.time();
    }

    /// 是否已到非循环结尾。
    public boolean finished() {
        return clock.isFinished();
    }

    /// 推进一帧。
    ///
    /// 非循环时在 `duration` 处夹住；循环时按 `duration` 取模。零时长动画保持 0。
    ///
    /// @param deltaSeconds 距上一帧的推进量，秒；有限非负
    public PlaybackTimeline advance(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException(
                    "deltaSeconds must be finite and non-negative: " + deltaSeconds);
        }

        if (deltaSeconds == 0f || clock.duration() == 0f) {
            return this;
        }

        double advanced = (double) clock.time() + (double) deltaSeconds * clock.speed();

        if (clock.loop()) {
            double wrapped = advanced % clock.duration();

            if (wrapped < 0) {
                wrapped += clock.duration();
            }

            return new PlaybackTimeline(new PlaybackClock(
                    (float) wrapped, clock.speed(), true, clock.duration()));
        }

        float clamped = (float) Math.min(advanced, clock.duration());
        return new PlaybackTimeline(new PlaybackClock(
                clamped, clock.speed(), false, clock.duration()));
    }

    /// 显式定位；不触发事件，不改变循环 / 速度 / 时长。
    ///
    /// @param target 目标时间，秒；有限且位于 `[0, duration]`
    public PlaybackTimeline seek(float target) {
        if (!Float.isFinite(target) || target < 0f) {
            throw new IllegalArgumentException("seek target must be finite and non-negative: " + target);
        }

        if (target > clock.duration()) {
            throw new IllegalArgumentException(
                    "seek target " + target + " is outside [0, " + clock.duration() + "]");
        }

        return new PlaybackTimeline(new PlaybackClock(
                target, clock.speed(), clock.loop(), clock.duration()));
    }
}
