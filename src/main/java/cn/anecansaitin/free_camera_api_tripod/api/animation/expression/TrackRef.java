package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 轨道读数：取某条曲线轨道在求值时刻的值。
///
/// 只作**变量的取值来源**，**不实现 [NumberSource]**，因此进不了关键帧字段——这是设计里
/// 那条类型级安全边界的落点。
///
/// 取值走 [Scope#resolver] 的 [TrackLookup#track]，也就是**这一帧的真值**：轨道上的公式照常参与，
/// 所以变量绑的轨道与该轨道作为属性播放给出同一个数。
///
/// "变量绑的轨道上又引用了这个变量"那种自嵌套现在是一个**环**，由 `eval.EvaluationGraph`
/// 在写入期拦下（挂公式、绑轨道、读档三处），不必在这里做递归保护。
///
/// 没有固定数值：静态求值（scope 为 null）与轨道缺失一样取不到值，按 0 处理。
@NullMarked
public record TrackRef(String trackId) implements ValueSource {
    @Override
    public float evaluateOrFallback(@Nullable Scope scope) {
        float value = scope == null ? Float.NaN : scope.resolver().track(trackId);
        return Float.isNaN(value) ? 0f : value;
    }

    @Override
    public String trackId() {
        return trackId;
    }

    @Override
    public ValueSource copy() {
        return this;
    }

    @Override
    public String toString() {
        return "@" + trackId;
    }
}
