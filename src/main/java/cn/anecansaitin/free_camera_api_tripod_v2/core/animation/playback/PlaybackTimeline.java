package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import org.jspecify.annotations.NullMarked;

/// 纯时间推进器：把“动画时间 + 速度 + 循环 + 时长”从播放生命周期中分离出来。
///
/// 本类不读取文档、不触发事件、不执行相机；它只回答“推进 delta 秒后时间是多少”。
/// 正式播放服务在准备帧时使用同一口径，定位不触发事件、暂停不推进时间。
///
/// ## 两个时间读数
///
/// - [clock] / [time]：**播放相位**，循环时已按 [duration] 取模、非循环时夹在结尾。
///   姿态、布局与接触时钟的读数使用它。
/// - [advancedTime]：**未取模的推进终点**。循环播放跨过多个完整周期时，它保留真实推进量，
///   事件展开必须使用 `(上一帧 advancedTime, 本帧 advancedTime]`，不能只看两个已取模相位
///   （主设计 §7.4：跨越多个周期时完整处理各周期，不能只对最终时间取模）。
///
/// 定位与重置会把两个读数一起设到目标时间；暂停不推进。
@NullMarked
public final class PlaybackTimeline {

    private final PlaybackClock clock;

    /// 未取模的推进终点，秒。循环时可能大于 [PlaybackClock#duration]；
    /// 非循环时与 [PlaybackClock#time] 相同，保证不会因持续推进而无限增大。
    private final double advancedTime;

    private PlaybackTimeline(PlaybackClock clock, double advancedTime) {
        this.clock = clock;
        this.advancedTime = advancedTime;
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

        return new PlaybackTimeline(clock, startTime);
    }

    /// 当前读数。
    public PlaybackClock clock() {
        return clock;
    }

    /// 当前动画时间（播放相位），秒。
    public float time() {
        return clock.time();
    }

    /// 未取模的推进终点，秒；事件展开使用本值作为区间终点。
    ///
    /// 非循环时等于 [time]；循环时保留完整推进量，允许大于时长。
    public float advancedTime() {
        return (float) advancedTime;
    }

    /// 是否已到非循环结尾。
    public boolean finished() {
        return clock.isFinished();
    }

    /// 推进一帧。
    ///
    /// 非循环时在 `duration` 处夹住；循环时按 `duration` 取模。零时长动画保持 0。
    /// 无论是否取模，[advancedTime] 都保留推进路径上的真实终点，供事件展开使用
    /// （主设计 §7.4）。
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

        double advanced = advancedTime + (double) deltaSeconds * clock.speed();

        if (clock.loop()) {
            double wrapped = advanced % clock.duration();

            if (wrapped < 0) {
                wrapped += clock.duration();
            }

            return new PlaybackTimeline(new PlaybackClock(
                    (float) wrapped, clock.speed(), true, clock.duration()), advanced);
        }

        double clamped = Math.min(advanced, clock.duration());
        return new PlaybackTimeline(new PlaybackClock(
                (float) clamped, clock.speed(), false, clock.duration()), clamped);
    }

    /// 显式定位；不触发事件，不改变循环 / 速度 / 时长。
    ///
    /// 定位同时重置 [advancedTime]，避免把定位前的推进量带入下一帧事件区间。
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
                target, clock.speed(), clock.loop(), clock.duration()), target);
    }
}