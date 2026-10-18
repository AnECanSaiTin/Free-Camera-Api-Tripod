package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;

/// 通道采样状态（主设计 §6.1）。
///
/// | 状态 | 含义 | 处理 |
/// | --- | --- | --- |
/// | [ABSENT] | 没有编写该通道 | 使用宿主基础姿态对应分量 |
/// | [VALUE] | 得到有限合法数值 | 参与合成 |
/// | [ERROR] | 已编写但本帧求值失败 | 正式播放终止；编辑预览标错 |
///
/// 失败结果也必须缓存，不能因 NaN 哨兵而重复求值；公共结果必须携带状态与诊断定位，
/// 不允许把错误压缩成缺失位（不变量 I9）。
@NullMarked
public enum SampleStatus {

    /// 未编写该通道。
    ABSENT,

    /// 得到有限合法数值。
    VALUE,

    /// 已编写但本帧求值失败。
    ERROR
}
