package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/// 内置函数表：名字、允许的参数个数、算子。
///
/// **参数个数在编译期校验**（[Compiler] 建 [Node.Builtin] 时用 [Builtin#accepts] 查一次），
/// 所以个数写错与语法错误同一种后果：整条公式编译不过、界面据此标红。这与自定义函数的
/// "运行期查表、个数不符给 `NaN`"是两种口径，和拆分之前完全一致。
///
/// 表驱动而不是一大段 `switch`：`BUILTIN_FUNCTIONS` 那份签名清单与实现在这里合成一处，
/// 界面列出的模板与真正算得出来的函数不会再各改各的。
@NullMarked
final class Builtins {
    /// 界面据此列出可插入的模板，函数名就是 `(` 之前的那一段
    static final List<String> SIGNATURES = List.of(
            "min(a, b)", "max(a, b)", "clamp(x, lo, hi)", "lerp(a, b, t)", "smoothstep(e0, e1, x)",
            "abs(x)", "sign(x)", "floor(x)", "ceil(x)", "round(x)", "sqrt(x)",
            "pow(a, b)", "mod(a, b)",
            "sin(x)", "cos(x)", "tan(x)", "asin(x)", "acos(x)", "atan(x)", "atan2(y, x)",
            "exp(x)", "log(x)", "log10(x)",
            "random()", "random(a, b)");

    private static final Map<String, Builtin> BY_NAME = build();

    private Builtins() {
    }

    /// 一个内置函数：名字、允许的参数个数（`random` 是 0 或 2）、算子，以及能不能被常量折叠。
    ///
    /// 算子拿到的是已经求好值的定长数组，长度必是 [Builtin#arities] 里的一个
    record Builtin(String name, int[] arities, Operator operator, boolean foldable) {
        boolean accepts(int count) {
            for (int arity : arities) {
                if (arity == count) {
                    return true;
                }
            }

            return false;
        }
    }

    @FunctionalInterface
    interface Operator {
        float apply(float[] arguments);
    }

    static boolean isBuiltin(String name) {
        return BY_NAME.containsKey(name);
    }

    static @Nullable Builtin get(String name) {
        return BY_NAME.get(name);
    }

    /// 参数个数不符时给调用者的说明（编译期就抛出去，公式判为不合法）
    static String arityText(Builtin builtin) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < builtin.arities().length; i++) {
            if (i > 0) {
                text.append(" or ");
            }

            text.append(builtin.arities()[i]);
        }

        return builtin.name() + " expects " + text + " value(s)";
    }

    private static Map<String, Builtin> build() {
        Map<String, Builtin> functions = new LinkedHashMap<>();

        add(functions, "min", (arguments -> Math.min(arguments[0], arguments[1])), 2);
        add(functions, "max", (arguments -> Math.max(arguments[0], arguments[1])), 2);
        add(functions, "mod", (arguments -> arguments[0] % arguments[1]), 2);

        add(functions, "abs", (arguments -> Math.abs(arguments[0])), 1);
        add(functions, "sign", (arguments -> Math.signum(arguments[0])), 1);
        add(functions, "floor", (arguments -> (float) Math.floor(arguments[0])), 1);
        add(functions, "ceil", (arguments -> (float) Math.ceil(arguments[0])), 1);
        add(functions, "round", (arguments -> Math.round(arguments[0])), 1);
        // sqrt 是 IEEE 754 要求正确舍入的五个运算之一，跨平台逐位一致，可以折
        add(functions, "sqrt", (arguments -> (float) Math.sqrt(arguments[0])), 1);

        add(functions, "clamp", (arguments ->
                Math.clamp(arguments[0], arguments[1], arguments[2])), 3);

        add(functions, "lerp", (arguments -> {
            float from = arguments[0];
            return from + (arguments[1] - from) * arguments[2];
        }), 3);

        add(functions, "smoothstep", (arguments -> {
            float edge0 = arguments[0];
            float edge1 = arguments[1];
            float t = Math.clamp((arguments[2] - edge0) / (edge1 - edge0), 0f, 1f);
            return t * t * (3 - 2 * t);
        }), 3);

        // 以下不参与常量折叠：`Math.pow` 与超越函数只保证 1 ulp 内、实现可因平台而异，
        // 折了就等于把值钉死在"编译这段文本的那个 JVM"上
        addUnfoldable(functions, "pow", (arguments -> (float) Math.pow(arguments[0], arguments[1])), 2);
        addUnfoldable(functions, "atan2", (arguments -> (float) Math.atan2(arguments[0], arguments[1])), 2);

        addUnfoldable(functions, "sin", (arguments -> (float) Math.sin(arguments[0])), 1);
        addUnfoldable(functions, "cos", (arguments -> (float) Math.cos(arguments[0])), 1);
        addUnfoldable(functions, "tan", (arguments -> (float) Math.tan(arguments[0])), 1);
        addUnfoldable(functions, "asin", (arguments -> (float) Math.asin(arguments[0])), 1);
        addUnfoldable(functions, "acos", (arguments -> (float) Math.acos(arguments[0])), 1);
        addUnfoldable(functions, "atan", (arguments -> (float) Math.atan(arguments[0])), 1);
        addUnfoldable(functions, "exp", (arguments -> (float) Math.exp(arguments[0])), 1);
        addUnfoldable(functions, "log", (arguments -> (float) Math.log(arguments[0])), 1);
        addUnfoldable(functions, "log10", (arguments -> (float) Math.log10(arguments[0])), 1);

        // 线程级随机源：公式在同一帧里可能被多个线程求值（客户端画曲线、服务端播放），
        // 共用一个 Random 是没必要的共享状态。**绝不能折叠**，否则"随机"会固化成编译那一刻的一个数
        addUnfoldable(functions, "random", (arguments -> arguments.length == 0
                ? ThreadLocalRandom.current().nextFloat()
                : arguments[0] + ThreadLocalRandom.current().nextFloat() * (arguments[1] - arguments[0])), 0, 2);

        return Map.copyOf(functions);
    }

    /// 可折叠的内置函数：结果只由实参决定，且跨平台逐位可复现
    private static void add(Map<String, Builtin> functions, String name, Operator operator, int... arities) {
        functions.put(name, new Builtin(name, arities, operator, true));
    }

    /// 不折叠的内置函数：不确定，或结果允许因平台而异
    private static void addUnfoldable(Map<String, Builtin> functions, String name, Operator operator, int... arities) {
        functions.put(name, new Builtin(name, arities, operator, false));
    }
}
