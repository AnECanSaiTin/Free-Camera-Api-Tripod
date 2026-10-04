package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.*;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 求值依赖图：一段动画里「谁依赖谁」的完整关系，用来保证**求值图无环**。
///
/// ## 为什么需要它
///
/// 环有三条闭合途径，都在这里被看见：
/// - **变量之间**：公式里按名字引用另一个变量
/// - **变量与轨道之间**：变量绑轨道（`TrackRef`），而那条轨道上的键又挂了引用该变量的公式
/// - **函数参与的任意组合**：函数体里引用变量、调用别的函数，或反过来被变量/轨道的公式调用
///
/// 第三条是这版补上的：函数体对图完全隐形时，`f(x) = f(x)` 与"变量 → 函数 → 该变量"
/// 这类环只能等求值时被深度栈截成 NaN。现在它们和别的环一样，**写入之前**就判出来。
///
/// ## 节点身份：类别前缀 + 名字
///
/// 图里的节点不是裸名字，而是 `v:` / `f:` / `t:` 加名字（[#variableKey] / [#functionKey] /
/// [#trackKey]）。原因是**语言层面这三类名字本来就是三个命名空间**——`a`、`a(x)`、轨道 id
/// 各走各的，`SymbolTable` 也只在同类内部查重——图里的节点必须跟着分开，否则：
/// - 同名的变量与函数会共用一条依赖表项，函数那边的依赖被吞掉，环**漏判**；
/// - 变量绑一条与自己同名的轨道（`fov` 绑 `fov`）会连出一条自环，被当成**假环**拒掉。
///
/// 前缀是单射、不需要转义：三类首字符互异。显示名另存一张表（[#displayNames]），
/// 只有 [cycle] / [cycleText] 输出时贴回裸名字。
///
/// 将来要加第四类节点就再加一个前缀；如果种类多到"前缀容易写错"，可以把 key 换成
/// `record NodeKey(Kind kind, String name)`——类型上防手误，代价是把 [Refs]、依赖表与
/// [#markResolved] 的反向索引全部换成 `NodeKey`（改动面约翻倍，收益只是编译期防错）。
///
/// ## 节点的依赖是怎么算出来的
///
/// 三类节点（变量 / 轨道 / 函数）各自记一份**直接引用**，其中函数那一跳**不展开**——
/// 函数名本身就是一个节点，边直接连过去，环交给拓扑去发现。展开成什么样的写法有讲究：
///
/// ```text
/// deps(变量) = 公式引用的变量 ∪ 公式调用的函数 ∪ （绑了轨道就再加那条轨道）
/// deps(轨道) = 该轨道所有键、所有槽位公式引用的变量与函数
/// deps(函数) = 函数体引用的变量与函数（形参不算引用）
/// ```
///
/// 引用**取自编译好的语法树**（见 [Expression#names]），不再扫文本：文本扫分不清函数体的形参
/// 与同名变量，会把 `f(a) = a + 1` 里的 `a` 算成对变量 `a` 的依赖——多算一条边，在"写入期
/// 拒绝"这条口径下就是拒绝一条本来合法的公式。树还能看见函数调用，这正是补第三条途径的前提。
///
/// 只保留**真实存在的节点**做边：公式里写了一个还没定义的函数名，它不成为图上的点
/// （运行期它给 NaN），否则一个"指向不存在的点"的边会让拓扑永远走不完，报出一个假环。
/// 内置量（`t` / `p` / `wt`）根本不会来：`Expression.names` 把它们放在单独一桶里，
/// 而它们是求值环境的名字（`ExpressionScope.resolve` 最先认掉），不是依赖。
///
/// ## 怎么判
///
/// 整理成一张普通的依赖表之后，剩下的就是 Kahn 拓扑：入度为 0 的先定值，
/// 一轮下来**没被定值的节点就都在环上**——这正是界面要报的那串名字。
///
/// 图很小（变量与函数都是几十上百的量级），所以每次按需现算、不做缓存。
@NullMarked
public final class EvaluationGraph {
    /// 变量节点：`v:` + 变量名
    private static final String VARIABLE_PREFIX = "v:";
    /// 函数节点：`f:` + 函数名
    private static final String FUNCTION_PREFIX = "f:";
    /// 轨道节点：`t:` + 轨道 id
    private static final String TRACK_PREFIX = "t:";

    /// 变量中的引用；key 是节点 key
    private final Map<String, Refs> variableFormulas = new LinkedHashMap<>();
    /// 轨道中的引用
    private final Map<String, Refs> trackFormulas = new LinkedHashMap<>();
    /// 函数中的引用
    private final Map<String, Refs> functionBodies = new LinkedHashMap<>();
    /// 每个变量绑的轨道：变量 key → 轨道 key；没绑轨道的不入表
    private final Map<String, String> variableBindings = new LinkedHashMap<>();
    /// 图上真实存在的**裸名字**：用来判断"公式里这个名字算不算引用"
    private final Set<String> variableNames = new LinkedHashSet<>();
    private final Set<String> functionNames = new LinkedHashSet<>();
    private final Set<String> trackIds = new LinkedHashSet<>();
    /// 节点 key → 给用户看的名字；只服务 [cycle] / [cycleText]
    private final Map<String, String> displayNames = new LinkedHashMap<>();
    /// 拓扑没走完的节点（key）；没有环时为空
    private final Set<String> cycle = new LinkedHashSet<>();

    private EvaluationGraph() {
    }

    private static String variableKey(String name) {
        return VARIABLE_PREFIX + name;
    }

    private static String functionKey(String name) {
        return FUNCTION_PREFIX + name;
    }

    private static String trackKey(String id) {
        return TRACK_PREFIX + id;
    }

    /// 按当前动画内容建图
    public static EvaluationGraph of(CameraAnimationc animation) {
        EvaluationGraph graph = new EvaluationGraph();
        SymbolTablec symbols = animation.symbols();

        // 读变量：**每个变量都是一个节点**，哪怕它的来源是固定值
        // （少写这一步，引用它的公式就会有一条指向图外的边，拓扑走不完 → 报出假环；
        //  绑轨道的变量还会连不上"变量 → 轨道"那条边，跨轨道的环就漏判了）
        for (Variable variable : symbols.variables()) {
            graph.note(variableKey(variable.name()), variable.name());
            graph.variableNames.add(variable.name());
            graph.variableFormulas.put(variableKey(variable.name()), Refs.NONE);
        }

        // 读函数
        for (CustomFunction function : symbols.functions()) {
            graph.note(functionKey(function.name()), function.name());
            graph.functionNames.add(function.name());
        }

        // 读曲线轨道
        for (AnimationTrack track : animation.tracks()) {
            if (track instanceof CurveTrack curveTrack) {
                graph.note(trackKey(curveTrack.id()), curveTrack.id());
                graph.trackIds.add(curveTrack.id());
            }
        }

        // 解析轨道的引用
        for (String trackId : graph.trackIds) {
            graph.trackFormulas.put(trackKey(trackId), graph.trackRefs(animation, trackId));
        }

        // 解析变量的引用
        for (Variable variable : symbols.variables()) {
            ValueSource source = variable.source();

            // 解析表达式型变量
            if (source instanceof Formula formula) {
                graph.variableFormulas.put(variableKey(variable.name()),
                        graph.refs(formula.expression(), Set.of()));
            }

            // 绑了轨道的变量：连一条变量 → 轨道的边
            if (source instanceof TrackRef track) {
                graph.note(trackKey(track.trackId()), track.trackId());
                graph.variableBindings.put(variableKey(variable.name()), trackKey(track.trackId()));
                graph.trackFormulas.putIfAbsent(trackKey(track.trackId()), Refs.NONE);
            }
        }

        // 解析函数的引用
        for (CustomFunction function : symbols.functions()) {
            graph.functionBodies.put(functionKey(function.name()),
                    graph.refs(function.body(), Set.copyOf(function.parameters())));
        }

        graph.cycle.addAll(markResolved(graph.resolved(graph.variableFormulas, graph.variableBindings,
                graph.trackFormulas, graph.functionBodies)));
        return graph;
    }

    /// 环上的节点名（变量名、轨道 id 与函数名混在一起）；没有环时返回 null。
    ///
    /// 结果**不保证只含环本身**：凡是依赖了环、因而也算不出值的节点都留在里面。
    /// 报给用户时这正是想要的——"这些名字参与了循环引用"比精确截出那一圈更好懂
    public @Nullable List<String> cycle() {
        return cycle.isEmpty() ? null : cycle.stream().map(this::display).toList();
    }

    /// 环上的节点名串成 `A → B` 的样子；没有环时返回 null
    public @Nullable String cycleText() {
        return cycle.isEmpty() ? null : String.join(" → ", cycle.stream().map(this::display).toList());
    }

    /// 该名字是否在环上（或依赖了环）。三类节点同名时只要有一类在环上就算
    public boolean isCircular(String name) {
        return cycle.contains(variableKey(name))
                || cycle.contains(functionKey(name))
                || cycle.contains(trackKey(name));
    }

    /// 这段公式挂到 [trackId] 上会不会成环。
    ///
    /// **只试算，不改动任何数据**：换掉该轨道的引用零件再判一次——绑在这条轨道上的变量
    /// 会跟着这条边一起被判到，不必单独传播
    public boolean allowsTrackFormula(String trackId, @Nullable String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        Map<String, Refs> tracks = new LinkedHashMap<>(trackFormulas);
        tracks.put(trackKey(trackId), refs(expression, Set.of()));
        return isAcyclic(resolved(variableFormulas, variableBindings, tracks, functionBodies));
    }

    /// 变量 [name] 的来源换成 [source] 之后会不会成环。
    ///
    /// **只试算，不改动任何数据**：换掉这一个节点的零件再判一次，其余节点沿用建图时的引用。
    ///
    /// 注意试算的粒度：[name] 若**还不存在于变量表**里，别的节点对它的引用在建图时就被当成
    /// "未定义的名字"滤掉了，这次试算看不到它们。编辑器里变量总是先建好再绑来源，所以不受影响
    public boolean allowsVariableSource(String name, @Nullable ValueSource source) {
        String key = variableKey(name);
        Map<String, Refs> formulas = new LinkedHashMap<>(variableFormulas);
        Map<String, String> bindings = new LinkedHashMap<>(variableBindings);
        Map<String, Refs> tracks = new LinkedHashMap<>(trackFormulas);
        // 试算的变量自己也算"存在的节点"，`V = V + 1` 这种自引用才判得出来
        Set<String> knownVariables = new LinkedHashSet<>(variableNames);
        knownVariables.add(name);

        formulas.put(key, Refs.NONE);
        bindings.remove(key);

        if (source instanceof Formula formula) {
            formulas.put(key, refs(formula.expression(), Set.of(), knownVariables, functionNames));
        }

        if (source instanceof TrackRef track) {
            bindings.put(key, trackKey(track.trackId()));
            tracks.putIfAbsent(trackKey(track.trackId()), Refs.NONE);
        }

        return isAcyclic(resolved(formulas, bindings, tracks, functionBodies));
    }

    /// 函数 [name] 的函数体换成 [body] 之后会不会成环。
    ///
    /// [parameters] 是这次编辑的形参表：形参名在函数体里不算引用，
    /// 所以它是参数的一部分，不能省
    public boolean allowsFunctionBody(String name, @Nullable String body, List<String> parameters) {
        Map<String, Refs> functions = new LinkedHashMap<>(functionBodies);
        // 试算的函数自己也算"存在的节点"，`f(x) = f(x)` 这种自递归才判得出来
        Set<String> knownFunctions = new LinkedHashSet<>(functionNames);
        knownFunctions.add(name);
        functions.put(functionKey(name), refs(body, Set.copyOf(parameters), variableNames, knownFunctions));
        return isAcyclic(resolved(variableFormulas, variableBindings, trackFormulas, functions));
    }

    /// 登记一个节点的显示名（同名不同类各登记各的）
    private void note(String key, String displayName) {
        displayNames.putIfAbsent(key, displayName);
    }

    /// 节点 key → 给用户看的名字；查不到就退回 key（正常构造下不会发生）
    private String display(String key) {
        return displayNames.getOrDefault(key, key);
    }

    // region 依赖展开

    /// 一条边集合：已经映射成**节点 key**，只含图上真实存在的节点
    private record Refs(Set<String> nodes) {
        static final Refs NONE = new Refs(Set.of());
    }

    /// 解析一段函数或表达式的引用。
    ///
    /// @param expression 函数或表达式的字符串
    /// @param parameters 函数的形参
    /// @return 所有引用（不含函数形参），已映射成节点 key。字符串空、语法错误、调用节点栈超上限时返回 [Refs#NONE]
    private Refs refs(@Nullable String expression, Set<String> parameters) {
        return refs(expression, parameters, variableNames, functionNames);
    }

    /// 解析一段函数或表达式的引用。
    ///
    /// 已知名中需要包含当前正在解析的函数或表达式名（变量名），从而避免递归引用。如：
    ///
    /// f1() -> f1() + t
    ///
    /// var1 -> var1 + t
    ///
    /// @param expression     函数或表达式的字符串
    /// @param parameters     函数的形参
    /// @param knownVariables 当前已知的变量名（含当前解析变量名）
    /// @param knownFunctions 当前已知的函数名（含当前解析函数名）
    /// @return 所有引用（不含函数形参），已映射成节点 key。字符串空、语法错误、调用节点栈超上限时返回 [Refs#NONE]
    private Refs refs(@Nullable String expression, Set<String> parameters, Set<String> knownVariables,
                      Set<String> knownFunctions) {
        Expression.Names names = Expression.names(expression, parameters);
        Set<String> nodes = new LinkedHashSet<>();

        // 裸名字只可能是变量（内置量在 names 的另一个桶里）
        for (String name : names.variables()) {
            if (knownVariables.contains(name)) {
                nodes.add(variableKey(name));
            }
        }

        // 调用名只可能是函数（内置函数的调用在编译期就变成了算子）
        for (String name : names.functions()) {
            if (knownFunctions.contains(name)) {
                nodes.add(functionKey(name));
            }
        }

        return nodes.isEmpty() ? Refs.NONE : new Refs(Set.copyOf(nodes));
    }

    /// 一条轨道上所有键、所有槽位上的公式引用
    private Refs trackRefs(CameraAnimationc animation, String trackId) {
        Curve curve = animation.curve(trackId);

        if (curve == null) {
            return Refs.NONE;
        }

        Set<String> nodes = new LinkedHashSet<>();

        for (int i = 0; i < curve.size(); i++) {
            Keyframe key = curve.key(i);

            for (KeyField field : KeyField.values()) {
                if (key.source(field) instanceof Formula formula) {
                    nodes.addAll(refs(formula.expression(), Set.of()).nodes());
                }
            }
        }

        return nodes.isEmpty() ? Refs.NONE : new Refs(Set.copyOf(nodes));
    }

    /// 把三类节点展开成一张普通的依赖表：节点 key → 它直接依赖的节点 key
    private static Map<String, Set<String>> resolved(Map<String, Refs> formulas,
                                                     Map<String, String> bindings,
                                                     Map<String, Refs> tracks,
                                                     Map<String, Refs> functions) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();

        for (Map.Entry<String, Refs> entry : formulas.entrySet()) {
            Set<String> found = new LinkedHashSet<>(entry.getValue().nodes());
            String bound = bindings.get(entry.getKey());

            if (bound != null) {
                // 变量 → 轨道：绑了就写一条显式的边，环由拓扑自己发现，不必把轨道的依赖搬进来
                found.add(bound);
            }

            dependencies.put(entry.getKey(), found);
        }

        for (Map.Entry<String, Refs> entry : tracks.entrySet()) {
            dependencies.putIfAbsent(entry.getKey(), entry.getValue().nodes());
        }

        for (Map.Entry<String, Refs> entry : functions.entrySet()) {
            dependencies.putIfAbsent(entry.getKey(), entry.getValue().nodes());
        }

        return dependencies;
    }

    // endregion

    // region 拓扑

    private static boolean isAcyclic(Map<String, Set<String>> dependencies) {
        return markResolved(dependencies).isEmpty();
    }

    /// Kahn 拓扑：返回**没能定值的节点**，空集表示无环。
    ///
    /// 一个节点能定值，当且仅当它依赖的节点全都已经定值
    private static Set<String> markResolved(Map<String, Set<String>> dependencies) {
        // 每个节点的剩余依赖，以及反向索引（谁依赖我）
        Map<String, Set<String>> remaining = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        Deque<String> ready = new ArrayDeque<>();

        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            remaining.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));

            for (String dependency : entry.getValue()) {
                dependents.computeIfAbsent(dependency, key -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }

        for (Map.Entry<String, Set<String>> entry : remaining.entrySet()) {
            if (entry.getValue().isEmpty()) {
                ready.add(entry.getKey());
            }
        }

        Set<String> resolved = new HashSet<>();

        while (!ready.isEmpty()) {
            String name = ready.poll();

            if (!resolved.add(name)) {
                continue;
            }

            for (String dependent : dependents.getOrDefault(name, Set.of())) {
                Set<String> left = remaining.get(dependent);

                if (left != null && left.remove(name) && left.isEmpty()) {
                    ready.add(dependent);
                }
            }
        }

        Set<String> unresolved = new LinkedHashSet<>();

        for (String name : remaining.keySet()) {
            if (!resolved.contains(name)) {
                unresolved.add(name);
            }
        }

        return unresolved;
    }

    // endregion
}
