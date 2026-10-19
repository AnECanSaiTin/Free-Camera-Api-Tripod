package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 已编译表达式的**不透明句柄**（主设计 §6.1、§4.2）。
///
/// ## 不是扩展接口
///
/// 设计 §6.1 明确：“编译器、语法树和解释器实现不作为稳定扩展接口公开”。
/// 因此本接口只暴露两件事：
///
/// - 原始源码（诊断与界面回显用）；
/// - 编译器登记的**外部引用**（提交期绑定、依赖图与重命名用）。
///
/// 求值不在本接口上：求值输入涉及快照、帧事实、机位上下文与曲线采样器，属于
/// `api.animation.eval` 的 `ExpressionEvaluator`。这样表达式层保持独立，
/// 不反向依赖 eval（设计 §2.1 的依赖方向）。
///
/// 实现必须深不可变：编译产物可被多线程共享，不得暴露可变语法树。
@NullMarked
public interface CompiledExpression {

    /// 编译时的源码文本（与编译缓存键一致）。
    String source();

    /// 本次编译登记的引用；按源码位置升序。
    ExpressionReferences references();
}
