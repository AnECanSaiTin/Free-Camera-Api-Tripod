package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;

/// 注视姿态捕获的时间选择模式（主设计 §4.7）。
///
/// t 已有 Off 时默认不可捕获；用户可显式选择“紧邻 t 左侧区间”的 Target 配置，但不能向更早
/// 区间追溯。
@NullMarked
public enum AimCaptureMode {

    /// 使用 t 当前生效的注视配置；t 为 Off 时不可捕获。
    CURRENT,

    /// 仅当 t 已有 Off 键时，使用紧邻左侧区间的 Target 配置；左侧仍为 Off 时不可捕获。
    LEFT_TARGET
}
