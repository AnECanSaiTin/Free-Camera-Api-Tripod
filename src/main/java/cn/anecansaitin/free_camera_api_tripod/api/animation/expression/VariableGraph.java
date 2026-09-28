package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 变量依赖分析。
///
/// 变量之间通过"公式里按名字引用另一个变量"连成有向图。图里**只有变量**：
/// 关键帧与路径节点上的公式不是节点，而变量读轨道时走的是静态曲线（见 [ExpressionScope]，
/// 轨道上的公式不参与），所以"变量绑轨道、轨道又引用该变量"这种自嵌套并不会成环——
/// 那是另一种语义歧义，由 `CameraAnimation.selfReferencing` 单独提示。
///
/// 成环的典型写法：`V1 = V2 * 2` 且 `V2 = V1 + 1`。
/// 这种图在求值时会互相拉扯，界面上要明确报错，而不是静默给一个数。
@NullMarked
public final class VariableGraph {
    private VariableGraph() {
    }

    /// 找出第一条循环引用，返回环上的变量名（首尾是同一个名字）；没有环返回 null
    public static @Nullable List<String> findCycle(List<Variable> variables) {
        Map<String, Variable> byName = new LinkedHashMap<>();

        for (Variable variable : variables) {
            byName.put(variable.name(), variable);
        }

        Set<String> done = new HashSet<>();
        Deque<String> path = new ArrayDeque<>();
        Set<String> onPath = new HashSet<>();

        for (Variable variable : variables) {
            List<String> cycle = visit(variable.name(), byName, done, path, onPath);

            if (cycle != null) {
                return cycle;
            }
        }

        return null;
    }

    /// 该变量的公式引用到的其它变量名（引用不存在的名字时不算依赖，那只是写错了）
    public static Set<String> dependencies(Variable variable, Set<String> names) {
        if (!(variable.source() instanceof FormulaValue formula)) {
            return Set.of();
        }

        Set<String> found = new LinkedHashSet<>();

        for (String name : Expression.identifiers(formula.expression())) {
            if (names.contains(name)) {
                found.add(name);
            }
        }

        return found;
    }

    private static @Nullable List<String> visit(String name, Map<String, Variable> byName, Set<String> done,
                                                Deque<String> path, Set<String> onPath) {
        if (done.contains(name)) {
            return null;
        }

        if (!onPath.add(name)) {
            // 撞回正在求值路径上的节点，说明这里是一个环：截出环的那一段，末尾补回起点
            List<String> cycle = new ArrayList<>();
            boolean collecting = false;

            for (String step : path) {
                if (step.equals(name)) {
                    collecting = true;
                }

                if (collecting) {
                    cycle.add(step);
                }
            }

            cycle.add(name);
            return List.copyOf(cycle);
        }

        path.addLast(name);
        Variable variable = byName.get(name);

        if (variable != null) {
            for (String dependency : dependencies(variable, byName.keySet())) {
                List<String> cycle = visit(dependency, byName, done, path, onPath);

                if (cycle != null) {
                    return cycle;
                }
            }
        }

        path.removeLast();
        onPath.remove(name);
        done.add(name);
        return null;
    }
}
