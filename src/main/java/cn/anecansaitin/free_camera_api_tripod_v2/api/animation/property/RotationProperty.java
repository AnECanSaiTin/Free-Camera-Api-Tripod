package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 机位整体旋转属性；默认关闭，关闭时不接管基础旋转。
@NullMarked
public record RotationProperty(PropertyBinding<QuaternionSource> source) {

    /// 结构编码。
    public static final MapCodec<RotationProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    PropertyBinding.codec(QuaternionSource.CODEC).fieldOf("source")
                            .forGetter(RotationProperty::source)
            ).apply(instance, RotationProperty::new));

    /// 独立 Codec。
    public static final Codec<RotationProperty> CODEC = MAP_CODEC.codec();

    public RotationProperty {
        Objects.requireNonNull(source, "source");
    }

    public static RotationProperty disabled() {
        return new RotationProperty(PropertyBinding.disabled());
    }
}
