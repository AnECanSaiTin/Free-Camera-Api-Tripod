package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 一次提交的结果：已发布新快照，或整体拒绝（主设计 §4.1、§3.3）。
///
/// 拒绝时文档、历史与发布序号均不变（不变量 I4）。成功后快照携带新发布序号；
/// 撤销恢复旧内容时也生成新序号。
@NullMarked
public sealed interface EditResult {

    /// 已发布：新快照 + 可选的提示性诊断（不是错误）。
    record Applied(AnimationSnapshot snapshot, List<Diagnostic> diagnostics) implements EditResult {
        public Applied {
            Objects.requireNonNull(snapshot, "snapshot");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /// 整体拒绝：至少一条诊断；不得部分发布。
    record Rejected(List<Diagnostic> diagnostics) implements EditResult {
        public Rejected {
            diagnostics = List.copyOf(diagnostics);

            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("EditResult.Rejected requires at least one diagnostic");
            }
        }
    }
}
