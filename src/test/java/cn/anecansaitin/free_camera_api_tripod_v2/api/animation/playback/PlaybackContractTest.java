package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
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
        return new FrameFacts(DIMENSION, 1L, 0f, 1L, Map.of(), Map.of(), true);
    }

    private static Diagnostic errorDiagnostic() {
        return new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("broken"), new DiagnosticScope.File("test-resource"),
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
                CameraSource.player(),
                List.of(),
                events,
                facts(),
                diagnostics);

        events.clear();
        diagnostics.clear();

        assertEquals(1, frame.events().size());
        assertEquals(1, frame.diagnostics().size());
        assertFalse(frame.isExecutable());
    }

    @Test
    void eventBudgetAndRenderStatusValidateBounds() {
        assertThrows(IllegalArgumentException.class, () -> new EventBudget(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new EventBudget(1, 0));

        assertThrows(IllegalArgumentException.class, () -> new RigRenderStatus(
                new RigId("rig"), true, 0, 100, null, null));

        RigRenderStatus stopped = new RigRenderStatus(
                new RigId("rig"), false, 0, 0, DegradeReason.RIG_PAUSED, null);
        assertFalse(stopped.rendered());
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
