package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/// 轨道类型描述。编辑器据此展示类型名、颜色，并在“新建轨道”时创建实例。
///
/// @param id      类型 id
/// @param label   类型显示名
/// @param color   类型默认颜色（ARGB）
/// @param factory 创建工厂；为 null 表示该类型暂时只作为占位（例如未来的事件/特效轨道）
public record TrackType(Identifier id, Component label, int color, @Nullable TrackFactory factory) {
    @FunctionalInterface
    public interface TrackFactory {
        /// @param id 新轨道的唯一标识
        AnimationTrack create(String id);
    }
}
