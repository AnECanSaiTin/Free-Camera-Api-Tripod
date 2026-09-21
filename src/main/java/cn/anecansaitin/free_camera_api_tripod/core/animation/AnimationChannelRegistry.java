package cn.anecansaitin.free_camera_api_tripod.core.animation;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/// 曲线通道元数据注册表：为属性名提供显示名称、颜色、默认值与所属分组。
///
/// 相机自身的通道（位置距离、旋转、FOV）在静态块中注册；
/// 未来的后处理特效等自定义通道在各自模块初始化时注册即可。
@NullMarked
public final class AnimationChannelRegistry {
    /// @param label        显示名称
    /// @param color        时间轴/图表中的颜色（ARGB）
    /// @param defaultValue 新建通道时的默认值
    /// @param group        所属分组 id；为 null 表示在时间轴上单独成行
    public record Channel(Component label, int color, float defaultValue, @Nullable String group) {
    }

    private static final Map<String, Channel> CHANNELS = new HashMap<>();
    private static final Map<String, Component> GROUPS = new LinkedHashMap<>();

    static {
        // 旋转的三个轴在时间轴上合并成一个可折叠的分组
        registerGroup("rotation", "camera_channel.group.rotation");
        register("position", "camera_channel.position", 0xFF7CFC9A, 0f);
        register("rotation.x", "camera_channel.rotation_x", 0xFFFF6B6B, 0f, "rotation");
        register("rotation.y", "camera_channel.rotation_y", 0xFF6BFF8B, 0f, "rotation");
        register("rotation.z", "camera_channel.rotation_z", 0xFF6B9BFF, 0f, "rotation");
        register("fov", "camera_channel.fov", 0xFFFFC46B, 70f);
    }

    private AnimationChannelRegistry() {
    }

    /// 注册一个分组，供时间轴折叠显示
    public static void registerGroup(String id, String translationKey) {
        GROUPS.put(id, Component.translatable("free_camera_api_tripod." + translationKey));
    }

    public static void register(String property, String translationKey, int color, float defaultValue) {
        register(property, translationKey, color, defaultValue, null);
    }

    public static void register(String property, String translationKey, int color, float defaultValue, @Nullable String group) {
        CHANNELS.put(property, new Channel(Component.translatable("free_camera_api_tripod." + translationKey), color, defaultValue, group));
    }

    /// 通道所属的分组 id；未分组或未注册时返回 null
    public static @Nullable String group(String property) {
        Channel channel = CHANNELS.get(property);
        return channel != null ? channel.group() : null;
    }

    /// 分组显示名；未注册的分组回退为 id 本身
    public static Component groupLabel(String id) {
        Component label = GROUPS.get(id);
        return label != null ? label : Component.literal(id);
    }

    /// 未注册的通道回退为「属性名 + 由名称派生的稳定颜色」，保证第三方通道也能正常显示
    public static Channel get(String property) {
        Channel channel = CHANNELS.get(property);

        if (channel != null) {
            return channel;
        }

        return new Channel(Component.literal(property), fallbackColor(property), 0f, null);
    }

    private static int fallbackColor(String property) {
        int hash = property.hashCode();
        int r = 0x80 + (hash & 0x7F);
        int g = 0x80 + (hash >> 8 & 0x7F);
        int b = 0x80 + (hash >> 16 & 0x7F);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
