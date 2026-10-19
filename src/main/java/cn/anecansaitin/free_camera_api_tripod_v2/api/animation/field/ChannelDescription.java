package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 相机通道的显示描述：标签、颜色与默认单位 / 说明（主设计 §8.1）。
///
/// 显示信息由通道描述注册表派生；用户可编辑的轨道名称或外观覆盖不写回本类型。标签使用
/// 翻译键而不是预先渲染的文本，避免把本地化状态写进契约。
///
/// @param channel    语义通道
/// @param labelKey   翻译键，非空白
/// @param color      RGB 颜色，`0x000000`–`0xFFFFFF`
/// @param defaultInfo 默认单位 / 说明，非空白
@NullMarked
public record ChannelDescription(
        CameraChannel channel,
        String labelKey,
        int color,
        String defaultInfo) {

    public ChannelDescription {
        Objects.requireNonNull(channel, "channel");

        if (labelKey.isBlank()) {
            throw new IllegalArgumentException("ChannelDescription labelKey must not be blank");
        }

        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException(
                    "ChannelDescription color must be in 0x000000..0xFFFFFF: " + color);
        }

        if (defaultInfo.isBlank()) {
            throw new IllegalArgumentException("ChannelDescription defaultInfo must not be blank");
        }
    }
}
