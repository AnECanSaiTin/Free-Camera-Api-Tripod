package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
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
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document.CoreAnimationDocumentStore;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 播放生命周期、时钟推进、失败终止与线程约束。
class CorePlaybackServiceTest {

    private static final EventBudget BUDGET = new EventBudget(10, 10);
    private static final Identifier DIMENSION =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void startsPausesResumesAndSeeks() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService service = new CorePlaybackService(store, frameFactory(document));

        PlaybackService.StartResult start = service.start(request(document, 2f), Revision.INITIAL);

        assertTrue(start.accepted());
        assertEquals(PlaybackState.PLAYING, service.state());
        service.pause();
        assertEquals(PlaybackState.PAUSED, service.state());
        service.resume();
        assertEquals(PlaybackState.PLAYING, service.state());
        service.seek(4f);
        assertEquals(4f, service.clock().time(), 1e-5f);
        PreparedFrame frame = service.prepareFrame(0L, 0f, BUDGET);
        assertEquals(4f, frame.clock().time(), 1e-5f);
    }

    @Test
    void staleRevisionDoesNotStart() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService service = new CorePlaybackService(store, frameFactory(document));

        PlaybackService.StartResult start = service.start(
                request(document, 0f), new Revision(9));

        assertFalse(start.accepted());
        assertEquals(PlaybackState.IDLE, service.state());
        assertEquals(PlaybackFailure.Reason.EVALUATION_ERROR, start.failure().reason());
    }

    @Test
    void invalidStartTimeDoesNotStart() {
        CameraAnimation document = document("doc", 2f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService service = new CorePlaybackService(store, frameFactory(document));

        PlaybackService.StartResult start = service.start(request(document, 3f), Revision.INITIAL);

        assertFalse(start.accepted());
        assertEquals(PlaybackState.IDLE, service.state());
    }

    @Test
    void invalidOverrideRigRejectsStartWithoutSwitchingDocument() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService service = new CorePlaybackService(store, frameFactory(document));
        PlaybackRequest request = new PlaybackRequest(
                document, Map.of(new RigId("missing"),
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides.INHERIT),
                1f, false, 0f);

        PlaybackService.StartResult start = service.start(request, Revision.INITIAL);

        assertFalse(start.accepted());
        assertEquals(PlaybackState.IDLE, service.state());
        assertTrue(start.failure().diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().toString().contains("missing rig")));
    }

    @Test
    void prepareFrameAdvancesClockAndPassesBudget() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        AtomicReference<PlaybackClock> seenClock = new AtomicReference<>();
        AtomicReference<EventBudget> seenBudget = new AtomicReference<>();
        CorePlaybackService service = new CorePlaybackService(store, (snapshot, request, clock, fromTime, index, budget) -> {
            seenClock.set(clock);
            seenBudget.set(budget);
            return frame(clock, snapshot, List.of());
        });

        service.start(request(document, 0f), Revision.INITIAL);
        PreparedFrame frame = service.prepareFrame(7L, 2f, BUDGET);

        assertEquals(2f, frame.clock().time(), 1e-5f);
        assertEquals(2f, seenClock.get().time(), 1e-5f);
        assertEquals(BUDGET, seenBudget.get());
    }

    @Test
    void errorDiagnosticHaltsAndReturnsNoFrame() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        Diagnostic error = new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                LocalizedMessage.literal("bad"), new DiagnosticScope.Document(Revision.INITIAL),
                null, null);
        CorePlaybackService service = new CorePlaybackService(store, (snapshot, request, clock, fromTime, index, budget) ->
                frame(clock, snapshot, List.of(error)));

        service.start(request(document, 0f), Revision.INITIAL);

        assertNull(service.prepareFrame(0L, 0f, BUDGET));
        assertEquals(PlaybackState.HALTED, service.state());
        assertEquals(PlaybackFailure.Reason.EVALUATION_ERROR, service.failure().reason());
    }

    @Test
    void factoryExceptionHalts() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService service = new CorePlaybackService(store, (snapshot, request, clock, fromTime, index, budget) -> {
            throw new IllegalStateException("boom");
        });

        service.start(request(document, 0f), Revision.INITIAL);

        assertNull(service.prepareFrame(0L, 0f, BUDGET));
        assertEquals(PlaybackState.HALTED, service.state());
        assertTrue(service.failure().message().toString().contains("boom"));
    }

    @Test
    void stopResetsStateAndClock() {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService service = new CorePlaybackService(store, frameFactory(document));

        service.start(request(document, 4f), Revision.INITIAL);
        service.stopAndReset();

        assertEquals(PlaybackState.IDLE, service.state());
        assertNull(service.failure());
        assertNull(service.prepareFrame(0L, 1f, BUDGET));
    }

    @Test
    void wrongThreadIsRejected() throws InterruptedException {
        CameraAnimation document = document("doc", 10f);
        CoreAnimationDocumentStore store = store(document);
        CorePlaybackService service = new CorePlaybackService(store, frameFactory(document));
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        Thread thread = new Thread(() -> {
            try {
                service.state();
            } catch (Throwable error) {
                thrown.set(error);
            }
        });
        thread.start();
        thread.join();

        assertTrue(thrown.get() instanceof IllegalStateException,
                () -> "unexpected: " + thrown.get());
    }

    private static PlaybackFrameFactory frameFactory(CameraAnimation document) {
        return (snapshot, request, clock, fromTime, index, budget) -> frame(clock, snapshot, List.of());
    }

    private static PreparedFrame frame(PlaybackClock clock, AnimationSnapshot snapshot, List<Diagnostic> diagnostics) {
        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 1L, 0L, 0.5f, 1L,
                Map.of(), Map.of(), true);
        return new PreparedFrame(clock, snapshot, CameraSource.player(), List.of(), List.of(), facts, diagnostics);
    }

    private static CoreAnimationDocumentStore store(CameraAnimation document) {
        return CoreAnimationDocumentStore.withFixedValidator(document, candidate -> List.of());
    }

    private static PlaybackRequest request(CameraAnimation document, float startTime) {
        return new PlaybackRequest(document, Map.of(), 1f, false, startTime);
    }

    private static CameraAnimation document(String name, float duration) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create(name, rig, new TrackId("source"));
        return new CameraAnimation(
                CameraAnimation.CURRENT_DATA_VERSION,
                document.name(),
                document.rigs(),
                document.cameraSource(),
                document.tracks(),
                document.symbols(),
                List.of(new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker(
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId("end"),
                        duration, "", cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MarkerKind.GENERIC)),
                document.samples(),
                document.audioRefs(),
                document.overlays());
    }
}
