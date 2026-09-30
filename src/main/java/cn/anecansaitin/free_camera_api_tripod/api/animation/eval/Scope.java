package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 求值环境：**当前时刻 + 曲线读数**。
///
/// 求值链只认这个接口，不认 `CameraAnimation`——单条曲线、单个值都能脱离动画求值，
/// 好测试也好复用。实现见 [ExpressionScope]。
///
/// 这里**不做名字解析**：公式里的一个名字该取什么值（内置量 `t` / `p` / `wt`、用户变量、
/// 自定义函数）是 [Expression.Resolver] 的事，由 [resolver] 给出。
/// 两者分开是因为用途不同——环境是"这一帧是什么时候、各条曲线读到多少",
/// 解析是"公式里的名字是什么"；只画一条曲线（见 `CurveSampler`）的人不需要解析能力。
///
/// 曲线那边只管拿解析好的数值（见 `ResolvedKeys`）。
@NullMarked
public interface Scope {
    /// 当前求值时间
    float time();

    /// 播放进度：当前时间占动画总时长的比例，归一化到 0~1
    float progress();

    /// 世界时间：游戏内一天的进度，归一化到 0~1
    float worldTime();

    /// 取某条曲线轨道在当前时刻的读数；轨道不存在返回 NaN
    float track(String id);

    /// 版本号：**每换一帧就 +1**。缓存按它失效，而不是按作用域的对象身份判断。
    ///
    /// 作用域是**可复用**的——播放器与编辑器各持有一份，每帧改时间与版本号接着用，
    /// 不必每帧新建（省下六个集合的分配）。代价是"换了个作用域对象"不再等价于"换了一帧"，
    /// 因此缓存必须认这个版本号，见 `ResolvedKeys`
    long version();

    /// 名字解析器：内置量、用户变量、自定义函数都在这里查
    Expression.Resolver resolver();

    /// 按本环境求值一段公式；公式非法或引用到取不到值的名字时返回 NaN。
    ///
    /// 默认实现就是把本环境的解析器交给 [Expression]，实现一般不必重写
    default float evaluate(@Nullable String expression) {
        return Expression.evaluate(expression, resolver());
    }
}
