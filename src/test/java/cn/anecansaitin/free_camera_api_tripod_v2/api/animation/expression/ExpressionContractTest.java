package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.FrameFacts;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialReadings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackLookup;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 表达式编译 / 求值契约的结构不变量（R1 契约批次）。
///
/// 本批只交付接口与结果类型，**不包含**编译器或求值器实现；因此测试覆盖：
/// 引用登记与容器隔离、诊断区间、编译结果分支不变量、求值输入校验、求值结果不泄漏非有限值。
class ExpressionContractTest {

    // region 引用

    @Test
    void referencesCopyContainerAndExposeStableOrder() {
        List<ExpressionReference> source = new ArrayList<>(List.of(
                ExpressionReference.sample("beat", 0, 14),
                ExpressionReference.variable("speed", 20, 25),
                ExpressionReference.function("noise", 2, 30, 35),
                ExpressionReference.variable("speed", 40, 45)));

        ExpressionReferences references = new ExpressionReferences(source);
        source.clear();

        assertEquals(4, references.references().size());
        assertEquals(List.of("beat"), references.sampleNames());
        assertEquals(List.of("speed"), references.variableNames());
        assertEquals(1, references.functions().size());
        assertEquals(List.of("speed"), references.names(ExpressionReferenceKind.VARIABLE));
        assertTrue(references.contains(ExpressionReferenceKind.SAMPLE, "beat"));
        assertFalse(references.contains(ExpressionReferenceKind.SAMPLE, "missing"));
        assertEquals(Set.of("noise/2"), references.functionSignatures());
        assertFalse(references.isEmpty());
        assertTrue(ExpressionReferences.EMPTY.isEmpty());
    }

    @Test
    void referenceRejectsInvalidSpansAndArgumentCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> ExpressionReference.variable(" ", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ExpressionReference.variable("x", -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ExpressionReference.variable("x", 3, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpressionReference(ExpressionReferenceKind.FUNCTION, "f", -1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpressionReference(ExpressionReferenceKind.VARIABLE, "x", 2, 0, 1));

        assertEquals(0, ExpressionReference.function("f", 0, 0, 1).argumentCount());
    }

    // endregion

    // region 诊断与编译结果

    @Test
    void diagnosticsValidateSpansAndClampWholeSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExpressionDiagnostic(ExpressionDiagnosticCode.SYNTAX_ERROR, "", 2, 1));

        ExpressionDiagnostic whole = ExpressionDiagnostic.overSource(
                ExpressionDiagnosticCode.SOURCE_TOO_LARGE, "too long", 42);

        assertEquals(0, whole.start());
        assertEquals(42, whole.end());
        assertEquals(0, ExpressionDiagnostic.overSource(
                ExpressionDiagnosticCode.SYNTAX_ERROR, "", -5).end());
    }

    @Test
    void compilationResultKeepsSuccessAndFailureDisjoint() {
        assertThrows(IllegalArgumentException.class, () -> new CompilationResult.Rejected(List.of()));
        assertThrows(NullPointerException.class, () -> new CompilationResult.Compiled(null));

        CompilationResult.Rejected rejected = new CompilationResult.Rejected(List.of(
                new ExpressionDiagnostic(ExpressionDiagnosticCode.SYNTAX_ERROR, "unexpected ')'", 3, 4)));

        assertFalse(rejected.compiled());
        assertTrue(rejected.compiledExpression().isEmpty());
        assertEquals(1, rejected.diagnostics().size());

        CompiledExpression expression = new CompiledExpression() {
            @Override
            public String source() {
                return "t + 1";
            }

            @Override
            public ExpressionReferences references() {
                return ExpressionReferences.EMPTY;
            }
        };
        CompilationResult.Compiled compiled = new CompilationResult.Compiled(expression);

        assertTrue(compiled.compiled());
        assertEquals("t + 1", compiled.compiledExpression().orElseThrow().source());
        assertTrue(compiled.diagnostics().isEmpty());
    }

    // endregion

    // region 求值输入

    @Test
    void expressionInputsValidateSourceSessionAndBuiltins() {
        EvaluationContext context = context();
        EvaluationSession session = EvaluationSession.create(128L, 64);
        ExpressionSource source = new ExpressionSource.Channel(new TrackId("track"), null);

        ExpressionInputs valid = new ExpressionInputs(context, source, null, 1.5f, 0.5f, 20f,
                SpatialReadings.EMPTY, 7L, session, unusedSampler());

        assertEquals(64, valid.session().maxDepth());
        assertEquals(128L, valid.session().remainingWork());
        assertEquals(source, valid.source());

        assertThrows(IllegalArgumentException.class, () -> new ExpressionInputs(
                context, source, null, -1f, 0.5f, 20f, SpatialReadings.EMPTY, 0L, session, unusedSampler()));
        assertThrows(IllegalArgumentException.class, () -> new ExpressionInputs(
                context, source, null, 1f, 1.5f, 20f, SpatialReadings.EMPTY, 0L, session, unusedSampler()));
        assertThrows(IllegalArgumentException.class, () -> new ExpressionInputs(
                context, source, null, 1f, 0.5f, Float.NaN, SpatialReadings.EMPTY, 0L, session, unusedSampler()));
        assertThrows(NullPointerException.class, () -> new ExpressionInputs(
                context, source, null, 1f, 0.5f, 20f, SpatialReadings.EMPTY, 0L, null, unusedSampler()));
        assertThrows(NullPointerException.class, () -> new ExpressionInputs(
                context, null, null, 1f, 0.5f, 20f, SpatialReadings.EMPTY, 0L, session, unusedSampler()));
    }

    @Test
    void expressionSourceDistinguishesFieldAndKey() {
        ExpressionSource channel = new ExpressionSource.Channel(new TrackId("track"), new KeyId("key"));
        ExpressionSource layout = new ExpressionSource.Layout(new RigId("rig"),
                ExpressionSource.LayoutField.WIDTH, new KeyId("layout-key"));

        assertEquals(new TrackId("track"), ((ExpressionSource.Channel) channel).trackId());
        assertEquals(new KeyId("key"), ((ExpressionSource.Channel) channel).keyId());
        assertEquals(ExpressionSource.LayoutField.WIDTH,
                ((ExpressionSource.Layout) layout).field());
        assertEquals(new KeyId("layout-key"), ((ExpressionSource.Layout) layout).keyId());

        // 同一机位同一布局的 x / y 必须是不同来源。
        assertNotEquals(
                ExpressionSource.Layout.of(new RigId("rig"), ExpressionSource.LayoutField.X),
                ExpressionSource.Layout.of(new RigId("rig"), ExpressionSource.LayoutField.Y));

        assertThrows(IllegalArgumentException.class,
                () -> new ExpressionSource.Variable(" "));
    }

    @Test
    void resourceFailuresAreNotCacheableInSharedCache() {
        assertFalse(ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED.cacheableInSharedCache());
        assertFalse(ExpressionDiagnosticCode.DEPTH_LIMIT_EXCEEDED.cacheableInSharedCache());
        assertFalse(ExpressionDiagnosticCode.INTERNAL_ERROR.cacheableInSharedCache());
        assertTrue(ExpressionDiagnosticCode.NON_FINITE_RESULT.cacheableInSharedCache());
        assertTrue(ExpressionDiagnosticCode.SAMPLE_UNKNOWN.cacheableInSharedCache());
        assertTrue(ExpressionDiagnosticCode.SYNTAX_ERROR.cacheableInSharedCache());
        assertTrue(ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED.executionStateDependent());
        assertFalse(ExpressionDiagnosticCode.NON_FINITE_RESULT.executionStateDependent());
    }

    @Test
    void expressionFailureKeepsSourceAndCauseChain() {
        ExpressionDiagnostic entry = new ExpressionDiagnostic(
                ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED, "budget", 0, 4);
        ExpressionDiagnostic inner = new ExpressionDiagnostic(
                ExpressionDiagnosticCode.NON_FINITE_RESULT, "division by zero", 6, 13);

        ExpressionFailure failure = new ExpressionFailure(
                new ExpressionSource.Channel(new TrackId("track"), null),
                entry,
                List.of(new ExpressionFailure(new ExpressionSource.Function("f"), inner, List.of())));

        assertEquals(ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED, failure.code());
        assertEquals(ExpressionDiagnosticCode.NON_FINITE_RESULT,
                failure.innermostDiagnostic().orElseThrow().code());
        assertEquals(ExpressionSource.Function.class,
                failure.innermost().source().getClass());

        ExpressionSample.Failure sample = new ExpressionSample.Failure(failure);

        assertEquals(ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED,
                sample.optionalDiagnostic().orElseThrow().code());
        assertEquals(failure, sample.optionalFailure().orElseThrow());
        assertFalse(sample.succeeded());
    }

    @Test
    void expressionSampleNeverCarriesNonFiniteValue() {
        assertEquals(2.5f, new ExpressionSample.Value(2.5f).optionalValue().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new ExpressionSample.Value(Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new ExpressionSample.Value(Float.POSITIVE_INFINITY));

        ExpressionSample.Failure failure = new ExpressionSample.Failure(new ExpressionFailure(
                new ExpressionSource.Document(),
                new ExpressionDiagnostic(
                        ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED, "budget", 0, 3),
                List.of()));

        assertFalse(failure.succeeded());
        assertTrue(failure.optionalValue().isEmpty());
        assertEquals(ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED,
                failure.optionalDiagnostic().orElseThrow().code());
    }

    // endregion

    // region 测试替身

    private static EvaluationContext context() {
        CameraRig rig = CameraRig.create(new RigId("rig"), "rig", List.of(),
                new TrackId("aim"), new TrackId("active"));
        CameraAnimation document = CameraAnimation.create("doc", rig, new TrackId("source"));
        AnimationSnapshot snapshot = new AnimationSnapshot(new Revision(1), document);

        FrameFacts facts = new FrameFacts(
                snapshot,
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                1L, 0L, 0.5f, 0L, Map.of(), Map.of(), true);

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

        return EvaluationContext.bind(0L, snapshot, facts, lookup, index);
    }

    private static CurveSampler unusedSampler() {
        return request -> {
            throw new AssertionError("sampler must not be used by contract tests");
        };
    }

    // endregion
}
