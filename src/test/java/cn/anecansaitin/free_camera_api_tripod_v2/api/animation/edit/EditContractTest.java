package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.EditTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        DiagnosticScope.File fileScope = new DiagnosticScope.File(new ResourceId("test-resource"));
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
    void documentScopeRequiresRevisionAndAllowsObjectLocationWithoutFix() {
        assertThrows(NullPointerException.class, () -> new DiagnosticScope.Document(null));

        DiagnosticScope.Document scope = new DiagnosticScope.Document(new Revision(2));
        Diagnostic diagnostic = new Diagnostic(
                DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR, LocalizedMessage.literal("x"),
                scope, new Diagnostic.Location(new RigId("rig"), ""), null);

        assertEquals(scope, diagnostic.scope());
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

    @Test
    void stateKeyEditAndFieldTargetAreTyped() {
        TrackId track = track("state");
        KeyId key = key("k");
        EditOp.SetStateKey operation = new EditOp.SetStateKey(track, key, AimState.off());

        assertEquals(AimState.off(), operation.value());
        assertThrows(NullPointerException.class,
                () -> new EditOp.SetStateKey(track, key, null));

        EditTarget.Field field = new EditTarget.Field(new EditTarget.Key(track, key), "value");
        assertEquals("value", field.fieldPath());
        assertThrows(IllegalArgumentException.class,
                () -> new EditTarget.Field(new EditTarget.Document(), " "));
        assertThrows(IllegalArgumentException.class,
                () -> new EditTarget.Field(field, "nested"));
        assertThrows(IllegalArgumentException.class, () -> new ResourceId(" "));
    }

    @Test
    void trialResultEnforcesStatusDiagnosticsAndReport() {
        TrialResult.TrialReport passedReport = new TrialResult.TrialReport(
                0f, 1f, "fixed-step", 10, 7L, "frozen facts", null);
        Diagnostic error = new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal("error"), new DiagnosticScope.File(new ResourceId("test")), null, null);

        TrialResult passed = new TrialResult(TrialStatus.PASSED, passedReport, List.of());
        assertFalse(passed.hasError());

        assertThrows(IllegalArgumentException.class,
                () -> new TrialResult(TrialStatus.PASSED, passedReport, List.of(error)));
        assertThrows(IllegalArgumentException.class,
                () -> new TrialResult(TrialStatus.FAILED, passedReport, List.of()));

        TrialResult failed = new TrialResult(TrialStatus.FAILED, passedReport, List.of(error));
        assertTrue(failed.hasError());

        assertThrows(IllegalArgumentException.class,
                () -> new TrialResult(TrialStatus.INCOMPLETE, passedReport, List.of()));

        TrialResult.TrialReport incompleteReport = new TrialResult.TrialReport(
                0f, 1f, "fixed-step", 10, 7L, "frozen facts",
                LocalizedMessage.literal("budget exhausted"));
        TrialResult incomplete = new TrialResult(TrialStatus.INCOMPLETE, incompleteReport, List.of());
        assertEquals(TrialStatus.INCOMPLETE, incomplete.status());
    }
}
