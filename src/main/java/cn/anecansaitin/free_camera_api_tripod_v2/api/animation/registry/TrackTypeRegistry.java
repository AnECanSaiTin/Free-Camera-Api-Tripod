package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 扩展轨道类型的注册表（主设计 §2.2、§8.1）。
///
/// 冻结后，文档解码按轨道 `type` 查询本表：命中则用其 `MapCodec` 解码，
/// 未命中**报错**，不构造占位轨道、不丢弃该轨道（主设计 §8.1）。
///
/// 内建轨道类型不注册，因此 `find` 对内建 ID 返回空是**正常**的：
/// 解码器必须先按 sealed 分支识别内建类型，再落到本表查扩展类型。
@NullMarked
public interface TrackTypeRegistry extends TypeRegistry<TrackType<?>> {
}
