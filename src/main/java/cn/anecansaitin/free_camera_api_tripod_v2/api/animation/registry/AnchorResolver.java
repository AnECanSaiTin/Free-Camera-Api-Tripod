package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.SpatialResolution;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 锚点的**客户端解析器**：把静态锚点参数解析成本帧空间事实（主设计 §6.3、§7.7）。
///
/// ## 为什么在客户端注册
///
/// 解析需要已加载的世界、实体与玩家数据，属于客户端运行部件；common 侧只注册类型 ID 与
/// Codec（[AnchorType]）。这里的契约本身不引用 `net.minecraft.client`，实现由客户端在启动
/// 阶段注册到 [AnchorResolverRegistry]，不得在 common 类初始化时加载（主设计 §7.7）。
///
/// ## 失败语义
///
/// - 解析器缺失是**运行能力缺失**，由 [TypeCapabilityService] 报告，不是解码错误；
/// - 解析本身失败（实体不存在、维度不匹配、数据未同步）必须返回
///   [SpatialResolution.Failed]，携带 ERROR 诊断；**不得**回退到文档默认锚点或其他坐标
///   （主设计 §6.3、不变量 I9）；
/// - 诊断的作用域必须取自 [AnchorResolutionContext#diagnosticScope]（与本次快照同一发布
///   版本），不能读取当前 store 判断版本，也不能使用文件作用域；对象 / 字段定位是可选项，
///   解析器不知道 `RigId` 时留空即可（复核 R49-H2）；
/// - 读取已加载数据不得触发区块加载；当前不可用属于运行失败，不是“不需要”。
///
/// ## 只解析被要求的事实
///
/// 是否解析某个锚点或目标由宿主的帧准备计划决定（`FrameFactsPlan`，复核 R49-H1）：
/// 实现只对收到的单个锚点负责，不得自行读取当前播放器 / 编辑器选中机位或文档默认值来补足输入。
///
/// ## 必须按类型实现
///
/// 实现只处理自己 [typeId] 的锚点；收到其它类型时应返回失败诊断，而不是尝试猜测。
/// 同一个解析器实例可以在同一帧内被多次调用，实现必须无副作用、线程使用遵循宿主约定。
@NullMarked
public interface AnchorResolver {

    /// 本解析器负责的锚点类型 ID，必须与注册的类型一致。
    Identifier typeId();

    /// 解析一个锚点。
    ///
    /// @param anchor  要解析的锚点；`anchor.type()` 必须等于 [typeId]
    /// @param context 本帧客户端世界读数与诊断作用域
    /// @return 成功事实或 ERROR 失败；不得返回“不需要”
    SpatialResolution resolve(Anchor anchor, AnchorResolutionContext context);
}
