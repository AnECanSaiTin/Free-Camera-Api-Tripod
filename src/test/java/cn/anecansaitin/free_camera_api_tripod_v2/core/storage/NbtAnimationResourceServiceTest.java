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
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

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
    void boundedListingTruncatesAndReportsIt() {
        NbtAnimationResourceService service = service(NbtResourceLimits.DEFAULT);
        assertType(SaveResult.Saved.class, service.save(new ResourceId("a.nbt"), document()));
        assertType(SaveResult.Saved.class, service.save(new ResourceId("b.nbt"), document()));

        cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceListing one = service.list(1);
        assertEquals(1, one.entries().size());
        assertTrue(one.truncated());

        cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceListing all = service.list(2);
        assertEquals(2, all.entries().size());
        assertFalse(all.truncated());
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
    void compressedSaveLoadAndEntriesRoundTrip() {
        NbtAnimationResourceService service = service(NbtResourceLimits.DEFAULT);
        ResourceId id = new ResourceId("test/animation.nbt.gz");
        CameraAnimation document = document();

        assertType(SaveResult.Saved.class, service.save(id, document));
        assertTrue(Files.isRegularFile(root.resolve("test/animation.nbt.gz")));

        LoadResult.Loaded loaded = assertType(LoadResult.Loaded.class, service.load(id));

        assertEquals(document, loaded.document());
        assertEquals(id, loaded.entry().resource());
        assertTrue(service.entry(id).isPresent());
        assertTrue(service.entries().stream().anyMatch(entry -> entry.resource().equals(id)));
    }

    @Test
    void corruptCompressedAndDecompressionBombAreRejected() throws IOException {
        NbtAnimationResourceService service = service(new NbtResourceLimits(128, 128, 1_000_000));
        Path corrupt = root.resolve("corrupt.nbt.gz");
        Files.write(corrupt, new byte[]{1, 2, 3});

        assertType(LoadResult.Rejected.class,
                service.load(new ResourceId("corrupt.nbt.gz")));

        Path bomb = root.resolve("bomb.nbt.gz");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(new byte[4096]);
        }

        Files.write(bomb, bytes.toByteArray());

        assertType(LoadResult.Rejected.class,
                service.load(new ResourceId("bomb.nbt.gz")));
    }

    @Test
    void structureFailureCarriesNbtFieldPathInFileDiagnostic() throws IOException {
        NbtAnimationResourceService service = service(NbtResourceLimits.DEFAULT);
        DataResult<Tag> encoded = CameraAnimation.codec(frozenRegistries())
                .encodeStart(NbtOps.INSTANCE, document());
        CompoundTag compound = (CompoundTag) encoded.result().orElseThrow();
        compound.putString("data_version", "1");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(compound, output);
        }

        Files.write(root.resolve("path-field.nbt"), bytes.toByteArray());

        LoadResult result = service.load(new ResourceId("path-field.nbt"));

        assertType(LoadResult.Rejected.class, result);
        LoadResult.Rejected rejected = (LoadResult.Rejected) result;
        assertEquals("data_version",
                rejected.report().diagnostics().getFirst().location().fieldPath());
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
