package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一台机位的全部可动画属性绑定；构造默认全部关闭。
///
/// 参考系、路径、运动/距离模式、输出开关与层级/质量属于静态配置，不放在本表。
@NullMarked
public record RigProperties(
        PositionProperty position,
        RotationProperty rotation,
        FovProperty fov,
        ActiveProperty active,
        RectProperty rect,
        ObstacleProperty obstacle) {

    /// 结构编码。
    public static final MapCodec<RigProperties> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    PositionProperty.CODEC.fieldOf("position").forGetter(RigProperties::position),
                    RotationProperty.CODEC.fieldOf("rotation").forGetter(RigProperties::rotation),
                    FovProperty.CODEC.fieldOf("fov").forGetter(RigProperties::fov),
                    ActiveProperty.CODEC.fieldOf("active").forGetter(RigProperties::active),
                    RectProperty.CODEC.fieldOf("rect").forGetter(RigProperties::rect),
                    ObstacleProperty.CODEC.fieldOf("obstacle").forGetter(RigProperties::obstacle)
            ).apply(instance, RigProperties::new));

    /// 独立 Codec。
    public static final Codec<RigProperties> CODEC = MAP_CODEC.codec();

    public RigProperties {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(fov, "fov");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(obstacle, "obstacle");
    }

    /// 全部属性关闭。
    public static RigProperties disabled() {
        return new RigProperties(
                PositionProperty.disabled(),
                RotationProperty.disabled(),
                FovProperty.disabled(),
                ActiveProperty.disabled(),
                RectProperty.fullScreen(),
                ObstacleProperty.disabled());
    }
}
