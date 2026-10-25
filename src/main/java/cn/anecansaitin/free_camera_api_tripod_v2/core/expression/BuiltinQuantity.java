package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 表达式内建量（主设计 §6.1）。
///
/// 内建量由求值输入直接提供，**不是**符号表里的外部引用：编译期识别它们，但不登记
/// `ExpressionReferenceKind.VARIABLE`。取值来自
/// `api.animation.eval.ExpressionInputs` 的 `time` / `progress` / `worldTime` 字段。
///
/// 名字与语义版本属于格式的一部分：改变语义必须提升格式版本，不能让同一段文本在
/// 版本间悄悄改变结果（见 `api.animation.expression.ExpressionCompiler` 的说明）。
@NullMarked
public enum BuiltinQuantity {

    /// 文档时间 `t`，单位秒；来源为 `ExpressionInputs#time`。
    TIME("t"),

    /// 动画进度 `p`，取值范围 `[0, 1]`；来源为 `ExpressionInputs#progress`。
    PROGRESS("p"),

    /// 世界时间 `wt`；来源为 `ExpressionInputs#worldTime`。
    WORLD_TIME("wt");

    private final String symbol;

    BuiltinQuantity(String symbol) {
        this.symbol = symbol;
    }

    /// 源码中使用的名字。
    public String symbol() {
        return symbol;
    }

    /// 按源码名字查找内建量。
    ///
    /// @param symbol 源码中的标识符
    /// @return 对应内建量；不是内建量时为空
    public static @Nullable BuiltinQuantity fromSymbol(String symbol) {
        for (BuiltinQuantity quantity : values()) {
            if (quantity.symbol.equals(symbol)) {
                return quantity;
            }
        }

        return null;
    }
}
