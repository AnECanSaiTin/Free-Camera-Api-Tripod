package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 类型能力查询服务（主设计 §2.2：注册表职责包含“能力描述”）。
///
/// 编辑器与播放宿主在打开文档、预览、试算和正式播放前查询本服务，决定：
///
/// - 能否完整播放 / 预览（缺客户端解析器或采集器）；
/// - 是否需要提示“类型不存在、文档结构有问题”（解码已经失败，通常不会走到这里）；
/// - 面板是否把某类型标为不可用。
///
/// ## 实现来源
///
/// 由宿主在启动装配阶段创建，持有 common 侧三个类型注册表与客户端
/// [AnchorResolverRegistry] / [SampleCollectorRegistry] 的冻结快照。查询是只读的；
/// 不得因为查询而注册类型或加载客户端类（主设计 §7.7）。
///
/// ## 不改变解码与校验
///
/// 能力缺失不使文档变成“结构非法”：解码仍按类型 ID 成功，统一校验也不因此拒绝发布。
/// 它只影响**本环境能否完整运行**；宿主必须把“类型不存在”和“实现缺失”分别报告
/// （主设计 §6.3）。
@NullMarked
public interface TypeCapabilityService {

    /// 查询锚点类型能力。
    ///
    /// @param typeId 锚点类型 ID
    /// @return 类型未注册、实现缺失或可用
    TypeCapability anchor(Identifier typeId);

    /// 查询世界采样类型能力。
    ///
    /// @param typeId 采样类型 ID
    /// @return 类型未注册、采集器缺失或可用
    TypeCapability sample(Identifier typeId);

    /// 查询扩展轨道类型能力。
    ///
    /// 扩展轨道由通用轨道机制执行，没有独立的客户端执行器；因此只有“已注册”与
    /// “未注册”两种状态，不存在 `RUNTIME_IMPLEMENTATION_MISSING`。
    ///
    /// @param typeId 扩展轨道类型 ID
    /// @return 已注册或未注册
    TypeCapability track(Identifier typeId);
}
