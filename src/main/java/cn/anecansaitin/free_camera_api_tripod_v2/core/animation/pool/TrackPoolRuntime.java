package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrameResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.ObstacleResolver;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 轨道池运行时：播放控制器 + 帧合成 + 宿主输入登记的 JVM 侧组合。
///
/// 不包含客户端渲染、线程驱动或世界查询；宿主每帧提供基础姿态、默认矩形、锚点与世界时间，
/// 然后读取 Prepared/Rejected。默认关闭的机位不产生姿态。
@NullMarked
public final class TrackPoolRuntime {

    private final TrackPoolDocumentStore store;
    private final TrackPoolPlaybackController playback;
    private final TrackPoolFrameComposer composer;
    private final Map<RigId, WorldPose> basePoses = new LinkedHashMap<>();
    private final Map<RigId, ResolvedRect> defaultRects = new LinkedHashMap<>();
    private final Map<RigId, WorldPose> anchors = new LinkedHashMap<>();
    private @Nullable ObstacleResolver obstacleResolver;

    public TrackPoolRuntime(TrackPoolDocumentStore store) {
        this(store, new TrackPoolFrameFactory(), TrackPoolSampleSource.NONE);
    }

    public TrackPoolRuntime(TrackPoolDocumentStore store,
                            TrackPoolFrameFactory frames,
                            TrackPoolSampleSource samples) {
        this.store = Objects.requireNonNull(store, "store");
        this.playback = new TrackPoolPlaybackController(store, frames, samples);
        this.composer = new TrackPoolFrameComposer();
    }

    public TrackPoolPlaybackController playback() {
        return playback;
    }

    public void basePose(RigId rigId, WorldPose pose) {
        basePoses.put(Objects.requireNonNull(rigId, "rigId"), Objects.requireNonNull(pose, "pose"));
    }

    public void defaultRect(RigId rigId, ResolvedRect rect) {
        defaultRects.put(Objects.requireNonNull(rigId, "rigId"), Objects.requireNonNull(rect, "rect"));
    }

    public void anchor(RigId rigId, WorldPose pose) {
        anchors.put(Objects.requireNonNull(rigId, "rigId"), Objects.requireNonNull(pose, "pose"));
    }

    public void obstacleResolver(@Nullable ObstacleResolver resolver) {
        this.obstacleResolver = resolver;
    }

    /// 推进并合成。
    public Result tick(float deltaSeconds, double worldTime) {
        TrackPoolFrameResult frameResult = playback.tick(deltaSeconds, worldTime);

        if (frameResult instanceof TrackPoolFrameResult.Rejected rejected) {
            return new Result.Rejected(rejected.diagnostics());
        }

        TrackPoolFrame frame = ((TrackPoolFrameResult.Prepared) frameResult).frame();

        try {
            Map<RigId, PooledRig> rigs = new LinkedHashMap<>();

            for (PooledRig rig : store.snapshot().document().rigs()) {
                rigs.put(rig.id(), rig);
            }

            List<RigPose> poses = composer.composeVisible(frame, rigs, basePoses, defaultRects,
                    anchors, obstacleResolver);
            return new Result.Prepared(frame, poses);
        } catch (IllegalArgumentException | NullPointerException exception) {
            Revision revision = store.snapshot().revision();
            Diagnostic diagnostic = new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                    LocalizedMessage.literal("Track-pool runtime could not compose frame: "
                            + exception.getMessage()),
                    new DiagnosticScope.Document(revision), null, null);
            return new Result.Rejected(List.of(diagnostic));
        }
    }

    /// 一次运行结果。
    public sealed interface Result permits Result.Prepared, Result.Rejected {
        record Prepared(TrackPoolFrame frame, List<RigPose> poses) implements Result {
            public Prepared {
                Objects.requireNonNull(frame, "frame");
                poses = List.copyOf(poses);
            }
        }

        record Rejected(List<Diagnostic> diagnostics) implements Result {
            public Rejected {
                diagnostics = List.copyOf(diagnostics);
            }
        }
    }
}
