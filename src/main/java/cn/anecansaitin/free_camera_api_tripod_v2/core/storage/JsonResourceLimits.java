package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import org.jspecify.annotations.NullMarked;

/// 本地 JSON 资源的规模上限。
///
/// 这些上限在读取文件内容之前、解析 JSON 之前与编码写入之前检查，避免把“限制”做成
/// 先完整分配再拒绝。上限属于宿主配置，不写进文档格式。
///
/// @param maxFileBytes 单个资源文件 / 编码结果的最大字节数，正数
/// @param maxJsonDepth JSON 容器最大嵌套深度，`>= 1`
@NullMarked
public record JsonResourceLimits(int maxFileBytes, int maxJsonDepth) {

    /// 默认：8 MiB、128 层嵌套。
    public static final JsonResourceLimits DEFAULT = new JsonResourceLimits(8 * 1024 * 1024, 128);

    public JsonResourceLimits {
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException(
                    "JsonResourceLimits maxFileBytes must be positive: " + maxFileBytes);
        }

        if (maxJsonDepth < 1) {
            throw new IllegalArgumentException(
                    "JsonResourceLimits maxJsonDepth must be >= 1: " + maxJsonDepth);
        }
    }
}
