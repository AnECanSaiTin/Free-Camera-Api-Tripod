package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.ValidationLayer;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 类型级校验钩子：校验一个已解码的轨道、锚点或采样参数实例（主设计 §2.2、§4.2）。
///
/// 只报告该类型自身的约束；跨对象引用、区间与模式约束仍由统一 `DocumentValidator` 负责。
/// 校验器不得修改候选或读取当前 store，返回空列表表示通过。
@NullMarked
@FunctionalInterface
public interface TypeValidator<T> {

    /// 校验器所属层级；默认 L2 结构。
    default ValidationLayer layer() {
        return ValidationLayer.STRUCTURE;
    }

    /// 校验单个类型实例。
    List<Diagnostic> validate(T value, TypeValidationContext context);
}
