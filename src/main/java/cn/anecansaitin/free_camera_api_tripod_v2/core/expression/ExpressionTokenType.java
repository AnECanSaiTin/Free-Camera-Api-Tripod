package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;

/// 词法单元种类（core 内部类型）。
///
/// 标点与运算符共用同一批种类，由语法分析按位置决定含义；没有关键字：
/// `t` / `p` / `wt` 与内建函数名都是普通标识符，在语法分析阶段识别。
@NullMarked
enum ExpressionTokenType {

    /// 十进制数字字面量。
    NUMBER,

    /// 标识符（变量名、函数名、内建量与内建函数名）。
    IDENTIFIER,

    /// 字符串字面量；v1 只允许作为 `sample(...)` 的第一个实参。
    STRING,

    /// `+`。
    PLUS,

    /// `-`。
    MINUS,

    /// `*`。
    STAR,

    /// `/`。
    SLASH,

    /// `%`。
    PERCENT,

    /// `(`。
    LEFT_PAREN,

    /// `)`。
    RIGHT_PAREN,

    /// `,`。
    COMMA,

    /// 源码结束；区间为零宽，位于 `source.length()`。
    END
}
