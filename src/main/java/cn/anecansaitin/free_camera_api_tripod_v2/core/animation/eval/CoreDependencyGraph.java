package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.DependencyGraph;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReference;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferenceKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.ValueSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.DefaultExpressionCompiler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 从文档公式与符号表构建轨道依赖图。
///
/// 节点是文档内所有 `TrackId`（机位通道、注视、活动、相机来源、文档级扩展轨道）。边表示
/// “公式求值会读取另一条轨道的值”：直接 `TrackRef`、变量公式、函数体中的引用都会展开到
/// 具体轨道。样本名、内建量和常量不产生轨道依赖。
///
/// 构建时同时报告未定义变量 / 函数、函数实参个数不符、变量 / 函数递归环与轨道依赖环。
/// 这些诊断与统一校验的表达式引用检查互补：后者检查名字存在性，本类进一步检查递归与环。
@NullMarked
public final class CoreDependencyGraph implements DependencyGraph {

    private final Map<TrackId, Set<TrackId>> dependencies;
    private final List<Diagnostic> diagnostics;

    private CoreDependencyGraph(Map<TrackId, Set<TrackId>> dependencies,
                                List<Diagnostic> diagnostics) {
        this.dependencies = dependencies;
        this.diagnostics = diagnostics;
    }

    /// 从快照构建依赖图。
    ///
    /// @param snapshot 已发布快照；诊断 scope 使用其 revision
    public static CoreDependencyGraph build(AnimationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Builder builder = new Builder(snapshot);
        builder.build();
        return new CoreDependencyGraph(builder.finishDependencies(), builder.finishDiagnostics());
    }

    @Override
    public Set<TrackId> dependenciesOf(TrackId trackId) {
        Objects.requireNonNull(trackId, "trackId");
        return dependencies.getOrDefault(trackId, Set.of());
    }

    @Override
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    private static final class Builder {
        private final AnimationSnapshot snapshot;
        private final CameraAnimation document;
        private final Revision revision;
        private final DefaultExpressionCompiler compiler = new DefaultExpressionCompiler();
        private final Map<TrackId, Set<TrackId>> dependencies = new LinkedHashMap<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final Map<String, Variable> variables = new HashMap<>();
        private final Map<String, CustomFunction> functions = new HashMap<>();

        private Builder(AnimationSnapshot snapshot) {
            this.snapshot = snapshot;
            this.document = snapshot.document();
            this.revision = snapshot.revision();

            for (Variable variable : document.symbols().variables()) {
                variables.put(variable.name(), variable);
            }
            for (CustomFunction function : document.symbols().functions()) {
                functions.put(function.name(), function);
            }
        }

        private void build() {
            collectTracks();
            for (TrackId trackId : dependencies.keySet()) {
                dependencies.put(trackId, resolveTrack(trackId));
            }
            detectCycles();
        }

        private void collectTracks() {
            for (CameraRig rig : document.rigs()) {
                for (CurveTrack channel : rig.channels()) {
                    dependencies.put(channel.id(), Set.of());
                }

                dependencies.put(rig.aim().id(), Set.of());
                dependencies.put(rig.view().active().id(), Set.of());
            }

            dependencies.put(document.cameraSource().id(), Set.of());

            for (var track : document.tracks()) {
                dependencies.put(track.id(), Set.of());
            }
        }

        private Set<TrackId> resolveTrack(TrackId trackId) {
            var track = document.track(trackId).orElse(null);

            if (!(track instanceof CurveTrack curve)) {
                return Set.of();
            }

            Set<TrackId> resolved = new LinkedHashSet<>();

            for (Keyframe key : curve.curve().keys()) {
                if (key.value() instanceof Formula formula) {
                    resolveFormula(formula.expression(), trackId, resolved,
                            new HashSet<>(), new HashSet<>(), Set.of());
                }
            }

            return Set.copyOf(resolved);
        }

        private void resolveFormula(String source,
                                    TrackId owner,
                                    Set<TrackId> out,
                                    Set<String> visitingVariables,
                                    Set<String> visitingFunctions,
                                    Set<String> locals) {
            CompiledExpression compiled = compile(source, owner, "formula");

            if (compiled == null) {
                return;
            }

            for (ExpressionReference reference : compiled.references().references()) {
                resolveReference(reference, owner, out, visitingVariables, visitingFunctions, locals);
            }
        }

        private void resolveReference(ExpressionReference reference,
                                      TrackId owner,
                                      Set<TrackId> out,
                                      Set<String> visitingVariables,
                                      Set<String> visitingFunctions,
                                      Set<String> locals) {
            switch (reference.kind()) {
                case VARIABLE -> resolveVariable(reference, owner, out,
                        visitingVariables, visitingFunctions, locals);
                case FUNCTION -> resolveFunction(reference, owner, out,
                        visitingVariables, visitingFunctions, locals);
                case SAMPLE -> {
                    // Samples are frame facts, not tracks.
                }
            }
        }

        private void resolveVariable(ExpressionReference reference,
                                     TrackId owner,
                                     Set<TrackId> out,
                                     Set<String> visitingVariables,
                                     Set<String> visitingFunctions,
                                     Set<String> locals) {
            if (locals.contains(reference.name())) {
                return;
            }

            Variable variable = variables.get(reference.name());

            if (variable == null) {
                diagnostics.add(error(owner,
                        "Formula references an unknown variable '" + reference.name() + "'"));
                return;
            }

            if (!visitingVariables.add(variable.name())) {
                diagnostics.add(error(owner,
                        "Variable cycle detected at '" + variable.name() + "'"));
                return;
            }

            try {
                ValueSource source = variable.source();

                if (source instanceof TrackRef ref) {
                    if (document.track(ref.trackId()).isEmpty()) {
                        diagnostics.add(error(owner,
                                "Variable '" + variable.name() + "' references a missing track "
                                        + ref.trackId()));
                    } else {
                        out.add(ref.trackId());
                    }
                } else if (source instanceof Formula formula) {
                    resolveFormula(formula.expression(), owner, out,
                            visitingVariables, visitingFunctions, locals);
                } else if (!(source instanceof Constant)) {
                    diagnostics.add(error(owner,
                            "Variable '" + variable.name() + "' has an unsupported value source"));
                }
            } finally {
                visitingVariables.remove(variable.name());
            }
        }

        private void resolveFunction(ExpressionReference reference,
                                     TrackId owner,
                                     Set<TrackId> out,
                                     Set<String> visitingVariables,
                                     Set<String> visitingFunctions,
                                     Set<String> locals) {
            CustomFunction function = functions.get(reference.name());

            if (function == null) {
                diagnostics.add(error(owner,
                        "Formula calls an unknown function '" + reference.name() + "'"));
                return;
            }

            if (function.arity() != reference.argumentCount()) {
                diagnostics.add(error(owner,
                        "Function '" + function.name() + "' expects " + function.arity()
                                + " arguments but the expression passes " + reference.argumentCount()));
                return;
            }

            if (!visitingFunctions.add(function.name())) {
                diagnostics.add(error(owner,
                        "Function cycle detected at '" + function.name() + "'"));
                return;
            }

            try {
                Set<String> nestedLocals = new HashSet<>(locals);
                nestedLocals.addAll(function.parameters());
                CompiledExpression compiled = compile(function.body(), owner,
                        "function '" + function.name() + "'");

                if (compiled != null) {
                    for (ExpressionReference nested : compiled.references().references()) {
                        resolveReference(nested, owner, out, visitingVariables,
                                visitingFunctions, Set.copyOf(nestedLocals));
                    }
                }
            } finally {
                visitingFunctions.remove(function.name());
            }
        }

        private @Nullable CompiledExpression compile(String source, TrackId owner, String where) {
            CompilationResult result = compiler.compile(source);

            if (result instanceof CompilationResult.Rejected rejected) {
                diagnostics.add(error(owner,
                        "Cannot compile " + where + ": " + rejected.diagnostics().getFirst().detail()));
                return null;
            }

            return result.compiledExpression().orElseThrow();
        }

        private void detectCycles() {
            Map<TrackId, Integer> colors = new HashMap<>();
            Deque<TrackId> path = new ArrayDeque<>();
            Set<String> reported = new HashSet<>();

            for (TrackId node : dependencies.keySet()) {
                visit(node, colors, path, reported);
            }
        }

        private void visit(TrackId node,
                           Map<TrackId, Integer> colors,
                           Deque<TrackId> path,
                           Set<String> reported) {
            int color = colors.getOrDefault(node, 0);

            if (color == 1) {
                List<TrackId> cycle = new ArrayList<>(path);
                Collections.reverse(cycle);
                int start = cycle.indexOf(node);
                List<TrackId> members = start < 0 ? cycle : cycle.subList(start, cycle.size());
                List<String> names = members.stream().map(TrackId::value).toList();
                String signature = String.join("->", names);

                if (reported.add(signature)) {
                    diagnostics.add(error(node,
                            "Track dependency cycle: " + String.join(" -> ", names) + " -> " + node));
                }
                return;
            }

            if (color == 2) {
                return;
            }

            colors.put(node, 1);
            path.addLast(node);

            for (TrackId dependency : dependencies.getOrDefault(node, Set.of())) {
                visit(dependency, colors, path, reported);
            }

            path.removeLast();
            colors.put(node, 2);
        }

        private Map<TrackId, Set<TrackId>> finishDependencies() {
            Map<TrackId, Set<TrackId>> copy = new LinkedHashMap<>();
            dependencies.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
            return Map.copyOf(copy);
        }

        private List<Diagnostic> finishDiagnostics() {
            return List.copyOf(diagnostics);
        }

        private Diagnostic error(TrackId trackId, String message) {
            return new Diagnostic(DiagnosticCode.EXPRESSION_INVALID, Severity.ERROR,
                    LocalizedMessage.literal(message),
                    new DiagnosticScope.Document(revision),
                    new Diagnostic.Location(trackId, "dependency"),
                    null);
        }
    }
}
