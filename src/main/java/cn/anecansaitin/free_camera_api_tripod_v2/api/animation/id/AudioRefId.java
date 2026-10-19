package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

/// 参考音轨引用 ID：整份文档内唯一的稳定身份（主设计 D20 / §3.1）。
///
/// 引用的顺序由文档的音频引用集合顺序表达，**不由 ID 表达**；
/// 因此 ID 只用于引用与相等判定，排序、改名、撤销都保留原 ID，只有复制引用才生成新 ID。
///
/// 参考音轨的**数据**要到最终音频阶段才实现，本批只定下身份类型：
/// 引用一律用本类型表达，后续实现不得改用下标或资源路径代替 ID。
///
/// @param value 持久化取值，非空白
@NullMarked
public record AudioRefId(String value) implements DocumentObjectId {
    /// 结构编码：直接以字符串读写音频引用 ID。
    ///
    /// 空白 ID 在解码期报错，不回退也不接受空串（主设计 §8.1）。
    public static final Codec<AudioRefId> CODEC = Codecs.nonBlankString().xmap(AudioRefId::new, AudioRefId::value);

    public AudioRefId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("AudioRefId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
