package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.QuaternionTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// 文件 → 校验 → 编辑 → 保存的完整服务往返。
class TrackPoolDocumentServiceTest {

    @TempDir
    Path directory;

    @Test
    void opensEditsAndSavesCurrentSnapshot() {
        DocumentRegistries registries = registries();
        TrackPoolDocumentService service = new TrackPoolDocumentService(registries);
        Path path = directory.resolve("doc.json");
        service.save(path, new TrackPoolEditorSession(new TrackPoolDocumentStore(document())));

        TrackPoolEditorSession session = service.open(path).result().orElseThrow();
        assertTrue(session.addTrack(new QuaternionTrack(new TrackId("rotation"), "rotation",
                Quaternion.IDENTITY, List.of())) instanceof
                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEditResult.Applied);
        service.save(path, session).result().orElseThrow();

        TrackPoolEditorSession reopened = service.open(path).result().orElseThrow();
        assertTrue(reopened.snapshot().document().track(new TrackId("rotation")).isPresent());
    }

    @Test
    void oldVersionFileCannotBeOpened() throws Exception {
        DocumentRegistries registries = registries();
        TrackPoolDocumentService service = new TrackPoolDocumentService(registries);
        Path path = directory.resolve("old.json");
        String encoded = TrackPoolDocumentCodec.encodeJson(document(), registries)
                .result().orElseThrow().toString().replace("\"data_version\":2", "\"data_version\":1");
        Files.writeString(path, encoded);

        assertTrue(service.open(path).error().isPresent());
    }

    private static TrackPoolAnimation document() {
        return TrackPoolAnimation.create("doc", PooledRig.create(new RigId("rig"), "rig"));
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
