package cn.anecansaitin.hm.api.animation.registry;

/// 文档类型注册表。
///
/// 同时暴露 [DocSetKeyRegistry]：所有 api 编解码器只拿一个注册表句柄，
/// 避免每个 codec 工厂都多传一个参数（`DOC_SET` 的键策略 id 就靠它解析）。
public interface DocTypeRegistry extends Registry<DocType<?>> {
    DocSetKeyRegistry keys();
}
