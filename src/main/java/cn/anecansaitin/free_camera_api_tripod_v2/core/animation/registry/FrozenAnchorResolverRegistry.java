package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolver;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolverRegistry;

/// 默认客户端锚点解析器注册表（主设计 §2.2、§6.3、§7.7）。
///
/// 与 common 侧锚点类型注册表分开：本表只注册运行期解析器，缺项表示当前客户端不能完整
/// 解析该类型，不改变文档是否可解码。冻结、重复 ID 与冻结后注册的语义由
/// [FrozenTypeRegistry] 统一提供。
public final class FrozenAnchorResolverRegistry extends FrozenTypeRegistry<AnchorResolver>
        implements AnchorResolverRegistry {

    public FrozenAnchorResolverRegistry() {
        super("anchor resolver", AnchorResolver::typeId);
    }
}
