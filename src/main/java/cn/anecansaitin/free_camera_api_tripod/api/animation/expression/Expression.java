package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/// 极简表达式求值器：四则运算、括号、正负号，以及变量与函数调用。
///
/// 支持的写法：
/// - 数字：`12`、`3.5`、`.5`
/// - 变量：字母、下划线或非 ASCII 字符开头（中文变量名可用），值由 {@link Resolver} 提供
/// - 函数：`min(a,b)`、`max(a,b)`、`sin(x)`、`cos(x)`、`random()`（0~1）、`random(a,b)`
/// - 运算符：`+ - * / %`、一元 `-`、括号分组（按算式原样从左到右，不做隐式优先级之外的处理）
///
/// 求值失败（语法错误、未知变量、参数个数不对）一律返回 {@link Float#NaN}，调用方据此回退到原来的数值。
/// 每次求值都会重新扫描字符串：表达式很短、每帧求值次数有限，省下 AST 的复杂度更划算。
public final class Expression {
    private static final Random RANDOM = new Random();

    private Expression() {
    }

    /// 求值；失败返回 NaN
    public static float evaluate(@Nullable String source, Resolver resolver) {
        if (source == null || source.isBlank()) {
            return Float.NaN;
        }

        Reader reader = new Reader(source, resolver);

        try {
            float value = reader.expression();
            reader.skipSpaces();
            return reader.end() ? value : Float.NaN;
        } catch (IllegalArgumentException e) {
            return Float.NaN;
        }
    }

    /// 只做语法与变量可见性检查，不关心具体数值（编辑表达式时用来判断能不能用）
    public static boolean valid(@Nullable String source, Resolver resolver) {
        if (source == null || source.isBlank()) {
            return false;
        }

        return !Float.isNaN(evaluate(source, resolver));
    }

    /// 这个名字能否被表达式识别成标识符。
    ///
    /// 规则与解析器里读标识符的那段完全一致：以字母、下划线或非 ASCII 字符开头，其后可跟数字。
    /// 变量名虽然不限字符集（中文可以），但要能被写进公式里引用，起名时得照这个规则来
    public static boolean validName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        char first = name.charAt(0);

        if (!(Character.isLetter(first) || first == '_' || first > 127)) {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            if (!nameChar(name.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /// 标识符里允许出现的字符：字母、数字、下划线，以及非 ASCII 字符（中文这类非拉丁文字靠它放行）
    private static boolean nameChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character > 127;
    }

    /// 一段公式里是否把 {@code name} 当作独立标识符来用。
    ///
    /// 按标识符边界匹配，避免把 `foo` 当成 `foobar` 的一部分。界面用它检查
    /// "变量绑定的轨道上有没有引用这个变量"，也就是自嵌套
    public static boolean references(@Nullable String source, String name) {
        if (source == null || name.isEmpty()) {
            return false;
        }

        int index = 0;

        while (true) {
            int at = source.indexOf(name, index);

            if (at < 0) {
                return false;
            }

            int end = at + name.length();
            boolean leftBoundary = at == 0 || !nameChar(source.charAt(at - 1));
            boolean rightBoundary = end >= source.length() || !nameChar(source.charAt(end));

            if (leftBoundary && rightBoundary) {
                return true;
            }

            index = at + 1;
        }
    }

    /// 变量取值入口；未知变量返回 NaN
    @FunctionalInterface
    public interface Resolver {
        float resolve(String name);
    }

    private static final class Reader {
        private final String source;
        private final Resolver resolver;
        private int index;

        private Reader(String source, Resolver resolver) {
            this.source = source;
            this.resolver = resolver;
        }

        private float expression() {
            float value = multiplicative();

            while (true) {
                skipSpaces();

                if (take('+')) {
                    value += multiplicative();
                } else if (take('-')) {
                    value -= multiplicative();
                } else {
                    return value;
                }
            }
        }

        private float multiplicative() {
            float value = unary();

            while (true) {
                skipSpaces();

                if (take('*')) {
                    value *= unary();
                } else if (take('/')) {
                    value /= unary();
                } else if (take('%')) {
                    value %= unary();
                } else {
                    return value;
                }
            }
        }

        private float unary() {
            skipSpaces();

            if (take('-')) {
                return -unary();
            }

            if (take('+')) {
                return unary();
            }

            return primary();
        }

        private float primary() {
            skipSpaces();

            if (take('(')) {
                float value = expression();
                skipSpaces();
                require(')');
                return value;
            }

            if (!end() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                return number();
            }

            String name = identifier();

            if (take('(')) {
                return call(name);
            }

            float value = resolver.resolve(name);

            if (Float.isNaN(value)) {
                throw new IllegalArgumentException("unknown variable: " + name);
            }

            return value;
        }

        private float call(String name) {
            List<Float> arguments = new ArrayList<>();
            skipSpaces();

            if (!take(')')) {
                do {
                    arguments.add(expression());
                    skipSpaces();
                } while (take(','));

                skipSpaces();
                require(')');
            }

            return apply(name, arguments);
        }

        private float number() {
            int start = index;

            while (!end() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                index++;
            }

            try {
                return Float.parseFloat(source.substring(start, index));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid number at " + start);
            }
        }

        private String identifier() {
            skipSpaces();
            int start = index;

            while (!end() && nameChar(source.charAt(index))) {
                index++;
            }

            if (index == start) {
                throw new IllegalArgumentException("expected a variable name at " + start);
            }

            return source.substring(start, index);
        }

        private boolean take(char expected) {
            if (!end() && source.charAt(index) == expected) {
                index++;
                return true;
            }

            return false;
        }

        private void require(char expected) {
            if (!take(expected)) {
                throw new IllegalArgumentException("expected '" + expected + "' at " + index);
            }
        }

        private void skipSpaces() {
            while (!end() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean end() {
            return index >= source.length();
        }

        private static float apply(String name, List<Float> arguments) {
            return switch (name) {
                case "min" -> binary(name, arguments, Math::min);
                case "max" -> binary(name, arguments, Math::max);
                case "sin" -> (float) java.lang.Math.sin(single(name, arguments));
                case "cos" -> (float) java.lang.Math.cos(single(name, arguments));
                case "random" -> switch (arguments.size()) {
                    case 0 -> RANDOM.nextFloat();
                    case 2 -> {
                        float from = arguments.get(0);
                        float to = arguments.get(1);
                        yield from + RANDOM.nextFloat() * (to - from);
                    }
                    default -> throw new IllegalArgumentException("random expects nothing or two values");
                };
                default -> throw new IllegalArgumentException("unknown function: " + name);
            };
        }

        private static float single(String name, List<Float> arguments) {
            if (arguments.size() != 1) {
                throw new IllegalArgumentException(name + " expects one value");
            }

            return arguments.getFirst();
        }

        private static float binary(String name, List<Float> arguments, FloatBinaryOp operator) {
            if (arguments.size() != 2) {
                throw new IllegalArgumentException(name + " expects two values");
            }

            return operator.apply(arguments.get(0), arguments.get(1));
        }
    }

    /// 两个 float 的运算；jdk 只给了 double / int / long 版本，这里自带一个
    @FunctionalInterface
    private interface FloatBinaryOp {
        float apply(float left, float right);
    }
}
