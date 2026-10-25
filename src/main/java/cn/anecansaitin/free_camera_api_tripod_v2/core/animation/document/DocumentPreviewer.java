package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Optional;

/// 纯预览入口：把 [EditOp] 应用到一个不可变文档，不校验、不发布、不写历史。
///
/// 编辑器会话需要把草稿操作显示为候选文档，但 `EditOpApplier` 是 core.document 的包内实现。
/// 本类只暴露“应用成功则返回候选，局部应用失败则返回空”，不把内部异常或半成品泄漏给
/// 编辑器；候选在 commit 时仍必须走 store 的统一校验与发布。
@NullMarked
public final class DocumentPreviewer {

    private DocumentPreviewer() {
    }

    /// 应用一个纯操作到候选文档。
    ///
    /// @param document  当前已发布文档
    /// @param operation 纯编辑操作
    /// @return 新候选；目标不存在、类型不匹配或局部约束失败时为空
    public static Optional<CameraAnimation> preview(CameraAnimation document, EditOp operation) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(operation, "operation");

        try {
            return Optional.of(EditOpApplier.apply(document, operation));
        } catch (EditOpApplier.EditApplyException | IllegalArgumentException exception) {
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
