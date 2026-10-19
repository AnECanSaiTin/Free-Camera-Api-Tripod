package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 锚点类型的描述：类型 ID + 结构 Codec + 网络 Codec（主设计 §6.3、D12、§8.3）。
///
/// ## 两个 Codec 都要
///
/// - [codec]：文档 Codec，负责存档里“锚点类型 + 完整参数”的读写。
/// - [streamCodec]：网络 Codec。播放请求按 `RigId` 携带锚点类型与**完整参数**
///   （D12：不传选择器字符串），因此锚点必须能双端解码。
///
/// 两个 Codec 由**同一个类型**声明，避免“存档能读、网络不能读”这种半可用状态：
/// 类型注册时两者必须同时就位。
///
/// ## common 侧只有编解码
///
/// 本接口只描述**数据**：类型 ID 与 Codec。把锚点解析成世界坐标的解析器
/// 需要世界数据（`Level` / `Entity`），属于客户端部件，单独注册
/// （主设计 §6.3、§7.7：不得在 common 初始化时加载客户端类）。
///
/// 因此“类型已注册但客户端解析器缺失”是**运行能力缺失**，
/// 与“类型不存在（解码期错误）”是两类问题，诊断必须分开报告。
///
/// ## 参数必须静态
///
/// 锚点参数只能是静态坐标或外部身份引用（实体 UUID、玩家 UUID、固定坐标），
/// **不能**依赖待求值相机、公式或其他样本（主设计 §7.7）：
/// 锚点在帧准备阶段解析，若它的参数依赖求值结果就会形成循环依赖。
///
/// @param <T> 该类型产生的锚点实现
@NullMarked
public interface AnchorType<T extends Anchor> {

    /// 类型标识，与 `Anchor.type()` 一致。
    Identifier id();

    /// 文档结构编解码：类型参数 → 不可变锚点值。
    ///
    /// 实现必须拒绝非有限坐标与非法参数；解码失败返回 DFU 错误，由存储层转成文件诊断。
    MapCodec<T> codec();

    /// 网络编解码：用于播放请求与来源数据。
    ///
    /// 未知类型与缺失实现必须在编解码边界转成**可报告的请求失败**
    /// （主设计 §8.3），不得静默构造默认锚点。
    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec();
}
