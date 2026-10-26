package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 播放帧准备端口：把生命周期 / 时钟服务与姿态、布局、事件、事实的求值实现解耦。
///
/// 实现必须只使用传入的 [AnimationSnapshot] 与 [PlaybackClock]，不得重新读取当前 store；
/// 任一路实际依赖失败时应在返回的 [PreparedFrame] 中放 ERROR 诊断，由服务统一转入 `HALTED`。
///
/// ## 启动准备
///
/// [prepareStart] 在**尚未换档**的候选快照上准备初始运行条件（初始事实、锚点、姿态与来源），
/// 返回 ERROR 表示启动必须被拒绝。服务只有在它返回无错误后才切换文档与播放状态；
/// 失败时原文档、序号、播放状态与相机控制保持不变（主设计 §7.1）。
@NullMarked
public interface PlaybackFrameFactory {

    /// 准备一帧。
    ///
    /// @param snapshot    本帧绑定的已发布快照
    /// @param request     启动时的播放请求
    /// @param clock       推进后的共享时钟；姿态与布局使用其**播放相位**
    /// @param fromTime    上一帧的未取模推进终点，秒；有限非负
    /// @param toTime      本帧的未取模推进终点，秒；有限非负且不小于 [fromTime]。
    ///                    事件展开使用 `(fromTime, toTime]`，循环跨多个周期时也完整保留
    /// @param frameIndex  帧序号，非负
    /// @param eventBudget 本帧事件预算
    /// @return 已准备帧；不得返回 null
    PreparedFrame prepare(AnimationSnapshot snapshot,
                          PlaybackRequest request,
                          PlaybackClock clock,
                          float fromTime,
                          float toTime,
                          long frameIndex,
                          EventBudget eventBudget);

    /// 在候选快照上准备启动所需初始条件；不展开事件、不消费事件预算。
    ///
    /// 返回的诊断可以包含警告；只要存在 `ERROR`，服务就必须拒绝启动且不换档。
    ///
    /// @param snapshot 候选文档快照；尚未发布，但已带上本次启动将使用的序号
    /// @param request  播放请求
    /// @param clock    启动时刻的时钟读数
    /// @return 初始准备诊断；空表示可以进入播放
    List<Diagnostic> prepareStart(AnimationSnapshot snapshot,
                                  PlaybackRequest request,
                                  PlaybackClock clock);
}