package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// 表达式求值上下文：内置变量 `t`（当前时间）加上动画里定义的变量。
///
/// 变量的取值来源二选一：绑定曲线轨道时取该轨道在当前时刻的读数，否则用变量自己的固定值。
///
/// 两个关键设计：
/// - **只读静态曲线**：取轨道读数时调 `curve().evaluate(time)`（**不带上下文**），
///   因此"变量指向某条轨道、该轨道的键又引用这个变量"这种自嵌套不会无限递归——
///   轨道在作为变量取值时，它自己挂的公式被忽略，用的是键上的固定数值。
///   代价是同一个键"作为相机属性播放"与"作为变量被引用"可能得到不同的值，
///   要避免这种歧义，就别让变量指向挂有公式的轨道（变量面板会标出来）。
/// - **一次求值内每个变量只算一次**：变量值在本次求值期间不会变（上下文绑定了固定时间），
///   结果缓存下来。同一条公式里写 3 次、或一帧内几十个字段引用同一个变量，都只算一次；
///   上下文是每帧新建的，不存在过期问题。
@NullMarked
public final class ExpressionContext implements Expression.Resolver {
    /// 内置变量：当前求值时间
    public static final String TIME_VARIABLE = "t";

    private final float time;
    /// 绑定了轨道的变量：名字 -> 轨道
    private final Map<String, CurveTrack> boundTracks = new HashMap<>();
    /// 未绑定轨道的变量：名字 -> 固定值
    private final Map<String, Float> fixedValues = new HashMap<>();
    /// 本次求值期间的变量取值缓存
    private final Map<String, Float> cache = new HashMap<>();

    public ExpressionContext(CameraAnimationc animation, float time) {
        this.time = time;

        for (Variable variable : animation.variables()) {
            if (!variable.bound()) {
                fixedValues.put(variable.name(), variable.value());
                continue;
            }

            CurveTrack track = findTrack(animation, variable.trackId());

            if (track != null) {
                boundTracks.put(variable.name(), track);
            }
        }
    }

    public float time() {
        return time;
    }

    @Override
    public float resolve(String name) {
        if (TIME_VARIABLE.equals(name)) {
            return time;
        }

        Float cached = cache.get(name);

        if (cached != null) {
            return cached;
        }

        float value = compute(name);
        cache.put(name, value);
        return value;
    }

    /// 求值一段公式；公式非法或变量取不到值时返回 NaN，调用方据此回退到原来的数值
    public float evaluate(@Nullable String expression) {
        return Expression.evaluate(expression, this);
    }

    /// 变量的实际取值：绑定轨道就读轨道，否则读固定值，都没有就是取不到（NaN）
    private float compute(String name) {
        CurveTrack track = boundTracks.get(name);

        if (track != null) {
            return track.curve().evaluate(time);
        }

        Float fixed = fixedValues.get(name);
        return fixed == null ? Float.NaN : fixed;
    }

    private static @Nullable CurveTrack findTrack(CameraAnimationc animation, String id) {
        for (CurveTrack track : animation.curveTracks()) {
            if (track.id().equals(id)) {
                return track;
            }
        }

        return null;
    }
}
