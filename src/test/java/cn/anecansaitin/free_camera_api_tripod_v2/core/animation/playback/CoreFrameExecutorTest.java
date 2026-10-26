package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime;
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
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 帧执行：相机先于事件、失败停止、部分事件计数与非法帧拒绝。
class CoreFrameExecutorTest {

    private static final EventBudget BUDGET = new EventBudget(10, 10);
    private static final Identifier DIMENSION =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void appliesCameraBeforeDispatchingEvents() {
        List<String> order = new ArrayList<>();
        CoreFrameExecutor executor = new CoreFrameExecutor((frame, budget) -> {
            order.add("events");
            return new EventDispatcher.Result(2, null);
        });
        PreparedFrame frame = frame(List.of());

        CoreFrameExecutor.Result result = executor.execute(frame, BUDGET, (source, rigs) -> order.add("camera"));

        assertTrue(result.presented());
        assertEquals(2, result.dispatchedEvents());
        assertEquals(List.of("camera", "events"), order);
    }

    @Test
    void invalidFrameDoesNotApplyCameraOrEvents() {
        AtomicBoolean camera = new AtomicBoolean();
        AtomicBoolean events = new AtomicBoolean();
        CoreFrameExecutor executor = new CoreFrameExecutor((frame, budget) -> {
            events.set(true);
            return new EventDispatcher.Result(0, null);
        });
        PreparedFrame frame = frame(List.of(error()));

        CoreFrameExecutor.Result result = executor.execute(frame, BUDGET, (source, rigs) -> camera.set(true));

        assertFalse(result.presented());
        assertEquals(PlaybackFailure.Reason.EVALUATION_ERROR, result.failure().reason());
        assertFalse(camera.get());
        assertFalse(events.get());
    }

    @Test
    void cameraFailureStopsEventDispatch() {
        AtomicBoolean events = new AtomicBoolean();
        CoreFrameExecutor executor = new CoreFrameExecutor((frame, budget) -> {
            events.set(true);
            return new EventDispatcher.Result(0, null);
        });

        CoreFrameExecutor.Result result = executor.execute(frame(List.of()), BUDGET, (source, rigs) -> {
            throw new IllegalStateException("camera");
        });

        assertFalse(result.presented());
        assertEquals(PlaybackFailure.Reason.RENDER_FAILURE, result.failure().reason());
        assertTrue(result.failure().message().toString().contains("camera"));
        assertFalse(events.get());
    }

    @Test
    void eventFailureKeepsPartialCount() {
        EventDispatcher.Result failure = new EventDispatcher.Result(1,
                new PlaybackFailure(PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED,
                        LocalizedMessage.literal("too many"), 1f, List.of()));
        CoreFrameExecutor executor = new CoreFrameExecutor((frame, budget) -> failure);

        CoreFrameExecutor.Result result = executor.execute(frame(List.of()), BUDGET, (source, rigs) -> {
        });

        assertFalse(result.presented());
        assertEquals(1, result.dispatchedEvents());
        assertEquals(PlaybackFailure.Reason.EVENT_BUDGET_EXCEEDED, result.failure().reason());
    }

    private static PreparedFrame frame(List<Diagnostic> diagnostics) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 1L, 0L, 0f,
                new WorldTime(0, 24000), 0L, Map.of(), Map.of(), true);
        return new PreparedFrame(new PlaybackClock(1f, 1f, false, 10f), snapshot,
                CameraSource.player(), List.of(), List.of(), facts, diagnostics);
    }

    private static Diagnostic error() {
        return new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                LocalizedMessage.literal("bad"), new DiagnosticScope.Document(Revision.INITIAL),
                null, null);
    }
}
