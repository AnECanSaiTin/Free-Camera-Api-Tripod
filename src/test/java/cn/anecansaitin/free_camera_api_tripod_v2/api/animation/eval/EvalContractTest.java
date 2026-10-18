package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 求值契约的纯结构约束（主设计 §3.4、§6.1–§6.2）。
class EvalContractTest {

    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier ANCHOR_TYPE =
            Identifier.fromNamespaceAndPath("free_camera_api_tripod", "test_anchor");

    private static FrameFacts emptyFacts() {
        return new FrameFacts(DIMENSION, 7L, 1L, 0.5f, 1L, Map.of(), Map.of(), true);
    }

    private static Diagnostic errorDiagnostic() {
        return new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("broken"), new DiagnosticScope.File(new ResourceId("test-resource")),
                null, null);
    }

    @Test
    void channelSampleSeparatesAbsentValueAndError() {
        assertEquals(SampleStatus.ABSENT, ChannelSample.absent().status());
        assertTrue(ChannelSample.value(2f).isValue());
        assertFalse(ChannelSample.value(2f).isError());

        ChannelSample error = ChannelSample.error(errorDiagnostic());
        assertTrue(error.isError());
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelSample(SampleStatus.ERROR, 0f, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ChannelSample(SampleStatus.VALUE, Float.NaN, null));
    }

    @Test
    void frameFactsCopiesMapsAndValidatesPartialTick() {
        Map<RigId, FrameFacts.RigFacts> rigs = new HashMap<>();
        rigs.put(new RigId("rig"), new FrameFacts.RigFacts(
                new SpatialResolution.Resolved(ResolvedAnchor.at(() -> ANCHOR_TYPE, Vec3.ZERO)),
                new SpatialResolution.NotRequired(), AimState.off()));

        FrameFacts facts = new FrameFacts(DIMENSION, 7L, 10L, 0.25f, 3L, rigs, Map.of(), true);
        rigs.clear();

        assertTrue(facts.rig(new RigId("rig")).isPresent());
        assertEquals(0.25f, facts.partialTick());
        assertThrows(IllegalArgumentException.class,
                () -> new FrameFacts(DIMENSION, 7L, 0L, 1f, 0L, Map.of(), Map.of(), true));
        assertThrows(IllegalArgumentException.class,
                () -> new FrameFacts(DIMENSION, 7L, -1L, 0f, 0L, Map.of(), Map.of(), true));
        assertThrows(IllegalArgumentException.class,
                () -> new FrameFacts(DIMENSION, -1L, 0L, 0f, 0L, Map.of(), Map.of(), true));
    }

    @Test
    void documentPoseCopiesDiagnosticsAndKeepsAxisStatesSeparate() {
        List<Diagnostic> diagnostics = new ArrayList<>(List.of(errorDiagnostic()));
        DocumentPose pose = new DocumentPose(
                new AxisSample.Value(0), new AxisSample.Absent(), new AxisSample.Value(0),
                new AxisSample.Value(0), new AxisSample.Value(0), new AxisSample.Absent(),
                ChannelSample.absent(), diagnostics);
        diagnostics.clear();

        assertEquals(1, pose.diagnostics().size());
        assertTrue(pose.positionMask().x());
        assertFalse(pose.positionMask().y());
        assertFalse(pose.rotationMask().z());
        assertFalse(pose.position().isPresent());
        assertFalse(pose.rotation().isPresent());
        assertTrue(pose.hasError());
    }

    @Test
    void axisAndSpatialResolutionKeepErrorSeparateFromAbsence() {
        Diagnostic error = errorDiagnostic();

        assertTrue(new AxisSample.Absent().isAbsent());
        assertEquals(1.0, ((AxisSample.Value) new AxisSample.Value(1.0)).value());
        assertTrue(new AxisSample.Error(error).isError());
        assertThrows(IllegalArgumentException.class,
                () -> new AxisSample.Value(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new AxisSample.Error(new Diagnostic(DiagnosticCode.BROKEN_REFERENCE,
                        Severity.WARNING, LocalizedMessage.literal("warning"),
                        new DiagnosticScope.File(new ResourceId("test")), null, null)));

        assertFalse(new SpatialResolution.NotRequired().isResolved());
        assertTrue(new SpatialResolution.Resolved(
                ResolvedAnchor.at(() -> ANCHOR_TYPE, Vec3.ZERO)).isResolved());
        assertTrue(new SpatialResolution.Failed(error).isFailed());
    }

    @Test
    void spatialReadingsRejectNegativeDistanceAndExposeEmpty() {
        assertTrue(SpatialReadings.EMPTY.isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new SpatialReadings(null, null, -1.0));
    }

    @Test
    void evaluationKeyRequiresTrackAndNonNegativeIdentities() {
        EvaluationKey key = new EvaluationKey(1, 2, 7, 3, 0.5f, new RigId("rig"),
                new TrackId("track"), 4, 5);

        assertEquals(new TrackId("track"), key.trackId());
        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationKey(-1, 0, 0, 0, 0f, null, new TrackId("t"), 0, 0));
        assertThrows(NullPointerException.class,
                () -> new EvaluationKey(1, 2, 7, 3, 0f, null, null, 4, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationKey(1, 2, 7, 3, Float.NaN, null, new TrackId("t"), 4, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new EvaluationKey(1, 2, -1, 3, 0f, null, new TrackId("t"), 4, 5));
    }

    @Test
    void evaluationKeyDistinguishesSampleTimesAndWorldInstances() {
        EvaluationKey atZero = new EvaluationKey(1, 2, 7, 3, -0.0f, null, new TrackId("t"), 4, 5);
        EvaluationKey atOne = new EvaluationKey(1, 2, 7, 3, 1.0f, null, new TrackId("t"), 4, 5);
        EvaluationKey otherWorld = new EvaluationKey(1, 2, 8, 3, 0f, null, new TrackId("t"), 4, 5);

        assertFalse(atZero.equals(atOne));
        assertFalse(atZero.equals(otherWorld));
        assertEquals(0, Float.floatToIntBits(atZero.sampleTime()));
    }

    @Test
    void evaluationContextRequiresSnapshotFactsAndLookups() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1),
                CameraAnimation.create("doc", rig, new TrackId("source")));

        TrackLookup lookup = trackId -> Optional.empty();
        TrackIndex index = new TrackIndex() {
            @Override
            public AnimationSnapshot snapshot() {
                return snapshot;
            }

            @Override
            public Optional<Entry> find(TrackId trackId) {
                return Optional.empty();
            }

            @Override
            public List<Diagnostic> diagnostics() {
                return List.of();
            }
        };

        EvaluationContext context = EvaluationContext.bind(1, snapshot, emptyFacts(), lookup, index);
        assertEquals(1, context.contextId());

        CurveSampleRequest request = new CurveSampleRequest(
                context, new TrackId("track"), new RigId("rig"), -0.0f);
        assertEquals(0, Float.floatToIntBits(request.sampleTime()));

        EvaluationKey fromRequest = EvaluationKey.from(request, 9L, 3L, 4L);
        assertEquals(7L, fromRequest.worldInstanceId());
        assertEquals(1L, fromRequest.sampleTableVersion());
        assertEquals(0, Float.floatToIntBits(fromRequest.sampleTime()));

        assertThrows(IllegalArgumentException.class,
                () -> new CurveSampleRequest(context, new TrackId("track"), null, Float.NaN));

        assertThrows(NullPointerException.class, () -> new AnimationSnapshot(new Revision(1), null));
        assertThrows(NullPointerException.class, () -> new EvaluationContext(0, null, emptyFacts(), lookup, index));
    }
}
