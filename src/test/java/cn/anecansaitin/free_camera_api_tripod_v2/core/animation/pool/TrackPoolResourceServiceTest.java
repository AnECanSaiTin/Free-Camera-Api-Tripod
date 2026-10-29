package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池资源目录：有界枚举、名称限制与读写删除。
class TrackPoolResourceServiceTest {

    @TempDir
    Path root;

    @Test
    void listsBoundedResourcesAndRejectsTraversal() throws Exception {
        DocumentRegistries registries = registries();
        TrackPoolResourceService service = new TrackPoolResourceService(root, registries);
        TrackPoolEditorSession session = new TrackPoolEditorSession(new TrackPoolDocumentStore(
                TrackPoolAnimation.create("doc", PooledRig.create(new RigId("rig"), "rig"))));
        service.save("a.json", session).result().orElseThrow();
        service.save("b.nbt", session).result().orElseThrow();
        service.save("c.json", session).result().orElseThrow();
        Files.writeString(root.resolve("ignore.txt"), "x");

        var entries = service.list(2).result().orElseThrow();

        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.name().endsWith(".json")
                || entry.name().endsWith(".nbt")));
        assertTrue(service.load("../escape.json").error().isPresent());
        assertTrue(service.save("sub/doc.json", session).error().isPresent());
    }

    @Test
    void savesLoadsAndDeletes() {
        DocumentRegistries registries = registries();
        TrackPoolResourceService service = new TrackPoolResourceService(root, registries);
        TrackPoolEditorSession session = new TrackPoolEditorSession(new TrackPoolDocumentStore(
                TrackPoolAnimation.create("doc", PooledRig.create(new RigId("rig"), "rig"))));

        service.save("doc.json", session).result().orElseThrow();
        assertEquals("doc", service.load("doc.json").result().orElseThrow().name());
        assertTrue(service.delete("doc.json").result().isPresent());
        assertTrue(service.load("doc.json").error().isPresent());
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
