package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 全局轨道池文档根：默认关闭、轨道池分派、data_version 与结构约束。
class TrackPoolAnimationTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void defaultDocumentIsHiddenAndAllPropertiesDisabled() {
        TrackPoolAnimation document = TrackPoolAnimation.create(
                "doc", PooledRig.create(RIG, "rig"));

        assertEquals(2, document.dataVersion());
        assertEquals(1, document.rigs().size());
        assertFalse(document.rigs().getFirst().output().enabled());
        assertTrue(document.rigs().getFirst().properties().position().x().isDisabled());
        assertTrue(document.cameraSourceBinding().isDisabled());
        assertTrue(document.tracks().isEmpty());
        assertEquals(PlaybackRange.AUTO, document.playbackRange());
    }

    @Test
    void codecRoundTripsTrackPoolAndBindings() {
        DocumentRegistries registries = registries();
        CurveTrack number = CurveTrack.ofCurve(new TrackId("number"), "number", Curve.of(
                new Keyframe(new KeyId("k"), 0f, new Constant(1f),
                        0f, 0f, 0f, 0f, Curve.Interpolation.LINEAR)));
        QuaternionTrack rotation = new QuaternionTrack(new TrackId("rotation"), "rotation",
                Quaternion.IDENTITY, List.of());
        ActiveTrack active = ActiveTrack.enabled(new TrackId("active"));
        CameraSourceTrack source = CameraSourceTrack.player(new TrackId("source"));
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "pool",
                List.of(number, rotation, active, source), List.of(PooledRig.create(RIG, "rig")),
                PropertyBinding.enabled(new CameraSourceProperty.Track(source.id())),
                Symbols.EMPTY, List.of(), List.of(), List.of(), List.of(), PlaybackRange.AUTO);

        JsonElement json = TrackPoolAnimation.codec(registries)
                .encodeStart(JsonOps.INSTANCE, document).result().orElseThrow();
        TrackPoolAnimation decoded = TrackPoolAnimation.codec(registries)
                .parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(document, decoded);
        assertTrue(decoded.track(number.id()).isPresent());
        assertTrue(decoded.track(rotation.id()).isPresent());
    }

    @Test
    void oldDataVersionIsRejected() {
        DocumentRegistries registries = registries();
        TrackPoolAnimation document = TrackPoolAnimation.create("doc", PooledRig.create(RIG, "rig"));
        JsonObject json = TrackPoolAnimation.codec(registries)
                .encodeStart(JsonOps.INSTANCE, document).result().orElseThrow().getAsJsonObject();
        json.addProperty("data_version", 1);

        var result = TrackPoolAnimation.codec(registries).parse(JsonOps.INSTANCE, json);

        assertTrue(result.error().isPresent(), () -> "old data_version must be rejected");
    }

    @Test
    void automaticPlaybackRangeUsesUnboundTracksAndMarkers() {
        QuaternionTrack unbound = new QuaternionTrack(new TrackId("rotation"), "rotation",
                Quaternion.IDENTITY, List.of(
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe<>(
                        new KeyId("a"), 0f, Quaternion.IDENTITY),
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe<>(
                        new KeyId("b"), 5f, Quaternion.IDENTITY)));
        TrackPoolAnimation document = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(unbound),
                List.of(PooledRig.create(RIG, "rig")), PropertyBinding.disabled(), Symbols.EMPTY,
                List.of(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker.at(
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId("m"), 8f)),
                List.of(), List.of(), List.of(), PlaybackRange.AUTO);

        assertEquals(8f, document.contentEndSeconds());
        assertEquals(0f, document.playbackStartSeconds());
        assertEquals(8f, document.playbackEndSeconds());
        assertEquals(8f, document.playbackDurationSeconds());
    }

    @Test
    void manualPlaybackRangeOverridesContentRange() {
        TrackPoolAnimation automatic = TrackPoolAnimation.create("doc", PooledRig.create(RIG, "rig"));
        TrackPoolAnimation manual = new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig")), PropertyBinding.disabled(), Symbols.EMPTY,
                List.of(), List.of(), List.of(), List.of(), new PlaybackRange.Manual(3f, 10f));

        assertEquals(0f, manual.contentEndSeconds());
        assertEquals(3f, manual.playbackStartSeconds());
        assertEquals(10f, manual.playbackEndSeconds());
        assertEquals(7f, manual.playbackDurationSeconds());
        assertEquals(0f, automatic.playbackDurationSeconds());
    }

    @Test
    void duplicateTrackAndRigIdsAreRejected() {
        CurveTrack first = CurveTrack.ofCurve(new TrackId("same"), "a", Curve.EMPTY);
        CurveTrack second = CurveTrack.ofCurve(new TrackId("same"), "b", Curve.EMPTY);

        assertThrows(IllegalArgumentException.class, () -> new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(first, second),
                List.of(PooledRig.create(RIG, "rig")), PropertyBinding.disabled(), Symbols.EMPTY,
                List.of(), List.of(), List.of(), List.of(), PlaybackRange.AUTO));

        assertThrows(IllegalArgumentException.class, () -> new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "a"), PooledRig.create(RIG, "b")),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO));
    }

    @Test
    void nonEmptyOverlayIsRejectedUntilOverlayDesignIsClosed() {
        assertThrows(IllegalArgumentException.class, () -> new TrackPoolAnimation(
                TrackPoolAnimation.CURRENT_DATA_VERSION, "doc", List.of(),
                List.of(PooledRig.create(RIG, "rig")), PropertyBinding.disabled(), Symbols.EMPTY,
                List.of(), List.of(), List.of(), List.of(new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayElement(
                        new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId("o"),
                        cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayKind.LETTERBOX,
                        true, 0, 0f, 0f, new Constant(1f), 0f, 0f, "content")),
                PlaybackRange.AUTO));
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
