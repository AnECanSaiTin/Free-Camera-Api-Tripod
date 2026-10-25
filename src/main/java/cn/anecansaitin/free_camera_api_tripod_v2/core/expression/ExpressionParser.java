package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.ExpressionDiagnosticCode;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.COMMA;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.END;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.IDENTIFIER;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.LEFT_PAREN;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.MINUS;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.NUMBER;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.PERCENT;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.PLUS;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.RIGHT_PAREN;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.SLASH;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.STAR;
import static cn.anecansaitin.free_camera_api_tripod_v2.core.expression.ExpressionTokenType.STRING;

/// 表达式语法分析器（core 内部类型）。
///
/// ## 语法（v1）
///
/// ```text
/// expression   := additive
/// additive     := multiplicative (('+' | '-') multiplicative)*
/// multiplicative := unary (('*' | '/' | '%') unary)*
/// unary        := ('+' | '-') unary | primary
/// primary      := NUMBER
///               | IDENTIFIER
///               | IDENTIFIER '(' arguments? ')'
///               | 'sample' '(' STRING (',' expression)? ')'
///               | '(' expression ')'
/// arguments    := expression (',' expression)*
/// ```
///
/// 采用优先级分层的递归下降（Pratt 风格的最小实现），同级运算符左结合；不比较、不短路、
/// 不支持赋值、属性访问、比较运算符或字符串运算。`sample` 是保留语法：只有带 `(` 时被识别，
/// 裸 `sample` 按普通变量引用处理（不判断符号表是否存在）。
///
/// ## 嵌套上限
///
/// 每进入一层括号、一层函数实参或一层一元运算计一层嵌套，超过
/// `maxNestingDepth` 报 `NESTING_TOO_DEEP`。连续同级二元运算（`1 + 1 + 1 ...`）不增加
/// 本计数：它们是扁平的左结合链，节点数由源码长度上限约束；运行期递归深度由共享
/// `EvaluationSession.maxDepth()` 另行限制。
///
/// ## 失败语义
///
/// 遇到第一条错误即抛 [ExpressionParseException]，由 [DefaultExpressionCompiler] 转成
/// `CompilationResult.Rejected`；不恢复、不继续收集多条诊断，也不产生部分语法树。
@NullMarked
final class ExpressionParser {

    /// `sample(...)` 的语法名字；保持保留字地位，不能作为自定义函数调用。
    private static final String SAMPLE_SYNTAX = "sample";

    private final int maxNestingDepth;
    private final List<ExpressionToken> tokens;

    private int position;

    /// 用一段源码构造解析器；构造时完成词法分析，语法错误在 [#parse()] 中抛出。
    ///
    /// @param source          源码文本
    /// @param maxNestingDepth 结构嵌套上限，至少 1
    ExpressionParser(String source, int maxNestingDepth) {
        Objects.requireNonNull(source, "source");

        if (maxNestingDepth < 1) {
            throw new IllegalArgumentException("maxNestingDepth must be positive: " + maxNestingDepth);
        }

        this.maxNestingDepth = maxNestingDepth;
        this.tokens = new ExpressionLexer(source).lex();
    }

    /// 解析整段源码。
    ///
    /// @return 语法树根
    /// @throws ExpressionParseException 词法或语法错误
    ExpressionAst parse() {
        ExpressionNode root = parseAdditive(0);
        ExpressionToken trailing = peek();

        if (trailing.type() != END) {
            throw syntax("unexpected token " + trailing.describe() + " after the expression", trailing);
        }

        return new ExpressionAst(root);
    }

    // region 语法层级

    /// `additive := multiplicative (('+' | '-') multiplicative)*`
    private ExpressionNode parseAdditive(int depth) {
        ExpressionNode left = parseMultiplicative(depth);

        while (peek().type() == PLUS || peek().type() == MINUS) {
            ExpressionToken operator = advance();
            ExpressionNode right = parseMultiplicative(depth);
            BinaryOperator binaryOperator = operator.type() == PLUS
                    ? BinaryOperator.ADD
                    : BinaryOperator.SUBTRACT;

            left = new ExpressionNode.Binary(binaryOperator, left, right, left.start(), right.end());
        }

        return left;
    }

    /// `multiplicative := unary (('*' | '/' | '%') unary)*`
    private ExpressionNode parseMultiplicative(int depth) {
        ExpressionNode left = parseUnary(depth);

        while (peek().type() == STAR || peek().type() == SLASH || peek().type() == PERCENT) {
            ExpressionToken operator = advance();
            ExpressionNode right = parseUnary(depth);
            BinaryOperator binaryOperator = switch (operator.type()) {
                case STAR -> BinaryOperator.MULTIPLY;
                case SLASH -> BinaryOperator.DIVIDE;
                case PERCENT -> BinaryOperator.MODULO;
                default -> throw new IllegalStateException("unreachable operator: " + operator);
            };

            left = new ExpressionNode.Binary(binaryOperator, left, right, left.start(), right.end());
        }

        return left;
    }

    /// `unary := ('+' | '-') unary | primary`
    private ExpressionNode parseUnary(int depth) {
        ExpressionToken token = peek();

        if (token.type() != PLUS && token.type() != MINUS) {
            return parsePrimary(depth);
        }

        advance();
        int operandDepth = depth + 1;
        requireDepth(operandDepth, token);
        ExpressionNode operand = parseUnary(operandDepth);
        UnaryOperator operator = token.type() == PLUS
                ? UnaryOperator.POSITIVE
                : UnaryOperator.NEGATE;

        return new ExpressionNode.Unary(operator, operand, token.start(), operand.end());
    }

    /// `primary := NUMBER | IDENTIFIER | IDENTIFIER '(' arguments? ')' | sample(...) | '(' expression ')'`
    private ExpressionNode parsePrimary(int depth) {
        ExpressionToken token = peek();

        return switch (token.type()) {
            case NUMBER -> {
                advance();
                yield new ExpressionNode.NumberLiteral(token.number(), token.start(), token.end());
            }
            case IDENTIFIER -> parseIdentifier(depth);
            case LEFT_PAREN -> parseParenthesized(token, depth);
            case STRING -> throw syntax(
                    "string literal is only allowed as the name argument of sample(...)", token);
            case RIGHT_PAREN -> throw syntax("unexpected ')'", token);
            case COMMA -> throw syntax("unexpected ','", token);
            case END -> throw syntax(
                    "unexpected end of expression; expected a number, name or '('", token);
            default -> throw syntax(
                    "unexpected operator " + token.describe() + "; expected a number, name or '('",
                    token);
        };
    }

    /// 括号分组；括号不产生独立节点，返回内部表达式。
    private ExpressionNode parseParenthesized(ExpressionToken open, int depth) {
        int innerDepth = depth + 1;
        requireDepth(innerDepth, open);
        advance();

        ExpressionNode inner = parseAdditive(innerDepth);
        expect(RIGHT_PAREN, "')' to close '('");

        return inner;
    }

    /// 标识符：裸名字是内建量或变量；后接 `(` 是函数调用（`sample` 走专用语法）。
    private ExpressionNode parseIdentifier(int depth) {
        ExpressionToken name = advance();

        if (peek().type() == LEFT_PAREN) {
            return parseCall(name, depth);
        }

        BuiltinQuantity quantity = BuiltinQuantity.fromSymbol(name.text());

        if (quantity != null) {
            return new ExpressionNode.Quantity(quantity, name.start(), name.end());
        }

        return new ExpressionNode.Variable(name.text(), name.start(), name.end());
    }

    /// 函数调用：内建函数校验实参个数，其它名字登记为 FUNCTION 引用。
    private ExpressionNode parseCall(ExpressionToken name, int depth) {
        ExpressionToken open = advance();
        int argumentDepth = depth + 1;
        requireDepth(argumentDepth, open);

        if (SAMPLE_SYNTAX.equals(name.text())) {
            return parseSampleCall(name, argumentDepth);
        }

        List<ExpressionNode> arguments = parseArguments(argumentDepth);
        ExpressionToken close = expect(RIGHT_PAREN,
                "')' to close the argument list of '" + name.text() + "'");

        BuiltinFunction function = BuiltinFunction.fromName(name.text());

        if (function == null) {
            return new ExpressionNode.Call(name.text(), name.start(), name.end(), arguments,
                    name.start(), close.end());
        }

        if (!function.accepts(arguments.size())) {
            throw diagnostic(ExpressionDiagnosticCode.BUILTIN_ARITY_MISMATCH,
                    "builtin function '" + name.text() + "' expects " + function.arityText()
                            + " but received " + arguments.size(),
                    name.start(), close.end());
        }

        return new ExpressionNode.BuiltinCall(function, arguments, name.start(), close.end());
    }

    /// `sample("名字")` / `sample("名字", fallback)`：名字必须是字符串字面量。
    ///
    /// 名字不是字符串、名字为空 / 全空白、或实参超过 2 个，都报
    /// `SAMPLE_REFERENCE_INVALID`；只有语法本身不完整（缺 `)`、尾随逗号等）才报
    /// `SYNTAX_ERROR`。
    private ExpressionNode parseSampleCall(ExpressionToken name, int argumentDepth) {
        ExpressionToken first = peek();

        if (first.type() != STRING) {
            throw diagnostic(ExpressionDiagnosticCode.SAMPLE_REFERENCE_INVALID,
                    "sample(...) requires its first argument to be a string literal name",
                    first.start(), first.end());
        }

        advance();
        String sampleName = first.string();

        if (sampleName == null || sampleName.isBlank()) {
            throw diagnostic(ExpressionDiagnosticCode.SAMPLE_REFERENCE_INVALID,
                    "sample name must not be blank",
                    first.start(), first.end());
        }

        ExpressionNode fallback = null;

        if (peek().type() == COMMA) {
            advance();
            fallback = parseAdditive(argumentDepth);
        }

        if (peek().type() == COMMA) {
            ExpressionToken extra = peek();
            throw diagnostic(ExpressionDiagnosticCode.SAMPLE_REFERENCE_INVALID,
                    "sample(...) accepts at most 2 arguments (a name and an optional fallback)",
                    extra.start(), extra.end());
        }

        ExpressionToken close = expect(RIGHT_PAREN, "')' to close sample(...)");

        return new ExpressionNode.Sample(sampleName, first.start(), first.end(), fallback,
                name.start(), close.end());
    }

    /// 函数实参列表；允许空列表，不允许尾随逗号（由 [#parseAdditive] 报语法错误）。
    private List<ExpressionNode> parseArguments(int depth) {
        if (peek().type() == RIGHT_PAREN) {
            return List.of();
        }

        List<ExpressionNode> arguments = new ArrayList<>();

        while (true) {
            arguments.add(parseAdditive(depth));

            if (peek().type() != COMMA) {
                return List.copyOf(arguments);
            }

            advance();
        }
    }

    // endregion

    // region 游标与诊断

    /// 当前 token；一定是列表中最后一个 END 之前 / 本身，不会越界。
    private ExpressionToken peek() {
        return tokens.get(position);
    }

    /// 消费当前 token 并前进。
    private ExpressionToken advance() {
        return tokens.get(position++);
    }

    /// 断言当前 token 类型并消费；否则报语法错误。
    private ExpressionToken expect(ExpressionTokenType expected, String expectation) {
        ExpressionToken token = peek();

        if (token.type() != expected) {
            throw syntax(token.type() == END
                    ? "unexpected end of expression; expected " + expectation
                    : "unexpected token " + token.describe() + "; expected " + expectation, token);
        }

        return advance();
    }

    /// 检查结构嵌套深度；超限报 `NESTING_TOO_DEEP`，区间指向进入该层的 token。
    private void requireDepth(int nextDepth, ExpressionToken token) {
        if (nextDepth <= maxNestingDepth) {
            return;
        }

        throw diagnostic(ExpressionDiagnosticCode.NESTING_TOO_DEEP,
                "expression nesting depth " + nextDepth
                        + " exceeds the limit of " + maxNestingDepth + " levels",
                token.start(), token.end());
    }

    /// 构造指向当前 token 的语法错误异常。
    private static ExpressionParseException syntax(String detail, ExpressionToken token) {
        return diagnostic(ExpressionDiagnosticCode.SYNTAX_ERROR, detail, token.start(), token.end());
    }

    /// 构造指定诊断码的异常。
    private static ExpressionParseException diagnostic(ExpressionDiagnosticCode code,
                                                       String detail,
                                                       int start,
                                                       int end) {
        return new ExpressionParseException(new ExpressionDiagnostic(code, detail, start, end));
    }

    // endregion
}
