package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import com.google.gson.JsonElement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 关键帧字段的取值来源：**固定值，或一段公式。没有第三态。**
///
/// 它同时也是 [ValueSource] 的一种，于是"关键帧的槽位"与"变量的来源"共用同一套求值入口；
/// 区别在于**轨道读数（[TrackRef]）只实现 [ValueSource]**，所以在类型上根本进不了关键帧字段，
/// 不再靠注释约束。
///
/// 求值约定：
/// - 作用域为 `null` 表示**静态求值**——公式一律算不出来（返回 NaN），只有固定数值可用
/// - 公式非法、变量取不到值、轨道不存在一律返回 [Float#NaN]；[#evaluate] 本身不做兜底，
///   要不要退回固定数值由 [#evaluateOrFallback] 一处决定
///
/// 两个实现都是不可变 record，因此**拷贝即快照**：装进别的对象时不必复制。
@NullMarked
public sealed interface NumberSource extends ValueSource permits Constant, Formula {
    /// 求值；算不出来返回 NaN。scope 为 null = 静态求值（公式一律算不出来）
    float evaluate(@Nullable Scope scope);

    /// 用不到公式时那个数（公式的回退值）
    float constant();

    /// **求值链上唯一的回退点**：evaluate 失败就用 constant，constant 也是 NaN 时用 0
    @Override
    default float evaluateOrFallback(@Nullable Scope scope) {
        float value = evaluate(scope);

        if (!Float.isNaN(value)) {
            return value;
        }

        float fallback = constant();
        return Float.isNaN(fallback) ? 0f : fallback;
    }

    /// 是不是一段公式。界面问"这个槽位是数值还是公式"只需要这一个判断，
    /// 不必到处 instanceof——求值层因此完全不需要认识具体的实现类型
    default boolean isFormula() {
        return false;
    }

    /// 来源不可变，拷贝返回自身
    @Override
    NumberSource copy();

    /// 读一个关键帧槽位的来源：数字是固定值，对象认 `expression`（公式）。
    ///
    /// 认不出来或字段缺失就用 [fallback]。**不认 `{"track": …}`**：轨道读数只有变量读得到，
    /// 那种写法属于历史遗留的非法通路，读到按 [fallback] 降级成固定值并记一条日志
    static NumberSource read(@Nullable JsonElement element, float fallback) {
        return SourceJson.readNumber(element, fallback);
    }
}
