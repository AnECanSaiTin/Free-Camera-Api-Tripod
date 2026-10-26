package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.AimCaptureMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.AimCaptureRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.BakeRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.BatchTransformRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EasingRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditTools;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.ToolResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.NumberSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CoreExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.ExpressionCurveSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.CorePlaybackFrameFactory;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.FrameEnvironment;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback.ObstacleResolver;
import cn.anecansaitin.free_camera_api_tripod_v2.core.math.CoreRotationMath;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 第一版编辑工具：缓动、批量变换与烘焙。
///
/// ## 边界
///
/// - 只读取请求携带的冻结快照 / 求值上下文；不读当前 store、不发布、不写历史。
/// - 每个方法只生成一个 `EditOp`；任一目标缺失、值域不支持或采样失败时整体拒绝。
/// - 缓动只写插值与曲柄；批量变换对公式值显式拒绝，避免把公式静默改成常量。
/// - 烘焙替换整条曲线：按 `[startTime, endTime]` 采样，包含起点与终点；超过 `maxKeys`
///   在采样前拒绝。公式采样共用请求中的 `EvaluationSession`。
/// - 注视捕获尚未接入单机位姿态求值链；当前明确拒绝，不生成部分候选。
@NullMarked
public final class CoreEditTools implements EditTools {

    /// 与统一校验 L4 一致的曲柄长度上限。
    private static final float MAX_HANDLE_LENGTH = 1.5f;

    private final IdGenerator ids;
    private final CurveSampler sampler;

    /// 默认使用随机 ID 与默认表达式采样器。
    public CoreEditTools() {
        this(IdGenerator.random(),
                new ExpressionCurveSampler(new CoreExpressionEvaluator()));
    }

    /// @param ids     烘焙新键的 ID 生成器；不得返回空白值
    /// @param sampler 曲线采样器；只使用请求中的上下文与会话
    public CoreEditTools(IdGenerator ids, CurveSampler sampler) {
        this.ids = Objects.requireNonNull(ids, "ids");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    @Override
    public ToolResult applyEasing(EasingRequest request) {
        Objects.requireNonNull(request, "request");
        AnimationSnapshot snapshot = request.snapshot();
        CurveTrack track = snapshot.document().curveTrack(request.trackId()).orElse(null);

        if (track == null) {
            return rejected(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                    "Track is not a curve track: " + request.trackId(),
                    request.trackId(), "curve"));
        }

        if (request.preset().inLength() > MAX_HANDLE_LENGTH
                || request.preset().outLength() > MAX_HANDLE_LENGTH) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Easing handle length must not exceed " + MAX_HANDLE_LENGTH,
                    request.trackId(), "curve"));
        }

        Map<KeyId, Keyframe> keys = new HashMap<>();
        for (Keyframe key : track.curve().keys()) {
            keys.put(key.keyId(), key);
        }

        List<Diagnostic> missing = new ArrayList<>();
        List<EditOp> operations = new ArrayList<>();

        for (KeyId keyId : new LinkedHashSet<>(request.keyIds())) {
            if (!keys.containsKey(keyId)) {
                missing.add(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                        "Key does not exist: " + keyId, request.trackId(),
                        "curve.keys." + keyId.value()));
                continue;
            }

            operations.add(new EditOp.SetKeyInterpolation(
                    request.trackId(), keyId, request.preset().interpolation()));
            operations.add(new EditOp.SetKeyHandles(
                    request.trackId(), keyId,
                    request.preset().inSlope(), request.preset().outSlope(),
                    request.preset().inLength(), request.preset().outLength()));
        }

        if (!missing.isEmpty()) {
            return new ToolResult.Rejected(missing);
        }

        return new ToolResult.Ready(new EditOp.Batch(operations), List.of());
    }

    @Override
    public ToolResult applyBatchTransform(BatchTransformRequest request) {
        Objects.requireNonNull(request, "request");
        AnimationSnapshot snapshot = request.snapshot();
        CameraAnimation document = snapshot.document();
        CurveTrack curve = document.curveTrack(request.trackId()).orElse(null);

        if (curve != null) {
            return batchCurve(request, curve);
        }

        List<? extends ValueKeyframe<?>> stateKeys = stateKeys(document, request.trackId());

        if (stateKeys != null) {
            return batchState(request, stateKeys);
        }

        if (document.track(request.trackId()).isPresent()) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Batch transform is not supported for extension tracks: " + request.trackId(),
                    request.trackId(), "tracks"));
        }

        return rejected(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                "Track does not exist: " + request.trackId(), request.trackId(), "tracks"));
    }

    /// 按最终 float 时间语义生成严格递增的采样计划，终点只出现一次。
    ///
    /// 直接在 double 上取 `start + index * step` 再转 float；若 float 舍入使候选不大于上一
    /// 采样点，则推进到下一个可表示值，避免重复键。终点在计划末尾单独补一次；非精确步长
    /// 不会先到达终点又被追加一次。
    private static List<Float> bakeTimes(BakeRequest request) {
        float start = request.startTime();
        float end = request.endTime();
        List<Float> times = new ArrayList<>();
        times.add(start);
        int index = 1;

        while (times.size() <= request.maxKeys()) {
            double raw = start + index * (double) request.stepSeconds();

            if (raw >= end) {
                break;
            }

            float candidate = (float) raw;
            float last = times.getLast();

            if (candidate <= last) {
                candidate = Math.nextUp(last);
            }

            if (!Float.isFinite(candidate) || candidate >= end) {
                break;
            }

            times.add(candidate);
            index++;
        }

        if (times.getLast() < end) {
            times.add(end);
        }

        return times;
    }

    @Override
    public ToolResult bake(BakeRequest request) {
        Objects.requireNonNull(request, "request");
        AnimationSnapshot snapshot = request.context().snapshot();
        CurveTrack track = snapshot.document().curveTrack(request.trackId()).orElse(null);

        if (track == null) {
            return rejected(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                    "Track is not a curve track: " + request.trackId(),
                    request.trackId(), "curve"));
        }

        TrackIndex.Entry entry = request.context().trackIndex().find(request.trackId()).orElse(null);

        if (entry == null || entry.ownerRigId() == null
                || !entry.ownerRigId().equals(request.rigId())) {
            return rejected(error(snapshot, DiagnosticCode.BROKEN_REFERENCE,
                    "Track " + request.trackId() + " is not owned by rig " + request.rigId(),
                    request.trackId(), "curve"));
        }

        List<Float> times = bakeTimes(request);

        if (times.size() > request.maxKeys()) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Bake would generate " + times.size()
                            + " keys, exceeding maxKeys " + request.maxKeys(),
                    request.trackId(), "curve"));
        }

        List<Keyframe> keys = new ArrayList<>(times.size());

        for (float time : times) {
            if (!Float.isFinite(time) || time < 0f) {
                return rejected(error(snapshot, DiagnosticCode.ILLEGAL_KEY_TIME,
                        "Bake sample time is invalid: " + time, request.trackId(), "curve"));
            }

            ChannelSample sample;

            try {
                sample = sampler.sample(new CurveSampleRequest(
                        request.context(), request.session(), request.trackId(),
                        request.rigId(), time));
            } catch (RuntimeException exception) {
                return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                        "Bake sampling failed: " + messageOf(exception),
                        request.trackId(), "curve"));
            }

            if (sample == null) {
                return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                        "Bake sampler returned null", request.trackId(), "curve"));
            }

            if (sample.isError()) {
                Diagnostic diagnostic = sample.diagnostic();

                if (diagnostic != null) {
                    return new ToolResult.Rejected(List.of(diagnostic));
                }

                return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                        "Bake sampling failed at " + time, request.trackId(), "curve"));
            }

            if (!sample.isValue()) {
                return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                        "Bake sample has no value at " + time, request.trackId(), "curve"));
            }

            try {
                String keyId = ids.nextId(KeyId.class);
                keys.add(new Keyframe(new KeyId(keyId), time, new Constant(sample.value()),
                        0f, 0f, 0f, 0f, request.interpolation()));
            } catch (RuntimeException exception) {
                return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                        "Cannot create baked key: " + messageOf(exception),
                        request.trackId(), "curve"));
            }
        }

        return new ToolResult.Ready(new EditOp.ReplaceKeys(request.trackId(), keys), List.of());
    }

    @Override
    public ToolResult captureAim(AimCaptureRequest request) {
        Objects.requireNonNull(request, "request");
        AnimationSnapshot snapshot = request.context().snapshot();
        CameraAnimation document = snapshot.document();
        CameraRig rig = document.rig(request.rigId()).orElse(null);

        if (rig == null) {
            return rejected(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                    "Rig does not exist: " + request.rigId(), request.rigId(), "rigs"));
        }

        if (request.override() instanceof AimOverride.Replace) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Clear the fixed AimOverride.Replace before capturing; writing Off cannot close it",
                    request.rigId(), "aim"));
        }

        if (request.override() instanceof AimOverride.Disabled) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Aim is explicitly disabled; there is no target to capture",
                    request.rigId(), "aim"));
        }

        FrameFacts facts = request.context().facts();

        if (!facts.valid()) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Frame facts are not valid; aim capture requires a complete frozen frame",
                    request.rigId(), "aim"));
        }

        FrameFacts.RigFacts rigFacts = facts.rig(rig.id()).orElse(null);

        if (rigFacts == null) {
            return rejected(error(snapshot, DiagnosticCode.BROKEN_REFERENCE,
                    "No frame facts for rig " + rig.id(), rig.id(), "aim"));
        }

        if (rigFacts.aim().targetOrNull() == null) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "No active aim target at " + request.time(),
                    rig.id(), "aim"));
        }

        if (!(rigFacts.target() instanceof SpatialResolution.Resolved)) {
            return rejected(error(snapshot, DiagnosticCode.BROKEN_REFERENCE,
                    "Aim target is not resolved in the frozen frame",
                    rig.id(), "aim.target"));
        }

        if (request.mode() == AimCaptureMode.LEFT_TARGET
                && !hasOffKeyAt(rig.aim(), request.time())) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "LEFT_TARGET requires an Off key exactly at " + request.time(),
                    rig.id(), "aim"));
        }

        FrameEnvironment environment = new FrameEnvironment(
                facts.dimension(), facts.worldInstanceId(), facts.gameTick(),
                facts.partialTick(), facts.worldTime());
        PlaybackClock clock = new PlaybackClock(
                request.time(), 1f, false, document.duration());
        RigPlaybackOverrides overrides = new RigPlaybackOverrides(
                null, null, request.override(), null);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                new CoreExpressionEvaluator(),
                ignored -> facts,
                () -> request.basePose(),
                () -> environment,
                obstacleRequest -> new ObstacleResolver.Result.Adjusted(obstacleRequest.expected()));

        CorePlaybackFrameFactory.RigPreparation prepared;

        try {
            prepared = factory.prepareRig(snapshot, request.rigId(), overrides, clock,
                    facts, environment, request.session());
        } catch (RuntimeException exception) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Aim pose preparation failed: " + messageOf(exception),
                    request.rigId(), "aim"));
        }

        List<Diagnostic> diagnostics = new ArrayList<>(prepared.diagnostics());

        if (prepared.pose() == null) {
            if (diagnostics.isEmpty()) {
                diagnostics.add(error(snapshot, DiagnosticCode.INVALID_EDIT,
                        "Aim pose preparation returned no pose", request.rigId(), "aim"));
            }

            return new ToolResult.Rejected(diagnostics);
        }

        if (hasError(diagnostics)) {
            return new ToolResult.Rejected(diagnostics);
        }

        Rotation worldRotation = prepared.pose().pose().rotation();
        Rotation documentRotation = worldRotation;

        if (rig.space() instanceof Space.Local local) {
            FrameFacts.RigFacts current = facts.rig(rig.id()).orElseThrow();

            if (!(current.anchor() instanceof SpatialResolution.Resolved resolved)) {
                return rejected(error(snapshot, DiagnosticCode.BROKEN_REFERENCE,
                        "Local rig anchor is not resolved in the frozen frame",
                        rig.id(), "space.anchor"));
            }

            Rotation frameRotation = resolved.anchor().rotation().masked(local.followMask());
            documentRotation = CoreRotationMath.compose(
                    CoreRotationMath.inverse(frameRotation), worldRotation);
        }

        CurveTrack x = channel(rig, CameraChannel.ROTATION_X);
        CurveTrack y = channel(rig, CameraChannel.ROTATION_Y);
        CurveTrack z = channel(rig, CameraChannel.ROTATION_Z);

        if (x == null || y == null || z == null) {
            return rejected(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                    "Aim capture requires existing rotation x / y / z channel tracks",
                    rig.id(), "channels"));
        }

        List<EditOp> operations = new ArrayList<>();
        List<Diagnostic> warnings = new ArrayList<>();

        try {
            addAngle(operations, warnings, x, request.time(), documentRotation.pitch(),
                    snapshot);
            addAngle(operations, warnings, y, request.time(), documentRotation.yaw(),
                    snapshot);
            addAngle(operations, warnings, z, request.time(), documentRotation.roll(),
                    snapshot);
            addAimOff(operations, warnings, rig.aim(), request.time(), snapshot);
        } catch (RuntimeException exception) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Aim capture candidate failed: " + messageOf(exception),
                    request.rigId(), "aim"));
        }

        warnings.addAll(diagnostics);
        return new ToolResult.Ready(new EditOp.Batch(operations), warnings);
    }

    // region 批量变换

    private ToolResult batchCurve(BatchTransformRequest request, CurveTrack track) {
        AnimationSnapshot snapshot = request.snapshot();
        Map<KeyId, Keyframe> keys = new HashMap<>();
        for (Keyframe key : track.curve().keys()) {
            keys.put(key.keyId(), key);
        }

        Set<KeyId> selected = new LinkedHashSet<>(request.keyIds());
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<EditOp> valueOperations = new ArrayList<>();
        List<EditOp.KeyMove> moves = new ArrayList<>();
        Set<Float> finalTimes = new HashSet<>();

        for (Keyframe key : track.curve().keys()) {
            if (!selected.contains(key.keyId())) {
                addFinalTime(finalTimes, key.time(), snapshot, request.trackId(), diagnostics);
            }
        }

        for (KeyId keyId : selected) {
            Keyframe key = keys.get(keyId);

            if (key == null) {
                diagnostics.add(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                        "Key does not exist: " + keyId, request.trackId(),
                        "curve.keys." + keyId.value()));
                continue;
            }

            float newTime = transformedTime(key.time(), request, snapshot, request.trackId(),
                    keyId, diagnostics);

            if (Float.isNaN(newTime)) {
                continue;
            }

            if (!addFinalTime(finalTimes, newTime, snapshot, request.trackId(), diagnostics)) {
                continue;
            }

            if (newTime != key.time()) {
                moves.add(new EditOp.KeyMove(request.trackId(), keyId, newTime));
            }

            if (hasValueTransform(request)) {
                NumberSource value = key.value();

                if (value instanceof Formula) {
                    diagnostics.add(error(snapshot, DiagnosticCode.INVALID_EDIT,
                            "Formula value requires an explicit equivalent formula; key " + keyId,
                            request.trackId(), "curve.keys." + keyId.value() + ".value"));
                    continue;
                }

                if (!(value instanceof Constant constant)) {
                    diagnostics.add(error(snapshot, DiagnosticCode.INVALID_EDIT,
                            "Batch transform does not support value source "
                                    + value.getClass().getSimpleName() + " for key " + keyId,
                            request.trackId(), "curve.keys." + keyId.value() + ".value"));
                    continue;
                }

                float transformed = transformValue(constant.value(), request);

                if (!Float.isFinite(transformed)) {
                    diagnostics.add(error(snapshot, DiagnosticCode.INVALID_EDIT,
                            "Batch transform produced a non-finite value for key " + keyId,
                            request.trackId(), "curve.keys." + keyId.value() + ".value"));
                    continue;
                }

                if (Float.floatToIntBits(transformed) != Float.floatToIntBits(constant.value())) {
                    valueOperations.add(new EditOp.SetKeyValue(
                            request.trackId(), keyId, new Constant(transformed)));
                }
            }
        }

        if (!diagnostics.isEmpty()) {
            return new ToolResult.Rejected(diagnostics);
        }

        List<EditOp> operations = new ArrayList<>();
        if (!moves.isEmpty()) {
            operations.add(new EditOp.MoveKeys(moves));
        }
        operations.addAll(valueOperations);

        return new ToolResult.Ready(new EditOp.Batch(operations), List.of());
    }

    private ToolResult batchState(BatchTransformRequest request,
                                  List<? extends ValueKeyframe<?>> keys) {
        AnimationSnapshot snapshot = request.snapshot();

        if (hasValueTransform(request)) {
            return rejected(error(snapshot, DiagnosticCode.INVALID_EDIT,
                    "Value transform is not supported for state tracks: " + request.trackId(),
                    request.trackId(), "keys"));
        }

        Map<KeyId, ValueKeyframe<?>> byId = new HashMap<>();
        for (ValueKeyframe<?> key : keys) {
            byId.put(key.keyId(), key);
        }

        Set<KeyId> selected = new LinkedHashSet<>(request.keyIds());
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<EditOp.KeyMove> moves = new ArrayList<>();
        Set<Float> finalTimes = new HashSet<>();

        for (ValueKeyframe<?> key : keys) {
            if (!selected.contains(key.keyId())) {
                addFinalTime(finalTimes, key.time(), snapshot, request.trackId(), diagnostics);
            }
        }

        for (KeyId keyId : selected) {
            ValueKeyframe<?> key = byId.get(keyId);

            if (key == null) {
                diagnostics.add(error(snapshot, DiagnosticCode.TARGET_NOT_FOUND,
                        "Key does not exist: " + keyId, request.trackId(),
                        "keys." + keyId.value()));
                continue;
            }

            float newTime = transformedTime(key.time(), request, snapshot, request.trackId(),
                    keyId, diagnostics);

            if (!Float.isNaN(newTime)
                    && addFinalTime(finalTimes, newTime, snapshot, request.trackId(), diagnostics)
                    && newTime != key.time()) {
                moves.add(new EditOp.KeyMove(request.trackId(), keyId, newTime));
            }
        }

        if (!diagnostics.isEmpty()) {
            return new ToolResult.Rejected(diagnostics);
        }

        List<EditOp> operations = moves.isEmpty()
                ? List.of()
                : List.of(new EditOp.MoveKeys(moves));
        return new ToolResult.Ready(new EditOp.Batch(operations), List.of());
    }

    private static float transformedTime(float time,
                                         BatchTransformRequest request,
                                         AnimationSnapshot snapshot,
                                         TrackId trackId,
                                         KeyId keyId,
                                         List<Diagnostic> diagnostics) {
        float newTime = time + request.transform().timeOffsetSeconds();

        if (newTime == 0f) {
            newTime = 0f;
        }

        if (!Float.isFinite(newTime) || newTime < 0f) {
            diagnostics.add(error(snapshot, DiagnosticCode.ILLEGAL_KEY_TIME,
                    "Batch transform produced an invalid time " + newTime + " for key " + keyId,
                    trackId, "keys." + keyId.value()));
            return Float.NaN;
        }

        return newTime;
    }

    private static boolean addFinalTime(Set<Float> times,
                                        float time,
                                        AnimationSnapshot snapshot,
                                        TrackId trackId,
                                        List<Diagnostic> diagnostics) {
        float normalized = time == 0f ? 0f : time;

        if (!times.add(normalized)) {
            diagnostics.add(error(snapshot, DiagnosticCode.DUPLICATE_KEY_TIME,
                    "Batch transform would duplicate key time " + normalized,
                    trackId, "keys"));
            return false;
        }

        return true;
    }

    private static boolean hasValueTransform(BatchTransformRequest request) {
        return request.transform().valueScale() != 1f
                || request.transform().valueOffset() != 0f
                || request.transform().negateValues();
    }

    /// 先取反，再缩放，最后偏移：`(-value | value) * scale + offset`。
    private static float transformValue(float value, BatchTransformRequest request) {
        double raw = request.transform().negateValues() ? -value : value;
        return (float) (raw * request.transform().valueScale()
                + request.transform().valueOffset());
    }

    // endregion

    // region 注视捕获辅助

    private static @Nullable CurveTrack channel(CameraRig rig, CameraChannel channel) {
        for (CurveTrack track : rig.channels()) {
            if (track.channel() == channel) {
                return track;
            }
        }

        return null;
    }

    private static boolean hasOffKeyAt(AimTrack aim, float time) {
        for (ValueKeyframe<AimState> key : aim.keys()) {
            if (key.time() == time) {
                return key.value() instanceof AimState.Off;
            }
        }

        return false;
    }

    private static boolean hasError(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    private void addAngle(List<EditOp> operations,
                          List<Diagnostic> warnings,
                          CurveTrack track,
                          float time,
                          double angle,
                          AnimationSnapshot snapshot) {
        float value = (float) compatibleAngle(angle, track, time);

        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Captured rotation is not finite: " + value);
        }

        Keyframe existing = exactKey(track.curve(), time);

        if (existing != null) {
            if (existing.value() instanceof Constant constant
                    && Float.floatToIntBits(constant.value()) == Float.floatToIntBits(value)) {
                return;
            }

            operations.add(new EditOp.SetKeyValue(
                    track.id(), existing.keyId(), new Constant(value)));
            warnings.add(warning(snapshot,
                    "Replaces the existing rotation key at " + time + "s for track " + track.id(),
                    track.id(), "curve.keys." + existing.keyId().value()));
            return;
        }

        KeyId keyId = new KeyId(ids.nextId(KeyId.class));
        operations.add(new EditOp.AddNumberKey(track.id(), new Keyframe(
                keyId, time, new Constant(value), 0f, 0f, 0f, 0f,
                Curve.Interpolation.LINEAR)));
    }

    private void addAimOff(List<EditOp> operations,
                           List<Diagnostic> warnings,
                           AimTrack aim,
                           float time,
                           AnimationSnapshot snapshot) {
        ValueKeyframe<AimState> existing = exactValueKey(aim.keys(), time);

        if (existing != null) {
            if (existing.value() instanceof AimState.Off) {
                return;
            }

            operations.add(new EditOp.SetStateKey(aim.id(), existing.keyId(), AimState.off()));
            warnings.add(warning(snapshot,
                    "Replaces the existing aim key at " + time + "s with Off",
                    aim.id(), "aim.keys." + existing.keyId().value()));
            return;
        }

        KeyId keyId = new KeyId(ids.nextId(KeyId.class));
        operations.add(new EditOp.AddValueKey(aim.id(),
                ValueKeyframe.at(keyId, time, AimState.off())));
    }

    /// 选取与同轴已有常量键最接近的等价 2π 分支，减少无意义整周跳转。
    private static double compatibleAngle(double angle, CurveTrack track, float time) {
        double reference = angle;
        boolean hasReference = false;

        for (Keyframe key : track.curve().keys()) {
            if (key.time() > time) {
                break;
            }

            if (key.value() instanceof Constant constant) {
                reference = constant.value();
                hasReference = true;
            }
        }

        if (!hasReference) {
            for (Keyframe key : track.curve().keys()) {
                if (key.value() instanceof Constant constant) {
                    reference = constant.value();
                    hasReference = true;
                    break;
                }
            }
        }

        if (!hasReference) {
            return angle;
        }

        double twoPi = Math.PI * 2.0;
        return angle + Math.rint((reference - angle) / twoPi) * twoPi;
    }

    private static @Nullable Keyframe exactKey(Curve curve, float time) {
        for (Keyframe key : curve.keys()) {
            if (key.time() == time) {
                return key;
            }
        }

        return null;
    }

    private static @Nullable ValueKeyframe<AimState> exactValueKey(List<ValueKeyframe<AimState>> keys,
                                                                    float time) {
        for (ValueKeyframe<AimState> key : keys) {
            if (key.time() == time) {
                return key;
            }
        }

        return null;
    }

    private static Diagnostic warning(AnimationSnapshot snapshot,
                                      String message,
                                      DocumentObjectId objectId,
                                      String fieldPath) {
        return new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.WARNING,
                LocalizedMessage.literal(message),
                new DiagnosticScope.Document(snapshot.revision()),
                new Diagnostic.Location(objectId, fieldPath), null);
    }

    // endregion

    // region 辅助

    private static @Nullable List<? extends ValueKeyframe<?>> stateKeys(CameraAnimation document,
                                                                        TrackId trackId) {
        for (CameraRig rig : document.rigs()) {
            if (rig.aim().id().equals(trackId)) {
                return rig.aim().keys();
            }

            if (rig.view().active().id().equals(trackId)) {
                return rig.view().active().keys();
            }
        }

        if (document.cameraSource().id().equals(trackId)) {
            return document.cameraSource().keys();
        }

        return null;
    }

    private static ToolResult rejected(Diagnostic diagnostic) {
        return new ToolResult.Rejected(List.of(diagnostic));
    }

    private static Diagnostic error(AnimationSnapshot snapshot,
                                    DiagnosticCode code,
                                    String message,
                                    @Nullable DocumentObjectId objectId,
                                    String fieldPath) {
        return new Diagnostic(code, Severity.ERROR, LocalizedMessage.literal(message),
                new DiagnosticScope.Document(snapshot.revision()),
                new Diagnostic.Location(objectId, fieldPath), null);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    // endregion
}
