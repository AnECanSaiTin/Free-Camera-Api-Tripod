package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一个动画资源的元数据条目（主设计 §8.2、§8.4）。
///
/// 存储只报告资源身份与字节级信息，不解释文档子对象字段；内容哈希用于分享 / 恢复槽识别，
/// 不替代文档校验。
///
/// @param resource           资源身份
/// @param sizeBytes          资源字节数，非负
/// @param contentHash        内容哈希，非空白；算法由 storage 实现定义
/// @param modifiedAtEpochMillis 最近修改时间，Unix epoch 毫秒，非负
@NullMarked
public record AnimationResourceEntry(
        ResourceId resource,
        long sizeBytes,
        String contentHash,
        long modifiedAtEpochMillis) {

    public AnimationResourceEntry {
        Objects.requireNonNull(resource, "resource");

        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "AnimationResourceEntry sizeBytes must be non-negative: " + sizeBytes);
        }

        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("AnimationResourceEntry contentHash must not be blank");
        }

        if (modifiedAtEpochMillis < 0) {
            throw new IllegalArgumentException(
                    "AnimationResourceEntry modifiedAtEpochMillis must be non-negative: "
                            + modifiedAtEpochMillis);
        }
    }
}
