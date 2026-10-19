package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import org.jspecify.annotations.NullMarked;

/// 世界采样类型的注册表（主设计 §2.2、§7.7、§8.1）。
///
/// 解码流程必须按这个顺序：先读 `WorldSample.kind`，再查本表拿该类型的 `MapCodec`
/// 解参数，最后校验 `kind == args.type()`（`WorldSample` 构造器已强制）。
///
/// 未注册类型**解码期报错**；已注册类型但客户端采集器缺失时，
/// 帧准备阶段报告**运行能力缺失**，不得把两者合并成同一条诊断。
@NullMarked
public interface SampleTypeRegistry extends TypeRegistry<SampleType<?>> {
}
