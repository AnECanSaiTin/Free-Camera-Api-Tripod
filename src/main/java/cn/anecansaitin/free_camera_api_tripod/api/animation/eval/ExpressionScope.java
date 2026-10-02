package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Resolver;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Scope;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.SymbolTablec;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 表达式求值环境：内置变量（当前时间 `t`、播放进度 `p`、世界时间 `wt`）加上动画里定义的变量，并提供轨道读数。
///
/// 它只认变量表与一个曲线查询，**不认整个动画对象**——单条曲线、单个值都能脱离动画求值，
/// 好测试也好复用；从动画构造的入口是 [CurveSampler#scope]。
///
/// 它同时实现 [Scope]（环境：时间）与 [Resolver]（解析：变量、函数、轨道读数）。
/// 两者在这一个实现里天然合一——解析变量要读轨道，读轨道又需要当前时间——
/// 但对**用**它的人来说是分开的两件事，见 [Scope] 的说明。
///
/// 三条关键约定：
/// - **一次求值内每个变量只算一次**：变量值在本次求值期间不会变（时间是切帧时定下的），
///   结果缓存下来，同一条公式里写 3 次、或一帧内几十个字段引用同一个变量，都只算一次
/// - **变量读轨道读的是这一帧的真值**：轨道按当前作用域求值（见 [track]），轨道上的公式照常参与，
///   所以"变量绑的轨道"与"该轨道作为属性播放"给出同一个数。变量与轨道因此构成互递归的一整张图，
///   由 `EvaluationGraph` 在写入期保证无环
/// - **成环是写入期就挡掉的事**：环在挂公式、绑轨道、读档三处判定并拒绝，正常数据里到不了这里。
///   [#cycle] 与 `visiting` 栈因此退化成一道**兜底**——万一有哪条编辑路径绕过了校验，
///   它保证求值返回 NaN 而不是递归到栈溢出
@NullMarked
public final class ExpressionScope implements Scope, Resolver {
    /// 内置变量：当前求值时间
    public static final String TIME_VARIABLE = "t";
    /// 内置变量：播放进度（0~1）
    public static final String PROGRESS_VARIABLE = "p";
    /// 内置变量：世界时间（0~1）
    public static final String WORLD_TIME_VARIABLE = "wt";

    /// 内置变量的名字；这些名字被求值环境自己占用，不能拿来当用户变量
    public static final List<String> BUILTIN_VARIABLES = List.of(TIME_VARIABLE, PROGRESS_VARIABLE, WORLD_TIME_VARIABLE);

    /// 变量求值栈的深度上限：只是环没被写入期拦下时的兜底，正常链路远到不了。
    /// 取一个明显大于任何真实依赖链、又远小于栈容量的数
    private static final int MAX_TRACK_DEPTH = 64;

    /// 按 id 查曲线；没有返回 null
    @FunctionalInterface
    public interface CurveLookup {
        @Nullable Curve curve(String id);
    }

    private final SymbolTablec symbols;
    private final CurveLookup curves;
    private float time;
    private float progress;
    private float worldTime;
    /// 帧版本；每调一次 [frame] 就 +1，缓存靠它失效（见 [Scope#version]）
    private long version;
    /// 变量取值缓存；换帧时清空，Map 本身复用
    private final Object2FloatMap<String> cache = new Object2FloatOpenHashMap<>();
    /// 每条曲线配一份读取器，避免同一次求值里反复新建；**带作用域**，所以轨道上的公式照常求值。
    /// 换帧时清空：作用域会被长期复用，不清的话读档换掉的旧曲线会一直被这张表拽着，
    /// 上一帧算出来的数也会被继续用。
    ///
    /// **这里刻意不走 [CurveSampler]**：采样器的缓存意义是"同一帧内同一条曲线只解析一次"，
    /// 而 [track] 是求值环境内部的查询，自己按 [Curve] 缓存一份已经够；硬要共享就会让
    /// 采样器与本类互相持有，而且公式求值可能撞上主采样器算到一半的那条曲线
    private final Map<Curve, CurveSample> trackSamples = new IdentityHashMap<>();
    /// 正在求值的变量（栈）：既是 [#resolving] 的答案，也是环没被写入期拦下时的兜底
    private final Deque<String> visiting = new ArrayDeque<>();
    private final Set<String> visitingSet = new HashSet<>();
    private @Nullable List<String> cycle;

    /// 绑定一份符号表与曲线查询建出作用域；建好后要用 [frame] 指定当前时刻才能求值。
    ///
    /// 符号表是**按引用持有**的，不抄成自己的一份：变量表随时可改（改名、增删、读档整体替换），
    /// 抄一份就再也同步不了了——界面上改个变量名，播放取的还是旧名字
    public ExpressionScope(SymbolTablec symbols, CurveLookup curves) {
        this.symbols = symbols;
        this.curves = curves;
    }

    /// 从动画构造：编辑器的预览作用域、播放器每帧切的那个作用域都走它，
    /// 需要一次性环境的调用方用 [CurveSampler#scope]。
    ///
    /// 播放进度由动画时长与当前时间算出，世界时间由调用方给出（归一化到 0~1）
    public static ExpressionScope of(CameraAnimationc animation, float time, float worldTime) {
        return new ExpressionScope(animation.symbols(), animation::curve)
                .frame(time, progressOf(animation, time), worldTime);
    }

    /// 切到新的一帧：更新时间与环境、清掉上一帧的缓存、版本号 +1，返回自身便于链式调用。
    ///
    /// 作用域是**可复用**的：播放器与编辑器各持有一份，每帧调一次这个即可，
    /// 不必每帧新建（原先每帧要分配六个集合）。缓存按版本号失效，见 [CurveSample]
    public ExpressionScope frame(float time, float progress, float worldTime) {
        this.time = time;
        this.progress = progress;
        this.worldTime = worldTime;
        this.version++;
        this.cache.clear();
        this.trackSamples.clear();
        this.cycle = null;
        return this;
    }

    /// 播放进度：当前时间占总时长的比例，夹在 0~1；空动画没有时长，返回 0
    public static float progressOf(CameraAnimationc animation, float time) {
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

    /// 名字解析与函数查询都在本环境上：[Scope] 只认环境，需要解析的调用方从这里取
    @Override
    public Resolver resolver() {
        return this;
    }

    @Override
    public long version() {
        return version;
    }

    /// 当前正在求值的变量名；不在变量求值过程中时返回 null
    @Override
    public @Nullable String resolving() {
        return visiting.peekLast();
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

        Variable variable = symbols.variable(name);

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
            // 回退只写在 ValueSource.evaluateOrFallback 一处，这里不再自己判 NaN
            value = variable.evaluate(this);
        } finally {
            visiting.removeLast();
            visitingSet.remove(name);
        }

        cache.put(name, value);
        return value;
    }

    /// 轨道读数：取这条曲线在**当前时刻**的值，轨道上的公式照常参与求值。
    ///
    /// 这和"把该轨道当属性播放"走的是同一段求值（同一份 [CurveSample] + 同一个 `this` 作用域），
    /// 所以同一个键在两个场景下给出同一个数——原来"变量读轨道走静态曲线"那条规则带来的
    /// 取值口径歧义就此消失。
    ///
    /// 递归一定终止：环在写入期就被 `EvaluationGraph` 拦掉了（挂公式、绑轨道、读档三处），
    /// 所以这里不需要每次求值再判一次环。万一有哪条路径绕过了校验，[#visiting] 的深度上限
    /// 兜住它——记一条环并把这一处退成 NaN，而不是栈溢出
    @Override
    public float track(String id) {
        // 查曲线本身已经是一次按名取表，这里不再额外缓存一份
        Curve curve = curves.curve(id);

        if (curve == null) {
            return Float.NaN;
        }

        if (visitingSet.size() >= MAX_TRACK_DEPTH) {
            return Float.NaN;
        }

        return curve.evaluate(time, trackSamples.computeIfAbsent(curve, key -> CurveSample.at(key, this)));
    }

    /// 自定义函数查询：公式里名字不在内置清单里时走这里
    @Override
    public @Nullable CustomFunction function(String name) {
        return symbols.function(name);
    }

    /// 本次求值撞上的循环引用（变量名按引用顺序）；没遇到返回 null。
    ///
    /// 正常数据不会走到这里——环在写入期就被 [EvaluationGraph] 拒绝了。留着它是因为
    /// "求值返回 NaN"比"递归到栈溢出"好得多，代价只是一个变量的判重集合
    public @Nullable List<String> cycle() {
        return cycle;
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
}
