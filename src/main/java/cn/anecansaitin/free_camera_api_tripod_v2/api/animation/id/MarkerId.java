package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import org.jspecify.annotations.NullMarked;

/// 文档级标记 ID：整份文档内唯一的稳定身份（主设计 D20 / §3.1）。
///
/// 标记不挂在单个机位下，顺序由文档的标记集合顺序表达，**不由 ID 表达**；
/// 因此 ID 只用于引用与相等判定，排序、改名、撤销都保留原 ID，只有复制标记才生成新 ID。
///
/// @param value 持久化取值，非空白
@NullMarked
public record MarkerId(String value) implements DocumentObjectId {
    public MarkerId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("MarkerId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
