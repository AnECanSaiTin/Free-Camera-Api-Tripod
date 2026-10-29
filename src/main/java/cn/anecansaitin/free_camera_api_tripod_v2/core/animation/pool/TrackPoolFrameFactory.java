package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrameResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 轨道池帧准备：求值全部属性并收集错误。
///
/// 任一属性或文档级相机来源失败时整帧拒绝，不返回部分可用的姿态；失败不降级为关闭或基础值。
@NullMarked
public final class TrackPoolFrameFactory {

    private final TrackPoolEvaluator evaluator;

    public TrackPoolFrameFactory() {
        this(new TrackPoolEvaluator());
    }

    public TrackPoolFrameFactory(TrackPoolEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    /// 用完整显式输入与采样端口准备一帧。
    public TrackPoolFrameResult prepare(TrackPoolSnapshot snapshot,
                                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs inputs,
                                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource samples) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(samples, "samples");
        return prepare(snapshot, inputs, samples, evaluator, inputs.time());
    }

    /// 用完整显式输入准备一帧；没有采样端口。
    public TrackPoolFrameResult prepare(TrackPoolSnapshot snapshot,
                                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs inputs) {
        return prepare(snapshot, inputs,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource.NONE);
    }

    /// 准备一帧；进度 0、世界时间 0。
    public TrackPoolFrameResult prepare(TrackPoolSnapshot snapshot, float time) {
        return prepare(snapshot,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs.at(time),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource.NONE,
                evaluator, time);
    }

    private TrackPoolFrameResult prepare(TrackPoolSnapshot snapshot,
                                         cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs inputs,
                                         cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource samples,
                                         TrackPoolEvaluator evaluator,
                                         float time) {
        Objects.requireNonNull(snapshot, "snapshot");

        List<EvaluatedRig> rigs = evaluator.evaluate(
                snapshot.document(), inputs, samples, snapshot.revision());
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (EvaluatedRig rig : rigs) {
            collect(diagnostics, rig.x());
            collect(diagnostics, rig.y());
            collect(diagnostics, rig.z());
            collect(diagnostics, rig.fov());
            collect(diagnostics, rig.obstacleMargin());
            collect(diagnostics, rig.rotation());
            collect(diagnostics, rig.active());
            collect(diagnostics, rig.obstacleEnabled());
            collect(diagnostics, rig.rect().x());
            collect(diagnostics, rig.rect().y());
            collect(diagnostics, rig.rect().width());
            collect(diagnostics, rig.rect().height());
        }

        EvaluatedRig.EvaluatedValue<CameraSource> cameraSource = evaluator.evaluateCameraSource(
                snapshot.document(), inputs, snapshot.revision());
        collect(diagnostics, cameraSource);

        if (cameraSource.isValue() && cameraSource.value() instanceof CameraSource.Rig sourceRig) {
            EvaluatedRig evaluatedSource = rigs.stream()
                    .filter(rig -> rig.id().equals(sourceRig.rigId()))
                    .findFirst()
                    .orElse(null);

            if (evaluatedSource == null) {
                diagnostics.add(error(snapshot.revision(), "camera_source",
                        "Camera source references a missing rig: " + sourceRig.rigId()));
            } else if (!evaluatedSource.active().isValue() || !evaluatedSource.active().value()) {
                diagnostics.add(error(snapshot.revision(), "camera_source",
                        "Camera source rig is not active: " + sourceRig.rigId()));
            }
        }

        if (!diagnostics.isEmpty()) {
            return new TrackPoolFrameResult.Rejected(diagnostics);
        }

        return new TrackPoolFrameResult.Prepared(new TrackPoolFrame(
                snapshot.revision(), time, rigs, cameraSource,
                snapshot.document().playbackStartSeconds(),
                snapshot.document().playbackEndSeconds()));
    }

    private static void collect(List<Diagnostic> diagnostics, ChannelSample sample) {
        if (sample.isError()) {
            diagnostics.add(Objects.requireNonNull(sample.diagnostic()));
        }
    }

    private static void collect(List<Diagnostic> diagnostics,
                                EvaluatedRig.EvaluatedValue<?> value) {
        if (value.isError()) {
            diagnostics.add(Objects.requireNonNull(value.diagnostic()));
        }
    }

    private static Diagnostic error(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision revision,
                                    String path,
                                    String message) {
        return new Diagnostic(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode.INVALID_EDIT,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity.ERROR,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage.literal(message),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope.Document(revision),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic.Location(null, path),
                null);
    }
}
