package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

/// 批量变换参数：时间偏移、值域缩放 / 偏移与取反（主设计 §4.6）。
///
/// 时间域与值域分开表达；公式不能被静默改成 literal，工具必须显式构造等价操作或拒绝。
///
/// @param timeOffsetSeconds 时间偏移，秒；有限
/// @param valueScale       值域缩放，有限
/// @param valueOffset      值域偏移，有限
/// @param negateValues     是否对值取反
@NullMarked
public record BatchTransform(
        float timeOffsetSeconds,
        float valueScale,
        float valueOffset,
        boolean negateValues) {

    public BatchTransform {
        if (!Float.isFinite(timeOffsetSeconds)
                || !Float.isFinite(valueScale)
                || !Float.isFinite(valueOffset)) {
            throw new IllegalArgumentException("BatchTransform parameters must be finite");
        }
    }
}
