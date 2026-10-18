package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 播放服务：生命周期、时间推进与帧准备（主设计 §7.1–§7.3）。
///
/// 内核只负责时间、状态、文档空间求值与事件区间准备；不读取世界、不写相机、不执行指令。
/// 请求通过文档校验后由同一文档存储切换文档；编辑器打开时按整份换档处理。
///
/// 失败规则：
///
/// - 任一实际依赖失败 → 终止播放、释放相机、保留失败时刻、不执行本帧事件；
/// - 普通停止与失败终止分开：`stopAndReset()` 重置播放头；`halt(...)` 保留失败信息；
/// - 继续播放前必须重新校验运行条件。
@NullMarked
public interface PlaybackService {

    /// 当前生命周期状态。
    PlaybackState state();

    /// 最近一次失败；未失败时为空。
    @Nullable
    PlaybackFailure failure();

    /// 开始播放。
    ///
    /// 必须**先校验请求并准备初始运行条件**，成功后才切换文档 / 发布序号并进入播放状态；
    /// 失败时原文档、发布序号、播放状态与相机控制均保持不变，不得先换档再回滚。
    ///
    /// @param request          播放请求
    /// @param expectedRevision 当前文档发布序号；切换文档仍走同一存储
    StartResult start(PlaybackRequest request, Revision expectedRevision);

    /// 暂停推进；世界不冻结，定位不触发事件。
    void pause();

    /// 恢复推进。
    void resume();

    /// 显式定位；不触发 Tick 事件，不改变已发布文档。
    ///
    /// @param time 目标动画时间，秒；有限且非负
    void seek(float time);

    /// 普通停止：重置播放头并回到 `IDLE`，不保留失败。
    void stopAndReset();

    /// 失败终止：保留失败时刻与原因，供诊断。
    void halt(PlaybackFailure failure);

    /// 准备一帧；成功返回可执行帧，失败 / 停止时返回空并把失败写入 [failure]。
    ///
    /// @param frameIndex   帧序号，非负
    /// @param deltaSeconds 距上一帧的推进量，秒；有限且非负
    @Nullable
    PreparedFrame prepareFrame(long frameIndex, float deltaSeconds);

    /// 开始结果。
    ///
    /// 拒绝 / 启动失败时 `failure` 非空，且调用前后文档、发布序号、播放状态与相机控制不变。
    ///
    /// @param accepted 是否接受并进入播放
    /// @param failure  拒绝或启动失败原因；成功时为空
    record StartResult(boolean accepted, @Nullable PlaybackFailure failure) {

        public StartResult {
            if (accepted && failure != null) {
                throw new IllegalArgumentException("Accepted StartResult must not carry a failure");
            }

            if (!accepted && failure == null) {
                throw new IllegalArgumentException("Rejected StartResult must carry a failure");
            }
        }
    }
}
