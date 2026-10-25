package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.validation;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReference;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferenceKind;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.AnimatedNumber;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Formula;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.DocumentObjectId;
import cn.anecansaitin.free_camera_api_tripod_v2.core.expression.DefaultExpressionCompiler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 把表达式编译与外部引用绑定接入统一文档校验的 L3 层。
///
/// 本类只检查**静态可判定**的内容：源码能否编译、变量 / 函数 / 样本名是否在文档符号表或采样
/// 声明中存在、函数实参个数是否与形参表一致。它不判断公式运行期结果、不读取世界事实，
/// 也不执行预算记账。
///
/// 函数形参在函数体内是局部名字：校验函数体时把形参集合传给 [#checkFormula]，同名
/// VARIABLE 引用不会被误报为“文档变量不存在”。
@NullMarked
final class ExpressionValidation {

    private final CameraAnimation document;
    private final Revision revision;
    private final DefaultExpressionCompiler compiler = new DefaultExpressionCompiler();
    private final Set<String> variableNames = new HashSet<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    ExpressionValidation(CameraAnimation document, Revision revision) {
        this.document = document;
        this.revision = revision;

        for (Variable variable : document.symbols().variables()) {
            variableNames.add(variable.name());
        }
    }

    List<Diagnostic> validate() {
        checkSymbols();
        checkCurves();
        checkLayout();
        checkOverlays();
        return List.copyOf(diagnostics);
    }

    private void checkSymbols() {
        List<Variable> variables = document.symbols().variables();

        for (int index = 0; index < variables.size(); index++) {
            Variable variable = variables.get(index);

            if (variable.source() instanceof Formula formula) {
                checkFormula(formula.expression(), null,
                        "symbols.variables[" + index + "].source", Set.of());
            }
        }

        List<CustomFunction> functions = document.symbols().functions();

        for (int index = 0; index < functions.size(); index++) {
            CustomFunction function = functions.get(index);
            checkFormula(function.body(), null,
                    "symbols.functions[" + index + "].body", Set.copyOf(function.parameters()));
        }
    }

    private void checkCurves() {
        for (int rigIndex = 0; rigIndex < document.rigs().size(); rigIndex++) {
            CameraRig rig = document.rigs().get(rigIndex);

            for (int channelIndex = 0; channelIndex < rig.channels().size(); channelIndex++) {
                CurveTrack track = rig.channels().get(channelIndex);

                for (int keyIndex = 0; keyIndex < track.curve().keys().size(); keyIndex++) {
                    Keyframe key = track.curve().keys().get(keyIndex);

                    if (key.value() instanceof Formula formula) {
                        checkFormula(formula.expression(), track.id(),
                                "rigs[" + rigIndex + "].channels[" + channelIndex
                                        + "].curve.keys[" + keyIndex + "].value", Set.of());
                    }
                }
            }
        }
    }

    private void checkLayout() {
        for (int rigIndex = 0; rigIndex < document.rigs().size(); rigIndex++) {
            CameraRig rig = document.rigs().get(rigIndex);
            String prefix = "rigs[" + rigIndex + "].view.rect.";

            switch (rig.view().rect()) {
                case cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView.Rect.Normalized n -> {
                    checkAnimated(n.x(), rig.id(), prefix + "x");
                    checkAnimated(n.y(), rig.id(), prefix + "y");
                    checkAnimated(n.width(), rig.id(), prefix + "width");
                    checkAnimated(n.height(), rig.id(), prefix + "height");
                }
                case cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView.Rect.Anchored a -> {
                    checkAnimated(a.x(), rig.id(), prefix + "x");
                    checkAnimated(a.y(), rig.id(), prefix + "y");
                    checkAnimated(a.width(), rig.id(), prefix + "width");
                    checkAnimated(a.height(), rig.id(), prefix + "height");
                }
            }
        }
    }

    private void checkAnimated(AnimatedNumber number, DocumentObjectId objectId, String fieldPath) {
        switch (number) {
            case AnimatedNumber.Value value -> {
                if (value.source() instanceof Formula formula) {
                    checkFormula(formula.expression(), objectId, fieldPath, Set.of());
                }
            }
            case AnimatedNumber.Keyed keyed -> {
                for (int keyIndex = 0; keyIndex < keyed.curve().keys().size(); keyIndex++) {
                    Keyframe key = keyed.curve().keys().get(keyIndex);

                    if (key.value() instanceof Formula formula) {
                        checkFormula(formula.expression(), objectId,
                                fieldPath + ".curve.keys[" + keyIndex + "].value", Set.of());
                    }
                }
            }
        }
    }

    private void checkOverlays() {
        for (int index = 0; index < document.overlays().size(); index++) {
            var overlay = document.overlays().get(index);

            if (overlay.opacity() instanceof Formula formula) {
                checkFormula(formula.expression(), overlay.id(),
                        "overlays[" + index + "].opacity", Set.of());
            }
        }
    }

    private void checkFormula(String source,
                              @Nullable DocumentObjectId objectId,
                              String fieldPath,
                              Set<String> localNames) {
        CompilationResult result = compiler.compile(source);

        if (result instanceof CompilationResult.Rejected rejected) {
            ExpressionDiagnostic expression = rejected.diagnostics().getFirst();
            diagnostics.add(error(objectId, fieldPath,
                    "Expression cannot compile (" + expression.code() + " at ["
                            + expression.start() + ", " + expression.end() + ")): "
                            + expression.detail()));
            return;
        }

        CompiledExpression compiled = result.compiledExpression().orElseThrow();

        for (ExpressionReference reference : compiled.references().references()) {
            switch (reference.kind()) {
                case VARIABLE -> {
                    if (!localNames.contains(reference.name())
                            && !variableNames.contains(reference.name())) {
                        diagnostics.add(error(objectId, fieldPath,
                                "Expression references an unknown variable '"
                                        + reference.name() + "'"));
                    }
                }
                case FUNCTION -> checkFunctionReference(reference, objectId, fieldPath);
                case SAMPLE -> {
                    boolean bound = document.samples().stream()
                            .anyMatch(sample -> sample.name().equals(reference.name()));

                    if (!bound) {
                        diagnostics.add(error(objectId, fieldPath,
                                "Expression references an unbound sample '"
                                        + reference.name() + "'"));
                    }
                }
            }
        }
    }

    private void checkFunctionReference(ExpressionReference reference,
                                        @Nullable DocumentObjectId objectId,
                                        String fieldPath) {
        CustomFunction function = document.symbols().function(reference.name()).orElse(null);

        if (function == null) {
            diagnostics.add(error(objectId, fieldPath,
                    "Expression calls an unknown function '" + reference.name() + "'"));
            return;
        }

        if (function.arity() != reference.argumentCount()) {
            diagnostics.add(error(objectId, fieldPath,
                    "Function '" + reference.name() + "' expects " + function.arity()
                            + " arguments but the expression passes " + reference.argumentCount()));
        }
    }

    private Diagnostic error(@Nullable DocumentObjectId objectId, String fieldPath, String message) {
        return new Diagnostic(DiagnosticCode.EXPRESSION_INVALID, Severity.ERROR,
                LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision),
                new Diagnostic.Location(objectId, fieldPath), null);
    }
}
