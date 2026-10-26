package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationDocumentStore;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.SessionSwitch;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 播放生命周期与共享时钟服务。
///
/// 本类只负责状态、时钟推进、启停 / 定位 / 失败终止与线程约束；姿态、布局、事件和帧事实
/// 由注入的 [PlaybackFrameFactory] 准备。这样生命周期可以在纯 JVM 测试中验证，宿主再接真实
/// 的帧准备实现。
///
/// ## 线程与重入
///
/// 构造时记录宿主线程，之后所有公开方法只允许该线程调用；其它线程直接抛
/// [IllegalStateException]，不排队、不切换线程。`prepareFrame` 不重新发布文档。
@NullMarked
public final class CorePlaybackService implements PlaybackService {

    private final AnimationDocumentStore store;
    private final PlaybackFrameFactory frameFactory;
    private final Thread ownerThread;

    private PlaybackTimeline timeline = PlaybackTimeline.start(0f, 1f, false, 0f);
    private @Nullable PlaybackRequest request;
    private PlaybackState state = PlaybackState.IDLE;
    private @Nullable PlaybackFailure failure;

    /// @param store        同一文档存储；启动时按请求切换文档
    /// @param frameFactory 帧准备实现；不得重新读取当前 store
    public CorePlaybackService(AnimationDocumentStore store, PlaybackFrameFactory frameFactory) {
        this.store = Objects.requireNonNull(store, "store");
        this.frameFactory = Objects.requireNonNull(frameFactory, "frameFactory");
        this.ownerThread = Thread.currentThread();
    }

    @Override
    public PlaybackState state() {
        requireOwnerThread();
        return state;
    }

    @Override
    public PlaybackClock clock() {
        requireOwnerThread();
        return timeline.clock();
    }

    @Override
    public @Nullable PlaybackFailure failure() {
        requireOwnerThread();
        return failure;
    }

    @Override
    public StartResult start(PlaybackRequest request, Revision expectedRevision) {
        requireOwnerThread();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(expectedRevision, "expectedRevision");

        if (!store.snapshot().revision().equals(expectedRevision)) {
            return startFailure(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Expected revision " + expectedRevision + " but current revision is "
                            + store.snapshot().revision());
        }

        PlaybackTimeline started;

        try {
            started = PlaybackTimeline.start(
                    request.animation().duration(), request.speed(), request.loop(), request.startTime());
        } catch (IllegalArgumentException exception) {
            return startFailure(PlaybackFailure.Reason.EVALUATION_ERROR, exception.getMessage());
        }

        List<Diagnostic> requestDiagnostics = PlaybackRequestValidator.validate(request, expectedRevision);

        if (!requestDiagnostics.isEmpty()) {
            return startRejected(started, "Playback request validation failed", requestDiagnostics);
        }

        AnimationSnapshot current = store.snapshot();
        boolean needsSwitch = !current.document().equals(request.animation());

        // 候选快照只用于启动准备，尚未发布；若需要换档，其序号与 switchDocument 将产生的一致。
        AnimationSnapshot candidate = needsSwitch
                ? new AnimationSnapshot(current.revision().next(), request.animation())
                : current;

        List<Diagnostic> preparationDiagnostics;

        try {
            preparationDiagnostics = frameFactory.prepareStart(candidate, request, started.clock());
        } catch (RuntimeException exception) {
            return startRejected(started,
                    "Initial playback preparation failed: " + messageOf(exception), List.of());
        }

        if (hasError(preparationDiagnostics)) {
            return startRejected(started, "Initial playback preparation failed",
                    preparationDiagnostics);
        }

        // 初始事实、锚点、姿态与来源全部通过后才换档；失败时原文档、序号与播放状态不变。
        if (needsSwitch) {
            EditResult switched = store.switchDocument(
                    request.animation(), current.revision(), SessionSwitch.LOAD);

            if (switched instanceof EditResult.Rejected rejected) {
                return startRejected(started,
                        "Playback request document was rejected by the document store",
                        rejected.diagnostics());
            }
        }

        this.request = request;
        this.timeline = started;
        this.state = PlaybackState.PLAYING;
        this.failure = null;
        return new StartResult(true, null);
    }

    private static StartResult startRejected(PlaybackTimeline started,
                                             String message,
                                             List<Diagnostic> diagnostics) {
        return new StartResult(false, new PlaybackFailure(
                PlaybackFailure.Reason.EVALUATION_ERROR,
                LocalizedMessage.literal(message),
                started.clock().time(), diagnostics));
    }

    @Override
    public void pause() {
        requireOwnerThread();

        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
        }
    }

    @Override
    public void resume() {
        requireOwnerThread();

        if (state == PlaybackState.PAUSED) {
            state = PlaybackState.PLAYING;
        }
    }

    @Override
    public void seek(float time) {
        requireOwnerThread();
        timeline = timeline.seek(time);
    }

    @Override
    public void stopAndReset() {
        requireOwnerThread();
        state = PlaybackState.IDLE;
        failure = null;

        if (request != null) {
            timeline = PlaybackTimeline.start(
                    request.animation().duration(), request.speed(), request.loop(), 0f);
        } else {
            timeline = PlaybackTimeline.start(0f, 1f, false, 0f);
        }
    }

    @Override
    public void halt(PlaybackFailure failure) {
        requireOwnerThread();
        this.failure = Objects.requireNonNull(failure, "failure");
        this.state = PlaybackState.HALTED;
    }

    @Override
    public @Nullable PreparedFrame prepareFrame(long frameIndex, float deltaSeconds, EventBudget eventBudget) {
        requireOwnerThread();
        Objects.requireNonNull(eventBudget, "eventBudget");

        if (frameIndex < 0) {
            throw new IllegalArgumentException("frameIndex must be non-negative: " + frameIndex);
        }

        if (state != PlaybackState.PLAYING || request == null) {
            return null;
        }

        // 使用未取模终点作为事件区间边界：循环跨多个周期时，只比较两个已取模相位会丢失
        // 完整周期的事件（主设计 §7.4）。
        float fromTime = timeline.advancedTime();
        timeline = timeline.advance(deltaSeconds);
        float toTime = timeline.advancedTime();
        PreparedFrame frame;

        try {
            frame = frameFactory.prepare(
                    store.snapshot(), request, timeline.clock(),
                    fromTime, toTime, frameIndex, eventBudget);
        } catch (RuntimeException exception) {
            halt(failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Frame preparation failed: " + messageOf(exception), timeline.time(), List.of()));
            return null;
        }

        if (frame == null) {
            halt(failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Frame factory returned no prepared frame", timeline.time(), List.of()));
            return null;
        }

        if (!frame.isExecutable()) {
            halt(failure(PlaybackFailure.Reason.EVALUATION_ERROR,
                    "Prepared frame contains error diagnostics", frame.clock().time(),
                    frame.diagnostics()));
            return null;
        }

        return frame;
    }

    private StartResult startFailure(PlaybackFailure.Reason reason, String message) {
        PlaybackFailure failure = failure(reason, message, timeline.time(), List.of());
        return new StartResult(false, failure);
    }

    private static boolean hasError(List<Diagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    private static PlaybackFailure failure(PlaybackFailure.Reason reason,
                                           String message,
                                           float time,
                                           List<Diagnostic> diagnostics) {
        return new PlaybackFailure(reason, LocalizedMessage.literal(message), time, diagnostics);
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("PlaybackService must be called on its owner thread");
        }
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
