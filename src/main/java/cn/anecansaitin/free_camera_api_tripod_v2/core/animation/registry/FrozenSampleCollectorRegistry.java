package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollector;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectorRegistry;

/// 默认客户端世界采样采集器注册表（主设计 §2.2、§7.7）。
///
/// 与 common 侧采样类型注册表分开：本表只注册运行期采集器，缺项表示当前客户端不能完整
/// 采集该类型，不改变文档是否可解码。冻结、重复 ID 与冻结后注册的语义由
/// [FrozenTypeRegistry] 统一提供。
public final class FrozenSampleCollectorRegistry extends FrozenTypeRegistry<SampleCollector>
        implements SampleCollectorRegistry {

    public FrozenSampleCollectorRegistry() {
        super("sample collector", SampleCollector::typeId);
    }
}
