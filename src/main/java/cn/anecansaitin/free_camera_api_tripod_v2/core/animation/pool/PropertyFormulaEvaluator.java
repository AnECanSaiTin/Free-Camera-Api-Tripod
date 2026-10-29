package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolEvaluationInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolSampleSource;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.BuiltinFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.BuiltinQuantity;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.CompiledExpressionImpl;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.DefaultExpressionCompiler;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionNode;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// 轨道池属性/符号公式的有限数值求值。
///
/// 支持 `t` / `p` / `wt`、算术、确定性内建函数、文档变量、自定义函数与变量 TrackRef 读数。
/// 采样读取与空间内建名仍返回结构化错误，不静默当 0。递归深度有上限，防止变量/函数相互
/// 引用造成的无限求值。
@NullMarked
final class PropertyFormulaEvaluator {

    private static final int MAX_DEPTH = 32;
    private static final DefaultExpressionCompiler COMPILER = new DefaultExpressionCompiler();

    private PropertyFormulaEvaluator() {
    }

    /// 只做编译检查；成功返回空，失败返回结构化细节。
    static Optional<String> compilationError(String source) {
        Objects.requireNonNull(source, "source");
        CompilationResult compilation = COMPILER.compile(source);

        return compilation instanceof CompilationResult.Rejected rejected
                ? Optional.of(rejected.diagnostics().isEmpty()
                        ? "Expression compilation failed"
                        : rejected.diagnostics().getFirst().detail())
                : Optional.empty();
    }

    static Result evaluate(String source, float time, String path, Revision revision) {
        return evaluate(source, new Ctx(TrackPoolEvaluationInputs.at(time), Symbols.EMPTY,
                Map.of(), TrackPoolSampleSource.NONE, 0, Map.of()), path, revision);
    }

    static Result evaluate(String source, TrackPoolEvaluationInputs inputs, String path, Revision revision) {
        return evaluate(source, new Ctx(inputs, Symbols.EMPTY, Map.of(),
                TrackPoolSampleSource.NONE, 0, Map.of()), path, revision);
    }

    static Result evaluate(String source,
                           TrackPoolEvaluationInputs inputs,
                           Symbols symbols,
                           Map<TrackId, CurveTrack> tracks,
                           String path,
                           Revision revision) {
        return evaluate(source, inputs, symbols, tracks, TrackPoolSampleSource.NONE, path, revision);
    }

    static Result evaluate(String source,
                           TrackPoolEvaluationInputs inputs,
                           Symbols symbols,
                           Map<TrackId, CurveTrack> tracks,
                           TrackPoolSampleSource samples,
                           String path,
                           Revision revision) {
        return evaluate(source, new Ctx(inputs, symbols, tracks, samples, 0, Map.of()),
                path, revision);
    }

    private static Result evaluate(String source, Ctx context, String path, Revision revision) {
        Objects.requireNonNull(source, "source");
        CompilationResult compilation = COMPILER.compile(source);

        if (compilation instanceof CompilationResult.Rejected rejected) {
            String message = rejected.diagnostics().isEmpty()
                    ? "Expression compilation failed"
                    : rejected.diagnostics().getFirst().detail();
            return Result.error(error(DiagnosticCode.EXPRESSION_INVALID, message, path, revision));
        }

        if (!(compilation instanceof CompilationResult.Compiled compiled)
                || !(compiled.expression() instanceof CompiledExpressionImpl implementation)) {
            return Result.error(error(DiagnosticCode.EXPRESSION_INVALID,
                    "Expression compiler returned an unsupported implementation", path, revision));
        }

        try {
            double value = evaluateNode(implementation.ast().root(), context, path, revision);

            if (!Double.isFinite(value)) {
                return Result.error(error(DiagnosticCode.EXPRESSION_INVALID,
                        "Expression result is not finite", path, revision));
            }

            return Result.value(value);
        } catch (FormulaFailure failure) {
            return Result.error(failure.diagnostic);
        }
    }

    private static double evaluateNode(ExpressionNode node, Ctx context, String path, Revision revision) {
        return switch (node) {
            case ExpressionNode.NumberLiteral literal -> literal.value();
            case ExpressionNode.Quantity quantity -> switch (quantity.quantity()) {
                case TIME -> context.inputs().time();
                case PROGRESS -> context.inputs().progress();
                case WORLD_TIME -> context.inputs().worldTime();
            };
            case ExpressionNode.Unary unary -> switch (unary.operator()) {
                case POSITIVE -> evaluateNode(unary.operand(), context, path, revision);
                case NEGATE -> -evaluateNode(unary.operand(), context, path, revision);
            };
            case ExpressionNode.Binary binary -> binary(binary, context, path, revision);
            case ExpressionNode.BuiltinCall call -> builtin(call, context, path, revision);
            case ExpressionNode.Variable variable -> variable(variable.name(), context, path, revision);
            case ExpressionNode.Call call -> function(call, context, path, revision);
            case ExpressionNode.Sample sample -> sample(sample, context, path, revision);
        };
    }

    private static double variable(String name, Ctx context, String path, Revision revision) {
        Double local = context.locals().get(name);

        if (local != null) {
            return local;
        }

        Variable variable = context.symbols().variable(name).orElse(null);

        if (variable == null) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Unknown variable: " + name, path, revision);
        }

        return valueSource(variable.source(), context, path, revision);
    }

    private static double valueSource(cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.ValueSource source,
                                      Ctx context,
                                      String path,
                                      Revision revision) {
        return switch (source) {
            case Constant constant -> constant.value();
            case Formula formula -> evaluateRecursive(formula.expression(), context, path, revision);
            case TrackRef ref -> curve(ref.trackId(), context, path, revision);
        };
    }

    private static double function(ExpressionNode.Call call,
                                   Ctx context,
                                   String path,
                                   Revision revision) {
        CustomFunction function = context.symbols().function(call.name()).orElse(null);

        if (function == null) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Unknown function: " + call.name(), path, revision);
        }

        if (function.parameters().size() != call.arguments().size()) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Function " + call.name() + " expects " + function.parameters().size()
                            + " arguments, got " + call.arguments().size(), path, revision);
        }

        Map<String, Double> locals = new HashMap<>(context.locals());

        for (int index = 0; index < function.parameters().size(); index++) {
            locals.put(function.parameters().get(index),
                    evaluateNode(call.arguments().get(index), context, path, revision));
        }

        Ctx nested = new Ctx(context.inputs(), context.symbols(), context.tracks(),
                context.samples(), context.depth() + 1, Map.copyOf(locals));
        return evaluateRecursive(function.body(), nested, path, revision);
    }

    private static double sample(ExpressionNode.Sample sample,
                                 Ctx context,
                                 String path,
                                 Revision revision) {
        SampleReading reading = context.samples().read(sample.name(), context.inputs());

        return switch (reading) {
            case SampleReading.Value value -> value.value();
            case SampleReading.Unknown unknown -> {
                if (sample.hasFallback()) {
                    yield evaluateNode(sample.fallbackNode().orElseThrow(), context, path, revision);
                }

                throw failure(DiagnosticCode.EXPRESSION_INVALID,
                        "Sample is unavailable: " + sample.name() + " (" + unknown.reason() + ")",
                        path, revision);
            }
            case SampleReading.CollectionError error -> throw failure(
                    DiagnosticCode.EXPRESSION_INVALID,
                    "Sample collection failed: " + sample.name() + " (" + error.reason() + ")",
                    path, revision);
        };
    }

    private static double evaluateRecursive(String expression,
                                            Ctx context,
                                            String path,
                                            Revision revision) {
        if (context.depth() >= MAX_DEPTH) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Formula recursion depth exceeds " + MAX_DEPTH, path, revision);
        }

        CompilationResult compilation = COMPILER.compile(expression);

        if (compilation instanceof CompilationResult.Rejected rejected) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    rejected.diagnostics().isEmpty()
                            ? "Expression compilation failed"
                            : rejected.diagnostics().getFirst().detail(), path, revision);
        }

        CompiledExpressionImpl implementation = (CompiledExpressionImpl) ((CompilationResult.Compiled) compilation)
                .expression();
        return evaluateNode(implementation.ast().root(), context, path, revision);
    }

    private static double curve(TrackId trackId, Ctx context, String path, Revision revision) {
        CurveTrack track = context.tracks().get(trackId);

        if (track == null) {
            throw failure(DiagnosticCode.BROKEN_REFERENCE,
                    "Variable TrackRef does not point to a curve track: " + trackId, path, revision);
        }

        Curve curve = track.curve();

        if (curve.isEmpty()) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Variable TrackRef points to an empty curve: " + trackId, path, revision);
        }

        if (curve.preMode() != Curve.Wrap.CLAMP || curve.postMode() != Curve.Wrap.CLAMP) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Variable TrackRef wrap is not available yet", path, revision);
        }

        double time = context.inputs().time();
        Keyframe first = curve.first();
        Keyframe last = curve.last();

        if (time <= first.time()) {
            return keyValue(first, context, path, revision);
        }

        if (time >= last.time()) {
            return keyValue(last, context, path, revision);
        }

        int index = 0;

        while (index + 1 < curve.keys().size() && curve.keys().get(index + 1).time() <= time) {
            index++;
        }

        Keyframe left = curve.keys().get(index);
        Keyframe right = curve.keys().get(index + 1);
        double leftValue = keyValue(left, context, path, revision);

        if (left.interpolation() == Curve.Interpolation.STEP) {
            return leftValue;
        }

        double rightValue = keyValue(right, context, path, revision);
        double normalized = (time - left.time()) / (right.time() - left.time());

        return switch (left.interpolation()) {
            case STEP -> leftValue;
            case LINEAR -> leftValue + (rightValue - leftValue) * normalized;
            case HERMITE -> cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CurveInterpolation.hermite(
                    normalized, right.time() - left.time(), leftValue, rightValue,
                    left.outSlope(), right.inSlope(), left.outLength(), right.inLength());
        };
    }

    private static double keyValue(Keyframe key, Ctx context, String path, Revision revision) {
        return switch (key.value()) {
            case Constant constant -> constant.value();
            case Formula formula -> evaluateRecursive(formula.expression(), context, path, revision);
            default -> throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Variable TrackRef key must be constant or formula", path, revision);
        };
    }

    private static double binary(ExpressionNode.Binary binary, Ctx context, String path, Revision revision) {
        double left = evaluateNode(binary.left(), context, path, revision);
        double right = evaluateNode(binary.right(), context, path, revision);

        return switch (binary.operator()) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> left / right;
            case MODULO -> left % right;
        };
    }

    private static double builtin(ExpressionNode.BuiltinCall call,
                                  Ctx context,
                                  String path,
                                  Revision revision) {
        List<ExpressionNode> arguments = call.arguments();
        BuiltinFunction function = call.function();

        if (function == BuiltinFunction.SELECT) {
            double condition = evaluateNode(arguments.get(0), context, path, revision);
            return condition != 0.0
                    ? evaluateNode(arguments.get(1), context, path, revision)
                    : evaluateNode(arguments.get(2), context, path, revision);
        }

        if (function == BuiltinFunction.NOISE || function == BuiltinFunction.RANDOM) {
            throw failure(DiagnosticCode.EXPRESSION_INVALID,
                    "Builtin '" + function.symbol() + "' is not available in property formulas", path, revision);
        }

        double[] values = new double[arguments.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = evaluateNode(arguments.get(index), context, path, revision);
        }

        return switch (function) {
            case STEP -> values.length == 1
                    ? (values[0] >= 0.0 ? 1.0 : 0.0)
                    : (values[1] >= values[0] ? 1.0 : 0.0);
            case MIN -> Math.min(values[0], values[1]);
            case MAX -> Math.max(values[0], values[1]);
            case ABS -> Math.abs(values[0]);
            case CLAMP -> Math.max(values[1], Math.min(values[2], values[0]));
            case SIN -> Math.sin(values[0]);
            case COS -> Math.cos(values[0]);
            case TAN -> Math.tan(values[0]);
            case SQRT -> Math.sqrt(values[0]);
            case FLOOR -> Math.floor(values[0]);
            case CEIL -> Math.ceil(values[0]);
            case ROUND -> Math.round(values[0]);
            case POW -> Math.pow(values[0], values[1]);
            case SELECT, NOISE, RANDOM -> throw new IllegalStateException("handled above");
        };
    }

    private static FormulaFailure failure(DiagnosticCode code,
                                          String message,
                                          String path,
                                          Revision revision) {
        return new FormulaFailure(error(code, message, path, revision));
    }

    private static Diagnostic error(DiagnosticCode code,
                                    String message,
                                    String path,
                                    Revision revision) {
        return new Diagnostic(code, Severity.ERROR, LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision), new Diagnostic.Location(null, path), null);
    }

    private record Ctx(TrackPoolEvaluationInputs inputs,
                       Symbols symbols,
                       Map<TrackId, CurveTrack> tracks,
                       TrackPoolSampleSource samples,
                       int depth,
                       Map<String, Double> locals) {
    }

    /// 公式结果：成功有限值或结构化错误。
    record Result(double value, Diagnostic diagnostic) {
        static Result value(double value) {
            return new Result(value, null);
        }

        static Result error(Diagnostic diagnostic) {
            return new Result(0.0, Objects.requireNonNull(diagnostic));
        }

        boolean isError() {
            return diagnostic != null;
        }
    }

    private static final class FormulaFailure extends RuntimeException {
        private final Diagnostic diagnostic;

        private FormulaFailure(Diagnostic diagnostic) {
            super(diagnostic.message().toString(), null, false, false);
            this.diagnostic = diagnostic;
        }
    }
}
