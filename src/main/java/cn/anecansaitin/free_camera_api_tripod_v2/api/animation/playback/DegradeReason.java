package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 性能 / 兼容性降级原因（主设计 §7.6）。
///
/// 降级不等同求值失败：它是可见、可恢复的运行策略。真正渲染异常必须终止，不能伪装成降级。
@NullMarked
public enum DegradeReason {

    /// 降低内部渲染分辨率。
    RESOLUTION_REDUCED,

    /// 隔帧复用旧画面。
    FRAME_SKIPPED,

    /// 暂时停路，不提交该路画面。
    RIG_PAUSED,

    /// 兼容性问题退回单路显示。
    SINGLE_RIG_FALLBACK
}
