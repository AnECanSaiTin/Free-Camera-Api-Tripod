package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 公式值：一段表达式 + 一个回退用的固定数值。
///
/// 编译结果**不进这个 record**——它是不可变值，带一个语法树会让 `equals` / 序列化 / 拷贝
/// 都捎上编译产物。取而代之的是 [Expression] 内部按公式文本驻留编译结果（它本来就有缓存），
/// [#evaluate] 每次查一次表：命中是一次 ConcurrentHashMap 查找，量级可忽略，
/// 而"同一段文本只解析一次"这条语义从"每个字段各存一份"变成了"全局一份"。
///
/// 编译不过的公式照样存得下来（界面要显示用户写错的内容），只是求值一律返回 NaN，
/// 于是外面会退回 [fallback]。
@NullMarked
public record Formula(String expression, float fallback) implements NumberSource {
    @Override
    public float evaluate(@Nullable Scope scope) {
        if (scope == null) {
            return Float.NaN;
        }

        return Expression.evaluate(expression, scope.resolver());
    }

    /// 公式算不出来时用的固定数值
    @Override
    public float constant() {
        return fallback;
    }

    @Override
    public boolean isFormula() {
        return true;
    }

    @Override
    public NumberSource copy() {
        return this;
    }

    /// 公式是否编译不过（界面据此直接标红，不必等求值）
    public boolean broken() {
        return Expression.compile(expression) == null;
    }

    @Override
    public String toString() {
        return expression;
    }
}
