package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 避障属性：启用来源与保留距离来源分别绑定；默认都关闭。
@NullMarked
public record ObstacleProperty(
        PropertyBinding<BooleanSource> enabled,
        PropertyBinding<NumericSource> margin) {

    /// 结构编码。
    public static final MapCodec<ObstacleProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    PropertyBinding.codec(BooleanSource.CODEC).fieldOf("enabled")
                            .forGetter(ObstacleProperty::enabled),
                    PropertyBinding.codec(NumericSource.CODEC).fieldOf("margin")
                            .forGetter(ObstacleProperty::margin)
            ).apply(instance, ObstacleProperty::new));

    /// 独立 Codec。
    public static final Codec<ObstacleProperty> CODEC = MAP_CODEC.codec();

    public ObstacleProperty {
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(margin, "margin");
    }

    public static ObstacleProperty disabled() {
        return new ObstacleProperty(PropertyBinding.disabled(), PropertyBinding.disabled());
    }
}
