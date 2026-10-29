package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Quaternion;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 整体旋转属性的来源：四元数常量、四元数轨道，或显式 Euler 组合。
///
/// Euler 组合的三个数值来源分别产生 Yaw / Pitch / Roll；[RotationOrder] 固定合成顺序，
/// [AngleUnit] 明确输入单位。四元数仍可记录连续角度路径之外的多圈信息由数值角度来源表达，
/// 烘焙时再按“相邻转角小于 180°”分段。
@NullMarked
public sealed interface QuaternionSource
        permits QuaternionSource.Constant, QuaternionSource.Track, QuaternionSource.EulerComposite {

    /// 固定四元数。
    record Constant(Quaternion value) implements QuaternionSource {
        public Constant {
            Objects.requireNonNull(value, "value");
        }
    }

    /// 四元数轨道引用。
    record Track(TrackId trackId) implements QuaternionSource {
        public Track {
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// 三个数值来源合成的 Euler 旋转。
    record EulerComposite(
            NumericSource yaw,
            NumericSource pitch,
            NumericSource roll,
            RotationOrder order,
            AngleUnit unit) implements QuaternionSource {

        public EulerComposite {
            Objects.requireNonNull(yaw, "yaw");
            Objects.requireNonNull(pitch, "pitch");
            Objects.requireNonNull(roll, "roll");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(unit, "unit");
        }
    }

    /// Euler 合成顺序。
    enum RotationOrder {
        YXZ,
        ZYX
    }

    /// Euler 输入角度单位。
    enum AngleUnit {
        RADIANS,
        DEGREES
    }

    /// 结构编码。
    Codec<QuaternionSource> CODEC = Codecs.variant("constant", "track", "euler")
            .<QuaternionSource>partialDispatch(
                    "type",
                    source -> DataResult.success(switch (source) {
                        case Constant ignored -> "constant";
                        case Track ignored -> "track";
                        case EulerComposite ignored -> "euler";
                    }),
                    type -> switch (type) {
                        case "constant" -> DataResult.<MapCodec<? extends QuaternionSource>>success(
                                Quaternion.CODEC.fieldOf("value").xmap(Constant::new, Constant::value));
                        case "track" -> DataResult.<MapCodec<? extends QuaternionSource>>success(
                                TrackId.CODEC.fieldOf("track").xmap(Track::new, Track::trackId));
                        case "euler" -> DataResult.<MapCodec<? extends QuaternionSource>>success(
                                RecordCodecBuilder.<EulerComposite>mapCodec(instance -> instance.group(
                                        NumericSource.CODEC.fieldOf("yaw").forGetter(EulerComposite::yaw),
                                        NumericSource.CODEC.fieldOf("pitch").forGetter(EulerComposite::pitch),
                                        NumericSource.CODEC.fieldOf("roll").forGetter(EulerComposite::roll),
                                        Codecs.enumByName(RotationOrder.class).fieldOf("order")
                                                .forGetter(EulerComposite::order),
                                        Codecs.enumByName(AngleUnit.class).fieldOf("unit")
                                                .forGetter(EulerComposite::unit)
                                ).apply(instance, EulerComposite::new)));
                        default -> DataResult.error(() -> "Unknown quaternion source: " + type);
                    });
}
