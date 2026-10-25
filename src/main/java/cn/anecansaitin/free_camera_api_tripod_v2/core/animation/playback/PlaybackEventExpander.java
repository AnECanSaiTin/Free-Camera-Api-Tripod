package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// 文档级扩展轨道的事件时间展开器（主设计 §7.4）。
///
/// 本类只读取传入的 [AnimationSnapshot]、只处理 `snapshot.document().tracks()` 中
/// **同时实现 [ExtensionTimelineTrack]** 的文档级轨道；其他扩展轨道（例如没有派生时间轴的
/// 第三方事件轨）直接跳过。每个 [ExtensionTimelineTrack.TimelineKey] 当前都按一个事件展开：
/// [PreparedFrame.Event] 只携带 `TrackId + time`，`KeyId` 与事件载荷由执行阶段按快照解析。
///
/// ## 事件顺序
///
/// 1. 先按推进路径的区间 / 周期顺序；
/// 2. 同一区间内按事件时间递增；
/// 3. 同一发生时刻按 `snapshot.document().tracks()` 的文档轨道顺序。
///
/// `EventDispatcher` 原样消费该顺序，不再重新排序或跨类型聚合（主设计 §7.4）。
///
/// ## 区间口径
///
/// - 非循环：展开 `(fromTime, toTime]`，超过播放时长的部分被夹掉；`fromTime == 0` 时
///   额外包含零时刻事件一次（从零开始播放）。
/// - 循环：把 `fromTime → toTime` 视为推进路径。`toTime >= fromTime` 时按该单调路径展开
///   （允许 `toTime > duration` 表示跨多个完整周期）；`toTime < fromTime` 时按**一次**
///   跨 0 回绕展开，即 `(fromTime, duration]` 后接新周期的 `[0, toTime]`。
///   `fromTime == 0` 时包含零时刻事件一次；每跨过一个周期边界（包括终点恰好落在边界上）
///   也包含一次，且每个边界只包含一次。
/// - `fromTime == toTime` 表示没有推进，不产生区间事件；循环模式下也不会把“恰好一整圈”
///   猜成一次完整周回——仅凭两个回绕后的时钟读数无法区分“没有推进”和“推进了整数圈”。
///
/// ## 预算与失败
///
/// [EventBudget.maxIntervals] 在展开区间 / 周期段时消费，`maxIntervals` 超限时在任何事件
/// 生成前失败；[EventBudget.maxEvents] 在生成事件时持续检查。两种超限都返回
/// [EventExpansion#failed] 且**不携带部分事件**（主设计 §7.4：不能先完整展开再交给执行器限流）。
/// 预算失败的原因为 [PlaybackFailure.Reason#EVENT_BUDGET_EXCEEDED]。
///
/// ## 参数
///
/// 时间与时长必须有限且非负；非循环不允许 `toTime < fromTime`（第一版不支持反向播放）。
/// 零时长动画只接受 `0 → 0`。非法参数抛 [IllegalArgumentException]；轨道实现自身读键失败
/// 等运行期异常转为 [EventExpansion#failed]，不向上抛出未包装异常。
///
/// 本类不模拟播放状态：定位、暂停、拖动和试算不产生事件，由调用方决定是否、以及用哪段
/// `fromTime → toTime` 调用本方法。
@NullMarked
public final class PlaybackEventExpander {

    /// 展开一段推进区间上的文档事件。
    ///
    /// @param snapshot 本帧绑定的不可变快照；事件顺序取自其文档的轨道顺序
    /// @param fromTime 推进前动画时间，秒；有限非负
    /// @param toTime   推进后动画时间，秒；有限非负
    /// @param loop     是否循环播放
    /// @param duration 可播放时长，秒；有限非负
    /// @param budget   本帧事件预算
    /// @return 完整展开结果；失败时不含部分事件
    public EventExpansion expand(AnimationSnapshot snapshot,
                                 float fromTime,
                                 float toTime,
                                 boolean loop,
                                 float duration,
                                 EventBudget budget) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(budget, "budget");
        requireFiniteNonNegative(fromTime, "fromTime");
        requireFiniteNonNegative(toTime, "toTime");
        requireFiniteNonNegative(duration, "duration");

        if (!loop && toTime < fromTime) {
            throw new IllegalArgumentException(
                    "Non-looping expansion requires fromTime <= toTime: " + fromTime + " > " + toTime);
        }

        float playableDuration = duration == 0f ? 0f : duration;
        double durationSeconds = playableDuration;

        if (playableDuration == 0f && (fromTime != 0f || toTime != 0f)) {
            throw new IllegalArgumentException(
                    "Zero-length animation only accepts time 0: fromTime=" + fromTime
                            + ", toTime=" + toTime);
        }

        double absoluteTo = loop && toTime < fromTime
                ? loopTarget(fromTime, toTime, durationSeconds)
                : toTime;

        double plannedIntervals = loop
                ? plannedLoopIntervals(fromTime, absoluteTo, durationSeconds)
                : plannedLinearIntervals(fromTime, toTime, durationSeconds);

        if (plannedIntervals > budget.maxIntervals()) {
            return EventExpansion.failed(budget.maxIntervals(), budgetFailure(
                    "Event expansion requires more than maxIntervals " + budget.maxIntervals(),
                    toTime));
        }

        int totalIntervals = (int) plannedIntervals;

        List<TrackTimeline> timelines;

        try {
            timelines = collectTimelines(snapshot.document(), playableDuration);
        } catch (RuntimeException exception) {
            return EventExpansion.failed(0, evaluationFailure(
                    "Failed to read extension timeline keys: " + messageOf(exception), toTime));
        }

        List<PreparedFrame.Event> events = new ArrayList<>();

        // 从零开始播放时单独包含零时刻事件一次；循环的新周期边界在下方逐段处理。
        if (fromTime == 0f && !appendZeroTimeEvents(events, timelines, budget.maxEvents())) {
            return EventExpansion.failed(0, maxEventsFailure(budget.maxEvents(), toTime));
        }

        if (totalIntervals == 0) {
            return EventExpansion.success(events, 0);
        }

        if (!loop) {
            double end = Math.min((double) toTime, durationSeconds);

            if (!appendRange(events, timelines, fromTime, end, budget.maxEvents())) {
                return EventExpansion.failed(1, maxEventsFailure(budget.maxEvents(), toTime));
            }

            return EventExpansion.success(events, 1);
        }

        return expandLoop(events, timelines, fromTime, absoluteTo, durationSeconds,
                budget.maxEvents(), toTime);
    }

    // region 区间规划

    /// 非循环只展开一个 `(from, min(to, duration)]` 区间；没有可推进长度时不消费区间。
    private static double plannedLinearIntervals(float fromTime, float toTime, double duration) {
        if (!(toTime > fromTime) || fromTime >= duration) {
            return 0d;
        }

        return 1d;
    }

    /// 循环区间的段数：按周期边界切开后的正长度段数。
    ///
    /// 起点相位为 `p`、推进 `delta` 时，所需段数为 `ceil((delta + p) / duration)`。
    private static double plannedLoopIntervals(double fromTime,
                                               double absoluteTo,
                                               double duration) {
        if (!(duration > 0d)) {
            return 0d;
        }

        double delta = absoluteTo - fromTime;

        if (!(delta > 0d)) {
            return 0d;
        }

        double phase = fromTime % duration;
        return Math.ceil((delta + phase) / duration);
    }

    /// 把回绕后的终点还原成推进路径上的绝对终点。
    ///
    /// `to >= from` 直接使用 `to`（允许跨多个完整周期）；`to < from` 表示一次跨 0 回绕，
    /// 取大于 `from` 且与 `to` 同余的最小时间。
    private static double loopTarget(double fromTime, double toTime, double duration) {
        if (toTime >= fromTime) {
            return toTime;
        }

        double cycles = Math.floor((fromTime - toTime) / duration);
        double target = toTime + cycles * duration;

        if (target <= fromTime) {
            target += duration;
        }

        return target;
    }

    // endregion

    // region 展开

    /// 逐段展开循环区间；每段结束后处理周期边界上的零时刻事件。
    private EventExpansion expandLoop(List<PreparedFrame.Event> events,
                                      List<TrackTimeline> timelines,
                                      double fromTime,
                                      double absoluteTo,
                                      double duration,
                                      int maxEvents,
                                      float failureTime) {
        int intervalsUsed = 0;
        double cursor = fromTime;
        double phase = cursor % duration;

        while (cursor < absoluteTo) {
            intervalsUsed++;
            double boundary = cursor + (phase == 0d ? duration : duration - phase);
            boolean endsAtBoundary = boundary <= absoluteTo;
            double end = endsAtBoundary ? boundary : absoluteTo;
            double endPhase = endsAtBoundary ? duration : end % duration;

            if (!appendRange(events, timelines, phase, endPhase, maxEvents)) {
                return EventExpansion.failed(intervalsUsed, maxEventsFailure(maxEvents, failureTime));
            }

            if (endsAtBoundary) {
                // 每个新周期边界只展开一次零时刻事件；终点恰好落在边界时也在此包含。
                if (!appendZeroTimeEvents(events, timelines, maxEvents)) {
                    return EventExpansion.failed(intervalsUsed,
                            maxEventsFailure(maxEvents, failureTime));
                }

                cursor = boundary;
                phase = 0d;
            } else {
                cursor = absoluteTo;
            }
        }

        return EventExpansion.success(events, intervalsUsed);
    }

    /// 把 `(from, to]` 内的事件按“时间 → 文档轨道序”追加到结果。
    ///
    /// @return 是否仍在上限内；返回 `false` 表示已经超限，调用方必须丢弃全部结果
    private static boolean appendRange(List<PreparedFrame.Event> events,
                                       List<TrackTimeline> timelines,
                                       double from,
                                       double to,
                                       int maxEvents) {
        List<RangeEvent> matched = new ArrayList<>();

        for (TrackTimeline timeline : timelines) {
            for (float time : timeline.times()) {
                double value = time;

                if (value > from && value <= to) {
                    matched.add(new RangeEvent(time, timeline.order(), timeline.trackId()));
                }
            }
        }

        matched.sort(Comparator.comparingDouble(RangeEvent::time)
                .thenComparingInt(RangeEvent::order));

        for (RangeEvent event : matched) {
            events.add(new PreparedFrame.Event(event.trackId(), event.time()));

            if (events.size() > maxEvents) {
                return false;
            }
        }

        return true;
    }

    /// 按文档轨道顺序追加所有 `time == 0` 的键；每个轨道最多一个（接口要求严格递增）。
    ///
    /// @return 是否仍在上限内；返回 `false` 表示已经超限，调用方必须丢弃全部结果
    private static boolean appendZeroTimeEvents(List<PreparedFrame.Event> events,
                                                List<TrackTimeline> timelines,
                                                int maxEvents) {
        for (TrackTimeline timeline : timelines) {
            float[] times = timeline.times();

            for (float time : times) {
                if (time != 0f) {
                    break;
                }

                events.add(new PreparedFrame.Event(timeline.trackId(), 0f));

                if (events.size() > maxEvents) {
                    return false;
                }
            }
        }

        return true;
    }

    // endregion

    // region 轨道读取

    /// 读取文档级扩展轨道中可用于时间轴展开的键，按文档顺序记录轨道身份与排序后的时间。
    ///
    /// 非 [ExtensionTimelineTrack] 的扩展轨道跳过；时间大于播放时长的键不可能产生事件，
    /// 直接过滤。实现必须返回不可变列表，本方法只读取一次。
    private static List<TrackTimeline> collectTimelines(CameraAnimation document, float duration) {
        List<AnimationTrack.ExtensionTrack> tracks = document.tracks();
        List<TrackTimeline> timelines = new ArrayList<>();

        for (int order = 0; order < tracks.size(); order++) {
            AnimationTrack.ExtensionTrack track = tracks.get(order);

            if (!(track instanceof ExtensionTimelineTrack timelineTrack)) {
                continue;
            }

            List<ExtensionTimelineTrack.TimelineKey> keys = Objects.requireNonNull(
                    timelineTrack.timelineKeys(), "timelineKeys");

            float[] times = new float[keys.size()];
            int size = 0;

            for (ExtensionTimelineTrack.TimelineKey key : keys) {
                Objects.requireNonNull(key, "timeline key");
                float time = key.time();

                if (time > duration) {
                    continue;
                }

                // 负零正规化，避免 PreparedFrame.Event 中出现 -0.0。
                times[size++] = time == 0f ? 0f : time;
            }

            if (size == 0) {
                continue;
            }

            float[] sorted = Arrays.copyOf(times, size);
            Arrays.sort(sorted);
            timelines.add(new TrackTimeline(track.id(), order, sorted));
        }

        return timelines;
    }

    // endregion

    // region 失败构造

    private static PlaybackFailure budgetFailure(String message, float failureTime) {
        return new PlaybackFailure(PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED,
                LocalizedMessage.literal(message), failureTime, List.of());
    }

    private static PlaybackFailure maxEventsFailure(int maxEvents, float failureTime) {
        return budgetFailure("Event expansion exceeds maxEvents " + maxEvents, failureTime);
    }

    private static PlaybackFailure evaluationFailure(String message, float failureTime) {
        return new PlaybackFailure(PlaybackFailure.Reason.EVALUATION_ERROR,
                LocalizedMessage.literal(message), failureTime, List.of());
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    // endregion

    private static void requireFiniteNonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative: " + value);
        }
    }

    /// 一条轨道的只读时间轴：稳定 ID、文档顺序与升序时间。
    private record TrackTimeline(TrackId trackId, int order, float[] times) {
    }

    /// 区间内按时间 / 文档顺序排序前的候选事件。
    private record RangeEvent(float time, int order, TrackId trackId) {
    }
}
