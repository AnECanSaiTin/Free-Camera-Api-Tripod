package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.*;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import org.jspecify.annotations.NullMarked;

import java.util.*;

@NullMarked
public class EvaluationGraph2 {
    private final MutableGraph<Node> graph;
    private final List<List<Node>> cycleInfo = new ArrayList<>();

    public record Node(Type type, String name) {
        private enum Type {
            VAR, FUNC, TRACK
        }

        private static Node var(String name) {
            return new Node(Type.VAR, name);
        }

        private static Node func(String name) {
            return new Node(Type.FUNC, name);
        }
        private static Node track(String name) {
            return new Node(Type.TRACK, name);
        }
    }

    public EvaluationGraph2(List<Variable> variables, List<CustomFunction> functions, List<AnimationTrack> tracks) {
        // region 创建图
        graph = GraphBuilder
                .directed()
                .expectedNodeCount(variables.size() + functions.size() + tracks.size())
                .build();
        // endregion
        // region 添加可作为目标的节点
        for (Variable variable : variables) {
            graph.addNode(new Node(Node.Type.VAR, variable.name()));
        }

        for (CustomFunction function : functions) {
            graph.addNode(new Node(Node.Type.FUNC, function.name()));
        }

        for (AnimationTrack track : tracks) {
            graph.addNode(new Node(Node.Type.TRACK, track.id()));
        }
        // 存储为用于过滤掉未定义引用
        Set<Node> defined = Set.copyOf(graph.nodes());
        // endregion
        // region 依据依赖关系添加边
        Set<Node> dest = new HashSet<>();

        for (AnimationTrack track : tracks) {
            Node nodeU = new Node(Node.Type.TRACK, track.id());
            dest.clear();
            trackEdges(track, defined, dest);
            putAllEdges(nodeU, dest);
        }

        for (Variable variable : variables) {
            Node nodeU = new Node(Node.Type.VAR, variable.name());
            dest.clear();
            formulaRefs(variable, defined, dest);
            putAllEdges(nodeU, dest);
        }

        for (CustomFunction function : functions) {
            Node nodeU = new Node(Node.Type.FUNC, function.name());
            dest.clear();
            funcEdges(function, defined, dest);
            putAllEdges(nodeU, dest);
        }
        // endregion
        // 检测循环引用
        dsfResolve(cycleInfo);
    }

    public List<List<Node>> cycleInfo() {
        return cycleInfo;
    }

    public boolean hasCycle() {
        return !cycleInfo.isEmpty();
    }

    /// 这条公式挂到 trackId 上会不会成环；**返回这次改动闭合的环**（空表 = 放行）。
    ///
    /// **只读探针**：不把候选边真的加上去试，也不改动图。判据是"候选节点在不在这个节点的祖先里"——
    /// 基础态无环（构造时 [#dsfResolve] 已确认过），所以新环一定经过这个节点，必然由
    /// "一条候选边 `node → v`"加上"一条 `v ⇒ node` 的路径"拼成。真加上去试就会多出两件麻烦：
    /// 自环加不进去（Guava 的图默认不允许自环，`putEdge` 会抛），以及试算的环会留在 [#cycleInfo] 里
    public List<List<Node>> checkTrackFormula(String trackId, String expression) {
        if (expression == null || expression.isBlank()) {
            return Collections.emptyList();
        }

        Node node = Node.track(trackId);
        return checkSource(node, replaceableEdges(node), refs(expression, Set.of(), definedWith(node)));
    }

    /// 变量 varName 的来源换成 source 之后会不会成环；返回闭合的环（空表 = 放行）。
    ///
    /// 候选 = 公式引用的变量与函数，或（绑轨道时）那条轨道。`Constant` 没有任何依赖，一定放行
    public List<List<Node>> checkVarFormula(String varName, ValueSource source) {
        Node node = Node.var(varName);
        Set<Node> defined = definedWith(node);
        Set<Node> candidates = new HashSet<>();

        switch (source) {
            case TrackRef trackRef -> {
                Node track = Node.track(trackRef.trackId());

                if (defined.contains(track)) {
                    candidates.add(track);
                }
            }
            case Formula formula -> candidates.addAll(refs(formula.expression(), Set.of(), defined));
            default -> {
            }
        }

        return checkSource(node, replaceableEdges(node), candidates);
    }

    /// 函数 funcName 的函数体换成 expression 之后会不会成环；返回闭合的环（空表 = 放行）。
    ///
    /// [parameters] 是这次编辑的形参表：形参名在函数体里不算引用，所以它是参数的一部分，不能省。
    /// 函数自己也算"图上存在的节点"（见 [#definedWith]），否则 `f(x) = f(x)` 这种自递归看不见
    public List<List<Node>> checkFuncFormula(String funcName, Set<String> parameters, String expression) {
        Node node = Node.func(funcName);
        return checkSource(node, replaceableEdges(node), refs(expression, parameters, definedWith(node)));
    }

    /// 判"把 [node] 的出边换成 [candidates] 会不会成环"，返回闭合的环（空表 = 放行）。
    ///
    /// [replaced] 是 [node] 原来那些出边，必须是**已经摘掉**的状态传进来（见 [#replaceableEdges]）：
    /// 它们正是这次改动要换掉的东西，留着就会把"本来就要消失的环"算成新环，误拒一次合法改动。
    /// 收完祖先马上放回去，所以整个过程对外仍然只读；判完把 [#cycleInfo] 刷新回真实图的状态
    private List<List<Node>> checkSource(Node node, Set<Node> replaced, Set<Node> candidates) {
        Set<Node> ancestors;

        try {
            ancestors = ancestors(node);
        } finally {
            putAllEdges(node, replaced);
        }

        for (Node candidate : candidates) {
            if (candidate.equals(node) || ancestors.contains(candidate)) {
                // 真实图没变，环还是原来的；这里只负责把"这次会闭合的那一圈"报出来
                List<List<Node>> cycle = new ArrayList<>();
                cycle.add(cyclePath(node, candidate));
                return cycle;
            }
        }

        // 放行：候选边不会被真的加上去，图还是原来那张，把 [#cycleInfo] 刷回它的真实状态
        dsfResolve(cycleInfo);
        return Collections.emptyList();
    }

    /// 图上的节点，外加 [node] 自己。
    ///
    /// 带上自己是为了让"变量 / 函数还不存在于表里"时也判得出自引用（`V = V + 1`、`f(x) = f(x)`）。
    /// 建图时别的节点对它的引用已经被当成"未定义的名字"滤掉了，这一点与旧实现同粒度
    private Set<Node> definedWith(Node node) {
        Set<Node> defined = new HashSet<>(graph.nodes());
        defined.add(node);
        return defined;
    }

    /// 解析一段公式（或函数体）引用到的节点；图上不存在的名字不成为边的另一头
    private Set<Node> refs(String source, Set<String> parameters, Set<Node> defined) {
        HashSet<Node> dest = new HashSet<>();
        refs(source, parameters, defined, dest);
        return dest;
    }

    /// [node] 这次要被换掉的那些出边：摘下来、拿到快照、立刻放回去。
    ///
    /// 调用方拿到返回值时图已经复原，摘掉的那一瞬只服务于"[#ancestors] 必须在没有这些边的图上收"
    private Set<Node> replaceableEdges(Node node) {
        Set<Node> successors = new HashSet<>(graph.successors(node));

        try {
            removeAllEdges(node, successors);
            return successors;
        } finally {
            putAllEdges(node, successors);
        }
    }

    /// [node] 的所有祖先：能沿依赖边走回 [node] 的节点（一步或多步），**不含 [node] 自己**。
    ///
    /// 就是"上游闭包"，沿 predecessors 反向走一遍。判环的判据全在这里：候选节点落在其中，
    /// 说明它已经依赖 [node]，再连一条 `node → 候选` 就闭合了
    private Set<Node> ancestors(Node node) {
        Set<Node> ancestors = new HashSet<>();
        Deque<Node> pending = new ArrayDeque<>(graph.predecessors(node));

        while (!pending.isEmpty()) {
            Node current = pending.poll();

            if (!current.equals(node) && ancestors.add(current)) {
                pending.addAll(graph.predecessors(current));
            }
        }

        return ancestors;
    }

    /// 从 [node] 到 [candidate] 的依赖链（含两端），拼成 `node → … → candidate → node` 那一圈。
    ///
    /// 先沿 predecessors 从 candidate 一路退回 node（收集到的是反向链），再反转过来
    private List<Node> cyclePath(Node node, Node candidate) {
        List<Node> path = new ArrayList<>();
        path.add(node);

        if (!candidate.equals(node)) {
            Map<Node, Node> steps = stepsTo(node, candidate);

            for (Node step = candidate; step != null; step = steps.get(step)) {
                path.add(step);
            }
        }

        path.add(node);
        return List.copyOf(path);
    }

    /// 从 [from] 沿 predecessors 走回 [to]，返回"每个节点上一步是谁"（`from` 不在表里，它是起点）；
    /// 走不到返回空表。只在基础态上走，所以路径上的边都真实存在
    private Map<Node, Node> stepsTo(Node from, Node to) {
        Map<Node, Node> steps = new HashMap<>();
        Set<Node> seen = new HashSet<>();
        Deque<Node> pending = new ArrayDeque<>();
        seen.add(from);
        pending.add(from);

        while (!pending.isEmpty()) {
            Node current = pending.poll();

            if (current.equals(to)) {
                return steps;
            }

            for (Node predecessor : graph.predecessors(current)) {
                if (seen.add(predecessor)) {
                    steps.put(predecessor, current);
                    pending.add(predecessor);
                }
            }
        }

        return Map.of();
    }

    private void putAllEdges(Node nodeU, Set<Node> nodeVs) {
        for (Node nodeV : nodeVs) {
            graph.putEdge(nodeU, nodeV);
        }
    }

    private void removeAllEdges(Node nodeU, Set<Node> nodeVs) {
        for (Node nodeV : nodeVs) {
            graph.removeEdge(nodeU, nodeV);
        }
    }

    private void trackEdges(AnimationTrack track, Set<Node> whiteList, Set<Node> dest) {
        if (!(track instanceof CurveTrack curveTrack)) {
            return;
        }

        for (int i = 0; i < curveTrack.keyCount(); i++) {
            Keyframe key = curveTrack.key(i);

            if (key == null) {
                continue;
            }

            for (KeyField field : KeyField.values) {
                if (key.source(field) instanceof Formula formula) {
                    formulaRefs(formula.expression(), whiteList, dest);
                }
            }
        }
    }

    private void formulaRefs(Variable variable, Set<Node> whiteList, Set<Node> dest) {
        switch (variable.source()) {
            case TrackRef trackRef -> {
                Node node = new Node(Node.Type.TRACK, trackRef.trackId());

                if (!whiteList.contains(node)) {
                    return;
                }

                dest.add(node);
            }
            case Formula formula -> formulaRefs(formula.expression(), whiteList, dest);
            default -> {}
        }
    }

    private void formulaRefs(String source, Set<Node> whiteList, Set<Node> dest) {
        refs(source, Set.of(), whiteList, dest);
    }

    private void funcEdges(CustomFunction function, Set<Node> whiteList, Set<Node> dest) {
        String body = function.body();
        refs(body, new HashSet<>(function.parameters()), whiteList, dest);
    }

    private void refs(String source, Set<String> parameters, Set<Node> whiteList, Set<Node> dest) {
        Expression.Names names = Expression.names(source, parameters);

        for (String variable : names.variables()) {
            Node node = new Node(Node.Type.VAR, variable);

            if (!whiteList.contains(node)) {
                continue;
            }

            dest.add(node);
        }

        for (String function : names.functions()) {
            Node node = new Node(Node.Type.FUNC, function);

            if (!whiteList.contains(node)) {
                continue;
            }

            dest.add(node);
        }
    }

    /// 深度优先走一遍图，把发现的环都写进 [dest]。
    ///
    /// **迭代实现**：原来靠递归下探，环很深（或依赖链很长）时会递归到栈溢出；现在把"当前节点还有哪些
    /// 后继没走"装进显式栈 [frames]，深度只受堆限制。判定口径与原来逐字一致——[pathStack] 就是递归版里
    /// 那个"当前访问路径上的节点"集合，[path] 就是递归版的调用路径（栈顶 = 当前节点）
    private void dsfResolve(List<List<Node>> dest) {
        // 已被访问过的节点
        Set<Node> visited = new HashSet<>();
        // 当前访问路径上的节点
        Set<Node> pathStack = new HashSet<>();
        // 按照访问顺序记录访问路径，用于打印循环引用
        Deque<Node> path = new ArrayDeque<>();
        // 显式栈：每帧 = 一个正在下探的节点 + 它还没走完的后继
        Deque<Frame> frames = new ArrayDeque<>();

        for (Node root : graph.nodes()) {
            if (visited.contains(root)) {
                continue;
            }

            visited.add(root);
            pathStack.add(root);
            path.addLast(root);
            frames.addLast(new Frame(root, graph.successors(root).iterator()));

            while (!frames.isEmpty()) {
                Frame frame = frames.peekLast();

                if (!frame.successors.hasNext()) {
                    // 这一支走完了，与递归版出栈时同步维护 pathStack / path 一样
                    frames.removeLast();
                    pathStack.remove(frame.node);
                    path.pollLast();
                    continue;
                }

                Node next = frame.successors.next();

                // 栈内存在相同节点，说明成环
                if (pathStack.contains(next)) {
                    ArrayList<Node> cycle = new ArrayList<>();
                    dest.add(cycle);
                    cycle.add(next);

                    for (Node pathNode : path) {
                        cycle.add(pathNode);

                        if (next.equals(pathNode)) {
                            break;
                        }
                    }

                    // deque遍历顺序从队尾到队头，因此需要反转顺序
                    Collections.reverse(cycle);
                    continue;
                }

                if (!visited.contains(next)) {
                    visited.add(next);
                    pathStack.add(next);
                    path.addLast(next);
                    frames.addLast(new Frame(next, graph.successors(next).iterator()));
                }
            }
        }
    }

    /// 迭代 DFS 的一帧：正在下探的节点，以及它那些还没走完的后继
    private record Frame(Node node, Iterator<Node> successors) {
    }
}
