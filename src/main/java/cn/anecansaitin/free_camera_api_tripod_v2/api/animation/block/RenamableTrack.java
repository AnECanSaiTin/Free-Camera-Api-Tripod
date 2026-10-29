package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;

/// 可选改名能力：支持通用改名编辑的轨道显式实现本接口。
///
/// 它**不继承** [AnimationTrack]，不扩大 sealed permits；内建轨道与愿意支持改名的扩展显式
/// 声明能力，具体 record 可用协变返回类型。这样固定名 / 只读扩展不必被迫实现无用方法，
/// 通用编辑也不必靠反射或装饰器猜测能力。
///
/// 实现必须返回**同一具体类型**的新实例，保留 [AnimationTrack#id()]、
/// [AnimationTrack#type()] 与该类型 Codec 能编码的全部字段，只替换显示名；不得返回框架
/// 不认识的包装器，否则按类型注册表分派的 Codec 可能拒绝该对象。
@NullMarked
public interface RenamableTrack {

    /// 返回替换显示名后的同类型实例。
    ///
    /// @param name 新显示名，非空白
    /// @return 新实例；不得修改当前对象
    AnimationTrack withName(String name);
}
