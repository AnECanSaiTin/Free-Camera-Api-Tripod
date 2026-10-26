package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.AimCaptureMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.AimCaptureRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.BakeRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.BatchTransform;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.BatchTransformRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EasingPreset;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EasingRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.ToolResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackLookup;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.IdGenerator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.SnapshotTrackIndex;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 缓动、批量变换与烘焙的 core 默认工具（主设计 §4.6）。
class CoreEditToolsTest {

    private static final RigId RIG = new RigId("rig");
    private static final TrackId TRACK = new TrackId("track");
    private static final TrackId AIM = new TrackId("aim");
    private static final TrackId ACTIVE = new TrackId("active");
    private static final TrackId SOURCE = new TrackId("source");
    private static final KeyId KEY_A = new KeyId("key-a");
    private static final KeyId KEY_B = new KeyId("key-b");
    private static final Identifier DIMENSION =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Anchor TARGET_ANCHOR =
            () -> Identifier.fromNamespaceAndPath("test", "target");
    private static final AimTarget AIM_TARGET = AimTarget.lookAt(TARGET_ANCHOR);

    @Test
    void aimCaptureWritesRotationKeysAndAimOff() {
        AnimationSnapshot snapshot = aimSnapshot(true);
        AimCaptureRequest request = aimRequest(snapshot, AimOverride.inherit());

        ToolResult result = new CoreEditTools().captureAim(request);

        ToolResult.Ready ready = assertType(ToolResult.Ready.class, result);
        EditOp.Batch batch = assertType(EditOp.Batch.class, ready.operation());
        assertEquals(4, batch.operations().size());
        assertEquals(3, (int) batch.operations().stream()
                .filter(operation -> operation instanceof EditOp.AddNumberKey)
                .count());
        assertEquals(1, (int) batch.operations().stream()
                .filter(operation -> operation instanceof EditOp.SetStateKey)
                .count());
    }

    @Test
    void aimCaptureRejectsReplaceOverrideAndMissingRotationChannel() {
        AnimationSnapshot replaceSnapshot = aimSnapshot(true);
        ToolResult replaced = new CoreEditTools().captureAim(
                aimRequest(replaceSnapshot, AimOverride.replace(AIM_TARGET)));
        assertType(ToolResult.Rejected.class, replaced);

        AnimationSnapshot missingChannel = aimSnapshot(false);
        ToolResult missing = new CoreEditTools().captureAim(
                aimRequest(missingChannel, AimOverride.inherit()));
        assertType(ToolResult.Rejected.class, missing);
    }

    @Test
    void easingWritesInterpolationAndHandlesForSelectedKeys() {
        CameraAnimation document = document(curve(0f, 1f));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);
        EasingPreset preset = new EasingPreset(
                "smooth", Curve.Interpolation.HERMITE, 0.1f, 0.2f, 0.5f, 0.75f);

        ToolResult result = new CoreEditTools().applyEasing(new EasingRequest(
                TRACK, List.of(KEY_A), preset, snapshot));

        ToolResult.Ready ready = assertType(ToolResult.Ready.class, result);
        EditOp.Batch batch = assertType(EditOp.Batch.class, ready.operation());
        assertEquals(2, batch.operations().size());

        EditOp.SetKeyInterpolation interpolation = assertType(
                EditOp.SetKeyInterpolation.class, batch.operations().get(0));
        assertEquals(KEY_A, interpolation.keyId());
        assertEquals(Curve.Interpolation.HERMITE, interpolation.interpolation());

        EditOp.SetKeyHandles handles = assertType(
                EditOp.SetKeyHandles.class, batch.operations().get(1));
        assertEquals(0.5f, handles.inLength());
        assertEquals(0.75f, handles.outLength());
    }

    @Test
    void easingRejectsMissingKeyAndExcessiveHandle() {
        CameraAnimation document = document(curve(0f, 1f));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);

        ToolResult missing = new CoreEditTools().applyEasing(new EasingRequest(
                TRACK, List.of(new KeyId("missing")),
                new EasingPreset("linear", Curve.Interpolation.LINEAR, 0f, 0f, 0f, 0f),
                snapshot));

        assertType(ToolResult.Rejected.class, missing);

        ToolResult excessive = new CoreEditTools().applyEasing(new EasingRequest(
                TRACK, List.of(KEY_A),
                new EasingPreset("too-long", Curve.Interpolation.LINEAR, 0f, 0f, 2f, 0f),
                snapshot));

        assertType(ToolResult.Rejected.class, excessive);
    }

    @Test
    void batchTransformMovesTimeAndTransformsConstantValue() {
        CameraAnimation document = document(curve(0f, 1f));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);

        ToolResult result = new CoreEditTools().applyBatchTransform(
                new BatchTransformRequest(TRACK, List.of(KEY_A),
                        new BatchTransform(0.5f, 2f, 3f, true), snapshot));

        ToolResult.Ready ready = assertType(ToolResult.Ready.class, result);
        EditOp.Batch batch = assertType(EditOp.Batch.class, ready.operation());
        assertEquals(2, batch.operations().size());

        EditOp.MoveKeys move = assertType(EditOp.MoveKeys.class, batch.operations().get(0));
        assertEquals(1, move.moves().size());
        assertEquals(0.5f, move.moves().getFirst().time());

        EditOp.SetKeyValue value = assertType(
                EditOp.SetKeyValue.class, batch.operations().get(1));
        assertEquals(KEY_A, value.keyId());
        Constant constant = assertType(Constant.class, value.value());
        // 值 2 先取反得 -2，再乘 2 加 3 得 -1。
        assertEquals(-1f, constant.value());
    }

    @Test
    void batchTransformRejectsFormulaValueAndTimeCollision() {
        CameraAnimation formulaDocument = document(new Curve(
                List.of(
                        key(KEY_A, 0f, new Constant(1f)),
                        key(KEY_B, 1f, new Formula("1 + 1"))),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP));
        AnimationSnapshot formulaSnapshot = new AnimationSnapshot(
                new Revision(1), formulaDocument);

        ToolResult formula = new CoreEditTools().applyBatchTransform(
                new BatchTransformRequest(TRACK, List.of(KEY_B),
                        new BatchTransform(0f, 2f, 0f, false), formulaSnapshot));
        assertType(ToolResult.Rejected.class, formula);

        CameraAnimation collisionDocument = document(curve(0f, 1f));
        AnimationSnapshot collisionSnapshot = new AnimationSnapshot(
                new Revision(1), collisionDocument);

        ToolResult collision = new CoreEditTools().applyBatchTransform(
                new BatchTransformRequest(TRACK, List.of(KEY_A),
                        new BatchTransform(1f, 1f, 0f, false), collisionSnapshot));
        assertType(ToolResult.Rejected.class, collision);
    }

    @Test
    void bakeSamplesInclusiveRangeAndGeneratesFreshIds() {
        CameraAnimation document = document(curve(0f, 1f));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);
        EvaluationContext context = context(snapshot, document.curveTrack(TRACK).orElseThrow());
        AtomicInteger ids = new AtomicInteger();
        IdGenerator generator = type -> "bake-" + ids.incrementAndGet();
        CoreEditTools tools = new CoreEditTools(generator,
                request -> cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample
                        .value(request.sampleTime()));

        ToolResult result = tools.bake(new BakeRequest(
                RIG, TRACK, 0f, 1f, 0.25f, Curve.Interpolation.LINEAR, 5,
                context, EvaluationSession.create(64, 8)));

        ToolResult.Ready ready = assertType(ToolResult.Ready.class, result);
        EditOp.ReplaceKeys replace = assertType(EditOp.ReplaceKeys.class, ready.operation());
        assertEquals(5, replace.keys().size());

        for (int index = 0; index < 5; index++) {
            Keyframe key = replace.keys().get(index);
            assertEquals(index * 0.25f, key.time());
            assertEquals(key.time(), ((Constant) key.value()).value());
        }

        assertNotEquals(replace.keys().get(0).keyId(), replace.keys().get(1).keyId());
    }

    @Test
    void bakeWithDecimalStepProducesUniqueEndpointOnce() {
        CameraAnimation document = document(curve(0f, 1f));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);
        EvaluationContext context = context(snapshot, document.curveTrack(TRACK).orElseThrow());
        CoreEditTools tools = new CoreEditTools(type -> "bake",
                request -> cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample
                        .value(request.sampleTime()));

        ToolResult result = tools.bake(new BakeRequest(
                RIG, TRACK, 0f, 0.3f, 0.1f, Curve.Interpolation.LINEAR, 4,
                context, EvaluationSession.create(64, 8)));

        ToolResult.Ready ready = assertType(ToolResult.Ready.class, result);
        EditOp.ReplaceKeys replace = assertType(EditOp.ReplaceKeys.class, ready.operation());
        assertEquals(4, replace.keys().size());
        assertEquals(0f, replace.keys().get(0).time());
        assertEquals(0.1f, replace.keys().get(1).time());
        assertEquals(0.2f, replace.keys().get(2).time());
        assertEquals(0.3f, replace.keys().get(3).time());
        assertEquals(4L, replace.keys().stream().map(Keyframe::time).distinct().count());

        ToolResult tooSmall = tools.bake(new BakeRequest(
                RIG, TRACK, 0f, 0.3f, 0.1f, Curve.Interpolation.LINEAR, 3,
                context, EvaluationSession.create(64, 8)));
        assertType(ToolResult.Rejected.class, tooSmall);
    }

    @Test
    void aimCaptureWorksWhenObstacleIsEnabled() {
        AnimationSnapshot base = aimSnapshot(true);
        CameraRig rig = base.document().rigs().getFirst();
        CameraRig withObstacle = new CameraRig(rig.id(), rig.name(), rig.channels(), rig.path(),
                rig.motionMode(), rig.distanceMode(), rig.space(), rig.aim(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings(true, 0.1),
                rig.view());
        AnimationSnapshot snapshot = new AnimationSnapshot(
                new Revision(1), base.document().withRig(withObstacle));

        ToolResult result = new CoreEditTools().captureAim(
                aimRequest(snapshot, AimOverride.inherit()));

        assertType(ToolResult.Ready.class, result);
    }

    @Test
    void bakeRejectsTooManyKeysBeforeSampling() {
        CameraAnimation document = document(curve(0f, 1f));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);
        EvaluationContext context = context(snapshot, document.curveTrack(TRACK).orElseThrow());
        CoreEditTools tools = new CoreEditTools(type -> "bake", request -> {
            throw new AssertionError("sampler must not be called");
        });

        ToolResult result = tools.bake(new BakeRequest(
                RIG, TRACK, 0f, 1f, 0.25f, Curve.Interpolation.LINEAR, 4,
                context, EvaluationSession.create(64, 8)));

        assertType(ToolResult.Rejected.class, result);
    }

    @Test
    void bakeRejectsAbsentSample() {
        CameraAnimation document = document(curve(0f, 1f));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);
        EvaluationContext context = context(snapshot, document.curveTrack(TRACK).orElseThrow());
        CoreEditTools tools = new CoreEditTools(type -> "bake",
                request -> cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample
                        .absent());

        ToolResult result = tools.bake(new BakeRequest(
                RIG, TRACK, 0f, 1f, 0.5f, Curve.Interpolation.LINEAR, 5,
                context, EvaluationSession.create(64, 8)));

        assertType(ToolResult.Rejected.class, result);
    }

    private static <T> T assertType(Class<T> type, Object value) {
        assertTrue(type.isInstance(value),
                () -> "expected " + type.getSimpleName() + " but got " + value);
        return type.cast(value);
    }

    private static AnimationSnapshot aimSnapshot(boolean includeZ) {
        List<CurveTrack> channels = new java.util.ArrayList<>();
        channels.add(CurveTrack.ofChannel(new TrackId("rot-x"), "rot-x",
                CameraChannel.ROTATION_X, Curve.EMPTY));
        channels.add(CurveTrack.ofChannel(new TrackId("rot-y"), "rot-y",
                CameraChannel.ROTATION_Y, Curve.EMPTY));

        if (includeZ) {
            channels.add(CurveTrack.ofChannel(new TrackId("rot-z"), "rot-z",
                    CameraChannel.ROTATION_Z, Curve.EMPTY));
        }

        AimTrack aim = AimTrack.of(AIM, "aim", AimState.off(),
                List.of(ValueKeyframe.at(new KeyId("aim-target"), 0f,
                        AimState.target(AIM_TARGET))));
        CameraRig rig = CameraRig.create(RIG, "rig", channels, aim.id(), ACTIVE)
                .withAim(aim);
        CameraAnimation document = CameraAnimation.create("doc", rig, SOURCE);
        return new AnimationSnapshot(new Revision(1), document);
    }

    private static AimCaptureRequest aimRequest(AnimationSnapshot snapshot, AimOverride override) {
        FrameFacts.RigFacts rigFacts = new FrameFacts.RigFacts(
                new SpatialResolution.NotRequired(),
                new SpatialResolution.Resolved(ResolvedAnchor.at(
                        TARGET_ANCHOR, new Vec3(0, 0, 10))),
                AimState.target(AIM_TARGET));
        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 7L, 1L, 0f,
                new WorldTime(12000, 24000), 1L,
                Map.of(RIG, rigFacts), Map.of(), true);
        TrackLookup lookup = trackId -> snapshot.document().curveTrack(trackId);
        TrackIndex index = SnapshotTrackIndex.build(snapshot);
        EvaluationContext context = EvaluationContext.bind(1, snapshot, facts, lookup, index);

        return new AimCaptureRequest(
                RIG, 0f, AimCaptureMode.CURRENT, override,
                new WorldPose(Vec3.ZERO, Rotation.NONE, 70f),
                new Revision(1), context, EvaluationSession.create(256, 16));
    }

    private static CameraAnimation document(Curve curve) {
        CurveTrack track = CurveTrack.ofCurve(TRACK, "track", curve);
        CameraRig rig = CameraRig.create(RIG, "rig", List.of(track), AIM, ACTIVE);
        return CameraAnimation.create("doc", rig, SOURCE);
    }

    private static Curve curve(float firstTime, float secondTime) {
        return new Curve(List.of(
                key(KEY_A, firstTime, new Constant(2f)),
                key(KEY_B, secondTime, new Constant(4f))),
                Curve.Wrap.CLAMP, Curve.Wrap.CLAMP);
    }

    private static Keyframe key(KeyId keyId, float time, cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.NumberSource value) {
        return new Keyframe(keyId, time, value, 0f, 0f, 0.2f, 0.2f,
                Curve.Interpolation.LINEAR);
    }

    private static EvaluationContext context(AnimationSnapshot snapshot, CurveTrack track) {
        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 7L, 1L, 0f,
                new WorldTime(12000, 24000), 1L, Map.of(), Map.of(), true);
        TrackLookup lookup = trackId -> trackId.equals(TRACK) ? Optional.of(track) : Optional.empty();
        TrackIndex index = new TrackIndex() {
            @Override
            public AnimationSnapshot snapshot() {
                return snapshot;
            }

            @Override
            public Optional<Entry> find(TrackId trackId) {
                return trackId.equals(TRACK)
                        ? Optional.of(new Entry(track, RIG))
                        : Optional.empty();
            }

            @Override
            public List<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic>
            diagnostics() {
                return List.of();
            }
        };

        return EvaluationContext.bind(1, snapshot, facts, lookup, index);
    }
}
