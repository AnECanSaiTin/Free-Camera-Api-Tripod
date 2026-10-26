package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
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
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.ValueSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId;
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
/// ## 覆盖范围
///
/// 构建时遍历**完整候选**的所有公式入口：轨道关键帧、变量定义、函数体、布局矩形分量与
/// 叠加透明度。因此即使一个变量没有被任何轨道引用，它自身的递归也能被发现。构建时同时
/// 报告未定义变量 / 函数、函数实参个数不符、变量 / 函数递归环与轨道依赖环。
///
/// ## 作用域
///
/// 展开必须与 [CoreExpressionEvaluator] 的词法作用域一致：函数形参只对本次函数体可见，
/// 调用实参在调用方作用域展开；进入全局变量定义后局部表为空。调用方形参不得泄漏到被调
/// 用函数体或全局变量公式，否则真实轨道环会被漏检（R56-H1）。
///
/// [diagnostics] 包含全部诊断；[cycleDiagnostics] 只返回环诊断，供统一校验 L3 在保留
/// 既有表达式引用检查的同时接入环检查，不重复报告未定义引用与 arity（主设计 §4.2 L3）。
@NullMarked
public final class CoreDependencyGraph implements DependencyGraph {

    private final Map<TrackId, Set<TrackId>> dependencies;
    private final List<Diagnostic> diagnostics;
    private final List<Diagnostic> cycleDiagnostics;

    private CoreDependencyGraph(Map<TrackId, Set<TrackId>> dependencies,
                                List<Diagnostic> diagnostics,
                                List<Diagnostic> cycleDiagnostics) {
        this.dependencies = dependencies;
        this.diagnostics = diagnostics;
        this.cycleDiagnostics = cycleDiagnostics;
    }

    /// 从已发布快照构建依赖图。
    ///
    /// @param snapshot 已发布快照；诊断 scope 使用其 revision
    public static CoreDependencyGraph build(AnimationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return build(snapshot.document(), snapshot.revision());
    }

    /// 从候选文档与将使用的发布序号构建依赖图；不读取当前 store。
    ///
    /// @param document 完整候选
    /// @param revision 候选将发布的序号；用于诊断作用域
    public static CoreDependencyGraph build(CameraAnimation document, Revision revision) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(revision, "revision");

        Builder builder = new Builder(document, revision);
        builder.build();
        return new CoreDependencyGraph(builder.finishDependencies(),
                builder.finishDiagnostics(), builder.finishCycleDiagnostics());
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

    /// 只包含依赖环的诊断；提交 / 载入的强制校验接入这一部分（主设计 §4.2 L3）。
    public List<Diagnostic> cycleDiagnostics() {
        return cycleDiagnostics;
    }

    private static final class Builder {
        private final CameraAnimation document;
        private final Revision revision;
        private final DefaultExpressionCompiler compiler = new DefaultExpressionCompiler();
        private final Map<TrackId, Set<TrackId>> dependencies = new LinkedHashMap<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final List<Diagnostic> cycleDiagnostics = new ArrayList<>();
        private final Set<String> reportedCycles = new HashSet<>();
        private final Map<String, Variable> variables = new HashMap<>();
        private final Map<String, CustomFunction> functions = new HashMap<>();

        private Builder(CameraAnimation document, Revision revision) {
            this.document = document;
            this.revision = revision;

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

            // 符号表与布局不是轨道节点，但必须作为公式根遍历：未被轨道引用的递归变量、
            // 布局公式中的环同样要在发布前拒绝（主设计 §4.2 L3）。
            resolveSymbols();
            resolveLayouts();
            resolveOverlays();
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

        // region 轨道

        private Set<TrackId> resolveTrack(TrackId trackId) {
            var track = document.track(trackId).orElse(null);

            if (!(track instanceof CurveTrack curve)) {
                return Set.of();
            }

            Set<TrackId> resolved = new LinkedHashSet<>();

            for (int index = 0; index < curve.curve().keys().size(); index++) {
                Keyframe key = curve.curve().keys().get(index);

                if (key.value() instanceof Formula formula) {
                    resolveFormula(formula.expression(), trackId,
                            "curve.keys[" + index + "].value", resolved,
                            new HashSet<>(), new HashSet<>(), Set.of());
                }
            }

            return Set.copyOf(resolved);
        }

        // endregion

        // region 符号 / 布局 / 叠加

        private void resolveSymbols() {
            List<Variable> symbols = document.symbols().variables();

            for (int index = 0; index < symbols.size(); index++) {
                Variable variable = symbols.get(index);
                String path = "symbols.variables[" + index + "]";

                if (variable.source() instanceof Formula formula) {
                    resolveFormula(formula.expression(), null, path + ".source",
                            new LinkedHashSet<>(), new HashSet<>(), new HashSet<>(), Set.of());
                } else if (variable.source() instanceof TrackRef ref
                        && document.track(ref.trackId()).isEmpty()) {
                    diagnostics.add(error(null, path + ".source",
                            "Variable '" + variable.name() + "' references a missing track "
                                    + ref.trackId()));
                }
            }

            List<CustomFunction> customFunctions = document.symbols().functions();

            for (int index = 0; index < customFunctions.size(); index++) {
                CustomFunction function = customFunctions.get(index);
                Set<String> visitingFunctions = new HashSet<>();
                visitingFunctions.add(function.name());
                resolveFunctionBody(function, null,
                        "symbols.functions[" + index + "].body",
                        new LinkedHashSet<>(), new HashSet<>(), visitingFunctions);
            }
        }

        private void resolveLayouts() {
            for (int rigIndex = 0; rigIndex < document.rigs().size(); rigIndex++) {
                CameraRig rig = document.rigs().get(rigIndex);
                String path = "rigs[" + rigIndex + "].view.rect";

                switch (rig.view().rect()) {
                    case RigView.Rect.Normalized normalized -> {
                        resolveAnimatedKeyIds(normalized.x(), rig, path + ".x");
                        resolveAnimatedKeyIds(normalized.y(), rig, path + ".y");
                        resolveAnimatedKeyIds(normalized.width(), rig, path + ".width");
                        resolveAnimatedKeyIds(normalized.height(), rig, path + ".height");
                    }
                    case RigView.Rect.Anchored anchored -> {
                        resolveAnimatedKeyIds(anchored.x(), rig, path + ".x");
                        resolveAnimatedKeyIds(anchored.y(), rig, path + ".y");
                        resolveAnimatedKeyIds(anchored.width(), rig, path + ".width");
                        resolveAnimatedKeyIds(anchored.height(), rig, path + ".height");
                    }
                }
            }
        }

        private void resolveAnimatedKeyIds(AnimatedNumber number, CameraRig rig, String fieldPath) {
            switch (number) {
                case AnimatedNumber.Value value -> {
                    if (value.source() instanceof Formula formula) {
                        resolveFormula(formula.expression(), rig.id(), fieldPath + ".source",
                                new LinkedHashSet<>(), new HashSet<>(), new HashSet<>(), Set.of());
                    }
                }
                case AnimatedNumber.Keyed keyed -> resolveCurveFormulas(keyed.curve(), rig.id(),
                        fieldPath + ".curve");
            }
        }

        private void resolveCurveFormulas(Curve curve,
                                          DocumentObjectId objectId,
                                          String fieldPath) {
            for (int index = 0; index < curve.keys().size(); index++) {
                Keyframe key = curve.keys().get(index);

                if (key.value() instanceof Formula formula) {
                    resolveFormula(formula.expression(), objectId,
                            fieldPath + ".keys[" + index + "].value",
                            new LinkedHashSet<>(), new HashSet<>(), new HashSet<>(), Set.of());
                }
            }
        }

        private void resolveOverlays() {
            for (int index = 0; index < document.overlays().size(); index++) {
                var overlay = document.overlays().get(index);

                if (overlay.opacity() instanceof Formula formula) {
                    resolveFormula(formula.expression(), overlay.id(),
                            "overlays[" + index + "].opacity",
                            new LinkedHashSet<>(), new HashSet<>(), new HashSet<>(), Set.of());
                }
            }
        }

        // endregion

        private void resolveFormula(String source,
                                    @Nullable DocumentObjectId objectId,
                                    String fieldPath,
                                    Set<TrackId> out,
                                    Set<String> visitingVariables,
                                    Set<String> visitingFunctions,
                                    Set<String> locals) {
            CompiledExpression compiled = compile(source, objectId, fieldPath, "formula");

            if (compiled == null) {
                return;
            }

            for (ExpressionReference reference : compiled.references().references()) {
                resolveReference(reference, objectId, fieldPath, out,
                        visitingVariables, visitingFunctions, locals);
            }
        }

        private void resolveReference(ExpressionReference reference,
                                      @Nullable DocumentObjectId objectId,
                                      String fieldPath,
                                      Set<TrackId> out,
                                      Set<String> visitingVariables,
                                      Set<String> visitingFunctions,
                                      Set<String> locals) {
            switch (reference.kind()) {
                case VARIABLE -> resolveVariable(reference, objectId, fieldPath, out,
                        visitingVariables, visitingFunctions, locals);
                case FUNCTION -> resolveFunction(reference, objectId, fieldPath, out,
                        visitingVariables, visitingFunctions);
                case SAMPLE -> {
                    // Samples are frame facts, not tracks.
                }
            }
        }

        private void resolveVariable(ExpressionReference reference,
                                     @Nullable DocumentObjectId objectId,
                                     String fieldPath,
                                     Set<TrackId> out,
                                     Set<String> visitingVariables,
                                     Set<String> visitingFunctions,
                                     Set<String> locals) {
            if (locals.contains(reference.name())) {
                return;
            }

            Variable variable = variables.get(reference.name());

            if (variable == null) {
                diagnostics.add(error(objectId, fieldPath,
                        "Formula references an unknown variable '" + reference.name() + "'"));
                return;
            }

            if (!visitingVariables.add(variable.name())) {
                cycleError(objectId, fieldPath,
                        "Variable cycle detected at '" + variable.name() + "'");
                return;
            }

            try {
                ValueSource source = variable.source();

                if (source instanceof TrackRef ref) {
                    if (document.track(ref.trackId()).isEmpty()) {
                        diagnostics.add(error(objectId, fieldPath,
                                "Variable '" + variable.name() + "' references a missing track "
                                        + ref.trackId()));
                    } else {
                        out.add(ref.trackId());
                    }
                } else if (source instanceof Formula formula) {
                    // 变量定义属于文档级（全局）作用域：即使从函数体引用，也不能看见
                    // 调用方或任何函数体的形参，与 CoreExpressionEvaluator 一致。
                    resolveFormula(formula.expression(), objectId, fieldPath, out,
                            visitingVariables, visitingFunctions, Set.of());
                } else if (!(source instanceof Constant)) {
                    diagnostics.add(error(objectId, fieldPath,
                            "Variable '" + variable.name() + "' has an unsupported value source"));
                }
            } finally {
                visitingVariables.remove(variable.name());
            }
        }

        private void resolveFunction(ExpressionReference reference,
                                     @Nullable DocumentObjectId objectId,
                                     String fieldPath,
                                     Set<TrackId> out,
                                     Set<String> visitingVariables,
                                     Set<String> visitingFunctions) {
            CustomFunction function = functions.get(reference.name());

            if (function == null) {
                diagnostics.add(error(objectId, fieldPath,
                        "Formula calls an unknown function '" + reference.name() + "'"));
                return;
            }

            if (function.arity() != reference.argumentCount()) {
                diagnostics.add(error(objectId, fieldPath,
                        "Function '" + function.name() + "' expects " + function.arity()
                                + " arguments but the expression passes " + reference.argumentCount()));
                return;
            }

            if (!visitingFunctions.add(function.name())) {
                cycleError(objectId, fieldPath,
                        "Function cycle detected at '" + function.name() + "'");
                return;
            }

            try {
                resolveFunctionBody(function, objectId, fieldPath, out,
                        visitingVariables, visitingFunctions);
            } finally {
                visitingFunctions.remove(function.name());
            }
        }

        private void resolveFunctionBody(CustomFunction function,
                                         @Nullable DocumentObjectId objectId,
                                         String fieldPath,
                                         Set<TrackId> out,
                                         Set<String> visitingVariables,
                                         Set<String> visitingFunctions) {
            // 函数体是独立词法作用域：只看到自己的形参，不继承调用方局部名。
            // 调用实参属于调用方作用域，已由 resolveFormula 按调用方的 locals 展开。
            Set<String> locals = Set.copyOf(function.parameters());
            CompiledExpression compiled = compile(function.body(), objectId, fieldPath,
                    "function '" + function.name() + "'");

            if (compiled != null) {
                for (ExpressionReference nested : compiled.references().references()) {
                    resolveReference(nested, objectId, fieldPath, out, visitingVariables,
                            visitingFunctions, locals);
                }
            }
        }

        private @Nullable CompiledExpression compile(String source,
                                                     @Nullable DocumentObjectId objectId,
                                                     String fieldPath,
                                                     String where) {
            CompilationResult result = compiler.compile(source);

            if (result instanceof CompilationResult.Rejected rejected) {
                diagnostics.add(error(objectId, fieldPath,
                        "Cannot compile " + where + ": " + rejected.diagnostics().getFirst().detail()));
                return null;
            }

            return result.compiledExpression().orElseThrow();
        }

        private void detectCycles() {
            Map<TrackId, Integer> colors = new HashMap<>();
            Deque<TrackId> path = new ArrayDeque<>();

            for (TrackId node : dependencies.keySet()) {
                visit(node, colors, path);
            }
        }

        private void visit(TrackId node,
                           Map<TrackId, Integer> colors,
                           Deque<TrackId> path) {
            int color = colors.getOrDefault(node, 0);

            if (color == 1) {
                List<TrackId> cycle = new ArrayList<>(path);
                Collections.reverse(cycle);
                int start = cycle.indexOf(node);
                List<TrackId> members = start < 0 ? cycle : cycle.subList(start, cycle.size());
                List<String> names = members.stream().map(TrackId::value).toList();

                cycleError(node, "dependency",
                        "Track dependency cycle: " + String.join(" -> ", names) + " -> " + node);
                return;
            }

            if (color == 2) {
                return;
            }

            colors.put(node, 1);
            path.addLast(node);

            for (TrackId dependency : dependencies.getOrDefault(node, Set.of())) {
                visit(dependency, colors, path);
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

        private List<Diagnostic> finishCycleDiagnostics() {
            return List.copyOf(cycleDiagnostics);
        }

        private Diagnostic error(@Nullable DocumentObjectId objectId,
                                 String fieldPath,
                                 String message) {
            return new Diagnostic(DiagnosticCode.EXPRESSION_INVALID, Severity.ERROR,
                    LocalizedMessage.literal(message),
                    new DiagnosticScope.Document(revision),
                    new Diagnostic.Location(objectId, fieldPath),
                    null);
        }

        private void cycleError(@Nullable DocumentObjectId objectId,
                                String fieldPath,
                                String message) {
            if (!reportedCycles.add(message)) {
                return;
            }

            Diagnostic diagnostic = error(objectId, fieldPath, message);
            diagnostics.add(diagnostic);
            cycleDiagnostics.add(diagnostic);
        }
    }
}
