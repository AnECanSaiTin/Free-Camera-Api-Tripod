package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 资源载入 / 保存的诊断报告（主设计 §8.2）。
///
/// 报告按一次操作汇总，不构造可发布的非法文档；资源错误不阻断编辑，只作为可诊断结果返回。
/// 拒绝报告必须至少包含一条诊断。
///
/// @param resource    资源身份
/// @param diagnostics 诊断；顺序稳定，不保留调用方容器
@NullMarked
public record DiagnosticReport(ResourceId resource, List<Diagnostic> diagnostics) {

    public DiagnosticReport {
        Objects.requireNonNull(resource, "resource");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);
    }

    /// 是否包含 ERROR 级诊断。
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }
}
