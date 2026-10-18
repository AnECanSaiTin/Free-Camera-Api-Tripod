package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一个恢复槽：捕获的已发布快照 + 资源元数据（主设计 §8.4）。
///
/// 恢复槽与正式资源按资源身份区分；不保存渲染缓存、撤销栈或宿主会话状态。槽位数量、体积、
/// 保留期由实现限制，错误可诊断且不阻断编辑。
///
/// @param resource              目标资源身份
/// @param snapshot              捕获的已发布快照
/// @param capturedAtEpochMillis 捕获时间，Unix epoch 毫秒，非负
/// @param sizeBytes             槽内容字节数，非负
/// @param contentHash           槽内容哈希，非空白
@NullMarked
public record RecoverySlot(
        ResourceId resource,
        AnimationSnapshot snapshot,
        long capturedAtEpochMillis,
        long sizeBytes,
        String contentHash) {

    public RecoverySlot {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(snapshot, "snapshot");

        if (capturedAtEpochMillis < 0) {
            throw new IllegalArgumentException(
                    "RecoverySlot capturedAtEpochMillis must be non-negative: " + capturedAtEpochMillis);
        }

        if (sizeBytes < 0) {
            throw new IllegalArgumentException("RecoverySlot sizeBytes must be non-negative: " + sizeBytes);
        }

        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("RecoverySlot contentHash must not be blank");
        }
    }
}
