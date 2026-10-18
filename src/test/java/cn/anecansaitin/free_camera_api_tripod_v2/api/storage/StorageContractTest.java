package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 存储与恢复契约的纯结构约束（主设计 §8.2、§8.4）。
class StorageContractTest {

    private static ResourceId resource() {
        return new ResourceId("animations/test.json");
    }

    private static AnimationResourceEntry entry() {
        return new AnimationResourceEntry(resource(), 128L, "sha256:test", 1L);
    }

    private static Diagnostic errorDiagnostic() {
        return new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("broken"), new DiagnosticScope.File(resource()), null, null);
    }

    private static AnimationSnapshot snapshot() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));

        return new AnimationSnapshot(new Revision(1),
                CameraAnimation.create("doc", rig, new TrackId("source")));
    }

    @Test
    void resourceIdsAndEntriesValidateMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceId(" "));
        assertEquals("animations/test.json", resource().value());
        assertEquals(128L, entry().sizeBytes());

        assertThrows(IllegalArgumentException.class,
                () -> new AnimationResourceEntry(resource(), -1, "hash", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AnimationResourceEntry(resource(), 0, " ", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AnimationResourceEntry(resource(), 0, "hash", -1));
    }

    @Test
    void diagnosticReportCopiesAndReportsErrors() {
        List<Diagnostic> diagnostics = new ArrayList<>(List.of(errorDiagnostic()));
        DiagnosticReport report = new DiagnosticReport(resource(), diagnostics);
        diagnostics.clear();

        assertEquals(1, report.diagnostics().size());
        assertTrue(report.hasErrors());
    }

    @Test
    void loadAndSaveResultsRejectEmptyReportsAndKeepParts() {
        DiagnosticReport report = new DiagnosticReport(resource(), List.of(errorDiagnostic()));
        LoadResult.Loaded loaded = new LoadResult.Loaded(snapshot().document(), entry());

        assertEquals(entry(), loaded.entry());
        assertEquals(1, ((LoadResult.Rejected) new LoadResult.Rejected(report)).report().diagnostics().size());
        assertThrows(IllegalArgumentException.class,
                () -> new LoadResult.Rejected(new DiagnosticReport(resource(), List.of())));

        SaveResult.Saved saved = new SaveResult.Saved(entry());
        assertEquals(entry(), saved.entry());
        assertThrows(IllegalArgumentException.class,
                () -> new SaveResult.Rejected(new DiagnosticReport(resource(), List.of())));
    }

    @Test
    void recoverySlotBindsSnapshotAndMetadata() {
        RecoverySlot slot = new RecoverySlot(resource(), snapshot(), 10L, 64L, "sha256:slot");

        assertEquals(1, slot.snapshot().revision().value());
        assertEquals(64L, slot.sizeBytes());
        assertThrows(IllegalArgumentException.class,
                () -> new RecoverySlot(resource(), snapshot(), -1, 0, "hash"));
        assertThrows(IllegalArgumentException.class,
                () -> new RecoverySlot(resource(), snapshot(), 0, -1, "hash"));
        assertThrows(IllegalArgumentException.class,
                () -> new RecoverySlot(resource(), snapshot(), 0, 0, " "));
    }

    @Test
    void recoveryDecisionKeepsExplicitThreeStates() {
        assertEquals(3, RecoveryDecision.values().length);
        assertEquals("RESTORE", RecoveryDecision.RESTORE.name());
    }
}
