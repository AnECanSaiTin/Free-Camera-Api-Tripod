package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleCollectionErrorReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleUnknownReason;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialReadings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackLookup;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferences;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.CompiledExpressionImpl;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.DefaultExpressionCompiler;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.BUILTIN_ARITY_MISMATCH;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.DEPTH_LIMIT_EXCEEDED;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.INTERNAL_ERROR;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.NON_FINITE_RESULT;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.SAMPLE_COLLECTION_ERROR;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.SAMPLE_UNKNOWN;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.UNKNOWN_NAME;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.UNKNOWN_SAMPLE_NAME;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 第一版求值器的行为口径：算术、内建、变量、函数、样本、预算、深度与失败链。
///
/// 上下文使用真实快照与帧事实；轨道查询 / 索引使用最小假实现，样本读数直接构造为
/// [FrameFacts#samples()] 的条目。需要曲线时复用 [ConstantCurveSampler] 的常量键。
class CoreExpressionEvaluatorTest {

    private static final CoreExpressionEvaluator EVALUATOR = new CoreExpressionEvaluator();
    private static final DefaultExpressionCompiler COMPILER = new DefaultExpressionCompiler();

    private static final Revision REVISION = new Revision(3);
    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier SAMPLE_KIND = Identifier.fromNamespaceAndPath("test", "number");
    private static final TrackId SOURCE_TRACK = new TrackId("source");
    private static final TrackId ENTRY_TRACK = new TrackId("entry");
    private static final RigId RIG = new RigId("rig");
    private static final ExpressionSource ENTRY_SOURCE = new ExpressionSource.Channel(ENTRY_TRACK, null);

    private static final long DEFAULT_QUOTA = 100_000L;
    private static final int DEFAULT_DEPTH = 64;
    private static final long DEFAULT_SEED = 0L;
    private static final float EPSILON = 1.0e-5f;

    private static final Scene EMPTY_SCENE =
            Scene.of(document(Symbols.EMPTY, List.of(), List.of()));

    // region 算术、Quantity 与内建

    @Test
    void arithmeticUsesPrecedenceAssociativityAndUnaryOperators() {
        assertEquals(7f, value("1 + 2 * 3"), EPSILON);
        assertEquals(9f, value("(1 + 2) * 3"), EPSILON);
        assertEquals(-3f, value("1 - 2 - 2"), EPSILON);
        assertEquals(3.5f, value("7 / 2"), EPSILON);
        assertEquals(3f, value("7 % 4"), EPSILON);
        assertEquals(-1f, value("-3 + +2"), EPSILON);
        assertEquals(-6f, value("2 * -3"), EPSILON);
    }

    @Test
    void builtinQuantitiesUseTheExplicitInputs() {
        // time = 1.5, progress = 0.5, worldTime = 0.5（世界日进度）。
        assertEquals(1.75f, value("t + p * wt", EMPTY_SCENE, 1.5f), EPSILON);
        assertEquals(-1.0f, value("wt - t", EMPTY_SCENE, 1.5f), EPSILON);
        assertEquals(0.5f, value("p"), EPSILON);
    }

    @Test
    void builtinMathFunctionsFollowTheDocumentedSignatures() {
        assertEquals(4f, value("abs(-4)"), EPSILON);
        assertEquals(-2f, value("min(3, -2)"), EPSILON);
        assertEquals(3f, value("max(3, -2)"), EPSILON);
        assertEquals(1f, value("clamp(5, 0, 1)"), EPSILON);
        assertEquals(8f, value("pow(2, 3)"), EPSILON);
        assertEquals(3f, value("sqrt(9)"), EPSILON);
        assertEquals(1f, value("floor(1.9)"), EPSILON);
        assertEquals(2f, value("ceil(1.1)"), EPSILON);
        assertEquals(2f, value("round(1.5)"), EPSILON);
        assertEquals(0f, value("sin(0)"), EPSILON);
        assertEquals(1f, value("cos(0)"), EPSILON);
        assertEquals(0f, value("tan(0)"), EPSILON);
    }

    @Test
    void stepSupportsOneAndTwoArgumentsAndExtraArgumentsAreRejectedBeforeEvaluation() {
        assertEquals(1f, value("step(0)"), EPSILON);
        assertEquals(0f, value("step(-0.001)"), EPSILON);
        assertEquals(1f, value("step(2, 3)"), EPSILON);
        assertEquals(0f, value("step(3, 2)"), EPSILON);

        // core 的 BuiltinFunction.STEP 在编译期只接受 1..2 个实参；求值器另有防御性拒绝。
        CompilationResult rejected = COMPILER.compile("step(1, 2, 3)");
        assertFalse(rejected.compiled());
        assertEquals(BUILTIN_ARITY_MISMATCH, rejected.diagnostics().getFirst().code());
    }

    @Test
    void selectEvaluatesOnlyTheSelectedBranch() {
        assertEquals(2f, value("select(1, 2, 1 / 0)"), EPSILON);
        assertEquals(3f, value("select(0, 1 / 0, 3)"), EPSILON);

        // 未选中的分支即使引用未知名字也不能影响结果。
        assertEquals(3f, value("select(0, missing, 3)"), EPSILON);
        assertEquals(4f, value("select(-2, 4, missing)"), EPSILON);
    }

    // endregion

    // region 变量与 TrackRef

    @Test
    void variableConstantFormulaAndTrackRefAreEvaluatedInDocumentScope() {
        CurveTrack curve = CurveTrack.ofCurve(new TrackId("curve"), "curve",
                Curve.of(constantKey("only", 0f, 42f)));
        Symbols symbols = new Symbols(List.of(
                Variable.constant("speed", 2.5f),
                new Variable("total", new Formula("speed + t")),
                new Variable("curveValue", new TrackRef(curve.id()))
        ), List.of());
        Scene scene = Scene.of(document(symbols, List.of(), List.of(curve)));

        assertEquals(5f, value("speed * 2", scene), EPSILON);
        assertEquals(4.5f, value("total", scene, 2f), EPSILON);
        assertEquals(43f, value("curveValue + 1", scene, 2f), EPSILON);
    }

    @Test
    void unknownVariableAndBrokenTrackRefsFail() {
        CurveTrack curve = CurveTrack.ofCurve(new TrackId("curve"), "curve",
                Curve.of(constantKey("only", 0f, 1f)));
        Symbols symbols = new Symbols(List.of(
                new Variable("missingTrack", new TrackRef(new TrackId("nope"))),
                new Variable("aimRef", new TrackRef(new TrackId("aim")))
        ), List.of());
        Scene scene = Scene.of(document(symbols, List.of(), List.of(curve)));

        failure(evaluate("doesNotExist + 1", scene), UNKNOWN_NAME);
        failure(evaluate("missingTrack", scene), UNKNOWN_NAME);

        // "aim" 是机位注视轨道，不是 CurveTrack；TrackRef 指向它必须失败。
        failure(evaluate("aimRef", scene), UNKNOWN_NAME);
    }

    @Test
    void constantVariableMustStillBeFinite() {
        Symbols symbols = new Symbols(List.of(
                new Variable("bad", new Constant(Float.POSITIVE_INFINITY))
        ), List.of());

        failure(evaluate("bad", Scene.of(document(symbols))), NON_FINITE_RESULT);
    }

    // endregion

    // region 自定义函数

    @Test
    void customFunctionsBindParametersAndShadowGlobalVariables() {
        Symbols symbols = new Symbols(List.of(
                Variable.constant("x", 100f),
                Variable.constant("speed", 2.5f)
        ), List.of(
                new CustomFunction("f", List.of("x"), "x + 1"),
                new CustomFunction("add", List.of("a", "b"), "a + b"),
                new CustomFunction("g", List.of(), "speed")
        ));
        Scene scene = Scene.of(document(symbols));

        // 形参 x 屏蔽全局变量 x；调用返回后外层 x 仍是 100。
        assertEquals(103f, value("f(2) + x", scene), EPSILON);
        assertEquals(5f, value("add(2, 3)", scene), EPSILON);
        assertEquals(2.5f, value("g()", scene), EPSILON);
        assertEquals(4.5f, value("add(f(1), g())", scene), EPSILON);

        failure(evaluate("nope()", scene), UNKNOWN_NAME);
        failure(evaluate("add(1)", scene), INTERNAL_ERROR);
    }

    @Test
    void functionBodyFailureKeepsFunctionSourceInTheChain() {
        Symbols symbols = new Symbols(List.of(), List.of(
                new CustomFunction("f", List.of(), "1 / 0")
        ));
        Scene scene = Scene.of(document(symbols));

        ExpressionFailure failure = failure(evaluate("f()", scene), NON_FINITE_RESULT);

        assertEquals(new ExpressionSource.Function("f"), failure.innermost().source());
        assertEquals(0, failure.innermost().diagnostic().start());
        assertEquals(5, failure.innermost().diagnostic().end());
    }

    @Test
    void variableFormulaDoesNotSeeCallerFunctionParameters() {
        Symbols symbols = new Symbols(List.of(
                Variable.constant("x", 7f),
                new Variable("v", new Formula("x + 1"))
        ), List.of(
                new CustomFunction("f", List.of("x"), "v")
        ));
        Scene scene = Scene.of(document(symbols));

        // 形参 x 只对函数体直接可见；v 的公式回到文档作用域读取全局 x。
        assertEquals(8f, value("f(2)", scene), EPSILON);
    }

    // endregion

    // region 样本

    @Test
    void sampleValueWinsAndFallbackIsNotEvaluated() {
        WorldSample sample = sample("speed");
        Scene scene = sampleScene(sample, new SampleReading.Value(12f, 4L));

        assertEquals(12f, value("sample(\"speed\")", scene), EPSILON);

        // 如果 fallback 被求值，会先触发 NON_FINITE_RESULT。
        assertEquals(12f, value("sample(\"speed\", 1 / 0)", scene), EPSILON);
    }

    @Test
    void sampleUnknownUsesFallbackLazily() {
        WorldSample sample = sample("speed");
        Scene unknown = sampleScene(sample,
                new SampleReading.Unknown(SampleUnknownReason.TARGET_NOT_FOUND, 4L));

        failure(evaluate("sample(\"speed\")", unknown), SAMPLE_UNKNOWN);
        assertEquals(7f, value("sample(\"speed\", 7)", unknown), EPSILON);
        failure(evaluate("sample(\"speed\", 1 / 0)", unknown), NON_FINITE_RESULT);
    }

    @Test
    void missingSampleReadingIsTreatedAsUnknown() {
        WorldSample sample = sample("speed");
        Scene missing = Scene.of(document(Symbols.EMPTY, List.of(sample), List.of()));

        failure(evaluate("sample(\"speed\")", missing), SAMPLE_UNKNOWN);
        assertEquals(7f, value("sample(\"speed\", 7)", missing), EPSILON);
    }

    @Test
    void collectionErrorNeverUsesFallback() {
        WorldSample sample = sample("speed");
        Scene scene = sampleScene(sample,
                new SampleReading.CollectionError(
                        SampleCollectionErrorReason.COLLECTOR_THREW, 4L));

        ExpressionFailure failure = failure(evaluate("sample(\"speed\", 1 / 0)", scene),
                SAMPLE_COLLECTION_ERROR);

        // fallback 未被执行，因此失败链里没有内层的除法错误。
        assertTrue(failure.causes().isEmpty(), describe(failure));
    }

    @Test
    void unknownSampleNameIsRejected() {
        failure(evaluate("sample(\"missing\")", EMPTY_SCENE), UNKNOWN_SAMPLE_NAME);
    }

    // endregion

    // region 非有限、预算与深度

    @Test
    void divisionByZeroAndNonFiniteMathFail() {
        assertNonFinite("1 / 0");
        assertNonFinite("1 % 0");
        assertNonFinite("0 / 0");
        assertNonFinite("sqrt(-1)");
        assertNonFinite("pow(10, 1000)");
        assertNonFinite("1e39");
    }

    @Test
    void workQuotaIsChargedPerNodeAndNeverRefunded() {
        Run failed = run("1 + 2", EMPTY_SCENE, 0f, DEFAULT_SEED, 2L, DEFAULT_DEPTH);
        failure(failed.sample(), WORK_LIMIT_EXCEEDED);
        assertEquals(2L, failed.session().consumedWork());

        Run exact = run("1 + 2", EMPTY_SCENE, 0f, DEFAULT_SEED, 3L, DEFAULT_DEPTH);
        assertEquals(3f, valueOf(exact.sample()), EPSILON);
        assertEquals(3L, exact.session().consumedWork());

        Run zero = run("1 + 2", EMPTY_SCENE, 0f, DEFAULT_SEED, 0L, DEFAULT_DEPTH);
        failure(zero.sample(), WORK_LIMIT_EXCEEDED);
        assertEquals(0L, zero.session().consumedWork());
    }

    @Test
    void enteringANestedFormulaCostsAnExtraWorkUnit() {
        Symbols symbols = new Symbols(List.of(
                new Variable("total", new Formula("1 + 2"))
        ), List.of());
        Scene scene = Scene.of(document(symbols));

        Run tooLittle = run("total", scene, 0f, DEFAULT_SEED, 4L, DEFAULT_DEPTH);
        failure(tooLittle.sample(), WORK_LIMIT_EXCEEDED);

        // 变量节点 1 + 嵌套进入 1 + 公式 1 + 两个字面量 2 = 5。
        Run exact = run("total", scene, 0f, DEFAULT_SEED, 5L, DEFAULT_DEPTH);
        assertEquals(3f, valueOf(exact.sample()), EPSILON);
        assertEquals(5L, exact.session().consumedWork());
    }

    @Test
    void depthLimitGuardsNestedFunctionBodiesAndUnwinds() {
        Symbols symbols = new Symbols(List.of(), List.of(
                new CustomFunction("f", List.of("x"), "g(x)"),
                new CustomFunction("g", List.of("x"), "x + 1")
        ));
        Scene scene = Scene.of(document(symbols));

        Run shallow = run("f(1)", scene, 0f, DEFAULT_SEED, DEFAULT_QUOTA, 1);
        failure(shallow.sample(), DEPTH_LIMIT_EXCEEDED);
        assertEquals(0, shallow.session().currentDepth());

        Run enough = run("f(1)", scene, 0f, DEFAULT_SEED, DEFAULT_QUOTA, 2);
        assertEquals(2f, valueOf(enough.sample()), EPSILON);
        assertEquals(0, enough.session().currentDepth());
    }

    // endregion

    // region 失败链与内部错误

    @Test
    void failureChainCarriesPerLayerSourcesAndSpans() {
        Symbols symbols = new Symbols(List.of(
                new Variable("bad", new Formula("1 / 0"))
        ), List.of());
        Scene scene = Scene.of(document(symbols));

        ExpressionSample sample = evaluate("bad + 1", scene);
        ExpressionFailure failure = failure(sample, NON_FINITE_RESULT);

        // 入口层：入口源码 + 整个表达式区间。
        assertEquals(ENTRY_SOURCE, failure.source());
        assertEquals(0, failure.diagnostic().start());
        assertEquals(7, failure.diagnostic().end());

        // 最内层：变量源码 + 变量公式内的除法区间。
        ExpressionFailure innermost = failure.innermost();
        assertEquals(new ExpressionSource.Variable("bad"), innermost.source());
        assertEquals(0, innermost.diagnostic().start());
        assertEquals(5, innermost.diagnostic().end());
        assertFalse(failure.causes().isEmpty());
    }

    @Test
    void nonCoreCompiledExpressionIsRejectedAsInternalError() {
        CompiledExpression handle = new CompiledExpression() {
            @Override
            public String source() {
                return "t + 1";
            }

            @Override
            public ExpressionReferences references() {
                return ExpressionReferences.EMPTY;
            }
        };

        Run run = run(handle, EMPTY_SCENE, 0f, DEFAULT_SEED, DEFAULT_QUOTA, DEFAULT_DEPTH);
        ExpressionFailure failure = failure(run.sample(), INTERNAL_ERROR);

        assertEquals(ENTRY_SOURCE, failure.source());
        assertEquals(0, failure.diagnostic().start());
        assertEquals(5, failure.diagnostic().end());
    }

    // endregion

    // region random / noise

    @Test
    void randomAdvancesWithinOneEvaluationAndResetsPerEvaluation() {
        float first = value("random()", EMPTY_SCENE, 0f, 42L);
        float second = value("random() * 0 + random()", EMPTY_SCENE, 0f, 42L);
        float sum = value("random() + random()", EMPTY_SCENE, 0f, 42L);

        assertTrue(Float.isFinite(first) && first >= 0f && first < 1f, "first: " + first);
        assertTrue(Float.isFinite(second) && second >= 0f && second < 1f, "second: " + second);
        assertNotEquals(first, second, "random() must advance the stream within one evaluation");
        assertEquals(first + second, sum, EPSILON);

        // 每次 evaluate 重新从 randomSeed 开始，因此同种子的首个 random() 相同。
        assertEquals(first, value("random()", EMPTY_SCENE, 0f, 42L), 0f);
        assertNotEquals(first, value("random()", EMPTY_SCENE, 0f, 43L));
    }

    @Test
    void noiseIsDeterministicSeedDependentAndBounded() {
        float base = value("noise(7, 1.25)", EMPTY_SCENE);

        assertEquals(base, value("noise(7, 1.25)", EMPTY_SCENE), 0f);
        assertTrue(base >= 0f && base < 1f, "noise out of range: " + base);
        assertNotEquals(base, value("noise(8, 1.25)", EMPTY_SCENE));
        assertNotEquals(base, value("noise(7, 1.5)", EMPTY_SCENE));
    }

    // endregion

    // region 测试辅助

    private record Run(ExpressionSample sample, EvaluationSession session) {
    }

    private static void assertNonFinite(String source) {
        failure(evaluate(source, EMPTY_SCENE), NON_FINITE_RESULT);
    }

    private static float value(String source) {
        return value(source, EMPTY_SCENE);
    }

    private static float value(String source, Scene scene) {
        return value(source, scene, 0f);
    }

    private static float value(String source, Scene scene, float time) {
        return value(source, scene, time, DEFAULT_SEED);
    }

    private static float value(String source, Scene scene, float time, long seed) {
        return valueOf(run(source, scene, time, seed, DEFAULT_QUOTA, DEFAULT_DEPTH).sample());
    }

    private static float valueOf(ExpressionSample sample) {
        assertTrue(sample instanceof ExpressionSample.Value,
                () -> "expected a value but got " + describe(sample));

        return ((ExpressionSample.Value) sample).value();
    }

    private static ExpressionSample evaluate(String source, Scene scene) {
        return evaluate(source, scene, 0f);
    }

    private static ExpressionSample evaluate(String source, Scene scene, float time) {
        return run(source, scene, time, DEFAULT_SEED, DEFAULT_QUOTA, DEFAULT_DEPTH).sample();
    }

    private static ExpressionFailure failure(ExpressionSample sample,
                                             ExpressionDiagnosticCode expectedCode) {
        assertTrue(sample instanceof ExpressionSample.Failure,
                () -> "expected " + expectedCode + " but got " + describe(sample));

        ExpressionFailure failure = ((ExpressionSample.Failure) sample).failure();
        assertEquals(expectedCode, failure.code(), () -> "unexpected diagnostic: " + describe(sample));

        return failure;
    }

    private static Run run(String source,
                           Scene scene,
                           float time,
                           long seed,
                           long workQuota,
                           int maxDepth) {
        return run(compile(source), scene, time, seed, workQuota, maxDepth);
    }

    private static Run run(CompiledExpression expression,
                           Scene scene,
                           float time,
                           long seed,
                           long workQuota,
                           int maxDepth) {
        EvaluationSession session = EvaluationSession.create(workQuota, maxDepth);
        EvaluationContext context = context(scene);
        ExpressionInputs inputs = new ExpressionInputs(context, ENTRY_SOURCE, RIG, time,
                0.5f, 0.5f, SpatialReadings.EMPTY, seed, session, new ConstantCurveSampler());

        return new Run(EVALUATOR.evaluate(expression, inputs), session);
    }

    private static EvaluationContext context(Scene scene) {
        AnimationSnapshot snapshot = new AnimationSnapshot(REVISION, scene.document());
        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 1L, 2L, 0.5f,
                new cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.WorldTime(12000, 24000),
                1L, Map.of(), scene.readings(), true);
        TrackLookup lookup = scene.document()::curveTrack;
        TrackIndex index = new FixedTrackIndex(snapshot, scene.entries());

        return EvaluationContext.bind(11L, snapshot, facts, lookup, index);
    }

    private static CompiledExpressionImpl compile(String source) {
        CompilationResult result = COMPILER.compile(source);
        assertTrue(result.compiled(),
                () -> "expected successful compilation for [" + source + "] but got "
                        + result.diagnostics());

        return (CompiledExpressionImpl) result.compiledExpression().orElseThrow();
    }

    private static String describe(ExpressionSample sample) {
        if (sample instanceof ExpressionSample.Value value) {
            return "value " + value.value();
        }

        ExpressionFailure current = ((ExpressionSample.Failure) sample).failure();
        StringBuilder builder = new StringBuilder();
        builder.append(current.code()).append(": ").append(current.diagnostic().detail());

        while (!current.causes().isEmpty()) {
            current = current.causes().getLast();
            builder.append(" <- ").append(current.source())
                    .append(": ").append(current.diagnostic().detail());
        }

        return builder.toString();
    }

    private static String describe(ExpressionFailure failure) {
        return failure.code() + ": " + failure.diagnostic().detail();
    }

    private static CameraAnimation document(Symbols symbols) {
        return document(symbols, List.of(), List.of());
    }

    private static CameraAnimation document(Symbols symbols,
                                            List<WorldSample> samples,
                                            List<CurveTrack> tracks) {
        CameraRig rig = CameraRig.create(RIG, "rig", tracks,
                new TrackId("aim"), new TrackId("active"));

        return CameraAnimation.create("doc", rig, SOURCE_TRACK)
                .withSymbols(symbols)
                .withSamples(samples);
    }

    private static Keyframe constantKey(String id, float time, float value) {
        return new Keyframe(new KeyId(id), time, new Constant(value),
                0f, 0f, 1f, 1f, Curve.Interpolation.LINEAR);
    }

    private static WorldSample sample(String name) {
        return new WorldSample(new SampleId("sample-" + name), name, SAMPLE_KIND,
                new TestSampleArgs(), 1);
    }

    private static Scene sampleScene(WorldSample sample, SampleReading reading) {
        return Scene.of(document(Symbols.EMPTY, List.of(sample), List.of()))
                .withReadings(Map.of(sample.id(), reading));
    }

    private record TestSampleArgs() implements SampleArgs {
        @Override
        public Identifier type() {
            return SAMPLE_KIND;
        }
    }

    /// 一份最小文档场景：真实文档 + 帧内采样读数 + 轨道索引条目。
    private record Scene(CameraAnimation document,
                         Map<SampleId, SampleReading> readings,
                         Map<TrackId, TrackIndex.Entry> entries) {

        private Scene {
            document = Objects.requireNonNull(document, "document");
            readings = Map.copyOf(readings);
            entries = Map.copyOf(entries);
        }

        private static Scene of(CameraAnimation document) {
            Map<TrackId, TrackIndex.Entry> entries = new LinkedHashMap<>();

            for (CameraRig rig : document.rigs()) {
                for (CurveTrack track : rig.channels()) {
                    entries.put(track.id(), new TrackIndex.Entry(track, rig.id()));
                }

                entries.put(rig.aim().id(), new TrackIndex.Entry(rig.aim(), rig.id()));
                entries.put(rig.view().active().id(),
                        new TrackIndex.Entry(rig.view().active(), rig.id()));
            }

            return new Scene(document, Map.of(), entries);
        }

        private Scene withReadings(Map<SampleId, SampleReading> newReadings) {
            return new Scene(document, newReadings, entries);
        }
    }

    private static final class FixedTrackIndex implements TrackIndex {

        private final AnimationSnapshot snapshot;
        private final Map<TrackId, Entry> entries;

        private FixedTrackIndex(AnimationSnapshot snapshot, Map<TrackId, Entry> entries) {
            this.snapshot = snapshot;
            this.entries = entries;
        }

        @Override
        public AnimationSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public Optional<Entry> find(TrackId trackId) {
            return Optional.ofNullable(entries.get(trackId));
        }

        @Override
        public List<Diagnostic> diagnostics() {
            return List.of();
        }
    }

    // endregion
}
