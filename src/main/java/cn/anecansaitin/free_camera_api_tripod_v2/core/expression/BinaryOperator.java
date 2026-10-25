package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;

/// 表达式的二元运算符（v1 只支持算术，不包含比较与逻辑运算）。
///
/// 优先级：`*` `/` `%` 高于 `+` `-`；同级左结合；括号改变结合顺序。
@NullMarked
public enum BinaryOperator {

    /// 加法 `a + b`。
    ADD("+"),

    /// 减法 `a - b`。
    SUBTRACT("-"),

    /// 乘法 `a * b`。
    MULTIPLY("*"),

    /// 除法 `a / b`。
    DIVIDE("/"),

    /// 取余 `a % b`。
    MODULO("%");

    private final String symbol;

    BinaryOperator(String symbol) {
        this.symbol = symbol;
    }

    /// 源码中的符号。
    public String symbol() {
        return symbol;
    }
}
