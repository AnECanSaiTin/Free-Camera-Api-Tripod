package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 求值环境：**这一帧是什么时候 + 名字怎么解析 + 轨道读到多少**。
///
/// 求值链只认这个接口，不认 `CameraAnimation`——单条曲线、单个值都能脱离动画求值，好测试也好复用。
/// 唯一实现在 `eval` 包里（`ExpressionScope`），从动画建一份走 `eval.CurveSampler#scope`。
///
/// 名字解析**不在这里做**：公式里的一个名字该取什么值（内置量 `t` / `p` / `wt`、用户变量、
/// 自定义函数）是 [Resolver] 的事，由 [#resolver] 给出。两者分开是因为用途不同——
/// 环境是"这一帧是什么时候"，解析是"公式里的名字是什么"；只画一条曲线的人只需要 [TrackLookup]。
@NullMarked
public interface Scope {
    /// 当前求值时间
    float time();

    /// 播放进度：当前时间占动画总时长的比例，归一化到 0~1
    float progress();

    /// 世界时间：游戏内一天的进度，归一化到 0~1
    float worldTime();

    /// 版本号：**每换一帧就 +1**。缓存按它失效，而不是按作用域的对象身份判断。
    ///
    /// 作用域是**可复用**的——播放器与编辑器各持有一份，每帧改时间与版本号接着用，
    /// 不必每帧新建（省下六个集合的分配）。代价是"换了个作用域对象"不再等价于"换了一帧"，
    /// 因此缓存必须认这个版本号
    long version();

    /// 名字解析器：内置量、用户变量、自定义函数，以及轨道读数都在这里查
    Resolver resolver();

    /// 当前正在求值的变量名；不在变量求值过程中时返回 null。
    ///
    /// 设计 §6.4 当初留它是为了把自嵌套从"整条轨道降级"收窄成"一个槽位降级"；那条路没走——
    /// 环现在由 `eval.EvaluationGraph` 在写入期直接拒绝，不需要在求值时挑挑拣拣。
    /// 留着它是给求值链一个**廉价的断言钩子**：想知道"这处公式是不是正在咬自己"，
    /// 问这一句就够了，不必自己维护一份求值栈（实现见 `eval.ExpressionScope`）
    default String resolving() {
        return null;
    }
}
