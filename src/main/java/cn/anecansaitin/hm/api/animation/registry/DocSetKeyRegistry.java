package cn.anecansaitin.hm.api.animation.registry;

/// 集合索引键策略的注册表。
///
/// 与 [DocTypeRegistry] 平行：`DocType` 决定"对象是什么、怎么编解码"，
/// `DocSetKey` 决定"集合按什么建索引"。
public interface DocSetKeyRegistry extends Registry<DocSetKey<?, ?>> {
}
