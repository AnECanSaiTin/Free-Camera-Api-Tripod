package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventDispatcher;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventExecutor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventExecutorRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 按原发生序分派 `PreparedFrame` 事件。
///
/// 事件已由准备阶段展开并排序；本类只做两件事：
///
/// 1. 按**连续同轨道类型**切分片段，交给注册的 [EventExecutor]；
/// 2. 汇总已成功分发的事件数，任一执行器异常或缺少执行器时立即终止后续分发。
///
/// 不重新排序、不跨类型聚合、不重新读取当前 store。`EventBudget.maxIntervals` 在准备阶段
/// 展开区间时消费；本类只能执行已经生成的扁平事件列表，因此只校验 `maxEvents`。
@NullMarked
public final class CoreEventDispatcher implements EventDispatcher {

    private final EventExecutorRegistry executors;

    /// @param executors 已冻结的事件执行器注册表
    public CoreEventDispatcher(EventExecutorRegistry executors) {
        this.executors = Objects.requireNonNull(executors, "executors");
    }

    @Override
    public Result dispatch(PreparedFrame frame, EventBudget budget) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(budget, "budget");

        if (!frame.isExecutable()) {
            return new Result(0, failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Prepared frame contains error diagnostics and cannot dispatch events",
                    frame, frame.diagnostics()));
        }

        List<PreparedFrame.Event> events = frame.events();

        if (events.size() > budget.maxEvents()) {
            return new Result(0, failure(PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED,
                    "Prepared frame contains " + events.size() + " events, exceeding maxEvents "
                            + budget.maxEvents(), frame));
        }

        Map<TrackId, AnimationTrack> resolved = new HashMap<>();
        int dispatched = 0;
        int index = 0;

        while (index < events.size()) {
            PreparedFrame.Event first = events.get(index);
            AnimationTrack track = resolve(frame, first.trackId(), resolved);

            if (track == null) {
                return new Result(dispatched, failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                        "Prepared frame references a missing event track: " + first.trackId(), frame));
            }

            Identifier type = track.type();
            EventExecutor executor = executors.find(type).orElse(null);

            if (executor == null) {
                return new Result(dispatched, failure(PlaybackFailure.Reason.HOST_CAPABILITY_MISSING,
                        "No event executor is registered for track type " + type, frame));
            }

            int end = index + 1;

            while (end < events.size()) {
                AnimationTrack next = resolve(frame, events.get(end).trackId(), resolved);

                if (next == null) {
                    return new Result(dispatched, failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                            "Prepared frame references a missing event track: "
                                    + events.get(end).trackId(), frame));
                }

                if (!next.type().equals(type)) {
                    break;
                }

                end++;
            }

            List<PreparedFrame.Event> group = List.copyOf(events.subList(index, end));
            Result result;

            try {
                result = executor.execute(frame, group);
            } catch (RuntimeException exception) {
                return new Result(dispatched, failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                        "Event executor for " + type + " failed: " + messageOf(exception), frame));
            }

            if (result == null) {
                return new Result(dispatched, failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                        "Event executor for " + type + " returned no result", frame));
            }

            if (result.dispatched() > group.size()) {
                return new Result(dispatched, failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                        "Event executor for " + type + " reported more events than it received", frame));
            }

            dispatched += result.dispatched();

            if (result.failure() != null) {
                return new Result(dispatched, result.failure());
            }

            index = end;
        }

        return new Result(dispatched, null);
    }

    private static AnimationTrack resolve(PreparedFrame frame,
                                          TrackId trackId,
                                          Map<TrackId, AnimationTrack> cache) {
        if (cache.containsKey(trackId)) {
            return cache.get(trackId);
        }

        AnimationTrack track = frame.snapshot().document().track(trackId).orElse(null);
        cache.put(trackId, track);
        return track;
    }

    private static PlaybackFailure failure(PlaybackFailure.Reason reason,
                                           String message,
                                           PreparedFrame frame) {
        return failure(reason, message, frame, List.of());
    }

    private static PlaybackFailure failure(PlaybackFailure.Reason reason,
                                           String message,
                                           PreparedFrame frame,
                                           List<Diagnostic> diagnostics) {
        return new PlaybackFailure(reason, LocalizedMessage.literal(message),
                frame.clock().time(), diagnostics);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
