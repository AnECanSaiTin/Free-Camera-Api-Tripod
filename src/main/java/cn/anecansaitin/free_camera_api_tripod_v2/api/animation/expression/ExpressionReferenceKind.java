package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 表达式里出现的外部引用种类（主设计 §4.2、§6.1、§7.7）。
///
/// 编译器只负责把源码编译成语法树并列出**引用**，不在编译期判断名字是否存在：
///
/// - 变量与自定义函数属于文档符号表，函数参数个数要与符号表声明一致；
/// - 样本名必须绑定到文档的采样声明（静态字符串，提交期绑定）。
///
/// 这些检查由统一文档校验（L3）使用本枚举与 [ExpressionReference] 完成，
/// 编译器自身不需要机位、符号表或采样表（保持与 `api.animation` 其余部分解耦）。
@NullMarked
public enum ExpressionReferenceKind {

    /// 变量名引用。
    VARIABLE,

    /// 函数调用引用（含自定义函数；内建函数的参数个数在编译期检查）。
    FUNCTION,

    /// 样本名引用：只能来自 `sample("名字")` 或 `sample("名字", fallback)` 的静态字符串。
    SAMPLE
}
