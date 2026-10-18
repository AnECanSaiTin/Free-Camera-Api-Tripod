package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// 编辑契约的纯结构约束（主设计 §4.1、§4.4）。
class EditContractTest {

    private static TrackId track(String value) {
        return new TrackId(value);
    }

    private static KeyId key(String value) {
        return new KeyId(value);
    }

    @Test
    void keyMoveValidatesTimeAndNormalizesNegativeZero() {
        assertEquals(0f, Float.floatToIntBits(
                new EditOp.KeyMove(track("t"), key("k"), -0.0f).time()));

        assertThrows(IllegalArgumentException.class,
                () -> new EditOp.KeyMove(track("t"), key("k"), Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new EditOp.KeyMove(track("t"), key("k"), -1f));
    }

    @Test
    void batchCopiesOperationsAndDoesNotKeepCallerContainer() {
        List<EditOp> input = new ArrayList<>(List.of(new EditOp.RenameDocument("A")));

        EditOp.Batch batch = new EditOp.Batch(input);
        input.clear();

        assertEquals(1, batch.operations().size());
    }

    @Test
    void rejectedResultRequiresAtLeastOneDiagnostic() {
        assertThrows(IllegalArgumentException.class, () -> new EditResult.Rejected(List.of()));
    }

    @Test
    void diagnosticCodeIsStableAndNonBlank() {
        DiagnosticCode code = new DiagnosticCode("free_camera_api_tripod:broken_reference");

        assertEquals("free_camera_api_tripod:broken_reference", code.value());
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticCode(" "));
    }

    @Test
    void diagnosticFixCarriesBaseRevision() {
        Diagnostic.Fix fix = new Diagnostic.Fix(
                LocalizedMessage.literal("fix"),
                new EditOp.RenameDocument("B"),
                new Revision(3));

        assertEquals(new Revision(3), fix.baseRevision());
        assertEquals(new EditOp.RenameDocument("B"), fix.operation());
        assertThrows(NullPointerException.class,
                () -> new Diagnostic.Fix(LocalizedMessage.literal("x"), null, new Revision(0)));
    }

    @Test
    void diagnosticWithFixBindsScopeRevision() {
        EditOp operation = new EditOp.RenameDocument("B");
        DiagnosticScope.Document scope = new DiagnosticScope.Document(new Revision(1));

        Diagnostic accepted = new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR, LocalizedMessage.literal("x"),
                scope, null, new Diagnostic.Fix(LocalizedMessage.literal("fix"), operation, new Revision(1)));
        assertEquals(scope, accepted.scope());

        assertThrows(IllegalArgumentException.class, () -> new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR, LocalizedMessage.literal("x"),
                scope, null, new Diagnostic.Fix(LocalizedMessage.literal("fix"), operation, new Revision(2))));
    }

    @Test
    void fileScopeRejectsFixAndObjectLocation() {
        DiagnosticScope.File fileScope = new DiagnosticScope.File("test-resource");
        EditOp operation = new EditOp.RenameDocument("B");

        assertThrows(IllegalArgumentException.class, () -> new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR, LocalizedMessage.literal("x"),
                fileScope, null, new Diagnostic.Fix(LocalizedMessage.literal("fix"), operation, new Revision(1))));

        assertThrows(IllegalArgumentException.class, () -> new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR, LocalizedMessage.literal("x"),
                fileScope, new Diagnostic.Location(new RigId("rig"), ""), null));

        Diagnostic fileDiagnostic = new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR, LocalizedMessage.literal("x"),
                fileScope, new Diagnostic.Location(null, "dataVersion"), null);
        assertEquals(fileScope, fileDiagnostic.scope());
    }

    @Test
    void editTargetRejectsBlankSymbolNames() {
        assertThrows(IllegalArgumentException.class, () -> new EditTarget.Variable(" "));
        assertThrows(IllegalArgumentException.class, () -> new EditTarget.Function(""));
    }

    @Test
    void editTargetIsSealedAcrossStableKinds() {
        assertEquals(new EditTarget.Document(), new EditTarget.Document());
        assertEquals(new EditTarget.Rig(new RigId("r")), new EditTarget.Rig(new RigId("r")));
    }
}
