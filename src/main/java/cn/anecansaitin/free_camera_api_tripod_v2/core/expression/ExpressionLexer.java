package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 表达式词法分析器（core 内部类型）。
///
/// ## 支持的词法
///
/// - 十进制数字：`123`、`1.5`、`1.`、`1e9`、`2.5E-3`。必须由数字开头（`.5` 不接受）；
///   指数部分必须至少有一位数字；数值必须有限，`1e999` 报 `SYNTAX_ERROR`；
/// - 标识符：ASCII `[A-Za-z_][A-Za-z0-9_]*`。中文等非 ASCII 字符按“意外字符”报错，
///   表达式名字因此不会因 Unicode 归一化产生歧义；
/// - 字符串字面量：双引号包围，支持 `\\`、`\"`、`\n`、`\r`、`\t` 转义；不许跨行；
/// - 标点与运算符：`+ - * / % ( ) ,`；
/// - 空白：`Character.isWhitespace` 跳过；不产生 token。
///
/// 词法错误统一抛 [ExpressionParseException]，带 `SYNTAX_ERROR` 与精确区间；解释器尚未实现，
/// 因此这里不做任何求值。
@NullMarked
final class ExpressionLexer {

    private final String source;
    private final List<ExpressionToken> tokens = new ArrayList<>();

    private int index;

    /// 用一段源码构造词法分析器；不做任何预处理，不修改源码。
    ///
    /// @param source 源码文本
    ExpressionLexer(String source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /// 扫描全部 token，末尾追加零宽结束 token。
    ///
    /// @return 不可变 token 列表，最后一项必为 [ExpressionTokenType#END]
    List<ExpressionToken> lex() {
        while (index < source.length()) {
            char current = source.charAt(index);

            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }

            if (isDigit(current)) {
                lexNumber();
                continue;
            }

            if (isIdentifierStart(current)) {
                lexIdentifier();
                continue;
            }

            if (current == '"') {
                lexString();
                continue;
            }

            lexPunctuation(current);
        }

        tokens.add(ExpressionToken.end(source.length()));
        return List.copyOf(tokens);
    }

    /// 扫描数字字面量；畸形数字在词法阶段报错。
    private void lexNumber() {
        int start = index;
        skipDigits();

        if (index < source.length() && source.charAt(index) == '.') {
            index++;
            skipDigits();
        }

        if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
            int exponentStart = index;
            index++;

            if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                index++;
            }

            int exponentDigitsStart = index;
            skipDigits();

            if (index == exponentDigitsStart) {
                throw syntax("numeric literal exponent must contain at least one digit",
                        exponentStart, index);
            }
        }

        int end = index;

        // `1.2.3`、`12abc` 一类：数字后面紧跟名字字符或第二个小数点，按畸形数字报错，
        // 而不是拆成两个 token 让调用方看到难以理解的“意外 token”。
        if (index < source.length()
                && (isIdentifierStart(source.charAt(index)) || source.charAt(index) == '.')) {
            int malformedEnd = Math.min(source.length(), index + 1);
            throw syntax("malformed numeric literal '" + source.substring(start, malformedEnd) + "'",
                    start, malformedEnd);
        }

        String text = source.substring(start, end);
        double value;

        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw syntax("malformed numeric literal '" + text + "'", start, end);
        }

        if (!Double.isFinite(value)) {
            throw syntax("numeric literal '" + text + "' is not finite", start, end);
        }

        tokens.add(ExpressionToken.number(text, start, end, value));
    }

    /// 扫描标识符；只接受 ASCII，避免不同 Unicode 归一化写法产生同名歧义。
    private void lexIdentifier() {
        int start = index;
        index++;

        while (index < source.length() && isIdentifierPart(source.charAt(index))) {
            index++;
        }

        tokens.add(ExpressionToken.identifier(source.substring(start, index), start, index));
    }

    /// 扫描字符串字面量；支持少量固定转义，不跨行。
    private void lexString() {
        int start = index;
        index++;

        StringBuilder value = new StringBuilder();

        while (index < source.length()) {
            char current = source.charAt(index);

            if (current == '"') {
                index++;
                tokens.add(ExpressionToken.string(value.toString(), start, index));
                return;
            }

            if (current == '\\') {
                int escapeStart = index;
                index++;

                if (index >= source.length()) {
                    break;
                }

                char escaped = source.charAt(index);

                switch (escaped) {
                    case '\\' -> value.append('\\');
                    case '"' -> value.append('"');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    default -> throw syntax(
                            "unsupported escape '\\" + escaped + "' in string literal",
                            escapeStart, index + 1);
                }

                index++;
                continue;
            }

            if (current == '\n' || current == '\r') {
                throw syntax("string literal is not terminated before the end of line", start, index);
            }

            value.append(current);
            index++;
        }

        throw syntax("string literal is not terminated", start, source.length());
    }

    /// 扫描标点 / 运算符；未知字符报 `SYNTAX_ERROR`。
    private void lexPunctuation(char current) {
        ExpressionTokenType type = switch (current) {
            case '+' -> ExpressionTokenType.PLUS;
            case '-' -> ExpressionTokenType.MINUS;
            case '*' -> ExpressionTokenType.STAR;
            case '/' -> ExpressionTokenType.SLASH;
            case '%' -> ExpressionTokenType.PERCENT;
            case '(' -> ExpressionTokenType.LEFT_PAREN;
            case ')' -> ExpressionTokenType.RIGHT_PAREN;
            case ',' -> ExpressionTokenType.COMMA;
            default -> null;
        };

        if (type == null) {
            throw unexpectedCharacter();
        }

        int start = index++;
        tokens.add(ExpressionToken.punctuation(type, String.valueOf(current), start, index));
    }

    /// 未知道字符的错误；按码点计算区间，代理对不会被截断。
    private ExpressionParseException unexpectedCharacter() {
        int codePoint = source.codePointAt(index);
        int start = index;
        int end = index + Character.charCount(codePoint);

        throw syntax("unexpected character " + describeCodePoint(codePoint), start, end);
    }

    /// 跳过一个或多个十进制数字。
    private void skipDigits() {
        while (index < source.length() && isDigit(source.charAt(index))) {
            index++;
        }
    }

    /// 构造 `SYNTAX_ERROR` 诊断异常。
    private static ExpressionParseException syntax(String detail, int start, int end) {
        return new ExpressionParseException(new ExpressionDiagnostic(
                ExpressionDiagnosticCode.SYNTAX_ERROR, detail, start, end));
    }

    /// 可打印字符加引号，控制字符用 `U+XXXX` 表示。
    private static String describeCodePoint(int codePoint) {
        if (codePoint >= 0x20 && codePoint != 0x7f) {
            return "'" + Character.toString(codePoint) + "'";
        }

        return String.format("'\\u%04X'", codePoint);
    }

    /// ASCII 数字。
    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    /// ASCII 标识符首字符：字母或下划线。
    private static boolean isIdentifierStart(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || character == '_';
    }

    /// ASCII 标识符后续字符：字母、数字或下划线。
    private static boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || isDigit(character);
    }
}
