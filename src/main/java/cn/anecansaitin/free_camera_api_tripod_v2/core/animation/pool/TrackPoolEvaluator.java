package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.EvaluatedRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CurveInterpolation;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 全局轨道池第一版属性求值器。
///
/// 支持常量、数值曲线、布尔/相机来源状态轨道与四元数常量/轨道/Euler 组合。公式来源暂返回
/// 结构化错误，等待表达式编译与会话预算接入；不会把公式降级成 0 或关闭。空轨道返回
/// `ABSENT`，不接管属性。
@NullMarked
public final class TrackPoolEvaluator {

    /// 属性公式的显式输入只在一次求值调用内可见，并在 finally 中恢复。
    private static final ThreadLocal<TrackPoolEvaluationInputs> ACTIVE_INPUTS = new ThreadLocal<>();
    private static final ThreadLocal<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols>
            ACTIVE_SYMBOLS = new ThreadLocal<>();
    private static final ThreadLocal<Map<TrackId, CurveTrack>> ACTIVE_TRACKS = new ThreadLocal<>();
    private static final ThreadLocal<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource>
            ACTIVE_SAMPLES = new ThreadLocal<>();

    /// 用完整输入与采样端口求值一份文档。
    public List<EvaluatedRig> evaluate(TrackPoolAnimation document,
                                       TrackPoolEvaluationInputs inputs,
                                       cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource samples,
                                       Revision revision) {
        Objects.requireNonNull(samples, "samples");
        var previous = ACTIVE_SAMPLES.get();
        ACTIVE_SAMPLES.set(samples);

        try {
            return withInputs(inputs, () -> evaluateDocument(document, inputs.time(), revision));
        } finally {
            restore(ACTIVE_SAMPLES, previous);
        }
    }

    /// 用完整输入求值一份文档；没有采样端口。
    public List<EvaluatedRig> evaluate(TrackPoolAnimation document,
                                       TrackPoolEvaluationInputs inputs,
                                       Revision revision) {
        return evaluate(document, inputs,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource.NONE,
                revision);
    }

    /// 求值一份文档；进度 0、世界时间 0。
    public List<EvaluatedRig> evaluate(TrackPoolAnimation document, float time, Revision revision) {
        return withInputs(TrackPoolEvaluationInputs.at(time),
                () -> evaluateDocument(document, time, revision));
    }

    private List<EvaluatedRig> evaluateDocument(TrackPoolAnimation document,
                                                float time,
                                                Revision revision) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(revision, "revision");

        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("TrackPoolEvaluator time must be finite and non-negative");
        }

        Map<TrackId, AnimationTrack> tracks = new LinkedHashMap<>();
        Map<TrackId, CurveTrack> curves = new LinkedHashMap<>();

        for (AnimationTrack track : document.tracks()) {
            tracks.put(track.id(), track);

            if (track instanceof CurveTrack curve) {
                curves.put(track.id(), curve);
            }
        }

        var previousSymbols = ACTIVE_SYMBOLS.get();
        var previousTracks = ACTIVE_TRACKS.get();
        ACTIVE_SYMBOLS.set(document.symbols());
        ACTIVE_TRACKS.set(Map.copyOf(curves));

        try {
            List<EvaluatedRig> result = new ArrayList<>();

            for (PooledRig rig : document.rigs()) {
                result.add(evaluateRig(rig, tracks, time, revision));
            }

            return List.copyOf(result);
        } finally {
            restore(ACTIVE_SYMBOLS, previousSymbols);
            restore(ACTIVE_TRACKS, previousTracks);
        }
    }

    private static <T> void restore(ThreadLocal<T> local, T previous) {
        if (previous == null) {
            local.remove();
        } else {
            local.set(previous);
        }
    }

    /// 用完整输入求值文档级相机来源绑定。
    public EvaluatedRig.EvaluatedValue<CameraSource> evaluateCameraSource(
            TrackPoolAnimation document,
            TrackPoolEvaluationInputs inputs,
            Revision revision) {
        Objects.requireNonNull(inputs, "inputs");
        return withInputs(inputs, () -> evaluateCameraSource(document, inputs.time(), revision));
    }

    /// 求值文档级相机来源绑定；进度 0、世界时间 0。
    public EvaluatedRig.EvaluatedValue<CameraSource> evaluateCameraSource(
            TrackPoolAnimation document, float time, Revision revision) {
        Map<TrackId, AnimationTrack> tracks = new LinkedHashMap<>();
        for (AnimationTrack track : document.tracks()) {
            tracks.put(track.id(), track);
        }

        return cameraSource(document.cameraSourceBinding(), tracks, time, revision);
    }

    private static <T> T withInputs(TrackPoolEvaluationInputs inputs,
                                    java.util.function.Supplier<T> action) {
        TrackPoolEvaluationInputs previous = ACTIVE_INPUTS.get();
        ACTIVE_INPUTS.set(inputs);

        try {
            return action.get();
        } finally {
            if (previous == null) {
                ACTIVE_INPUTS.remove();
            } else {
                ACTIVE_INPUTS.set(previous);
            }
        }
    }

    private static TrackPoolEvaluationInputs inputsFor(float time) {
        TrackPoolEvaluationInputs inputs = ACTIVE_INPUTS.get();
        return inputs == null ? TrackPoolEvaluationInputs.at(time) : inputs;
    }

    private static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols symbolsFor() {
        var symbols = ACTIVE_SYMBOLS.get();
        return symbols == null
                ? cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols.EMPTY
                : symbols;
    }

    private static Map<TrackId, CurveTrack> curveTracksFor() {
        Map<TrackId, CurveTrack> tracks = ACTIVE_TRACKS.get();
        return tracks == null ? Map.of() : tracks;
    }

    private static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource samplesFor() {
        var samples = ACTIVE_SAMPLES.get();
        return samples == null
                ? cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource.NONE
                : samples;
    }

    private EvaluatedRig evaluateRig(PooledRig rig,
                                     Map<TrackId, AnimationTrack> tracks,
                                     float time,
                                     Revision revision) {
        String base = "rigs[" + rig.id().value() + "].";
        var properties = rig.properties();

        return new EvaluatedRig(
                rig.id(),
                rig.output(),
                numeric(properties.position().x(), tracks, time, revision, base + "position.x"),
                numeric(properties.position().y(), tracks, time, revision, base + "position.y"),
                numeric(properties.position().z(), tracks, time, revision, base + "position.z"),
                quaternion(properties.rotation().source(), tracks, time, revision, base + "rotation"),
                numeric(properties.fov().source(), tracks, time, revision, base + "fov"),
                bool(properties.active().source(), tracks, time, revision, base + "active"),
                rect(properties.rect(), tracks, time, revision, base + "rect"),
                bool(properties.obstacle().enabled(), tracks, time, revision, base + "obstacle.enabled"),
                numeric(properties.obstacle().margin(), tracks, time, revision, base + "obstacle.margin"));
    }

    private ChannelSample numeric(PropertyBinding<NumericSource> binding,
                                  Map<TrackId, AnimationTrack> tracks,
                                  float time,
                                  Revision revision,
                                  String path) {
        if (!(binding instanceof PropertyBinding.Enabled<NumericSource> enabled)) {
            return ChannelSample.absent();
        }

        return numericSource(enabled.source(), tracks, time, revision, path);
    }

    private ChannelSample numericSource(NumericSource source,
                                        Map<TrackId, AnimationTrack> tracks,
                                        float time,
                                        Revision revision,
                                        String path) {
        return switch (source) {
            case NumericSource.Constant constant -> {
                double value = constant.value();
                if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) {
                    yield ChannelSample.error(error(DiagnosticCode.INVALID_EDIT,
                            "Numeric property is outside the float range", path, revision));
                }
                yield ChannelSample.value((float) value);
            }
            case NumericSource.Formula formula -> {
                PropertyFormulaEvaluator.Result result = PropertyFormulaEvaluator.evaluate(
                        formula.expression(), inputsFor(time), symbolsFor(), curveTracksFor(),
                        samplesFor(), path, revision);

                if (result.isError()) {
                    yield ChannelSample.error(Objects.requireNonNull(result.diagnostic()));
                }

                yield finite((float) result.value(), path, revision);
            }
            case NumericSource.Track track -> curve(track.trackId(), tracks, time, revision, path);
        };
    }

    private ChannelSample curve(TrackId trackId,
                                Map<TrackId, AnimationTrack> tracks,
                                float time,
                                Revision revision,
                                String path) {
        AnimationTrack track = tracks.get(trackId);

        if (track == null) {
            return ChannelSample.error(error(DiagnosticCode.BROKEN_REFERENCE,
                    "Track does not exist in the global pool: " + trackId, path, revision));
        }

        if (!(track instanceof CurveTrack curveTrack)) {
            return ChannelSample.error(error(DiagnosticCode.INVALID_EDIT,
                    "Numeric property requires a curve track, got " + track.type(), path, revision));
        }

        Curve curve = curveTrack.curve();

        if (curve.isEmpty()) {
            return ChannelSample.absent();
        }

        Keyframe first = curve.first();
        Keyframe last = curve.last();
        double sampleTime = CurveInterpolation.wrapTime(time,
                first.time(), last.time(), curve.preMode(), curve.postMode());

        if (!Double.isFinite(sampleTime)) {
            return ChannelSample.error(error(DiagnosticCode.INVALID_EDIT,
                    "Wrapped curve time is not finite", path, revision));
        }

        if (sampleTime <= first.time()) {
            return keySample(first, time, path, revision);
        }

        if (sampleTime >= last.time()) {
            return keySample(last, time, path, revision);
        }

        int index = 0;

        while (index + 1 < curve.keys().size() && curve.keys().get(index + 1).time() <= sampleTime) {
            index++;
        }

        Keyframe left = curve.keys().get(index);
        Keyframe right = curve.keys().get(index + 1);

        ChannelSample leftSample = keySample(left, time, path, revision);

        if (leftSample.isError()) {
            return leftSample;
        }

        if (left.interpolation() == Curve.Interpolation.STEP) {
            return leftSample;
        }

        ChannelSample rightSample = keySample(right, time, path, revision);

        if (rightSample.isError()) {
            return rightSample;
        }

        double leftValue = leftSample.value();
        double rightValue = rightSample.value();
        double normalized = CurveInterpolation.normalizedTime(sampleTime, left.time(), right.time());
        double value = switch (left.interpolation()) {
            case STEP -> leftValue;
            case LINEAR -> CurveInterpolation.linear(normalized, leftValue, rightValue);
            case HERMITE -> CurveInterpolation.hermite(normalized,
                    right.time() - left.time(), leftValue, rightValue,
                    left.outSlope(), right.inSlope(), left.outLength(), right.inLength());
        };

        return finite((float) value, path, revision);
    }

    private ChannelSample keySample(Keyframe key, float time, String path, Revision revision) {
        if (key.value() instanceof Formula formula) {
            PropertyFormulaEvaluator.Result result = PropertyFormulaEvaluator.evaluate(
                    formula.expression(), inputsFor(time), symbolsFor(), curveTracksFor(),
                    samplesFor(), path, revision);

            if (result.isError()) {
                return ChannelSample.error(Objects.requireNonNull(result.diagnostic()));
            }

            return finite((float) result.value(), path, revision);
        }

        return finite(key.constantValue(), path, revision);
    }

    private EvaluatedRig.EvaluatedValue<Quaternion> quaternion(
            PropertyBinding<QuaternionSource> binding,
            Map<TrackId, AnimationTrack> tracks,
            float time,
            Revision revision,
            String path) {
        if (!(binding instanceof PropertyBinding.Enabled<QuaternionSource> enabled)) {
            return EvaluatedRig.EvaluatedValue.absent();
        }

        return switch (enabled.source()) {
            case QuaternionSource.Constant constant ->
                    EvaluatedRig.EvaluatedValue.value(constant.value());
            case QuaternionSource.Track track -> quaternionTrack(
                    track.trackId(), tracks, time, revision, path);
            case QuaternionSource.EulerComposite euler -> euler(euler, tracks, time, revision, path);
        };
    }

    private EvaluatedRig.EvaluatedValue<Quaternion> quaternionTrack(
            TrackId trackId,
            Map<TrackId, AnimationTrack> tracks,
            float time,
            Revision revision,
            String path) {
        AnimationTrack track = tracks.get(trackId);

        if (track == null) {
            return EvaluatedRig.EvaluatedValue.error(error(DiagnosticCode.BROKEN_REFERENCE,
                    "Track does not exist in the global pool: " + trackId, path, revision));
        }

        if (!(track instanceof QuaternionTrack quaternionTrack)) {
            return EvaluatedRig.EvaluatedValue.error(error(DiagnosticCode.INVALID_EDIT,
                    "Rotation requires a quaternion track, got " + track.type(), path, revision));
        }

        if (quaternionTrack.keyCount() == 0) {
            return EvaluatedRig.EvaluatedValue.absent();
        }

        return EvaluatedRig.EvaluatedValue.value(quaternionTrack.at(time));
    }

    private EvaluatedRig.EvaluatedValue<Quaternion> euler(
            QuaternionSource.EulerComposite euler,
            Map<TrackId, AnimationTrack> tracks,
            float time,
            Revision revision,
            String path) {
        ChannelSample yaw = numericSource(euler.yaw(), tracks, time, revision, path + ".yaw");
        ChannelSample pitch = numericSource(euler.pitch(), tracks, time, revision, path + ".pitch");
        ChannelSample roll = numericSource(euler.roll(), tracks, time, revision, path + ".roll");

        for (ChannelSample sample : List.of(yaw, pitch, roll)) {
            if (sample.isError()) {
                return EvaluatedRig.EvaluatedValue.error(Objects.requireNonNull(sample.diagnostic()));
            }

            if (!sample.isValue()) {
                return EvaluatedRig.EvaluatedValue.error(error(DiagnosticCode.INVALID_EDIT,
                        "Euler rotation input is absent", path, revision));
            }
        }

        double factor = euler.unit() == QuaternionSource.AngleUnit.DEGREES
                ? Math.PI / 180.0
                : 1.0;
        double halfYaw = yaw.value() * factor / 2.0;
        double halfPitch = pitch.value() * factor / 2.0;
        double halfRoll = roll.value() * factor / 2.0;
        Quaternion qy = new Quaternion(0, Math.sin(halfYaw), 0, Math.cos(halfYaw));
        Quaternion qx = new Quaternion(Math.sin(halfPitch), 0, 0, Math.cos(halfPitch));
        Quaternion qz = new Quaternion(0, 0, Math.sin(halfRoll), Math.cos(halfRoll));
        Quaternion result = euler.order() == QuaternionSource.RotationOrder.YXZ
                ? qy.multiply(qx).multiply(qz)
                : qz.multiply(qy).multiply(qx);

        return EvaluatedRig.EvaluatedValue.value(result.normalized());
    }

    private EvaluatedRig.EvaluatedValue<Boolean> bool(
            PropertyBinding<BooleanSource> binding,
            Map<TrackId, AnimationTrack> tracks,
            float time,
            Revision revision,
            String path) {
        if (!(binding instanceof PropertyBinding.Enabled<BooleanSource> enabled)) {
            return EvaluatedRig.EvaluatedValue.absent();
        }

        return switch (enabled.source()) {
            case BooleanSource.Constant constant ->
                    EvaluatedRig.EvaluatedValue.value(constant.value());
            case BooleanSource.Track track -> {
                AnimationTrack found = tracks.get(track.trackId());

                if (found == null) {
                    yield EvaluatedRig.EvaluatedValue.error(error(DiagnosticCode.BROKEN_REFERENCE,
                            "Track does not exist in the global pool: " + track.trackId(), path, revision));
                }

                if (!(found instanceof ActiveTrack activeTrack)) {
                    yield EvaluatedRig.EvaluatedValue.error(error(DiagnosticCode.INVALID_EDIT,
                            "Boolean property requires an active track, got " + found.type(),
                            path, revision));
                }

                yield activeTrack.keyCount() == 0
                        ? EvaluatedRig.EvaluatedValue.absent()
                        : EvaluatedRig.EvaluatedValue.value(activeTrack.at(time));
            }
        };
    }

    private EvaluatedRig.EvaluatedValue<CameraSource> cameraSource(
            PropertyBinding<CameraSourceProperty> binding,
            Map<TrackId, AnimationTrack> tracks,
            float time,
            Revision revision) {
        if (!(binding instanceof PropertyBinding.Enabled<CameraSourceProperty> enabled)) {
            return EvaluatedRig.EvaluatedValue.absent();
        }

        return switch (enabled.source()) {
            case CameraSourceProperty.Constant constant ->
                    EvaluatedRig.EvaluatedValue.value(constant.source());
            case CameraSourceProperty.Track track -> {
                AnimationTrack found = tracks.get(track.trackId());

                if (found == null) {
                    yield EvaluatedRig.EvaluatedValue.error(error(DiagnosticCode.BROKEN_REFERENCE,
                            "Camera source track does not exist: " + track.trackId(),
                            "camera_source", revision));
                }

                if (!(found instanceof CameraSourceTrack sourceTrack)) {
                    yield EvaluatedRig.EvaluatedValue.error(error(DiagnosticCode.INVALID_EDIT,
                            "Camera source requires a camera-source track, got " + found.type(),
                            "camera_source", revision));
                }

                yield sourceTrack.keyCount() == 0
                        ? EvaluatedRig.EvaluatedValue.absent()
                        : EvaluatedRig.EvaluatedValue.value(sourceTrack.at(time));
            }
        };
    }

    private EvaluatedRig.EvaluatedRect rect(RectProperty property,
                                            Map<TrackId, AnimationTrack> tracks,
                                            float time,
                                            Revision revision,
                                            String path) {
        return switch (property) {
            case RectProperty.Normalized normalized -> new EvaluatedRig.EvaluatedRect(
                    EvaluatedRig.EvaluatedRect.Kind.NORMALIZED, null,
                    numeric(normalized.x(), tracks, time, revision, path + ".x"),
                    numeric(normalized.y(), tracks, time, revision, path + ".y"),
                    numeric(normalized.width(), tracks, time, revision, path + ".width"),
                    numeric(normalized.height(), tracks, time, revision, path + ".height"));
            case RectProperty.Anchored anchored -> new EvaluatedRig.EvaluatedRect(
                    EvaluatedRig.EvaluatedRect.Kind.ANCHORED, anchored.corner(),
                    numeric(anchored.x(), tracks, time, revision, path + ".x"),
                    numeric(anchored.y(), tracks, time, revision, path + ".y"),
                    numeric(anchored.width(), tracks, time, revision, path + ".width"),
                    numeric(anchored.height(), tracks, time, revision, path + ".height"));
        };
    }

    private ChannelSample finite(float value, String path, Revision revision) {
        if (!Float.isFinite(value)) {
            return ChannelSample.error(error(DiagnosticCode.INVALID_EDIT,
                    "Numeric property is not finite", path, revision));
        }

        return ChannelSample.value(value);
    }

    private static Diagnostic error(DiagnosticCode code, String message, String path, Revision revision) {
        return new Diagnostic(code, Severity.ERROR, LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision), new Diagnostic.Location(null, path), null);
    }
}
