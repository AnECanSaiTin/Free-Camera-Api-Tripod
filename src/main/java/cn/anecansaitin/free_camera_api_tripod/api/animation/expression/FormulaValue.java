package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.Scope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression.Formula;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 公式值：一段表达式 + 一个回退用的固定数值。
///
/// 表达式在**构造与改写时**编译一次并缓存（见 [Expression#compile]），求值不再扫描字符串；
/// 编译不过的公式照样能存下来（界面要显示用户写错的内容），只是求值一律返回 NaN，
/// 于是外面会退回 [constant]。
@NullMarked
public final class FormulaValue implements ValueSource {
    private String expression;
    /// 编译结果；公式编译不过时为 null
    private @Nullable Formula formula;
    private float constant;

    public FormulaValue(String expression, float constant) {
        this.constant = constant;
        this.expression = expression;
        this.formula = Expression.compile(expression);
    }

    public String expression() {
        return expression;
    }

    /// 换一条公式并重新编译
    public FormulaValue expression(String expression) {
        this.expression = expression;
        this.formula = Expression.compile(expression);
        return this;
    }

    public FormulaValue constant(float constant) {
        this.constant = constant;
        return this;
    }

    /// 公式是否已经编译失败（界面据此直接标红，不必等求值）
    public boolean broken() {
        return formula == null;
    }

    @Override
    public float evaluate(@Nullable Scope scope) {
        if (formula == null || scope == null) {
            return Float.NaN;
        }

        return Expression.evaluate(formula, scope.resolver());
    }

    /// 公式算不出来时用的固定数值
    @Override
    public float constant() {
        return constant;
    }

    /// 就地改回退值，公式本身不动——界面上"改成固定值"就是这一条路
    @Override
    public ValueSource withConstant(float value) {
        return this.constant(value);
    }

    @Override
    public ValueSource copy() {
        return new FormulaValue(expression, constant);
    }

    @Override
    public String toString() {
        return expression;
    }
}
