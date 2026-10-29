package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolFrameResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ActiveProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.BooleanSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.FovProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.NumericSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.ObstacleProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PositionProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.QuaternionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RectProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RotationProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 新模型端到端 JVM 验收：轨道池 → 属性 → 文件 → 播放 → 帧 → 姿态。
class TrackPoolEndToEndTest {

    private static final RigId RIG = new RigId("rig");

    @TempDir
    Path directory;

    @Test
    void poolBindsSavesPlaysAndComposesPose() {
        DocumentRegistries registries = registries();
        TrackId number = new TrackId("number");
        TrackId rotation = new TrackId("rotation");
        TrackId active = new TrackId("active");
        TrackId source = new TrackId("source");
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(
                CurveTrack.ofCurve(number, "number", new Curve(List.of(
                        new Keyframe(new KeyId("a"), 0f, new Constant(0f),
                                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR),
                        new Keyframe(new KeyId("b"), 4f, new Constant(4f),
                                0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)),
                        Curve.Wrap.CLAMP, Curve.Wrap.CLAMP)),
                new QuaternionTrack(rotation, "rotation", Quaternion.IDENTITY, List.of()),
                ActiveTrack.enabled(active),
                new CameraSourceTrack(source, "source", CameraSource.player(), List.of(
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe<>(
                                new KeyId("source-key"), 0f, CameraSource.player())))),
                List.of(PooledRig.create(RIG, "rig").withProperties(new RigProperties(
                        new PositionProperty(
                                PropertyBinding.enabled(new NumericSource.Track(number)),
                                PropertyBinding.disabled(),
                                PropertyBinding.disabled()),
                        new RotationProperty(PropertyBinding.enabled(
                                new QuaternionSource.Track(rotation))),
                        FovProperty.disabled(),
                        new ActiveProperty(PropertyBinding.enabled(
                                new BooleanSource.Constant(true))),
                        RectProperty.fullScreen(),
                        ObstacleProperty.disabled()))),
                PropertyBinding.enabled(new CameraSourceProperty.Track(source)),
                Symbols.EMPTY,
                List.of(Marker.at(new MarkerId("end"), 4f)),
                List.of(), List.of(), List.of(), PlaybackRange.AUTO);

        TrackPoolEditorSession session = new TrackPoolEditorSession(
                new TrackPoolDocumentStore(document));
        Path file = directory.resolve("doc.nbt.gz");
        new TrackPoolDocumentService(registries).save(file, session).result().orElseThrow();
        TrackPoolEditorSession reloaded = new TrackPoolDocumentService(registries)
                .open(file).result().orElseThrow();

        TrackPoolPlaybackController playback = new TrackPoolPlaybackController(
                new TrackPoolDocumentStore(reloaded.snapshot().document()));
        playback.play();
        TrackPoolFrameResult first = playback.tick(2f);
        assertTrue(first instanceof TrackPoolFrameResult.Prepared);
        var frame = ((TrackPoolFrameResult.Prepared) first).frame();

        List<RigPose> poses = new TrackPoolFrameComposer().compose(frame,
                Map.of(RIG, document.rigs().getFirst()),
                Map.of(RIG, new WorldPose(new Vec3(1, 2, 3), Rotation.NONE, 70f)),
                Map.of(RIG, new ResolvedRect.Normalized(0, 0, 1, 1)),
                Map.of(), null);

        assertEquals(1, poses.size());
        assertEquals(2.0, poses.getFirst().pose().position().x(), 1e-6);
        assertEquals(2.0, frame.time(), 1e-6);
        assertTrue(frame.cameraSource().isValue());
        assertEquals(new CameraSource.Player(), frame.cameraSource().value());
    }

    private static DocumentRegistries registries() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        anchors.freeze();
        tracks.freeze();
        samples.freeze();
        return new DocumentRegistries(anchors, tracks, samples);
    }
}
