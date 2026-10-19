package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 有类型轨道的一个关键帧：稳定身份 + 时间 + 原生值（主设计 §3.1.1、D28）。
///
/// ## 与数值 `Keyframe` 的分工
///
/// 数值曲线（`field.Curve`）的键是 `field.Keyframe`：它承载 `ValueSource`（常量或公式）、
/// 四个曲柄参数与插值模式，因为数值需要插值。状态值不需要也不允许插值：
///
/// | 键类型 | 值形态 | 插值 |
/// | --- | --- | --- |
/// | `field.Keyframe` | `ValueSource`（数值） | STEP / LINEAR / HERMITE |
/// | 本类型 | 原生 Java 值（`AimState`、`Boolean`、`CameraSource`） | 仅 STEP |
///
/// 主设计 §3.1.1 明确“不要求所有轨道采用同一个 Java 数值 Keyframe record”：
/// 把目标 / 布尔 / 来源编码成浮点数会丢掉类型，把“未编写”和“0”混在一起，
/// 也会让作者误以为这些控制状态可以被公式或 `TrackRef` 读取。
///
/// ## 身份与时间口径
///
/// - `keyId` 与数值键**共用** `KeyId`：移动、排序、改值、撤销都保留它，
///   复制键时由 `IdGenerator` 生成新值（主设计 §3.1、§3.1.1）。
/// - 时间以秒存储、不做量化（主设计 §4.4）；必须有限、非负；`-0.0` 统一为 `0.0`。
///   同一轨道内时间唯一且严格递增，由轨道构造器校验；本类型只保证自身时间合法。
///
/// ## 值
///
/// 值必须非空。值**自身**的语义合法性（例如注视掩码是否允许 Roll）属于轨道类型与
/// 文档校验（L3），不在本类型里提前拒绝：构造候选时允许出现暂时不完整的值，
/// 由统一校验给出结构化诊断（主设计 §4.2）。
///
/// @param keyId 稳定身份
/// @param time  秒，有限且非负
/// @param value 原生值，非空
/// @param <T>   轨道值类型
@NullMarked
public record ValueKeyframe<T>(KeyId keyId, float time, T value) {

    /// 按值类型构造结构编码：`{key_id, time, value}`。
    ///
    /// 时间用秒并做有限、非负校验；`value` 由调用方给出的值 Codec 决定。
    public static <T> MapCodec<ValueKeyframe<T>> mapCodec(Codec<T> valueCodec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                KeyId.CODEC.fieldOf("key_id").forGetter(ValueKeyframe::keyId),
                Codecs.seconds().fieldOf("time").forGetter(ValueKeyframe::time),
                valueCodec.fieldOf("value").forGetter(ValueKeyframe::value)
        ).apply(instance, ValueKeyframe::new));
    }

    /// 按值类型构造独立 Codec；形状与 [mapCodec] 相同。
    public static <T> Codec<ValueKeyframe<T>> codec(Codec<T> valueCodec) {
        return mapCodec(valueCodec).codec();
    }

    public ValueKeyframe {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(value, "value");

        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("ValueKeyframe time must be finite and non-negative: " + time);
        }

        // -0.0 规范化；其余有效时间不变（主设计 §4.4）
        if (time == 0f) {
            time = 0f;
        }
    }

    /// 便捷构造。
    public static <T> ValueKeyframe<T> at(KeyId keyId, float time, T value) {
        return new ValueKeyframe<>(keyId, time, value);
    }

    /// 改时间，其余不变。返回派生对象（不修改自身）。
    public ValueKeyframe<T> withTime(float newTime) {
        return new ValueKeyframe<>(keyId, newTime, value);
    }

    /// 改值，其余不变。
    public ValueKeyframe<T> withValue(T newValue) {
        return new ValueKeyframe<>(keyId, time, newValue);
    }

    /// 改身份，其余不变。只在**复制键**时由 `IdGenerator` 配套使用。
    public ValueKeyframe<T> withId(KeyId newKeyId) {
        return new ValueKeyframe<>(newKeyId, time, value);
    }
}
