package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// 公式门面：编译、求值、引用分析。**编译与求值是两件事，本类只做门面**。
///
/// 拆分前这里同时是解析器、求值入口与缓存持有者，而且编译产物是闭包——闭包既是树又是求值器，
/// 于是"求值入口唯一"只能靠约定（`CustomFunction.invoke` 里两处直调闭包就是这么漏出去的）。
/// 现在各归各位：
///
/// | 职责 | 在哪 |
/// | --- | --- |
/// | 文本 → [Node] 树、编译缓存、编译期上限、常量折叠 | [Compiler] |
/// | 内置函数表（名字 / 参数个数 / 算子 / 能不能折） | [Builtins] |
/// | 解释 [Node] 树 | [Evaluator] |
/// | 一次求值的会话：解析器、参数帧 | [Evaluation] |
/// | 求值深度栈 | [EvalDepth] |
/// | 对外入口与引用分析（[Expression#names]） | 本类 |
///
/// 对调用方而言唯一的变化是 [Expression#compile] 不再公开（[Node] 是包内类型）：
/// 需要"这段公式能不能用"就调 [Expression#valid]，语义与原来的 `compile(...) == null` 一致。
/// 想知道"这段公式引用了谁"，走 [Expression#names]——它看的是语法树，不再扫文本。
///
/// 支持的写法：数字（`12` / `3.5` / `.5`）、变量（字母、下划线或非 ASCII 开头，值由 [Resolver] 提供）、
/// 内置函数（见 [BUILTIN_FUNCTIONS]）、自定义函数（名字不在内置清单里时向 [Resolver#function] 查一次）、
/// `+ - * / %`、一元 `-`、括号分组。
///
/// 求值失败（语法错误、未知变量、函数参数个数不对、深度超限）一律返回 [Float#NaN]，
/// 由调用方回退到固定数值。未知变量不抛异常而是让 NaN 顺着算术传播，这样 `min(V, 3)` 也能按预期失败。
///
/// 编译缓存是**全局**的、按公式文本驻留：`Formula`（数值来源里的那个 record）因此不必自己带着
/// 语法树走，序列化与拷贝都不会捎上编译产物，"同一段文本只解析一次"也从"每个字段各存一份"
/// 变成了"整个进程一份"。
@NullMarked
public final class Expression {
    /// 内置函数清单：界面据此列出可插入的模板，函数名就是 `(` 之前的那一段
    public static final List<String> BUILTIN_FUNCTIONS = Builtins.SIGNATURES;

    private Expression() {
    }

    // region 编译与求值

    /// 这段公式能否编译；界面用它判断能不能保存（与 [Compiler] 的两条上限同一判据）
    public static boolean valid(@Nullable String source) {
        return Compiler.compile(source) != null;
    }

    /// 求值一段公式；编译不过或求值出错一律返回 [Float#NaN]
    public static float evaluate(@Nullable String source, Resolver resolver) {
        Node node = Compiler.compile(source);
        return node == null ? Float.NaN : evaluate(node, resolver);
    }

    /// 求值已经编译好的公式
    static float evaluate(Node node, Resolver resolver) {
        return Evaluation.root(resolver).expression(node);
    }

    /// 编译一段公式；空串、语法错误、超上限返回 `null`。
    ///
    /// 包内可见：编译产物 [Node] 不对外发放。外部要"能不能用"这个答案请走 [Expression#valid]
    static @Nullable Node compile(@Nullable String source) {
        return Compiler.compile(source);
    }

    // endregion

    // region 名字与引用分析

    /// 名字是否是内置函数
    public static boolean isBuiltinFunction(String name) {
        return Builtins.isBuiltin(name);
    }

    /// 这个名字能否被表达式识别成标识符。
    ///
    /// 规则与 [Compiler] 里读标识符的那段完全一致：以字母、下划线或非 ASCII 字符开头，其后可跟数字。
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

    /// @param variables 自定义变量名（含拼错的名字）
    /// @param functions 被调用的自定义函数名
    /// @param builtins 被调用的内置量名
    public record Names(Set<String> variables, Set<String> functions, Set<String> builtins) {
        /// 编译不过的公式：谁也没引用
        public static final Names EMPTY = new Names(Set.of(), Set.of(), Set.of());
    }

    /// 编译函数或表达式，并检索引用了哪些名字。
    ///
    /// @param source 函数或表达式字符串
    /// @return 除内置函数名外的引用名称，编译不过返回 [Names#EMPTY]
    public static Names names(@Nullable String source) {
        return names(source, Set.of());
    }

    /// 编译函数或表达式，并检索引用了哪些名字。
    ///
    /// @param source 函数或表达式字符串
    /// @param parameters 函数参数
    /// @return 除函数参数名、内置函数名外的引用名称，编译不过返回 [Names#EMPTY]
    public static Names names(@Nullable String source, Set<String> parameters) {
        Node node = Compiler.compile(source);

        if (node == null) {
            return Names.EMPTY;
        }

        Set<String> variables = new LinkedHashSet<>();
        Set<String> functions = new LinkedHashSet<>();
        Set<String> builtins = new LinkedHashSet<>();
        collect(node, parameters, variables, functions, builtins);
        return new Names(Set.copyOf(variables), Set.copyOf(functions), Set.copyOf(builtins));
    }

    private static void collect(Node node, Set<String> parameters, Set<String> variables, Set<String> functions,
                                Set<String> builtins) {
        switch (node) {
            case Node.Const _ -> {
            }
            case Node.Name name -> {
                String referenced = name.name();

                if (parameters.contains(referenced)) {
                    // 形参不如引用表
                    return;
                }

                if (Resolver.isBuiltinName(referenced)) {
                    builtins.add(referenced);
                } else {
                    variables.add(referenced);
                }
            }
            case Node.Neg neg -> collect(neg.operand(), parameters, variables, functions, builtins);
            case Node.Binary binary -> {
                collect(binary.left(), parameters, variables, functions, builtins);
                collect(binary.right(), parameters, variables, functions, builtins);
            }
            // 内置函数名不是对被调用者的引用：它是编译期就定下来的算子
            case Node.Builtin builtin -> arguments(builtin.arguments(), parameters, variables, functions, builtins);
            case Node.Custom custom -> {
                functions.add(custom.name());
                arguments(custom.arguments(), parameters, variables, functions, builtins);
            }
        }
    }

    private static void arguments(List<Node> nodes, Set<String> parameters, Set<String> variables,
                                  Set<String> functions, Set<String> builtins) {
        for (Node node : nodes) {
            collect(node, parameters, variables, functions, builtins);
        }
    }

    /// 标识符里允许出现的字符：字母、数字、下划线，以及非 ASCII 字符（中文这类非拉丁文字靠它放行）。
    ///
    /// 解析（[Compiler]）与 [Expression#validName] 共用同一条规则，免得"写得进去"和"起得了名"两套口径
    static boolean nameChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character > 127;
    }

    // endregion
}
