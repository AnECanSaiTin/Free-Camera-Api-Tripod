package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;

/// 时间轴吸附源（主设计 §9.1、UI §6.3）。
///
/// 波形峰值 / 节拍候选只有经用户确认形成标记或被明确启用时才参与；等距候选使用固定优先级，
/// 不按遍历顺序随机选择。
@NullMarked
public enum SnapSource {

    /// 当前显示单位的时间网格。
    TIME_GRID,

    /// 其他关键帧。
    KEYFRAMES,

    /// 文档标记。
    MARKERS,

    /// 播放头。
    PLAYHEAD,

    /// 音频偏移起点 / 终点。
    AUDIO_BOUNDS,

    /// 预览区间边界。
    PREVIEW_BOUNDS
}
