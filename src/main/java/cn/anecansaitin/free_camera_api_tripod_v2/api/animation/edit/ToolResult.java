package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 编辑工具结果：可提交的完整候选，或整体拒绝（主设计 §4.6、§4.7）。
///
/// 工具只生成候选，不发布、不执行 IO、不修改输入文档；调用方仍须经文档存储校验与原子提交。
@NullMarked
public sealed interface ToolResult permits ToolResult.Ready, ToolResult.Rejected {

    /// 完整候选；可带提示性诊断，但不得含 ERROR。
    record Ready(EditOp operation, List<Diagnostic> diagnostics) implements ToolResult {
        public Ready {
            Objects.requireNonNull(operation, "operation");
            diagnostics = List.copyOf(diagnostics);

            if (diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR)) {
                throw new IllegalArgumentException("ToolResult.Ready must not carry an ERROR diagnostic");
            }
        }
    }

    /// 整体拒绝；至少一条诊断，不返回部分操作。
    record Rejected(List<Diagnostic> diagnostics) implements ToolResult {
        public Rejected {
            diagnostics = List.copyOf(diagnostics);

            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("ToolResult.Rejected requires at least one diagnostic");
            }
        }
    }
}
