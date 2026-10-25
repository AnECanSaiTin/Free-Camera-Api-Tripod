package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 依赖图：TrackRef / 变量 / 函数展开、未定义引用与环。
class CoreDependencyGraphTest {

    private static final TrackId A = new TrackId("a");
    private static final TrackId B = new TrackId("b");

    @Test
    void variableTrackRefBecomesTrackDependency() {
        CameraAnimation document = document(
                formulaTrack(A, "v"),
                constantTrack(B),
                new Symbols(List.of(new Variable("v", new TrackRef(B))), List.of()));

        CoreDependencyGraph graph = graph(document);

        assertEquals(java.util.Set.of(B), graph.dependenciesOf(A));
        assertTrue(graph.diagnostics().isEmpty(), () -> "unexpected: " + graph.diagnostics());
    }

    @Test
    void functionBodyTrackRefBecomesDependencyAndParametersAreLocal() {
        CameraAnimation document = document(
                formulaTrack(A, "f(1)"),
                constantTrack(B),
                new Symbols(
                        List.of(new Variable("w", new TrackRef(B))),
                        List.of(new CustomFunction("f", List.of("x"), "w + x"))));

        CoreDependencyGraph graph = graph(document);

        assertEquals(java.util.Set.of(B), graph.dependenciesOf(A));
        assertTrue(graph.diagnostics().isEmpty(), () -> "unexpected: " + graph.diagnostics());
    }

    @Test
    void unknownVariableIsReported() {
        CameraAnimation document = document(
                formulaTrack(A, "missing"),
                constantTrack(B),
                Symbols.EMPTY);

        CoreDependencyGraph graph = graph(document);

        assertTrue(graph.dependenciesOf(A).isEmpty());
        assertEquals(1, graph.diagnostics().size());
        assertTrue(graph.diagnostics().getFirst().message().toString().contains("unknown variable"));
    }

    @Test
    void trackCycleIsReported() {
        CameraAnimation document = document(
                formulaTrack(A, "va"),
                formulaTrack(B, "vb"),
                new Symbols(List.of(
                        new Variable("va", new TrackRef(B)),
                        new Variable("vb", new TrackRef(A))), List.of()));

        CoreDependencyGraph graph = graph(document);

        assertEquals(java.util.Set.of(B), graph.dependenciesOf(A));
        assertEquals(java.util.Set.of(A), graph.dependenciesOf(B));
        assertTrue(graph.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().toString().contains("cycle")), () -> "unexpected: " + graph.diagnostics());
    }

    @Test
    void functionArityMismatchIsReported() {
        CameraAnimation document = document(
                formulaTrack(A, "f(1, 2)"),
                constantTrack(B),
                new Symbols(List.of(), List.of(
                        new CustomFunction("f", List.of("x"), "x"))));

        CoreDependencyGraph graph = graph(document);

        assertTrue(graph.dependenciesOf(A).isEmpty());
        assertTrue(graph.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().toString().contains("expects 1")));
    }

    @Test
    void noFormulasMeansNoDependencies() {
        CameraAnimation document = document(
                constantTrack(A),
                constantTrack(B),
                Symbols.EMPTY);

        CoreDependencyGraph graph = graph(document);

        assertTrue(graph.dependenciesOf(A).isEmpty());
        assertTrue(graph.dependenciesOf(B).isEmpty());
        assertTrue(graph.diagnostics().isEmpty());
    }

    private static CoreDependencyGraph graph(CameraAnimation document) {
        return CoreDependencyGraph.build(new AnimationSnapshot(new Revision(2), document));
    }

    private static CameraAnimation document(CurveTrack first, CurveTrack second, Symbols symbols) {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(first, second),
                new TrackId("aim"), new TrackId("active"));
        return CameraAnimation.create("doc", rig, new TrackId("source")).withSymbols(symbols);
    }

    private static CurveTrack formulaTrack(TrackId id, String formula) {
        Keyframe key = new Keyframe(new KeyId(id.value() + "-key"), 0f, new Formula(formula),
                0f, 0f, 1f, 1f, Curve.Interpolation.LINEAR);
        return CurveTrack.ofCurve(id, id.value(), Curve.of(key));
    }

    private static CurveTrack constantTrack(TrackId id) {
        Keyframe key = new Keyframe(new KeyId(id.value() + "-key"), 0f, new Constant(1f),
                0f, 0f, 1f, 1f, Curve.Interpolation.LINEAR);
        return CurveTrack.ofCurve(id, id.value(), Curve.of(key));
    }
}
