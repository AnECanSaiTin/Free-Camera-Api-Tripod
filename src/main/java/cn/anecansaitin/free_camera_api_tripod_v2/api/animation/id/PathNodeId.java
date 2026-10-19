package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

/// 路径节点 ID：所属路径内唯一的稳定身份（主设计 D20 / §3.1）。
///
/// 节点顺序由路径的节点集合顺序表达，**不由 ID 表达**；
/// 因此 ID 只用于引用与相等判定，排序、改名、撤销都保留原 ID，只有复制节点才生成新 ID。
///
/// @param value 持久化取值，非空白
@NullMarked
public record PathNodeId(String value) implements DocumentObjectId {
    /// 结构编码：直接以字符串读写路径节点 ID。
    ///
    /// 空白 ID 在解码期报错，不回退也不接受空串（主设计 §8.1）。
    public static final Codec<PathNodeId> CODEC = Codecs.nonBlankString().xmap(PathNodeId::new, PathNodeId::value);

    public PathNodeId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("PathNodeId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
