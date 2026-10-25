package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
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
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventDispatcher;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventExecutor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventExecutorRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 事件切分、预算、缺失执行器与执行器异常的部分执行。
class CoreEventDispatcherTest {

    private static final Identifier TYPE_A = Identifier.fromNamespaceAndPath("test", "a");
    private static final Identifier TYPE_B = Identifier.fromNamespaceAndPath("test", "b");
    private static final TrackId A = new TrackId("a");
    private static final TrackId B = new TrackId("b");

    @Test
    void dispatchesConsecutiveSameTypeGroupsInOriginalOrder() {
        List<Integer> groupSizesA = new ArrayList<>();
        List<Integer> groupSizesB = new ArrayList<>();
        CoreEventDispatcher dispatcher = dispatcher(
                executor(groupSizesA), executor(groupSizesB));
        PreparedFrame frame = frame(
                event(A, 1f), event(A, 2f), event(B, 3f), event(A, 4f));

        EventDispatcher.Result result = dispatcher.dispatch(frame, new EventBudget(10, 10));

        assertTrue(result.succeeded());
        assertEquals(4, result.dispatched());
        assertEquals(List.of(2, 1), groupSizesA);
        assertEquals(List.of(1), groupSizesB);
    }

    @Test
    void exceedsEventBudgetBeforeCallingExecutor() {
        List<Integer> groups = new ArrayList<>();
        CoreEventDispatcher dispatcher = dispatcher(executor(groups), executor(new ArrayList<>()));
        PreparedFrame frame = frame(event(A, 1f), event(A, 2f), event(A, 3f));

        EventDispatcher.Result result = dispatcher.dispatch(frame, new EventBudget(2, 10));

        assertFalse(result.succeeded());
        assertEquals(PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED, result.failure().reason());
        assertEquals(0, result.dispatched());
        assertTrue(groups.isEmpty());
    }

    @Test
    void missingExecutorTerminatesDispatch() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher(new EventExecutorRegistry(List.of()));
        PreparedFrame frame = frame(event(A, 1f));

        EventDispatcher.Result result = dispatcher.dispatch(frame, new EventBudget(10, 10));

        assertFalse(result.succeeded());
        assertEquals(PlaybackFailure.Reason.HOST_CAPABILITY_MISSING, result.failure().reason());
        assertEquals(0, result.dispatched());
    }

    @Test
    void executorExceptionReportsPartialExecution() {
        EventExecutor failing = (frame, events) -> {
            throw new IllegalStateException("boom");
        };
        CoreEventDispatcher dispatcher = new CoreEventDispatcher(new EventExecutorRegistry(List.of(
                new EventExecutorRegistry.Entry(TYPE_A, failing))));
        PreparedFrame frame = frame(event(A, 1f));

        EventDispatcher.Result result = dispatcher.dispatch(frame, new EventBudget(10, 10));

        assertFalse(result.succeeded());
        assertEquals(PlaybackFailure.Reason.EVALUATION_ERROR, result.failure().reason());
        assertEquals(0, result.dispatched());
    }

    @Test
    void errorDiagnosticPreventsDispatch() {
        List<Integer> groups = new ArrayList<>();
        CoreEventDispatcher dispatcher = dispatcher(executor(groups), executor(new ArrayList<>()));
        PreparedFrame frame = frame(
                List.of(new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                        LocalizedMessage.literal("bad"), new DiagnosticScope.Document(Revision.INITIAL),
                        null, null)),
                event(A, 1f));

        EventDispatcher.Result result = dispatcher.dispatch(frame, new EventBudget(10, 10));

        assertFalse(result.succeeded());
        assertEquals(PlaybackFailure.Reason.EVALUATION_ERROR, result.failure().reason());
        assertTrue(groups.isEmpty());
    }

    private static CoreEventDispatcher dispatcher(EventExecutor a, EventExecutor b) {
        return new CoreEventDispatcher(new EventExecutorRegistry(List.of(
                new EventExecutorRegistry.Entry(TYPE_A, a),
                new EventExecutorRegistry.Entry(TYPE_B, b))));
    }

    private static EventExecutor executor(List<Integer> groupSizes) {
        return (frame, events) -> {
            groupSizes.add(events.size());
            return new EventDispatcher.Result(events.size(), null);
        };
    }

    private static PreparedFrame frame(PreparedFrame.Event... events) {
        return frame(List.of(), events);
    }

    private static PreparedFrame frame(List<Diagnostic> diagnostics, PreparedFrame.Event... events) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withTracks(List.of(
                        new TestTrack(A, TYPE_A, "a"),
                        new TestTrack(B, TYPE_B, "b")));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(2), document);
        FrameFacts facts = new FrameFacts(snapshot,
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                7L, 10L, 0.5f, 1L, Map.of(), Map.of(), true);

        return new PreparedFrame(
                new PlaybackClock(3f, 1f, false, 10f),
                snapshot,
                CameraSource.player(),
                List.of(),
                List.of(events),
                facts,
                diagnostics);
    }

    private static PreparedFrame.Event event(TrackId trackId, float time) {
        return new PreparedFrame.Event(trackId, time);
    }

    private record TestTrack(TrackId id, Identifier type, String name)
            implements AnimationTrack.ExtensionTrack {

        @Override
        public TestTrack withName(String newName) {
            return new TestTrack(id, type, newName);
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }
    }
}
