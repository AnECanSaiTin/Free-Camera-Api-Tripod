package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Constant;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.TrackRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.CompiledExpressionImpl;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.DefaultExpressionCompiler;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 轨道池符号表校验：变量/函数名字、公式引用、函数元数、采样名与 TrackRef。
///
/// 只做静态绑定，不执行公式；循环引用目前由共享求值会话/依赖图阶段处理。
@NullMarked
final class TrackPoolSymbolValidator {

    private static final Set<String> RESERVED = Set.of("t", "p", "wt");
    private static final DefaultExpressionCompiler COMPILER = new DefaultExpressionCompiler();

    List<Diagnostic> validate(TrackPoolAnimation document, Revision revision) {
        Symbols symbols = document.symbols();
        Map<String, Variable> variables = new LinkedHashMap<>();
        Map<String, CustomFunction> functions = new LinkedHashMap<>();
        Set<String> sampleNames = new HashSet<>();
        document.samples().forEach(sample -> sampleNames.add(sample.name()));
        List<Diagnostic> diagnostics = new java.util.ArrayList<>();

        for (Variable variable : symbols.variables()) {
            if (RESERVED.contains(variable.name())) {
                diagnostics.add(error(revision, "symbols",
                        "Variable name is reserved: " + variable.name()));
            }

            if (variables.putIfAbsent(variable.name(), variable) != null) {
                diagnostics.add(error(revision, "symbols",
                        "Duplicate variable name: " + variable.name()));
            }
        }

        for (CustomFunction function : symbols.functions()) {
            if (functions.putIfAbsent(function.name(), function) != null) {
                diagnostics.add(error(revision, "symbols",
                        "Duplicate function name: " + function.name()));
            }
        }

        for (Variable variable : symbols.variables()) {
            String path = "symbols.variables[" + variable.name() + "]";
            String expression = switch (variable.source()) {
                case Constant ignored -> null;
                case Formula formula -> formula.expression();
                case TrackRef ref -> {
                    AnimationTrack track = document.track(ref.trackId()).orElse(null);

                    if (!(track instanceof CurveTrack)) {
                        diagnostics.add(error(revision, path,
                                "Variable TrackRef must point to a curve track: " + ref.trackId()));
                    }

                    yield null;
                }
            };

            if (expression != null) {
                validateExpression(expression, path, variables, functions, sampleNames,
                        Set.of(), revision, diagnostics);
            }
        }

        for (CustomFunction function : symbols.functions()) {
            validateExpression(function.body(),
                    "symbols.functions[" + function.name() + "]", variables, functions,
                    sampleNames, Set.copyOf(function.parameters()), revision, diagnostics);
        }

        detectCycles(variables, functions, revision, diagnostics);
        return List.copyOf(diagnostics);
    }

    private void detectCycles(Map<String, Variable> variables,
                              Map<String, CustomFunction> functions,
                              Revision revision,
                              List<Diagnostic> diagnostics) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();

        for (Variable variable : variables.values()) {
            if (variable.source() instanceof Formula formula) {
                edges.put("v:" + variable.name(), referencesOf(formula.expression(), Set.of()));
            }
        }

        for (CustomFunction function : functions.values()) {
            edges.put("f:" + function.name(),
                    referencesOf(function.body(), Set.copyOf(function.parameters())));
        }

        Map<String, Integer> colors = new HashMap<>();
        Set<String> reported = new HashSet<>();

        for (String node : edges.keySet()) {
            visit(node, edges, colors, reported, revision, diagnostics);
        }
    }

    private void visit(String node,
                       Map<String, Set<String>> edges,
                       Map<String, Integer> colors,
                       Set<String> reported,
                       Revision revision,
                       List<Diagnostic> diagnostics) {
        int color = colors.getOrDefault(node, 0);

        if (color == 2) {
            return;
        }

        if (color == 1) {
            if (reported.add(node)) {
                diagnostics.add(new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                        LocalizedMessage.literal("Symbol dependency cycle includes: " + node),
                        new DiagnosticScope.Document(revision),
                        new Diagnostic.Location(null, "symbols"), null));
            }

            return;
        }

        colors.put(node, 1);

        for (String target : edges.getOrDefault(node, Set.of())) {
            visit(target, edges, colors, reported, revision, diagnostics);
        }

        colors.put(node, 2);
    }

    private Set<String> referencesOf(String expression, Set<String> locals) {
        CompilationResult result = COMPILER.compile(expression);

        if (!(result instanceof CompilationResult.Compiled compiled)
                || !(compiled.expression() instanceof CompiledExpressionImpl implementation)) {
            return Set.of();
        }

        Set<String> targets = new java.util.LinkedHashSet<>();

        for (String variable : implementation.references().variableNames()) {
            if (!locals.contains(variable)) {
                targets.add("v:" + variable);
            }
        }

        for (var function : implementation.references().functions()) {
            targets.add("f:" + function.name());
        }

        return Set.copyOf(targets);
    }

    private void validateExpression(String expression,
                                    String path,
                                    Map<String, Variable> variables,
                                    Map<String, CustomFunction> functions,
                                    Set<String> samples,
                                    Set<String> locals,
                                    Revision revision,
                                    List<Diagnostic> diagnostics) {
        CompilationResult result = COMPILER.compile(expression);

        if (result instanceof CompilationResult.Rejected rejected) {
            diagnostics.add(error(revision, path, rejected.diagnostics().isEmpty()
                    ? "Expression compilation failed"
                    : rejected.diagnostics().getFirst().detail()));
            return;
        }

        if (!(result instanceof CompilationResult.Compiled compiled)
                || !(compiled.expression() instanceof CompiledExpressionImpl implementation)) {
            diagnostics.add(error(revision, path, "Unsupported compiled expression"));
            return;
        }

        var references = implementation.references();

        for (String variable : references.variableNames()) {
            if (!locals.contains(variable)
                    && (!variables.containsKey(variable) || RESERVED.contains(variable))) {
                diagnostics.add(error(revision, path, "Unknown variable: " + variable));
            }
        }

        for (var reference : references.functions()) {
            CustomFunction function = functions.get(reference.name());

            if (function == null) {
                diagnostics.add(error(revision, path, "Unknown function: " + reference.name()));
            } else if (function.parameters().size() != reference.argumentCount()) {
                diagnostics.add(error(revision, path,
                        "Function " + reference.name() + " expects "
                                + function.parameters().size() + " arguments, got "
                                + reference.argumentCount()));
            }
        }

        for (String sample : references.sampleNames()) {
            if (!samples.contains(sample)) {
                diagnostics.add(error(revision, path, "Unknown sample: " + sample));
            }
        }
    }

    private static Diagnostic error(Revision revision, String path, String message) {
        return new Diagnostic(DiagnosticCode.BROKEN_REFERENCE, Severity.ERROR,
                LocalizedMessage.literal(message), new DiagnosticScope.Document(revision),
                new Diagnostic.Location(null, path), null);
    }
}
