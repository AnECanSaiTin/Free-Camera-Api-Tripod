package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import org.jspecify.annotations.NullMarked;

/// NBT 文档载荷的规模上限。
///
/// 字节上限在读取缓冲之前与写入结果之后检查；深度 / 条目上限在解码后、遍历文档前检查。
/// 上限属于宿主配置，不写进文档格式，也不改变 `data_version`。
///
/// @param maxBytes   单个 NBT 载荷的最大字节数，正数
/// @param maxDepth   Compound / List 的最大嵌套深度，`>= 1`
/// @param maxEntries 一次载荷内 Compound 键、List 元素与基本数组元素的总数上限，正数
@NullMarked
public record NbtResourceLimits(int maxBytes, int maxDepth, int maxEntries) {

    /// 默认：8 MiB、128 层、1,000,000 个条目。
    public static final NbtResourceLimits DEFAULT =
            new NbtResourceLimits(8 * 1024 * 1024, 128, 1_000_000);

    public NbtResourceLimits {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException(
                    "NbtResourceLimits maxBytes must be positive: " + maxBytes);
        }

        if (maxDepth < 1) {
            throw new IllegalArgumentException(
                    "NbtResourceLimits maxDepth must be >= 1: " + maxDepth);
        }

        if (maxEntries <= 0) {
            throw new IllegalArgumentException(
                    "NbtResourceLimits maxEntries must be positive: " + maxEntries);
        }
    }
}
