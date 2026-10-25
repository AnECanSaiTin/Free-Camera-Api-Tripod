package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialReadings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackLookup;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CoreExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CurveInterpolation;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.ExpressionCurveSampler;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 布局求值：常量 / 公式 / 曲线分支、非有限失败与 ResolvedRect 两种口径。
///
/// 上下文使用真实 [AnimationSnapshot] 与 [FrameFacts]，只以最小假 [TrackLookup] / [TrackIndex]
/// 提供空轨道查询；布局求值不读取轨道，公式里的 `TrackRef` 才需要采样器。
/// 公式成功与失败都走真实 [CoreExpressionEvaluator]，避免把“编译通过”误当成“求值通过”。
class LayoutEvaluatorTest {

    private static final Revision REVISION = new Revision(7);
    private static final Identifier DIMENSION =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final RigId RIG = new RigId("rig");
    private static final CoreExpressionEvaluator CORE_EVALUATOR = new CoreExpressionEvaluator();
    private static final AnimatedNumberEvaluator NUMBER_EVALUATOR =
            new AnimatedNumberEvaluator(CORE_EVALUATOR);
    private static final ExpressionCurveSampler CURVE_SAMPLER =
            new ExpressionCurveSampler(CORE_EVALUATOR);
    private static final ResolvedRectEvaluator RECT_EVALUATOR =
            new ResolvedRectEvaluator(NUMBER_EVALUATOR);
    private static final float EPSILON = 1.0e-6f;

    // region 数值分支

    @Test
    void constantValueIsReturnedDirectly() {
        LayoutValue result = NUMBER_EVALUATOR.evaluate(
                AnimatedNumber.constant(12.5f), input(ExpressionSource.LayoutField.X, 3f));

        assertEquals(12.5d, success(result).value(), EPSILON);
    }

    @Test
    void emptyKeyedCurveUsesExplicitDefault() {
        AnimatedNumber.Keyed keyed = new AnimatedNumber.Keyed(Curve.EMPTY, 0.75f);

        LayoutValue result = NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.WIDTH, 100f));

        assertEquals(0.75d, success(result).value(), EPSILON);
    }

    @Test
    void keyedLinearInterpolatesAndIgnoresTheDefault() {
        Curve curve = curve(
                key("left", 0f, 10f, Curve.Interpolation.LINEAR),
                key("right", 10f, 20f, Curve.Interpolation.LINEAR));
        // 默认值只在空曲线时生效；非空曲线中点必须是 15 而不是 999。
        AnimatedNumber.Keyed keyed = new AnimatedNumber.Keyed(curve, 999f);

        assertEquals(15d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.X, 5f))).value(), EPSILON);
    }

    @Test
    void keyedStepHoldsTheLeftValueWithinTheSegment() {
        Curve curve = curve(
                key("left", 0f, 10f, Curve.Interpolation.STEP),
                key("right", 10f, 20f, Curve.Interpolation.STEP));
        AnimatedNumber.Keyed keyed = new AnimatedNumber.Keyed(curve, 0f);

        assertEquals(10d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.Y, 9.999f))).value(), EPSILON);
        assertEquals(20d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.Y, 10f))).value(), EPSILON);
    }

    @Test
    void keyedClampsOutsideTheKeyRange() {
        Curve curve = curve(
                key("first", 5f, 10f, Curve.Interpolation.LINEAR),
                key("last", 15f, 20f, Curve.Interpolation.LINEAR));
        AnimatedNumber.Keyed keyed = new AnimatedNumber.Keyed(curve, 0f);

        assertEquals(10d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.WIDTH, 0f))).value(), EPSILON);
        assertEquals(20d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.WIDTH, 100f))).value(), EPSILON);
        assertEquals(15d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.WIDTH, 10f))).value(), EPSILON);
    }

    @Test
    void singleKeyCurveClampsToThatKey() {
        Curve curve = Curve.of(key("only", 2f, 42f, Curve.Interpolation.LINEAR));
        AnimatedNumber.Keyed keyed = new AnimatedNumber.Keyed(curve, 0f);

        assertEquals(42d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.HEIGHT, 0f))).value(), EPSILON);
        assertEquals(42d, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.HEIGHT, 100f))).value(), EPSILON);
    }

    @Test
    void keyedHermiteMatchesCurveInterpolation() {
        Keyframe left = new Keyframe(new KeyId("left"), 0f, new Constant(0f),
                0f, 2f, 1f, 1f, Curve.Interpolation.HERMITE);
        Keyframe right = new Keyframe(new KeyId("right"), 2f, new Constant(10f),
                3f, 0f, 0.5f, 1.5f, Curve.Interpolation.HERMITE);
        AnimatedNumber.Keyed keyed = new AnimatedNumber.Keyed(curve(left, right), 0f);
        float time = 0.75f;
        double expected = CurveInterpolation.hermite(
                CurveInterpolation.normalizedTime(time, 0d, 2d), 2d,
                0d, 10d, 2d, 3d, 1d, 0.5d);

        assertEquals(expected, success(NUMBER_EVALUATOR.evaluate(
                keyed, input(ExpressionSource.LayoutField.X, time))).value(), 1.0e-9d);
    }

    // endregion

    // region 公式

    @Test
    void formulaValueSeesDocumentTimeAndProgress() {
        LayoutValue result = NUMBER_EVALUATOR.evaluate(
                AnimatedNumber.formula("t * 2 + p"),
                input(ExpressionSource.LayoutField.X, 2.5f, 0.25f));

        // t = 2.5，p = 0.25 → 5.25。
        assertEquals(5.25d, success(result).value(), EPSILON);
    }

    @Test
    void formulaValueFailureCarriesStructuredDiagnostic() {
        LayoutEvalInput input = input(ExpressionSource.LayoutField.WIDTH, 1f);

        LayoutValue.Failure failure = failure(NUMBER_EVALUATOR.evaluate(
                AnimatedNumber.formula("missing_variable"), input));
        Diagnostic diagnostic = failure.diagnostic();

        assertEquals(DiagnosticCode.EXPRESSION_INVALID, diagnostic.code());
        assertEquals(Severity.ERROR, diagnostic.severity());
        assertTrue(diagnostic.scope() instanceof DiagnosticScope.Document);
        assertEquals(REVISION, ((DiagnosticScope.Document) diagnostic.scope()).revision());

        assertNotNull(diagnostic.location());
        assertEquals(RIG, diagnostic.location().objectId());
        assertTrue(diagnostic.location().fieldPath().contains("width"),
                diagnostic.location().fieldPath());

        String message = messageOf(diagnostic);
        assertTrue(message.contains("missing_variable") || message.contains("unknown variable"), message);
    }

    @Test
    void keyedEndpointFormulaFailurePropagatesWithTheEndpointKey() {
        Curve curve = curve(
                formulaKey("left", 0f, "missing_variable", Curve.Interpolation.LINEAR),
                key("right", 10f, 20f, Curve.Interpolation.LINEAR));

        LayoutValue.Failure failure = failure(NUMBER_EVALUATOR.evaluate(
                new AnimatedNumber.Keyed(curve, 0f),
                input(ExpressionSource.LayoutField.X, 5f)));

        Diagnostic diagnostic = failure.diagnostic();
        assertEquals(DiagnosticCode.EXPRESSION_INVALID, diagnostic.code());
        assertNotNull(diagnostic.location());
        assertTrue(diagnostic.location().fieldPath().contains("left"),
                diagnostic.location().fieldPath());
        assertTrue(messageOf(diagnostic).contains("missing_variable"), messageOf(diagnostic));
    }

    @Test
    void formulaInputsUseLayoutSourceWithTheEndpointKey() {
        AtomicReference<ExpressionInputs> captured = new AtomicReference<>();
        ExpressionEvaluator recording = (expression, inputs) -> {
            captured.set(inputs);
            return new ExpressionSample.Value(7f);
        };
        AnimatedNumberEvaluator evaluator = new AnimatedNumberEvaluator(recording);
        Curve curve = curve(
                key("left", 0f, 0f, Curve.Interpolation.LINEAR),
                formulaKey("right", 10f, "t + 1", Curve.Interpolation.LINEAR));
        LayoutEvalInput input = input(ExpressionSource.LayoutField.Y, 5f);

        // 左 0，右由记录器固定为 7；线性中点为 3.5。
        LayoutValue result = evaluator.evaluate(new AnimatedNumber.Keyed(curve, 0f), input);

        assertEquals(3.5d, success(result).value(), EPSILON);
        ExpressionInputs inputs = captured.get();
        assertNotNull(inputs);
        assertEquals(new ExpressionSource.Layout(RIG, ExpressionSource.LayoutField.Y,
                new KeyId("right")), inputs.source());
        assertEquals(RIG, inputs.rigId());
        assertEquals(5f, inputs.time());
        assertEquals(0.25f, inputs.progress());
        assertEquals(12.5f, inputs.worldTime());
        assertSame(input.context(), inputs.context());
        assertSame(CURVE_SAMPLER, inputs.sampler());
    }

    @Test
    void nonFiniteFormulaResultIsFailure() {
        LayoutValue.Failure failure = failure(NUMBER_EVALUATOR.evaluate(
                AnimatedNumber.formula("1 / 0"),
                input(ExpressionSource.LayoutField.X, 0f)));

        assertEquals(DiagnosticCode.EXPRESSION_INVALID, failure.diagnostic().code());
    }

    @Test
    void nonFiniteConstantIsFailureNotNaN() {
        LayoutValue.Failure failure = failure(NUMBER_EVALUATOR.evaluate(
                AnimatedNumber.constant(Float.NaN),
                input(ExpressionSource.LayoutField.X, 0f)));

        assertEquals(LayoutDiagnostics.LAYOUT_VALUE_INVALID, failure.diagnostic().code());
        assertTrue(messageOf(failure.diagnostic()).contains("finite"),
                messageOf(failure.diagnostic()));
    }

    @Test
    void inputRejectsNonFiniteTime() {
        assertThrows(IllegalArgumentException.class, () -> new LayoutEvalInput(
                context(), EvaluationSession.create(64L, 8), RIG,
                ExpressionSource.LayoutField.X, Float.NaN, 0f, 0f,
                SpatialReadings.EMPTY, 0L, CURVE_SAMPLER));
    }

    // endregion

    // region 矩形

    @Test
    void normalizedRectIsEvaluated() {
        RigView.Rect.Normalized rect = new RigView.Rect.Normalized(
                AnimatedNumber.constant(0.1f),
                AnimatedNumber.constant(0.2f),
                AnimatedNumber.constant(0.5f),
                AnimatedNumber.constant(0.25f));

        ResolvedRect resolved = resolved(RECT_EVALUATOR.evaluate(
                rect, input(ExpressionSource.LayoutField.X, 0f)));

        assertTrue(resolved instanceof ResolvedRect.Normalized);
        ResolvedRect.Normalized normalized = (ResolvedRect.Normalized) resolved;
        assertEquals(0.1d, normalized.x(), EPSILON);
        assertEquals(0.2d, normalized.y(), EPSILON);
        assertEquals(0.5d, normalized.width(), EPSILON);
        assertEquals(0.25d, normalized.height(), EPSILON);
        assertTrue(resolved.normalized());
    }

    @Test
    void anchoredRectKeepsCornerAndPixelOffsets() {
        RigView.Rect.Anchored rect = new RigView.Rect.Anchored(
                RigView.Rect.Corner.BOTTOM_RIGHT,
                AnimatedNumber.constant(-12f),
                AnimatedNumber.constant(-8f),
                AnimatedNumber.constant(320f),
                AnimatedNumber.constant(180f));

        ResolvedRect resolved = resolved(RECT_EVALUATOR.evaluate(
                rect, input(ExpressionSource.LayoutField.X, 0f)));

        assertTrue(resolved instanceof ResolvedRect.Anchored);
        ResolvedRect.Anchored anchored = (ResolvedRect.Anchored) resolved;
        assertEquals(RigView.Rect.Corner.BOTTOM_RIGHT, anchored.corner());
        assertEquals(-12d, anchored.x(), EPSILON);
        assertEquals(-8d, anchored.y(), EPSILON);
        assertEquals(320d, anchored.width(), EPSILON);
        assertEquals(180d, anchored.height(), EPSILON);
        assertFalse(resolved.normalized());
    }

    @Test
    void zeroWidthFailsInsteadOfProducingAResolvedRect() {
        RigView.Rect.Normalized rect = new RigView.Rect.Normalized(
                AnimatedNumber.constant(0f),
                AnimatedNumber.constant(0f),
                AnimatedNumber.formula("0"),
                AnimatedNumber.constant(1f));

        ResolvedRectEvaluator.Result result = RECT_EVALUATOR.evaluate(
                rect, input(ExpressionSource.LayoutField.WIDTH, 0f));

        assertTrue(result.failed());
        Diagnostic diagnostic = ((ResolvedRectEvaluator.Result.Failure) result).diagnostic();
        assertEquals(LayoutDiagnostics.LAYOUT_RECT_INVALID, diagnostic.code());
        assertNotNull(diagnostic.location());
        assertTrue(diagnostic.location().fieldPath().contains("width"),
                diagnostic.location().fieldPath());
        assertTrue(messageOf(diagnostic).contains("positive"), messageOf(diagnostic));
    }

    @Test
    void negativeHeightFailsInAnchoredMode() {
        RigView.Rect.Anchored rect = new RigView.Rect.Anchored(
                RigView.Rect.Corner.TOP_LEFT,
                AnimatedNumber.constant(0f),
                AnimatedNumber.constant(0f),
                AnimatedNumber.constant(320f),
                AnimatedNumber.formula("-1"));

        ResolvedRectEvaluator.Result result = RECT_EVALUATOR.evaluate(
                rect, input(ExpressionSource.LayoutField.HEIGHT, 0f));

        assertTrue(result.failed());
        Diagnostic diagnostic = ((ResolvedRectEvaluator.Result.Failure) result).diagnostic();
        assertEquals(LayoutDiagnostics.LAYOUT_RECT_INVALID, diagnostic.code());
        assertTrue(diagnostic.location().fieldPath().contains("height"),
                diagnostic.location().fieldPath());
    }

    // endregion

    // region 测试工具

    private static EvaluationContext context() {
        CameraRig rig = CameraRig.create(RIG, "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        AnimationSnapshot snapshot = new AnimationSnapshot(REVISION,
                CameraAnimation.create("doc", rig, new TrackId("source")));
        FrameFacts facts = new FrameFacts(snapshot, DIMENSION, 1L, 10L, 0.5f, 1L,
                Map.of(), Map.of(), true);
        TrackLookup lookup = trackId -> Optional.empty();
        TrackIndex index = new TrackIndex() {
            @Override
            public AnimationSnapshot snapshot() {
                return snapshot;
            }

            @Override
            public Optional<Entry> find(TrackId trackId) {
                return Optional.empty();
            }

            @Override
            public List<Diagnostic> diagnostics() {
                return List.of();
            }
        };

        return EvaluationContext.bind(1L, snapshot, facts, lookup, index);
    }

    private static LayoutEvalInput input(ExpressionSource.LayoutField field, float time) {
        return input(field, time, 0.25f);
    }

    private static LayoutEvalInput input(ExpressionSource.LayoutField field,
                                         float time,
                                         float progress) {
        return new LayoutEvalInput(context(), EvaluationSession.create(4096L, 16), RIG, field,
                time, progress, 12.5f, SpatialReadings.EMPTY, 12345L, CURVE_SAMPLER);
    }

    private static LayoutValue.Success success(LayoutValue value) {
        assertTrue(value.succeeded(), () -> "expected layout success, got " + value.optionalDiagnostic());
        return (LayoutValue.Success) value;
    }

    private static LayoutValue.Failure failure(LayoutValue value) {
        assertTrue(value.failed(), "expected layout failure, got a value");
        return (LayoutValue.Failure) value;
    }

    private static ResolvedRect resolved(ResolvedRectEvaluator.Result result) {
        assertTrue(result.succeeded(),
                () -> "expected resolved rect, got " + result.optionalDiagnostic());
        return ((ResolvedRectEvaluator.Result.Success) result).rect();
    }

    private static String messageOf(Diagnostic diagnostic) {
        return diagnostic.message() instanceof LocalizedMessage.Literal literal
                ? literal.text()
                : diagnostic.message().toString();
    }

    private static Curve curve(Keyframe... keys) {
        return new Curve(List.of(keys), Curve.Wrap.CLAMP, Curve.Wrap.CLAMP);
    }

    private static Keyframe key(String id,
                                float time,
                                float value,
                                Curve.Interpolation interpolation) {
        return new Keyframe(new KeyId(id), time, new Constant(value),
                0f, 0f, 1f, 1f, interpolation);
    }

    private static Keyframe formulaKey(String id,
                                       float time,
                                       String expression,
                                       Curve.Interpolation interpolation) {
        return new Keyframe(new KeyId(id), time, new Formula(expression),
                0f, 0f, 1f, 1f, interpolation);
    }

    // endregion
}
