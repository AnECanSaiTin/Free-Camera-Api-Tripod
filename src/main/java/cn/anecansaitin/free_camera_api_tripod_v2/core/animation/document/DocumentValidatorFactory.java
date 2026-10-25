package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DocumentValidator;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 按候选发布序号装配 [DocumentValidator]。
///
/// 统一校验器需要把 `Revision` 放进每条诊断的 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope.Document]
/// 与 `TypeValidationContext`，但 [DocumentValidator#validate] 只接收候选文档。与其修改公开接口，
/// 不如在每次发布/校验时用闭包捕获本次序号，得到本次请求专属的只读校验器。
@NullMarked
@FunctionalInterface
public interface DocumentValidatorFactory {

    /// 为一次候选发布构造校验器。
    ///
    /// @param revision 候选将发布的序号
    DocumentValidator forRevision(Revision revision);

    /// 固定的校验器（测试或不需要版本信息的宿主）。
    static DocumentValidatorFactory fixed(DocumentValidator validator) {
        Objects.requireNonNull(validator, "validator");
        return _ -> validator;
    }
}
