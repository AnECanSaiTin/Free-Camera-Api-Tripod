package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池文件服务：JSON/NBT 往返与旧版本拒绝。
class TrackPoolFileServiceTest {

    @TempDir
    Path directory;

    @Test
    void writesAndReadsJsonAndNbtAtomically() {
        DocumentRegistries registries = registries();
        TrackPoolAnimation document = document();
        TrackPoolFileService service = new TrackPoolFileService();

        Path json = directory.resolve("doc.json");
        Path nbt = directory.resolve("doc.nbt");
        Path gzip = directory.resolve("doc.nbt.gz");

        assertEquals(document, service.read(
                service.write(json, document, registries).result().orElseThrow(), registries)
                .result().orElseThrow());
        assertEquals(document, service.read(
                service.write(nbt, document, registries).result().orElseThrow(), registries)
                .result().orElseThrow());
        var gzipWrite = service.write(gzip, document, registries);
        assertTrue(gzipWrite.error().isEmpty(), () -> "gzip write: "
                + gzipWrite.error().map(com.mojang.serialization.DataResult.Error::message).orElse(""));
        var gzipRead = service.read(gzip, registries);
        assertTrue(gzipRead.error().isEmpty(), () -> "gzip read: "
                + gzipRead.error().map(com.mojang.serialization.DataResult.Error::message).orElse(""));
        assertEquals(document, gzipRead.result().orElseThrow());
        assertTrue(Files.isRegularFile(json));
        assertTrue(Files.isRegularFile(nbt));
        assertTrue(Files.isRegularFile(gzip));
    }

    @Test
    void oldVersionFileIsRejected() throws Exception {
        DocumentRegistries registries = registries();
        TrackPoolFileService service = new TrackPoolFileService();
        Path json = directory.resolve("old.json");
        String encoded = TrackPoolDocumentCodec.encodeJson(document(), registries)
                .result().orElseThrow().toString().replace("\"data_version\":2", "\"data_version\":1");
        Files.writeString(json, encoded);

        assertTrue(service.read(json, registries).error().isPresent());
    }

    private static TrackPoolAnimation document() {
        CurveTrack number = CurveTrack.ofCurve(new TrackId("number"), "number", Curve.EMPTY);
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc",
                List.of(number), List.of(PooledRig.create(new RigId("rig"), "rig")),
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
