package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 机位位置三轴的显式绑定。
///
/// 三轴各自可独立关闭；未接管轴沿用宿主基础姿态。位置不是单一向量来源，因为作者经常只
/// 接管其中一轴，且每轴可以绑定不同轨道。
@NullMarked
public record PositionProperty(
        PropertyBinding<NumericSource> x,
        PropertyBinding<NumericSource> y,
        PropertyBinding<NumericSource> z) {

    /// 结构编码。
    public static final MapCodec<PositionProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    PropertyBinding.codec(NumericSource.CODEC).fieldOf("x").forGetter(PositionProperty::x),
                    PropertyBinding.codec(NumericSource.CODEC).fieldOf("y").forGetter(PositionProperty::y),
                    PropertyBinding.codec(NumericSource.CODEC).fieldOf("z").forGetter(PositionProperty::z)
            ).apply(instance, PositionProperty::new));

    /// 独立 Codec。
    public static final Codec<PositionProperty> CODEC = MAP_CODEC.codec();

    public PositionProperty {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(z, "z");
    }

    /// 三轴全部关闭。
    public static PositionProperty disabled() {
        return new PositionProperty(
                PropertyBinding.disabled(),
                PropertyBinding.disabled(),
                PropertyBinding.disabled());
    }
}
