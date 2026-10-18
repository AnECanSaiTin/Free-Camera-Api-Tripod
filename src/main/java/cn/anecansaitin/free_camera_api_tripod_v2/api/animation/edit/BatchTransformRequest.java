package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 对一条轨道上选定键执行批量变换的请求（主设计 §4.6）。
///
/// 请求显式绑定冻结快照；同 ID 不同文档不能复用同一请求。工具不得读取当前 store。
///
/// @param trackId   目标轨道
/// @param keyIds    选中的键；非空且不保留调用方容器
/// @param transform 时间域 / 值域变换
/// @param snapshot  冻结的输入快照
@NullMarked
public record BatchTransformRequest(
        TrackId trackId,
        List<KeyId> keyIds,
        BatchTransform transform,
        AnimationSnapshot snapshot) {

    public BatchTransformRequest {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(snapshot, "snapshot");

        keyIds = List.copyOf(keyIds);

        if (keyIds.isEmpty()) {
            throw new IllegalArgumentException("BatchTransformRequest requires at least one key");
        }
    }
}
