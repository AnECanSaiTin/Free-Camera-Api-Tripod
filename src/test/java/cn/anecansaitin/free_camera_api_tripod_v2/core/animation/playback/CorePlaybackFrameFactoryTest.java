package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MarkerKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.NumberSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackClock;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PreparedFrame;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 帧准备：通道补全、布局、事实绑定、错误传播与事件展开接线。
class CorePlaybackFrameFactoryTest {

    private static final RigId RIG = new RigId("rig");
    private static final TrackId AIM = new TrackId("aim");
    private static final TrackId ACTIVE = new TrackId("active");
    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final FrameEnvironment ENVIRONMENT = new FrameEnvironment(DIMENSION, 7L, 20L, 0.5f);
    private static final EventBudget BUDGET = new EventBudget(10, 10);

    @Test
    void constantChannelsProduceWorldPoseAndLayout() {
        CameraAnimation document = document(constantChannels());
        CameraRig rig = document.rigs().getFirst();
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, new WorldPose(
                new Vec3(10, 20, 30), new Rotation(0.5, 0.25, 0.1), 90f));

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(1, frame.rigs().size());
        var pose = frame.rigs().getFirst();
        assertEquals(RIG, pose.rigId());
        assertEquals(1.0, pose.pose().position().x(), 1e-6);
        assertEquals(2.0, pose.pose().position().y(), 1e-6);
        assertEquals(3.0, pose.pose().position().z(), 1e-6);
        assertEquals(0.1, pose.pose().rotation().pitch(), 1e-6);
        assertEquals(0.2, pose.pose().rotation().yaw(), 1e-6);
        assertEquals(0.3, pose.pose().rotation().roll(), 1e-6);
        assertEquals(70f, pose.pose().fov(), 1e-6);
        assertEquals(1.0, pose.rect().asNormalized().orElseThrow().width(), 1e-6);
    }

    @Test
    void pathModeUsesDistanceChannelAndPathSampler() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path path =
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path("path", List.of(
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathNode.at(
                                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId("a"),
                                new Vec3(0, 0, 0)),
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathNode.at(
                                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId("b"),
                                new Vec3(10, 0, 0))));
        CameraRig base = CameraRig.create(RIG, "rig",
                List.of(track(CameraChannel.PATH_DISTANCE, new Constant(5f))), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), path,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MotionMode.PATH,
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.DistanceMode.ABSOLUTE,
                base.space(), base.aim(), base.obstacle(), base.view());
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, basePose());

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(5.0, frame.rigs().getFirst().pose().position().x(), 1e-6);
    }

    @Test
    void localRigAppliesAnchorRotationToPositionAndPose() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor anchor =
                () -> Identifier.fromNamespaceAndPath("test", "anchor");
        CameraRig base = CameraRig.create(RIG, "rig",
                List.of(track(CameraChannel.POSITION_X, new Constant(1f))), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                        anchor, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.NONE),
                base.aim(), base.obstacle(), base.view());
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        var resolved = new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor(
                anchor, new Vec3(10, 0, 0), new Rotation(Math.PI / 2, 0, 0), false);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> new FrameFacts(request.snapshot(), request.dimension(),
                        request.worldInstanceId(), request.gameTick(), request.partialTick(), 1L,
                        Map.of(RIG, new FrameFacts.RigFacts(
                                new SpatialResolution.Resolved(resolved),
                                new SpatialResolution.NotRequired(), AimState.off())),
                        Map.of(), true),
                () -> new WorldPose(new Vec3(0, 0, 0), Rotation.NONE, 90f), () -> ENVIRONMENT);

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        var pose = frame.rigs().getFirst().pose();
        assertEquals(10, pose.position().x(), 1e-6);
        assertEquals(0, pose.position().y(), 1e-6);
        assertEquals(-1, pose.position().z(), 1e-6);
        assertEquals(Math.PI / 2, pose.rotation().yaw(), 1e-6);
    }

    @Test
    void obstacleResolverAdjustsPositionAfterAim() {
        CameraAnimation document = obstacleDocument();
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                new cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CoreExpressionEvaluator(),
                request -> facts(request, document), () -> basePose(), () -> ENVIRONMENT,
                request -> new ObstacleResolver.Result.Adjusted(
                        request.expected().add(new Vec3(0, 2, 0))));

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(22.0, frame.rigs().getFirst().pose().position().y(), 1e-6);
    }

    @Test
    void obstacleWithoutResolverFailsFrame() {
        CameraAnimation document = obstacleDocument();
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, basePose());

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertFalse(frame.isExecutable());
        assertTrue(frame.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().toString().contains("obstacle resolver")));
    }

    @Test
    void inactiveCameraSourceRigFailsFrame() {
        CameraRig base = CameraRig.create(RIG, "rig", List.of(), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(), base.space(), base.aim(), base.obstacle(),
                base.view().withEnabled(false));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withCameraSource(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack.of(
                        new TrackId("source"), "source", CameraSource.rig(RIG), List.of()))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, basePose());

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertFalse(frame.isExecutable());
        assertTrue(frame.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().toString().contains("not active")));
    }

    @Test
    void missingChannelsFallBackToBasePosePerAxis() {
        CameraAnimation document = document(List.of());
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, new WorldPose(
                new Vec3(10, 20, 30), new Rotation(0.5, 0.25, 0.1), 90f));

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertTrue(frame.isExecutable());
        assertEquals(10.0, frame.rigs().getFirst().pose().position().x(), 1e-6);
        assertEquals(90f, frame.rigs().getFirst().pose().fov(), 1e-6);
    }

    @Test
    void channelErrorProducesErrorDiagnosticAndNonExecutableFrame() {
        CameraAnimation document = document(List.of(track(CameraChannel.POSITION_X, new Formula("1/0"))));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, basePose());

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertFalse(frame.isExecutable());
        assertTrue(frame.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.severity() == cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity.ERROR));
        assertTrue(frame.rigs().isEmpty() || frame.rigs().getFirst().pose().position().x() == 10.0);
    }

    @Test
    void frameFactsReceiveSameSnapshotAndPlan() {
        CameraAnimation document = document(constantChannels());
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        var seen = new java.util.concurrent.atomic.AtomicReference<AnimationSnapshot>();
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> {
                    seen.set(request.snapshot());
                    return facts(request, document);
                }, () -> basePose(), () -> ENVIRONMENT);

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 0L, BUDGET);

        assertTrue(frame.isExecutable());
        assertTrue(frame.facts().sourceSnapshot() == snapshot);
        assertTrue(seen.get() == snapshot);
        assertEquals(DIMENSION, frame.facts().dimension());
    }

    private static CorePlaybackFrameFactory factory(AnimationSnapshot snapshot, WorldPose base) {
        return new CorePlaybackFrameFactory(
                request -> facts(request, snapshot.document()), () -> base, () -> ENVIRONMENT);
    }

    private static FrameFacts facts(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFactsRequest request,
                                    CameraAnimation document) {
        return new FrameFacts(request.snapshot(), request.dimension(), request.worldInstanceId(),
                request.gameTick(), request.partialTick(), 1L,
                Map.of(RIG, new FrameFacts.RigFacts(new SpatialResolution.NotRequired(),
                        new SpatialResolution.NotRequired(), AimState.off())),
                Map.of(), true);
    }

    private static PlaybackRequest request(CameraAnimation document) {
        return new PlaybackRequest(document, Map.of(), 1f, false, 0f);
    }

    private static PlaybackClock clock(float time) {
        return new PlaybackClock(time, 1f, false, 10f);
    }

    private static WorldPose basePose() {
        return new WorldPose(new Vec3(10, 20, 30), new Rotation(0.5, 0.25, 0.1), 90f);
    }

    private static CameraAnimation obstacleDocument() {
        CameraRig base = CameraRig.create(RIG, "rig",
                List.of(track(CameraChannel.POSITION_X, new Constant(1f))), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(), base.space(), base.aim(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings(true, 0.2),
                base.view());
        return CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
    }

    private static CameraAnimation document(List<CurveTrack> channels) {
        CameraRig rig = CameraRig.create(RIG, "rig", channels, AIM, ACTIVE);
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));
        return document.withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "",
                MarkerKind.GENERIC)));
    }

    private static List<CurveTrack> constantChannels() {
        return List.of(
                track(CameraChannel.POSITION_X, new Constant(1f)),
                track(CameraChannel.POSITION_Y, new Constant(2f)),
                track(CameraChannel.POSITION_Z, new Constant(3f)),
                track(CameraChannel.ROTATION_X, new Constant(0.1f)),
                track(CameraChannel.ROTATION_Y, new Constant(0.2f)),
                track(CameraChannel.ROTATION_Z, new Constant(0.3f)),
                track(CameraChannel.FOV, new Constant(70f)));
    }

    private static CurveTrack track(CameraChannel channel, NumberSource source) {
        Keyframe key = new Keyframe(new KeyId("key-" + channel.key()), 0f, source,
                0f, 0f, 1f, 1f, Curve.Interpolation.LINEAR);
        return CurveTrack.ofChannel(new TrackId("track-" + channel.key()), channel.key(), channel,
                Curve.of(key));
    }
}
