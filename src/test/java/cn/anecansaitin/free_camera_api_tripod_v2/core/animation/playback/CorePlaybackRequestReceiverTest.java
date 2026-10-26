package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackAction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.DiagnosticReport;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 播放协议动作到本地播放请求的接收入口（主设计 §8.3）。
class CorePlaybackRequestReceiverTest {

    private static final RigId RIG = new RigId("rig");
    private static final ResourceId RESOURCE = new ResourceId("test/animation");

    @Test
    void embeddedStartUsesTheSameDocumentInstance() {
        CameraAnimation document = document("embedded");
        FakePlaybackService playback = new FakePlaybackService();
        CorePlaybackRequestReceiver receiver = new CorePlaybackRequestReceiver(
                new FakeResources(null), playback);
        Map<RigId, RigPlaybackOverrides> overrides = Map.of(
                RIG, RigPlaybackOverrides.INHERIT);

        CorePlaybackRequestReceiver.Result result = receiver.receive(
                new PlaybackAction.Start(
                        new PlaybackSource.Embedded(document), overrides,
                        1.5f, true, 2f),
                new Revision(7));

        assertTrue(result instanceof CorePlaybackRequestReceiver.Result.Accepted);
        assertEquals(1, playback.starts);
        assertSame(document, playback.request.animation());
        assertEquals(overrides, playback.request.rigOverrides());
        assertEquals(1.5f, playback.request.speed());
        assertTrue(playback.request.loop());
        assertEquals(2f, playback.request.startTime());
        assertEquals(new Revision(7), playback.revision);
    }

    @Test
    void localStartResolvesThroughResourceService() {
        CameraAnimation document = document("local");
        AnimationResourceEntry entry = new AnimationResourceEntry(RESOURCE, 1L, "hash", 0L);
        FakeResources resources = new FakeResources(new LoadResult.Loaded(document, entry));
        FakePlaybackService playback = new FakePlaybackService();
        CorePlaybackRequestReceiver receiver = new CorePlaybackRequestReceiver(resources, playback);

        CorePlaybackRequestReceiver.Result result = receiver.receive(
                new PlaybackAction.Start(new PlaybackSource.Local(RESOURCE),
                        Map.of(), 1f, false, 0f),
                new Revision(1));

        assertTrue(result instanceof CorePlaybackRequestReceiver.Result.Accepted);
        assertEquals(RESOURCE, resources.loaded);
        assertSame(document, playback.request.animation());
    }

    @Test
    void localLoadFailureIsReportedWithoutStarting() {
        ResourceId resource = new ResourceId("test/missing");
        Diagnostic diagnostic = new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("missing"),
                new DiagnosticScope.File(resource), null, null);
        FakeResources resources = new FakeResources(new LoadResult.Rejected(
                new DiagnosticReport(resource, List.of(diagnostic))));
        FakePlaybackService playback = new FakePlaybackService();
        CorePlaybackRequestReceiver receiver = new CorePlaybackRequestReceiver(resources, playback);

        CorePlaybackRequestReceiver.Result result = receiver.receive(
                new PlaybackAction.Start(new PlaybackSource.Local(resource),
                        Map.of(), 1f, false, 0f),
                new Revision(1));

        CorePlaybackRequestReceiver.Result.Rejected rejected =
                (CorePlaybackRequestReceiver.Result.Rejected) result;
        assertEquals(PlaybackFailure.Reason.HOST_CAPABILITY_MISSING, rejected.failure().reason());
        assertEquals(1, rejected.failure().diagnostics().size());
        assertEquals(0, playback.starts);
    }

    @Test
    void stopDelegatesToPlaybackService() {
        FakePlaybackService playback = new FakePlaybackService();
        CorePlaybackRequestReceiver receiver = new CorePlaybackRequestReceiver(
                new FakeResources(null), playback);

        CorePlaybackRequestReceiver.Result result = receiver.receive(
                new PlaybackAction.Stop(), null);

        assertTrue(result instanceof CorePlaybackRequestReceiver.Result.Accepted);
        assertTrue(playback.stopped);
        assertEquals(0, playback.starts);
    }

    @Test
    void startFailureAndMissingRevisionAreReported() {
        CameraAnimation document = document("rejected");
        PlaybackFailure failure = new PlaybackFailure(
                PlaybackFailure.Reason.EVALUATION_ERROR,
                LocalizedMessage.literal("rejected"), 0f, List.of());
        FakePlaybackService playback = new FakePlaybackService();
        playback.result = new PlaybackService.StartResult(false, failure);
        CorePlaybackRequestReceiver receiver = new CorePlaybackRequestReceiver(
                new FakeResources(null), playback);

        CorePlaybackRequestReceiver.Result rejected = receiver.receive(
                new PlaybackAction.Start(new PlaybackSource.Embedded(document),
                        Map.of(), 1f, false, 0f),
                new Revision(1));

        assertSame(failure, ((CorePlaybackRequestReceiver.Result.Rejected) rejected).failure());

        FakePlaybackService noStart = new FakePlaybackService();
        CorePlaybackRequestReceiver receiverNoRevision = new CorePlaybackRequestReceiver(
                new FakeResources(null), noStart);

        CorePlaybackRequestReceiver.Result missingRevision = receiverNoRevision.receive(
                new PlaybackAction.Start(new PlaybackSource.Embedded(document),
                        Map.of(), 1f, false, 0f),
                null);

        assertTrue(missingRevision instanceof CorePlaybackRequestReceiver.Result.Rejected);
        assertEquals(0, noStart.starts);
    }

    private static CameraAnimation document(String name) {
        CameraRig rig = CameraRig.create(RIG, "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        return CameraAnimation.create(name, rig, new TrackId("source"));
    }

    private static final class FakePlaybackService implements PlaybackService {
        private @Nullable PlaybackRequest request;
        private @Nullable Revision revision;
        private StartResult result = new StartResult(true, null);
        private boolean stopped;
        private int starts;

        @Override
        public PlaybackState state() {
            return PlaybackState.IDLE;
        }

        @Override
        public PlaybackClock clock() {
            return new PlaybackClock(0f, 1f, false, 0f);
        }

        @Override
        public @Nullable PlaybackFailure failure() {
            return null;
        }

        @Override
        public StartResult start(PlaybackRequest request, Revision expectedRevision) {
            this.request = request;
            this.revision = expectedRevision;
            this.starts++;
            return result;
        }

        @Override
        public void pause() {
        }

        @Override
        public void resume() {
        }

        @Override
        public void seek(float time) {
        }

        @Override
        public void stopAndReset() {
            stopped = true;
        }

        @Override
        public void halt(PlaybackFailure failure) {
        }

        @Override
        public @Nullable PreparedFrame prepareFrame(long frameIndex,
                                                    float deltaSeconds,
                                                    EventBudget eventBudget) {
            return null;
        }
    }

    private static final class FakeResources implements AnimationResourceService {
        private final @Nullable LoadResult result;
        private @Nullable ResourceId loaded;

        private FakeResources(@Nullable LoadResult result) {
            this.result = result;
        }

        @Override
        public LoadResult load(ResourceId resource) {
            loaded = resource;
            return result == null ? new LoadResult.Rejected(new DiagnosticReport(
                    resource, List.of(new Diagnostic(
                    DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                    LocalizedMessage.literal("no resource"),
                    new DiagnosticScope.File(resource), null, null))))
                    : result;
        }

        @Override
        public SaveResult save(ResourceId resource, CameraAnimation document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveAsync(ResourceId resource,
                              CameraAnimation document,
                              Consumer<SaveResult> completion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AnimationResourceEntry> entry(ResourceId resource) {
            return Optional.empty();
        }

        @Override
        public List<AnimationResourceEntry> entries() {
            return List.of();
        }
    }
}
