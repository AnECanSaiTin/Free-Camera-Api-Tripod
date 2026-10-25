package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import org.jspecify.annotations.NullMarked;

/// 播放帧准备端口：把生命周期 / 时钟服务与姿态、布局、事件、事实的求值实现解耦。
///
/// 实现必须只使用传入的 [AnimationSnapshot] 与 [PlaybackClock]，不得重新读取当前 store；
/// 任一路实际依赖失败时应在返回的 [PreparedFrame] 中放 ERROR 诊断，由服务统一转入 `HALTED`。
@NullMarked
@FunctionalInterface
public interface PlaybackFrameFactory {

    /// 准备一帧。
    ///
    /// @param snapshot   本帧绑定的已发布快照
    /// @param request    启动时的播放请求
    /// @param clock      推进后的共享时钟
    /// @param fromTime   推进前时钟时间；用于展开 `(fromTime, clock.time()]` 事件
    /// @param frameIndex 帧序号，非负
    /// @param eventBudget 本帧事件预算
    /// @return 已准备帧；不得返回 null
    PreparedFrame prepare(AnimationSnapshot snapshot,
                          PlaybackRequest request,
                          PlaybackClock clock,
                          float fromTime,
                          long frameIndex,
                          EventBudget eventBudget);
}
