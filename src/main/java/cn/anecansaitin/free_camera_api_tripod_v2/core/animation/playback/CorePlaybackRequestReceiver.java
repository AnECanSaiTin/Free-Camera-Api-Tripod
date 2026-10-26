package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackAction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 播放协议动作到本地播放请求的接收端入口（主设计 §8.3）。
///
/// 本类只负责：
///
/// 1. `Stop` → [PlaybackService#stopAndReset]；
/// 2. `Start` 的 `Embedded` 直接取随包文档；`Local` 交给 [AnimationResourceService] 解析；
/// 3. 用 [PlaybackAction.Start#toRequest] 生成执行请求（`Embedded` 必须沿用同一实例）；
/// 4. 交给 [PlaybackService#start] 做基版本、播放参数、启动准备与文档发布校验。
///
/// 因此解码后的随包文档仍会经过播放服务的统一校验；本类不复制文档校验、不构造默认锚点、
/// 不把资源载入失败静默成空文档。`Start` 的覆盖三态与协议规模限制由上游
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackProtocolCodec]
/// 保留，本类不再改写。
///
/// ## 线程
///
/// [PlaybackService#start] / [PlaybackService#stopAndReset] 必须在播放服务宿主线程调用；
/// 本类不切换线程、不排队。`Local` 资源载入可能阻塞，宿主若在网络线程解析，应先完成解析
/// 再进入该入口；本类不提供跨线程队列。
@NullMarked
public final class CorePlaybackRequestReceiver {

    private final AnimationResourceService resources;
    private final PlaybackService playback;

    /// @param resources 本地资源服务；只用于解析 `PlaybackSource.Local`
    /// @param playback  播放服务；所有开始 / 停止动作最终交给它
    public CorePlaybackRequestReceiver(AnimationResourceService resources,
                                       PlaybackService playback) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.playback = Objects.requireNonNull(playback, "playback");
    }

    /// 接收一个已解码动作。
    ///
    /// @param action           已通过协议解码的动作
    /// @param expectedRevision 开始播放时的当前发布序号；`Stop` 可为空
    /// @return 已接受、已停止，或带失败原因的拒绝
    public Result receive(PlaybackAction action, @Nullable Revision expectedRevision) {
        Objects.requireNonNull(action, "action");

        if (action.stop()) {
            playback.stopAndReset();
            return new Result.Accepted();
        }

        PlaybackAction.Start start = action.start().orElseThrow();

        if (expectedRevision == null) {
            return rejected(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Start requires a current document revision", 0f, List.of());
        }

        CameraAnimation animation;

        if (start.source() instanceof PlaybackSource.Embedded embedded) {
            animation = embedded.animation();
        } else if (start.source() instanceof PlaybackSource.Local local) {
            LoadResult loaded;

            try {
                loaded = resources.load(local.resourceId());
            } catch (RuntimeException exception) {
                return rejected(PlaybackFailure.Reason.HOST_CAPABILITY_MISSING,
                        "Local playback resource could not be loaded: " + messageOf(exception),
                        start.startTime(), List.of());
            }

            if (loaded instanceof LoadResult.Rejected rejected) {
                return rejected(PlaybackFailure.Reason.HOST_CAPABILITY_MISSING,
                        "Local playback resource was rejected",
                        start.startTime(), rejected.report().diagnostics());
            }

            animation = ((LoadResult.Loaded) loaded).document();
        } else {
            return rejected(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Unknown playback source: " + start.source().getClass().getName(),
                    start.startTime(), List.of());
        }

        PlaybackRequest request;

        try {
            request = start.toRequest(animation);
        } catch (IllegalArgumentException exception) {
            return rejected(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Playback request construction failed: " + messageOf(exception),
                    start.startTime(), List.of());
        }

        PlaybackService.StartResult result = playback.start(request, expectedRevision);
        return result.accepted()
                ? new Result.Accepted()
                : new Result.Rejected(Objects.requireNonNull(result.failure(), "failure"));
    }

    private static Result rejected(PlaybackFailure.Reason reason,
                                   String message,
                                   float failureTime,
                                   List<Diagnostic> diagnostics) {
        return new Result.Rejected(new PlaybackFailure(
                reason, LocalizedMessage.literal(message), failureTime, diagnostics));
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    /// 接收结果。
    public sealed interface Result permits Result.Accepted, Result.Rejected {

        /// 动作已被接受；`Stop` 也使用该结果。
        record Accepted() implements Result {
        }

        /// 动作被拒绝或启动失败；不会返回部分播放状态。
        ///
        /// @param failure 失败原因
        record Rejected(PlaybackFailure failure) implements Result {

            public Rejected {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }
}
