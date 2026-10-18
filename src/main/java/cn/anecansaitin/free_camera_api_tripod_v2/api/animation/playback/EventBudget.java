package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 事件执行预算（主设计 §7.4）。
///
/// 每帧限制事件执行数量与区间处理工作量；超限时明确终止并报告，不能静默丢弃。
///
/// @param maxEvents    本帧最多执行的事件数，正整数
/// @param maxIntervals 本帧最多处理的区间 / 周期数，正整数
@NullMarked
public record EventBudget(int maxEvents, int maxIntervals) {

    public EventBudget {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("EventBudget maxEvents must be >= 1: " + maxEvents);
        }

        if (maxIntervals < 1) {
            throw new IllegalArgumentException("EventBudget maxIntervals must be >= 1: " + maxIntervals);
        }
    }
}
