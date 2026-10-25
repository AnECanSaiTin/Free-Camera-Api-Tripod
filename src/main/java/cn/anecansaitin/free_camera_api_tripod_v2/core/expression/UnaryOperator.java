package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;

/// 表达式的一元运算符（v1 只支持符号与取反）。
///
/// 命名避开 `ExpressionTokenType.PLUS` / `MINUS`，以免解析器里的枚举常量重名。
@NullMarked
public enum UnaryOperator {

    /// 一元加号 `+x`：不改变数值。
    POSITIVE("+"),

    /// 一元减号 `-x`：取相反数。
    NEGATE("-");

    private final String symbol;

    UnaryOperator(String symbol) {
        this.symbol = symbol;
    }

    /// 源码中的符号。
    public String symbol() {
        return symbol;
    }
}
