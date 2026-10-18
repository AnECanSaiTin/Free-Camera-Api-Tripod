package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 单轴求值结果：`ABSENT` / `VALUE` / `ERROR` 三态（主设计 §6.2、§6.4）。
///
/// 错误不能压缩成缺失：某轴 `ERROR` 时该分量不可用，但依赖它的姿态合成必须失败，不能
/// 当作 `ABSENT` 用基础姿态静默补全。
@NullMarked
public sealed interface AxisSample permits AxisSample.Absent, AxisSample.Value, AxisSample.Error {

    /// 未编写 / 无值。
    record Absent() implements AxisSample {
    }

    /// 有限数值。
    ///
    /// @param value 轴值；必须有限
    record Value(double value) implements AxisSample {
        public Value {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("AxisSample.Value must be finite: " + value);
            }
        }
    }

    /// 求值错误；必须携带 ERROR 诊断。
    ///
    /// @param diagnostic 结构化错误，不得被当作缺失
    record Error(Diagnostic diagnostic) implements AxisSample {
        public Error {
            Objects.requireNonNull(diagnostic, "diagnostic");

            if (diagnostic.severity() != Severity.ERROR) {
                throw new IllegalArgumentException("AxisSample.Error requires an ERROR diagnostic");
            }
        }
    }

    /// 是否为有限值。
    default boolean isValue() {
        return this instanceof Value;
    }

    /// 是否为错误。
    default boolean isError() {
        return this instanceof Error;
    }

    /// 是否为缺失。
    default boolean isAbsent() {
        return this instanceof Absent;
    }
}
