package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// FOV 属性；默认关闭，关闭时沿用宿主基础 FOV。
@NullMarked
public record FovProperty(PropertyBinding<NumericSource> source) {

    /// 结构编码。
    public static final MapCodec<FovProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    PropertyBinding.codec(NumericSource.CODEC).fieldOf("source").forGetter(FovProperty::source)
            ).apply(instance, FovProperty::new));

    /// 独立 Codec。
    public static final Codec<FovProperty> CODEC = MAP_CODEC.codec();

    public FovProperty {
        Objects.requireNonNull(source, "source");
    }

    public static FovProperty disabled() {
        return new FovProperty(PropertyBinding.disabled());
    }
}
