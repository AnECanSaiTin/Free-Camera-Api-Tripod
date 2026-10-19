package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

/// 轨道 ID：**整份文档内**唯一的稳定身份（主设计 D20 / §3.1）。
///
/// 轨道顺序由所属机位的轨道集合顺序表达，**不由 ID 表达**；
/// 因此 ID 只用于引用与相等判定，排序、改名、撤销都保留原 ID，只有复制轨道才生成新 ID。
///
/// 主设计 §3.1 要求轨道 ID 跨机位也唯一：一个轨道 ID 就能定位轨道，
/// `TrackRef` 因此只需带一个轨道 ID，不必退化成“机位 ID + 轨道 ID”的二元键。
///
/// @param value 持久化取值，非空白
@NullMarked
public record TrackId(String value) implements DocumentObjectId {
    /// 结构编码：直接以字符串读写轨道 ID。
    ///
    /// 空白 ID 在解码期报错，保证与构造器约束一致（主设计 §8.1）。
    public static final Codec<TrackId> CODEC = Codecs.nonBlankString().xmap(TrackId::new, TrackId::value);

    public TrackId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("TrackId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
