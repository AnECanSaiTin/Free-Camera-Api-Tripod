package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.SessionSwitch;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceEntry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.AnimationResourceService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.DiagnosticReport;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.LoadResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.SaveResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.storage.FileAnimationResourceService;
import cn.anecansaitin.free_camera_api_tripod_v2.core.storage.ResourcePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 载入结果到文档发布边界的衔接。
class CoreAnimationLoaderTest {

    @TempDir
    Path tempDir;

    private static final ResourceId RESOURCE = new ResourceId("animations/test.json");
    private static final AnimationResourceEntry ENTRY =
            new AnimationResourceEntry(RESOURCE, 2, "hash", 0);

    private static CameraAnimation document(String name) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        return CameraAnimation.create(name, rig, new TrackId("source"));
    }

    @Test
    void publishesLoadedDocumentThroughSwitchDocument() {
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("old"), candidate -> List.of());
        CoreAnimationLoader loader = new CoreAnimationLoader(store);

        CoreAnimationLoader.DocumentLoadOutcome outcome = loader.load(
                new LoadResult.Loaded(document("loaded"), ENTRY),
                Revision.INITIAL, SessionSwitch.LOAD);

        assertTrue(outcome instanceof CoreAnimationLoader.DocumentLoadOutcome.Published);
        CoreAnimationLoader.DocumentLoadOutcome.Published published =
                (CoreAnimationLoader.DocumentLoadOutcome.Published) outcome;
        assertEquals(new Revision(1), published.snapshot().revision());
        assertEquals("loaded", published.snapshot().document().name());
        assertEquals(ENTRY, published.entry());
        assertEquals("loaded", store.snapshot().document().name());
    }

    @Test
    void loadsAndPublishesThroughResourceServiceFacade() {
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("old"), candidate -> List.of());
        CoreAnimationLoader loader = new CoreAnimationLoader(store);

        AnimationResourceService resources = new AnimationResourceService() {
            @Override
            public LoadResult load(ResourceId resource) {
                return new LoadResult.Loaded(document("loaded"), ENTRY);
            }

            @Override
            public SaveResult save(ResourceId resource, CameraAnimation document) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void saveAsync(ResourceId resource, CameraAnimation document,
                                  Consumer<SaveResult> completion) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<AnimationResourceEntry> entry(ResourceId resource) {
                return Optional.of(ENTRY);
            }

            @Override
            public List<AnimationResourceEntry> entries() {
                return List.of(ENTRY);
            }
        };

        CoreAnimationLoader.DocumentLoadOutcome outcome =
                loader.load(resources, RESOURCE, Revision.INITIAL, SessionSwitch.LOAD);

        assertTrue(outcome instanceof CoreAnimationLoader.DocumentLoadOutcome.Published);
        assertEquals("loaded", store.snapshot().document().name());
        assertEquals(new Revision(1), store.snapshot().revision());
    }

    @Test
    void fileServiceSaveLoadAndPublishRoundTrip() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        anchors.freeze();
        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        tracks.freeze();
        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        samples.freeze();

        FileAnimationResourceService resources = new FileAnimationResourceService(
                new ResourcePathResolver(tempDir.resolve("root")),
                new DocumentRegistries(anchors, tracks, samples));
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("old"), candidate -> List.of());
        CoreAnimationLoader loader = new CoreAnimationLoader(store);

        assertTrue(resources.save(RESOURCE, document("file-roundtrip")) instanceof SaveResult.Saved);
        CoreAnimationLoader.DocumentLoadOutcome outcome = loader.load(
                resources, RESOURCE, Revision.INITIAL, SessionSwitch.LOAD);

        assertTrue(outcome instanceof CoreAnimationLoader.DocumentLoadOutcome.Published);
        assertEquals("file-roundtrip", store.snapshot().document().name());
        assertEquals(new Revision(1), store.snapshot().revision());
    }

    @Test
    void staleLoadDoesNotPublish() {
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("old"), candidate -> List.of());
        CoreAnimationLoader loader = new CoreAnimationLoader(store);

        CoreAnimationLoader.DocumentLoadOutcome outcome = loader.load(
                new LoadResult.Loaded(document("loaded"), ENTRY),
                new Revision(4), SessionSwitch.LOAD);

        assertTrue(outcome instanceof CoreAnimationLoader.DocumentLoadOutcome.Rejected);
        assertEquals(DiagnosticCode.STALE_REVISION,
                ((CoreAnimationLoader.DocumentLoadOutcome.Rejected) outcome)
                        .diagnostics().getFirst().code());
        assertEquals("old", store.snapshot().document().name());
        assertEquals(Revision.INITIAL, store.snapshot().revision());
    }

    @Test
    void resourceRejectionIsReturnedWithoutPublishing() {
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("old"), candidate -> List.of());
        CoreAnimationLoader loader = new CoreAnimationLoader(store);
        Diagnostic fileError = new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("bad file"),
                new DiagnosticScope.File(RESOURCE), null, null);

        CoreAnimationLoader.DocumentLoadOutcome outcome = loader.load(
                new LoadResult.Rejected(new DiagnosticReport(RESOURCE, List.of(fileError))),
                Revision.INITIAL, SessionSwitch.LOAD);

        assertTrue(outcome instanceof CoreAnimationLoader.DocumentLoadOutcome.Rejected);
        assertEquals(fileError,
                ((CoreAnimationLoader.DocumentLoadOutcome.Rejected) outcome).diagnostics().getFirst());
        assertEquals("old", store.snapshot().document().name());
    }

    @Test
    void validatorFailureRejectsLoadedDocument() {
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("old"), candidate -> List.of(new Diagnostic(
                        DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                        LocalizedMessage.literal("invalid"), new DiagnosticScope.Document(Revision.INITIAL),
                        null, null)));
        CoreAnimationLoader loader = new CoreAnimationLoader(store);

        CoreAnimationLoader.DocumentLoadOutcome outcome = loader.load(
                new LoadResult.Loaded(document("loaded"), ENTRY),
                Revision.INITIAL, SessionSwitch.LOAD);

        assertTrue(outcome instanceof CoreAnimationLoader.DocumentLoadOutcome.Rejected);
        assertEquals("old", store.snapshot().document().name());
    }
}
