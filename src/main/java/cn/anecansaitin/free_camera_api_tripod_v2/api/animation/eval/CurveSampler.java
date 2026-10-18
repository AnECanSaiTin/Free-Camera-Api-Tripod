package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;

/// 曲线采样器：数值曲线 / 数值预览 / 姿态共用同一求值规则（主设计 §6.1、不变量 I6）。
///
/// 采样器只读取请求中显式绑定的上下文、轨道与时间，不访问世界、不读待求值相机、不产生
/// 副作用；需要环境数据时由调用方先准备事实并通过 [CurveSampleRequest#context] 传入。
/// 采样器不得从“当前活动机位”或当前 store 推断绑定。
///
/// 失败必须返回 [SampleStatus#ERROR] 与结构化诊断；空曲线返回
/// [SampleStatus#ABSENT]，不是 0。
@NullMarked
@FunctionalInterface
public interface CurveSampler {

    /// 按显式请求采样一条数值曲线。
    ///
    /// @param request 上下文、轨道、机位与采样时间
    ChannelSample sample(CurveSampleRequest request);
}
