package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

/// 关键帧 ID：所属轨道内唯一的稳定身份（主设计 D20 / §3.1）。
///
/// 键顺序由轨道内键集合的顺序（时间序）表达，**不由 ID 表达**；
/// 因此 ID 只用于引用与相等判定，排序、改名、撤销都保留原 ID，只有复制关键帧才生成新 ID。
///
/// 同一轨道内键时间唯一，但**键身份靠 KeyId，不靠时间也不靠下标**：
/// 时间会随拖动改变，下标会随插入删除改变，两者都不是稳定身份。
///
/// @param value 持久化取值，非空白
@NullMarked
public record KeyId(String value) implements DocumentObjectId {
    /// 结构编码：直接以字符串读写关键帧 ID。
    ///
    /// 空白 ID 在解码期报错，不回退也不接受空串（主设计 §8.1）。
    public static final Codec<KeyId> CODEC = Codecs.nonBlankString().xmap(KeyId::new, KeyId::value);

    public KeyId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("KeyId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
