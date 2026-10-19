package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic.Location;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

    /// 构造带快照作用域的失败结果：严重级别固定 ERROR。
    ///
    /// 客户端解析器应传入 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolutionContext#diagnosticScope]，
    /// 不要自行使用文件作用域或凭当前 store 猜测版本（复核 R49-H2）。
    ///
    /// @param scope 本帧快照的文档作用域
    /// @param code  稳定错误码
    /// @param message 不可变、可本地化消息
    /// @return 携带 ERROR 诊断的失败结果
    static SpatialResolution failed(
            DiagnosticScope.Document scope,
            DiagnosticCode code,
            LocalizedMessage message) {
        return failed(scope, code, message, null);
    }

    /// 构造带快照作用域与可选对象定位的失败结果。
    ///
    /// 对象定位是可选项：解析器只拿到锚点时，`location` 可以为 `null`（复核 R49-H2）。
    ///
    /// @param scope    本帧快照的文档作用域
    /// @param code     稳定错误码
    /// @param message  不可变、可本地化消息
    /// @param location 对象 / 字段定位；可空
    /// @return 携带 ERROR 诊断的失败结果
    static SpatialResolution failed(
            DiagnosticScope.Document scope,
            DiagnosticCode code,
            LocalizedMessage message,
            @Nullable Location location) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");

        return new Failed(new Diagnostic(code, Severity.ERROR, message, scope, location, null));
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
