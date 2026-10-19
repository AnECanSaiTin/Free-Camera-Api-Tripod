package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import com.mojang.serialization.DataResult;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 网络播放动作的编解码契约（主设计 §8.3、D12）。
///
/// ## 为什么不是只给 `StreamCodec`
///
/// MC 的 [StreamCodec] 用异常表达失败；但设计要求“未知类型、缺失实现、版本或规模错误”
/// 都变成**可报告的请求失败**。因此本接口提供两条路径：
///
/// - [decode] / [encode] 返回 [DataResult]，网络边界可以直接把错误消息转成拒绝原因；
/// - [streamCodec] 是给 `StreamCodec` 注册点用的适配器，失败时抛
///   [DecoderException] / [EncoderException]，由包处理器捕获并报告。
///
/// ## 必须满足
///
/// 1. 先写/先读 [PlaybackProtocol#CURRENT_VERSION]；版本不符直接失败，不尝试向后兼容猜测。
/// 2. 解码在分配与遍历前检查 [PlaybackLimits]；超限失败，不返回部分请求。
/// 3. 锚点按**类型 ID + 完整参数**编码（[cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorType#streamCodec]），
///    不传输选择器字符串；未知类型、缺失锚点类型 Codec 一律失败。
/// 4. 覆盖必须保留“继承 / 显式禁用 / 替换”三态；不能用可空值把显式禁用抹平。
/// 5. 不做文档校验：解码只保证请求形状；随包文档仍走文档 Codec 与统一校验，失败不发布。
/// 6. 实现无状态、可在网络线程调用；不得读取当前 store 或客户端世界。
@NullMarked
public interface PlaybackProtocolCodec {

    /// 把一个动作编码进缓冲区。
    ///
    /// @param buffer 目标缓冲区
    /// @param action 要编码的动作
    /// @param limits 规模上限
    /// @return 成功或可报告的失败；失败时缓冲区内容不保证可回滚，调用方应丢弃该缓冲
    DataResult<Void> encode(RegistryFriendlyByteBuf buffer, PlaybackAction action, PlaybackLimits limits);

    /// 从缓冲区解码一个动作。
    ///
    /// @param buffer 源缓冲区
    /// @param limits 规模上限
    /// @return 解码成功的动作或可报告的失败
    DataResult<PlaybackAction> decode(RegistryFriendlyByteBuf buffer, PlaybackLimits limits);

    /// 适配 MC 注册点；失败以异常表达，异常消息来自 [decode] / [encode] 的错误。
    ///
    /// @param limits 规模上限；必须与接收 / 发送两侧配置一致
    /// @return 流编解码器
    default StreamCodec<RegistryFriendlyByteBuf, PlaybackAction> streamCodec(PlaybackLimits limits) {
        Objects.requireNonNull(limits, "limits");

        return new StreamCodec<>() {
            @Override
            public PlaybackAction decode(RegistryFriendlyByteBuf buffer) {
                DataResult<PlaybackAction> result = PlaybackProtocolCodec.this.decode(buffer, limits);

                if (result.result().isPresent()) {
                    return result.result().orElseThrow();
                }

                throw new DecoderException(result.error()
                        .map(DataResult.Error::message)
                        .orElse("Playback request decode failed"));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, PlaybackAction action) {
                DataResult<Void> result = PlaybackProtocolCodec.this.encode(buffer, action, limits);

                if (result.isError()) {
                    throw new EncoderException(result.error()
                            .map(DataResult.Error::message)
                            .orElse("Playback request encode failed"));
                }
            }
        };
    }
}
