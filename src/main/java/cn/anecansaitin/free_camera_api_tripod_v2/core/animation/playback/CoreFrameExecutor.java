package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventDispatcher;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 一帧执行顺序：先应用相机，再按已冻结顺序分派事件。
///
/// 本类只编排两个端口，不做求值、不读当前 store：
///
/// - [CameraFrameSink] 应用来源与机位姿态；异常转为 `RENDER_FAILURE`，不再执行本帧事件；
/// - [EventDispatcher] 执行已展开事件；失败时保留已经发生的事件计数，相机副作用不回滚。
///
/// 准备帧含 ERROR 诊断时整帧拒绝，相机与事件都不执行。执行顺序固定，失败不伪装成成功帧。
@NullMarked
public final class CoreFrameExecutor {

    private final EventDispatcher events;

    /// @param events 事件分派器；必须使用准备帧快照解析事件
    public CoreFrameExecutor(EventDispatcher events) {
        this.events = Objects.requireNonNull(events, "events");
    }

    /// 执行一帧。
    ///
    /// @param frame  已准备帧
    /// @param budget 本帧事件预算
    /// @param sink   相机应用端口
    /// @return 成功或带部分执行信息的失败
    public Result execute(PreparedFrame frame, EventBudget budget, CameraFrameSink sink) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(sink, "sink");

        if (!frame.isExecutable()) {
            return new Result(false, 0, failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Prepared frame contains error diagnostics and cannot execute",
                    frame, frame.diagnostics()));
        }

        try {
            sink.apply(frame.source(), frame.rigs());
        } catch (RuntimeException exception) {
            return new Result(false, 0, failure(PlaybackFailure.Reason.RENDER_FAILURE,
                    "Camera host failed to apply the prepared frame: " + messageOf(exception),
                    frame, List.of()));
        }

        EventDispatcher.Result dispatched = events.dispatch(frame, budget);

        if (!dispatched.succeeded()) {
            return new Result(false, dispatched.dispatched(), dispatched.failure());
        }

        return new Result(true, dispatched.dispatched(), null);
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

    /// 一帧执行结果。
    ///
    /// @param presented       是否完整执行（相机已应用且事件全部成功）
    /// @param dispatchedEvents 已成功分发的事件数；相机失败时为 0
    /// @param failure         失败；成功时为空
    public record Result(boolean presented, int dispatchedEvents, @Nullable PlaybackFailure failure) {

        public Result {
            if (dispatchedEvents < 0) {
                throw new IllegalArgumentException(
                        "CoreFrameExecutor.Result dispatchedEvents must be non-negative");
            }

            if (presented && failure != null) {
                throw new IllegalArgumentException(
                        "Presented frame result must not carry a failure");
            }

            if (!presented && failure == null) {
                throw new IllegalArgumentException(
                        "Failed frame result must carry a failure");
            }
        }
    }
}
