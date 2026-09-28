package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 求值环境：当前时间 + 变量表 + 曲线读数 + 自定义函数表。
///
/// 求值链只认这个接口，不认 `CameraAnimation`——单条曲线、单个值都能脱离动画求值，
/// 好测试也好复用。实现见 [ExpressionScope]。
///
/// 名字解析（[Expression.Resolver]）与"环境里有什么"在这里合为一体：公式里的一个名字，
/// 可能是内置量（`t` / `p` / `wt`）、用户变量、某条轨道的读数，也可能是自定义函数，
/// 这些只有环境自己知道。曲线那边只管拿解析好的数值（见 [ResolvedKeys]）。
@NullMarked
public interface Scope extends Expression.Resolver {
    /// 当前求值时间
    float time();

    /// 播放进度：当前时间占动画总时长的比例，归一化到 0~1
    float progress();

    /// 世界时间：游戏内一天的进度，归一化到 0~1
    float worldTime();

    /// 取某条曲线轨道在当前时刻的读数；轨道不存在返回 NaN
    float track(String id);

    /// 按本环境求值一段公式；公式非法或引用到取不到值的名字时返回 NaN。
    ///
    /// 默认实现就是把本环境交给 [Expression]，实现一般不必重写
    default float evaluate(@Nullable String expression) {
        return Expression.evaluate(expression, this);
    }
}
