package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 资源载入结果（主设计 §8.2）。
///
/// [Loaded] 只表示**字节解码成功**，不表示文档已发布：调用方仍须执行完整文档校验，
/// 通过后再由文档存储发布；不得把部分解码结果当作合法文档。
@NullMarked
public sealed interface LoadResult permits LoadResult.Loaded, LoadResult.Rejected {

    /// 解码成功；文档尚未发布。
    ///
    /// @param document 解码出的文档，尚未经过强制文档校验
    /// @param entry    资源元数据
    record Loaded(CameraAnimation document, AnimationResourceEntry entry) implements LoadResult {
        public Loaded {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(entry, "entry");
        }
    }

    /// 载入失败；不返回部分文档。
    record Rejected(DiagnosticReport report) implements LoadResult {
        public Rejected {
            Objects.requireNonNull(report, "report");

            if (report.diagnostics().isEmpty()) {
                throw new IllegalArgumentException("LoadResult.Rejected requires at least one diagnostic");
            }
        }
    }
}
