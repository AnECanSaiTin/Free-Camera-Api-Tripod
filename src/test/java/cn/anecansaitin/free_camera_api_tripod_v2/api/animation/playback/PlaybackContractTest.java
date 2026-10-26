package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 播放契约的纯结构约束（主设计 §7.1–§7.6）。
class PlaybackContractTest {

    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    private static CameraAnimation document() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));

        return CameraAnimation.create("doc", rig, new TrackId("source"));
    }

    private static FrameFacts facts() {
        return new FrameFacts(snapshot(), DIMENSION, 7L, 1L, 0f,
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime(12000, 24000),
                1L, Map.of(), Map.of(), true);
    }

    private static RigQualityStatus quality() {
        OutputPixelRect full = new OutputPixelRect(0, 0, 100, 100);
        return new RigQualityStatus(full, full, 100, 100, 100, 100, 1.0, false);
    }

    private static RigQualityStatus offscreenQuality() {
        return new RigQualityStatus(
                new OutputPixelRect(-200, 0, 100, 100), null, 100, 100, 0, 0, 0.0, false);
    }

    private static AnimationSnapshot snapshot() {
        return new AnimationSnapshot(new Revision(1), document());
    }

    private static Diagnostic errorDiagnostic() {
        return new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("broken"), new DiagnosticScope.File(new ResourceId("test-resource")),
                null, null);
    }

    private static PlaybackFailure failure() {
        return new PlaybackFailure(PlaybackFailure.Reason.EVALUATION_ERROR,
                LocalizedMessage.literal("failed"), 1f, List.of());
    }

    @Test
    void playbackRequestRejectsInvalidValuesAndCopiesOverrides() {
        Map<RigId, RigPlaybackOverrides> overrides = new HashMap<>();
        overrides.put(new RigId("rig"), RigPlaybackOverrides.INHERIT);

        PlaybackRequest request = new PlaybackRequest(document(), overrides, 1f, false, 0f);
        overrides.clear();

        assertEquals(1, request.rigOverrides().size());
        assertThrows(IllegalArgumentException.class,
                () -> new PlaybackRequest(document(), Map.of(), 0f, false, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaybackRequest(document(), Map.of(), 1f, false, -1f));
    }

    @Test
    void overridesDefaultToInherit() {
        RigPlaybackOverrides overrides = new RigPlaybackOverrides(null, null, null, null);

        assertEquals(AimOverride.inherit(), overrides.aim());
        assertEquals(AimOverride.inherit(), RigPlaybackOverrides.INHERIT.aim());
    }

    @Test
    void clockValidatesAndReportsFinish() {
        assertTrue(new PlaybackClock(5f, 1f, false, 5f).isFinished());
        assertFalse(new PlaybackClock(4f, 1f, false, 5f).isFinished());
        assertFalse(new PlaybackClock(99f, 1f, true, 5f).isFinished());

        assertThrows(IllegalArgumentException.class, () -> new PlaybackClock(Float.NaN, 1f, false, 5f));
        assertThrows(IllegalArgumentException.class, () -> new PlaybackClock(0f, 0f, false, 5f));
    }

    @Test
    void preparedFrameCopiesCollectionsAndReportsExecutability() {
        List<PreparedFrame.Event> events = new ArrayList<>(List.of(
                new PreparedFrame.Event(new TrackId("event"), 1f)));
        List<Diagnostic> diagnostics = new ArrayList<>(List.of(errorDiagnostic()));

        PreparedFrame frame = new PreparedFrame(
                new PlaybackClock(0f, 1f, false, 10f),
                snapshot(),
                CameraSource.player(),
                List.of(),
                events,
                facts(),
                diagnostics);

        events.clear();
        diagnostics.clear();

        assertEquals(1, frame.events().size());
        assertEquals(1, frame.diagnostics().size());
        assertEquals(new Revision(1), frame.snapshot().revision());
        assertFalse(frame.isExecutable());
    }

    @Test
    void rigPoseFreezesLayerAndQualityRequests() {
        RigPose pose = new RigPose(
                new RigId("rig"),
                new WorldPose(Vec3.ZERO, Rotation.NONE, 70f),
                new ResolvedRect.Normalized(0, 0, 1, 1),
                3,
                0.5,
                2);

        assertEquals(3, pose.zOrder());
        assertEquals(0.5, pose.resolutionScale());
        assertEquals(2, pose.updateEveryFrames());

        assertThrows(IllegalArgumentException.class, () -> new RigPose(
                new RigId("rig"), new WorldPose(Vec3.ZERO, Rotation.NONE, 70f),
                new ResolvedRect.Normalized(0, 0, 1, 1), 0, 0.0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RigPose(
                new RigId("rig"), new WorldPose(Vec3.ZERO, Rotation.NONE, 70f),
                new ResolvedRect.Normalized(0, 0, 1, 1), 0, 1.0, 0));
    }

    @Test
    void resolvedRectRejectsIllegalEvaluatedGeometry() {
        // 已求值矩形不接受非有限值与非正尺寸：非法求值结果不能伪装成合法准备帧
        // （主设计 §3.1.2：曲线过冲与公式导致的非法中间帧属于运行期错误）。
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedRect.Normalized(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedRect.Normalized(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedRect.Anchored(RigView.Rect.Corner.TOP_LEFT, 0, 0, -1, 1));

        ResolvedRect anchored = new ResolvedRect.Anchored(RigView.Rect.Corner.BOTTOM_RIGHT, -10, -20, 320, 180);

        assertFalse(anchored.scalesWithWindow());
        assertFalse(anchored.normalized());
        assertTrue(anchored.asNormalized().isEmpty());
    }

    @Test
    void eventBudgetAndRenderStatusValidateBounds() {
        assertThrows(IllegalArgumentException.class, () -> new EventBudget(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new EventBudget(1, 0));

        RigRenderStatus.SampleIdentity sample = new RigRenderStatus.SampleIdentity(1, 0f, 1, 7, 1, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new RigRenderStatus(
                new RigId("rig"), true, 0, 100, null, sample, quality()));

        RigRenderStatus stopped = new RigRenderStatus(
                new RigId("rig"), false, 0, 0, DegradeReason.RIG_PAUSED, null, null);
        assertFalse(stopped.rendered());
    }

    @Test
    void visibleQualityDistinguishesOffscreenAndChecksGeometry() {
        RigRenderStatus offscreen = new RigRenderStatus(
                new RigId("rig"), false, 0, 0, DegradeReason.CANVAS_OFFSCREEN, null, offscreenQuality());
        assertTrue(offscreen.quality().offscreen());

        assertThrows(IllegalArgumentException.class, () -> new RigRenderStatus(
                new RigId("rig"), true, 0, 0, DegradeReason.CANVAS_OFFSCREEN, null, offscreenQuality()));
        assertThrows(IllegalArgumentException.class, () -> new RigRenderStatus(
                new RigId("rig"), true, 100, 100, null,
                new RigRenderStatus.SampleIdentity(1, 0f, 1, 7, 1, 0, 0), offscreenQuality()));

        OutputPixelRect full = new OutputPixelRect(0, 0, 100, 100);
        assertTrue(full.intersection(new OutputPixelRect(150, 0, 10, 10)).isEmpty());
        assertEquals(10L * 10L,
                full.intersection(new OutputPixelRect(90, 90, 20, 20)).orElseThrow().area());
        assertThrows(IllegalArgumentException.class,
                () -> new RigQualityStatus(full, new OutputPixelRect(90, 90, 20, 20),
                        100, 100, 100, 100, 1.0, false));

        RigQualityStatus visiblePaused = new RigQualityStatus(
                full, full, 100, 100, 0, 0, 0.0, false);
        RigRenderStatus pausedWithGeometry = new RigRenderStatus(
                new RigId("rig"), false, 0, 0, DegradeReason.RIG_PAUSED, null, visiblePaused);
        assertFalse(pausedWithGeometry.quality().offscreen());
        assertEquals(0.0, pausedWithGeometry.quality().density());
    }

    @Test
    void renderedStatusRequiresDisplayedSampleIdentity() {
        RigRenderStatus.SampleIdentity sample = new RigRenderStatus.SampleIdentity(
                42, 1.5f, 20, 7, 1, 2, 3);
        RigRenderStatus reused = new RigRenderStatus(
                new RigId("rig"), true, 100, 100, DegradeReason.FRAME_SKIPPED, sample, quality());

        assertEquals(sample, reused.sampledFrame());
        assertEquals(7, sample.worldInstanceId());
        assertEquals(2, sample.activityGeneration());

        assertThrows(IllegalArgumentException.class, () -> new RigRenderStatus(
                new RigId("rig"), true, 100, 100, null, null, quality()));
        assertThrows(IllegalArgumentException.class, () -> new RigRenderStatus(
                new RigId("rig"), false, 0, 0, DegradeReason.FRAME_SKIPPED, null, null));
        assertThrows(IllegalArgumentException.class, () -> new RigRenderStatus(
                new RigId("rig"), true, 100, 100, DegradeReason.RIG_PAUSED, sample, quality()));
        assertThrows(IllegalArgumentException.class,
                () -> new RigRenderStatus.SampleIdentity(-1, 0f, 0, 0, 0, 0, 0));
    }

    @Test
    void compositionAndStartResultsEnforceConsistency() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompositionResult(true, List.of(), failure()));
        assertThrows(IllegalArgumentException.class,
                () -> new CompositionResult(false, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaybackService.StartResult(true, failure()));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaybackService.StartResult(false, null));
    }

    @Test
    void worldPoseRequiresPositiveFov() {
        assertEquals(70f, new WorldPose(Vec3.ZERO, Rotation.NONE, 70f).fov());
        assertThrows(IllegalArgumentException.class,
                () -> new WorldPose(Vec3.ZERO, Rotation.NONE, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldPose(Vec3.ZERO, Rotation.NONE, Float.NaN));
    }
}
