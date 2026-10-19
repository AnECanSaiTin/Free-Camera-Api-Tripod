package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.Set;

/// 开放类型的注册表：按 `Identifier` 分派，启动时注册并冻结（主设计 §2.2、§4.2、§8.1）。
///
/// ## 生命周期
///
/// ```text
/// 启动阶段：register(...) 若干次 → freeze()
/// 运行阶段：find(...) / ids() 只读
/// ```
///
/// 冻结后 `register` 必须抛 [IllegalStateException]。冻结前重复 ID 必须抛
/// [IllegalArgumentException]，**不得**静默覆盖——覆盖会让“同一个 `Identifier` 在不同
/// 加载顺序下指向不同 Codec”，存档解析结果取决于 mod 加载顺序，是不可接受的隐性行为。
///
/// ## 查询语义
///
/// `find` 返回空表示**类型未注册**。调用方（解码器 / 校验器）必须把它转成可报告的错误，
/// 不得回退到默认值或跳过该对象（主设计 §8.1：未知类型必须报错；
/// §7.7：类型不存在在解码时报错）。
///
/// 冻结后的查询必须线程安全且无阻塞：解码可能发生在工作线程，
/// 但读的是一份不可变快照。
///
/// ## 与能力 / 校验的关系
///
/// 注册表职责还包括“能力描述与校验”（主设计 §2.2），但两者的接口形状尚未定稿，
/// 见包说明“本批边界”。本接口只声明 ID 分派与冻结语义。
///
/// @param <T> 注册项类型（类型描述对象，不是数据对象）
@NullMarked
public interface TypeRegistry<T> {

    /// 按类型 ID 查找类型描述；未注册返回空。
    ///
    /// @param id 类型标识
    Optional<T> find(Identifier id);

    /// 已注册的全部类型 ID，供诊断“已注册类型有哪些”与 UI 枚举。
    ///
    /// 返回不可变集合；顺序不作保证（类型 ID 的字典序不表达任何语义）。
    Set<Identifier> ids();

    /// 注册一个类型描述。只能在冻结前调用。
    ///
    /// @param type 类型描述
    /// @throws IllegalStateException    已冻结
    /// @throws IllegalArgumentException ID 重复
    void register(T type);

    /// 冻结注册表。幂等：重复调用无副作用。
    void freeze();

    /// 是否已冻结。
    boolean frozen();
}
