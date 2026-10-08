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

    private record Node(Type type, String name) {
        private enum Type {
            VAR, FUNC, TRACK
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
            putAllEdge(nodeU, dest);
        }

        for (Variable variable : variables) {
            Node nodeU = new Node(Node.Type.VAR, variable.name());
            dest.clear();
            formulaRefs(variable, defined, dest);
            putAllEdge(nodeU, dest);
        }

        for (CustomFunction function : functions) {
            Node nodeU = new Node(Node.Type.FUNC, function.name());
            dest.clear();
            funcEdges(function, defined, dest);
            putAllEdge(nodeU, dest);
        }
        // endregion
    }

    private void putAllEdge(Node nodeU, Set<Node> nodeVs) {
        for (Node nodeV : nodeVs) {
            graph.putEdge(nodeU, nodeV);
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

    private void resolve() {
        // 已被访问过的节点
        Set<Node> visited = new HashSet<>();
        // 当前访问路径上的节点
        Set<Node> stack = new HashSet<>();
        // 按照访问顺序记录访问路径，用于打印循环引用
        Deque<Node> path = new ArrayDeque<>();
        cycleInfo.clear();

        for (Node node : graph.nodes()) {
            if (visited.contains(node)) {
                continue;
            }

            dfs(node, visited, stack, path);
        }
    }

    private void dfs(Node node, Set<Node> visited, Set<Node> stack, Deque<Node> path) {
        visited.add(node);
        stack.add(node);
        path.push(node);

        for (Node next : graph.successors(node)) {
            // 栈内存在相同节点，说明成环
            if (stack.contains(next)) {
                ArrayList<Node> cycle = new ArrayList<>();
                cycleInfo.add(cycle);
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
                dfs(next, visited, stack, path);
            }
        }

        path.pop();
        stack.remove(node);
    }
}
