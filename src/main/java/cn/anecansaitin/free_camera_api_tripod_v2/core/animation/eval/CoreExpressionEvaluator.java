package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ChannelSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionFailure;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SampleStatus;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionCompiler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.BinaryOperator;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.BoundedExpressionCompiler;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.CompiledExpressionImpl;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionNode;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.BUILTIN_ARITY_MISMATCH;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.DEPTH_LIMIT_EXCEEDED;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.INTERNAL_ERROR;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.NON_FINITE_RESULT;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.SAMPLE_COLLECTION_ERROR;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.SAMPLE_UNKNOWN;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.UNKNOWN_NAME;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.UNKNOWN_SAMPLE_NAME;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.WORK_LIMIT_EXCEEDED;

/// 第一版公式求值器：把 [CompiledExpressionImpl] 的 core 语法树解释成有限数值或结构化失败。
///
/// ## 支持范围
///
/// - 数字字面量；内建量 `t` / `p` / `wt`；
/// - 一元 `+` / `-`；二元 `+ - * / %`；
/// - 内建函数：`step`、惰性 `select`、确定性 `noise`、`random`、`min / max / clamp / pow /
///   abs / sin / cos / tan / sqrt / floor / ceil / round`；
/// - 文档变量：`Constant`、`Formula`、`TrackRef`；
/// - 自定义函数及形参绑定；
/// - `sample("名字")` / `sample("名字", fallback)`。
///
/// 本版**没有**定义空间内建名（世界坐标 / 锚点局部坐标 / 目标距离），因此不读取
/// `ExpressionInputs#spatial` 或 `rigId`。将来加入空间内建名时，必须在这里把缺机位上下文
/// 映射为 `SPATIAL_CONTEXT_MISSING`，而不是猜一台机位。
///
/// ## 作用域
///
/// 自定义函数形参保存在求值器内部的 [Env#locals] 里：形参只对**本次函数体**可见，并屏蔽
/// 同名文档变量。变量公式与轨道读数属于文档级作用域，求值时使用空的局部表，不会看到调用方
/// 的形参。公开输入 [ExpressionInputs] 不需要、也没有 locals 字段。
///
/// ## 预算与深度口径
///
/// - 每个被求值的 AST 节点消耗 1 单位工作；
/// - 每次进入嵌套求值（变量公式、自定义函数体、`TrackRef` 曲线读取）再消耗 1 单位；
/// - 根表达式深度为 `0`；进入变量公式 / 函数体 / `TrackRef` 前调用
///   [EvaluationSession#tryEnterDepth]，并在 `finally` 中退出；
/// - 惰性分支（`select` 未选中侧、未触发的 `sample` fallback）不消耗工作，也不执行；
/// - 额度不足返回 `WORK_LIMIT_EXCEEDED`，深度不足返回 `DEPTH_LIMIT_EXCEEDED`；失败前已消耗
///   的额度不退回。
///
/// ## 失败链
///
/// 每个向上传播失败的节点都会包一层 [ExpressionFailure]：本层 `source` 使用当前源码身份，
/// 区间使用当前节点区间，嵌套失败放入 `causes`。因此入口失败的 [ExpressionFailure#innermost()]
/// 指向真正出错的源码层（变量公式 / 函数体 / 曲线或样本读取），而不是一律映射回入口表达式。
///
/// 本版对没有专用诊断码的情形作如下稳定选择：
///
/// - `TrackRef` 指向不存在或非曲线轨道：`UNKNOWN_NAME`；
/// - 曲线采样器返回 `ABSENT` / `ERROR`：`INTERNAL_ERROR`（API 尚无“曲线采样失败”诊断码）；
/// - 自定义函数实参个数与形参表不符：`INTERNAL_ERROR`；
/// - `step` 多于 2 个实参：core 编译器（`BuiltinFunction.STEP` 为 1..2）已拒绝，正常编译产物
///   不会到达求值器；这里仍保留防御性 `BUILTIN_ARITY_MISMATCH`，绝不静默忽略多余实参。
///
/// ## random / noise 版本
///
/// - [RANDOM_VERSION] = 1：每次 `evaluate` 用 [ExpressionInputs#randomSeed] 创建一个
///   SplitMix64 流，`random()` 每次调用推进该流并返回 `[0, 1)`。同一种子与同一求值路径得到
///   同一序列；宿主换帧改变种子时结果明确非确定。变量 / 函数 / `TrackRef` 嵌套共享同一流。
/// - [NOISE_VERSION] = 1：SplitMix64 对 `(seed, floor(t))` 格点哈希，再按 `floor(t)` 与
///   `floor(t) + 1` 的格点值做线性插值。改变哈希或插值方式必须提升本版本号。
///
/// ## 编译与缓存
///
/// 第一版不缓存：每次求值变量公式或函数体都调用 [DefaultExpressionCompiler] 重新编译。
/// 这保证不会引入无界缓存；后续若加入缓存，必须按源码文本使用有界 LRU。
///
/// 本类无可变状态，实例可复用、可并发调用；随机流在每次 [ExpressionInputs#session] 求值入口创建，
/// 工作与深度状态属于调用方传入的共享 [EvaluationSession]。
@NullMarked
public final class CoreExpressionEvaluator implements ExpressionEvaluator {

    /// 求值语义版本；改变节点语义、失败映射或预算口径时提升。
    public static final int EVALUATION_VERSION = 1;

    /// 确定性噪声语义版本；见类文档。
    public static final int NOISE_VERSION = 1;

    /// 非确定随机数语义版本；见类文档。
    public static final int RANDOM_VERSION = 1;

    /// 每个 AST 节点消耗的工作量。
    private static final int WORK_PER_NODE = 1;

    /// 每次进入嵌套求值额外消耗的工作量。
    private static final int WORK_PER_NESTED_EVALUATION = 1;

    /// SplitMix64 的黄金比例增量。
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private final ExpressionCompiler compiler;

    /// 使用默认有界编译缓存创建求值器；实例可复用、可并发调用。
    public CoreExpressionEvaluator() {
        this(new BoundedExpressionCompiler());
    }

    /// @param compiler 公式编译器；建议传入有界编译器
    public CoreExpressionEvaluator(ExpressionCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    @Override
    public ExpressionSample evaluate(CompiledExpression expression, ExpressionInputs inputs) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(inputs, "inputs");

        if (!(expression instanceof CompiledExpressionImpl compiled)) {
            String source = expression.source();
            return new ExpressionSample.Failure(new ExpressionFailure(
                    inputs.source(),
                    ExpressionDiagnostic.overSource(INTERNAL_ERROR,
                            "CoreExpressionEvaluator only accepts core CompiledExpressionImpl, but got "
                                    + expression.getClass().getName(),
                            source.length()),
                    List.of()));
        }

        Env env = new Env(inputs, inputs.source(), Map.of(), new RandomStream(inputs.randomSeed()), compiler);
        EvalResult result = evalNode(compiled.ast().root(), env);

        if (result instanceof EvalResult.Failure failure) {
            return new ExpressionSample.Failure(failure.failure());
        }

        double value = ((EvalResult.Value) result).value();
        ExpressionNode root = compiled.ast().root();
        EvalResult checked = finiteValue(value, env, root, "expression result");

        if (checked instanceof EvalResult.Failure failure) {
            return new ExpressionSample.Failure(failure.failure());
        }

        return new ExpressionSample.Value((float) value);
    }

    // region AST 分派

    /// 求值一个 AST 节点；每个节点先记账，再按节点类型分派。
    private static EvalResult evalNode(ExpressionNode node, Env env) {
        EvaluationSession session = env.inputs().session();

        if (!session.tryConsumeWork(WORK_PER_NODE)) {
            return failure(env, node, WORK_LIMIT_EXCEEDED,
                    "expression work quota exhausted at this node (remaining "
                            + session.remainingWork() + " units)");
        }

        return switch (node) {
            case ExpressionNode.NumberLiteral literal -> finiteValue(literal.value(), env, node,
                    "number literal");
            case ExpressionNode.Quantity quantity -> evalQuantity(quantity, env);
            case ExpressionNode.Variable variable -> evalVariable(variable, env);
            case ExpressionNode.Unary unary -> evalUnary(unary, env);
            case ExpressionNode.Binary binary -> evalBinary(binary, env);
            case ExpressionNode.BuiltinCall call -> evalBuiltin(call, env);
            case ExpressionNode.Call call -> evalCustomFunction(call, env);
            case ExpressionNode.Sample sample -> evalSample(sample, env);
        };
    }

    private static EvalResult evalQuantity(ExpressionNode.Quantity node, Env env) {
        double value = switch (node.quantity()) {
            case TIME -> env.inputs().time();
            case PROGRESS -> env.inputs().progress();
            case WORLD_TIME -> env.inputs().worldTime();
        };

        return finiteValue(value, env, node, "builtin quantity '" + node.quantity().symbol() + "'");
    }

    private static EvalResult evalUnary(ExpressionNode.Unary node, Env env) {
        EvalResult operand = evalNode(node.operand(), env);

        if (operand instanceof EvalResult.Failure failure) {
            return wrap(failure, env, node,
                    "unary '" + node.operator().symbol() + "' operand failed");
        }

        double value = ((EvalResult.Value) operand).value();
        double computed = node.operator() == UnaryOperator.NEGATE ? -value : value;

        return finiteValue(computed, env, node, "unary '" + node.operator().symbol() + "'");
    }

    private static EvalResult evalBinary(ExpressionNode.Binary node, Env env) {
        EvalResult left = evalNode(node.left(), env);

        if (left instanceof EvalResult.Failure failure) {
            return wrap(failure, env, node,
                    "binary '" + node.operator().symbol() + "' left operand failed");
        }

        EvalResult right = evalNode(node.right(), env);

        if (right instanceof EvalResult.Failure failure) {
            return wrap(failure, env, node,
                    "binary '" + node.operator().symbol() + "' right operand failed");
        }

        double leftValue = ((EvalResult.Value) left).value();
        double rightValue = ((EvalResult.Value) right).value();

        if ((node.operator() == BinaryOperator.DIVIDE || node.operator() == BinaryOperator.MODULO)
                && rightValue == 0d) {
            return failure(env, node, NON_FINITE_RESULT,
                    "binary '" + node.operator().symbol() + "' divides by zero");
        }

        double computed = switch (node.operator()) {
            case ADD -> leftValue + rightValue;
            case SUBTRACT -> leftValue - rightValue;
            case MULTIPLY -> leftValue * rightValue;
            case DIVIDE -> leftValue / rightValue;
            case MODULO -> leftValue % rightValue;
        };

        return finiteValue(computed, env, node, "binary '" + node.operator().symbol() + "'");
    }

    // endregion

    // region 内建函数

    private static EvalResult evalBuiltin(ExpressionNode.BuiltinCall node, Env env) {
        return switch (node.function()) {
            case STEP -> evalStep(node, env);
            case SELECT -> evalSelect(node, env);
            case NOISE -> evalNoise(node, env);
            case RANDOM -> evalRandom(node, env);
            case MIN -> evalTwoArgumentMath(node, env, Math::min);
            case MAX -> evalTwoArgumentMath(node, env, Math::max);
            case POW -> evalTwoArgumentMath(node, env, Math::pow);
            case CLAMP -> evalClamp(node, env);
            case ABS -> evalOneArgumentMath(node, env, Math::abs);
            case SIN -> evalOneArgumentMath(node, env, Math::sin);
            case COS -> evalOneArgumentMath(node, env, Math::cos);
            case TAN -> evalOneArgumentMath(node, env, Math::tan);
            case SQRT -> evalOneArgumentMath(node, env, Math::sqrt);
            case FLOOR -> evalOneArgumentMath(node, env, Math::floor);
            case CEIL -> evalOneArgumentMath(node, env, Math::ceil);
            case ROUND -> evalOneArgumentMath(node, env, value -> (double) Math.round(value));
        };
    }

    /// `step(x)` / `step(edge, x)`；v1 明确不支持多于 2 个实参，不静默忽略。
    private static EvalResult evalStep(ExpressionNode.BuiltinCall node, Env env) {
        int argumentCount = node.arguments().size();

        if (argumentCount == 1) {
            EvalResult argument = evalBuiltinArgument(node, 0, env);

            if (argument instanceof EvalResult.Failure failure) {
                return failure;
            }

            double value = ((EvalResult.Value) argument).value();
            return finiteValue(value >= 0d ? 1d : 0d, env, node, "builtin 'step'");
        }

        if (argumentCount == 2) {
            EvalResult edge = evalBuiltinArgument(node, 0, env);

            if (edge instanceof EvalResult.Failure failure) {
                return failure;
            }

            EvalResult value = evalBuiltinArgument(node, 1, env);

            if (value instanceof EvalResult.Failure failure) {
                return failure;
            }

            double threshold = ((EvalResult.Value) edge).value();
            double candidate = ((EvalResult.Value) value).value();
            return finiteValue(candidate >= threshold ? 1d : 0d, env, node, "builtin 'step'");
        }

        return failure(env, node, BUILTIN_ARITY_MISMATCH,
                "builtin 'step' supports 1 or 2 arguments at evaluation, but got " + argumentCount
                        + "; v1 does not define extra-argument semantics");
    }

    /// 惰性条件选择：条件非零时只求值第二个实参，否则只求值第三个。
    private static EvalResult evalSelect(ExpressionNode.BuiltinCall node, Env env) {
        if (node.arguments().size() != 3) {
            return internalArityFailure(node, env);
        }

        EvalResult condition = evalBuiltinArgument(node, 0, env);

        if (condition instanceof EvalResult.Failure failure) {
            return failure;
        }

        double selector = ((EvalResult.Value) condition).value();
        int selectedIndex = selector != 0d ? 1 : 2;

        return evalBuiltinArgument(node, selectedIndex, env);
    }

    private static EvalResult evalNoise(ExpressionNode.BuiltinCall node, Env env) {
        if (node.arguments().size() != 2) {
            return internalArityFailure(node, env);
        }

        EvalResult seed = evalBuiltinArgument(node, 0, env);

        if (seed instanceof EvalResult.Failure failure) {
            return failure;
        }

        EvalResult position = evalBuiltinArgument(node, 1, env);

        if (position instanceof EvalResult.Failure failure) {
            return failure;
        }

        double computed = noiseV1(((EvalResult.Value) seed).value(),
                ((EvalResult.Value) position).value());

        return finiteValue(computed, env, node, "builtin 'noise' v" + NOISE_VERSION);
    }

    private static EvalResult evalRandom(ExpressionNode.BuiltinCall node, Env env) {
        if (!node.arguments().isEmpty()) {
            return internalArityFailure(node, env);
        }

        return finiteValue(env.random().nextDouble(), env, node,
                "builtin 'random' v" + RANDOM_VERSION);
    }

    private static EvalResult evalTwoArgumentMath(ExpressionNode.BuiltinCall node,
                                                  Env env,
                                                  DoubleBinaryOperator operation) {
        if (node.arguments().size() != 2) {
            return internalArityFailure(node, env);
        }

        EvalResult left = evalBuiltinArgument(node, 0, env);

        if (left instanceof EvalResult.Failure failure) {
            return failure;
        }

        EvalResult right = evalBuiltinArgument(node, 1, env);

        if (right instanceof EvalResult.Failure failure) {
            return failure;
        }

        double computed = operation.applyAsDouble(((EvalResult.Value) left).value(),
                ((EvalResult.Value) right).value());

        return finiteValue(computed, env, node,
                "builtin '" + node.function().symbol() + "'");
    }

    private static EvalResult evalClamp(ExpressionNode.BuiltinCall node, Env env) {
        if (node.arguments().size() != 3) {
            return internalArityFailure(node, env);
        }

        EvalResult value = evalBuiltinArgument(node, 0, env);

        if (value instanceof EvalResult.Failure failure) {
            return failure;
        }

        EvalResult minimum = evalBuiltinArgument(node, 1, env);

        if (minimum instanceof EvalResult.Failure failure) {
            return failure;
        }

        EvalResult maximum = evalBuiltinArgument(node, 2, env);

        if (maximum instanceof EvalResult.Failure failure) {
            return failure;
        }

        double computed = Math.max(((EvalResult.Value) minimum).value(),
                Math.min(((EvalResult.Value) value).value(),
                        ((EvalResult.Value) maximum).value()));

        return finiteValue(computed, env, node, "builtin 'clamp'");
    }

    private static EvalResult evalOneArgumentMath(ExpressionNode.BuiltinCall node,
                                                  Env env,
                                                  DoubleUnaryOperator operation) {
        if (node.arguments().size() != 1) {
            return internalArityFailure(node, env);
        }

        EvalResult argument = evalBuiltinArgument(node, 0, env);

        if (argument instanceof EvalResult.Failure failure) {
            return failure;
        }

        double computed = operation.applyAsDouble(((EvalResult.Value) argument).value());

        return finiteValue(computed, env, node,
                "builtin '" + node.function().symbol() + "'");
    }

    /// 求一个内建实参；失败在调用节点处包一层当前源码层。
    private static EvalResult evalBuiltinArgument(ExpressionNode.BuiltinCall node,
                                                  int index,
                                                  Env env) {
        EvalResult argument = evalNode(node.arguments().get(index), env);

        if (argument instanceof EvalResult.Failure failure) {
            return wrap(failure, env, node,
                    "builtin '" + node.function().symbol() + "' argument " + (index + 1) + " failed");
        }

        return argument;
    }

    /// 编译期已保证定长内建函数的实参个数；运行期到达这里说明实现不一致。
    private static EvalResult internalArityFailure(ExpressionNode.BuiltinCall node, Env env) {
        return failure(env, node, INTERNAL_ERROR,
                "builtin '" + node.function().symbol() + "' reached the evaluator with "
                        + node.arguments().size() + " arguments, but the compiler guarantees "
                        + node.function().arityText());
    }

    // endregion

    // region 变量与 TrackRef

    private static EvalResult evalVariable(ExpressionNode.Variable node, Env env) {
        Double local = env.locals().get(node.name());

        if (local != null) {
            return finiteValue(local, env, node, "function parameter '" + node.name() + "'");
        }

        Symbols symbols = env.inputs().context().snapshot().document().symbols();
        Optional<Variable> variable = symbols.variable(node.name());

        if (variable.isEmpty()) {
            return failure(env, node, UNKNOWN_NAME,
                    "unknown variable '" + node.name() + "' in the bound document");
        }

        return evalValueSource(node, variable.get(), env);
    }

    private static EvalResult evalValueSource(ExpressionNode.Variable node,
                                              Variable variable,
                                              Env env) {
        return switch (variable.source()) {
            case Constant constant -> finiteValue(constant.value(), env, node,
                    "constant variable '" + variable.name() + "'");
            case Formula formula -> evalFormulaVariable(node, variable, formula, env);
            case TrackRef reference -> evalTrackRefVariable(node, variable, reference, env);
        };
    }

    private static EvalResult evalFormulaVariable(ExpressionNode.Variable node,
                                                  Variable variable,
                                                  Formula formula,
                                                  Env env) {
        EvaluationSession session = env.inputs().session();
        EvalResult.Failure enterFailure = enterNested(env, node, "variable '" + variable.name() + "' formula");

        if (enterFailure != null) {
            return enterFailure;
        }

        try {
            ExpressionSource nestedSource = new ExpressionSource.Variable(variable.name());
            CompilationResult compilation = env.compiler().compile(formula.expression());

            if (compilation instanceof CompilationResult.Rejected rejected) {
                return wrap(compileFailure(nestedSource, rejected.diagnostics(),
                                "variable '" + variable.name() + "' formula"), env, node,
                        "variable '" + variable.name() + "' formula failed to compile");
            }

            CompiledExpression handle = compilation.compiledExpression().orElseThrow();

            if (!(handle instanceof CompiledExpressionImpl compiled)) {
                return wrap(internalFailure(nestedSource, 0, formula.expression().length(),
                                "default compiler produced a non-core CompiledExpression: "
                                        + handle.getClass().getName()),
                        env, node, "variable '" + variable.name() + "' formula failed");
            }

            Env nested = new Env(env.inputs(), nestedSource, Map.of(), env.random(), env.compiler());
            EvalResult result = evalNode(compiled.ast().root(), nested);

            if (result instanceof EvalResult.Failure failure) {
                return wrap(failure, env, node,
                        "variable '" + variable.name() + "' formula failed");
            }

            return result;
        } finally {
            session.exitDepth();
        }
    }

    private static EvalResult evalTrackRefVariable(ExpressionNode.Variable node,
                                                   Variable variable,
                                                   TrackRef reference,
                                                   Env env) {
        EvaluationSession session = env.inputs().session();
        EvalResult.Failure enterFailure = enterNested(env, node,
                "variable '" + variable.name() + "' track reference");

        if (enterFailure != null) {
            return enterFailure;
        }

        try {
            ExpressionSource nestedSource = new ExpressionSource.Variable(variable.name());
            Optional<TrackIndex.Entry> found = env.inputs().context().trackIndex()
                    .find(reference.trackId());

            if (found.isEmpty() || !(found.get().track() instanceof CurveTrack)) {
                EvalResult.Failure nested = failure(nestedSource, UNKNOWN_NAME,
                        "variable '" + variable.name() + "' references track '" + reference.trackId()
                                + "', which is absent from the bound track index or is not a curve track",
                        0, 0);
                return wrap(nested, env, node,
                        "variable '" + variable.name() + "' track reference failed");
            }

            ChannelSample sample = env.inputs().sampler().sample(new CurveSampleRequest(
                    env.inputs().context(),
                    session,
                    reference.trackId(),
                    found.get().ownerRigId(),
                    env.inputs().time()));

            if (sample.isValue()) {
                return finiteValue(sample.value(), env, node,
                        "variable '" + variable.name() + "' track '" + reference.trackId() + "'");
            }

            String status = sample.status() == SampleStatus.ABSENT
                    ? "the curve is absent (it has no keys)"
                    : "the sampler returned an error";
            EvalResult.Failure nested = internalFailure(nestedSource, 0, 0,
                    "variable '" + variable.name() + "' could not read track '" + reference.trackId()
                            + "': " + status);

            return wrap(nested, env, node,
                    "variable '" + variable.name() + "' track reference failed");
        } finally {
            session.exitDepth();
        }
    }

    // endregion

    // region 自定义函数

    private static EvalResult evalCustomFunction(ExpressionNode.Call node, Env env) {
        Symbols symbols = env.inputs().context().snapshot().document().symbols();
        Optional<CustomFunction> found = symbols.function(node.name());

        if (found.isEmpty()) {
            return failure(env, node, UNKNOWN_NAME,
                    "unknown custom function '" + node.name() + "' in the bound document");
        }

        CustomFunction function = found.get();

        if (function.arity() != node.arguments().size()) {
            return failure(env, node, INTERNAL_ERROR,
                    "custom function '" + function.name() + "' expects " + function.arity()
                            + " arguments, but the call supplies " + node.arguments().size());
        }

        List<Double> arguments = new ArrayList<>(node.arguments().size());

        for (int index = 0; index < node.arguments().size(); index++) {
            EvalResult argument = evalNode(node.arguments().get(index), env);

            if (argument instanceof EvalResult.Failure failure) {
                return wrap(failure, env, node,
                        "custom function '" + function.name() + "' argument " + (index + 1)
                                + " failed");
            }

            arguments.add(((EvalResult.Value) argument).value());
        }

        EvaluationSession session = env.inputs().session();
        EvalResult.Failure enterFailure = enterNested(env, node,
                "custom function '" + function.name() + "' body");

        if (enterFailure != null) {
            return enterFailure;
        }

        try {
            ExpressionSource nestedSource = new ExpressionSource.Function(function.name());
            CompilationResult compilation = env.compiler().compile(function.body());

            if (compilation instanceof CompilationResult.Rejected rejected) {
                return wrap(compileFailure(nestedSource, rejected.diagnostics(),
                                "custom function '" + function.name() + "' body"), env, node,
                        "custom function '" + function.name() + "' body failed to compile");
            }

            CompiledExpression handle = compilation.compiledExpression().orElseThrow();

            if (!(handle instanceof CompiledExpressionImpl compiled)) {
                return wrap(internalFailure(nestedSource, 0, function.body().length(),
                                "default compiler produced a non-core CompiledExpression: "
                                        + handle.getClass().getName()),
                        env, node, "custom function '" + function.name() + "' body failed");
            }

            Map<String, Double> locals = new HashMap<>();

            for (int index = 0; index < function.parameters().size(); index++) {
                locals.put(function.parameters().get(index), arguments.get(index));
            }

            Env nested = new Env(env.inputs(), nestedSource, Map.copyOf(locals), env.random(), env.compiler());
            EvalResult result = evalNode(compiled.ast().root(), nested);

            if (result instanceof EvalResult.Failure failure) {
                return wrap(failure, env, node,
                        "custom function '" + function.name() + "' body failed");
            }

            return result;
        } finally {
            session.exitDepth();
        }
    }

    // endregion

    // region 样本读取

    private static EvalResult evalSample(ExpressionNode.Sample node, Env env) {
        WorldSample declared = findSample(env.inputs().context().snapshot().document(), node.name());

        if (declared == null) {
            return failure(env, node, UNKNOWN_SAMPLE_NAME,
                    "sample name '" + node.name() + "' is not bound to any WorldSample declaration");
        }

        Optional<SampleReading> reading = env.inputs().context().facts().sample(declared.id());

        if (reading.isEmpty()) {
            return sampleFallbackOrUnknown(node, env,
                    "sample '" + node.name() + "' has no reading in this frame's sample table");
        }

        return switch (reading.get()) {
            case SampleReading.Value value -> finiteValue(value.value(), env, node,
                    "sample '" + node.name() + "'");
            case SampleReading.Unknown unknown -> sampleFallbackOrUnknown(node, env,
                    "sample '" + node.name() + "' is UNKNOWN: " + unknown.reason());
            case SampleReading.CollectionError error -> failure(env, node, SAMPLE_COLLECTION_ERROR,
                    "sample '" + node.name() + "' failed to collect: " + error.reason()
                            + "; collection errors must not be hidden by a fallback");
        };
    }

    /// UNKNOWN 或缺失读数：只有存在 fallback 时才惰性求值它。
    private static EvalResult sampleFallbackOrUnknown(ExpressionNode.Sample node,
                                                      Env env,
                                                      String detail) {
        if (!node.hasFallback()) {
            return failure(env, node, SAMPLE_UNKNOWN, detail);
        }

        EvalResult fallback = evalNode(node.fallback(), env);

        if (fallback instanceof EvalResult.Failure failure) {
            return wrap(failure, env, node,
                    "fallback for sample '" + node.name() + "' failed");
        }

        return fallback;
    }

    private static @Nullable WorldSample findSample(CameraAnimation document, String name) {
        for (WorldSample sample : document.samples()) {
            if (sample.name().equals(name)) {
                return sample;
            }
        }

        return null;
    }

    // endregion

    // region 预算、深度与失败构造

    /// 进入嵌套求值：先计 1 单位工作，再申请一层深度。返回非空表示已在当前节点失败。
    private static EvalResult.@Nullable Failure enterNested(Env env, ExpressionNode node, String layer) {
        EvaluationSession session = env.inputs().session();

        if (!session.tryConsumeWork(WORK_PER_NESTED_EVALUATION)) {
            return failure(env, node, WORK_LIMIT_EXCEEDED,
                    "work quota exhausted before entering " + layer + " (remaining "
                            + session.remainingWork() + " units)");
        }

        if (!session.tryEnterDepth()) {
            return failure(env, node, DEPTH_LIMIT_EXCEEDED,
                    "depth limit " + session.maxDepth() + " exceeded before entering " + layer);
        }

        return null;
    }

    /// 当前层失败：源码为 [Env#source]，区间为当前节点。
    private static EvalResult.Failure failure(Env env,
                                              ExpressionNode node,
                                              ExpressionDiagnosticCode code,
                                              String detail) {
        return failure(env.source(), code, detail, node.start(), node.end());
    }

    private static EvalResult.Failure failure(ExpressionSource source,
                                              ExpressionDiagnosticCode code,
                                              String detail,
                                              int start,
                                              int end) {
        return new EvalResult.Failure(new ExpressionFailure(source,
                new ExpressionDiagnostic(code, detail, start, end), List.of()));
    }

    /// 把子节点的失败包一层当前节点 / 当前源码层。
    private static EvalResult wrap(EvalResult.Failure inner,
                                   Env env,
                                   ExpressionNode node,
                                   String detail) {
        ExpressionFailure cause = inner.failure();
        return new EvalResult.Failure(new ExpressionFailure(env.source(),
                new ExpressionDiagnostic(cause.code(), detail, node.start(), node.end()),
                List.of(cause)));
    }

    /// 编译失败：保留编译器诊断码、细节与相对**内层源码**的区间。
    private static EvalResult.Failure compileFailure(ExpressionSource source,
                                                     List<ExpressionDiagnostic> diagnostics,
                                                     String description) {
        ExpressionDiagnostic first = diagnostics.getFirst();
        return failure(source, first.code(), description + ": " + first.detail(),
                first.start(), first.end());
    }

    private static EvalResult.Failure internalFailure(ExpressionSource source,
                                                      int start,
                                                      int end,
                                                      String detail) {
        return failure(source, INTERNAL_ERROR, detail, start, end);
    }

    /// 检查非有限并同时保证结果能无损收敛到公开的 `float` 结果域。
    private static EvalResult finiteValue(double value,
                                          Env env,
                                          ExpressionNode node,
                                          String description) {
        if (!Double.isFinite(value) || !Float.isFinite((float) value)) {
            return failure(env, node, NON_FINITE_RESULT,
                    description + " produced a non-finite result: " + value);
        }

        return new EvalResult.Value(value);
    }

    // endregion

    // region random / noise v1

    /// v1 确定性噪声：SplitMix64 格点哈希 + 线性插值，输出 `[0, 1)`。
    private static double noiseV1(double seed, double position) {
        long seedValue = (long) seed;
        double floored = Math.floor(position);
        long cell = (long) floored;
        double fraction = position - floored;

        double left = noiseLattice(seedValue, cell);
        double right = noiseLattice(seedValue, cell + 1);

        return left + (right - left) * fraction;
    }

    private static double noiseLattice(long seed, long cell) {
        return unitDouble(mix64(seed + GOLDEN_GAMMA * cell));
    }

    private static double unitDouble(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    /// SplitMix64 finalizer；random 与 noise 共用，保证跨 JDK 的位级确定性。
    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    /// 单次求值内部共享的 SplitMix64 流；每次 `random()` 调用推进一格。
    private static final class RandomStream {

        private long state;

        private RandomStream(long seed) {
            this.state = seed;
        }

        private double nextDouble() {
            state += GOLDEN_GAMMA;
            return unitDouble(mix64(state));
        }
    }

    // endregion

    // region 内部值类型

    /// 求值内部环境：当前源码身份、局部形参表与本次求值共享的随机流。
    private record Env(ExpressionInputs inputs,
                       ExpressionSource source,
                       Map<String, Double> locals,
                       RandomStream random,
                       ExpressionCompiler compiler) {

        private Env {
            Objects.requireNonNull(inputs, "inputs");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(locals, "locals");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(compiler, "compiler");
        }
    }

    private sealed interface EvalResult permits EvalResult.Value, EvalResult.Failure {

        record Value(double value) implements EvalResult {
        }

        record Failure(ExpressionFailure failure) implements EvalResult {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    // endregion
}
