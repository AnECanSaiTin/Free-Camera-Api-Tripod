package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import org.jspecify.annotations.NullMarked;

/// 客户端世界采样采集器注册表（主设计 §2.2、§7.7）。
///
/// 与 common 侧 [SampleTypeRegistry] 分开：
///
/// | 表 | 侧 | 缺项后果 |
/// | --- | --- | --- |
/// | [SampleTypeRegistry] | common | 文档解码错误（结构期） |
/// | 本表 | 客户端 | 运行能力缺失：文档可读可编辑，但采样读数只能是采集错误 |
///
/// 实现必须在 `freeze()` 前校验 `collector.typeId()` 非空且不重复；冻结后拒绝注册。
/// 采集器只在客户端注册，不得在 common 初始化时加载（主设计 §7.7）。
@NullMarked
public interface SampleCollectorRegistry extends TypeRegistry<SampleCollector> {
}
