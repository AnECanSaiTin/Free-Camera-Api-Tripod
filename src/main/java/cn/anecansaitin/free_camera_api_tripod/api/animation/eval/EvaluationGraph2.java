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

/// 配套编辑器要求：
/// 1.删除任意节点时先检查是否被引用。如有，则无法直接删除。可考虑加个"断开引用"的操作，将对应引用改为固定值0。或者沿着引用链全部删除。
/// 2.
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
                .allowsSelfLoops(true)// 允许自环，由dfs来检测
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

    /// 预检查轨道表达式是否成环。
    ///
    /// 无副作用。
    ///
    /// @param trackId 轨道 ID
    /// @param expression 表达式
    /// @return 所有闭合的环
    public List<List<Node>> checkTrackFormula(String trackId, String expression) {
        if (expression.isBlank()) {
            return Collections.emptyList();
        }

        Node node = Node.track(trackId);
        Set<Node> defined = definedWith(node);
        HashSet<Node> dest = new HashSet<>();
        refs(expression, Set.of(), defined, dest);
        return walker(node, dest);
    }

    /// 预检查变量表达式是否成环。
    ///
    /// 无副作用。
    ///
    /// @param varName 变量名
    /// @param source 值来源
    /// @return 所有闭合的环
    public List<List<Node>> checkVarFormula(String varName, ValueSource source) {
        Node node = Node.var(varName);
        Set<Node> defined = definedWith(node);
        HashSet<Node> dest = new HashSet<>();

        switch (source) {
            case TrackRef trackRef -> {
                Node track = Node.track(trackRef.trackId());

                if (defined.contains(track)) {
                    dest.add(track);
                }
            }
            case Formula formula -> refs(formula.expression(), Set.of(), defined, dest);
            default -> {
            }
        }

        return walker(node, dest);
    }

    /// 预检查函数表达式是否成环。
    ///
    /// 无副作用。
    ///
    /// @param funcName 函数名
    /// @param parameters 函数形参
    /// @param expression 方法体
    /// @return 所有闭合的环
    public List<List<Node>> checkFuncFormula(String funcName, Set<String> parameters, String expression) {
        if (expression.isBlank()) {
            return Collections.emptyList();
        }

        Node node = Node.func(funcName);
        Set<Node> defined = definedWith(node);
        HashSet<Node> dest = new HashSet<>();
        refs(expression, parameters, defined, dest);
        return walker(node, dest);
    }

    /// 反向遍历找环：从 [node] 沿 predecessors（"谁会引用我"）往上走，**撞上 [dest] 里的节点就是闭合的环**。
    ///
    /// [dest] 是"这次改动之后 [node] 将引用到的节点"，也就是它未来的出边另一头。判据是：
    /// 一条经过 [node] 的环，必然有一条边是**走进** [node] 的（`候选 → … → node`），
    /// 所以从 [node] 反向走一定碰得到那个候选；碰到时栈里存的就是 `候选 → … → node` 那一段。
    /// 因此**不需要把 [dest] 真的加进图**：加不加都不影响"谁能走到 node"，
    /// 而 [node] 自己的出边也永远不会出现在一条以 [node] 结尾的路径上——这也让它天然是只读的
    ///
    /// 一条环报一条，**报全**：[visited] 只增不减，每个能走回 [node] 的节点只入栈一次，
    /// 所以同一个环不会从不同路径重复报出来；[dest] 里每个能走回 [node] 的节点各报一条
    ///
    /// 无出边就一定无环；命中 [dest] 后不再往下走，免得同一个环被报成"更长的绕法"
    ///
    /// 报出来的格式与 [#dsfResolve] 一致：有序的闭合路径，第一个节点与最后一个节点相同
    ///
    /// @param node 被改动的节点（轨道 / 变量 / 函数）
    /// @param dest 这次改动之后它引用到的节点
    /// @return 所有闭合的环；空表 = 放行
    private List<List<Node>> walker(Node node, Set<Node> dest) {
        // 无后继，不会成环
        if (dest.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<List<Node>> result = new ArrayList<>();
        // 已被访问过的节点
        HashSet<Node> visited = new HashSet<>();
        // 当前访问路径上的节点，栈顶 = 当前节点
        ArrayDeque<Node> pathStack = new ArrayDeque<>();
        // 栈帧
        Deque<Frame> frames = new ArrayDeque<>();
        visited.add(node);
        pathStack.push(node);
        frames.push(new Frame(node, graph.predecessors(node).iterator()));

        while (!frames.isEmpty()) {
            Frame frame = frames.peek();

            if (!frame.remaining.hasNext()) {
                frames.pop();
                pathStack.pop();
                continue;
            }

            Node next = frame.remaining.next();

            // 能走回 node：next → … → node 这一圈闭合
            if (dest.contains(next)) {
                ArrayList<Node> cycle = new ArrayList<>();
                cycle.add(next);
                cycle.addAll(pathStack);
                cycle.add(next);
                result.add(cycle);
                continue;
            }

            if (visited.add(next)) {
                pathStack.push(next);
                frames.push(new Frame(next, graph.predecessors(next).iterator()));
            }
        }

        return result;
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

    private void putAllEdges(Node nodeU, Set<Node> nodeVs) {
        for (Node nodeV : nodeVs) {
            graph.putEdge(nodeU, nodeV);
        }
    }

    private void trackEdges(AnimationTrack track, Set<Node> defined, Set<Node> dest) {
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
                    formulaRefs(formula.expression(), defined, dest);
                }
            }
        }
    }

    private void formulaRefs(Variable variable, Set<Node> defined, Set<Node> dest) {
        switch (variable.source()) {
            case TrackRef trackRef -> {
                Node node = new Node(Node.Type.TRACK, trackRef.trackId());

                if (!defined.contains(node)) {
                    return;
                }

                dest.add(node);
            }
            case Formula formula -> formulaRefs(formula.expression(), defined, dest);
            default -> {}
        }
    }

    private void formulaRefs(String source, Set<Node> defined, Set<Node> dest) {
        refs(source, Set.of(), defined, dest);
    }

    private void funcEdges(CustomFunction function, Set<Node> defined, Set<Node> dest) {
        String body = function.body();
        refs(body, new HashSet<>(function.parameters()), defined, dest);
    }

    private void refs(String source, Set<String> parameters, Set<Node> defined, Set<Node> dest) {
        Expression.Names names = Expression.names(source, parameters);

        for (String variable : names.variables()) {
            Node node = new Node(Node.Type.VAR, variable);

            if (!defined.contains(node)) {
                continue;
            }

            dest.add(node);
        }

        for (String function : names.functions()) {
            Node node = new Node(Node.Type.FUNC, function);

            if (!defined.contains(node)) {
                continue;
            }

            dest.add(node);
        }
    }

    /// 深度优先走一遍图，把发现的环都写进 [dest]。
    ///
    /// 报出来的格式与 [#walker] 一致：有序的闭合路径，第一个节点与最后一个节点相同
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

                if (!frame.remaining.hasNext()) {
                    // 这一支走完了
                    frames.removeLast();
                    pathStack.remove(frame.node);
                    path.pollLast();
                    continue;
                }

                Node next = frame.remaining.next();

                // 栈内存在相同节点，说明成环
                if (pathStack.contains(next)) {
                    // 与 walker 同一套格式：有序的闭合路径，首尾是同一个节点（`next → … → next`）。
                    // path 从队头（当前节点）排到队尾（这棵 DFS 树的根），顺着读就是环的方向，
                    // 只取到 next 为止，省掉反转变量的绕法
                    ArrayList<Node> cycle = new ArrayList<>();
                    dest.add(cycle);
                    cycle.add(next);

                    for (Node pathNode : path) {
                        cycle.add(pathNode);

                        if (next.equals(pathNode)) {
                            break;
                        }
                    }

                    cycle.add(next);
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

    /// 栈帧
    /// 栈帧：正在下探的节点，以及它**还没走完的那些邻居**。
    ///
    /// 不叫 successors：本结构被两处复用，[#walker] 沿 predecessors（反向）走、
    /// [#dsfResolve] 沿 successors（正向）走，名字必须与方向无关
    private record Frame(Node node, Iterator<Node> remaining) {
    }
}
