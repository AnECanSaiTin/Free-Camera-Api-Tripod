package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 单次求值上下文内的采样缓存（主设计 §3.4）。
///
/// 缓存以 [EvaluationKey] 为键；**确定性失败结果也必须缓存**，不能因 NaN 哨兵重复求值。
/// 缓存有界，随上下文 / 会话关闭释放；文档结构缓存与帧求值缓存分开，不得把
/// `active(t)`、来源或姿态结果当成静态文档派生数据长期缓存。
///
/// ## 资源失败不得写入共享缓存
///
/// `WORK_LIMIT_EXCEEDED` / `DEPTH_LIMIT_EXCEEDED` 取决于**本次执行状态**（剩余额度、当前
/// 调用深度），不是仅由文档、事实与时间决定的结果（复核 R44-N1）。同一身份在额度充足的
/// 会话里可能成功，因此这两类失败**不得**通过本缓存共享：
///
/// - 实现可以完全不缓存它们，或只在带调用深度身份的**会话级**负缓存中短暂复用；
/// - 任何命中的缓存结果都必须按**当前入口**重新包装来源与诊断，不能继承其它调用者的
///   来源链（见 [ExpressionSource]）。
///
/// 除此之外的确定性数值 / 领域失败（含 `NON_FINITE_RESULT`、`SAMPLE_UNKNOWN` 等）
/// 仍应按同一键缓存；`INTERNAL_ERROR` 表示实现缺陷，不应被缓存掩盖。
///
/// 实现可复用缓冲区（`PoseBuffer` 一类），但不得把可变缓冲区暴露给调用方。
@NullMarked
public interface EvaluationCache {

    /// 取已缓存的采样；不存在返回空（空结果也必须显式缓存为对应状态）。
    @Nullable
    ChannelSample get(EvaluationKey key);

    /// 写入采样结果；确定性失败同样写入，但**资源失败**（工作量为 / 深度超限）不得写入
    /// 共享缓存（见类型文档 R44-N1）。
    void put(EvaluationKey key, ChannelSample sample);

    /// 清空缓存；换档、候选变化或事实版本失效时调用。
    void clear();

    /// 当前条目数，用于预算与诊断。
    int size();
}
