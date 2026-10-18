package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import org.jspecify.annotations.NullMarked;

/// 曲线采样器：数值曲线 / 数值预览 / 姿态共用同一求值规则（主设计 §6.1、不变量 I6）。
///
/// 采样器只读取传入的不可变曲线与显式时间，不访问世界、不读待求值相机、不产生副作用；
/// 需要环境数据时由调用方先准备事实并通过上下文传入。无环境静态读取只能明确读取常量，
/// 不得伪装成动态采样结果。
///
/// 失败必须返回 [SampleStatus#ERROR] 与结构化诊断；空曲线返回
/// [SampleStatus#ABSENT]，不是 0。
@NullMarked
@FunctionalInterface
public interface CurveSampler {

    /// 在 `time` 处采样一条数值曲线。
    ///
    /// @param curve 曲线数据
    /// @param time  秒；必须有限
    ChannelSample sample(Curve curve, float time);
}
