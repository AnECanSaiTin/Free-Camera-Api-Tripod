package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

/// 求值环境：当前时间 + 变量表 + 曲线读数。
///
/// 求值链只认这个接口，不认 `CameraAnimation`——单条曲线、单个值都能脱离动画求值，
/// 好测试也好复用。实现见 [ExpressionSolver]。
public interface Solver extends Expression.Resolver {
    /// 当前求值时间
    float time();

    /// 播放进度：当前时间占动画总时长的比例，归一化到 0~1
    float progress();

    /// 世界时间：游戏内一天的进度，归一化到 0~1
    float worldTime();

    /// 取某条曲线轨道在当前时刻的读数；轨道不存在返回 NaN
    float track(String id);
}
