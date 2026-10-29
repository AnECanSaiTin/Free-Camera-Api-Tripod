package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 全局轨道池第一版结构 / 绑定校验。
///
/// 只检查轨道池模型能静态确定的引用与类型：
/// 属性来源的 TrackId 必须指向兼容轨道；重复 ID 拒绝；文档相机来源绑定必须指向
/// `CameraSourceTrack`；属性公式与曲线关键帧公式做编译检查。类型校验钩子、播放范围时长与
/// 真实宿主能力由后续阶段补。
@NullMarked
public final class TrackPoolValidator {

    /// 校验文档。
    ///
    /// @param document 轨道池文档
    /// @param revision 诊断作用域版本
    /// @return 错误诊断；空表示通过
    public List<Diagnostic> validate(TrackPoolAnimation document, Revision revision) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(revision, "revision");

        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<TrackId, AnimationTrack> tracks = new LinkedHashMap<>();

        for (AnimationTrack track : document.tracks()) {
            if (tracks.putIfAbsent(track.id(), track) != null) {
                diagnostics.add(error(revision, "tracks",
                        "Duplicate track id: " + track.id()));
            }

            if (track instanceof CurveTrack curve && curve.channel() != null) {
                diagnostics.add(error(revision, "tracks",
                        "A global-pool curve track must not carry a camera channel: " + track.id()));
            }

            if (track instanceof cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack) {
                diagnostics.add(error(revision, "tracks",
                        "AimTrack is not part of the global track pool: " + track.id()));
            }

            if (track instanceof AnimationTrack.ExtensionTrack extension
                    && (extension.type().equals(CurveTrack.TYPE)
                    || extension.type().equals(QuaternionTrack.TYPE)
                    || extension.type().equals(ActiveTrack.TYPE)
                    || extension.type().equals(CameraSourceTrack.TYPE))) {
                diagnostics.add(error(revision, "tracks",
                        "Extension track must not claim a built-in type id: " + extension.type()));
            }

            if (track instanceof cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack timeline) {
                validateTimelineKeys(timeline, track.id(), revision, diagnostics);
            }
        }

        Set<RigId> rigIds = new LinkedHashSet<>();

        for (PooledRig rig : document.rigs()) {
            rigIds.add(rig.id());
        }

        validateCameraSource(document.cameraSourceBinding(), tracks, rigIds, revision, diagnostics);
        validateAuxiliaryIds(document, revision, diagnostics);

        for (PooledRig rig : document.rigs()) {
            String base = "rigs[" + rig.id().value() + "].";
            validatePosition(rig.properties().position(), tracks, revision, base, diagnostics);
            validateRotation(rig.properties().rotation(), tracks, revision, base, diagnostics);
            validateFov(rig.properties().fov(), tracks, revision, base, diagnostics);
            validateActive(rig.properties().active(), tracks, revision, base, diagnostics);
            validateRect(rig.properties().rect(), tracks, revision, base, diagnostics);
            validateObstacle(rig.properties().obstacle(), tracks, revision, base, diagnostics);
        }

        diagnostics.addAll(new TrackPoolSymbolValidator().validate(document, revision));
        return List.copyOf(diagnostics);
    }

    private void validateTimelineKeys(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack timeline,
                                      TrackId trackId,
                                      Revision revision,
                                      List<Diagnostic> diagnostics) {
        float previous = Float.NaN;
        Set<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId> keys = new LinkedHashSet<>();

        for (var key : timeline.timelineKeys()) {
            if (!keys.add(key.keyId())) {
                diagnostics.add(error(revision, "tracks",
                        "Extension timeline has duplicate KeyId: " + trackId));
            }

            if (Float.isFinite(previous) && key.time() <= previous) {
                diagnostics.add(error(revision, "tracks",
                        "Extension timeline keys must be strictly increasing: " + trackId));
            }

            previous = key.time();
        }
    }

    private void validateAuxiliaryIds(TrackPoolAnimation document,
                                      Revision revision,
                                      List<Diagnostic> diagnostics) {
        var markerIds = new LinkedHashSet<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId>();
        var sampleIds = new LinkedHashSet<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId>();
        var audioIds = new LinkedHashSet<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.AudioRefId>();
        var sampleNames = new LinkedHashSet<String>();

        for (var marker : document.markers()) {
            if (!markerIds.add(marker.id())) {
                diagnostics.add(error(revision, "markers", "Duplicate marker id: " + marker.id()));
            }
        }

        for (var sample : document.samples()) {
            if (!sampleIds.add(sample.id())) {
                diagnostics.add(error(revision, "samples", "Duplicate sample id: " + sample.id()));
            }

            if (!sampleNames.add(sample.name())) {
                diagnostics.add(error(revision, "samples", "Duplicate sample name: " + sample.name()));
            }
        }

        for (var audio : document.audioRefs()) {
            if (!audioIds.add(audio.id())) {
                diagnostics.add(error(revision, "audio_refs", "Duplicate audio ref id: " + audio.id()));
            }
        }
    }

    private void validatePosition(PositionProperty property,
                                  Map<TrackId, AnimationTrack> tracks,
                                  Revision revision,
                                  String base,
                                  List<Diagnostic> diagnostics) {
        validateNumeric(property.x(), tracks, revision, base + "position.x", diagnostics);
        validateNumeric(property.y(), tracks, revision, base + "position.y", diagnostics);
        validateNumeric(property.z(), tracks, revision, base + "position.z", diagnostics);
    }

    private void validateRotation(RotationProperty property,
                                  Map<TrackId, AnimationTrack> tracks,
                                  Revision revision,
                                  String base,
                                  List<Diagnostic> diagnostics) {
        if (property.source() instanceof PropertyBinding.Enabled<QuaternionSource> enabled) {
            validateQuaternion(enabled.source(), tracks, revision, base + "rotation", diagnostics);
        }
    }

    private void validateFov(FovProperty property,
                             Map<TrackId, AnimationTrack> tracks,
                             Revision revision,
                             String base,
                             List<Diagnostic> diagnostics) {
        validateNumeric(property.source(), tracks, revision, base + "fov", diagnostics);
        requirePositiveConstant(property.source(), revision, base + "fov", diagnostics);
    }

    private void validateActive(ActiveProperty property,
                                Map<TrackId, AnimationTrack> tracks,
                                Revision revision,
                                String base,
                                List<Diagnostic> diagnostics) {
        validateBoolean(property.source(), tracks, revision, base + "active", diagnostics);
    }

    private void validateRect(RectProperty property,
                              Map<TrackId, AnimationTrack> tracks,
                              Revision revision,
                              String base,
                              List<Diagnostic> diagnostics) {
        switch (property) {
            case RectProperty.Normalized normalized -> {
                validateNumeric(normalized.x(), tracks, revision, base + "rect.x", diagnostics);
                validateNumeric(normalized.y(), tracks, revision, base + "rect.y", diagnostics);
                validateNumeric(normalized.width(), tracks, revision, base + "rect.width", diagnostics);
                validateNumeric(normalized.height(), tracks, revision, base + "rect.height", diagnostics);
                requirePositiveConstant(normalized.width(), revision, base + "rect.width", diagnostics);
                requirePositiveConstant(normalized.height(), revision, base + "rect.height", diagnostics);
            }
            case RectProperty.Anchored anchored -> {
                validateNumeric(anchored.x(), tracks, revision, base + "rect.x", diagnostics);
                validateNumeric(anchored.y(), tracks, revision, base + "rect.y", diagnostics);
                validateNumeric(anchored.width(), tracks, revision, base + "rect.width", diagnostics);
                validateNumeric(anchored.height(), tracks, revision, base + "rect.height", diagnostics);
                requirePositiveConstant(anchored.width(), revision, base + "rect.width", diagnostics);
                requirePositiveConstant(anchored.height(), revision, base + "rect.height", diagnostics);
            }
        }
    }

    private void validateObstacle(ObstacleProperty property,
                                  Map<TrackId, AnimationTrack> tracks,
                                  Revision revision,
                                  String base,
                                  List<Diagnostic> diagnostics) {
        validateBoolean(property.enabled(), tracks, revision, base + "obstacle.enabled", diagnostics);
        validateNumeric(property.margin(), tracks, revision, base + "obstacle.margin", diagnostics);
        requireNonNegativeConstant(property.margin(), revision, base + "obstacle.margin", diagnostics);
    }

    private void requirePositiveConstant(PropertyBinding<NumericSource> binding,
                                         Revision revision,
                                         String path,
                                         List<Diagnostic> diagnostics) {
        if (binding instanceof PropertyBinding.Enabled<NumericSource> enabled
                && enabled.source() instanceof NumericSource.Constant constant
                && constant.value() <= 0.0) {
            diagnostics.add(error(revision, path, "Constant value must be positive: " + constant.value()));
        }
    }

    private void requireNonNegativeConstant(PropertyBinding<NumericSource> binding,
                                             Revision revision,
                                             String path,
                                             List<Diagnostic> diagnostics) {
        if (binding instanceof PropertyBinding.Enabled<NumericSource> enabled
                && enabled.source() instanceof NumericSource.Constant constant
                && constant.value() < 0.0) {
            diagnostics.add(error(revision, path,
                    "Constant value must be non-negative: " + constant.value()));
        }
    }

    private void validateCameraSource(PropertyBinding<CameraSourceProperty> binding,
                                      Map<TrackId, AnimationTrack> tracks,
                                      Set<RigId> rigIds,
                                      Revision revision,
                                      List<Diagnostic> diagnostics) {
        if (!(binding instanceof PropertyBinding.Enabled<CameraSourceProperty> enabled)) {
            return;
        }

        switch (enabled.source()) {
            case CameraSourceProperty.Constant constant ->
                    validateCameraSourceValue(constant.source(), rigIds, revision, diagnostics);
            case CameraSourceProperty.Track track -> {
                requireTrack(track.trackId(), tracks, CameraSourceTrack.class,
                        revision, "camera_source", diagnostics);
                AnimationTrack found = tracks.get(track.trackId());

                if (found instanceof CameraSourceTrack sourceTrack) {
                    validateCameraSourceValue(sourceTrack.defaultValue(), rigIds, revision, diagnostics);

                    for (ValueKeyframe<CameraSource> key : sourceTrack.keys()) {
                        validateCameraSourceValue(key.value(), rigIds, revision, diagnostics);
                    }
                }
            }
        }
    }

    private void validateCameraSourceValue(CameraSource source,
                                           Set<RigId> rigIds,
                                           Revision revision,
                                           List<Diagnostic> diagnostics) {
        if (source instanceof CameraSource.Rig rig && !rigIds.contains(rig.rigId())) {
            diagnostics.add(error(revision, "camera_source",
                    "Camera source references a missing rig: " + rig.rigId()));
        }
    }

    private void validateNumeric(PropertyBinding<NumericSource> binding,
                                 Map<TrackId, AnimationTrack> tracks,
                                 Revision revision,
                                 String path,
                                 List<Diagnostic> diagnostics) {
        if (binding instanceof PropertyBinding.Enabled<NumericSource> enabled) {
            validateNumericSource(enabled.source(), tracks, revision, path, diagnostics);
        }
    }

    private void validateNumericSource(NumericSource source,
                                       Map<TrackId, AnimationTrack> tracks,
                                       Revision revision,
                                       String path,
                                       List<Diagnostic> diagnostics) {
        if (source instanceof NumericSource.Formula formula) {
            PropertyFormulaEvaluator.compilationError(formula.expression()).ifPresent(error ->
                    diagnostics.add(error(revision, path, "Formula cannot compile: " + error)));
        }

        if (source instanceof NumericSource.Track track) {
            requireTrack(track.trackId(), tracks, CurveTrack.class, revision, path, diagnostics);
            AnimationTrack found = tracks.get(track.trackId());

            if (found instanceof CurveTrack curve) {
                validateCurveFormulas(curve, revision, path, diagnostics);
            }
        }
    }

    private void validateCurveFormulas(CurveTrack track,
                                       Revision revision,
                                       String path,
                                       List<Diagnostic> diagnostics) {
        for (Keyframe key : track.curve().keys()) {
            if (key.value() instanceof Formula formula) {
                PropertyFormulaEvaluator.compilationError(formula.expression()).ifPresent(error ->
                        diagnostics.add(error(revision, path,
                                "Curve key formula cannot compile: " + error)));
            }
        }
    }

    private void validateBoolean(PropertyBinding<BooleanSource> binding,
                                 Map<TrackId, AnimationTrack> tracks,
                                 Revision revision,
                                 String path,
                                 List<Diagnostic> diagnostics) {
        if (binding instanceof PropertyBinding.Enabled<BooleanSource> enabled
                && enabled.source() instanceof BooleanSource.Track track) {
            requireTrack(track.trackId(), tracks, ActiveTrack.class, revision, path, diagnostics);
        }
    }

    private void validateQuaternion(QuaternionSource source,
                                    Map<TrackId, AnimationTrack> tracks,
                                    Revision revision,
                                    String path,
                                    List<Diagnostic> diagnostics) {
        switch (source) {
            case QuaternionSource.Track track ->
                    requireTrack(track.trackId(), tracks, QuaternionTrack.class,
                            revision, path, diagnostics);
            case QuaternionSource.EulerComposite euler -> {
                validateNumericSource(euler.yaw(), tracks, revision, path + ".yaw", diagnostics);
                validateNumericSource(euler.pitch(), tracks, revision, path + ".pitch", diagnostics);
                validateNumericSource(euler.roll(), tracks, revision, path + ".roll", diagnostics);
            }
            case QuaternionSource.Constant ignored -> {
            }
        }
    }

    private void requireTrack(TrackId trackId,
                              Map<TrackId, AnimationTrack> tracks,
                              Class<? extends AnimationTrack> expected,
                              Revision revision,
                              String path,
                              List<Diagnostic> diagnostics) {
        AnimationTrack track = tracks.get(trackId);

        if (track == null) {
            diagnostics.add(error(revision, path,
                    "Track does not exist in the global pool: " + trackId));
            return;
        }

        if (!expected.isInstance(track)) {
            diagnostics.add(error(revision, path,
                    "Track " + trackId + " has type " + track.type()
                            + " but " + expected.getSimpleName() + " is required"));
        }
    }

    private static Diagnostic error(Revision revision, String path, String message) {
        return new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal(message), new DiagnosticScope.Document(revision),
                new Diagnostic.Location(null, path), null);
    }
}
