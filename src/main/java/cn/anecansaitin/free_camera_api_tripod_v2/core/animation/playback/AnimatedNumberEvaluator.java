package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionEvaluator;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionInputs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.ExpressionSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionCompiler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval.CurveInterpolation;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.BoundedExpressionCompiler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 布局数值求值：把 [AnimatedNumber] 的常量 / 公式 / 曲线分支求成本帧的一个有限数值。
///
/// ## 两条分支
///
/// | 分支 | 规则 |
/// | --- | --- |
/// | [AnimatedNumber.Value] | `Constant` 直接取值；`Formula` 编译后走共享 [ExpressionEvaluator] |
/// | [AnimatedNumber.Keyed] | 空曲线取显式默认值；非空曲线按 `CLAMP` + `STEP` / `LINEAR` / `HERMITE` 求值 |
///
/// 求值结果使用 [LayoutValue]：成功是有限 `double`，失败是结构化 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic]。
/// **不用 `NaN` 表示失败**，也不把失败静默替换成默认值或另一端点。
///
/// ## 公式
///
/// 公式在**当前文档时间**求值（[LayoutEvalInput#time]），而不是端点键时间；与曲线采样器
/// 对公式键的口径一致。来源固定为 [ExpressionSource.Layout]，携带布局所属机位、当前分量
/// 与实际端点键（[AnimatedNumber.Value] 的公式没有键，`keyId` 为空）。因此失败链能定位到
/// `view.rect.<field>` 或 `view.rect.<field>.curve.keys.<KeyId>`。
///
/// 公式只存文本，默认由 [BoundedExpressionCompiler] 按源码文本缓存编译结果。编译失败、
/// 求值失败、非有限结果都返回失败，不产生可继续插值的数值。
///
/// ## 曲线
///
/// 非空曲线按 [CurveInterpolation] 的既有规则求值：
///
/// - 时间先按 `wrapTime` 夹到首末键之间；布局曲线的 `CLAMP/CLAMP` 由
///   [AnimatedNumber.Keyed] 构造器保证，这里不额外接受其它 wrap 语义；
/// - 单键曲线、首键前、末键后都取对应端点；
/// - 段内取左键的插值模式：`STEP` 只求值左端点并直接返回左值（右端点公式不应被无谓求值），
///   `LINEAR` 与 `HERMITE` 需要两端，两端中任一公式失败都会向上传播。
///
/// ## 为什么 Keyed 不走 CurveSampler
///
/// 布局曲线不是轨道，没有 `TrackId`，无法构造合法的
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.CurveSampleRequest]。因此这里
/// 直接复用 [CurveInterpolation] 的纯数值规则，而不是伪造一个临时轨道身份。[LayoutEvalInput#sampler]
/// 只用于公式里的变量 `TrackRef`，不会用来读取布局曲线本身。
///
/// ## 会话与线程
///
/// 本类不新建 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationSession]，
/// 也不缓存结果；公式求值与曲线读取都消费调用方传入的同一会话。实例本身无可变状态，
/// 可复用；是否可并发取决于注入的 [ExpressionEvaluator]。
@NullMarked
public final class AnimatedNumberEvaluator {

    private final ExpressionEvaluator evaluator;
    private final ExpressionCompiler compiler;

    /// 使用默认有界编译缓存创建求值器。
    ///
    /// @param evaluator 公式求值器，非空；不得读取当前 store 或当前编辑机位
    public AnimatedNumberEvaluator(ExpressionEvaluator evaluator) {
        this(evaluator, new BoundedExpressionCompiler());
    }

    /// @param evaluator 公式求值器，非空
    /// @param compiler  公式编译器；同一源码文本应复用缓存
    public AnimatedNumberEvaluator(ExpressionEvaluator evaluator, ExpressionCompiler compiler) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    /// 按运行时类型分派两条分支。
    ///
    /// @param number 布局数值，非空
    /// @param input  本帧显式输入，非空
    /// @return 有限数值或结构化失败
    public LayoutValue evaluate(AnimatedNumber number, LayoutEvalInput input) {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(input, "input");

        return switch (number) {
            case AnimatedNumber.Value value -> evaluate(value, input);
            case AnimatedNumber.Keyed keyed -> evaluate(keyed, input);
        };
    }

    /// 求值常量值源分支。
    ///
    /// @param value 常量分支，非空
    /// @param input 本帧显式输入，非空
    /// @return 常量值（有限）或失败；公式失败不会被替换成默认值
    public LayoutValue evaluate(AnimatedNumber.Value value, LayoutEvalInput input) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(input, "input");

        return switch (value.source()) {
            case Constant constant -> finite(constant.value(), input, null, "constant value");
            case Formula formula -> evaluateFormula(formula, input, null);
        };
    }

    /// 求值曲线分支。
    ///
    /// 空曲线返回显式默认值；非空曲线按 [CurveInterpolation] 的 `CLAMP` 与三种插值模式求值。
    ///
    /// @param keyed 曲线分支，非空
    /// @param input 本帧显式输入，非空
    /// @return 本帧有限数值或结构化失败
    public LayoutValue evaluate(AnimatedNumber.Keyed keyed, LayoutEvalInput input) {
        Objects.requireNonNull(keyed, "keyed");
        Objects.requireNonNull(input, "input");

        Curve curve = keyed.curve();

        if (curve.isEmpty()) {
            return finite(keyed.defaultValue(), input, null, "empty curve default");
        }

        List<Keyframe> keys = curve.keys();
        Keyframe first = keys.getFirst();
        Keyframe last = keys.getLast();
        double time = CurveInterpolation.wrapTime(
                input.time(), first.time(), last.time(), curve.preMode(), curve.postMode());

        if (!Double.isFinite(time)) {
            return LayoutValue.failure(LayoutDiagnostics.valueInvalid(input, null,
                    "layout curve wrap produced a non-finite time: " + time));
        }

        // 单键（或所有键同刻）曲线退化为该端点；Curve 已保证时间严格递增，这里只防单键。
        if (!(last.time() > first.time())) {
            return evaluateKeyframe(first, input);
        }

        if (time <= first.time()) {
            return evaluateKeyframe(first, input);
        }

        if (time >= last.time()) {
            return evaluateKeyframe(last, input);
        }

        int leftIndex = floorIndex(keys, time);
        Keyframe left = keys.get(leftIndex);
        Keyframe right = keys.get(leftIndex + 1);

        LayoutValue leftValue = evaluateKeyframe(left, input);

        if (leftValue instanceof LayoutValue.Failure failure) {
            return failure;
        }

        double leftNumber = ((LayoutValue.Success) leftValue).value();

        // STEP 只需要左端；右端公式不该被无谓求值，也不该影响本帧结果。
        if (left.interpolation() == Curve.Interpolation.STEP) {
            return LayoutValue.success(leftNumber);
        }

        LayoutValue rightValue = evaluateKeyframe(right, input);

        if (rightValue instanceof LayoutValue.Failure failure) {
            return failure;
        }

        double rightNumber = ((LayoutValue.Success) rightValue).value();
        double normalized = CurveInterpolation.normalizedTime(time, left.time(), right.time());
        double result = switch (left.interpolation()) {
            case STEP -> leftNumber;
            case LINEAR -> CurveInterpolation.linear(normalized, leftNumber, rightNumber);
            case HERMITE -> CurveInterpolation.hermite(
                    normalized,
                    (double) right.time() - left.time(),
                    leftNumber,
                    rightNumber,
                    left.outSlope(),
                    right.inSlope(),
                    left.outLength(),
                    right.inLength());
        };

        return finite(result, input, left.keyId(), "curve interpolation at " + time + "s");
    }

    // region 内部求值

    /// 求值一个曲线端点；常量直接取，公式按端点键的身份构造 Layout source。
    private LayoutValue evaluateKeyframe(Keyframe key, LayoutEvalInput input) {
        return switch (key.value()) {
            case Constant constant -> finite(constant.value(), input, key.keyId(),
                    "curve key " + key.keyId());
            case Formula formula -> evaluateFormula(formula, input, key.keyId());
        };
    }

    /// 编译并求值一段布局公式。
    private LayoutValue evaluateFormula(Formula formula, LayoutEvalInput input, @Nullable KeyId keyId) {
        CompilationResult compilation = compiler.compile(formula.expression());

        if (compilation instanceof CompilationResult.Rejected rejected) {
            return LayoutValue.failure(
                    LayoutDiagnostics.compileFailure(input, keyId, rejected.diagnostics().getFirst()));
        }

        CompiledExpression compiled = ((CompilationResult.Compiled) compilation).expression();
        ExpressionInputs inputs = new ExpressionInputs(
                input.context(),
                new ExpressionSource.Layout(input.rigId(), input.field(), keyId),
                input.rigId(),
                input.time(),
                input.progress(),
                input.worldTime(),
                input.spatial(),
                input.randomSeed(),
                input.session(),
                input.sampler());
        ExpressionSample sample = evaluator.evaluate(compiled, inputs);

        return switch (sample) {
            // 求值器契约已保证 Value 有限；这里仍走统一检查，避免自定义实现破坏结果域。
            case ExpressionSample.Value value -> finite(value.value(), input, keyId, "formula result");
            case ExpressionSample.Failure failure -> LayoutValue.failure(
                    LayoutDiagnostics.expressionFailure(input, keyId, failure.failure()));
        };
    }

    /// 非有限布局数值一律失败；不用 NaN 继续传播。
    private static LayoutValue finite(double value,
                                      LayoutEvalInput input,
                                      @Nullable KeyId keyId,
                                      String description) {
        if (!Double.isFinite(value)) {
            return LayoutValue.failure(LayoutDiagnostics.valueInvalid(input, keyId,
                    "layout " + description + " must be finite: " + value));
        }

        return LayoutValue.success(value);
    }

    /// 二分查找左端点下标：`keys.get(index).time() <= time < keys.get(index + 1).time()`。
    private static int floorIndex(List<Keyframe> keys, double time) {
        int low = 0;
        int high = keys.size() - 1;

        while (low < high) {
            int middle = (low + high + 1) >>> 1;

            if (keys.get(middle).time() <= time) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        return low;
    }

    // endregion
}
