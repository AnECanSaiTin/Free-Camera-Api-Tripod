package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 编译过程中的内部失败（core 内部类型）。
///
/// 词法与语法分析遇到第一条错误就把结构化诊断包成受检语义的异常抛出，由
/// [DefaultExpressionCompiler] 统一转成 `CompilationResult.Rejected`。第一版不恢复、不继续
/// 收集多条诊断；调用方永远看不到本异常。
///
/// 关闭栈追踪：编译失败是正常的用户输入错误，不需要异常栈；诊断自带源码区间。
@NullMarked
final class ExpressionParseException extends RuntimeException {

    private final ExpressionDiagnostic diagnostic;

    /// 用一条结构化诊断构造。
    ///
    /// @param diagnostic 诊断；非 null
    ExpressionParseException(ExpressionDiagnostic diagnostic) {
        super(diagnostic.code() + ": " + diagnostic.detail(), null, false, false);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    /// 本次失败的结构化诊断。
    ExpressionDiagnostic diagnostic() {
        return diagnostic;
    }
}
