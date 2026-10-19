package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/// 相机通道描述注册表（主设计 §8.1）。
///
/// 注册表是**完整**的不可变映射：每个 `CameraChannel` 都必须有描述，避免编辑器在不同机器上
/// 得到不同默认标签 / 颜色。作者覆盖的轨道名称或外观不属于本注册表，须显式存储。
@NullMarked
public record ChannelDescriptionRegistry(Map<CameraChannel, ChannelDescription> descriptions) {

    public ChannelDescriptionRegistry {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        descriptions = Map.copyOf(descriptions);

        if (descriptions.size() != CameraChannel.values().length) {
            throw new IllegalArgumentException(
                    "ChannelDescriptionRegistry must describe every CameraChannel");
        }

        for (Map.Entry<CameraChannel, ChannelDescription> entry : descriptions.entrySet()) {
            if (entry.getValue().channel() != entry.getKey()) {
                throw new IllegalArgumentException(
                        "ChannelDescriptionRegistry entry channel must match its key");
            }
        }
    }

    /// 取一个通道描述；注册表完整，因此不为空。
    public ChannelDescription description(CameraChannel channel) {
        ChannelDescription description = descriptions.get(Objects.requireNonNull(channel, "channel"));

        if (description == null) {
            throw new IllegalArgumentException("Missing channel description: " + channel);
        }

        return description;
    }

    /// 内置默认描述。
    public static ChannelDescriptionRegistry builtIn() {
        EnumMap<CameraChannel, ChannelDescription> descriptions = new EnumMap<>(CameraChannel.class);
        descriptions.put(CameraChannel.PATH_DISTANCE, new ChannelDescription(
                CameraChannel.PATH_DISTANCE, "free_camera_api_tripod:channel.path_distance",
                0xFFFFFF, "blocks or normalized percent"));
        descriptions.put(CameraChannel.POSITION_X, new ChannelDescription(
                CameraChannel.POSITION_X, "free_camera_api_tripod:channel.position_x",
                0xFF5555, "blocks"));
        descriptions.put(CameraChannel.POSITION_Y, new ChannelDescription(
                CameraChannel.POSITION_Y, "free_camera_api_tripod:channel.position_y",
                0x55FF55, "blocks"));
        descriptions.put(CameraChannel.POSITION_Z, new ChannelDescription(
                CameraChannel.POSITION_Z, "free_camera_api_tripod:channel.position_z",
                0x5555FF, "blocks"));
        descriptions.put(CameraChannel.ROTATION_X, new ChannelDescription(
                CameraChannel.ROTATION_X, "free_camera_api_tripod:channel.rotation_x",
                0xFFAA55, "degrees"));
        descriptions.put(CameraChannel.ROTATION_Y, new ChannelDescription(
                CameraChannel.ROTATION_Y, "free_camera_api_tripod:channel.rotation_y",
                0xAA55FF, "degrees"));
        descriptions.put(CameraChannel.ROTATION_Z, new ChannelDescription(
                CameraChannel.ROTATION_Z, "free_camera_api_tripod:channel.rotation_z",
                0x55FFFF, "degrees"));
        descriptions.put(CameraChannel.FOV, new ChannelDescription(
                CameraChannel.FOV, "free_camera_api_tripod:channel.fov",
                0xFFFFFF, "degrees"));

        return new ChannelDescriptionRegistry(descriptions);
    }
}
