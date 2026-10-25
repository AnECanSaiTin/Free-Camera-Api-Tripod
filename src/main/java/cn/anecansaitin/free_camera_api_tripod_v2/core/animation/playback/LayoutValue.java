package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/// 一个布局数值的求值结果：有限数值或结构化失败。
///
/// ## 为什么不用 `NaN` 表示失败
///
/// 布局数值最终会进入 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.ResolvedRect]，
/// 而 `NaN` 既是合法的 `double` 值，又无法携带“哪一处公式/曲线出错”的信息。把失败压成
/// `NaN` 会让调用方无法区分“尚未求值”“数值为零”和“本帧错误”，违反本项目禁止用哨兵值
/// 同时表达多种状态的口径（不变量 I9）。因此本类型只有成功与失败两个分支：
///
/// - [Success]：有限 `double`；非有限值在构造时就拒绝；
/// - [Failure]：必须携带 [Diagnostic]，调用方据此终止整帧或标记预览错误。
///
/// 成功值使用 `double` 而不是 `float`，因为矩形最终以 `double` 交给合成阶段；表达式与曲线
/// 的 `float` 结果可以无损放进来，插值过程也不必为了包装结果提前损失精度。
///
/// @see AnimatedNumberEvaluator
/// @see ResolvedRectEvaluator
@NullMarked
public sealed interface LayoutValue permits LayoutValue.Success, LayoutValue.Failure {

    /// 求值成功。
    ///
    /// @param value 有限数值
    record Success(double value) implements LayoutValue {
        public Success {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("LayoutValue.Success must be finite: " + value);
            }
        }
    }

    /// 求值失败。
    ///
    /// @param diagnostic 结构化诊断，非空；至少包含错误码、严重级别、文档作用域与机位/字段定位
    record Failure(Diagnostic diagnostic) implements LayoutValue {
        public Failure {
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    /// 成功结果。
    static Success success(double value) {
        return new Success(value);
    }

    /// 失败结果。
    static Failure failure(Diagnostic diagnostic) {
        return new Failure(diagnostic);
    }

    /// 是否成功。
    default boolean succeeded() {
        return this instanceof Success;
    }

    /// 是否失败。
    default boolean failed() {
        return this instanceof Failure;
    }

    /// 成功时的数值；失败时为空。
    default OptionalDouble optionalValue() {
        return this instanceof Success success
                ? OptionalDouble.of(success.value())
                : OptionalDouble.empty();
    }

    /// 失败时的诊断；成功时为空。
    default Optional<Diagnostic> optionalDiagnostic() {
        return this instanceof Failure failure
                ? Optional.of(failure.diagnostic())
                : Optional.empty();
    }
}
