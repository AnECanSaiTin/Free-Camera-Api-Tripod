package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import org.jspecify.annotations.NullMarked;

/// 一个关键帧：时间 + 取值来源 + 插值参数。
///
/// ## 时间
///
/// 时间**始终以秒存储**，不做隐式量化（主设计 §4.4）：秒、刻、帧、时间码只是显示与输入换算，
/// 切换显示单位不得重写键时间。时间必须有限、非负，且同一轨道内唯一；
/// `-0.0` 在构造时统一为 `0.0`。
///
/// ## 取值
///
/// `ValueSource` 是固定值或公式。**公式失败不是“取固定值”**：求值层返回 `ERROR`，
/// 正式播放终止，编辑预览标错（主设计 §6.1、§7.3）。这里不做任何兜底。
///
/// ## 曲柄
///
/// `inSlope` / `outSlope` 是入/出侧斜率，`inLength` / `outLength` 是曲柄长度倍数。
/// 两侧曲柄长度之和不超过整段时长时横坐标单调，反解才有唯一解，因此取用时统一夹到
/// `[0, 1.5]`（算法在采样器里，本类型不做夹取——校验器负责报告越界，见主设计 L4）。
///
/// `Interpolation.STEP` 忽略曲柄；`LINEAR` 只用取值；`HERMITE` 使用全部四个参数。
///
/// @param keyId        稳定身份。移动、重排、改值都保留它；复制时由 `IdGenerator` 生成新的
/// @param time         秒，有限且非负
/// @param value        取值来源，非空
/// @param inSlope      入侧斜率
/// @param outSlope     出侧斜率
/// @param inLength     入侧曲柄长度倍数
/// @param outLength    出侧曲柄长度倍数
/// @param interpolation 插值模式
@NullMarked
public record Keyframe(
        KeyId keyId,
        float time,
        ValueSource value,
        float inSlope,
        float outSlope,
        float inLength,
        float outLength,
        Curve.Interpolation interpolation) {

    public Keyframe {
        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("Keyframe time must be finite and non-negative: " + time);
        }

        // -0.0 规范化；其余有效时间不变
        if (time == 0f) {
            time = 0f;
        }
    }

    /// 只要固定数值的键。公式键会抛异常——调用方若要“只读常量”，应当先判断来源类型。
    public float constantValue() {
        return ((Constant) value).value();
    }

    /// 该键的取值是否为公式。求值层因此不必 `instanceof`。
    public boolean isFormula() {
        return value instanceof Formula;
    }

    /// 改时间，其余不变。返回派生对象（不修改自身）。
    public Keyframe withTime(float newTime) {
        return new Keyframe(keyId, newTime, value, inSlope, outSlope, inLength, outLength, interpolation);
    }

    /// 改取值来源，其余不变。
    public Keyframe withValue(ValueSource newValue) {
        return new Keyframe(keyId, time, newValue, inSlope, outSlope, inLength, outLength, interpolation);
    }

    /// 改插值参数，其余不变。
    public Keyframe withHandles(float inSlope, float outSlope, float inLength, float outLength,
                                Curve.Interpolation newInterpolation) {
        return new Keyframe(keyId, time, value, inSlope, outSlope, inLength, outLength, newInterpolation);
    }

    /// 改身份，其余不变。只在复制对象时由 `IdGenerator` 配套使用。
    public Keyframe withId(KeyId newKeyId) {
        return new Keyframe(newKeyId, time, value, inSlope, outSlope, inLength, outLength, interpolation);
    }
}
