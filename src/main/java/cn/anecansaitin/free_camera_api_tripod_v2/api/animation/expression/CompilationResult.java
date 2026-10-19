package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 编译结果：成功得到不透明句柄，或失败给出一组结构化诊断。
///
/// 失败时**不产生** [CompiledExpression]：调用方不得用“空表达式”继续求值或静默替换数值
/// （主设计 §6.1、D18）。诊断按源码位置升序，供编辑器高亮。
@NullMarked
public sealed interface CompilationResult permits CompilationResult.Compiled, CompilationResult.Rejected {

    /// 编译成功。
    ///
    /// @param expression 不透明句柄；语法树与解释器实现不属于稳定扩展接口
    record Compiled(CompiledExpression expression) implements CompilationResult {
        public Compiled {
            Objects.requireNonNull(expression, "expression");
        }
    }

    /// 编译失败：至少一条诊断。
    ///
    /// @param diagnostics 结构化诊断，非空
    record Rejected(List<ExpressionDiagnostic> diagnostics) implements CompilationResult {
        public Rejected {
            Objects.requireNonNull(diagnostics, "diagnostics");
            diagnostics = List.copyOf(diagnostics);

            if (diagnostics.isEmpty()) {
                throw new IllegalArgumentException("Rejected compilation must carry at least one diagnostic");
            }
        }
    }

    /// 成功时的句柄。
    default Optional<CompiledExpression> compiledExpression() {
        return this instanceof Compiled compiled
                ? Optional.of(compiled.expression())
                : Optional.empty();
    }

    /// 是否编译成功。
    default boolean compiled() {
        return this instanceof Compiled;
    }

    /// 全部诊断；成功时为空列表。
    default List<ExpressionDiagnostic> diagnostics() {
        return this instanceof Rejected rejected ? rejected.diagnostics() : List.of();
    }
}
