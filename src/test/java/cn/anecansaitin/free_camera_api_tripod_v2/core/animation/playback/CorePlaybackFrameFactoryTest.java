package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ExtensionTimelineTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MarkerKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
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
    private static final FrameEnvironment ENVIRONMENT = new FrameEnvironment(
            DIMENSION, 7L, 20L, 0.5f, new WorldTime(12000, 24000));
    private static final EventBudget BUDGET = new EventBudget(10, 10);

    @Test
    void constantChannelsProduceWorldPoseAndLayout() {
        CameraAnimation document = document(constantChannels());
        CameraRig rig = document.rigs().getFirst();
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, new WorldPose(
                new Vec3(10, 20, 30), new Rotation(0.5, 0.25, 0.1), 90f));

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 1f, 0L, BUDGET);

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
                0f, 1f, 0L, BUDGET);

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
                        request.worldInstanceId(), request.gameTick(), request.partialTick(),
                        request.worldTime(), 1L,
                        Map.of(RIG, new FrameFacts.RigFacts(
                                new SpatialResolution.Resolved(resolved),
                                new SpatialResolution.NotRequired(), AimState.off())),
                        Map.of(), true),
                () -> new WorldPose(new Vec3(0, 0, 0), Rotation.NONE, 90f), () -> ENVIRONMENT);

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        var pose = frame.rigs().getFirst().pose();
        // 跟随掩码 NONE：锚点旋转不参与位置与姿态合成；未编写的 Y/Z 在文档参考系内由
        // 基础姿态补全后再正变换回来。基础位置是原点，锚点在 (10,0,0)，因此世界位置是 (11,0,0)。
        assertEquals(11, pose.position().x(), 1e-6);
        assertEquals(0, pose.position().y(), 1e-6);
        assertEquals(0, pose.position().z(), 1e-6);
        assertEquals(0, pose.rotation().yaw(), 1e-6);
    }

    @Test
    void localRigAbsentAxesRoundTripBackToBaseWorldPose() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor anchor =
                () -> Identifier.fromNamespaceAndPath("test", "anchor");
        CameraRig base = CameraRig.create(RIG, "rig", List.of(), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                        anchor, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.NONE),
                base.aim(), base.obstacle(), base.view());
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        var resolved = new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor(
                anchor, new Vec3(100, 0, 0),
                new Rotation(Math.PI / 2, 0.25, 0.5), false);
        WorldPose basePose = new WorldPose(new Vec3(10, 20, 30),
                new Rotation(0.5, 0.25, 0.1), 90f);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> new FrameFacts(request.snapshot(), request.dimension(),
                        request.worldInstanceId(), request.gameTick(), request.partialTick(),
                        request.worldTime(), 1L,
                        Map.of(RIG, new FrameFacts.RigFacts(
                                new SpatialResolution.Resolved(resolved),
                                new SpatialResolution.NotRequired(), AimState.off())),
                        Map.of(), true),
                () -> basePose, () -> ENVIRONMENT);

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        WorldPose pose = frame.rigs().getFirst().pose();
        // 没有通道：位置在文档参考系取基础位置，正变换后必须回到基础世界位置。
        assertEquals(basePose.position().x(), pose.position().x(), 1e-6);
        assertEquals(basePose.position().y(), pose.position().y(), 1e-6);
        assertEquals(basePose.position().z(), pose.position().z(), 1e-6);
    }

    @Test
    void localRigFollowMaskAllAppliesAnchorRotation() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor anchor =
                () -> Identifier.fromNamespaceAndPath("test", "anchor");
        CameraRig base = CameraRig.create(RIG, "rig",
                List.of(track(CameraChannel.POSITION_X, new Constant(1f))), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                        anchor, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.ALL),
                base.aim(), base.obstacle(), base.view());
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        var resolved = new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor(
                anchor, new Vec3(10, 0, 0), new Rotation(Math.PI / 2, 0, 0), false);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> new FrameFacts(request.snapshot(), request.dimension(),
                        request.worldInstanceId(), request.gameTick(), request.partialTick(),
                        request.worldTime(), 1L,
                        Map.of(RIG, new FrameFacts.RigFacts(
                                new SpatialResolution.Resolved(resolved),
                                new SpatialResolution.NotRequired(), AimState.off())),
                        Map.of(), true),
                () -> new WorldPose(new Vec3(0, 0, 0), Rotation.NONE, 90f), () -> ENVIRONMENT);

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        WorldPose pose = frame.rigs().getFirst().pose();
        assertEquals(0, pose.position().x(), 1e-6);
        assertEquals(0, pose.position().y(), 1e-6);
        assertEquals(-1, pose.position().z(), 1e-6);
        // 基础姿态是单位旋转：逆变换进参考系再正变换回来，仍应是单位旋转。
        assertEquals(0, pose.rotation().yaw(), 1e-6);
    }

    @Test
    void wtUsesSameDayProgressForChannelAndLayoutFormulas() {
        WorldTime world = new WorldTime(3000, 12000); // 0.25，昼夜长度不是 24000
        FrameEnvironment environment = new FrameEnvironment(
                DIMENSION, 7L, 20L, 0.5f, world);
        CameraRig base = CameraRig.create(RIG, "rig",
                List.of(track(CameraChannel.POSITION_X, new Formula("wt"))), AIM, ACTIVE);
        RigView.Rect.Normalized rect = new RigView.Rect.Normalized(
                AnimatedNumber.constant(0f),
                AnimatedNumber.constant(0f),
                AnimatedNumber.formula("wt"),
                AnimatedNumber.constant(1f));
        CameraRig rig = base.withView(base.view().withRect(rect));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> new FrameFacts(request.snapshot(), request.dimension(),
                        request.worldInstanceId(), request.gameTick(), request.partialTick(),
                        request.worldTime(), 1L,
                        Map.of(RIG, new FrameFacts.RigFacts(new SpatialResolution.NotRequired(),
                                new SpatialResolution.NotRequired(), AimState.off())),
                        Map.of(), true),
                () -> new WorldPose(new Vec3(0, 0, 0), Rotation.NONE, 90f),
                () -> environment);

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(0.25, frame.rigs().getFirst().pose().position().x(), 1e-6);
        assertEquals(0.25,
                frame.rigs().getFirst().rect().asNormalized().orElseThrow().width(), 1e-6);
    }

    @Test
    void loopIntervalExpandsEventsAcrossFullCycles() {
        TestTimelineTrack events = new TestTimelineTrack(new TrackId("events"), "events",
                List.of(new ExtensionTimelineTrack.TimelineKey(new KeyId("at-5"), 5f)));
        CameraRig rig = CameraRig.create(RIG, "rig", List.of(), AIM, ACTIVE);
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withTracks(List.of(events))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> facts(request, document), () -> basePose(), () -> ENVIRONMENT);
        PlaybackRequest looped = new PlaybackRequest(document, Map.of(), 1f, true, 2f);
        PlaybackClock finalClock = new PlaybackClock(2f, 1f, true, 10f);

        PreparedFrame frame = factory.prepare(snapshot, looped, finalClock,
                2f, 22f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        // 推进区间 (2,22] 跨两个完整 10 秒周期：5 秒事件应分别在 5 秒与 15 秒各触发一次。
        assertEquals(2, frame.events().size());
        assertEquals(5f, frame.events().get(0).time());
        assertEquals(5f, frame.events().get(1).time());
    }

    @Test
    void prepareStartReportsUnresolvedLocalAnchor() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor anchor =
                () -> Identifier.fromNamespaceAndPath("test", "anchor");
        CameraRig base = CameraRig.create(RIG, "rig", List.of(), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                        anchor, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.NONE),
                base.aim(), base.obstacle(), base.view());
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> facts(request, document), () -> basePose(), () -> ENVIRONMENT);

        List<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic> diagnostics =
                factory.prepareStart(snapshot, request(document), clock(1f));

        assertTrue(diagnostics.stream().anyMatch(diagnostic ->
                        diagnostic.message().toString().contains("requires a resolved anchor")),
                () -> "unexpected: " + diagnostics);
    }

    @Test
    void prepareStartAcceptsResolvedLocalAnchor() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor anchor =
                () -> Identifier.fromNamespaceAndPath("test", "anchor");
        CameraRig base = CameraRig.create(RIG, "rig", List.of(), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                        anchor, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.NONE),
                base.aim(), base.obstacle(), base.view());
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        var resolved = new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor(
                anchor, new Vec3(10, 0, 0), Rotation.NONE, false);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> new FrameFacts(request.snapshot(), request.dimension(),
                        request.worldInstanceId(), request.gameTick(), request.partialTick(),
                        request.worldTime(), 1L,
                        Map.of(RIG, new FrameFacts.RigFacts(
                                new SpatialResolution.Resolved(resolved),
                                new SpatialResolution.NotRequired(), AimState.off())),
                        Map.of(), true),
                () -> basePose(), () -> ENVIRONMENT);

        List<cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic> diagnostics =
                factory.prepareStart(snapshot, request(document), clock(1f));

        assertTrue(diagnostics.isEmpty(), () -> "unexpected: " + diagnostics);
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
                0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(22.0, frame.rigs().getFirst().pose().position().y(), 1e-6);
    }

    @Test
    void requestFollowMaskOverrideDisablesDocumentFollowMask() {
        cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor anchor =
                () -> Identifier.fromNamespaceAndPath("test", "anchor");
        CameraRig base = CameraRig.create(RIG, "rig",
                List.of(track(CameraChannel.POSITION_X, new Constant(1f))), AIM, ACTIVE);
        CameraRig rig = new CameraRig(base.id(), base.name(), base.channels(), base.path(),
                base.motionMode(), base.distanceMode(),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space.Local(
                        anchor, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.ALL),
                base.aim(), base.obstacle(), base.view());
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"))
                .withMarkers(List.of(new Marker(new MarkerId("end"), 10f, "", MarkerKind.GENERIC)));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        var resolved = new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor(
                anchor, new Vec3(10, 0, 0), new Rotation(Math.PI / 2, 0, 0), false);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                request -> new FrameFacts(request.snapshot(), request.dimension(),
                        request.worldInstanceId(), request.gameTick(), request.partialTick(),
                        request.worldTime(), 1L,
                        Map.of(RIG, new FrameFacts.RigFacts(
                                new SpatialResolution.Resolved(resolved),
                                new SpatialResolution.NotRequired(), AimState.off())),
                        Map.of(), true),
                () -> new WorldPose(new Vec3(0, 0, 0), Rotation.NONE, 90f), () -> ENVIRONMENT);

        PlaybackRequest overridden = new PlaybackRequest(document,
                Map.of(RIG, new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides(
                        null, cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask.NONE,
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride.inherit(),
                        null)),
                1f, false, 0f);
        PreparedFrame frame = factory.prepare(snapshot, overridden, clock(1f), 0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(11, frame.rigs().getFirst().pose().position().x(), 1e-6);
        assertEquals(0, frame.rigs().getFirst().pose().rotation().yaw(), 1e-6);
    }

    @Test
    void requestObstacleOverrideCanDisableDocumentSettingWithoutResolver() {
        CameraAnimation document = obstacleDocument();
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, basePose());
        PlaybackRequest request = new PlaybackRequest(document,
                Map.of(RIG, new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides(
                        null, null,
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride.inherit(),
                        cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings.DISABLED)),
                1f, false, 0f);

        PreparedFrame frame = factory.prepare(snapshot, request, clock(1f), 0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(1.0, frame.rigs().getFirst().pose().position().x(), 1e-6);
    }

    @Test
    void requestObstacleOverrideCanEnableWhenDocumentDisablesIt() {
        CameraAnimation enabledDocument = obstacleDocument();
        CameraRig enabledRig = enabledDocument.rig(RIG).orElseThrow();
        CameraAnimation document = enabledDocument.withRig(
                enabledRig.withObstacle(cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings.DISABLED));
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = new CorePlaybackFrameFactory(
                new cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CoreExpressionEvaluator(),
                request -> facts(request, document), () -> basePose(), () -> ENVIRONMENT,
                request -> new ObstacleResolver.Result.Adjusted(
                        request.expected().add(new Vec3(0, 2, 0))));
        PlaybackRequest request = new PlaybackRequest(document,
                Map.of(RIG, new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides(
                        null, null,
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride.inherit(),
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings(true, 0.2))),
                1f, false, 0f);

        PreparedFrame frame = factory.prepare(snapshot, request, clock(1f), 0f, 1f, 0L, BUDGET);

        assertTrue(frame.isExecutable(), () -> "unexpected: " + frame.diagnostics());
        assertEquals(22.0, frame.rigs().getFirst().pose().position().y(), 1e-6);
    }

    @Test
    void obstacleWithoutResolverFailsFrame() {
        CameraAnimation document = obstacleDocument();
        AnimationSnapshot snapshot = new AnimationSnapshot(Revision.INITIAL, document);
        CorePlaybackFrameFactory factory = factory(snapshot, basePose());

        PreparedFrame frame = factory.prepare(snapshot, request(document), clock(1f),
                0f, 1f, 0L, BUDGET);

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
                0f, 1f, 0L, BUDGET);

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
                0f, 1f, 0L, BUDGET);

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
                0f, 1f, 0L, BUDGET);

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
                0f, 1f, 0L, BUDGET);

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
                request.gameTick(), request.partialTick(), request.worldTime(), 1L,
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

    private record TestTimelineTrack(TrackId id,
                                     String name,
                                     List<ExtensionTimelineTrack.TimelineKey> timelineKeys)
            implements ExtensionTimelineTrack {

        private static final Identifier TYPE =
                Identifier.fromNamespaceAndPath("test", "timeline");

        @Override
        public Identifier type() {
            return TYPE;
        }

        @Override
        public float duration() {
            return timelineKeys.isEmpty() ? 0f : timelineKeys.getLast().time();
        }

        @Override
        public int keyCount() {
            return timelineKeys.size();
        }

        @Override
        public TestTimelineTrack withName(String newName) {
            return new TestTimelineTrack(id, newName, timelineKeys);
        }
    }
}
