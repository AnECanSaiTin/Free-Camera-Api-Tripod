package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 一条结构化诊断：稳定错误码、严重级别、可本地化消息、对象 / 字段定位与可选修复
/// （主设计 §4.1、§4.2）。
///
/// 带引用的修复必须关联基版本（[Fix#baseRevision]）；过期修复需重新生成，不能套到新上下文。
/// 诊断本身不可变；校验器不得通过诊断修改候选。
///
/// @param code    稳定错误码
/// @param severity 严重级别
/// @param message 面向用户的可本地化消息
/// @param location 对象 / 字段定位；整体文档级消息可为空
/// @param fix     可选修复操作；须在应用前重新检查基版本
@NullMarked
public record Diagnostic(
        DiagnosticCode code,
        Severity severity,
        Component message,
        @Nullable Location location,
        @Nullable Fix fix) {

    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
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
    /// @param label        面向用户的修复说明
    /// @param operation    修复操作；不得修改输入文档
    /// @param baseRevision 生成该修复时的基版本；应用前须与当前序号比较
    public record Fix(Component label, EditOp operation, Revision baseRevision) {

        public Fix {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(baseRevision, "baseRevision");
        }
    }
}
