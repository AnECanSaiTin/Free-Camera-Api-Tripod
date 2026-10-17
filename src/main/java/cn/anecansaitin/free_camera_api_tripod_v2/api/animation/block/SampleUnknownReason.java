package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;

/// 世界采样取不到数据（`UNKNOWN`）的原因（主设计 §7.7）。
///
/// ## 只服务诊断与展示
///
/// 原因**不改变兜底决策**：`sample(name)` 遇到任何 `UNKNOWN` 都使依赖通道 ERROR，
/// `sample(name, fallback)` 遇到任何 `UNKNOWN` 都显式求值兜底。原因用于诊断定位与
/// 编辑器面板展示（例如“坐标未加载”“实体不存在”“记分板目标不存在”）。
///
/// ## 为什么是封闭枚举
///
/// 采样类型由第三方注册（D16），但“取不到”的分类必须能被框架、校验器与面板统一解释；
/// 采集器专有的细节属于诊断消息，不属于读数状态。未知或未归类的情形使用
/// [DATA_UNAVAILABLE]，而不是新增隐藏分支。
///
/// ## 与采集错误的区别
///
/// 采集器缺失、抛异常、输出非法或超出预算**不是** UNKNOWN，见
/// [SampleCollectionErrorReason]：那些错误不可被 `fallback` 兜底。
@NullMarked
public enum SampleUnknownReason {

    /// 采样位置所在区块未加载。宿主只读已加载数据，绝不触发区块加载（D24）。
    CHUNK_NOT_LOADED,

    /// 所需数据尚未同步到客户端（例如记分板、实体同步状态）。
    DATA_NOT_SYNCED,

    /// 采样引用的目标不存在（实体 / 玩家 / 方块实体已消失或从未存在）。
    TARGET_NOT_FOUND,

    /// 其它“本周期取不到”的情形；具体原因由诊断消息给出。
    DATA_UNAVAILABLE
}
