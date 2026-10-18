package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 单个资源的 IO / 解码诊断报告（主设计 §8.2）。
///
/// 报告头 [resource] 与每条诊断的 [DiagnosticScope.File] 资源必须一致；不得混入
/// `Document(Revision)` 诊断或其他资源的诊断。文档校验诊断属于文档作用域，由校验 / 提交
/// 链路单独报告，不塞进 storage 的单资源报告。该约束避免消费方按报告头定位到错误文件。
///
/// 报告按一次操作汇总，不构造可发布的非法文档；资源错误不阻断编辑，只作为可诊断结果返回。
///
/// @param resource    资源身份
/// @param diagnostics 诊断；全部为同一资源的 File 作用域，顺序稳定
@NullMarked
public record DiagnosticReport(ResourceId resource, List<Diagnostic> diagnostics) {

    public DiagnosticReport {
        Objects.requireNonNull(resource, "resource");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);

        for (Diagnostic diagnostic : diagnostics) {
            if (!(diagnostic.scope() instanceof DiagnosticScope.File file)) {
                throw new IllegalArgumentException(
                        "DiagnosticReport requires File-scoped diagnostics only");
            }

            if (!file.resource().equals(resource)) {
                throw new IllegalArgumentException(
                        "DiagnosticReport resource must match every File diagnostic resource");
            }
        }
    }

    /// 是否包含 ERROR 级诊断。
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }
}
