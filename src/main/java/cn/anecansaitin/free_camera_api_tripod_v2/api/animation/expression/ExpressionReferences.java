package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// 一次编译得到的全部外部引用（提交期绑定与依赖分析的输入）。
///
/// ## 用途
///
/// - **L3 校验**：变量/函数是否存在、函数实参个数是否与形参表一致、样本名是否绑定到
///   文档的采样声明（主设计 §4.2、§6.1、§7.7）。
/// - **依赖图**：依赖图从“编译后语法树”派生（主设计 §4.2），构建时读这些引用，
///   而不是重新解析文本。
/// - **重命名**：样本或变量改名时必须同步更新引用，或明确拒绝产生悬空引用。
///
/// 引用**按源码位置排序**（编译器保证），因此诊断可以按出现顺序展示；容器防御性复制。
///
/// @param references 全部外部引用，按源码位置升序
@NullMarked
public record ExpressionReferences(List<ExpressionReference> references) {

    /// 没有任何外部引用。
    public static final ExpressionReferences EMPTY = new ExpressionReferences(List.of());

    public ExpressionReferences {
        Objects.requireNonNull(references, "references");
        references = List.copyOf(references);
    }

    /// 本表达式引用的全部样本名（去重，保持首次出现顺序）。
    public List<String> sampleNames() {
        return names(ExpressionReferenceKind.SAMPLE);
    }

    /// 本表达式引用的全部变量名（去重，保持首次出现顺序）。
    public List<String> variableNames() {
        return names(ExpressionReferenceKind.VARIABLE);
    }

    /// 本表达式调用的全部函数引用（可能同名多次；参数个数可能不同）。
    public List<ExpressionReference> functions() {
        return references.stream()
                .filter(reference -> reference.kind() == ExpressionReferenceKind.FUNCTION)
                .toList();
    }

    /// 指定种类的去重名字，保持首次出现顺序。
    public List<String> names(ExpressionReferenceKind kind) {
        Set<String> seen = new java.util.LinkedHashSet<>();

        for (ExpressionReference reference : references) {
            if (reference.kind() == kind) {
                seen.add(reference.name());
            }
        }

        return List.copyOf(seen);
    }

    /// 某种引用是否出现。
    public boolean contains(ExpressionReferenceKind kind, String name) {
        return references.stream()
                .anyMatch(reference -> reference.kind() == kind && reference.name().equals(name));
    }

    /// 供依赖图使用的“函数名 -> 实参个数”集合（同名不同参数个数会保留多组）。
    public Set<String> functionSignatures() {
        return references.stream()
                .filter(reference -> reference.kind() == ExpressionReferenceKind.FUNCTION)
                .map(reference -> reference.name() + "/" + reference.argumentCount())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public boolean isEmpty() {
        return references.isEmpty();
    }
}
