package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;

/// 播放生命周期状态（主设计 §7.2）。
///
/// 普通停止与失败终止分开：`IDLE` 由 `stopAndReset()` 进入并重置播放头；
/// `HALTED` 由 `halt(...)` 进入，保留失败时刻供诊断。继续播放前必须重新校验运行条件。
@NullMarked
public enum PlaybackState {

    /// 未播放 / 已停止并重置。
    IDLE,

    /// 正常推进或暂停可恢复。
    PLAYING,

    /// 已暂停；世界不冻结，定位不触发事件。
    PAUSED,

    /// 因准备、求值、事件或渲染失败终止；保留失败信息。
    HALTED
}
