package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 资源保存结果（主设计 §8.2、§8.4）。
///
/// [Saved] 只表示字节已写入并生成元数据；恢复槽写入不得把候选 / 自动恢复版本误报为
/// 用户正式保存。失败不发布部分文件，由实现使用临时文件与原子替换。
@NullMarked
public sealed interface SaveResult permits SaveResult.Saved, SaveResult.Rejected {

    /// 保存成功。
    record Saved(AnimationResourceEntry entry) implements SaveResult {
        public Saved {
            Objects.requireNonNull(entry, "entry");
        }
    }

    /// 保存失败；不得留下部分正式文件。
    record Rejected(DiagnosticReport report) implements SaveResult {
        public Rejected {
            Objects.requireNonNull(report, "report");

            if (report.diagnostics().isEmpty()) {
                throw new IllegalArgumentException("SaveResult.Rejected requires at least one diagnostic");
            }
        }
    }
}
