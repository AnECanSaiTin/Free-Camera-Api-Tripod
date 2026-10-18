package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import org.jspecify.annotations.NullMarked;

/// 显式缓动预设：写入明确的插值模式与曲柄参数（主设计 §4.6）。
///
/// 不新增隐式缓动算法；面板只选择已定义的预设。曲柄仍须满足曲线领域约束，非法值应让整个
/// 候选拒绝，而不是夹取。
///
/// @param id            预设标识，非空白
/// @param interpolation 插值模式
/// @param inSlope       入曲柄斜率，有限
/// @param outSlope      出曲柄斜率，有限
/// @param inLength      入曲柄长度，有限且非负
/// @param outLength     出曲柄长度，有限且非负
@NullMarked
public record EasingPreset(
        String id,
        Curve.Interpolation interpolation,
        float inSlope,
        float outSlope,
        float inLength,
        float outLength) {

    public EasingPreset {
        if (id.isBlank()) {
            throw new IllegalArgumentException("EasingPreset id must not be blank");
        }

        if (interpolation == null) {
            throw new IllegalArgumentException("EasingPreset interpolation must not be null");
        }

        if (!Float.isFinite(inSlope) || !Float.isFinite(outSlope)
                || !Float.isFinite(inLength) || !Float.isFinite(outLength)
                || inLength < 0f || outLength < 0f) {
            throw new IllegalArgumentException(
                    "EasingPreset handles must be finite and lengths non-negative");
        }
    }
}
