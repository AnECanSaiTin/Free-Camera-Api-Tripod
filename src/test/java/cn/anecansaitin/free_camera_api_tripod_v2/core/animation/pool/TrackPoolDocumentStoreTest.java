package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.TrackPoolEditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池 store：提交、过期拒绝、撤销/重做与监听隔离。
class TrackPoolDocumentStoreTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void submitPublishesAndUndoRedoPreserveContent() {
        TrackPoolAnimation initial = document();
        TrackPoolDocumentStore store = new TrackPoolDocumentStore(initial);
        AtomicInteger notifications = new AtomicInteger();
        store.addListener(snapshot -> notifications.incrementAndGet());
        QuaternionTrack track = new QuaternionTrack(new TrackId("rotation"), "rotation",
                Quaternion.IDENTITY, List.of());

        TrackPoolEditResult result = store.submit(new TrackPoolEditOp.AddTrack(track),
                Revision.INITIAL);

        assertTrue(result instanceof TrackPoolEditResult.Applied applied
                && applied.snapshot().revision().equals(new Revision(1)));
        assertEquals(1, notifications.get());

        assertTrue(store.undo());
        assertEquals(initial, store.snapshot().document());
        assertEquals(new Revision(2), store.snapshot().revision());

        assertTrue(store.redo());
        assertTrue(store.snapshot().document().track(track.id()).isPresent());
        assertEquals(new Revision(3), store.snapshot().revision());
        assertFalse(store.canUndo() && store.canRedo());
    }

    @Test
    void staleRevisionIsRejectedWithoutPublishing() {
        TrackPoolDocumentStore store = new TrackPoolDocumentStore(document());
        TrackPoolEditResult first = store.submit(new TrackPoolEditOp.SetPlaybackRange(
                new PlaybackRange.Manual(0f, 2f)), Revision.INITIAL);
        assertTrue(first instanceof TrackPoolEditResult.Applied);

        TrackPoolEditResult stale = store.submit(new TrackPoolEditOp.SetPlaybackRange(
                new PlaybackRange.Manual(0f, 3f)), Revision.INITIAL);

        assertTrue(stale instanceof TrackPoolEditResult.Rejected rejected
                && rejected.diagnostics().getFirst().code().equals(DiagnosticCode.STALE_REVISION));
        assertEquals(new Revision(1), store.snapshot().revision());
    }

    @Test
    void invalidEditDoesNotPublish() {
        TrackId number = new TrackId("number");
        CurveTrack track = CurveTrack.ofCurve(number, "number", Curve.EMPTY);
        TrackPoolAnimation bound = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(track),
                List.of(PooledRig.create(RIG, "rig").withProperties(new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Track(number)),
                                PropertyBinding.disabled(),
                                PropertyBinding.disabled()),
                        RotationProperty.disabled(),
                        FovProperty.disabled(),
                        ActiveProperty.disabled(),
                        RectProperty.fullScreen(),
                        ObstacleProperty.disabled()))),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
        TrackPoolDocumentStore store = new TrackPoolDocumentStore(bound);

        TrackPoolEditResult result = store.submit(new TrackPoolEditOp.RemoveTrack(number),
                Revision.INITIAL);

        assertTrue(result instanceof TrackPoolEditResult.Rejected);
        assertTrue(store.snapshot().document().track(number).isPresent());
        assertEquals(Revision.INITIAL, store.snapshot().revision());
    }

    @Test
    void reentrantSubmitFromListenerIsRejected() {
        TrackPoolDocumentStore store = new TrackPoolDocumentStore(document());
        java.util.concurrent.atomic.AtomicReference<TrackPoolEditResult> nested =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean nestedUndo = new java.util.concurrent.atomic.AtomicBoolean();
        store.addListener(snapshot -> {
            nested.set(store.submit(
                    new TrackPoolEditOp.SetPlaybackRange(new PlaybackRange.Manual(0f, 8f)),
                    snapshot.revision()));
            nestedUndo.set(store.undo());
        });

        assertTrue(store.submit(new TrackPoolEditOp.SetPlaybackRange(
                new PlaybackRange.Manual(0f, 4f)), Revision.INITIAL)
                instanceof TrackPoolEditResult.Applied);
        assertTrue(nested.get() instanceof TrackPoolEditResult.Rejected rejected
                && rejected.diagnostics().getFirst().code().equals(DiagnosticCode.REENTRANT_SUBMIT));
        assertTrue(!nestedUndo.get());
        assertEquals(4f, store.snapshot().document().playbackEndSeconds());
    }

    @Test
    void listenerFailureDoesNotBlockPublishOrOtherListeners() {
        TrackPoolDocumentStore store = new TrackPoolDocumentStore(document());
        AtomicInteger called = new AtomicInteger();
        store.addListener(snapshot -> {
            throw new IllegalStateException("listener failure");
        });
        store.addListener(snapshot -> called.incrementAndGet());

        TrackPoolEditResult result = store.submit(new TrackPoolEditOp.SetPlaybackRange(
                new PlaybackRange.Manual(0f, 4f)), Revision.INITIAL);

        assertTrue(result instanceof TrackPoolEditResult.Applied);
        assertEquals(1, called.get());
        assertEquals(4f, store.snapshot().document().playbackEndSeconds());
    }

    private static TrackPoolAnimation document() {
        return TrackPoolAnimation.create("doc", PooledRig.create(RIG, "rig"));
    }
}
