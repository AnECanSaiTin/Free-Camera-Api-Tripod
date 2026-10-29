package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.DistanceMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MotionMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 全局轨道池模型的机位：静态配置 + 有类型属性绑定。
///
/// 机位不直接持有轨道；位置、旋转、FOV、活动、矩形与避障通过 [RigProperties] 引用轨道或
/// 常量来源。新建机位默认不输出，所有可动画属性默认关闭。参考系、路径与运动模式属于静态
/// 配置，仍留在本记录上。
@NullMarked
public record PooledRig(
        RigId id,
        String name,
        Path path,
        MotionMode motionMode,
        DistanceMode distanceMode,
        Space space,
        RigOutput output,
        RigProperties properties) {

    public PooledRig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(motionMode, "motionMode");
        Objects.requireNonNull(distanceMode, "distanceMode");
        Objects.requireNonNull(space, "space");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(properties, "properties");

        if (name.isBlank()) {
            throw new IllegalArgumentException("PooledRig name must not be blank");
        }
    }

    /// 默认机位：世界空间、坐标模式、不输出、所有属性关闭。
    public static PooledRig create(RigId id, String name) {
        return new PooledRig(id, name, Path.EMPTY, MotionMode.COORDINATE,
                DistanceMode.ABSOLUTE, new Space.World(), RigOutput.HIDDEN,
                RigProperties.disabled());
    }

    /// 结构编码。
    public static MapCodec<PooledRig> mapCodec(DocumentRegistries registries) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                RigId.CODEC.fieldOf("id").forGetter(PooledRig::id),
                Codecs.nonBlankString().fieldOf("name").forGetter(PooledRig::name),
                Path.CODEC.fieldOf("path").forGetter(PooledRig::path),
                Codecs.enumByName(MotionMode.class).fieldOf("motion_mode").forGetter(PooledRig::motionMode),
                Codecs.enumByName(DistanceMode.class).fieldOf("distance_mode").forGetter(PooledRig::distanceMode),
                Space.codec(registries).fieldOf("space").forGetter(PooledRig::space),
                RigOutput.CODEC.fieldOf("output").forGetter(PooledRig::output),
                RigProperties.CODEC.fieldOf("properties").forGetter(PooledRig::properties)
        ).apply(instance, PooledRig::new));
    }

    /// 独立 Codec。
    public static Codec<PooledRig> codec(DocumentRegistries registries) {
        return mapCodec(registries).codec();
    }

    public PooledRig withName(String newName) {
        return new PooledRig(id, newName, path, motionMode, distanceMode, space, output, properties);
    }

    public PooledRig withOutput(RigOutput newOutput) {
        return new PooledRig(id, name, path, motionMode, distanceMode, space, newOutput, properties);
    }

    public PooledRig withProperties(RigProperties newProperties) {
        return new PooledRig(id, name, path, motionMode, distanceMode, space, output, newProperties);
    }
}
