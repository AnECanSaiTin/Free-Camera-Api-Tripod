package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 表达式求值器：内置变量（当前时间 `t`、播放进度 `progress`、世界时间 `worldTime`）加上动画里定义的变量，并提供轨道读数。
///
/// 它只认[变量表][Variable]与一个[曲线查询][CurveLookup]，**不认整个动画对象**——
/// 单条曲线、单个值都能脱离动画求值，好测试也好复用；从动画构造的入口是 [ExpressionSolver#of]。
///
/// 三条关键约定：
/// - **一次求值内每个变量只算一次**：变量值在本次求值期间不会变（时间是构造时定下的），
///   结果缓存下来，同一条公式里写 3 次、或一帧内几十个字段引用同一个变量，都只算一次
/// - **变量读轨道走静态曲线**：`curve.evaluate(time)` 不带求解器，所以轨道上的公式在这一步被忽略，
///   用的是键上的固定数值。这样"变量指向某条轨道、该轨道的键又引用这个变量"不会无限递归
/// - **成环时明确记下来**：`V1` 引用 `V2`、`V2` 又引用 `V1` 时，求值返回 NaN 并把这串环记录在
///   [cycle] 里，界面据此报错；不会静默给一个数，也不会递归到栈溢出
@NullMarked
public final class ExpressionSolver implements Solver {
    /// 内置变量：当前求值时间
    public static final String TIME_VARIABLE = "t";
    /// 内置变量：播放进度（0~1）
    public static final String PROGRESS_VARIABLE = "p";
    /// 内置变量：世界时间（0~1）
    public static final String WORLD_TIME_VARIABLE = "wt";

    /// 内置变量的名字；这些名字被求值器自己占用，不能拿来当用户变量
    public static final List<String> BUILTIN_VARIABLES = List.of(TIME_VARIABLE, PROGRESS_VARIABLE, WORLD_TIME_VARIABLE);

    /// 按 id 查曲线；没有返回 null
    @FunctionalInterface
    public interface CurveLookup {
        @Nullable Curve curve(String id);
    }

    private final float time;
    private final float progress;
    private final float worldTime;
    private final Map<String, Variable> variables = new LinkedHashMap<>();
    /// 自定义函数，按名字查
    private final Map<String, CustomFunction> functions = new LinkedHashMap<>();
    private final CurveLookup curves;
    /// 变量取值缓存
    private final Map<String, Float> cache = new HashMap<>();
    /// 已解析的曲线，避免每次读轨道都扫一遍轨道表
    private final Map<String, Curve> curveCache = new HashMap<>();
    /// 正在求值的变量（栈），用来发现循环引用
    private final Deque<String> visiting = new ArrayDeque<>();
    private final Set<String> visitingSet = new HashSet<>();
    private @Nullable List<String> cycle;

    public ExpressionSolver(List<Variable> variables, List<CustomFunction> functions, CurveLookup curves, float time, float progress, float worldTime) {
        this.time = time;
        this.progress = progress;
        this.worldTime = worldTime;
        this.curves = curves;

        for (Variable variable : variables) {
            this.variables.put(variable.name(), variable);
        }

        for (CustomFunction function : functions) {
            this.functions.put(function.name(), function);
        }
    }

    /// 从动画构造：编辑器与播放器都走这个入口。
    /// 播放进度由动画时长与当前时间算出，世界时间由调用方给出（归一化到 0~1）
    public static ExpressionSolver of(CameraAnimationc animation, float time, float worldTime) {
        return new ExpressionSolver(animation.variables(), animation.functions(), id -> findCurve(animation, id), time,
                progressOf(animation, time), worldTime);
    }

    /// 播放进度：当前时间占总时长的比例，夹在 0~1；空动画没有时长，返回 0
    private static float progressOf(CameraAnimationc animation, float time) {
        float duration = animation.duration();
        return duration > 0 ? Math.clamp(time / duration, 0f, 1f) : 0f;
    }

    /// 名字是否被内置变量占用
    public static boolean isBuiltin(String name) {
        return BUILTIN_VARIABLES.contains(name);
    }

    @Override
    public float time() {
        return time;
    }

    @Override
    public float progress() {
        return progress;
    }

    @Override
    public float worldTime() {
        return worldTime;
    }

    @Override
    public float resolve(String name) {
        if (TIME_VARIABLE.equals(name)) {
            return time;
        }

        if (PROGRESS_VARIABLE.equals(name)) {
            return progress;
        }

        if (WORLD_TIME_VARIABLE.equals(name)) {
            return worldTime;
        }

        Float cached = cache.get(name);

        if (cached != null) {
            return cached;
        }

        Variable variable = variables.get(name);

        if (variable == null) {
            return Float.NaN;
        }

        // 撞回正在求值的变量就是循环引用：记下来，返回 NaN 让上层走回退值
        if (!visitingSet.add(name)) {
            recordCycle(name);
            return Float.NaN;
        }

        visiting.addLast(name);
        float value;

        try {
            value = ValueSource.evaluateOrFallback(variable.source(), this);
        } finally {
            visiting.removeLast();
            visitingSet.remove(name);
        }

        cache.put(name, value);
        return value;
    }

    @Override
    public float track(String id) {
        Curve curve = curveCache.computeIfAbsent(id, curves::curve);
        return curve == null ? Float.NaN : curve.evaluate(time);
    }

    /// 自定义函数查询：公式里名字不在内置清单里时走这里
    @Override
    public @Nullable CustomFunction function(String name) {
        return functions.get(name);
    }

    /// 本次求值遇到的循环引用（变量名按引用顺序，首尾同名）；没遇到返回 null
    public @Nullable List<String> cycle() {
        return cycle;
    }

    /// 求值一段公式；公式非法或变量取不到值时返回 NaN
    public float evaluate(@Nullable String expression) {
        return Expression.evaluate(expression, this);
    }

    private void recordCycle(String name) {
        if (cycle != null) {
            return;
        }

        List<String> path = new ArrayList<>(visiting);
        int from = path.indexOf(name);
        List<String> found = new ArrayList<>(from < 0 ? path : path.subList(from, path.size()));
        found.add(name);
        cycle = List.copyOf(found);
    }

    private static @Nullable Curve findCurve(CameraAnimationc animation, String id) {
        for (CurveTrack track : animation.curveTracks()) {
            if (track.id().equals(id)) {
                return track.curve();
            }
        }

        return null;
    }
}
