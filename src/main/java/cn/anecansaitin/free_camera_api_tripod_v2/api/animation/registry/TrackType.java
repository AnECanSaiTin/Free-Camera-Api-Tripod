package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 扩展轨道类型的描述：类型 ID + 自身字段的 `MapCodec`（主设计 D7、§8.1）。
///
/// ## 为什么上界是 `ExtensionTrack`
///
/// `TrackType` **只描述扩展轨道**：内建轨道（数值曲线、注视、机位活动、相机来源）是框架
/// 固定的 sealed 分支，按所属位置直接编解码，不经过本注册表。因此类型参数收窄到
/// [AnimationTrack.ExtensionTrack]：注册 `TrackType<CurveTrack>` 这类“内建轨道冒充扩展”
/// 的配置会**编译失败**，而不是在文档解码后产生 `List<ExtensionTrack>` 里的异类元素
/// （复核 R34 C3）。
///
/// 内建类型 ID 仍是保留值（`free_camera_api_tripod:curve` / `:aim` / `:active` /
/// `:camera_source`）；扩展轨道若在 `id()` 里返回它们，属于 L2 结构错误。
///
/// ## 数据类型维护自身字段
///
/// 主设计 §8.1：**数据类型维护自身字段名与 Codec**，容器只认识自身字段，不复制子对象字段规则。
/// 因此轨道类型的描述只交出 `MapCodec`，文档 Codec 负责在 `type` 分派后把它组合进去。
///
/// ## 没有 StreamCodec
///
/// 轨道是文档数据，只参与文档 Codec；本轮网络协议只传播放请求与来源数据
/// （主设计 §8.2），不单独传轨道。需要跨端传文档时走“随包文档”的整份 Codec，
/// 不为轨道单独造一套网络编码。
///
/// ## 不变
///
/// 本接口是**描述对象**，不是数据对象：实现应当是不可变的、单例的，
/// 不持有可变状态，也不缓存解码结果。
///
/// @param <T> 该类型产生的扩展轨道实现
@NullMarked
public interface TrackType<T extends AnimationTrack.ExtensionTrack> {

    /// 类型标识。
    ///
    /// 必须落在扩展轨道允许的命名空间内：不得使用框架保留的四个内建 ID。
    Identifier id();

    /// 该轨道类型的结构编解码。
    ///
    /// 负责自身字段的读写与**局部约束**（例如键时间有限非负、键时间唯一）；
    /// 跨对象的引用完整性、作用域与领域约束仍由统一文档校验负责（主设计 §8.1）。
    MapCodec<T> codec();

    /// 可选的类型级校验钩子；默认无类型专属校验。
    default java.util.Optional<TypeValidator<T>> validator() {
        return java.util.Optional.empty();
    }
}
