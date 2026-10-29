package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RenamableTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentListener;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditSubmission;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.SessionSwitch;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.UndoHistory;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenAnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenSampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry.FrozenTrackTypeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 文档存储的发布顺序、历史、换档与通知重入边界。
class CoreAnimationDocumentStoreTest {

    private static final LocalizedMessage RENAME = LocalizedMessage.literal("rename");

    private static CameraAnimation document(String name) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        return CameraAnimation.create(name, rig, new TrackId("source"));
    }

    private static EditSubmission rename(String name, Revision revision) {
        return new EditSubmission(new EditOp.RenameDocument(name), revision, RENAME);
    }

    private static CoreAnimationDocumentStore store() {
        return CoreAnimationDocumentStore.withFixedValidator(document("doc"), candidate -> List.of());
    }

    @Test
    void appliesEditAndPublishesNewSnapshotInOrder() {
        CoreAnimationDocumentStore store = store();
        AtomicReference<AnimationSnapshot> notified = new AtomicReference<>();
        store.addListener(notified::set);

        EditResult result = store.submit(rename("renamed", Revision.INITIAL));

        EditResult.Applied applied = (EditResult.Applied) result;
        assertEquals(new Revision(1), applied.snapshot().revision());
        assertEquals("renamed", applied.snapshot().document().name());
        assertEquals(applied.snapshot(), store.snapshot());
        assertEquals(applied.snapshot(), notified.get());
        assertEquals(2, store.history().entries().size());
        assertTrue(store.history().canUndo());
        assertFalse(store.history().canRedo());
    }

    @Test
    void staleSubmissionDoesNotChangeDocumentOrRevision() {
        CoreAnimationDocumentStore store = store();

        EditResult result = store.submit(rename("renamed", new Revision(9)));

        EditResult.Rejected rejected = (EditResult.Rejected) result;
        assertEquals(DiagnosticCode.STALE_REVISION, rejected.diagnostics().getFirst().code());
        assertEquals(new Revision(0), store.snapshot().revision());
        assertEquals("doc", store.snapshot().document().name());
        assertEquals(1, store.history().entries().size());
    }

    @Test
    void noOpSubmissionDoesNotCreateSnapshotOrHistory() {
        CoreAnimationDocumentStore store = store();

        EditResult result = store.submit(rename("doc", Revision.INITIAL));

        assertTrue(result instanceof EditResult.Applied);
        assertSame(store.snapshot(), ((EditResult.Applied) result).snapshot());
        assertEquals(new Revision(0), store.snapshot().revision());
        assertEquals(1, store.history().entries().size());
    }

    @Test
    void validatorErrorRejectsWholeCandidate() {
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(document("doc"),
                candidate -> List.of(new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                        LocalizedMessage.literal("broken"), new DiagnosticScope.Document(Revision.INITIAL),
                        null, null)));

        EditResult result = store.submit(rename("renamed", Revision.INITIAL));

        assertTrue(result instanceof EditResult.Rejected);
        assertEquals("doc", store.snapshot().document().name());
        assertEquals(new Revision(0), store.snapshot().revision());
    }

    @Test
    void reentrantSubmitIsRejectedAndDeferredSubmitRunsAfterNotification() {
        CoreAnimationDocumentStore store = store();
        AtomicReference<EditResult> reentrant = new AtomicReference<>();
        AtomicReference<EditResult> deferred = new AtomicReference<>();
        AtomicReference<Boolean> fired = new AtomicReference<>(false);

        store.addListener(snapshot -> {
            if (!fired.getAndSet(true)) {
                reentrant.set(store.submit(rename("reentrant", snapshot.revision())));
                store.submitAfterNotifications(rename("deferred", snapshot.revision()), deferred::set);
            }
        });

        EditResult first = store.submit(rename("first", Revision.INITIAL));

        assertTrue(first instanceof EditResult.Applied);
        assertEquals(DiagnosticCode.REENTRANT_SUBMIT,
                ((EditResult.Rejected) reentrant.get()).diagnostics().getFirst().code());
        assertTrue(deferred.get() instanceof EditResult.Applied);
        assertEquals(new Revision(2), store.snapshot().revision());
        assertEquals("deferred", store.snapshot().document().name());
    }

    @Test
    void switchAndHistoryWritesAreRejectedDuringNotification() {
        CoreAnimationDocumentStore store = store();
        AtomicReference<EditResult> switchResult = new AtomicReference<>();
        AtomicReference<EditResult> undoResult = new AtomicReference<>();

        store.addListener(snapshot -> {
            if (snapshot.revision().value() == 1) {
                switchResult.set(store.switchDocument(document("other"),
                        snapshot.revision(), SessionSwitch.SWITCH));
                undoResult.set(store.undo(snapshot.revision()));
            }
        });

        store.submit(rename("first", Revision.INITIAL));

        assertEquals(DiagnosticCode.REENTRANT_SUBMIT,
                ((EditResult.Rejected) switchResult.get()).diagnostics().getFirst().code());
        assertEquals(DiagnosticCode.REENTRANT_SUBMIT,
                ((EditResult.Rejected) undoResult.get()).diagnostics().getFirst().code());
        assertEquals("first", store.snapshot().document().name());
    }

    @Test
    void undoAndRedoCreateFreshRevisions() {
        CoreAnimationDocumentStore store = store();
        store.submit(rename("first", Revision.INITIAL));
        store.submit(rename("second", new Revision(1)));

        EditResult undone = store.undo(new Revision(2));
        assertEquals(new Revision(3), ((EditResult.Applied) undone).snapshot().revision());
        assertEquals("first", store.snapshot().document().name());
        assertTrue(store.history().canRedo());

        EditResult redone = store.redo(new Revision(3));
        assertEquals(new Revision(4), ((EditResult.Applied) redone).snapshot().revision());
        assertEquals("second", store.snapshot().document().name());
    }

    @Test
    void switchClearsHistoryAndInvalidatesOldRevisions() {
        CoreAnimationDocumentStore store = store();
        store.submit(rename("first", Revision.INITIAL));

        EditResult switched = store.switchDocument(document("other"), new Revision(1), SessionSwitch.LOAD);

        assertEquals(new Revision(2), ((EditResult.Applied) switched).snapshot().revision());
        assertEquals("other", store.snapshot().document().name());
        assertEquals(1, store.history().entries().size());
        assertFalse(store.history().canUndo());
        assertFalse(store.history().canRedo());

        EditResult stale = store.submit(rename("old", Revision.INITIAL));
        assertEquals(DiagnosticCode.STALE_REVISION,
                ((EditResult.Rejected) stale).diagnostics().getFirst().code());
    }

    @Test
    void batchIsAtomicWhenOneOperationFails() {
        CoreAnimationDocumentStore store = store();

        EditResult result = store.submit(new EditSubmission(
                new EditOp.Batch(List.of(
                        new EditOp.RenameDocument("changed"),
                        new EditOp.RemoveChannel(new RigId("rig"), new TrackId("missing")))),
                Revision.INITIAL, LocalizedMessage.literal("batch")));

        assertTrue(result instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.TARGET_NOT_FOUND,
                ((EditResult.Rejected) result).diagnostics().getFirst().code());
        assertEquals("doc", store.snapshot().document().name());
        assertEquals(new Revision(0), store.snapshot().revision());
    }

    @Test
    void historyEntriesAreNewestFirst() {
        CoreAnimationDocumentStore store = store();
        store.submit(rename("first", Revision.INITIAL));
        store.submit(rename("second", new Revision(1)));

        List<UndoHistory.Entry> entries = store.history().entries();

        assertEquals(3, entries.size());
        assertEquals("second", entries.getFirst().snapshot().document().name());
        assertEquals("first", entries.get(1).snapshot().document().name());
        assertEquals("doc", entries.get(2).snapshot().document().name());
    }

    @Test
    void renamesExtensionTrackThroughItsOwnTypeContract() {
        TestExtensionTrack track = new TestExtensionTrack(new TrackId("ext"), "before");
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("doc").withTracks(List.of(track)), candidate -> List.of());

        EditResult result = store.submit(new EditSubmission(
                new EditOp.RenameTrack(track.id(), "after"),
                Revision.INITIAL, LocalizedMessage.literal("rename extension")));

        assertTrue(result instanceof EditResult.Applied);
        TestExtensionTrack renamed = (TestExtensionTrack) store.snapshot().document().tracks().getFirst();
        assertEquals("after", renamed.name());
        assertEquals(track.id(), renamed.id());
        assertEquals(track.type(), renamed.type());
    }

    @Test
    void rejectsRenameForExtensionWithoutCapability() {
        FixedNameTrack track = new FixedNameTrack(new TrackId("fixed"), "fixed");
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("doc").withTracks(List.of(track)), candidate -> List.of());

        EditResult result = store.submit(new EditSubmission(
                new EditOp.RenameTrack(track.id(), "after"),
                Revision.INITIAL, LocalizedMessage.literal("rename fixed")));

        assertTrue(result instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.INVALID_EDIT,
                ((EditResult.Rejected) result).diagnostics().getFirst().code());
        FixedNameTrack unchanged = (FixedNameTrack) store.snapshot().document().tracks().getFirst();
        assertEquals("fixed", unchanged.name());
    }

    @Test
    void rejectsExtensionRenameThatChangesStableIdentity() {
        BadExtensionTrack track = new BadExtensionTrack(new TrackId("ext"), "before");
        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withFixedValidator(
                document("doc").withTracks(List.of(track)), candidate -> List.of());

        EditResult result = store.submit(new EditSubmission(
                new EditOp.RenameTrack(track.id(), "after"),
                Revision.INITIAL, LocalizedMessage.literal("bad rename")));

        assertTrue(result instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.INVALID_EDIT,
                ((EditResult.Rejected) result).diagnostics().getFirst().code());
        assertEquals("doc", store.snapshot().document().name());
    }

    @Test
    void unifiedValidationRejectsInvalidCandidateBeforePublish() {
        FrozenAnchorTypeRegistry anchors = new FrozenAnchorTypeRegistry();
        anchors.freeze();
        FrozenTrackTypeRegistry tracks = new FrozenTrackTypeRegistry();
        tracks.freeze();
        FrozenSampleTypeRegistry samples = new FrozenSampleTypeRegistry();
        samples.freeze();

        CoreAnimationDocumentStore store = CoreAnimationDocumentStore.withUnifiedValidation(
                document("doc"), new DocumentRegistries(anchors, tracks, samples));

        CameraRig first = CameraRig.create(new RigId("same"), "first", List.of(),
                new TrackId("a-aim"), new TrackId("a-active"));
        CameraRig second = CameraRig.create(new RigId("same"), "second", List.of(),
                new TrackId("b-aim"), new TrackId("b-active"));
        CameraAnimation duplicateRigs = CameraAnimation.create("dup", first, new TrackId("source"))
                .withRigs(List.of(first, second));

        EditResult result = store.submit(new EditSubmission(
                new EditOp.ReplaceDocument(duplicateRigs),
                Revision.INITIAL, LocalizedMessage.literal("replace")));

        assertTrue(result instanceof EditResult.Rejected);
        assertEquals(DiagnosticCode.INVALID_EDIT,
                ((EditResult.Rejected) result).diagnostics().getFirst().code());
        assertEquals("doc", store.snapshot().document().name());
    }

    @Test
    void listenerCanBeRemoved() {
        CoreAnimationDocumentStore store = store();
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        DocumentListener listener = snapshot -> called.set(true);

        store.addListener(listener);
        store.removeListener(listener);
        store.submit(rename("renamed", Revision.INITIAL));

        assertFalse(called.get());
    }

    private record FixedNameTrack(TrackId id, String name) implements AnimationTrack.ExtensionTrack {

        @Override
        public net.minecraft.resources.Identifier type() {
            return net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "fixed_name");
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }
    }

    private record BadExtensionTrack(TrackId id, String name)
            implements AnimationTrack.ExtensionTrack, RenamableTrack {

        @Override
        public BadExtensionTrack withName(String newName) {
            return new BadExtensionTrack(new TrackId(id.value() + "-changed"), newName);
        }

        @Override
        public net.minecraft.resources.Identifier type() {
            return net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "bad_extension");
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }
    }

    private record TestExtensionTrack(TrackId id, String name)
            implements AnimationTrack.ExtensionTrack, RenamableTrack {

        @Override
        public TestExtensionTrack withName(String newName) {
            return new TestExtensionTrack(id, newName);
        }

        @Override
        public net.minecraft.resources.Identifier type() {
            return net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "extension");
        }

        @Override
        public float duration() {
            return 0f;
        }

        @Override
        public int keyCount() {
            return 0;
        }
    }
}
