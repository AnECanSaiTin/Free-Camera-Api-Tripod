package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import org.jspecify.annotations.NullMarked;

/// 客户端锚点解析器注册表（主设计 §2.2、§6.3）。
///
/// 与 common 侧 [AnchorTypeRegistry] 是**两张表**：
///
/// | 表 | 侧 | 缺项后果 |
/// | --- | --- | --- |
/// | [AnchorTypeRegistry] | common | 文档解码错误（结构期） |
/// | 本表 | 客户端 | 运行能力缺失：文档可读可编辑，但不能完整播放/预览 |
///
/// 两张表都按 `Identifier` 分派，都用 [TypeRegistry] 的冻结语义；实现必须在 `freeze()` 前
/// 校验 `resolver.typeId()` 非空且不重复，冻结后拒绝注册。
///
/// 客户端实现不得在 common 初始化时加载（主设计 §7.7）。
@NullMarked
public interface AnchorResolverRegistry extends TypeRegistry<AnchorResolver> {
}
