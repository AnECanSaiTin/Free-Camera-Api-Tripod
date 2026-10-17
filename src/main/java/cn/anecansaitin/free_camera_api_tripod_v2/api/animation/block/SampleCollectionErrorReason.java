package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;

/// 世界采样**采集错误**的原因：不可被显式兜底掩盖（主设计 §7.7、D18）。
///
/// ## 与 UNKNOWN 的分界
///
/// | 情形 | 状态 | 能否 `sample(name, fallback)` |
/// | --- | --- | --- |
/// | 区块未加载 / 数据未同步 / 目标不存在 | `SampleReading.Unknown` | 能，且只在 UNKNOWN 时惰性求值兜底 |
/// | 采集器缺失、抛异常、输出非法、超出预算 | `SampleReading.CollectionError` | 不能；正式播放终止并指名样本 |
///
/// 主设计 §7.7 的口径是“采集器异常、非法输出和预算违规为独立采集错误，不能标成
/// 可兜底的 UNKNOWN”：否则一个坏采集器会被作者的兜底值悄悄掩盖，运行期错误就被
/// 伪装成了合法数值（不变量 I9）。
///
/// ## 何时报告
///
/// - 类型已解码但客户端没有注册采集器 → [COLLECTOR_MISSING]（运行能力缺失，
///   不是文档损坏；文档仍可打开与编辑，只是不能完整播放）。
/// - 采集器抛出异常 → [COLLECTOR_THREW]；宿主记录诊断并终止整场，不吞掉异常。
/// - 采集器返回 NaN / Infinity 或类型不符 → [ILLEGAL_OUTPUT]。
/// - 单个周期的采集工作量超出宿主预算 → [BUDGET_EXCEEDED]。
///
/// 这些错误都不能被算作“世界暂时没准备好”，因此不给作者提供隐式出口。
@NullMarked
public enum SampleCollectionErrorReason {

    /// 类型已在 common 侧注册，但客户端没有对应采集器（运行能力缺失）。
    COLLECTOR_MISSING,

    /// 采集器抛出异常。
    COLLECTOR_THREW,

    /// 采集器输出非法：非有限数值、缺字段或类型不符。
    ILLEGAL_OUTPUT,

    /// 本周期采集工作量超出宿主预算。
    BUDGET_EXCEEDED
}
