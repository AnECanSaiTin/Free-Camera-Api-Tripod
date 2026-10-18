package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 一次纯编辑操作的结果：成功给出候选文档，失败给出结构化诊断（主设计 §4.1）。
///
/// 本类型只表示“操作是否构造出候选”，**不代表已发布**；发布结果见 [EditResult]。
/// 操作不得修改输入文档；候选失败时原文档与历史不变（不变量 I4）。
@NullMarked
public sealed interface EditOutcome {

    /// 成功构造候选文档。
    record Ok(CameraAnimation candidate) implements EditOutcome {
        public Ok {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    /// 无法构造候选：拒绝整个操作，不部分应用。
    record Err(List<Diagnostic> diagnostics) implements EditOutcome {
        public Err {
            // 防御性复制：不保留调用方容器（主设计 §3.2）
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
