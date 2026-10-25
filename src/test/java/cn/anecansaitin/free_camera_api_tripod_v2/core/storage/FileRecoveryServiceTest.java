package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.DiagnosticReport;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoveryDecision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.RecoverySlot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [FileRecoveryService] 的本地恢复槽：捕获、列表、恢复、丢弃、决定与数量上限。
///
/// 覆盖：capture → slots → restore 往返与 envelope 内容、槽跨实例稳定排序、正式资源隔离、
/// 非法 JSON / 结构错误 / 哈希不一致的 Rejected、最旧槽裁剪、异步回调恰好一次，以及
/// `KEEP` 只持久化决定而不删除 / 恢复。
class FileRecoveryServiceTest {

    @TempDir
    Path tempDir;

    private Path resourceRootPath;
    private Path recoveryRootPath;
    private FileAnimationResourceService resourceService;
    private FileRecoveryService recoveryService;
    private AtomicLong clock;

    @BeforeEach
    void setUp() {
        resourceRootPath = tempDir.resolve("resources");
        recoveryRootPath = tempDir.resolve("recovery");

        DocumentRegistries registries = registries();
        resourceService = new FileAnimationResourceService(
                new ResourcePathResolver(resourceRootPath), registries);
        clock = new AtomicLong(1_000_000L);
        recoveryService = new FileRecoveryService(
                new ResourcePathResolver(recoveryRootPath),
                new ResourcePathResolver(resourceRootPath),
                registries,
                clock::get);
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

    private static AnimationSnapshot snapshot(long revision, CameraAnimation document) {
        return new AnimationSnapshot(new Revision(revision), document);
    }

    @Test
    void captureListsAndRestoresSlotWithContentAndMetadata() throws IOException, InterruptedException {
        CameraAnimation document = document("roundtrip");
        AnimationSnapshot snapshot = snapshot(7, document);
        ResourceId target = new ResourceId("animations/roundtrip.json");

        Captured captured = capture(123_456_789L, snapshot, target);
        SaveResult.Saved saved = captured.saved();
        RecoverySlot slot = captured.slot();

        ResourceId slotId = saved.entry().resource();
        assertTrue(slotId.value().startsWith(FileRecoveryService.SLOT_DIRECTORY + "/slot-"),
                "Saved entry must use the recovery slot identity");
        assertNotEquals(target, slotId, "Saved entry must not pretend to be the formal resource");
        assertEquals(64, saved.entry().contentHash().length());
        assertTrue(saved.entry().sizeBytes() > 0);
        assertEquals(123_456_789L, saved.entry().modifiedAtEpochMillis());

        assertEquals(target, slot.resource());
        assertEquals(snapshot, slot.snapshot());
        assertEquals(123_456_789L, slot.capturedAtEpochMillis());
        assertEquals(saved.entry().sizeBytes(), slot.sizeBytes());
        assertEquals(saved.entry().contentHash(), slot.contentHash());

        List<RecoverySlot> slots = recoveryService.slots();
        assertEquals(List.of(slot), slots);
        assertEquals(slots, recoveryService.slots(), "slot order and contents must be stable");

        LoadResult loaded = recoveryService.restore(slot);
        assertTrue(loaded instanceof LoadResult.Loaded, () -> "restore failed: " + loaded);
        LoadResult.Loaded accepted = (LoadResult.Loaded) loaded;
        assertEquals(document, accepted.document());
        assertEquals(saved.entry(), accepted.entry());
        assertEquals(slotId, accepted.entry().resource());
        assertEquals(slot.capturedAtEpochMillis(), accepted.entry().modifiedAtEpochMillis());

        Path slotFile = recoveryRootPath.resolve(slotId.value());
        assertTrue(Files.isRegularFile(slotFile));

        JsonObject envelope = JsonParser.parseString(Files.readString(slotFile)).getAsJsonObject();
        assertEquals(FileRecoveryService.SLOT_FORMAT_VERSION,
                envelope.get("slot_version").getAsInt());
        assertEquals(target.value(), envelope.get("resource").getAsString());
        assertEquals(7L, envelope.get("revision").getAsLong());
        assertEquals(123_456_789L, envelope.get("captured_at_epoch_millis").getAsLong());
        assertEquals(slot.sizeBytes(), envelope.get("size_bytes").getAsLong());
        assertEquals(slot.contentHash(), envelope.get("sha256").getAsString());
        assertTrue(envelope.get("document").isJsonObject());

        byte[] documentBytes = envelope.get("document").toString()
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(slot.sizeBytes(), documentBytes.length);
        assertEquals(slot.contentHash(), sha256Hex(documentBytes));

        assertFalse(Files.exists(resourceRootPath),
                "recovery must not create or touch the formal resource root");
        assertNoTemporaryFiles();
    }

    @Test
    void slotsAreNewestFirstAndStable() throws InterruptedException {
        Captured first = capture(1_000L, snapshot(1, document("first")),
                new ResourceId("animations/first.json"));
        Captured second = capture(3_000L, snapshot(2, document("second")),
                new ResourceId("animations/second.json"));
        Captured third = capture(2_000L, snapshot(3, document("third")),
                new ResourceId("animations/third.json"));

        List<RecoverySlot> slots = recoveryService.slots();

        assertEquals(List.of(second.slot(), third.slot(), first.slot()), slots);
        assertEquals(List.of(3_000L, 2_000L, 1_000L),
                slots.stream().map(RecoverySlot::capturedAtEpochMillis).toList());
        assertEquals(slots, recoveryService.slots(), "ordering must be stable across calls");
    }

    @Test
    void discardDeletesOnlyTheRequestedSlot() throws InterruptedException {
        Captured kept = capture(1_000L, snapshot(1, document("kept")),
                new ResourceId("animations/kept.json"));
        Captured dropped = capture(2_000L, snapshot(2, document("dropped")),
                new ResourceId("animations/dropped.json"));

        Path keptFile = recoveryRootPath.resolve(kept.saved().entry().resource().value());
        Path droppedFile = recoveryRootPath.resolve(dropped.saved().entry().resource().value());
        assertTrue(Files.isRegularFile(keptFile));
        assertTrue(Files.isRegularFile(droppedFile));

        recoveryService.discard(dropped.slot());

        assertFalse(Files.exists(droppedFile));
        assertTrue(Files.isRegularFile(keptFile));
        assertEquals(List.of(kept.slot()), recoveryService.slots());
        assertFalse(Files.exists(resourceRootPath), "discard must not touch formal resources");

        // 重复丢弃不抛异常，也不影响其他槽。
        recoveryService.discard(dropped.slot());
        assertEquals(List.of(kept.slot()), recoveryService.slots());
    }

    @Test
    void captureDoesNotOverwriteOrTouchTheFormalResource() throws IOException, InterruptedException {
        ResourceId target = new ResourceId("animations/shared.json");
        CameraAnimation formalDocument = document("formal");
        assertTrue(resourceService.save(target, formalDocument) instanceof SaveResult.Saved);

        Path formalFile = resourceRootPath.resolve(target.value());
        byte[] formalBytes = Files.readAllBytes(formalFile);
        AnimationResourceEntry formalEntry = resourceService.entry(target).orElseThrow();

        CameraAnimation recoveryDocument = document("recovery");
        Captured captured = capture(5_000L, snapshot(9, recoveryDocument), target);

        assertArrayEquals(formalBytes, Files.readAllBytes(formalFile));
        assertEquals(formalEntry, resourceService.entry(target).orElseThrow());

        LoadResult formalLoad = resourceService.load(target);
        assertTrue(formalLoad instanceof LoadResult.Loaded);
        assertEquals(formalDocument, ((LoadResult.Loaded) formalLoad).document());

        ResourceId slotId = captured.saved().entry().resource();
        assertFalse(slotId.value().startsWith("animations/"),
                "recovery slot identity must not reuse the formal resource prefix");
        assertTrue(Files.isRegularFile(recoveryRootPath.resolve(slotId.value())));
        assertEquals(List.of(formalEntry), resourceService.entries(),
                "recovery slots must not appear as formal resources");
        assertEquals(List.of(captured.slot()), recoveryService.slots());
    }

    @Test
    void malformedSlotJsonIsRejectedWithFileDiagnostic() throws IOException, InterruptedException {
        Captured captured = capture(1_000L, snapshot(1, document("broken")),
                new ResourceId("animations/broken.json"));
        Path slotFile = recoveryRootPath.resolve(captured.saved().entry().resource().value());
        Files.writeString(slotFile, "{\"slot_version\": 1,", StandardCharsets.UTF_8);

        assertTrue(recoveryService.slots().isEmpty(), "malformed slots must not be listed");

        LoadResult result = recoveryService.restore(captured.slot());

        assertRejectedLoad(result, captured.saved().entry().resource(),
                FileRecoveryService.RECOVERY_JSON_INVALID);
    }

    @Test
    void structurallyInvalidSlotIsRejectedWithoutThrowing() throws IOException, InterruptedException {
        CameraAnimation document = document("original");
        AnimationSnapshot snapshot = snapshot(4, document);
        ResourceId target = new ResourceId("animations/target.json");
        Captured captured = capture(7_000L, snapshot, target);

        JsonObject badDocument = JsonParser.parseString("{\"data_version\": 1}")
                .getAsJsonObject();
        byte[] documentBytes = badDocument.toString().getBytes(StandardCharsets.UTF_8);
        String hash = sha256Hex(documentBytes);
        ResourceId badSlotId = FileRecoveryService.slotResourceId(
                target, snapshot.revision(), 7_000L, documentBytes.length, hash);

        JsonObject envelope = new JsonObject();
        envelope.addProperty("slot_version", FileRecoveryService.SLOT_FORMAT_VERSION);
        envelope.addProperty("resource", target.value());
        envelope.addProperty("revision", snapshot.revision().value());
        envelope.addProperty("captured_at_epoch_millis", 7_000L);
        envelope.addProperty("size_bytes", documentBytes.length);
        envelope.addProperty("sha256", hash);
        envelope.add("document", badDocument);

        Path slotsDirectory = recoveryRootPath.resolve(FileRecoveryService.SLOT_DIRECTORY);
        Files.createDirectories(slotsDirectory);
        Files.writeString(recoveryRootPath.resolve(badSlotId.value()), envelope.toString(),
                StandardCharsets.UTF_8);
        Files.delete(recoveryRootPath.resolve(captured.saved().entry().resource().value()));

        RecoverySlot badSlot = new RecoverySlot(target, snapshot, 7_000L,
                documentBytes.length, hash);
        LoadResult result = recoveryService.restore(badSlot);

        assertRejectedLoad(result, badSlotId, FileRecoveryService.RECOVERY_STRUCTURE_INVALID);
        assertTrue(recoveryService.slots().isEmpty(),
                "structurally invalid slots must not be listed");
    }

    @Test
    void tamperedSlotContentIsRejectedAsContentMismatch() throws IOException, InterruptedException {
        Captured captured = capture(2_000L, snapshot(2, document("tampered")),
                new ResourceId("animations/tampered.json"));
        Path slotFile = recoveryRootPath.resolve(captured.saved().entry().resource().value());

        JsonObject envelope = JsonParser.parseString(Files.readString(slotFile))
                .getAsJsonObject();
        envelope.getAsJsonObject("document").addProperty("name", "changed-after-capture");
        Files.writeString(slotFile, envelope.toString(), StandardCharsets.UTF_8);

        LoadResult result = recoveryService.restore(captured.slot());

        assertRejectedLoad(result, captured.saved().entry().resource(),
                FileRecoveryService.RECOVERY_CONTENT_MISMATCH);
        assertTrue(recoveryService.slots().isEmpty());
    }

    @Test
    void captureEvictsTheOldestSlotsBeyondTheLimit() throws IOException, InterruptedException {
        int total = FileRecoveryService.MAX_SLOTS + 2;
        List<Captured> captures = new ArrayList<>();

        for (int index = 1; index <= total; index++) {
            String name = String.format(Locale.ROOT, "doc-%02d", index);
            captures.add(capture(index, snapshot(index, document(name)),
                    new ResourceId("animations/" + name + ".json")));
        }

        List<RecoverySlot> slots = recoveryService.slots();
        assertEquals(FileRecoveryService.MAX_SLOTS, slots.size());
        assertEquals(total, (int) slots.getFirst().capturedAtEpochMillis());
        assertEquals(3L, slots.getLast().capturedAtEpochMillis());
        assertEquals(FileRecoveryService.MAX_SLOTS,
                (int) slots.stream().map(RecoverySlot::capturedAtEpochMillis).count());

        for (int index = 0; index < total - FileRecoveryService.MAX_SLOTS; index++) {
            ResourceId evicted = captures.get(index).saved().entry().resource();
            assertFalse(Files.exists(recoveryRootPath.resolve(evicted.value())),
                    () -> "oldest slot should have been evicted: " + evicted.value());
        }

        for (int index = total - FileRecoveryService.MAX_SLOTS; index < total; index++) {
            ResourceId retained = captures.get(index).saved().entry().resource();
            assertTrue(Files.isRegularFile(recoveryRootPath.resolve(retained.value())),
                    () -> "newer slot should have been retained: " + retained.value());
        }

        assertEquals(FileRecoveryService.MAX_SLOTS, countSlotFiles());
        assertNoTemporaryFiles();
    }

    @Test
    void asyncCaptureInvokesCompletionExactlyOnceOffTheCallingThread()
            throws InterruptedException {
        ResourceId target = new ResourceId("animations/async.json");
        AnimationSnapshot snapshot = snapshot(3, document("async"));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<SaveResult> captured = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        recoveryService.capture(snapshot, target, result -> {
            calls.incrementAndGet();
            captured.set(result);
            callbackThread.set(Thread.currentThread());
            completed.countDown();
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS), "async capture did not complete");
        Thread.sleep(100);

        assertEquals(1, calls.get(), "completion must be invoked exactly once");
        assertNotSame(Thread.currentThread(), callbackThread.get(),
                "capture completion must not run on the editing thread");
        assertTrue(captured.get() instanceof SaveResult.Saved,
                () -> "async capture failed: " + captured.get());
        assertEquals(List.of(snapshot),
                recoveryService.slots().stream().map(RecoverySlot::snapshot).toList());
        assertFalse(Files.exists(resourceRootPath));
    }

    @Test
    void keepDecisionIsPersistedAndDoesNotDeleteOrRestore() throws IOException, InterruptedException {
        ResourceId target = new ResourceId("animations/keep.json");
        CameraAnimation formalDocument = document("formal");
        assertTrue(resourceService.save(target, formalDocument) instanceof SaveResult.Saved);
        Path formalFile = resourceRootPath.resolve(target.value());
        byte[] formalBytes = Files.readAllBytes(formalFile);
        AnimationResourceEntry formalEntry = resourceService.entry(target).orElseThrow();

        CameraAnimation recoveryDocument = document("recovery");
        Captured captured = capture(9_000L, snapshot(3, recoveryDocument), target);
        ResourceId slotId = captured.saved().entry().resource();

        recoveryService.decide(captured.slot(), RecoveryDecision.KEEP);

        assertEquals(Optional.of(RecoveryDecision.KEEP),
                recoveryService.recordedDecision(captured.slot()));
        assertEquals(List.of(captured.slot()), recoveryService.slots(),
                "KEEP must not delete the slot");
        assertTrue(Files.isRegularFile(recoveryRootPath.resolve(slotId.value())),
                "KEEP must not delete the slot file");
        assertArrayEquals(formalBytes, Files.readAllBytes(formalFile),
                "KEEP must not rewrite the formal resource");
        assertEquals(formalEntry, resourceService.entry(target).orElseThrow());

        // 决定元数据必须跨服务实例持久化，而不是只存在内存。
        FileRecoveryService restarted = new FileRecoveryService(
                new ResourcePathResolver(recoveryRootPath),
                new ResourcePathResolver(resourceRootPath),
                registries(),
                () -> 0L);
        assertEquals(Optional.of(RecoveryDecision.KEEP),
                restarted.recordedDecision(captured.slot()));

        // KEEP 本身不执行恢复；显式 restore 仍能读到捕获的版本，且不发布正式文档。
        LoadResult restored = recoveryService.restore(captured.slot());
        assertTrue(restored instanceof LoadResult.Loaded, () -> "restore failed: " + restored);
        assertEquals(recoveryDocument, ((LoadResult.Loaded) restored).document());

        LoadResult formalLoad = resourceService.load(target);
        assertTrue(formalLoad instanceof LoadResult.Loaded);
        assertEquals(formalDocument, ((LoadResult.Loaded) formalLoad).document(),
                "recovery must not publish the captured version over the formal document");
        assertNoTemporaryFiles();
    }

    @Test
    void rejectsOverlappingRecoveryAndFormalResourceRoots() {
        ResourcePathResolver formal = new ResourcePathResolver(tempDir.resolve("shared"));
        ResourcePathResolver nested = new ResourcePathResolver(tempDir.resolve("shared/recovery"));

        assertThrows(IllegalArgumentException.class,
                () -> new FileRecoveryService(nested, formal, registries()));
        assertThrows(IllegalArgumentException.class,
                () -> new FileRecoveryService(formal, formal, registries()));
    }

    private Captured capture(long capturedAt, AnimationSnapshot snapshot, ResourceId target)
            throws InterruptedException {
        clock.set(capturedAt);

        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<SaveResult> result = new AtomicReference<>();

        recoveryService.capture(snapshot, target, value -> {
            calls.incrementAndGet();
            result.set(value);
            completed.countDown();
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS), "capture did not complete");
        assertEquals(1, calls.get(), "completion must be invoked exactly once");
        assertTrue(result.get() instanceof SaveResult.Saved,
                () -> "capture failed: " + result.get());

        SaveResult.Saved saved = (SaveResult.Saved) result.get();
        RecoverySlot slot = recoveryService.slots().stream()
                .filter(candidate -> candidate.resource().equals(target)
                        && candidate.snapshot().equals(snapshot))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "captured slot was not listed: " + saved.entry().resource().value()));

        return new Captured(saved, slot);
    }

    private long countSlotFiles() throws IOException {
        Path slotsDirectory = recoveryRootPath.resolve(FileRecoveryService.SLOT_DIRECTORY);

        try (Stream<Path> files = Files.list(slotsDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .count();
        }
    }

    private static void assertRejectedLoad(LoadResult result, ResourceId expectedResource,
                                           DiagnosticCode expectedCode) {
        assertTrue(result instanceof LoadResult.Rejected,
                () -> "expected rejection: " + result);

        DiagnosticReport report = ((LoadResult.Rejected) result).report();
        assertEquals(expectedResource, report.resource());
        assertFalse(report.diagnostics().isEmpty());
        assertTrue(report.hasErrors());
        assertEquals(expectedCode, report.diagnostics().getFirst().code());

        for (Diagnostic diagnostic : report.diagnostics()) {
            assertTrue(diagnostic.scope() instanceof DiagnosticScope.File,
                    "recovery diagnostics must be File-scoped");
            assertEquals(expectedResource,
                    ((DiagnosticScope.File) diagnostic.scope()).resource());
            assertEquals(Severity.ERROR, diagnostic.severity());
            assertNull(diagnostic.fix());
            assertNull(diagnostic.location());
        }
    }

    private void assertNoTemporaryFiles() throws IOException {
        if (!Files.exists(recoveryRootPath)) {
            return;
        }

        try (Stream<Path> files = Files.walk(recoveryRootPath)) {
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

    private record Captured(SaveResult.Saved saved, RecoverySlot slot) {
    }
}
