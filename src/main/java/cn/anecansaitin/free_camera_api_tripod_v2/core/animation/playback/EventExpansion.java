package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 一次文档级事件展开的结果。
///
/// 成功时 [events] 是完整的、已按播放推进顺序排好的扁平列表；失败时不携带任何部分事件，
/// 调用方必须整帧丢弃，不能把空列表当成“本帧没有事件”。
///
/// [intervalsUsed] 是本次展开实际消费的区间 / 周期段数：区间从一个时间推进到下一个
/// 周期边界算一段。预算失败时该值只表示已经消费的数量，不表示完整工作量。
///
/// @param events       展开出的事件；失败时为空
/// @param intervalsUsed 已消费的区间 / 周期段数，非负
/// @param failure      失败原因；成功时为 {@code null}
@NullMarked
public record EventExpansion(
        List<PreparedFrame.Event> events,
        int intervalsUsed,
        @Nullable PlaybackFailure failure) {

    public EventExpansion {
        Objects.requireNonNull(events, "events");

        if (intervalsUsed < 0) {
            throw new IllegalArgumentException("intervalsUsed must be non-negative: " + intervalsUsed);
        }

        // 失败结果不允许携带部分事件：执行阶段无法安全地只执行前半段（主设计 §7.4）。
        if (failure != null && !events.isEmpty()) {
            throw new IllegalArgumentException("failed EventExpansion must not carry partial events");
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）。
        events = List.copyOf(events);
    }

    /// 成功结果。
    ///
    /// @param events        完整事件列表
    /// @param intervalsUsed 消费的区间 / 周期段数
    public static EventExpansion success(List<PreparedFrame.Event> events, int intervalsUsed) {
        return new EventExpansion(events, intervalsUsed, null);
    }

    /// 失败结果；不携带任何部分事件。
    ///
    /// @param intervalsUsed 已经消费的区间 / 周期段数
    /// @param failure       失败原因
    public static EventExpansion failed(int intervalsUsed, PlaybackFailure failure) {
        Objects.requireNonNull(failure, "failure");
        return new EventExpansion(List.of(), intervalsUsed, failure);
    }

    /// 是否成功。
    public boolean succeeded() {
        return failure == null;
    }

    /// 是否因事件数量或区间 / 周期数量超过 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget]
    /// 而失败。
    public boolean overBudget() {
        return failure != null && failure.reason() == PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED;
    }
}
