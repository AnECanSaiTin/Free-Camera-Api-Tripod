package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 文档编解码所需的三个冻结注册表（主设计 §8.1、§8.3）。
///
/// ## 为什么打包成一个值
///
/// 文档 Codec 需要按类型分派三处：
///
/// | 数据 | 注册表 |
/// | --- | --- |
/// | `Space.Local.defaultAnchor`、`AimTarget.target` 等锚点 | [AnchorTypeRegistry] |
/// | `CameraAnimation.tracks` 的扩展轨道 | [TrackTypeRegistry] |
/// | `WorldSample.args` 的参数 | [SampleTypeRegistry] |
///
/// 三者总是一起使用（同一份文档解码 / 编码），单独传递会让每个成员 Codec 的方法签名
/// 都多出三个参数。打包后成员 Codec 统一接收本值，并在**构造时**确认非空。
///
/// ## 冻结与未知类型
///
/// 注册表必须已经冻结（启动完成后）；本值不负责冻结，只负责把“查不到类型”
/// 变成**可报告的解码错误**：
///
/// - 未知类型在解码时返回 `DataResult` 错误，不构造默认锚点 / 采样参数 / 轨道；
/// - 类型存在但客户端解析器缺失，属于**运行能力缺失**，不在这里报告
///   （解析器只在客户端注册，见主设计 §6.3、§7.7）。
///
/// @param anchors 锚点类型注册表
/// @param tracks  扩展轨道类型注册表
/// @param samples 世界采样类型注册表
@NullMarked
public record DocumentRegistries(
        AnchorTypeRegistry anchors,
        TrackTypeRegistry tracks,
        SampleTypeRegistry samples) {

    public DocumentRegistries {
        Objects.requireNonNull(anchors, "anchors");
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(samples, "samples");
    }

    /// 锚点的分派 Codec：按 `Anchor.type()` 查注册表，再交给该类型的 `MapCodec`。
    ///
    /// 编码写成 `{"type": "<锚点类型 ID>", ...该类型的字段...}`。
    ///
    /// 未知类型在解码时返回错误。由于 `Codec#dispatch` 的类型分派函数无法返回错误，
    /// 这里对未注册 ID 返回一个**总是失败**的 Codec：错误信息指名未注册的类型 ID，
    /// 而不是抛异常或回退到某个默认锚点。
    public Codec<Anchor> anchorCodec() {
        return Identifier.CODEC.<Anchor>partialDispatch(
                "type",
                anchor -> DataResult.success(anchor.type()),
                id -> anchors.find(id)
                        .<DataResult<MapCodec<? extends Anchor>>>map(type -> DataResult.success(type.codec()))
                        .orElseGet(() -> DataResult.error(() -> "Unknown anchor type: " + id)));
    }
}
