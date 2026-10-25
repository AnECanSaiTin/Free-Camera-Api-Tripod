package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReference;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferenceKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 第一版表达式编译器的行为口径：语法、引用、样本、内建 arity、预算与诊断区间。
///
/// 本测试只依赖 JDK 与 `api.animation.expression` 契约，不建立文档、世界或采样器替身：
/// 编译器按设计不判断符号表、采样绑定与运行期语义。求值器的数值语义（`step`、惰性
/// `select`、`noise`、`random`）不在本测试范围内——求值器尚未实现。
class DefaultExpressionCompilerTest {

    private static final DefaultExpressionCompiler COMPILER = new DefaultExpressionCompiler();

    private static final int MAX_SOURCE_LENGTH = DefaultExpressionCompiler.MAX_SOURCE_LENGTH;
    private static final int MAX_NESTING_DEPTH = DefaultExpressionCompiler.MAX_NESTING_DEPTH;

    // region 语法：算术、优先级、括号、一元

    @Test
    void arithmeticUsesPrecedenceParenthesesAndLeftAssociativity() {
        ExpressionNode.Binary add = binary(compile("1 + 2 * 3").ast().root());
        assertEquals(BinaryOperator.ADD, add.operator());
        assertEquals(1.0, literal(add.left()));

        ExpressionNode.Binary multiply = binary(add.right());
        assertEquals(BinaryOperator.MULTIPLY, multiply.operator());
        assertEquals(2.0, literal(multiply.left()));
        assertEquals(3.0, literal(multiply.right()));

        ExpressionNode.Binary grouped = binary(compile("(1 + 2) * 3").ast().root());
        assertEquals(BinaryOperator.MULTIPLY, grouped.operator());
        assertEquals(BinaryOperator.ADD, binary(grouped.left()).operator());
        assertEquals(3.0, literal(grouped.right()));

        // 同级左结合：1 - 2 - 3 是 (1 - 2) - 3，不是 1 - (2 - 3)。
        ExpressionNode.Binary subtraction = binary(compile("1 - 2 - 3").ast().root());
        assertEquals(BinaryOperator.SUBTRACT, subtraction.operator());
        assertEquals(3.0, literal(subtraction.right()));
        assertEquals(BinaryOperator.SUBTRACT, binary(subtraction.left()).operator());

        // * / % 同级左结合：7 % 4 / 2 是 (7 % 4) / 2。
        ExpressionNode.Binary division = binary(compile("7 % 4 / 2").ast().root());
        assertEquals(BinaryOperator.DIVIDE, division.operator());
        assertEquals(BinaryOperator.MODULO, binary(division.left()).operator());
    }

    @Test
    void decimalAndScientificLiteralsAreSupported() {
        assertEquals(150.0, literal(compile("1.5e2").ast().root()));
        assertEquals(0.2, literal(compile("2E-1").ast().root()));
        assertEquals(42.0, literal(compile("42.").ast().root()));
        assertEquals(3.25, literal(compile("3.25").ast().root()));

        // 编译器不做常量折叠：0.5 + 1 仍是二元节点。
        ExpressionNode.Binary sum = binary(compile("0.5 + 1").ast().root());
        assertEquals(BinaryOperator.ADD, sum.operator());
        assertEquals(0.5, literal(sum.left()));
        assertEquals(1.0, literal(sum.right()));
    }

    @Test
    void unaryOperatorsBindTighterThanArithmetic() {
        ExpressionNode.Unary negate = unary(compile("-3").ast().root());
        assertEquals(UnaryOperator.NEGATE, negate.operator());
        assertEquals(3.0, literal(negate.operand()));

        ExpressionNode.Unary positive = unary(compile("+ -3").ast().root());
        assertEquals(UnaryOperator.POSITIVE, positive.operator());
        assertEquals(UnaryOperator.NEGATE, unary(positive.operand()).operator());

        ExpressionNode.Binary multiply = binary(compile("2 * -3").ast().root());
        assertEquals(BinaryOperator.MULTIPLY, multiply.operator());
        assertEquals(UnaryOperator.NEGATE, unary(multiply.right()).operator());

        ExpressionNode.Unary grouped = unary(compile("-(1 + 2)").ast().root());
        assertEquals(UnaryOperator.NEGATE, grouped.operator());
        assertEquals(BinaryOperator.ADD, binary(grouped.operand()).operator());
    }

    // endregion

    // region 引用：变量、函数、样本、内建

    @Test
    void variableAndFunctionReferencesAreSortedBySourcePosition() {
        CompiledExpressionImpl compiled = compile("alpha + f(beta) * gamma");
        List<ExpressionReference> references = compiled.references().references();

        assertEquals(4, references.size());
        assertEquals(List.of(
                        ExpressionReferenceKind.VARIABLE,
                        ExpressionReferenceKind.FUNCTION,
                        ExpressionReferenceKind.VARIABLE,
                        ExpressionReferenceKind.VARIABLE),
                references.stream().map(ExpressionReference::kind).toList());

        assertReference(references.get(0), ExpressionReferenceKind.VARIABLE, "alpha", -1, 0, 5);
        assertReference(references.get(1), ExpressionReferenceKind.FUNCTION, "f", 1, 8, 9);
        assertReference(references.get(2), ExpressionReferenceKind.VARIABLE, "beta", -1, 10, 14);
        assertReference(references.get(3), ExpressionReferenceKind.VARIABLE, "gamma", -1, 18, 23);

        assertAscending(references);
        assertEquals(List.of("alpha", "beta", "gamma"), compiled.references().variableNames());
        assertEquals(Set.of("f/1"), compiled.references().functionSignatures());
        assertFalse(compiled.references().contains(ExpressionReferenceKind.VARIABLE, "f"));
    }

    @Test
    void customFunctionWithoutArgumentsRegistersZeroArity() {
        ExpressionNode.Call call = as(ExpressionNode.Call.class, compile("f()").ast().root());
        assertEquals("f", call.name());
        assertTrue(call.arguments().isEmpty());

        ExpressionReference reference = compile("f()").references().references().getFirst();
        assertReference(reference, ExpressionReferenceKind.FUNCTION, "f", 0, 0, 1);
    }

    @Test
    void nestedCustomCallsKeepArgumentCountsAndSourceOrder() {
        CompiledExpressionImpl compiled = compile("outer(inner(1), x)");
        List<ExpressionReference> references = compiled.references().references();

        assertEquals(3, references.size());
        assertReference(references.get(0), ExpressionReferenceKind.FUNCTION, "outer", 2, 0, 5);
        assertReference(references.get(1), ExpressionReferenceKind.FUNCTION, "inner", 1, 6, 11);
        assertReference(references.get(2), ExpressionReferenceKind.VARIABLE, "x", -1, 16, 17);
        assertAscending(references);
    }

    @Test
    void builtinQuantitiesAreNotExternalReferences() {
        CompiledExpressionImpl compiled = compile("t + p * wt");

        assertTrue(compiled.references().isEmpty());
        assertTrue(compiled.references().variableNames().isEmpty());

        ExpressionNode.Binary add = binary(compiled.ast().root());
        assertEquals(BinaryOperator.ADD, add.operator());
        assertEquals(BuiltinQuantity.TIME, quantity(add.left()).quantity());

        ExpressionNode.Binary multiply = binary(add.right());
        assertEquals(BuiltinQuantity.PROGRESS, quantity(multiply.left()).quantity());
        assertEquals(BuiltinQuantity.WORLD_TIME, quantity(multiply.right()).quantity());
    }

    @Test
    void builtinFunctionsAreNotExternalReferences() {
        CompiledExpressionImpl compiled = compile("sin(t) + clamp(x, 0, 1)");
        List<ExpressionReference> references = compiled.references().references();

        assertEquals(1, references.size());
        assertReference(references.getFirst(), ExpressionReferenceKind.VARIABLE, "x", -1, 15, 16);

        ExpressionNode.Binary add = binary(compiled.ast().root());
        ExpressionNode.BuiltinCall sine = as(ExpressionNode.BuiltinCall.class, add.left());
        assertEquals(BuiltinFunction.SIN, sine.function());
        assertEquals(1, sine.arguments().size());

        ExpressionNode.BuiltinCall clamp = as(ExpressionNode.BuiltinCall.class, add.right());
        assertEquals(BuiltinFunction.CLAMP, clamp.function());
        assertEquals(3, clamp.arguments().size());
    }

    @Test
    void selectKeepsAllArgumentsInTheTreeSoBothBranchesStayVisible() {
        CompiledExpressionImpl compiled = compile("select(c, x, y)");
        ExpressionNode.BuiltinCall select = as(ExpressionNode.BuiltinCall.class, compiled.ast().root());

        assertEquals(BuiltinFunction.SELECT, select.function());
        assertEquals(3, select.arguments().size());
        assertEquals(List.of("c", "x", "y"), compiled.references().variableNames());
    }

    @Test
    void sampleRegistersSampleReferenceOnTheStringLiteralSpan() {
        CompiledExpressionImpl compiled = compile("sample(\"a\")");
        ExpressionReferences references = compiled.references();

        assertEquals(1, references.references().size());
        assertReference(references.references().getFirst(),
                ExpressionReferenceKind.SAMPLE, "a", -1, 7, 10);

        ExpressionNode.Sample sample = as(ExpressionNode.Sample.class, compiled.ast().root());
        assertEquals("a", sample.name());
        assertEquals(7, sample.nameStart());
        assertEquals(10, sample.nameEnd());
        assertEquals(0, sample.start());
        assertEquals(11, sample.end());
        assertFalse(sample.hasFallback());
        assertTrue(sample.fallbackNode().isEmpty());
    }

    @Test
    void sampleWithFallbackRegistersOnlyTheSampleReference() {
        CompiledExpressionImpl compiled = compile("sample(\"a\", 1)");
        List<ExpressionReference> references = compiled.references().references();

        assertEquals(1, references.size());
        assertReference(references.getFirst(), ExpressionReferenceKind.SAMPLE, "a", -1, 7, 10);

        ExpressionNode.Sample sample = as(ExpressionNode.Sample.class, compiled.ast().root());
        assertTrue(sample.hasFallback());
        assertEquals(1.0, literal(sample.fallback()));
        assertEquals(0, sample.start());
        assertEquals(14, sample.end());
    }

    @Test
    void nestedSampleFallbackKeepsAscendingReferences() {
        CompiledExpressionImpl compiled = compile("sample(\"a\", sample(\"b\"))");
        List<ExpressionReference> references = compiled.references().references();

        assertEquals(2, references.size());
        assertReference(references.get(0), ExpressionReferenceKind.SAMPLE, "a", -1, 7, 10);
        assertReference(references.get(1), ExpressionReferenceKind.SAMPLE, "b", -1, 19, 22);
        assertAscending(references);

        ExpressionNode.Sample outer = as(ExpressionNode.Sample.class, compiled.ast().root());
        ExpressionNode.Sample inner = as(ExpressionNode.Sample.class, outer.fallback());
        assertEquals("b", inner.name());
        assertFalse(inner.hasFallback());
    }

    @Test
    void escapedStringLiteralIsDecodedForTheSampleName() {
        CompiledExpressionImpl compiled = compile("sample(\"a\\\"b\")");
        ExpressionReference reference = compiled.references().references().getFirst();

        assertEquals("a\"b", reference.name());
        assertEquals(7, reference.start());
        assertEquals(13, reference.end());
    }

    @Test
    void sampleRejectsNonLiteralNamesBlankNamesAndExtraArguments() {
        assertSampleInvalid("sample(name)", 7, 11);
        assertSampleInvalid("sample(1)", 7, 8);
        assertSampleInvalid("sample()", 7, 8);
        assertSampleInvalid("sample(\"\")", 7, 9);
        assertSampleInvalid("sample(\"   \")", 7, 12);
        assertSampleInvalid("sample(\"a\", 1, 2)", 13, 14);
    }

    // endregion

    // region 内建 arity

    @Test
    void builtinArityIsCheckedAtCompileTime() {
        assertArityMismatch("sin()");
        assertArityMismatch("sin(1, 2)");
        assertArityMismatch("abs(1, 2)");
        assertArityMismatch("clamp(1, 2)");
        assertArityMismatch("pow(1)");
        assertArityMismatch("min(1)");
        assertArityMismatch("max(1, 2, 3)");
        assertArityMismatch("select(1, 2)");
        assertArityMismatch("select(1, 2, 3, 4)");
        assertArityMismatch("noise(1)");
        assertArityMismatch("noise(1, 2, 3)");
        assertArityMismatch("random(1)");
        assertArityMismatch("step()");
        assertArityMismatch("step(1, 2, 3)");

        ExpressionDiagnostic diagnostic = diagnostic("sin(1, 2)",
                ExpressionDiagnosticCode.BUILTIN_ARITY_MISMATCH);
        assertEquals(0, diagnostic.start());
        assertEquals(9, diagnostic.end());
        assertTrue(diagnostic.detail().contains("sin"), diagnostic.detail());
        assertTrue(diagnostic.detail().contains("exactly 1 argument"), diagnostic.detail());
    }

    @Test
    void builtinCallsWithAcceptedArityCompile() {
        compile("step(1)");
        compile("step(1, 2)");
        compile("select(1, 2, 3)");
        compile("noise(1, 2)");
        compile("random()");
        compile("min(1, 2)");
        compile("max(1, 2)");
        compile("clamp(1, 2, 3)");
        compile("abs(-1)");
        compile("sin(t)");
        compile("cos(t)");
        compile("tan(t)");
        compile("sqrt(4)");
        compile("floor(1.5)");
        compile("ceil(1.5)");
        compile("round(1.5)");
        compile("pow(2, 3)");
    }

    @Test
    void builtinArityTableMatchesTheDocumentedSignatures() {
        assertEquals(1, BuiltinFunction.STEP.minArity());
        assertEquals(2, BuiltinFunction.STEP.maxArity());
        assertTrue(BuiltinFunction.STEP.accepts(1));
        assertTrue(BuiltinFunction.STEP.accepts(2));
        assertFalse(BuiltinFunction.STEP.accepts(3));
        assertFalse(BuiltinFunction.STEP.accepts(0));

        assertTrue(BuiltinFunction.SELECT.accepts(3));
        assertFalse(BuiltinFunction.SELECT.accepts(2));
        assertTrue(BuiltinFunction.NOISE.accepts(2));
        assertFalse(BuiltinFunction.NOISE.accepts(1));
        assertTrue(BuiltinFunction.RANDOM.accepts(0));
        assertFalse(BuiltinFunction.RANDOM.accepts(1));
        assertTrue(BuiltinFunction.CLAMP.accepts(3));
        assertTrue(BuiltinFunction.POW.accepts(2));
        assertTrue(BuiltinFunction.MIN.accepts(2));
        assertTrue(BuiltinFunction.MAX.accepts(2));
        assertTrue(BuiltinFunction.ABS.accepts(1));
        assertEquals("exactly 1 argument", BuiltinFunction.SIN.arityText());
        assertEquals("1 or 2 arguments", BuiltinFunction.STEP.arityText());
    }

    // endregion

    // region 语法错误与诊断区间

    @Test
    void syntaxErrorsReportStableCodeAndBoundedSpan() {
        List<String> sources = List.of(
                "(1 + 2",       // 缺右括号
                "1 +",          // 尾随运算符
                "1 + * 2",      // 运算符处于操作数位置
                "1 2",          // 表达式后还有 token
                "1)",           // 多余右括号
                "sin(1",        // 调用未闭合
                ".5",           // 小数必须以数字开头
                "1e",           // 指数缺少数字
                "1e999",        // 字面量不是有限值
                "\"a\"",        // 字符串只能出现在 sample 名字位置
                "sample(\"a\"", // sample 未闭合
                "1..2",         // 畸形数字
                "1abc",         // 畸形数字
                "1 + &",        // 意外字符
                "变量"           // 非 ASCII 标识符
        );

        for (String source : sources) {
            ExpressionDiagnostic diagnostic = diagnostic(source);
            assertEquals(ExpressionDiagnosticCode.SYNTAX_ERROR, diagnostic.code(),
                    () -> "unexpected diagnostic code for source: " + source);
            assertSpanInBounds(diagnostic, source.length());
            assertFalse(diagnostic.detail().isBlank());
        }
    }

    @Test
    void specificSyntaxDiagnosticsPointAtTheOffendingToken() {
        assertSpan("1 + * 2", 4, 5);   // '*'
        assertSpan("1 2", 2, 3);       // 尾随的 '2'
        assertSpan("1)", 1, 2);        // ')'
        assertSpan("1 + &", 4, 5);     // '&'
        assertSpan("1 +", 3, 3);       // EOF，零宽
        assertSpan("(", 1, 1);         // EOF，零宽
        assertSpan("(1 + 2", 6, 6);    // EOF，零宽
        assertSpan("sin(1", 5, 5);     // EOF，零宽
        assertSpan("sample(\"a\"", 10, 10); // EOF，零宽
    }

    @Test
    void eofDiagnosticsNeverExceedTheSourceLength() {
        List<String> sources = List.of("1 +", "(", "(1 + 2", "sin(1", "sample(\"a\"");

        for (String source : sources) {
            ExpressionDiagnostic diagnostic = diagnostic(source, ExpressionDiagnosticCode.SYNTAX_ERROR);
            assertEquals(source.length(), diagnostic.start(), () -> "source: " + source);
            assertEquals(source.length(), diagnostic.end(), () -> "source: " + source);
            assertSpanInBounds(diagnostic, source.length());
        }
    }

    @Test
    void emptyAndBlankSourcesAreRejectedWithWholeSourceSpan() {
        List<String> sources = List.of("", " ", "   ", "\n\t", " \r\n ");

        for (String source : sources) {
            ExpressionDiagnostic diagnostic = diagnostic(source, ExpressionDiagnosticCode.SYNTAX_ERROR);
            assertEquals(0, diagnostic.start(), () -> "source: [" + source + "]");
            assertEquals(source.length(), diagnostic.end(), () -> "source: [" + source + "]");
            assertSpanInBounds(diagnostic, source.length());
            assertFalse(diagnostic.detail().isBlank());
        }
    }

    @Test
    void rejectedCompilationNeverCarriesAnEvaluableHandle() {
        CompilationResult.Rejected rejected = reject("1 +");

        assertFalse(rejected.compiled());
        assertTrue(rejected.compiledExpression().isEmpty());
        assertEquals(1, rejected.diagnostics().size());
        assertThrows(UnsupportedOperationException.class, () -> rejected.diagnostics().clear());
    }

    // endregion

    // region 预算：嵌套与长度

    @Test
    void nestingLimitIsEnforcedAtTheDocumentedBoundary() {
        String allowed = "(".repeat(MAX_NESTING_DEPTH) + "1" + ")".repeat(MAX_NESTING_DEPTH);
        compile(allowed);

        String tooDeep = "(".repeat(MAX_NESTING_DEPTH + 1) + "1" + ")".repeat(MAX_NESTING_DEPTH + 1);
        ExpressionDiagnostic diagnostic = diagnostic(tooDeep,
                ExpressionDiagnosticCode.NESTING_TOO_DEEP);
        assertSpanInBounds(diagnostic, tooDeep.length());
        assertEquals(MAX_NESTING_DEPTH, diagnostic.start());

        String nestedCalls = "sin(".repeat(200) + "t" + ")".repeat(200);
        ExpressionDiagnostic callDiagnostic = diagnostic(nestedCalls,
                ExpressionDiagnosticCode.NESTING_TOO_DEEP);
        assertSpanInBounds(callDiagnostic, nestedCalls.length());
    }

    @Test
    void sourceLengthLimitUsesWholeSourceSpan() {
        String tooLarge = "1".repeat(MAX_SOURCE_LENGTH + 1);
        ExpressionDiagnostic diagnostic = diagnostic(tooLarge,
                ExpressionDiagnosticCode.SOURCE_TOO_LARGE);

        assertEquals(0, diagnostic.start());
        assertEquals(tooLarge.length(), diagnostic.end());
        assertSpanInBounds(diagnostic, tooLarge.length());

        // 恰好等于上限的合法源码必须仍然编译成功。
        String atLimit = "1" + " ".repeat(MAX_SOURCE_LENGTH - 4) + "+ 1";
        assertEquals(MAX_SOURCE_LENGTH, atLimit.length());
        compile(atLimit);
    }

    // endregion

    // region 编译产物：不可变值、源码与 AST

    @Test
    void compiledExpressionIsAnImmutableValueWithAstAccess() {
        CompiledExpressionImpl first = compile("t + sample(\"beat\", x) * 2");
        CompiledExpressionImpl second = compile("t + sample(\"beat\", x) * 2");

        assertEquals("t + sample(\"beat\", x) * 2", first.source());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, compile("t + 1"));
        assertEquals(first.references(), second.references());
        assertNotNull(first.ast());
        assertEquals(first.ast(), second.ast());

        // api 句柄仍然只暴露源码与引用；AST 由 core 实现类型额外提供。
        CompiledExpression handle = first;
        assertEquals(first.source(), handle.source());
        assertEquals(List.of("beat"), handle.references().sampleNames());
        assertEquals(List.of("x"), handle.references().variableNames());

        assertThrows(UnsupportedOperationException.class,
                () -> handle.references().references().clear());
    }

    // endregion

    // region 测试辅助

    /// 编译并断言成功；失败时报告全部诊断。
    private static CompiledExpressionImpl compile(String source) {
        CompilationResult result = COMPILER.compile(source);
        assertTrue(result.compiled(), () -> "expected successful compilation but got "
                + describeDiagnostics(result.diagnostics()));
        assertTrue(result.diagnostics().isEmpty());

        return as(CompiledExpressionImpl.class, result.compiledExpression().orElseThrow());
    }

    /// 编译并断言失败。
    private static CompilationResult.Rejected reject(String source) {
        CompilationResult result = COMPILER.compile(source);

        assertFalse(result.compiled(), () -> "expected rejection for source: " + source);
        assertTrue(result.compiledExpression().isEmpty(),
                () -> "rejected compilation must not carry a handle: " + source);

        return as(CompilationResult.Rejected.class, result);
    }

    /// 断言失败并返回唯一诊断（第一版只报首条错误）。
    private static ExpressionDiagnostic diagnostic(String source) {
        CompilationResult.Rejected rejected = reject(source);

        assertEquals(1, rejected.diagnostics().size(),
                () -> "v1 reports the first error only: " + describeDiagnostics(rejected.diagnostics()));

        return rejected.diagnostics().getFirst();
    }

    /// 断言失败、诊断码与区间边界。
    private static ExpressionDiagnostic diagnostic(String source, ExpressionDiagnosticCode expectedCode) {
        ExpressionDiagnostic diagnostic = diagnostic(source);

        assertEquals(expectedCode, diagnostic.code(), () -> "source: " + source
                + " -> " + diagnostic.code() + ": " + diagnostic.detail());
        assertSpanInBounds(diagnostic, source.length());

        return diagnostic;
    }

    /// 断言 `SYNTAX_ERROR` 并检查精确区间。
    private static void assertSpan(String source, int start, int end) {
        ExpressionDiagnostic diagnostic = diagnostic(source, ExpressionDiagnosticCode.SYNTAX_ERROR);

        assertEquals(start, diagnostic.start(), () -> "source: " + source);
        assertEquals(end, diagnostic.end(), () -> "source: " + source);
    }

    /// 断言 `SAMPLE_REFERENCE_INVALID` 并检查精确区间。
    private static void assertSampleInvalid(String source, int start, int end) {
        ExpressionDiagnostic diagnostic = diagnostic(source,
                ExpressionDiagnosticCode.SAMPLE_REFERENCE_INVALID);

        assertEquals(start, diagnostic.start(), () -> "source: " + source);
        assertEquals(end, diagnostic.end(), () -> "source: " + source);
    }

    /// 断言内建实参个数错误。
    private static void assertArityMismatch(String source) {
        diagnostic(source, ExpressionDiagnosticCode.BUILTIN_ARITY_MISMATCH);
    }

    /// 诊断区间必须满足 `0 <= start <= end <= source.length()`。
    private static void assertSpanInBounds(ExpressionDiagnostic diagnostic, int sourceLength) {
        assertTrue(diagnostic.start() >= 0,
                () -> "negative start: " + diagnostic.start());
        assertTrue(diagnostic.start() <= diagnostic.end(),
                () -> "start > end: [" + diagnostic.start() + ", " + diagnostic.end() + ")");
        assertTrue(diagnostic.end() <= sourceLength,
                () -> "end " + diagnostic.end() + " exceeds source length " + sourceLength);
    }

    /// 引用必须按源码位置升序。
    private static void assertAscending(List<ExpressionReference> references) {
        for (int index = 1; index < references.size(); index++) {
            ExpressionReference previous = references.get(index - 1);
            ExpressionReference current = references.get(index);

            assertTrue(previous.start() <= current.start(),
                    () -> "references are not sorted: " + references);
        }
    }

    /// 单条引用的完整断言。
    private static void assertReference(ExpressionReference reference,
                                        ExpressionReferenceKind kind,
                                        String name,
                                        int argumentCount,
                                        int start,
                                        int end) {
        assertEquals(kind, reference.kind());
        assertEquals(name, reference.name());
        assertEquals(argumentCount, reference.argumentCount());
        assertEquals(start, reference.start());
        assertEquals(end, reference.end());
    }

    /// 转为指定节点 / 类型；失败时给出实际类型。
    private static <T> T as(Class<T> type, Object value) {
        assertTrue(type.isInstance(value),
                () -> "expected " + type.getSimpleName() + " but got "
                        + (value == null ? "null" : value.getClass().getSimpleName()));
        return type.cast(value);
    }

    private static ExpressionNode.Binary binary(ExpressionNode node) {
        return as(ExpressionNode.Binary.class, node);
    }

    private static ExpressionNode.Unary unary(ExpressionNode node) {
        return as(ExpressionNode.Unary.class, node);
    }

    private static ExpressionNode.Quantity quantity(ExpressionNode node) {
        return as(ExpressionNode.Quantity.class, node);
    }

    private static double literal(ExpressionNode node) {
        return as(ExpressionNode.NumberLiteral.class, node).value();
    }

    private static String describeDiagnostics(List<ExpressionDiagnostic> diagnostics) {
        return diagnostics.stream()
                .map(diagnostic -> diagnostic.code() + "[" + diagnostic.start() + ", "
                        + diagnostic.end() + "): " + diagnostic.detail())
                .reduce((left, right) -> left + "; " + right)
                .orElse("<no diagnostics>");
    }

    // endregion
}
