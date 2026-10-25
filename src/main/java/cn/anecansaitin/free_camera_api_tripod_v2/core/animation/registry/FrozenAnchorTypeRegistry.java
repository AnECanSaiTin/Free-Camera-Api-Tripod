package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorType;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorTypeRegistry;

/// 默认锚点类型注册表。
public final class FrozenAnchorTypeRegistry extends FrozenTypeRegistry<AnchorType<?>>
        implements AnchorTypeRegistry {

    public FrozenAnchorTypeRegistry() {
        super("anchor", AnchorType::id);
    }
}
