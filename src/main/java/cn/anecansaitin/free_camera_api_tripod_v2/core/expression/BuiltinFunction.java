package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 表达式内建函数（主设计 §6.1）。
///
/// 编译期认识这些名字并校验实参个数；它们**不登记**为
/// `ExpressionReferenceKind.FUNCTION` 外部引用。`sample` 不是普通内建函数：它的名字必须是
/// 字符串字面量，由编译器的 `sample(...)` 语法单独处理，登记为
/// `ExpressionReferenceKind.SAMPLE`。
///
/// ## 实参个数（v1 裁定）
///
/// | 函数 | 实参个数 | 说明 |
/// | --- | --- | --- |
/// | `step` | 1 或 2 | `step(x)` 按符号；`step(edge, x)` 按阈值；多于 2 个实参编译期拒绝 |
/// | `select` | 恰好 3 | 惰性条件：只求值选中分支，静态仍检查全部实参引用 |
/// | `noise` | 恰好 2 | 确定性、版本化；`noise(seed, t)` |
/// | `random` | 恰好 0 | 明确非确定；种子来自 `ExpressionInputs#randomSeed` |
/// | `min` / `max` | 恰好 2 | 固定二元，不提供变参形式 |
/// | `clamp` | 恰好 3 | `clamp(value, min, max)` |
/// | `pow` | 恰好 2 | `pow(base, exponent)` |
/// | `abs` / `sin` / `cos` / `tan` / `sqrt` / `floor` / `ceil` / `round` | 恰好 1 | 一元数学函数 |
///
/// 实参个数在编译期检查；不符返回 `ExpressionDiagnosticCode.BUILTIN_ARITY_MISMATCH`。
/// 函数名与语义版本属于格式的一部分，改变语义必须提升格式版本。
@NullMarked
public enum BuiltinFunction {

    /// 阶跃函数。
    ///
    /// v1 语义：`step(x)` 在 `x >= 0` 时为 `1`，否则 `0`；`step(edge, x)` 在 `x >= edge` 时为 `1`。
    /// 编译期只接受 1 个或 2 个实参：多于 2 个实参的语义尚未定稿，直接在编译期报
    /// `BUILTIN_ARITY_MISMATCH`，不让未定语义进入求值器。
    STEP("step", 1, 2),

    /// 惰性条件选择：`select(cond, a, b)`。条件非零时只求值 `a`，否则只求值 `b`。
    SELECT("select", 3, 3),

    /// 确定性噪声：`noise(seed, t)`。不依赖采样顺序、线程随机源或发布序号。
    NOISE("noise", 2, 2),

    /// 明确非确定的随机数：`random()`，无实参，种子来自本次求值输入。
    RANDOM("random", 0, 0),

    /// 二元最小值：`min(a, b)`。
    MIN("min", 2, 2),

    /// 二元最大值：`max(a, b)`。
    MAX("max", 2, 2),

    /// 一元绝对值：`abs(x)`。
    ABS("abs", 1, 1),

    /// 夹取：`clamp(value, min, max)`。
    CLAMP("clamp", 3, 3),

    /// 正弦：`sin(x)`，弧度。
    SIN("sin", 1, 1),

    /// 余弦：`cos(x)`，弧度。
    COS("cos", 1, 1),

    /// 正切：`tan(x)`，弧度。
    TAN("tan", 1, 1),

    /// 平方根：`sqrt(x)`。
    SQRT("sqrt", 1, 1),

    /// 向下取整：`floor(x)`。
    FLOOR("floor", 1, 1),

    /// 向上取整：`ceil(x)`。
    CEIL("ceil", 1, 1),

    /// 四舍五入：`round(x)`。
    ROUND("round", 1, 1),

    /// 幂：`pow(base, exponent)`。
    POW("pow", 2, 2);

    private final String symbol;
    private final int minArity;
    private final int maxArity;

    BuiltinFunction(String symbol, int minArity, int maxArity) {
        this.symbol = symbol;
        this.minArity = minArity;
        this.maxArity = maxArity;
    }

    /// 源码中使用的名字。
    public String symbol() {
        return symbol;
    }

    /// 允许的最少实参个数。
    public int minArity() {
        return minArity;
    }

    /// 允许的最多实参个数。
    public int maxArity() {
        return maxArity;
    }

    /// 给定实参个数是否合法。
    public boolean accepts(int argumentCount) {
        return argumentCount >= minArity && argumentCount <= maxArity;
    }

    /// 面向诊断的实参个数描述，例如 `exactly 3 arguments`、`1 to 2 arguments`。
    public String arityText() {
        if (minArity == maxArity) {
            return minArity == 1 ? "exactly 1 argument" : "exactly " + minArity + " arguments";
        }

        if (minArity + 1 == maxArity) {
            return minArity + " or " + maxArity + " arguments";
        }

        return minArity + " to " + maxArity + " arguments";
    }

    /// 按源码名字查找内建函数。
    ///
    /// @param name 源码中的标识符
    /// @return 对应内建函数；不是内建函数时为空
    public static @Nullable BuiltinFunction fromName(String name) {
        for (BuiltinFunction function : values()) {
            if (function.symbol.equals(name)) {
                return function;
            }
        }

        return null;
    }
}
