package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/// 布尔属性来源：常量或布尔状态轨道。
@NullMarked
public sealed interface BooleanSource permits BooleanSource.Constant, BooleanSource.Track {

    /// 固定布尔值。
    record Constant(boolean value) implements BooleanSource {
    }

    /// 布尔状态轨道引用。
    record Track(TrackId trackId) implements BooleanSource {
        public Track {
            java.util.Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// 结构编码。
    Codec<BooleanSource> CODEC = Codecs.variant("constant", "track").<BooleanSource>partialDispatch(
            "type",
            source -> DataResult.success(source instanceof Track ? "track" : "constant"),
            type -> switch (type) {
                case "constant" -> DataResult.<MapCodec<? extends BooleanSource>>success(
                        Codec.BOOL.fieldOf("value").xmap(Constant::new, Constant::value));
                case "track" -> DataResult.<MapCodec<? extends BooleanSource>>success(
                        TrackId.CODEC.fieldOf("track").xmap(Track::new, Track::trackId));
                default -> DataResult.error(() -> "Unknown boolean source: " + type);
            });
}
