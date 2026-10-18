package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 一条结构化诊断：稳定错误码、严重级别、不可变消息、作用域、对象 / 字段定位与可选修复
/// （主设计 §4.1、§4.2；§6.5 C3 / C4 裁决）。
///
/// ## 作用域
///
/// [scope] 显式区分：
///
/// - [DiagnosticScope.Document]：候选 / 已发布文档诊断，带适用发布版本；允许对象 / 字段定位
///   与 `Fix`，异步结果与换档后相同 ID 的场景必须按版本判断过期；
/// - [DiagnosticScope.File]：文件 / 解码诊断，不属于已发布文档；不允许对象 ID 定位与 `Fix`，
///   避免伪装成可提交候选诊断。
///
/// 不再用可空 `baseRevision` 兼表两种作用域。带 [Fix] 的诊断必须是 `Document` 作用域，
/// 且 `Fix.baseRevision` 与作用域版本一致。
///
/// ## 消息不可变
///
/// [message] 与 [Fix#label] 使用 [LocalizedMessage]，只含不可变文本、翻译参数与受限样式；
/// 不保存任何可变 `Component`。宿主在显示时渲染为 `Component`。
///
/// @param code     稳定错误码
/// @param severity 严重级别
/// @param message  不可变、可本地化消息
/// @param scope    诊断作用域
/// @param location 对象 / 字段定位；文件作用域不得带对象 ID
/// @param fix      可选修复；仅文档作用域允许
@NullMarked
public record Diagnostic(
        DiagnosticCode code,
        Severity severity,
        LocalizedMessage message,
        DiagnosticScope scope,
        @Nullable Location location,
        @Nullable Fix fix) {

    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(scope, "scope");

        if (scope instanceof DiagnosticScope.File) {
            if (fix != null) {
                throw new IllegalArgumentException("File-scoped Diagnostic must not carry a Fix");
            }

            if (location != null && location.objectId() != null) {
                throw new IllegalArgumentException(
                        "File-scoped Diagnostic must not locate a DocumentObjectId");
            }
        }

        if (fix != null) {
            if (!(scope instanceof DiagnosticScope.Document document)) {
                throw new IllegalArgumentException("Diagnostic with a Fix requires a Document scope");
            }

            if (!fix.baseRevision().equals(document.revision())) {
                throw new IllegalArgumentException(
                        "Diagnostic scope revision must match Fix.baseRevision: "
                                + document.revision() + " != " + fix.baseRevision());
            }
        }
    }

    /// 对象或字段定位。
    ///
    /// @param objectId  出错对象；纯文档级字段可为空
    /// @param fieldPath 字段路径，例如 `rigs[0].view.rect.width`；空串表示对象整体
    public record Location(@Nullable DocumentObjectId objectId, String fieldPath) {

        public Location {
            Objects.requireNonNull(fieldPath, "fieldPath");
        }
    }

    /// 可选修复：一个纯编辑操作 + 其适用的基版本。
    ///
    /// @param label        不可变的修复说明
    /// @param operation    修复操作；不得修改输入文档
    /// @param baseRevision 生成该修复时的基版本；必须与诊断作用域版本一致
    public record Fix(LocalizedMessage label, EditOp operation, Revision baseRevision) {

        public Fix {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(baseRevision, "baseRevision");
        }
    }
}
