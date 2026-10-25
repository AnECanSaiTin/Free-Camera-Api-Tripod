package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsPlan;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 帧事实计划：时间求值、覆盖三态、局部锚点与目标需求。
class FrameFactsPlanBuilderTest {

    private static final RigId RIG = new RigId("rig");
    private static final TestAnchor DOCUMENT_ANCHOR = new TestAnchor("document");
    private static final TestAnchor OVERRIDE_ANCHOR = new TestAnchor("override");
    private static final AimTarget DOCUMENT_TARGET = new AimTarget(
            new TestAnchor("target"), RotationMask.YAW_PITCH, Vec3.ZERO);
    private static final AimTarget REPLACE_TARGET = new AimTarget(
            new TestAnchor("replace"), RotationMask.YAW_PITCH, Vec3.ZERO);

    @Test
    void worldRigWithOffAimNeedsNothing() {
        FrameFactsPlan plan = FrameFactsPlanBuilder.build(
                snapshot(worldRig(AimTrack.off(new TrackId("aim")))), 0f, Map.of(), Set.of(RIG));

        FrameFactsPlan.RigDemand demand = plan.demand(RIG).orElseThrow();
        assertTrue(demand.anchor() instanceof FrameFactsPlan.SpatialDemand.NotRequired<?>);
        assertTrue(demand.target() instanceof FrameFactsPlan.SpatialDemand.NotRequired<?>);
        assertEquals(AimState.off(), demand.aim());
    }

    @Test
    void localRigAndTimedTargetProduceBothDemands() {
        AimTrack aim = aimingTrack();
        FrameFactsPlan plan = FrameFactsPlanBuilder.build(
                snapshot(localRig(aim)), 5f, Map.of(), Set.of(RIG));

        FrameFactsPlan.RigDemand demand = plan.demand(RIG).orElseThrow();
        assertEquals(DOCUMENT_ANCHOR,
                ((FrameFactsPlan.SpatialDemand.Required<Anchor>) demand.anchor()).value());
        assertEquals(DOCUMENT_TARGET,
                ((FrameFactsPlan.SpatialDemand.Required<AimTarget>) demand.target()).value());
        assertEquals(AimState.target(DOCUMENT_TARGET), demand.aim());
    }

    @Test
    void anchorOverrideReplacesDocumentDefault() {
        RigPlaybackOverrides overrides = new RigPlaybackOverrides(
                OVERRIDE_ANCHOR, null, AimOverride.inherit(), null);
        FrameFactsPlan plan = FrameFactsPlanBuilder.build(
                snapshot(localRig(aimingTrack())), 5f, Map.of(RIG, overrides), Set.of(RIG));

        FrameFactsPlan.RigDemand demand = plan.demand(RIG).orElseThrow();
        assertEquals(OVERRIDE_ANCHOR,
                ((FrameFactsPlan.SpatialDemand.Required<Anchor>) demand.anchor()).value());
    }

    @Test
    void disabledAimDropsTargetDemand() {
        RigPlaybackOverrides overrides = new RigPlaybackOverrides(
                null, null, AimOverride.disabled(), null);
        FrameFactsPlan plan = FrameFactsPlanBuilder.build(
                snapshot(localRig(aimingTrack())), 5f, Map.of(RIG, overrides), Set.of(RIG));

        FrameFactsPlan.RigDemand demand = plan.demand(RIG).orElseThrow();
        assertEquals(AimState.off(), demand.aim());
        assertTrue(demand.target() instanceof FrameFactsPlan.SpatialDemand.NotRequired<?>);
    }

    @Test
    void replaceAimRequiresTheReplacementTarget() {
        RigPlaybackOverrides overrides = new RigPlaybackOverrides(
                null, null, AimOverride.replace(REPLACE_TARGET), null);
        FrameFactsPlan plan = FrameFactsPlanBuilder.build(
                snapshot(worldRig(aimingTrack())), 5f, Map.of(RIG, overrides), Set.of(RIG));

        FrameFactsPlan.RigDemand demand = plan.demand(RIG).orElseThrow();
        assertEquals(AimState.target(REPLACE_TARGET), demand.aim());
        assertEquals(REPLACE_TARGET,
                ((FrameFactsPlan.SpatialDemand.Required<AimTarget>) demand.target()).value());
    }

    @Test
    void replaceAimWithRollMaskIsRejected() {
        AimTarget rollTarget = new AimTarget(new TestAnchor("roll"), RotationMask.ALL, Vec3.ZERO);
        RigPlaybackOverrides overrides = new RigPlaybackOverrides(
                null, null, AimOverride.replace(rollTarget), null);

        assertThrows(IllegalArgumentException.class, () -> FrameFactsPlanBuilder.build(
                snapshot(worldRig(AimTrack.off(new TrackId("aim")))),
                0f, Map.of(RIG, overrides), Set.of(RIG)));
    }

    @Test
    void localRigWithoutAnyAnchorIsRejected() {
        CameraRig rig = CameraRig.create(RIG, "rig", List.of(),
                        new TrackId("aim"), new TrackId("active"))
                .withSpace(new Space.Local(null, RotationMask.NONE));

        assertThrows(IllegalArgumentException.class, () -> FrameFactsPlanBuilder.build(
                snapshot(rig), 0f, Map.of(), Set.of(RIG)));
    }

    @Test
    void unknownRigInOverridesOrPlanIsRejected() {
        AnimationSnapshot snapshot = snapshot(worldRig(AimTrack.off(new TrackId("aim"))));
        RigPlaybackOverrides overrides = RigPlaybackOverrides.INHERIT;

        assertThrows(IllegalArgumentException.class, () -> FrameFactsPlanBuilder.build(
                snapshot, 0f, Map.of(new RigId("missing"), overrides), Set.of(RIG)));
        assertThrows(IllegalArgumentException.class, () -> FrameFactsPlanBuilder.build(
                snapshot, 0f, Map.of(), Set.of(new RigId("missing"))));
    }

    @Test
    void emptyRigSetReturnsEmptyPlan() {
        FrameFactsPlan plan = FrameFactsPlanBuilder.build(
                snapshot(worldRig(AimTrack.off(new TrackId("aim")))), 0f, Map.of(), Set.of());

        assertTrue(plan.rigs().isEmpty());
    }

    private static CameraRig worldRig(AimTrack aim) {
        return CameraRig.create(RIG, "rig", List.of(), aim.id(), new TrackId("active"))
                .withAim(aim);
    }

    private static CameraRig localRig(AimTrack aim) {
        return worldRig(aim).withSpace(new Space.Local(DOCUMENT_ANCHOR, RotationMask.NONE));
    }

    private static AimTrack aimingTrack() {
        return AimTrack.of(new TrackId("aim"), "aim", AimState.off(), List.of(
                ValueKeyframe.at(new KeyId("target-key"), 2f, AimState.target(DOCUMENT_TARGET))));
    }

    private static AnimationSnapshot snapshot(CameraRig rig) {
        return new AnimationSnapshot(new Revision(1),
                CameraAnimation.create("doc", rig, new TrackId("source")));
    }

    private record TestAnchor(String name) implements Anchor {
        @Override
        public Identifier type() {
            return Identifier.fromNamespaceAndPath("test", name);
        }
    }
}
