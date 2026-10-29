package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 快照轨道索引的绑定、所属机位与重复 ID 行为。
class SnapshotTrackIndexTest {

    @Test
    void indexesAllTrackPositionsWithOwnerRig() {
        CurveTrack channel = CurveTrack.ofCurve(new TrackId("channel"), "channel", Curve.EMPTY);
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(channel),
                new TrackId("aim"), new TrackId("active"));
        TestExtensionTrack extension = new TestExtensionTrack(new TrackId("ext"), "ext");
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withTracks(List.of(extension));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(3), document);

        SnapshotTrackIndex index = SnapshotTrackIndex.build(snapshot);

        assertSame(snapshot, index.snapshot());
        assertTrue(index.diagnostics().isEmpty());
        assertEquals(rig.id(), index.find(channel.id()).orElseThrow().ownerRigId());
        assertEquals(rig.id(), index.find(new TrackId("aim")).orElseThrow().ownerRigId());
        assertEquals(rig.id(), index.find(new TrackId("active")).orElseThrow().ownerRigId());
        assertEquals(null, index.find(new TrackId("source")).orElseThrow().ownerRigId());
        assertEquals(null, index.find(extension.id()).orElseThrow().ownerRigId());
        assertTrue(index.find(new TrackId("missing")).isEmpty());
    }

    @Test
    void duplicateIdIsReportedAndNotAddressable() {
        CurveTrack first = CurveTrack.ofCurve(new TrackId("dup"), "first", Curve.EMPTY);
        CurveTrack second = CurveTrack.ofCurve(new TrackId("dup"), "second", Curve.EMPTY);
        CameraRig a = CameraRig.create(new RigId("a"), "a", List.of(first),
                new TrackId("a-aim"), new TrackId("a-active"));
        CameraRig b = CameraRig.create(new RigId("b"), "b", List.of(second),
                new TrackId("b-aim"), new TrackId("b-active"));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(4),
                CameraAnimation.create("doc", a, new TrackId("source")).withRigs(List.of(a, b)));

        SnapshotTrackIndex index = SnapshotTrackIndex.build(snapshot);

        assertTrue(index.find(new TrackId("dup")).isEmpty());
        assertEquals(1, index.diagnostics().size());
        assertEquals(new Revision(4),
                ((cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope.Document)
                        index.diagnostics().getFirst().scope()).revision());
    }

    @Test
    void thirdOccurrenceDoesNotReintroduceDuplicate() {
        CurveTrack first = CurveTrack.ofCurve(new TrackId("dup"), "first", Curve.EMPTY);
        CurveTrack second = CurveTrack.ofCurve(new TrackId("dup"), "second", Curve.EMPTY);
        CurveTrack third = CurveTrack.ofCurve(new TrackId("dup"), "third", Curve.EMPTY);
        CameraRig a = CameraRig.create(new RigId("a"), "a", List.of(first),
                new TrackId("a-aim"), new TrackId("a-active"));
        CameraRig b = CameraRig.create(new RigId("b"), "b", List.of(second),
                new TrackId("b-aim"), new TrackId("b-active"));
        CameraRig c = CameraRig.create(new RigId("c"), "c", List.of(third),
                new TrackId("c-aim"), new TrackId("c-active"));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(5),
                CameraAnimation.create("doc", a, new TrackId("source"))
                        .withRigs(List.of(a, b, c)));

        SnapshotTrackIndex index = SnapshotTrackIndex.build(snapshot);

        assertFalse(index.find(new TrackId("dup")).isPresent());
        assertEquals(2, index.diagnostics().size());
    }

    private record TestExtensionTrack(TrackId id, String name) implements AnimationTrack.ExtensionTrack {

        public TestExtensionTrack withName(String newName) {
            return new TestExtensionTrack(id, newName);
        }

        @Override
        public net.minecraft.resources.Identifier type() {
            return net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "extension");
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
