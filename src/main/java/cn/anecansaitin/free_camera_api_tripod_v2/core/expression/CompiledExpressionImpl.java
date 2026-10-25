package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.CompiledExpression;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReference;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionReferences;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// 第一版编译产物：不可变源码 + core 内部语法树 + 外部引用（core 实现类型）。
///
/// ## 不可变值
///
/// 三个字段全部是深不可变值：源码字符串、record 语法树、防御性复制的引用列表。因此实例
/// 可以跨线程共享，也可以作为后续编译缓存的值（本版尚未加入缓存）。`equals` / `hashCode`
/// 按源码与语法树结构实现，同一段源码重复编译得到相等但不保证同一引用的产物。
///
/// ## AST 访问
///
/// [ast()] 是 core 内部入口，供后续 `ExpressionEvaluator` 实现使用；api 契约
/// [CompiledExpression] 仍只暴露 [source()] 与 [references()]，语法树不进入 api 包。
///
/// ## 引用登记规则
///
/// 引用从语法树派生，保证与编译结果一致：
///
/// - [ExpressionNode.Variable] -> `VARIABLE`，区间为标识符；
/// - [ExpressionNode.Call] -> `FUNCTION`，带实参个数，区间为函数名标识符；
/// - [ExpressionNode.Sample] -> `SAMPLE`，区间为字符串字面量 token（含引号）；
/// - 内建量、内建函数、数字字面量与运算符不登记引用。
///
/// 结果按 `start` 升序、同起点按 `end` 升序排序。函数引用的区间只覆盖名字而不是整个调用，
/// 便于改名与高亮；整个调用区间保留在语法树节点的 `start()` / `end()` 上。
@NullMarked
public final class CompiledExpressionImpl implements CompiledExpression {

    private final String source;
    private final ExpressionAst ast;
    private final ExpressionReferences references;

    /// 由 [DefaultExpressionCompiler] 使用；调用方不应绕过编译器直接构造。
    ///
    /// @param source     编译时的源码
    /// @param ast        语法树
    /// @param references 已按源码位置排序的引用
    CompiledExpressionImpl(String source, ExpressionAst ast, ExpressionReferences references) {
        this.source = Objects.requireNonNull(source, "source");
        this.ast = Objects.requireNonNull(ast, "ast");
        this.references = Objects.requireNonNull(references, "references");
    }

    /// 从源码与语法树构造产物，并派生引用列表。
    ///
    /// @param source 编译时的源码
    /// @param ast    语法树
    /// @return 不可变编译产物
    static CompiledExpressionImpl of(String source, ExpressionAst ast) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(ast, "ast");

        List<ExpressionReference> collected = new ArrayList<>();
        collectReferences(ast.root(), collected);
        collected.sort(Comparator.comparingInt(ExpressionReference::start)
                .thenComparingInt(ExpressionReference::end));

        return new CompiledExpressionImpl(source, ast, new ExpressionReferences(collected));
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public ExpressionReferences references() {
        return references;
    }

    /// core 内部语法树入口；供后续求值器与依赖分析使用，不属于 api 契约。
    public ExpressionAst ast() {
        return ast;
    }

    /// 从语法树派生引用；顺序为源码先序，随后由调用方稳定排序。
    private static void collectReferences(ExpressionNode node, List<ExpressionReference> collected) {
        switch (node) {
            case ExpressionNode.NumberLiteral ignored -> {
            }
            case ExpressionNode.Quantity ignored -> {
            }
            case ExpressionNode.Variable variable -> collected.add(
                    ExpressionReference.variable(variable.name(), variable.start(), variable.end()));
            case ExpressionNode.Unary unary -> collectReferences(unary.operand(), collected);
            case ExpressionNode.Binary binary -> {
                collectReferences(binary.left(), collected);
                collectReferences(binary.right(), collected);
            }
            case ExpressionNode.BuiltinCall call -> call.arguments()
                    .forEach(argument -> collectReferences(argument, collected));
            case ExpressionNode.Call call -> {
                collected.add(ExpressionReference.function(call.name(), call.arguments().size(),
                        call.nameStart(), call.nameEnd()));
                call.arguments().forEach(argument -> collectReferences(argument, collected));
            }
            case ExpressionNode.Sample sample -> {
                collected.add(ExpressionReference.sample(sample.name(),
                        sample.nameStart(), sample.nameEnd()));

                if (sample.fallback() != null) {
                    collectReferences(sample.fallback(), collected);
                }
            }
        }
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || (other instanceof CompiledExpressionImpl that
                && source.equals(that.source)
                && ast.equals(that.ast));
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, ast);
    }

    @Override
    public String toString() {
        return "CompiledExpressionImpl[source=" + source + "]";
    }
}
