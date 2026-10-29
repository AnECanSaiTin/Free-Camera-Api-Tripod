package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.TrackPoolEditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池编辑：增删改、能力检查与引用删除拒绝。
class TrackPoolEditApplierTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void addsRenamesAndRemovesUnreferencedTrack() {
        TrackPoolEditApplier applier = new TrackPoolEditApplier();
        TrackPoolAnimation document = document(List.of(), false);
        QuaternionTrack track = new QuaternionTrack(new TrackId("rotation"), "rotation",
                Quaternion.IDENTITY, List.of());

        TrackPoolAnimation added = applier.apply(document,
                new TrackPoolEditOp.AddTrack(track), Revision.INITIAL);
        assertTrue(added.track(track.id()).isPresent());

        TrackPoolAnimation renamed = applier.apply(added,
                new TrackPoolEditOp.RenameTrack(track.id(), "after"), Revision.INITIAL);
        assertEquals("after", renamed.track(track.id()).orElseThrow().name());

        TrackPoolAnimation removed = applier.apply(renamed,
                new TrackPoolEditOp.RemoveTrack(track.id()), Revision.INITIAL);
        assertTrue(removed.track(track.id()).isEmpty());
    }

    @Test
    void removingBoundTrackIsRejected() {
        TrackId number = new TrackId("number");
        CurveTrack track = CurveTrack.ofCurve(number, "number", Curve.EMPTY);
        TrackPoolAnimation document = document(List.of(track), true);

        assertThrows(IllegalArgumentException.class, () -> new TrackPoolEditApplier().apply(
                document, new TrackPoolEditOp.RemoveTrack(number), Revision.INITIAL));
    }

    @Test
    void renameRequiresCapability() {
        TrackId fixed = new TrackId("fixed");
        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack track =
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack.ExtensionTrack() {
                    @Override
                    public TrackId id() {
                        return fixed;
                    }

                    @Override
                    public String name() {
                        return "fixed";
                    }

                    @Override
                    public net.minecraft.resources.Identifier type() {
                        return net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "fixed");
                    }

                    @Override
                    public float duration() {
                        return 0f;
                    }

                    @Override
                    public int keyCount() {
                        return 0;
                    }
                };
        TrackPoolAnimation document = document(List.of(track), false);

        assertThrows(IllegalArgumentException.class, () -> new TrackPoolEditApplier().apply(
                document, new TrackPoolEditOp.RenameTrack(fixed, "after"), Revision.INITIAL));
    }

    @Test
    void rigsCanBeAddedRenamedAndRemovedButLastRigIsKept() {
        TrackPoolEditApplier applier = new TrackPoolEditApplier();
        TrackPoolAnimation document = document(List.of(), false);
        RigId second = new RigId("second");

        TrackPoolAnimation added = applier.apply(document,
                new TrackPoolEditOp.AddRig(PooledRig.create(second, "second")), Revision.INITIAL);
        assertEquals(2, added.rigs().size());

        TrackPoolAnimation renamed = applier.apply(added,
                new TrackPoolEditOp.RenameRig(second, "renamed"), Revision.INITIAL);
        assertEquals("renamed", renamed.rig(second).orElseThrow().name());

        TrackPoolAnimation removed = applier.apply(renamed,
                new TrackPoolEditOp.RemoveRig(second), Revision.INITIAL);
        assertEquals(1, removed.rigs().size());
        assertThrows(IllegalArgumentException.class, () -> applier.apply(removed,
                new TrackPoolEditOp.RemoveRig(RIG), Revision.INITIAL));
    }

    @Test
    void outputAndPlaybackRangeAreEditable() {
        TrackPoolEditApplier applier = new TrackPoolEditApplier();
        TrackPoolAnimation document = document(List.of(), false);

        TrackPoolAnimation renamed = applier.apply(document, new TrackPoolEditOp.SetRigOutput(
                RIG, new RigOutput(true, 2, 0.5, 3)), Revision.INITIAL);
        assertEquals(2, renamed.rig(RIG).orElseThrow().output().zOrder());
        assertTrue(renamed.rig(RIG).orElseThrow().output().enabled());

        TrackPoolAnimation ranged = applier.apply(renamed, new TrackPoolEditOp.SetPlaybackRange(
                new PlaybackRange.Manual(1f, 6f)), Revision.INITIAL);
        assertEquals(5f, ranged.playbackDurationSeconds());
    }

    private static TrackPoolAnimation document(List<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack> tracks,
                                               boolean bindNumber) {
        RigProperties properties = bindNumber
                ? new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Track(new TrackId("number"))),
                                PropertyBinding.disabled(),
                                PropertyBinding.disabled()),
                        RotationProperty.disabled(),
                        FovProperty.disabled(),
                        ActiveProperty.disabled(),
                        RectProperty.fullScreen(),
                        ObstacleProperty.disabled())
                : RigProperties.disabled();
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", tracks,
                List.of(PooledRig.create(RIG, "rig").withProperties(properties)),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
    }
}
