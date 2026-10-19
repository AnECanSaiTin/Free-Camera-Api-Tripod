package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// 世界采样类型的描述：类型 ID + 参数的 `MapCodec`（主设计 §7.7、D16）。
///
/// ## 只定“参数怎么存”
///
/// `WorldSample` 只声明“要采哪个世界量”的静态参数，参数形状由**类型自己**的
/// `MapCodec` 定义（坐标、方块 id、UUID、记分板目标名…）。解码顺序是
/// “先读 `kind`，再查冻结注册表拿到该类型的 `MapCodec`，最后解参数”
/// （主设计 §8.1）。
///
/// ## 没有网络 Codec
///
/// 本轮世界采样**只在客户端本地采集**，不提供服务端采样表下发（D24）：
/// 因此参数只需文档 Codec，不需要网络 Codec。未来若加入服务端附表，
/// 需要另行定义网络形状与一致性承诺，不能直接复用本接口假定“已经能传”。
///
/// ## 采集器不在 common
///
/// 真正读取方块 / 实体 / 记分板的**采集器**属于客户端部件，单独注册
/// （主设计 §7.7）。采集器缺失是运行能力缺失；类型不存在是解码错误。两者不可混淆。
///
/// ## 能力描述不在本批
///
/// 主设计 §2.2 把“能力描述”列在注册表职责内。本批未定义其接口形状：
/// 能力是运行期概念（需要区分“采集器缺失”“宿主版本不支持”“数据非法”），
/// 先写死一个形状容易把可恢复的能力缺失与不可恢复的求值错误混在一起。
/// 该形状随客户端采集器批次一并裁决。
///
/// @param <T> 该类型产生的参数值
@NullMarked
public interface SampleType<T extends SampleArgs> {

    /// 类型标识，必须与 `SampleArgs.type()` 及 `WorldSample.kind` 一致。
    Identifier id();

    /// 参数值的结构编解码。实现必须拒绝非法参数（非有限坐标、未知枚举等）。
    MapCodec<T> codec();

    /// 可选的类型级校验钩子；默认无类型专属校验。
    default java.util.Optional<TypeValidator<T>> validator() {
        return java.util.Optional.empty();
    }
}
