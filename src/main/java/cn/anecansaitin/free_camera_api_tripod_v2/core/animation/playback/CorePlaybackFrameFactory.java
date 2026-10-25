package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.AxisSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.DocumentPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsPlan;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsProvider;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialReadings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.CameraProbe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CoreExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.ExpressionCurveSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.FrameFactsPlanBuilder;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.SnapshotTrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.path.CorePathSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.core.math.CoreRotationMath;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// 第一版帧准备：事实、活动机位、通道 / 布局、基础姿态、事件展开与服务端帧结构。
///
/// ## 姿态口径
///
/// 通道逐轴求值；缺失轴用宿主 [CameraProbe] 的基础姿态补全。局部机位把解析锚点的位置与
/// 旋转叠加到局部值；世界机位直接使用通道值。注视按目标位置计算 Yaw/Pitch，只覆盖掩码
/// 启用的轴，Roll 保留。避障、参考系完整旋转、路径模式与真实世界读数仍不在本版。
///
/// ## 失败
///
/// 任一 ERROR 诊断都会使 [PreparedFrame#isExecutable] 为 false，由 [CorePlaybackService]
/// 转入 `HALTED`；本类不返回部分成功帧，也不读当前 store。
@NullMarked
public final class CorePlaybackFrameFactory implements PlaybackFrameFactory {

    /// 本版固定工作额度；后续可按宿主配置注入。
    public static final long DEFAULT_WORK_QUOTA = 1_000_000L;

    /// 本版固定最大求值深度。
    public static final int DEFAULT_MAX_DEPTH = 64;

    private final CurveSampler curveSampler;
    private final AnimatedNumberEvaluator numbers;
    private final ResolvedRectEvaluator rects;
    private final FrameFactsProvider factsProvider;
    private final CameraProbe cameraProbe;
    private final Supplier<FrameEnvironment> environment;
    private final @Nullable ObstacleResolver obstacleResolver;
    private final PlaybackEventExpander eventExpander = new PlaybackEventExpander();
    private final CorePathSampler pathSampler = new CorePathSampler();

    /// 使用默认表达式求值器。
    public CorePlaybackFrameFactory(FrameFactsProvider factsProvider,
                                    CameraProbe cameraProbe,
                                    Supplier<FrameEnvironment> environment) {
        this(new CoreExpressionEvaluator(), factsProvider, cameraProbe, environment, null);
    }

    /// @param evaluator   表达式求值器
    /// @param factsProvider 帧事实提供者
    /// @param cameraProbe 宿主基础姿态
    /// @param environment 宿主环境
    public CorePlaybackFrameFactory(ExpressionEvaluator evaluator,
                                    FrameFactsProvider factsProvider,
                                    CameraProbe cameraProbe,
                                    Supplier<FrameEnvironment> environment) {
        this(evaluator, factsProvider, cameraProbe, environment, null);
    }

    /// @param evaluator        表达式求值器
    /// @param factsProvider    帧事实提供者
    /// @param cameraProbe      宿主基础姿态
    /// @param environment      宿主环境
    /// @param obstacleResolver 避障端口；为空时启用避障的机位会失败
    public CorePlaybackFrameFactory(ExpressionEvaluator evaluator,
                                    FrameFactsProvider factsProvider,
                                    CameraProbe cameraProbe,
                                    Supplier<FrameEnvironment> environment,
                                    @Nullable ObstacleResolver obstacleResolver) {
        ExpressionEvaluator checked = Objects.requireNonNull(evaluator, "evaluator");
        this.curveSampler = new ExpressionCurveSampler(checked);
        this.numbers = new AnimatedNumberEvaluator(checked);
        this.rects = new ResolvedRectEvaluator(numbers);
        this.factsProvider = Objects.requireNonNull(factsProvider, "factsProvider");
        this.cameraProbe = Objects.requireNonNull(cameraProbe, "cameraProbe");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.obstacleResolver = obstacleResolver;
    }

    @Override
    public PreparedFrame prepare(AnimationSnapshot snapshot,
                                 PlaybackRequest request,
                                 PlaybackClock clock,
                                 float fromTime,
                                 long frameIndex,
                                 EventBudget eventBudget) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(eventBudget, "eventBudget");

        FrameEnvironment env = environment.get();
        List<Diagnostic> diagnostics = new ArrayList<>();
        CameraAnimation document = snapshot.document();
        float time = clock.time();

        List<CameraRig> active = document.rigs().stream()
                .filter(rig -> rig.view().enabled() && rig.view().active().at(time))
                .toList();
        Set<RigId> activeIds = active.stream()
                .map(CameraRig::id)
                .collect(Collectors.toUnmodifiableSet());

        EvaluationSession session = EvaluationSession.create(DEFAULT_WORK_QUOTA, DEFAULT_MAX_DEPTH);
        FrameFactsPlan plan;

        try {
            plan = FrameFactsPlanBuilder.build(snapshot, time, request.rigOverrides(), activeIds);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(error(snapshot, null, "Frame plan failed: " + exception.getMessage()));
            return frame(clock, snapshot, CameraSource.player(), List.of(), List.of(),
                    emptyFacts(snapshot, env), diagnostics);
        }

        FrameFacts facts;

        try {
            facts = factsProvider.prepare(new FrameFactsRequest(
                    snapshot, time, env.dimension(), env.worldInstanceId(), env.gameTick(),
                    env.partialTick(), plan, session));
        } catch (RuntimeException exception) {
            diagnostics.add(error(snapshot, null, "Frame facts failed: " + messageOf(exception)));
            return frame(clock, snapshot, CameraSource.player(), List.of(), List.of(),
                    emptyFacts(snapshot, env), diagnostics);
        }

        SnapshotTrackIndex index = SnapshotTrackIndex.build(snapshot);
        diagnostics.addAll(index.diagnostics());

        if (!index.diagnostics().isEmpty()) {
            return frame(clock, snapshot, CameraSource.player(), List.of(), List.of(), facts, diagnostics);
        }

        EvaluationContext context = EvaluationContext.bind(
                frameIndex, snapshot, facts, document::curveTrack, index);

        WorldPose base;

        try {
            base = cameraProbe.basePose();
        } catch (RuntimeException exception) {
            diagnostics.add(error(snapshot, null, "Camera probe failed: " + messageOf(exception)));
            return frame(clock, snapshot, CameraSource.player(), List.of(), List.of(), facts, diagnostics);
        }

        List<RigPose> poses = new ArrayList<>();

        for (CameraRig rig : active) {
            RigPose pose = evaluateRig(rig, snapshot, context, session, time, env, base, facts, diagnostics);
            poses.add(pose);
        }

        EventExpansion expansion;

        try {
            expansion = eventExpander.expand(snapshot, fromTime, time, request.loop(),
                    document.duration(), eventBudget);
        } catch (IllegalArgumentException exception) {
            diagnostics.add(error(snapshot, null, "Event expansion failed: " + exception.getMessage()));
            expansion = EventExpansion.success(List.of(), 0);
        }

        if (!expansion.succeeded()) {
            diagnostics.addAll(expansion.failure().diagnostics());

            if (expansion.failure().message() != null) {
                diagnostics.add(error(snapshot, null, expansion.failure().message().toString()));
            }

            return frame(clock, snapshot, document.cameraSourceAt(time), List.of(), List.of(), facts, diagnostics);
        }

        CameraSource source = document.cameraSourceAt(time);

        if (source instanceof CameraSource.Rig rigSource) {
            RigId sourceRig = rigSource.rigId();

            if (document.rig(sourceRig).isEmpty()) {
                diagnostics.add(error(snapshot, sourceRig,
                        "Camera source references a missing rig: " + sourceRig));
            } else if (!activeIds.contains(sourceRig)) {
                diagnostics.add(error(snapshot, sourceRig,
                        "Camera source rig is not active at " + time + "s: " + sourceRig));
            }
        }

        return frame(clock, snapshot, source, poses, expansion.events(), facts, diagnostics);
    }

    private RigPose evaluateRig(CameraRig rig,
                                AnimationSnapshot snapshot,
                                EvaluationContext context,
                                EvaluationSession session,
                                float time,
                                FrameEnvironment env,
                                WorldPose base,
                                FrameFacts facts,
                                List<Diagnostic> diagnostics) {
        List<Diagnostic> poseDiagnostics = new ArrayList<>();

        AxisSample x = axis(rig, CameraChannel.POSITION_X, context, session, time, poseDiagnostics);
        AxisSample y = axis(rig, CameraChannel.POSITION_Y, context, session, time, poseDiagnostics);
        AxisSample z = axis(rig, CameraChannel.POSITION_Z, context, session, time, poseDiagnostics);
        AxisSample pitch = axis(rig, CameraChannel.ROTATION_X, context, session, time, poseDiagnostics);
        AxisSample yaw = axis(rig, CameraChannel.ROTATION_Y, context, session, time, poseDiagnostics);
        AxisSample roll = axis(rig, CameraChannel.ROTATION_Z, context, session, time, poseDiagnostics);
        ChannelSample fovSample = sample(rig, CameraChannel.FOV, context, session, time);
        addSampleDiagnostic(fovSample, poseDiagnostics);

        DocumentPose documentPose = new DocumentPose(x, y, z, pitch, yaw, roll, fovSample, poseDiagnostics);
        diagnostics.addAll(poseDiagnostics);

        Vec3 position = new Vec3(
                value(x, base.position().x()),
                value(y, base.position().y()),
                value(z, base.position().z()));

        if (rig.onPath()) {
            ChannelSample distance = sample(rig, CameraChannel.PATH_DISTANCE, context, session, time);
            addSampleDiagnostic(distance, diagnostics);

            if (!distance.isValue()) {
                diagnostics.add(error(snapshot, rig.id(),
                        "Path position requires a PATH_DISTANCE value"));
            } else {
                CorePathSampler.Result result = pathSampler.sample(
                        rig.path(), rig.distanceMode(), distance.value());

                if (result instanceof CorePathSampler.Result.Success success) {
                    position = success.position();
                } else {
                    CorePathSampler.Result.Failure failure = (CorePathSampler.Result.Failure) result;
                    diagnostics.add(error(snapshot, rig.id(),
                            "Path sampling failed: " + failure.reason()));
                }
            }
        }

        Rotation rotation = new Rotation(
                value(yaw, base.rotation().yaw()),
                value(pitch, base.rotation().pitch()),
                value(roll, base.rotation().roll()));

        Vec3 obstacleOrigin = base.position();

        if (rig.space() instanceof Space.Local) {
            Optional<ResolvedAnchor> anchor = resolvedAnchor(facts, rig.id());

            if (anchor.isEmpty()) {
                diagnostics.add(error(snapshot, rig.id(),
                        "Local rig " + rig.id() + " requires a resolved anchor"));
            } else {
                obstacleOrigin = anchor.get().position();
                position = anchor.get().position().add(
                        CoreRotationMath.rotate(anchor.get().rotation(), position));
                rotation = CoreRotationMath.compose(anchor.get().rotation(), rotation);
            }
        }

        AimState aim = facts.rig(rig.id())
                .map(FrameFacts.RigFacts::aim)
                .orElse(AimState.off());
        rotation = applyAim(rig, snapshot, rotation, position, aim, facts, diagnostics);

        if (rig.obstacle().enabled()) {
            position = applyObstacle(rig, snapshot, obstacleOrigin, position, diagnostics);
        }

        float fov = fovSample.isValue() ? fovSample.value() : base.fov();

        if (!Float.isFinite(fov) || fov <= 0f) {
            diagnostics.add(error(snapshot, rig.id(), "Final FOV must be finite and positive: " + fov));
            fov = base.fov();
        }

        LayoutEvalInput layoutInput = new LayoutEvalInput(
                context, session, rig.id(), cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource.LayoutField.X,
                time, progress(time, snapshot.document().duration()), env.gameTick() + env.partialTick(),
                SpatialReadings.EMPTY, env.gameTick(), curveSampler);

        ResolvedRect rect;
        ResolvedRectEvaluator.Result rectResult = rects.evaluate(rig.view().rect(), layoutInput);

        if (rectResult instanceof ResolvedRectEvaluator.Result.Success success) {
            rect = success.rect();
        } else {
            ResolvedRectEvaluator.Result.Failure failure = (ResolvedRectEvaluator.Result.Failure) rectResult;
            diagnostics.add(failure.diagnostic());
            rect = new ResolvedRect.Normalized(0, 0, 1, 1);
        }

        return new RigPose(rig.id(), new WorldPose(
                position, rotation, fov), rect, rig.view().zOrder(),
                rig.view().resolutionScale(), rig.view().updateEveryFrames());
    }

    private Vec3 applyObstacle(CameraRig rig,
                               AnimationSnapshot snapshot,
                               Vec3 origin,
                               Vec3 expected,
                               List<Diagnostic> diagnostics) {
        if (obstacleResolver == null) {
            diagnostics.add(error(snapshot, rig.id(),
                    "Obstacle is enabled but no host obstacle resolver is installed"));
            return expected;
        }

        ObstacleResolver.Result result;

        try {
            result = obstacleResolver.resolve(new ObstacleResolver.Request(
                    rig.id(), origin, expected, rig.obstacle().margin()));
        } catch (RuntimeException exception) {
            diagnostics.add(error(snapshot, rig.id(),
                    "Obstacle resolver failed: " + messageOf(exception)));
            return expected;
        }

        if (result instanceof ObstacleResolver.Result.Adjusted adjusted) {
            return adjusted.position();
        }

        ObstacleResolver.Result.Unavailable unavailable = (ObstacleResolver.Result.Unavailable) result;
        diagnostics.add(error(snapshot, rig.id(),
                "Obstacle resolver unavailable: " + unavailable.reason()));
        return expected;
    }

    private Rotation applyAim(CameraRig rig,
                              AnimationSnapshot snapshot,
                              Rotation rotation,
                              Vec3 position,
                              AimState aim,
                              FrameFacts facts,
                              List<Diagnostic> diagnostics) {
        if (!(aim instanceof AimState.Target targetState)) {
            return rotation;
        }

        AimTarget target = targetState.target();
        Optional<ResolvedAnchor> targetAnchor = resolvedTarget(facts, rig.id());

        if (targetAnchor.isEmpty()) {
            diagnostics.add(error(snapshot, rig.id(), "Aim target was not resolved"));
            return rotation;
        }

        Vec3 point = targetAnchor.get().position().add(target.offset());
        Vec3 direction = point.subtract(position);

        if (direction.length() == 0 || !Double.isFinite(direction.length())) {
            diagnostics.add(error(snapshot, rig.id(), "Aim target coincides with camera position"));
            return rotation;
        }

        Rotation look = CoreRotationMath.lookRotation(direction);
        RotationMask mask = target.mask();

        return new Rotation(
                mask.y() ? look.yaw() : rotation.yaw(),
                mask.x() ? look.pitch() : rotation.pitch(),
                rotation.roll());
    }

    private AxisSample axis(CameraRig rig,
                            CameraChannel channel,
                            EvaluationContext context,
                            EvaluationSession session,
                            float time,
                            List<Diagnostic> diagnostics) {
        ChannelSample sample = sample(rig, channel, context, session, time);

        return switch (sample.status()) {
            case VALUE -> new AxisSample.Value(sample.value());
            case ABSENT -> new AxisSample.Absent();
            case ERROR -> {
                Diagnostic diagnostic = Objects.requireNonNull(sample.diagnostic());
                diagnostics.add(diagnostic);
                yield new AxisSample.Error(diagnostic);
            }
        };
    }

    private ChannelSample sample(CameraRig rig,
                                 CameraChannel channel,
                                 EvaluationContext context,
                                 EvaluationSession session,
                                 float time) {
        Optional<CurveTrack> track = rig.channels().stream()
                .filter(candidate -> candidate.channel() == channel)
                .findFirst();

        if (track.isEmpty()) {
            return ChannelSample.absent();
        }

        return curveSampler.sample(new CurveSampleRequest(
                context, session, track.get().id(), rig.id(), time));
    }

    private static void addSampleDiagnostic(ChannelSample sample, List<Diagnostic> diagnostics) {
        if (sample.isError() && sample.diagnostic() != null) {
            diagnostics.add(sample.diagnostic());
        }
    }

    private static double value(AxisSample sample, double fallback) {
        return sample instanceof AxisSample.Value value ? value.value() : fallback;
    }

    private static Optional<ResolvedAnchor> resolvedAnchor(FrameFacts facts, RigId rigId) {
        return facts.rig(rigId)
                .map(FrameFacts.RigFacts::anchor)
                .filter(SpatialResolution.Resolved.class::isInstance)
                .map(SpatialResolution.Resolved.class::cast)
                .map(SpatialResolution.Resolved::anchor);
    }

    private static Optional<ResolvedAnchor> resolvedTarget(FrameFacts facts, RigId rigId) {
        return facts.rig(rigId)
                .map(FrameFacts.RigFacts::target)
                .filter(SpatialResolution.Resolved.class::isInstance)
                .map(SpatialResolution.Resolved.class::cast)
                .map(SpatialResolution.Resolved::anchor);
    }

    private static float progress(float time, float duration) {
        if (duration <= 0f) {
            return 0f;
        }

        return Math.max(0f, Math.min(1f, time / duration));
    }

    private static PreparedFrame frame(PlaybackClock clock,
                                       AnimationSnapshot snapshot,
                                       CameraSource source,
                                       List<RigPose> rigs,
                                       List<PreparedFrame.Event> events,
                                       FrameFacts facts,
                                       List<Diagnostic> diagnostics) {
        return new PreparedFrame(clock, snapshot, source, rigs, events, facts, diagnostics);
    }

    private static FrameFacts emptyFacts(AnimationSnapshot snapshot, FrameEnvironment env) {
        return new FrameFacts(snapshot, env.dimension(), env.worldInstanceId(), env.gameTick(),
                env.partialTick(), 0L, Map.of(), Map.of(), false);
    }

    private static Diagnostic error(AnimationSnapshot snapshot, @Nullable RigId rigId, String message) {
        return new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                LocalizedMessage.literal(message),
                new DiagnosticScope.Document(snapshot.revision()),
                new Diagnostic.Location(rigId, "playback"), null);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
