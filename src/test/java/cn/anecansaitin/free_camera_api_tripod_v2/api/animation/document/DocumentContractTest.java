package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 文档层契约的纯净值与防御性复制（主设计 §3.3、§4.5、§5）。
class DocumentContractTest {

    private static CameraAnimation document() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));

        return CameraAnimation.create("doc", rig, new TrackId("source"));
    }

    @Test
    void revisionIsMonotonicAndNonNegative() {
        Revision first = Revision.INITIAL;
        Revision second = first.next();

        assertTrue(second.isAfter(first));
        assertEquals(new Revision(1), second);
        assertThrows(IllegalArgumentException.class, () -> new Revision(-1));
    }

    @Test
    void snapshotRequiresDocumentAndRevision() {
        CameraAnimation document = document();

        assertEquals(new Revision(3),
                new AnimationSnapshot(new Revision(3), document).revision());
        assertThrows(NullPointerException.class, () -> new AnimationSnapshot(null, document));
        assertThrows(NullPointerException.class, () -> new AnimationSnapshot(new Revision(0), null));
    }

    @Test
    void selectionCopiesInputLists() {
        List<RigId> rigs = new ArrayList<>(List.of(new RigId("rig")));

        Selection selection = new Selection(rigs, List.of(), List.of(), List.of());
        rigs.clear();

        assertEquals(1, selection.rigs().size());
        assertEquals(false, selection.isEmpty());
        assertTrue(Selection.EMPTY.isEmpty());
    }

    @Test
    void previewStateCopiesDiagnosticsAndAllowsMissingCandidate() {
        List<Diagnostic> diagnostics = new ArrayList<>(List.of(new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE,
                Severity.ERROR,
                Component.literal("broken"),
                null,
                null)));

        PreviewState preview = new PreviewState(null, diagnostics, false);
        diagnostics.clear();

        assertEquals(1, preview.diagnostics().size());
        assertEquals(false, preview.hasCandidate());
    }

    @Test
    void editTransactionRejectsNegativeIdAndMissingParts() {
        assertThrows(IllegalArgumentException.class, () -> new EditTransaction(
                -1, Revision.INITIAL, new EditTarget.Document(), Component.literal("x")));

        assertThrows(NullPointerException.class, () -> new EditTransaction(
                0, null, new EditTarget.Document(), Component.literal("x")));
    }
}
