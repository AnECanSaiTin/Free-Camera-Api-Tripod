package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 数值属性的来源：常量、公式或数值轨道引用。
///
/// 它与旧 `field.NumberSource` 的区别是新增了受类型约束的 `TrackId` 引用；轨道池迁移后，
/// 数值属性可以显式绑定一条数值轨道，而不再靠机位通道隐式持有。
@NullMarked
public sealed interface NumericSource
        permits NumericSource.Constant, NumericSource.Formula, NumericSource.Track {

    /// 固定数值。
    record Constant(double value) implements NumericSource {
        public Constant {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("NumericSource.Constant must be finite: " + value);
            }
        }
    }

    /// 公式文本。
    record Formula(String expression) implements NumericSource {
        public Formula {
            Objects.requireNonNull(expression, "expression");

            if (expression.isBlank()) {
                throw new IllegalArgumentException("NumericSource.Formula must not be blank");
            }
        }
    }

    /// 数值轨道引用。
    record Track(TrackId trackId) implements NumericSource {
        public Track {
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// 结构编码：按 `type` 分派。
    Codec<NumericSource> CODEC = Codecs.variant("constant", "formula", "track")
            .<NumericSource>partialDispatch(
                    "type",
                    source -> DataResult.success(switch (source) {
                        case Constant ignored -> "constant";
                        case Formula ignored -> "formula";
                        case Track ignored -> "track";
                    }),
                    type -> switch (type) {
                        case "constant" -> DataResult.<MapCodec<? extends NumericSource>>success(
                                Codec.DOUBLE.fieldOf("value").xmap(Constant::new, Constant::value));
                        case "formula" -> DataResult.<MapCodec<? extends NumericSource>>success(
                                Codec.STRING.fieldOf("expression").xmap(Formula::new, Formula::expression));
                        case "track" -> DataResult.<MapCodec<? extends NumericSource>>success(
                                TrackId.CODEC.fieldOf("track").xmap(Track::new, Track::trackId));
                        default -> DataResult.error(() -> "Unknown numeric source: " + type);
                    });

    static NumericSource constant(double value) {
        return new Constant(value);
    }

    static NumericSource formula(String expression) {
        return new Formula(expression);
    }

    static NumericSource track(TrackId trackId) {
        return new Track(trackId);
    }
}
