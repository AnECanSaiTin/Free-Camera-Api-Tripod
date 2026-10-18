package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 对一条轨道上选定键应用缓动预设的请求（主设计 §4.6）。
///
/// 只描述意图；工具生成一个完整候选，任一键不存在或预设非法时整体拒绝。
///
/// @param trackId 目标轨道
/// @param keyIds  选中的键；非空且不保留调用方容器
/// @param preset  显式预设
@NullMarked
public record EasingRequest(TrackId trackId, List<KeyId> keyIds, EasingPreset preset) {

    public EasingRequest {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(preset, "preset");

        keyIds = List.copyOf(keyIds);

        if (keyIds.isEmpty()) {
            throw new IllegalArgumentException("EasingRequest requires at least one key");
        }
    }
}
