package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 空间锚点 / 目标的一帧解析结果：不需要 / 成功 / 失败（主设计 §6.1、§7.2）。
///
/// 不能用可空字段同时表达“这个上下文不需要”和“解析失败”：前者可以继续合成，后者必须
/// 阻止引用它的姿态或通道，并携带结构化诊断。
@NullMarked
public sealed interface SpatialResolution
        permits SpatialResolution.NotRequired, SpatialResolution.Resolved, SpatialResolution.Failed {

    /// 当前上下文不需要该锚点 / 目标。
    record NotRequired() implements SpatialResolution {
    }

    /// 解析成功。
    ///
    /// @param anchor 一帧内解析结果
    record Resolved(ResolvedAnchor anchor) implements SpatialResolution {
        public Resolved {
            Objects.requireNonNull(anchor, "anchor");
        }
    }

    /// 解析失败；必须携带 ERROR 诊断。
    ///
    /// @param diagnostic 失败原因
    record Failed(Diagnostic diagnostic) implements SpatialResolution {
        public Failed {
            Objects.requireNonNull(diagnostic, "diagnostic");

            if (diagnostic.severity() != Severity.ERROR) {
                throw new IllegalArgumentException("SpatialResolution.Failed requires an ERROR diagnostic");
            }
        }
    }

    /// 是否成功解析。
    default boolean isResolved() {
        return this instanceof Resolved;
    }

    /// 是否失败。
    default boolean isFailed() {
        return this instanceof Failed;
    }
}
