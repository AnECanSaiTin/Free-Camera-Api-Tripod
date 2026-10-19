package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 一次公式求值的结果：有限数值或结构化失败（主设计 §6.1、不变量 I9）。
///
/// ## 没有第三个数值态
///
/// 与通道采样的三态不同，公式**已经编写**，因此结果只有：
///
/// - [Value]：有限合法数值；
/// - [Failure]：本帧求值失败，携带来源化的失败链。
///
/// `NaN` / `Infinity` 不得作为数值返回，必须转成 `NON_FINITE_RESULT` 失败。
/// **确定性**失败也应按上下文身份缓存，不能因为“没有数值”而每帧重复求值；
/// `WORK_LIMIT_EXCEEDED` / `DEPTH_LIMIT_EXCEEDED` 取决于本次执行状态，不得写入共享缓存
/// （复核 R44-N1）。
@NullMarked
public sealed interface ExpressionSample permits ExpressionSample.Value, ExpressionSample.Failure {

    /// 求值成功。
    ///
    /// @param value 有限数值
    record Value(float value) implements ExpressionSample {
        public Value {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("ExpressionSample.Value must be finite: " + value);
            }
        }
    }

    /// 求值失败。
    ///
    /// @param failure 来源化的失败链；入口失败在链首
    record Failure(ExpressionFailure failure) implements ExpressionSample {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /// 成功时的数值。
    default Optional<Float> optionalValue() {
        return this instanceof Value value ? Optional.of(value.value()) : Optional.empty();
    }

    /// 失败时的入口诊断。
    default Optional<ExpressionDiagnostic> optionalDiagnostic() {
        return this instanceof Failure failure
                ? Optional.of(failure.failure().diagnostic())
                : Optional.empty();
    }

    /// 失败时的完整失败链。
    default Optional<ExpressionFailure> optionalFailure() {
        return this instanceof Failure failure ? Optional.of(failure.failure()) : Optional.empty();
    }

    /// 是否成功。
    default boolean succeeded() {
        return this instanceof Value;
    }
}
