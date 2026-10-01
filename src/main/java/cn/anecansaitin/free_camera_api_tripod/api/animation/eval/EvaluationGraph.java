package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimationc;
import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Expression;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Formula;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Variable;
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
/// 环有两条闭合途径，都在这里被看见：
/// - **变量之间**：公式里按名字引用另一个变量
/// - **变量与轨道之间**：变量绑轨道（`TrackRef`），而那条轨道上的键又挂了引用该变量的公式
///
/// 第二条是 `VariableGraph`（已删）看不见的——它按设计只装变量，理由是"变量读轨道走静态曲线，
/// 所以不会成环"。那条免责声明是为旧的静态读取语义写的；这里把它收回，于是环在**写入之前**
/// 就能判出来，而不是等求值的时候静默退回固定值。
///
/// ## 节点的依赖是怎么算出来的
///
/// 一个变量的依赖由两部分组成，这也是它绑轨道那一跳的展开方式：
///
/// ```text
/// deps(V) = 公式里引用的变量 ∪ deps(V 绑的那条轨道)
/// deps(轨道) = 该轨道所有键、所有槽位的公式里引用的变量
/// ```
///
/// 所以图里存的是**零件**（变量的公式引用、变量绑了谁、每条轨道的引用），
/// 依赖关系由 [#resolved] 现算。这样"如果改一下会怎样"的试算就不必去猜哪些下游要跟着变——
/// 换掉零件、重算一遍就是准确结果。
///
/// ## 怎么判
///
/// 整理成一张普通的依赖表之后，剩下的就是 Kahn 拓扑：入度为 0 的先定值，
/// 一轮下来**没被定值的节点就都在环上**——这正是界面要报的那串名字。
///
/// 图很小（变量与轨道都是几十上百的量级），所以每次按需现算、不做缓存。
@NullMarked
public final class EvaluationGraph {
    /// 每个变量的公式**直接**引用到的变量名；绑轨道那一跳不在这里
    private final Map<String, Set<String>> variableFormulas = new LinkedHashMap<>();
    /// 每个变量绑的轨道 id；没绑轨道的不入表
    private final Map<String, String> variableBindings = new LinkedHashMap<>();
    /// 每条曲线轨道依赖的变量名（该轨道所有键、所有槽位上的公式合并）
    private final Map<String, Set<String>> trackDependencies = new LinkedHashMap<>();
    /// 拓扑没走完的节点；没有环时为空
    private final Set<String> cycle = new LinkedHashSet<>();

    private EvaluationGraph() {
    }

    /// 按当前动画内容建图
    public static EvaluationGraph of(CameraAnimationc animation) {
        EvaluationGraph graph = new EvaluationGraph();

        for (Variable variable : animation.symbols().variables()) {
            graph.variableFormulas.put(variable.name(), Set.of());
        }

        Set<String> names = new HashSet<>(graph.variableFormulas.keySet());

        // 先把每条曲线轨道的依赖算好：变量可能绑轨道，要顺着这一跳展开
        for (AnimationTrack track : animation.tracks()) {
            if (track instanceof CurveTrack curveTrack) {
                graph.trackDependencies.put(curveTrack.id(), trackFormulaNames(animation, curveTrack.id(), names));
            }
        }

        for (Variable variable : animation.symbols().variables()) {
            ValueSource source = variable.source();

            if (source instanceof Formula formula) {
                graph.variableFormulas.put(variable.name(), formulaNames(formula.expression(), names));
            }

            if (source != null && source.trackId() != null) {
                graph.variableBindings.put(variable.name(), source.trackId());
                graph.trackDependencies.putIfAbsent(source.trackId(), Set.of());
            }
        }

        graph.cycle.addAll(markResolved(graph.resolved()));
        return graph;
    }

    /// 环上的节点名（变量名与轨道 id 混在一起）；没有环时返回 null。
    ///
    /// 结果**不保证只含环本身**：凡是依赖了环、因而也算不出值的节点都留在里面。
    /// 报给用户时这正是想要的——"这些名字参与了循环引用"比精确截出那一圈更好懂
    public @Nullable List<String> cycle() {
        return cycle.isEmpty() ? null : List.copyOf(cycle);
    }

    /// 环上的节点名串成 `A → B` 的样子；没有环时返回 null
    public @Nullable String cycleText() {
        return cycle.isEmpty() ? null : String.join(" → ", cycle);
    }

    /// 该名字是否在环上（或依赖了环）
    public boolean isCircular(String name) {
        return cycle.contains(name);
    }

    /// 这段公式挂到 [trackId] 上会不会成环。
    ///
    /// **只试算，不改动任何数据**：换掉该轨道的引用零件再判一次——绑在这条轨道上的变量
    /// 会通过 [#resolved] 自动跟着变，不必单独传播
    public boolean allowsTrackFormula(String trackId, @Nullable String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        Map<String, Set<String>> tracks = copy(trackDependencies);
        tracks.put(trackId, formulaNames(expression, variableFormulas.keySet()));
        return isAcyclic(resolved(variableFormulas, variableBindings, tracks));
    }

    /// 变量 [name] 的来源换成 [source] 之后会不会成环
    public boolean allowsVariableSource(String name, @Nullable ValueSource source) {
        Map<String, Set<String>> formulas = copy(variableFormulas);
        Map<String, String> bindings = new LinkedHashMap<>(variableBindings);
        Map<String, Set<String>> tracks = copy(trackDependencies);

        formulas.put(name, Set.of());
        bindings.remove(name);

        if (source instanceof Formula formula) {
            formulas.put(name, formulaNames(formula.expression(), formulas.keySet()));
        }

        if (source != null && source.trackId() != null) {
            bindings.put(name, source.trackId());
            tracks.putIfAbsent(source.trackId(), Set.of());
        }

        return isAcyclic(resolved(formulas, bindings, tracks));
    }

    // region 依赖展开

    /// 把零件展开成一张完整的依赖表：变量 → 它依赖的变量名（含绑轨道那一跳），轨道 → 同理
    private Map<String, Set<String>> resolved() {
        return resolved(variableFormulas, variableBindings, trackDependencies);
    }

    private static Map<String, Set<String>> resolved(Map<String, Set<String>> formulas,
                                                      Map<String, String> bindings,
                                                      Map<String, Set<String>> tracks) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : formulas.entrySet()) {
            Set<String> found = new LinkedHashSet<>(entry.getValue());
            String bound = bindings.get(entry.getKey());

            if (bound != null) {
                found.addAll(tracks.getOrDefault(bound, Set.of()));
            }

            dependencies.put(entry.getKey(), found);
        }

        // 没被任何变量绑、但自己挂了公式的轨道也要在图里：环可能就闭在它身上
        for (Map.Entry<String, Set<String>> entry : tracks.entrySet()) {
            dependencies.putIfAbsent(entry.getKey(), entry.getValue());
        }

        return dependencies;
    }

    /// 某条轨道上所有键、所有槽位上的公式引用到的变量名
    private static Set<String> trackFormulaNames(CameraAnimationc animation, String trackId, Set<String> names) {
        Curve curve = animation.curve(trackId);

        if (curve == null) {
            return Set.of();
        }

        Set<String> found = new LinkedHashSet<>();

        for (int i = 0; i < curve.size(); i++) {
            Keyframe key = curve.key(i);

            for (KeyField field : KeyField.values()) {
                if (key.source(field) instanceof Formula formula) {
                    found.addAll(formulaNames(formula.expression(), names));
                }
            }
        }

        return found;
    }

    /// 一段公式里出现的、确实是本动画变量的名字。
    ///
    /// 用 [Expression#identifiers] 扫标识符而不是做真正的名字解析：函数名、内置量、参数名
    /// 都不在变量表里，被 [names] 挡掉；剩下的就是依赖。宁可多报一个名字（判环更保守）也不漏
    private static Set<String> formulaNames(@Nullable String expression, Set<String> names) {
        Set<String> found = new LinkedHashSet<>();

        for (String identifier : Expression.identifiers(expression)) {
            if (names.contains(identifier)) {
                found.add(identifier);
            }
        }

        return found;
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

    private static Map<String, Set<String>> copy(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }

        return copy;
    }
}
