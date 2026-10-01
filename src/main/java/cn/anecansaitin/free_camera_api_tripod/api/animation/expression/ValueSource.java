package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import com.google.gson.JsonElement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 一个数值的来源：可以是固定值、公式，或（只作变量取值来源的）曲线轨道读数。
///
/// 三种状态由两个接口分层表达：**[NumberSource] 是两态**（`Constant` | `Formula`），
/// 关键帧字段用它；**本接口是三态**，多一个 [TrackRef]，只有变量用它。
/// "轨道读数"因此在类型上进不了关键帧字段，不必靠注释约束。
///
/// 求值约定：
/// - 作用域为 `null` 表示**静态求值**——只有固定数值可用，公式一律算不出来（返回 NaN）
/// - 公式非法、变量取不到值、轨道不存在一律返回 [Float#NaN]；[#evaluateOrFallback] 是唯一的回退点
///
/// 三个实现都是不可变 record，所以 **`copy()` 就是 `this`**：来源可以作为快照直接共享。
@NullMarked
public sealed interface ValueSource permits NumberSource, TrackRef {
    /// 求值并回退固定数值——求值链上唯一的回退点
    float evaluateOrFallback(@Nullable Scope scope);

    /// 轨道引用返回其 id，数值来源返回 null（原 `Variable#trackId` 的职责）
    default @Nullable String trackId() {
        return null;
    }

    /// 来源不可变，拷贝返回自身
    ValueSource copy();

    /// 读一个变量的来源：数字是固定值，对象认 `expression`（公式）与 `track`（轨道读数）
    static ValueSource read(@Nullable JsonElement element, float fallback) {
        return SourceJson.readValue(element, fallback);
    }

    /// 一个数值来源 -> JSON
    static JsonElement write(ValueSource source) {
        return SourceJson.write(source);
    }
}
