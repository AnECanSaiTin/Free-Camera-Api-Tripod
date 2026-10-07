package cn.anecansaitin.free_camera_api_tripod.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.KeyFields;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.*;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EvaluationGraph2 {
    private record Node(Type type, String name) {
        private enum Type {
            VAR, FUNC, TRACK
        }
    }

    public EvaluationGraph2(List<Variable> variables, List<CustomFunction> functions, List<AnimationTrack> tracks) {
        // region 创建图
        MutableGraph<Node> graph = GraphBuilder
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
        for (AnimationTrack track : tracks) {
            Node nodeU = new Node(Node.Type.TRACK, track.id());

            for (Node nodeV : trackEdges(track, defined)) {
                graph.putEdge(nodeU, nodeV);
            }
        }

        for (Variable variable : variables) {
            Node nodeU = new Node(Node.Type.VAR, variable.name());

            for (Node nodeV : varEdges(variable, defined)) {
                graph.putEdge(nodeU, nodeV);
            }
        }

        for (CustomFunction function : functions) {
            Node nodeU = new Node(Node.Type.FUNC, function.name());

            for (Node nodeV : funcEdges(function, defined)) {
                graph.putEdge(nodeU, nodeV);
            }
        }
        // endregion
    }

    private Set<Node> trackEdges(AnimationTrack track, Set<Node> whiteList) {
        if (!(track instanceof CurveTrack curveTrack)) {
            return Set.of();
        }

        Set<Node> edges = new HashSet<>();

        for (int i = 0; i < curveTrack.keyCount(); i++) {
            Keyframe key = curveTrack.key(i);

            if (key == null) {
                continue;
            }

            for (KeyField field : KeyField.values) {
                if (key.source(field) instanceof Formula formula) {
                    varEdges(formula.expression(), whiteList, edges);
                }
            }
        }

        return edges;
    }

    private Set<Node> varEdges(Variable variable, Set<Node> whiteList) {
        variable.source();
        return Set.of();
    }

    private void varEdges(String source, Set<Node> whiteList, Set<Node> dest) {

    }

    private Set<Node> funcEdges(CustomFunction function, Set<Node> whiteList) {
        return Set.of();
    }

    private Set<Node> funcEdges(String source, List<String> parameters, Set<Node> whiteList) {
        return Set.of();
    }

    private void refs(){

    }
}
