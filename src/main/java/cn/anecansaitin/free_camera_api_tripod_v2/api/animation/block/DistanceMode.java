package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;

/// 位置通道的取值口径（主设计 §3.1）。
///
/// 与 [MotionMode] 正交：`MotionMode` 决定位置**来自哪里**，本枚举决定路径模式下
/// “位置通道”的数值**怎么解释**。
@NullMarked
public enum DistanceMode {
    /// 绝对距离：通道取值是沿路径的弧长，单位格。
    ABSOLUTE,

    /// 百分比：通道取值是 `0~1` 的路径进度，采样时再乘以路径总长。
    PERCENT
}
