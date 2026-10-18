package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 文档校验器：单一层级的只读检查（主设计 §4.2、§4.3）。
///
/// 内置与第三方校验器在启动阶段注册并冻结；提交、解码完成与收包载入采用同一组强制规则。
/// 校验器**不得修改候选**；发现问题以 [Diagnostic] 报告，由提交层决定拒绝或提供显式修复。
@NullMarked
@FunctionalInterface
public interface DocumentValidator {

    /// 本校验器所属层级。
    ///
    /// 默认 L2（结构）只适用于快速内联校验；注册表要求实现显式覆写本方法。
    default ValidationLayer layer() {
        return ValidationLayer.STRUCTURE;
    }

    /// 校验候选；返回值不得为空，可返回空列表。
    List<Diagnostic> validate(CameraAnimation candidate);
}
