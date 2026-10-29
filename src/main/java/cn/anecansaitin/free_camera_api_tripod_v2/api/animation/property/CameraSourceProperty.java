package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 相机来源属性来源：常量或状态轨道。
@NullMarked
public sealed interface CameraSourceProperty
        permits CameraSourceProperty.Constant, CameraSourceProperty.Track {

    /// 固定来源。
    record Constant(CameraSource source) implements CameraSourceProperty {
        public Constant {
            Objects.requireNonNull(source, "source");
        }
    }

    /// 相机来源状态轨道引用。
    record Track(TrackId trackId) implements CameraSourceProperty {
        public Track {
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// 结构编码。
    Codec<CameraSourceProperty> CODEC = Codecs.variant("constant", "track")
            .<CameraSourceProperty>partialDispatch(
                    "type",
                    source -> DataResult.success(source instanceof Track ? "track" : "constant"),
                    type -> switch (type) {
                        case "constant" -> DataResult.<MapCodec<? extends CameraSourceProperty>>success(
                                CameraSource.CODEC.fieldOf("source")
                                        .xmap(Constant::new, Constant::source));
                        case "track" -> DataResult.<MapCodec<? extends CameraSourceProperty>>success(
                                TrackId.CODEC.fieldOf("track").xmap(Track::new, Track::trackId));
                        default -> DataResult.error(() -> "Unknown camera source property: " + type);
                    });
}
