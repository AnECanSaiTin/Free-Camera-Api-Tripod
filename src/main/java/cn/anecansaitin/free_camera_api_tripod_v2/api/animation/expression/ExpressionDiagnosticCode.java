package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 表达式诊断码（主设计 §4.2、§6.1、§7.7）。
///
/// ## 为什么在表达式包内自定义
///
/// `api.animation.expression` 必须保持独立：设计 §2.1 只允许它依赖 JDK 与必要的序列化库。
/// `api.animation.edit` 的 `Diagnostic` 引用文档作用域与本地化消息，会把表达式层拖进上层依赖。
/// 因此表达式层使用**自己的**诊断码与轻量诊断；文档校验器再把它们转换成 `edit.Diagnostic`
/// （补上作用域、对象定位与本地化消息）。
///
/// ## 编译期与运行期
///
/// 同一个枚举既服务编译（源码形状问题）也服务求值（运行期失败），但**用途不同**：
/// 编译期发现的问题应在提交时拒绝；运行期错误按主设计 §6.1 / D18 终止整场，
/// 只有 `sample(name, fallback)` 能显式处理 `SAMPLE_UNKNOWN`。
@NullMarked
public enum ExpressionDiagnosticCode {

    // region 编译期

    /// 语法错误（无法解析源码）。
    SYNTAX_ERROR,

    /// 源码超过编译预算（长度或节点数量上限）。
    SOURCE_TOO_LARGE,

    /// 语法嵌套超过编译期上限。
    NESTING_TOO_DEEP,

    /// 内建函数的实参个数不符。自定义函数的实参个数由提交期符号表校验。
    BUILTIN_ARITY_MISMATCH,

    /// `sample` 的名字不是静态字符串字面量，或参数个数不是 1 或 2。
    SAMPLE_REFERENCE_INVALID,

    // endregion

    // region 运行期

    /// 名字在运行期仍无法解析（正常应在提交期被拒绝；此处兜底并给出可诊断错误）。
    UNKNOWN_NAME,

    /// 除零或其它使结果非有限（NaN / Infinity）的运算。
    NON_FINITE_RESULT,

    /// 运行期嵌套深度超过共享执行会话的 `EvaluationSession.maxDepth()`。
    DEPTH_LIMIT_EXCEEDED,

    /// 运行期工作量超过本帧共享执行会话的剩余额度（`EvaluationSession.tryConsumeWork`）。
    WORK_LIMIT_EXCEEDED,

    /// 公式读取当前机位空间事实，但求值输入没有机位上下文。
    SPATIAL_CONTEXT_MISSING,

    /// 读取的样本本帧为 `UNKNOWN`，且没有 `sample(name, fallback)` 的显式兜底。
    SAMPLE_UNKNOWN,

    /// 采样采集错误（采集器缺失、异常、非法输出或预算违规），**不可**用 fallback 兜底。
    SAMPLE_COLLECTION_ERROR,

    /// `sample` 引用的名字没有绑定到文档采样声明。
    UNKNOWN_SAMPLE_NAME,

    /// 实现内部错误（不应出现；出现时必须显式报告，不能返回数值）。
    INTERNAL_ERROR;

    // endregion

    /// 结果是否取决于**本次执行状态**（剩余额度 / 当前调用深度），而不是文档、事实与时间。
    ///
    /// 这类失败不得进入与会话无关的共享结果缓存：同一身份在额度充足的会话里可能成功
    /// （复核 R44-N1）。
    public boolean executionStateDependent() {
        return this == WORK_LIMIT_EXCEEDED || this == DEPTH_LIMIT_EXCEEDED;
    }

    /// 是否允许写入上下文共享的结果缓存。
    ///
    /// `INTERNAL_ERROR` 表示实现缺陷，缓存会掩盖问题；资源失败见
    /// [executionStateDependent()]。其余确定性数值 / 领域失败应缓存。
    public boolean cacheableInSharedCache() {
        return !executionStateDependent() && this != INTERNAL_ERROR;
    }
}
