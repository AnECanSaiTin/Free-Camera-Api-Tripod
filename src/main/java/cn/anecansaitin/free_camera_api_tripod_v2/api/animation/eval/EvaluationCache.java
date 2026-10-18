package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 单次求值上下文内的采样缓存（主设计 §3.4）。
///
/// 缓存以 [EvaluationKey] 为键；**失败结果也必须缓存**，不能因 NaN 哨兵重复求值。
/// 缓存有界，随上下文 / 会话关闭释放；文档结构缓存与帧求值缓存分开，不得把
/// `active(t)`、来源或姿态结果当成静态文档派生数据长期缓存。
///
/// 实现可复用缓冲区（`PoseBuffer` 一类），但不得把可变缓冲区暴露给调用方。
@NullMarked
public interface EvaluationCache {

    /// 取已缓存的采样；不存在返回空（空结果也必须显式缓存为对应状态）。
    @Nullable
    ChannelSample get(EvaluationKey key);

    /// 写入采样结果，失败结果同样写入。
    void put(EvaluationKey key, ChannelSample sample);

    /// 清空缓存；换档、候选变化或事实版本失效时调用。
    void clear();

    /// 当前条目数，用于预算与诊断。
    int size();
}
