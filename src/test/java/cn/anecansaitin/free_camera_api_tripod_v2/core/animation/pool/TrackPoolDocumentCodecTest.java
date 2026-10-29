package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池 JSON/NBT 编解码与旧版本拒绝。
class TrackPoolDocumentCodecTest {

    private static final RigId RIG = new RigId("rig");

    @Test
    void jsonAndNbtRoundTrip() {
        DocumentRegistries registries = registries();
        TrackPoolAnimation document = document();

        var json = TrackPoolDocumentCodec.encodeJson(document, registries).result().orElseThrow();
        TrackPoolAnimation fromJson = TrackPoolDocumentCodec.decodeJson(json, registries)
                .result().orElseThrow();
        assertEquals(document, fromJson);

        byte[] nbt = TrackPoolDocumentCodec.encodeNbt(document, registries).result().orElseThrow();
        TrackPoolAnimation fromNbt = TrackPoolDocumentCodec.decodeNbt(nbt, registries)
                .result().orElseThrow();
        assertEquals(document, fromNbt);
    }

    @Test
    void oldDataVersionIsRejectedByJsonAndNbt() {
        DocumentRegistries registries = registries();
        JsonObject json = TrackPoolDocumentCodec.encodeJson(document(), registries)
                .result().orElseThrow().getAsJsonObject();
        json.addProperty("data_version", 1);

        assertTrue(TrackPoolDocumentCodec.decodeJson(json, registries).error().isPresent());
        assertTrue(TrackPoolDocumentCodec.decodeJson("{not json", registries).error().isPresent());
    }

    private static TrackPoolAnimation document() {
        CurveTrack number = CurveTrack.ofCurve(new TrackId("number"), "number", Curve.EMPTY);
        QuaternionTrack rotation = new QuaternionTrack(new TrackId("rotation"), "rotation",
                Quaternion.IDENTITY, List.of());
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc",
                List.of(number, rotation), List.of(PooledRig.create(RIG, "rig")),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
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
