package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackTypeRegistry;

/// 默认扩展轨道类型注册表。
public final class FrozenTrackTypeRegistry extends FrozenTypeRegistry<TrackType<?>>
        implements TrackTypeRegistry {

    public FrozenTrackTypeRegistry() {
        super("track", TrackType::id);
    }
}
