package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import org.jspecify.annotations.NullMarked;

/// 公式求值服务（主设计 §6.1、§7.7）。
///
/// 求值入口在 `eval` 而不是 `expression`：输入涉及快照、帧事实、机位上下文与曲线采样器，
/// 表达式包必须保持独立（设计 §2.1 的依赖方向）。
///
/// ## 语义要求
///
/// 1. **纯求值**：只读 [ExpressionInputs]；不访问世界、不读当前 store、不产生副作用。
///    同一输入必须得到同一结果（`random()` 除外，它使用输入里的种子并明确非确定）。
/// 2. **失败可诊断且可定位**：任何失败返回 [ExpressionSample.Failure]，其 [ExpressionFailure]
///    携带**源码身份 + 本层区间 + 嵌套原因链**；不得返回 `NaN` / `Infinity`，也不得静默替换
///    数值（D18、I9）。嵌套失败的区间属于各自源码（函数体 / 变量 / 被引用轨道公式），
///    不能一律映射回入口表达式。
/// 3. **样本语义**：`sample(name)` 读到 `UNKNOWN` 时返回 `SAMPLE_UNKNOWN` 失败；
///    `sample(name, fallback)` **仅在 UNKNOWN 时**惰性求值 fallback。
///    采集错误（`COLLECTION_ERROR`）不可被 fallback 兜底；未知样本名返回 `UNKNOWN_SAMPLE_NAME`。
/// 4. **惰性控制流**：`select(cond, a, b)` 只求值选中分支；静态校验仍检查两个分支的引用。
/// 5. **预算与深度**：使用 [ExpressionInputs#session] 的共享会话记账。
///    超过 [EvaluationSession#maxDepth] 返回 `DEPTH_LIMIT_EXCEEDED`；
///    工作量不足返回 `WORK_LIMIT_EXCEEDED`。进入/离开深度必须用 `try` / `finally` 配对；
///    经 `TrackRef` 进入曲线公式时传入同一会话，不得新建额度。缓存命中不消耗额度，
///    失败前已消耗的工作量不退回（见 [EvaluationSession] 的记账规则）。
/// 6. **空间上下文**：读取空间事实但没有 [ExpressionInputs#rigId] 时返回
///    `SPATIAL_CONTEXT_MISSING`；`TrackRef` 使用被引用轨道所属机位，不使用该字段。
/// 7. **不确定性**：`random()` 明确非确定，预览与多次播放不保证一致；
///    确定性替代是 `noise(seed, t)`（版本化）。试算遇到非确定公式应报告 `Incomplete`，
///    不能以一次随机结果作为拒绝依据（主设计 §4.3）。
///
/// 实现可以把求值结果缓存在宿主侧（`EvaluationCache`），但缓存身份必须包含上下文、
/// 机位、事实版本、**入口源码位置**与有效 `AimState`（主设计 §3.4），不能只比较表达式文本；
/// 缓存键**不包含** [EvaluationSession]（会话是预算状态，不是结果身份）。
///
/// 缓存只保存**确定性**结果：`WORK_LIMIT_EXCEEDED` / `DEPTH_LIMIT_EXCEEDED` 由本次执行
/// 状态决定，不得写入共享缓存，也不得据此认定同一表达式在额度充足的会话中必然失败
/// （复核 R44-N1）。从缓存取回的失败必须按**当前** [ExpressionInputs#source] 重新包装，
/// 不能把其它入口的来源链接过来（复核 R44-N2）。
@NullMarked
@FunctionalInterface
public interface ExpressionEvaluator {

    /// 用显式输入求值一个已编译表达式。
    ///
    /// @param expression 已编译句柄；编译失败的源码不会到达这里
    /// @param inputs     显式求值输入
    /// @return 有限数值或结构化失败
    ExpressionSample evaluate(CompiledExpression expression, ExpressionInputs inputs);
}
