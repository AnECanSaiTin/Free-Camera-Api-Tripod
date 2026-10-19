package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import org.jspecify.annotations.NullMarked;

/// 锚点类型的注册表（主设计 §2.2、§6.3、§8.3）。
///
/// 内建锚点（固定坐标、实体 UUID、玩家 UUID）与第三方锚点都通过本表按类型 ID 分派：
/// 文档 Codec 与网络 Codec 都查这里，因此**同一份类型表**同时服务存档与播放请求，
/// 不会出现“存档认得的类型、网络不认得”的不一致。
///
/// 未注册的锚点类型在解码期报错；类型已注册但客户端解析器缺失时，
/// 属于运行能力缺失，由客户端侧单独报告（见 [AnchorType]）。
@NullMarked
public interface AnchorTypeRegistry extends TypeRegistry<AnchorType<?>> {
}
