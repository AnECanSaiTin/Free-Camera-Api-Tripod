package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;

/// 一帧内共享的求值执行会话：工作量额度与调用深度（主设计 §6.1、D18）。
///
/// ## 为什么需要会话对象
///
/// 只把“剩余额度”放进不可变输入是不够的：一次公式求值可能经 `TrackRef` 进入曲线公式、
/// 再进入变量或函数体，形成**跨服务调用链**。外层消耗必须能被后续调用看到，否则同帧的
/// 多个根公式无法共享一份额度，嵌套调用也无法继承深度。
///
/// 因此本类型是**显式、可变、按帧创建**的执行会话：
///
/// - 由宿主在帧准备阶段创建一个，交给 `ExpressionEvaluator` 与 `CurveSampler` 共同使用；
/// - 不依赖全局变量、线程局部或采样器隐式状态；
/// - **不是**文档数据，不进入历史、缓存键或网络；换帧必须新建。
///
/// ## 线程与生命周期
///
/// 会话**不是线程安全的**：一次帧求值在宿主线程上串行使用。不得跨帧复用，也不得把同一个
/// 会话交给并发的预览 / 正式播放。
///
/// ## 记账规则（契约）
///
/// 1. 额度是**帧级总量**；每次真正执行的求值按实现估算的工作量调用 [tryConsumeWork]。
/// 2. **缓存命中计费为 0**：从缓存取回的结果不重复消耗额度；失败结果缓存命中同样为 0。
/// 3. **失败仍计费**：已执行到失败前的工作量不得退回（避免用失败无限重试）。
/// 4. 额度不足时返回 `WORK_LIMIT_EXCEEDED` 失败；[tryConsumeWork] 在不足时**不部分扣减**。
/// 5. 深度按“进入求值 / 离开求值”配对记账，实现必须用 `try/finally` 保证成对；
///    超过 [maxDepth] 返回 `DEPTH_LIMIT_EXCEEDED` 失败。
/// 6. **资源失败不进入共享结果缓存**：`WORK_LIMIT_EXCEEDED` / `DEPTH_LIMIT_EXCEEDED`
///    取决于本次会话的剩余额度与当前深度，同一身份在额度充足的会话里可能成功。
///    这类失败不得通过 `EvaluationCache` 之类的会话无关缓存复用；允许用新额度重试。
///    确定性数值 / 领域失败仍按同一身份缓存（复核 R44-N1）。
@NullMarked
public final class EvaluationSession {

    private final long workQuota;
    private final int maxDepth;

    private long consumedWork;
    private int depth;

    private EvaluationSession(long workQuota, int maxDepth) {
        this.workQuota = workQuota;
        this.maxDepth = maxDepth;
    }

    /// 创建一帧的执行会话。
    ///
    /// @param workQuota 本帧工作量总额度，非负；`0` 表示任何实际求值都会超限（用于严格模式）
    /// @param maxDepth  允许的最大嵌套深度，`>= 1`
    public static EvaluationSession create(long workQuota, int maxDepth) {
        if (workQuota < 0) {
            throw new IllegalArgumentException(
                    "EvaluationSession workQuota must be non-negative: " + workQuota);
        }

        if (maxDepth < 1) {
            throw new IllegalArgumentException("EvaluationSession maxDepth must be >= 1: " + maxDepth);
        }

        return new EvaluationSession(workQuota, maxDepth);
    }

    /// 本帧工作量总额度。
    public long workQuota() {
        return workQuota;
    }

    /// 已消耗的工作量。
    public long consumedWork() {
        return consumedWork;
    }

    /// 剩余工作量，非负。
    public long remainingWork() {
        return workQuota - consumedWork;
    }

    /// 额度是否已耗尽。
    public boolean exhausted() {
        return consumedWork >= workQuota;
    }

    /// 尝试消耗工作量；额度不足时返回 `false` 且**不扣减**。
    ///
    /// @param units 本次实际执行消耗的工作量，非负
    /// @return 是否成功扣减
    public boolean tryConsumeWork(int units) {
        if (units < 0) {
            throw new IllegalArgumentException("EvaluationSession units must be non-negative: " + units);
        }

        if (units > remainingWork()) {
            return false;
        }

        consumedWork += units;
        return true;
    }

    /// 允许的最大嵌套深度。
    public int maxDepth() {
        return maxDepth;
    }

    /// 当前嵌套深度；根调用为 `0`。
    public int currentDepth() {
        return depth;
    }

    /// 剩余可进入的深度。
    public int remainingDepth() {
        return maxDepth - depth;
    }

    /// 尝试进入下一层求值；已到上限时返回 `false` 且不改变深度。
    public boolean tryEnterDepth() {
        if (depth >= maxDepth) {
            return false;
        }

        depth++;
        return true;
    }

    /// 离开一层求值；调用方必须与 [tryEnterDepth] 成对使用。
    public void exitDepth() {
        if (depth <= 0) {
            throw new IllegalStateException("EvaluationSession.exitDepth without matching enterDepth");
        }

        depth--;
    }
}
