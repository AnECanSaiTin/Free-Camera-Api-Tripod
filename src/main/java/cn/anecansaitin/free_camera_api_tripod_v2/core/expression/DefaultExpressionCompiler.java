package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompilationResult;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionCompiler;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.SOURCE_TOO_LARGE;
import static cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode.SYNTAX_ERROR;

/// 第一版表达式编译器：只编译、不求值（主设计 §6.1、§7.7）。
///
/// ## 语法
///
/// ```text
/// expression     := additive
/// additive       := multiplicative (('+' | '-') multiplicative)*
/// multiplicative := unary (('*' | '/' | '%') unary)*
/// unary          := ('+' | '-') unary | primary
/// primary        := NUMBER
///                 | IDENTIFIER
///                 | IDENTIFIER '(' arguments? ')'
///                 | 'sample' '(' STRING (',' expression)? ')'
///                 | '(' expression ')'
/// arguments      := expression (',' expression)*
/// ```
///
/// - 数字：十进制整数 / 小数 / 科学计数法；必须由数字开头（`.5` 不接受），数值必须有限；
/// - 一元 `+` / `-`，二元 `+ - * / %`，括号；`* / %` 优先于 `+ -`，同级左结合；
/// - 函数调用 `name(arg1, arg2, ...)`；
/// - 内建量 `t` / `p` / `wt`：识别为内建量，**不**登记外部变量引用；
/// - 内建函数：[BuiltinFunction] 中的名字，编译期检查实参个数，不登记 FUNCTION 引用；
/// - `sample("静态名字")` / `sample("静态名字", fallback)`：名字必须是字符串字面量，登记为
///   [cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferenceKind#SAMPLE]；
/// - 其它标识符：后接 `(` 登记为 FUNCTION 引用（带实参个数），否则登记为 VARIABLE 引用。
///
/// 编译器**不判断**变量 / 自定义函数是否存在于符号表，也不判断样本名是否绑定到采样声明：
/// 这些由统一文档校验使用 [CompiledExpressionImpl#references()] 完成。
///
/// ## 预算与上限
///
/// - [MAX_SOURCE_LENGTH]：源码最多 4096 个 UTF-16 字符；超出返回 `SOURCE_TOO_LARGE`，
///   区间覆盖整段源码。上限同时约束语法树规模。
/// - [MAX_NESTING_DEPTH]：结构嵌套最多 128 层，按括号、函数实参、`sample` 默认值与一元运算
///   计层；超出返回 `NESTING_TOO_DEEP`。连续同级二元运算不增加嵌套层数，其运行期递归深度
///   由共享 `EvaluationSession.maxDepth()` 另行限制。
///
/// ## 失败与诊断
///
/// - 空白（含空串）源码返回 `SYNTAX_ERROR`；失败**不产生**可求值句柄。
/// - 第一版遇到第一条错误即停止：`Rejected` 只带一条诊断，不恢复、不收集多条。
/// - 诊断区间为 `[start, end)`；EOF 相关错误落在零宽区间 `[source.length(), source.length())`，
///   不会越界。空白源码的区间是 `[0, source.length())`。
/// - 诊断码：`SYNTAX_ERROR`、`SOURCE_TOO_LARGE`、`NESTING_TOO_DEEP`、
///   `BUILTIN_ARITY_MISMATCH`、`SAMPLE_REFERENCE_INVALID`。
///
/// ## 缓存
///
/// 本版**不做编译缓存**。若后续加入，必须按源码文本使用有界 LRU（接口说明第 3 条），并保持
/// 返回值语义不变；当前实现每次调用都重新词法与语法分析。
///
/// ## 求值
///
/// 求值器尚未实现：本类只产出 [CompiledExpressionImpl]，不执行任何运算。`step` / 惰性
/// `select` / `noise` / `random` 的数值语义、作用域绑定与预算记账都属于后续
/// `api.animation.eval.ExpressionEvaluator` 实现。
@NullMarked
public final class DefaultExpressionCompiler implements ExpressionCompiler {

    /// 源码长度上限（UTF-16 字符数）。
    public static final int MAX_SOURCE_LENGTH = 4096;

    /// 结构嵌套层数上限（括号 / 函数实参 / 一元运算）。
    public static final int MAX_NESTING_DEPTH = 128;

    /// 创建无状态编译器；实例可复用、可并发调用。
    public DefaultExpressionCompiler() {
    }

    @Override
    public CompilationResult compile(String source) {
        Objects.requireNonNull(source, "source");

        if (source.length() > MAX_SOURCE_LENGTH) {
            return rejected(ExpressionDiagnostic.overSource(SOURCE_TOO_LARGE,
                    "expression source length " + source.length()
                            + " exceeds the limit of " + MAX_SOURCE_LENGTH + " characters",
                    source.length()));
        }

        if (source.isBlank()) {
            return rejected(ExpressionDiagnostic.overSource(SYNTAX_ERROR,
                    "expression source is blank", source.length()));
        }

        try {
            ExpressionAst ast = new ExpressionParser(source, MAX_NESTING_DEPTH).parse();
            return new CompilationResult.Compiled(CompiledExpressionImpl.of(source, ast));
        } catch (ExpressionParseException exception) {
            return rejected(exception.diagnostic());
        }
    }

    /// 单条诊断的失败结果。
    private static CompilationResult rejected(ExpressionDiagnostic diagnostic) {
        return new CompilationResult.Rejected(List.of(diagnostic));
    }
}
