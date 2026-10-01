package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 按 id 取轨道读数。
///
/// 这是求值环境里最小的一块：只读一条曲线、只想要"某条轨道此刻是多少"的地方（见 `eval.CurveSampler`）
/// 拿到这个接口就够了，不必认识整个[名字解析器][Resolver]。
///
/// 轨道不存在时返回 [Float#NaN]——与"公式算不出来"同一个信号，由 [ValueSource#evaluateOrFallback] 统一兜底。
@FunctionalInterface
@NullMarked
public interface TrackLookup {
    /// 该轨道在当前时刻的读数；轨道不存在返回 NaN
    float track(String id);
}
