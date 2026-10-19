package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

/// 机位 ID：文档内唯一的稳定身份（主设计 D20）。
///
/// 机位顺序由 `CameraAnimation.rigs()` 的集合顺序表达，**不由 ID 表达**；
/// 因此 ID 只用于引用与相等判定，排序、改名、撤销都保留原 ID，只有复制机位才生成新 ID。
///
/// @param value 持久化取值，非空白
@NullMarked
public record RigId(String value) implements DocumentObjectId {
    /// 结构编码：直接以字符串读写机位 ID。
    ///
    /// 空白 ID 在解码期报错，不回退也不接受空串（主设计 §8.1）。
    public static final Codec<RigId> CODEC = Codecs.nonBlankString().xmap(RigId::new, RigId::value);

    public RigId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("RigId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
