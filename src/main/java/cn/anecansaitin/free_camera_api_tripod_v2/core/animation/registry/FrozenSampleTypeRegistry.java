package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleTypeRegistry;

/// 默认世界采样类型注册表。
public final class FrozenSampleTypeRegistry extends FrozenTypeRegistry<SampleType<?>>
        implements SampleTypeRegistry {

    public FrozenSampleTypeRegistry() {
        super("sample", SampleType::id);
    }
}
