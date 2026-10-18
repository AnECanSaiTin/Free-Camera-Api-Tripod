package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;

/// 时间轴视图参数（主设计 §9.1、UI §6.3）。
///
/// 时间始终以秒存储和计算；缩放、滚动只影响展示，不重写键时间。显示单位换算与逐刻 / 逐帧
/// 步进由面板适配器处理，不改变本类型的秒基准。
///
/// @param pixelsPerSecond 每秒像素数，有限且为正
/// @param scrollSeconds   视口左边界时间，秒；有限且非负
@NullMarked
public record TimelineView(float pixelsPerSecond, float scrollSeconds) {

    public TimelineView {
        if (!Float.isFinite(pixelsPerSecond) || pixelsPerSecond <= 0f) {
            throw new IllegalArgumentException(
                    "TimelineView pixelsPerSecond must be finite and positive: " + pixelsPerSecond);
        }

        if (!Float.isFinite(scrollSeconds) || scrollSeconds < 0f) {
            throw new IllegalArgumentException(
                    "TimelineView scrollSeconds must be finite and non-negative: " + scrollSeconds);
        }
    }

    /// 默认视图。
    public static TimelineView defaults() {
        return new TimelineView(100f, 0f);
    }
}
