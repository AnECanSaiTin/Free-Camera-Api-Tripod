package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import org.jspecify.annotations.NullMarked;

/// 存储资源身份：动画文件、恢复槽资源等的稳定、非空白引用（主设计 §8.2）。
///
/// 不使用绝对文件系统路径作为共享身份：资源服务负责把资源根与相对引用组合成本地路径，
/// 诊断只携带本类型。具体资源根、大小限制与解析策略由 storage 契约 / 实现批次补齐。
///
/// @param value 相对资源标识，非空白
@NullMarked
public record ResourceId(String value) {

    public ResourceId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("ResourceId must not be blank");
        }
    }
}
