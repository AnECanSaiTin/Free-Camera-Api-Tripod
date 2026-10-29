package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 机位活动属性；默认关闭，关闭表示不读取活动来源。
@NullMarked
public record ActiveProperty(PropertyBinding<BooleanSource> source) {

    /// 结构编码。
    public static final MapCodec<ActiveProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    PropertyBinding.codec(BooleanSource.CODEC).fieldOf("source")
                            .forGetter(ActiveProperty::source)
            ).apply(instance, ActiveProperty::new));

    /// 独立 Codec。
    public static final Codec<ActiveProperty> CODEC = MAP_CODEC.codec();

    public ActiveProperty {
        Objects.requireNonNull(source, "source");
    }

    public static ActiveProperty disabled() {
        return new ActiveProperty(PropertyBinding.disabled());
    }
}
