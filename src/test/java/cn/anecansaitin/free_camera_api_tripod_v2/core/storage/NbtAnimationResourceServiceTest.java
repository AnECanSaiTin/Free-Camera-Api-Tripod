package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// NBT 文件资源服务：往返、原子保存、枚举、路径与规模拒绝（主设计 §8.2）。
class NbtAnimationResourceServiceTest {

    @TempDir
    Path root;

    @Test
    void saveLoadAndEntriesRoundTrip() {
        NbtAnimationResourceService service = service(NbtResourceLimits.DEFAULT);
        ResourceId id = new ResourceId("test/animation.nbt");
        CameraAnimation document = document();

        SaveResult saved = service.save(id, document);

        assertType(SaveResult.Saved.class, saved);
        assertTrue(Files.isRegularFile(root.resolve("test/animation.nbt")));

        LoadResult loaded = service.load(id);
        LoadResult.Loaded accepted = assertType(LoadResult.Loaded.class, loaded);
        assertEquals(document, accepted.document());

        AnimationResourceEntry entry = service.entry(id).orElseThrow();
        assertEquals(id, entry.resource());
        assertFalse(entry.contentHash().isBlank());

        List<AnimationResourceEntry> entries = service.entries();
        assertEquals(1, entries.size());
        assertEquals(id, entries.getFirst().resource());
    }

    @Test
    void missingAndEscapingResourcesAreRejected() {
        NbtAnimationResourceService service = service(NbtResourceLimits.DEFAULT);

        assertType(LoadResult.Rejected.class,
                service.load(new ResourceId("test/missing.nbt")));
        assertType(LoadResult.Rejected.class,
                service.load(new ResourceId("../escape.nbt")));
    }

    @Test
    void oversizedSaveAndCorruptLoadAreRejected() {
        NbtAnimationResourceService service = service(
                new NbtResourceLimits(32, 128, 1_000_000));
        ResourceId id = new ResourceId("test/animation.nbt");

        assertType(SaveResult.Rejected.class, service.save(id, document()));

        Path bad = root.resolve("bad.nbt");

        try {
            Files.createDirectories(bad.getParent());
            Files.write(bad, new byte[]{1, 2, 3});
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        assertType(LoadResult.Rejected.class,
                service.load(new ResourceId("bad.nbt")));
    }

    @Test
    void asyncSaveCompletesOnce() throws Exception {
        NbtAnimationResourceService service = service(NbtResourceLimits.DEFAULT);
        CompletableFuture<SaveResult> future = new CompletableFuture<>();

        service.saveAsync(new ResourceId("async.nbt"), document(), future::complete);

        SaveResult result = future.get(5, TimeUnit.SECONDS);
        assertType(SaveResult.Saved.class, result);
        assertTrue(Files.isRegularFile(root.resolve("async.nbt")));
    }

    private static <T> T assertType(Class<T> type, Object value) {
        assertTrue(type.isInstance(value),
                () -> "expected " + type.getSimpleName() + " but got " + value);
        return type.cast(value);
    }
    private NbtAnimationResourceService service(NbtResourceLimits limits) {
        return new NbtAnimationResourceService(
                new ResourcePathResolver(root),
                CameraAnimation.codec(frozenRegistries()),
                limits);
    }

    private static DocumentRegistries frozenRegistries() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        anchors.freeze();
        tracks.freeze();
        samples.freeze();
        return new DocumentRegistries(anchors, tracks, samples);
    }

    private static CameraAnimation document() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        return CameraAnimation.create("doc", rig, new TrackId("source"));
    }
}
