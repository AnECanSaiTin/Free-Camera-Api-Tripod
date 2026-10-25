package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 词法单元（core 内部类型）。
///
/// 除种类与半开区间外，按种类携带一个值槽：
///
/// - [ExpressionTokenType#NUMBER]：`number` 为解析后的有限数值；
/// - [ExpressionTokenType#STRING]：`string` 为反转义后的文本；
/// - 标识符与标点：只使用 `text`。
///
/// 结束 token 的区间是 `[source.length(), source.length())`，因此“缺少右括号”一类诊断可以
/// 安全地落在 EOF 零宽区间，不越界。
///
/// @param type   词法单元种类
/// @param text   源码文本（字符串 token 为反转义后的文本；结束 token 为空串）
/// @param start  源码起始下标
/// @param end    源码结束下标（不含）
/// @param number 数字值；非数字 token 为 `NaN`
/// @param string 字符串值；非字符串 token 为 null
@NullMarked
record ExpressionToken(
        ExpressionTokenType type,
        String text,
        int start,
        int end,
        double number,
        @Nullable String string) {

    public ExpressionToken {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");

        if (start < 0 || end < start) {
            throw new IllegalArgumentException(
                    "ExpressionToken span must satisfy 0 <= start <= end: [" + start + ", " + end + ")");
        }
    }

    /// 数字字面量 token。
    static ExpressionToken number(String text, int start, int end, double value) {
        return new ExpressionToken(ExpressionTokenType.NUMBER, text, start, end, value, null);
    }

    /// 标识符 token。
    static ExpressionToken identifier(String text, int start, int end) {
        return new ExpressionToken(ExpressionTokenType.IDENTIFIER, text, start, end, Double.NaN, null);
    }

    /// 字符串字面量 token；`text` 与 `string` 都保存反转义后的文本。
    static ExpressionToken string(String value, int start, int end) {
        return new ExpressionToken(ExpressionTokenType.STRING, value, start, end, Double.NaN, value);
    }

    /// 标点 / 运算符 token。
    static ExpressionToken punctuation(ExpressionTokenType type, String symbol, int start, int end) {
        return new ExpressionToken(type, symbol, start, end, Double.NaN, null);
    }

    /// 源码结束 token，零宽。
    static ExpressionToken end(int position) {
        return new ExpressionToken(ExpressionTokenType.END, "", position, position, Double.NaN, null);
    }

    /// 是否是指定种类。
    boolean is(ExpressionTokenType expected) {
        return type == expected;
    }

    /// 供诊断使用的可读描述，例如 `'+'` 或 `end of expression`。
    String describe() {
        return type == ExpressionTokenType.END ? "end of expression" : "'" + text + "'";
    }
}
