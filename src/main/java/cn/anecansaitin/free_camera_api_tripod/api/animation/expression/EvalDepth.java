package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 求值深度栈：**一层 = 一次公式求值**（变量取值、轨道上的键公式、自定义函数体都算一层）。
///
/// 计数是**线程级**的，不挂在某一次求值的会话上。原因在入口的形状：
/// `Formula.evaluate(scope)` 每次都从 `Expression.evaluate` 起一次新求值（[Evaluation]），
/// 计数若跟会话走，任何"新建一个根"都会把深度清零，上限形同虚设——第三方实现只要在自己的
/// `Resolver.resolve` 里再求值一段公式就能绕开。线程级共享之后，嵌套多少层都累加在同一格上。
///
/// 推拉只在 [Evaluation#expression] 一处，`try/finally` 配平；实现者既不需要、也拿不到这个格子。
@NullMarked
final class EvalDepth {
    /// 深度上限。一层约 5~10 个 Java 栈帧，64 层离栈容量还很远；正常数据到不了两位数
    /// （环由 `eval.ExpressionScope` 的定环检测与 `eval.EvaluationGraph` 在写入期拦掉，
    /// 这里是它们漏掉时的兜底）
    static final int MAX = 64;

    /// 本线程的深度格；同一线程上的所有 [Evaluation] 共用它
    private static final ThreadLocal<int[]> LEVEL = ThreadLocal.withInitial(() -> new int[1]);

    private EvalDepth() {
    }

    static int[] level() {
        return LEVEL.get();
    }
}
