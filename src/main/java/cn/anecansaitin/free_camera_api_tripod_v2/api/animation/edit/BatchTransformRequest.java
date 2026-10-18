package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 对一条轨道上选定键执行批量变换的请求（主设计 §4.6）。
///
/// @param trackId   目标轨道
/// @param keyIds    选中的键；非空且不保留调用方容器
/// @param transform 时间域 / 值域变换
@NullMarked
public record BatchTransformRequest(TrackId trackId, List<KeyId> keyIds, BatchTransform transform) {

    public BatchTransformRequest {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(transform, "transform");

        keyIds = List.copyOf(keyIds);

        if (keyIds.isEmpty()) {
            throw new IllegalArgumentException("BatchTransformRequest requires at least one key");
        }
    }
}
