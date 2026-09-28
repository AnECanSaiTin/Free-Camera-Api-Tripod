package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// 极简表达式求值器：四则运算、括号、正负号，以及变量与函数调用。
///
/// 支持的写法：
/// - 数字：`12`、`3.5`、`.5`
/// - 变量：字母、下划线或非 ASCII 字符开头（中文变量名可用），值由 [Resolver] 提供
/// - 内置函数：见 [BUILTIN_FUNCTIONS]
/// - 自定义函数：名字不在内置清单里时，调用时向 [Resolver#function] 查一次（见 [CustomFunction]）
/// - 运算符：`+ - * / %`、一元 `-`、括号分组
///
/// 公式先**编译**成 [Formula]（一棵由闭包拼起来的语法树）再缓存，同一段文本只解析一次；
/// 求值失败（语法错误、未知变量、参数个数不对）一律返回 [Float#NaN]，由调用方回退到固定数值。
/// 未知变量不抛异常而是让 NaN 顺着算术传播，这样 `min(V, 3)` 这种写法也能按预期失败。
public final class Expression {
    /// 编译结果缓存上限；超出后整体清空——公式总量很小，没必要做精细淘汰
    private static final int CACHE_LIMIT = 512;
    private static final Map<String, Formula> CACHE = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    /// 内置函数清单：界面据此列出可插入的模板，函数名就是 `(` 之前的那一段
    public static final List<String> BUILTIN_FUNCTIONS = List.of(
            "min(a, b)", "max(a, b)", "clamp(x, lo, hi)", "lerp(a, b, t)", "smoothstep(e0, e1, x)",
            "abs(x)", "sign(x)", "floor(x)", "ceil(x)", "round(x)", "sqrt(x)",
            "pow(a, b)", "mod(a, b)",
            "sin(x)", "cos(x)", "tan(x)", "asin(x)", "acos(x)", "atan(x)", "atan2(y, x)",
            "exp(x)", "log(x)", "log10(x)",
            "random()", "random(a, b)");

    /// 内置函数名字；自定义函数不能取这些名字，否则调用永远落在内置实现上
    private static final Set<String> BUILTIN_FUNCTION_NAMES = builtinFunctionNames();

    private static Set<String> builtinFunctionNames() {
        Set<String> names = new LinkedHashSet<>();

        for (String signature : BUILTIN_FUNCTIONS) {
            int at = signature.indexOf('(');
            names.add(at < 0 ? signature : signature.substring(0, at));
        }

        return Set.copyOf(names);
    }

    /// 名字是否是内置函数
    public static boolean isBuiltinFunction(String name) {
        return BUILTIN_FUNCTION_NAMES.contains(name);
    }

    private Expression() {
    }

    /// 编译后的公式：语法树已经固化成闭包，重复求值不再扫描字符串
    @FunctionalInterface
    public interface Formula {
        float evaluate(Resolver resolver);
    }

    /// 变量取值入口；未知变量返回 NaN
    @FunctionalInterface
    public interface Resolver {
        float resolve(String name);

        /// 自定义函数查询；没有重写时一律当作"不存在"。函数名不在内置清单里时调用时查这里
        default @Nullable CustomFunction function(String name) {
            return null;
        }
    }

    // region 编译与求值

    /// 编译一段公式；空串或语法错误返回 null
    public static @Nullable Formula compile(@Nullable String source) {
        if (source == null || source.isBlank()) {
            return null;
        }

        Formula cached = CACHE.get(source);

        if (cached != null) {
            return cached;
        }

        Formula formula = parse(source);

        if (formula == null) {
            return null;
        }

        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }

        CACHE.put(source, formula);
        return formula;
    }

    /// 求值一段公式；失败返回 NaN
    public static float evaluate(@Nullable String source, Resolver resolver) {
        Formula formula = compile(source);
        return formula == null ? Float.NaN : evaluate(formula, resolver);
    }

    /// 求值已经编译好的公式；求值中出错（例如变量取值时抛异常）返回 NaN
    public static float evaluate(Formula formula, Resolver resolver) {
        try {
            return formula.evaluate(resolver);
        } catch (RuntimeException e) {
            return Float.NaN;
        }
    }

    /// 只做语法检查，不关心具体数值（编辑公式时用来判断能不能用）
    public static boolean valid(@Nullable String source) {
        return compile(source) != null;
    }

    private static @Nullable Formula parse(String source) {
        Reader reader = new Reader(source);

        try {
            Formula formula = reader.expression();
            reader.skipSpaces();
            return reader.end() ? formula : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // endregion

    // region 名字分析

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

    /// 扫出一段公式里出现的全部标识符（用于变量依赖分析）
    public static Set<String> identifiers(@Nullable String source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }

        Set<String> names = new LinkedHashSet<>();
        int index = 0;

        while (index < source.length()) {
            if (!nameChar(source.charAt(index))) {
                index++;
                continue;
            }

            int start = index;

            while (index < source.length() && nameChar(source.charAt(index))) {
                index++;
            }

            names.add(source.substring(start, index));
        }

        return names;
    }

    /// 一段公式里是否把 [name] 当作独立标识符来用。
    ///
    /// 按标识符边界匹配，避免把 `foo` 当成 `foobar` 的一部分；与 [identifiers] 同一套字符规则，
    /// 只是这里要按关键帧逐条扫，所以不建集合、早点退出
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

    /// 标识符里允许出现的字符：字母、数字、下划线，以及非 ASCII 字符（中文这类非拉丁文字靠它放行）
    private static boolean nameChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character > 127;
    }

    // endregion

    /// 递归下降解析：每解析出一段就返回一个闭包，算式的层次由闭包的嵌套体现
    private static final class Reader {
        private final String source;
        private int index;

        private Reader(String source) {
            this.source = source;
        }

        /// 加减
        private Formula expression() {
            Formula left = multiplicative();

            while (true) {
                skipSpaces();
                Formula leftValue = left;

                if (take('+')) {
                    Formula right = multiplicative();
                    left = resolver -> leftValue.evaluate(resolver) + right.evaluate(resolver);
                } else if (take('-')) {
                    Formula right = multiplicative();
                    left = resolver -> leftValue.evaluate(resolver) - right.evaluate(resolver);
                } else {
                    return left;
                }
            }
        }

        /// 乘除模
        private Formula multiplicative() {
            Formula left = unary();

            while (true) {
                skipSpaces();
                Formula leftValue = left;

                if (take('*')) {
                    Formula right = unary();
                    left = resolver -> leftValue.evaluate(resolver) * right.evaluate(resolver);
                } else if (take('/')) {
                    Formula right = unary();
                    left = resolver -> leftValue.evaluate(resolver) / right.evaluate(resolver);
                } else if (take('%')) {
                    Formula right = unary();
                    left = resolver -> leftValue.evaluate(resolver) % right.evaluate(resolver);
                } else {
                    return left;
                }
            }
        }

        /// 一元正负
        private Formula unary() {
            skipSpaces();

            if (take('-')) {
                Formula value = unary();
                return resolver -> -value.evaluate(resolver);
            }

            if (take('+')) {
                return unary();
            }

            return primary();
        }

        /// 括号 / 数字 / 变量 / 函数调用
        private Formula primary() {
            skipSpaces();

            if (take('(')) {
                Formula value = expression();
                skipSpaces();
                require(')');
                return value;
            }

            if (!end() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                float number = number();
                return resolver -> number;
            }

            String name = identifier();

            if (take('(')) {
                return call(name);
            }

            return resolver -> resolver.resolve(name);
        }

        private Formula call(String name) {
            List<Formula> arguments = new ArrayList<>();
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
    }

    /// 函数：参数个数不对在编译期就报错（返回 null），不必等到求值。
    ///
    /// 名字不在内置清单里时不判错，而是生成一个**运行期**节点去查 [Resolver#function]：
    /// 自定义函数随时可加，编译期不该把它当成语法错误
    private static Formula apply(String name, List<Formula> arguments) {
        return switch (name) {
            case "min" -> binary(name, arguments, Math::min);
            case "max" -> binary(name, arguments, Math::max);
            case "pow" -> binary(name, arguments, (base, exponent) -> (float) Math.pow(base, exponent));
            case "mod" -> binary(name, arguments, (a, b) -> a % b);
            case "atan2" -> binary(name, arguments, (y, x) -> (float) Math.atan2(y, x));
            case "abs" -> unary(name, arguments, Math::abs);
            case "sign" -> unary(name, arguments, Math::signum);
            case "floor" -> unary(name, arguments, value -> (float) Math.floor(value));
            case "ceil" -> unary(name, arguments, value -> (float) Math.ceil(value));
            case "round" -> unary(name, arguments, value -> (float) Math.round(value));
            case "sqrt" -> unary(name, arguments, value -> (float) Math.sqrt(value));
            case "sin" -> unary(name, arguments, value -> (float) Math.sin(value));
            case "cos" -> unary(name, arguments, value -> (float) Math.cos(value));
            case "tan" -> unary(name, arguments, value -> (float) Math.tan(value));
            case "asin" -> unary(name, arguments, value -> (float) Math.asin(value));
            case "acos" -> unary(name, arguments, value -> (float) Math.acos(value));
            case "atan" -> unary(name, arguments, value -> (float) Math.atan(value));
            case "exp" -> unary(name, arguments, value -> (float) Math.exp(value));
            case "log" -> unary(name, arguments, value -> (float) Math.log(value));
            case "log10" -> unary(name, arguments, value -> (float) Math.log10(value));
            case "clamp" -> {
                requireArguments(name, arguments, 3);
                Formula value = arguments.get(0);
                Formula low = arguments.get(1);
                Formula high = arguments.get(2);
                yield resolver -> Math.clamp(value.evaluate(resolver), low.evaluate(resolver), high.evaluate(resolver));
            }
            case "lerp" -> {
                requireArguments(name, arguments, 3);
                Formula from = arguments.get(0);
                Formula to = arguments.get(1);
                Formula ratio = arguments.get(2);
                yield resolver -> {
                    float start = from.evaluate(resolver);
                    return start + (to.evaluate(resolver) - start) * ratio.evaluate(resolver);
                };
            }
            case "smoothstep" -> {
                requireArguments(name, arguments, 3);
                Formula edge0 = arguments.get(0);
                Formula edge1 = arguments.get(1);
                Formula value = arguments.get(2);
                yield resolver -> {
                    float t = Math.clamp((value.evaluate(resolver) - edge0.evaluate(resolver))
                            / (edge1.evaluate(resolver) - edge0.evaluate(resolver)), 0f, 1f);
                    return t * t * (3 - 2 * t);
                };
            }
            case "random" -> switch (arguments.size()) {
                case 0 -> _ -> RANDOM.nextFloat();
                case 2 -> resolver -> {
                    float from = arguments.get(0).evaluate(resolver);
                    float to = arguments.get(1).evaluate(resolver);
                    return from + RANDOM.nextFloat() * (to - from);
                };
                default -> throw new IllegalArgumentException("random expects nothing or two values");
            };
            default -> resolver -> {
                CustomFunction function = resolver.function(name);

                // 函数还没定义、或实参个数与形参不符：给 NaN，由调用方回退到固定数值
                if (function == null || function.parameters().size() != arguments.size()) {
                    return Float.NaN;
                }

                return function.invoke(arguments, resolver);
            };
        };
    }

    /// 一元内置函数的算子
    @FunctionalInterface
    private interface FloatOperator {
        float apply(float value);
    }

    /// 二元内置函数的算子
    @FunctionalInterface
    private interface FloatPairOperator {
        float apply(float first, float second);
    }

    /// 一元内置函数
    private static Formula unary(String name, List<Formula> arguments, FloatOperator operator) {
        requireArguments(name, arguments, 1);
        Formula argument = arguments.get(0);
        return resolver -> operator.apply(argument.evaluate(resolver));
    }

    /// 二元内置函数
    private static Formula binary(String name, List<Formula> arguments, FloatPairOperator operator) {
        requireArguments(name, arguments, 2);
        Formula first = arguments.get(0);
        Formula second = arguments.get(1);
        return resolver -> operator.apply(first.evaluate(resolver), second.evaluate(resolver));
    }

    private static void requireArguments(String name, List<Formula> arguments, int count) {
        if (arguments.size() != count) {
            throw new IllegalArgumentException(name + " expects " + count + " value(s)");
        }
    }
}
