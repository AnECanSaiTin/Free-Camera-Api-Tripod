package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayElement;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.DiagnosticReport;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [FileAnimationResourceService] 的本地 JSON 保存 / 载入、元数据与失败边界。
///
/// 覆盖：往返、仅结构解码（不执行统一文档校验）、非法 JSON / 结构错误、原子保存失败清理、
/// 元数据哈希与稳定枚举、异步回调一次、路径穿越拒绝。
class FileAnimationResourceServiceTest {

    @TempDir
    Path tempDir;

    private Path root;
    private FileAnimationResourceService service;

    @BeforeEach
    void setUp() {
        root = tempDir.resolve("resources");
        service = new FileAnimationResourceService(new ResourcePathResolver(root), registries());
    }

    private static DocumentRegistries registries() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        anchors.freeze();

        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        tracks.freeze();

        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        samples.freeze();

        return new DocumentRegistries(anchors, tracks, samples);
    }

    private static CameraAnimation document(String name) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        return CameraAnimation.create(name, rig, new TrackId("source"));
    }

    @Test
    void savesAndLoadsJsonRoundTripWithMetadata() throws IOException {
        CameraAnimation document = document("roundtrip");
        ResourceId id = new ResourceId("animations/roundtrip.json");

        SaveResult saved = service.save(id, document);

        assertTrue(saved instanceof SaveResult.Saved, () -> "save failed: " + saved);
        AnimationResourceEntry savedEntry = ((SaveResult.Saved) saved).entry();
        assertEquals(id, savedEntry.resource());
        assertEquals(64, savedEntry.contentHash().length());
        assertTrue(savedEntry.sizeBytes() > 0);
        assertTrue(Files.isRegularFile(root.resolve("animations/roundtrip.json")));

        LoadResult loaded = service.load(id);

        assertTrue(loaded instanceof LoadResult.Loaded, () -> "load failed: " + loaded);
        LoadResult.Loaded accepted = (LoadResult.Loaded) loaded;
        assertEquals(document, accepted.document());
        assertEquals(savedEntry, accepted.entry());
        assertEquals(savedEntry, service.entry(id).orElseThrow());
        assertNoTemporaryFiles();
    }

    @Test
    void saveAtomicallyReplacesAnExistingResource() throws IOException {
        ResourceId id = new ResourceId("replace.json");

        assertTrue(service.save(id, document("first")) instanceof SaveResult.Saved);
        String firstHash = service.entry(id).orElseThrow().contentHash();

        SaveResult replaced = service.save(id, document("second"));

        assertTrue(replaced instanceof SaveResult.Saved, () -> "replace failed: " + replaced);
        String secondHash = service.entry(id).orElseThrow().contentHash();
        assertNotEquals(firstHash, secondHash);

        LoadResult loaded = service.load(id);
        assertTrue(loaded instanceof LoadResult.Loaded);
        assertEquals("second", ((LoadResult.Loaded) loaded).document().name());
        assertNoTemporaryFiles();
    }

    @Test
    void missingResourceIsRejectedWithFileScopedDiagnostic() {
        ResourceId id = new ResourceId("animations/missing.json");

        LoadResult result = service.load(id);

        assertRejectedLoad(result, id, FileAnimationResourceService.RESOURCE_READ_FAILED);
        assertTrue(service.entry(id).isEmpty());
    }

    @Test
    void rejectsOversizedFileBeforeJsonParsing() throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("large.json"), "x".repeat(128));
        FileAnimationResourceService limited = new FileAnimationResourceService(
                new ResourcePathResolver(root), registries(), new JsonResourceLimits(32, 8));
        ResourceId id = new ResourceId("large.json");

        LoadResult result = limited.load(id);

        assertRejectedLoad(result, id, FileAnimationResourceService.RESOURCE_TOO_LARGE);
    }

    @Test
    void rejectsTooDeepJsonBeforeParsing() throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("deep.json"), "[".repeat(20) + "]".repeat(20));
        FileAnimationResourceService limited = new FileAnimationResourceService(
                new ResourcePathResolver(root), registries(), new JsonResourceLimits(1024, 4));
        ResourceId id = new ResourceId("deep.json");

        LoadResult result = limited.load(id);

        assertRejectedLoad(result, id, FileAnimationResourceService.RESOURCE_TOO_DEEP);
    }

    @Test
    void rejectsOversizedEncodedDocument() {
        FileAnimationResourceService limited = new FileAnimationResourceService(
                new ResourcePathResolver(root), registries(), new JsonResourceLimits(32, 128));
        ResourceId id = new ResourceId("too-large.json");

        SaveResult result = limited.save(id, document("too-large"));

        assertTrue(result instanceof SaveResult.Rejected, () -> "unexpected: " + result);
        assertTrue(((SaveResult.Rejected) result).report().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code()
                        .equals(FileAnimationResourceService.RESOURCE_TOO_LARGE)));
        assertFalse(Files.exists(root.resolve("too-large.json")));
    }

    @Test
    void malformedJsonIsRejectedWithoutPartialDocument() throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("broken.json"), "{\"data_version\": 1, ");
        ResourceId id = new ResourceId("broken.json");

        LoadResult result = service.load(id);

        assertRejectedLoad(result, id, FileAnimationResourceService.RESOURCE_JSON_INVALID);
        assertTrue(result instanceof LoadResult.Rejected);
    }

    @Test
    void structurallyInvalidJsonIsRejectedWithoutPartialDocument() throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("structure.json"), "{\"data_version\": 1}");
        ResourceId id = new ResourceId("structure.json");

        LoadResult result = service.load(id);

        assertRejectedLoad(result, id, FileAnimationResourceService.RESOURCE_STRUCTURE_INVALID);
    }

    @Test
    void loadOnlyDecodesStructureAndDoesNotRunDocumentValidation() throws IOException {
        // 来源引用一台不存在的机位：结构可编码，但统一校验会拒绝。资源服务必须只做结构解码。
        CameraAnimation semanticallyInvalid = document("unvalidated").withCameraSource(
                CameraSourceTrack.of(new TrackId("source"), "source",
                        CameraSource.rig(new RigId("missing")), List.of()));
        ResourceId id = new ResourceId("unvalidated.json");

        assertTrue(service.save(id, semanticallyInvalid) instanceof SaveResult.Saved);

        LoadResult loaded = service.load(id);

        assertTrue(loaded instanceof LoadResult.Loaded, () -> "load failed: " + loaded);
        assertEquals(semanticallyInvalid, ((LoadResult.Loaded) loaded).document());
    }

    @Test
    void entriesListJsonRegularFilesInStableOrderWithHashAndSize() throws IOException {
        service.save(new ResourceId("b.json"), document("b"));
        service.save(new ResourceId("nested/c.json"), document("c"));
        service.save(new ResourceId("a.json"), document("a"));

        Files.createDirectories(root.resolve("directory.json"));
        Files.writeString(root.resolve("ignore.txt"), "not a resource");
        Files.writeString(root.resolve(".leftover.tmp"), "not a resource");

        List<AnimationResourceEntry> entries = service.entries();

        assertEquals(List.of("a.json", "b.json", "nested/c.json"),
                entries.stream().map(entry -> entry.resource().value()).toList());
        assertEquals(entries, service.entries(), "enumeration must be stable");

        for (AnimationResourceEntry entry : entries) {
            Path file = root.resolve(entry.resource().value());
            assertTrue(Files.isRegularFile(file));
            assertEquals(Files.size(file), entry.sizeBytes());
            assertEquals(sha256Hex(Files.readAllBytes(file)), entry.contentHash());
            assertTrue(entry.modifiedAtEpochMillis() > 0);
            assertEquals(entry, service.entry(entry.resource()).orElseThrow());
            assertTrue(entry.resource().value().indexOf('\\') < 0,
                    "resource ids must use '/' separators");
        }

        assertTrue(service.entry(new ResourceId("directory.json")).isEmpty());
        assertTrue(service.entry(new ResourceId("missing.json")).isEmpty());
    }

    @Test
    void saveAsyncInvokesCompletionExactlyOnce() throws InterruptedException {
        ResourceId id = new ResourceId("async.json");
        CameraAnimation document = document("async");
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<SaveResult> captured = new AtomicReference<>();

        service.saveAsync(id, document, result -> {
            calls.incrementAndGet();
            captured.set(result);
            completed.countDown();
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS), "async save did not complete");
        Thread.sleep(100);

        assertEquals(1, calls.get(), "completion must be invoked exactly once");
        assertTrue(captured.get() instanceof SaveResult.Saved,
                () -> "async save failed: " + captured.get());

        LoadResult loaded = service.load(id);
        assertTrue(loaded instanceof LoadResult.Loaded);
        assertEquals(document, ((LoadResult.Loaded) loaded).document());
    }

    @Test
    void failedSaveLeavesNoFormalFileOrTemporaryFile() throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("blocked"), "a regular file blocks the directory");
        ResourceId id = new ResourceId("blocked/anim.json");

        SaveResult result = service.save(id, document("blocked"));

        assertRejectedSave(result, id, FileAnimationResourceService.RESOURCE_WRITE_FAILED);
        assertFalse(Files.exists(root.resolve("blocked/anim.json")));
        assertNoTemporaryFiles();
    }

    @Test
    void failedAtomicReplacementKeepsTargetAndRemovesTemporaryFile() throws IOException {
        Path target = Files.createDirectories(root.resolve("target.json"));
        Files.writeString(target.resolve("keep.txt"), "keep");
        ResourceId id = new ResourceId("target.json");

        SaveResult result = service.save(id, document("target"));

        assertRejectedSave(result, id, FileAnimationResourceService.RESOURCE_WRITE_FAILED);
        assertTrue(Files.isDirectory(target));
        assertEquals("keep", Files.readString(target.resolve("keep.txt")));
        assertNoTemporaryFiles();
    }

    @Test
    void encodeFailureDoesNotCreateResource() throws IOException {
        OverlayElement overlay = new OverlayElement(
                new OverlayId("overlay"), OverlayKind.LETTERBOX, true, 0,
                0f, 10f, new Constant(1f), 0f, 0f, "");
        CameraAnimation withOverlay = document("overlay").withOverlays(List.of(overlay));
        ResourceId id = new ResourceId("overlay.json");

        SaveResult result = service.save(id, withOverlay);

        assertRejectedSave(result, id, FileAnimationResourceService.RESOURCE_ENCODE_FAILED);
        assertFalse(Files.exists(root.resolve("overlay.json")));
        assertNoTemporaryFiles();
    }

    @Test
    void pathTraversalIsRejectedThroughServiceSurfaceWithoutThrowing() throws IOException {
        Path outside = tempDir.resolve("outside.json");
        Files.writeString(outside, "outside");
        String originalOutside = Files.readString(outside);

        List<String> values = List.of(
                "../outside.json",
                "nested/../../outside.json",
                "/outside.json",
                "C:/outside.json");

        for (String value : values) {
            ResourceId id = new ResourceId(value);

            LoadResult loaded = service.load(id);
            assertRejectedLoad(loaded, id, FileAnimationResourceService.RESOURCE_PATH_REJECTED);
            assertTrue(service.entry(id).isEmpty());

            SaveResult saved = service.save(id, document("escape"));
            assertRejectedSave(saved, id, FileAnimationResourceService.RESOURCE_PATH_REJECTED);
        }

        assertEquals(originalOutside, Files.readString(outside));
        assertFalse(Files.exists(root), "rejected saves must not create the resource root");
        assertTrue(service.entries().isEmpty());
    }

    private static void assertRejectedLoad(LoadResult result, ResourceId id, DiagnosticCode expectedCode) {
        assertTrue(result instanceof LoadResult.Rejected, () -> "expected rejection: " + result);
        DiagnosticReport report = ((LoadResult.Rejected) result).report();
        assertFileReport(report, id);
        assertEquals(expectedCode, report.diagnostics().getFirst().code());
    }

    private static void assertRejectedSave(SaveResult result, ResourceId id, DiagnosticCode expectedCode) {
        assertTrue(result instanceof SaveResult.Rejected, () -> "expected rejection: " + result);
        DiagnosticReport report = ((SaveResult.Rejected) result).report();
        assertFileReport(report, id);
        assertEquals(expectedCode, report.diagnostics().getFirst().code());
    }

    private static void assertFileReport(DiagnosticReport report, ResourceId id) {
        assertEquals(id, report.resource());
        assertFalse(report.diagnostics().isEmpty());
        assertTrue(report.hasErrors());

        for (Diagnostic diagnostic : report.diagnostics()) {
            assertTrue(diagnostic.scope() instanceof DiagnosticScope.File,
                    "storage diagnostics must be File-scoped");
            assertEquals(id, ((DiagnosticScope.File) diagnostic.scope()).resource());
            assertEquals(Severity.ERROR, diagnostic.severity());
            assertNull(diagnostic.fix());
            assertNull(diagnostic.location());
        }
    }

    private void assertNoTemporaryFiles() throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> files = Files.walk(root)) {
            List<Path> temporary = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".tmp"))
                    .toList();

            assertTrue(temporary.isEmpty(), () -> "temporary files left behind: " + temporary);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is unavailable", e);
        }
    }
}
