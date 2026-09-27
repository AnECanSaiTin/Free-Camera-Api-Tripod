package cn.anecansaitin.free_camera_api_tripod.registry;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// 服务端 → 客户端：让目标玩家播放或停止一段相机动画。
///
/// 相机只存在于客户端，服务端能决定的只是「播哪一段」，因此这里把三种意图压进一个包：
/// - {@link Kind#LOCAL}：只带名字，客户端自己去本地文件里找（本地文件在玩家自己的游戏目录里，
///   服务端读不到——多人游戏时尤其如此）
/// - {@link Kind#STORAGE}：连 JSON 一起送来，内容取自存档数据；服务端读得到存档，客户端只管收下
/// - {@link Kind#STOP}：停止当前播放，另外两个字段为空串
///
/// 动画 JSON 自带路径数据，所以「需要路径的动画」不需要额外参数，两种情况走的是同一条路。
public record CameraPlaybackPayload(Kind kind, String name, String json) implements CustomPacketPayload {
    public enum Kind {
        /// 从玩家本地文件读
        LOCAL,
        /// 用包里带来的 JSON
        STORAGE,
        /// 停止播放
        STOP
    }

    public static final Type<CameraPlaybackPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FreeCameraApiTripod.MODID, "camera_playback"));

    /// 枚举按序数收发：包只在自己的两端之间来回，序数保持一致
    private static final StreamCodec<ByteBuf, Kind> KIND_CODEC = StreamCodec.of(
            (buf, kind) -> buf.writeByte(kind.ordinal()),
            buf -> Kind.values()[buf.readByte()]);

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraPlaybackPayload> STREAM_CODEC = StreamCodec.composite(
            KIND_CODEC, CameraPlaybackPayload::kind,
            ByteBufCodecs.STRING_UTF8, CameraPlaybackPayload::name,
            ByteBufCodecs.STRING_UTF8, CameraPlaybackPayload::json,
            CameraPlaybackPayload::new);

    /// 客户端要播的本地文件
    public static CameraPlaybackPayload local(String name) {
        return new CameraPlaybackPayload(Kind.LOCAL, name, "");
    }

    /// 客户端要播的存档动画
    public static CameraPlaybackPayload storage(String name, String json) {
        return new CameraPlaybackPayload(Kind.STORAGE, name, json);
    }

    public static CameraPlaybackPayload stop() {
        return new CameraPlaybackPayload(Kind.STOP, "", "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
