package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;

/// 一帧事实提供者：解析机位空间事实与声明式世界采样（主设计 §2.2、§3.4、§7.7）。
///
/// 契约在 `api`，实现由 `core.playback` 提供；实现持有冻结的 common 类型注册表与客户端
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolverRegistry] /
/// [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectorRegistry]，
/// 但不得在 common 初始化时加载客户端类（主设计 §7.7）。
///
/// ## 必须满足
///
/// 1. **一帧一次**：同一帧的全部机位、通道与公式读同一份 [FrameFacts]；求值阶段不再访问世界
///    （不变量 I5）。实现不得在多次 `prepare` 之间让调用方看到半更新状态。
/// 2. **只准备计划里的机位与项目**：只为 [FrameFactsRequest#plan] 中的机位填
///    [FrameFacts.RigFacts]，并且只解析该机位 `Required` 的锚点 / 目标；`NotRequired` 写
///    [SpatialResolution.NotRequired]，不能混用 `null` 或失败。计划由宿主按动画时间、播放覆盖与
///    实际依赖算出（复核 R49-H1），实现**不得**再从文档默认锚点、当前播放器、当前编辑机位或
///    上一帧状态推断这些输入。
/// 3. **生效注视状态原样写入**：[FrameFacts.RigFacts#aim] 必须等于计划里的生效状态（含
///    `Inherit` / `Disabled` / `Replace` 覆盖后的结果），不能按文档默认值重新求值。
/// 4. **失败不兜底**：锚点 / 目标解析失败写 [SpatialResolution.Failed] 与 ERROR 诊断；
///    客户端解析器或采集器缺失、采集器抛异常、输出非法、超预算写
///    [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading.CollectionError]；
///    未加载 / 未同步 / 目标不存在写 `Unknown`。任何一项都不许回退到文档默认锚点、上一帧成功值
///    或默认数值（主设计 §6.3、§7.7、D18、不变量 I9）。
/// 5. **诊断作用域**：交给 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolver]
///    的 [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolutionContext]
///    必须携带与 `request.snapshot()` 同版本的诊断作用域；返回的 `Failed` 诊断不得来自其它快照，
///    也不得伪造文件作用域（复核 R49-H2）。
/// 6. **不触发加载**：只读已加载且客户端可见的数据；绝不触发区块加载。
/// 7. **采样节奏**：默认每游戏刻重采，按 `WorldSample.resampleEveryTicks` 与游戏刻判断；
///    渲染帧率变化不得重复采集。同参数采样可去重；暂停时不因渲染频率重采。
/// 8. **表版本**：`sampleTableVersion` 只在读数、有效性或采样时间元数据改变时递增；
///    世界切换、声明 / 参数变化、资源重解析必须使相关缓存失效（主设计 §7.7）。
/// 9. **有效性**：`valid` 表示本帧事实是否完整；缺失解析器、世界不可用、预算耗尽等情况下
///    必须为 `false`，且逐项失败仍要落在 facts 里，不能只给一个布尔值。`valid = true`
///    不代表没有 `Unknown` 采样。
/// 10. **预算**：解析与采集消耗 [FrameFactsRequest#session] 的工作量；不足时按资源失败报告，
///    允许下一帧用新会话重试，不能写入会话无关缓存（复核 R44-N1）。
/// 11. **结果绑定**：返回事实的 [FrameFacts#sourceSnapshot] 必须是 `request.snapshot()` 的
///    同一个对象引用，而不是同 revision / 同内容的新快照；事实只对请求的快照与计划有效，
///    不得跨帧复用或混用（复核 R49-H1、R51-N1）。
/// 12. **线程**：在宿主主线程调用；实现不得自行切换线程访问世界。
@NullMarked
@FunctionalInterface
public interface FrameFactsProvider {

    /// 准备一帧事实。
    ///
    /// @param request 本帧快照、动画时间、世界身份、时间、空间准备计划与共享会话
    /// @return 不可变帧事实；逐项失败已编码在 facts 中
    FrameFacts prepare(FrameFactsRequest request);
}
