package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/// 编辑器轨道显隐与 solo（主设计 §9.1、UI §6.3）。
///
/// 仅过滤编辑器中的轨道行、曲线和几何辅助显示；**不改变**求值、事件、声音或正式播放结果，
/// 也不得用 UI mute 赋予轨道停播语义。机位启用、音频属性和叠加可见性必须走文档事务。
///
/// @param hidden 隐藏的轨道
/// @param solo   单独显示的轨道；非空时只显示这些轨道
@NullMarked
public record TrackVisibility(Set<TrackId> hidden, Set<TrackId> solo) {

    public TrackVisibility {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        hidden = Set.copyOf(hidden);
        solo = Set.copyOf(solo);
    }

    /// 默认全部可见。
    public static TrackVisibility allVisible() {
        return new TrackVisibility(Set.of(), Set.of());
    }

    /// 该轨道在编辑器中是否显示；solo 优先于 hidden。
    public boolean isVisible(TrackId trackId) {
        return solo.isEmpty() ? !hidden.contains(trackId) : solo.contains(trackId);
    }
}
