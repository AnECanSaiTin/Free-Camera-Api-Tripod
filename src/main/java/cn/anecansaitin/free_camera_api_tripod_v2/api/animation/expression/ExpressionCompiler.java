package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 表达式编译服务（主设计 §6.1、D7）。
///
/// ## 契约
///
/// 1. **纯编译**：只读源码，不访问世界、不读文档、不产生副作用。
/// 2. **失败不产生句柄**：语法错误、内建函数实参个数不符、`sample` 名字不是静态字面量等
///    返回 [CompilationResult.Rejected]；不得返回“可求值但会失败”的句柄。
/// 3. **编译缓存**：实现按**源码文本**缓存编译结果（有界 LRU，主设计 §3.4）。
///    同一文本只解析一次；缓存不改变返回值语义，也不把缓存身份泄露给调用方。
/// 4. **作用域检查不在编译期**：变量/自定义函数是否存在、实参个数是否匹配符号表、
///    样本名是否绑定到采样声明，均由统一文档校验（L3）使用
///    [CompiledExpression#references] 完成（主设计 §4.2、§6.1、§7.7）。
/// 5. **不公开语法树**：实现内部 AST、解释器与求值细节不属于稳定扩展接口。
///
/// ## 内建量与内建函数（语义以设计 §6.1 为准）
///
/// 编译期必须认识并校验：
///
/// - 内建量：`t`（文档时间，秒）、`p`（动画进度）、`wt`（世界时间）；
/// - 内建函数：`step`、惰性 `select(cond, a, b)`（只求值选中分支，静态仍检查两分支）、
///   `noise(seed, t)`（确定性、版本化）、`random()`（明确非确定）、常用数学函数。
///
/// 内建函数与内建量的**名字与语义版本**属于格式的一部分：改变语义必须提升格式版本，
/// 不能让同一段文本在版本间悄悄改变结果（主设计 §6.1、§8.1）。
@NullMarked
@FunctionalInterface
public interface ExpressionCompiler {

    /// 编译一段表达式源码。
    ///
    /// @param source 源码文本；空白源码是否合法由实现按文档规则判定（应返回结构化诊断，
    ///               不抛异常）
    /// @return 成功句柄或结构化诊断；不得返回“部分成功”
    CompilationResult compile(String source);
}
