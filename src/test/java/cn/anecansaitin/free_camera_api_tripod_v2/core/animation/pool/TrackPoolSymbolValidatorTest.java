package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 轨道池符号表校验：保留名、重复、缺失引用、函数元数与采样。
class TrackPoolSymbolValidatorTest {

    @Test
    void validSymbolsPass() {
        Symbols symbols = new Symbols(
                List.of(new Variable("speed", new Formula("t + 1"))),
                List.of(new CustomFunction("double", List.of("x"), "x + x")));
        TrackPoolAnimation document = document(symbols);

        assertTrue(new TrackPoolValidator().validate(document, Revision.INITIAL).isEmpty());
    }

    @Test
    void reservedDuplicateAndBrokenReferencesAreReported() {
        Symbols symbols = new Symbols(
                List.of(new Variable("t", new Constant(1)),
                        new Variable("same", new Constant(1)),
                        new Variable("same", new Constant(2)),
                        new Variable("bad", new Formula("unknown + 1")),
                        new Variable("track", new TrackRef(new TrackId("missing")))),
                List.of(new CustomFunction("f", List.of("x"), "x")));
        TrackPoolAnimation document = document(symbols);

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertTrue(diagnostics.size() >= 4, () -> "diagnostics: " + diagnostics);
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("reserved")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("Duplicate variable")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("Unknown variable")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.message().toString().contains("curve track")));
    }

    @Test
    void variableAndFunctionCyclesAreReported() {
        Symbols variableCycle = new Symbols(
                List.of(new Variable("a", new Formula("a + 1"))), List.of());
        var variableDiagnostics = new TrackPoolValidator().validate(
                document(variableCycle), Revision.INITIAL);
        assertTrue(variableDiagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.message().toString().contains("cycle")));

        Symbols functionCycle = new Symbols(List.of(), List.of(
                new CustomFunction("f", List.of(), "g()"),
                new CustomFunction("g", List.of(), "f()")));
        var functionDiagnostics = new TrackPoolValidator().validate(
                document(functionCycle), Revision.INITIAL);
        assertTrue(functionDiagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.message().toString().contains("cycle")));
    }

    @Test
    void functionArityMismatchIsReported() {
        Symbols symbols = new Symbols(
                List.of(new Variable("v", new Formula("f(1)"))),
                List.of(new CustomFunction("f", List.of("x", "y"), "x + y")));
        TrackPoolAnimation document = document(symbols);

        var diagnostics = new TrackPoolValidator().validate(document, Revision.INITIAL);

        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.getFirst().message().toString().contains("expects 2 arguments"));
    }

    private static TrackPoolAnimation document(Symbols symbols) {
        return new TrackPoolAnimation(TrackPoolAnimation.CURRENT_DATA_VERSION, "doc",
                List.of(CurveTrack.ofCurve(new TrackId("curve"), "curve", Curve.EMPTY)),
                List.of(PooledRig.create(new RigId("rig"), "rig")), PropertyBinding.disabled(),
                symbols, List.of(), List.of(), List.of(), List.of(), PlaybackRange.AUTO);
    }
}
