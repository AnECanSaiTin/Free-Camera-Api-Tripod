package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;

/// 位置通道的来源（主设计 §3.1、§6.3）。
///
/// 与 `Space` 正交：`MotionMode` 决定位置**来自路径还是坐标通道**，
/// `Space` 决定参考系怎么解释。
@NullMarked
public enum MotionMode {
    /// 路径模式：位置 = 绑定路径在“位置通道”处的取值，口径见 [DistanceMode]。
    PATH,

    /// 直接坐标模式：位置 = `position.x` / `position.y` / `position.z` 三个通道的取值。
    COORDINATE
}
