package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// 编译：公式文本 → [Node] 树，并按文本驻留结果。
///
/// 与求值分开之后这里只做三件事：递归下降解析、**编译期上限**、缓存。求值期状态（深度栈、
/// 参数帧）一律不在这里——原来这些和解析挤在同一个类里，深度计数没有明确归属，只能挂在
/// 最外层的 `ExpressionScope` 上兜着。
///
/// 两条上限都在编译期生效，超了就当公式不合法（`compile` 返回 `null`，界面据此标红，
/// 运行期退回固定数值）：
/// - [Compiler#MAX_NESTING] 管括号与实参的嵌套，挡住解析器自己递归爆栈；
/// - [Compiler#MAX_NODES] 管节点总数，挡住**解释器**遍历树时递归爆栈
///   （`1+1+1+…` 这种链在树上是左倾的，值得几项就有几层）。
///
/// 运行期的深度栈（[Evaluation]）管的是另一件事——**跨公式**的递归（变量、轨道、函数体），
/// 两者互补，缺一不可。
@NullMarked
final class Compiler {
    /// 编译缓存上限；超出后整体清空——公式总量很小，没必要做精细淘汰
    private static final int CACHE_LIMIT = 512;

    /// 括号与实参的嵌套上限。每层约 4 个解析栈帧，64 层离栈容量很远，正常公式到不了两位数
    static final int MAX_NESTING = 64;

    /// 单条公式的节点数上限。树深不超过节点数，1024 个节点最多让解释器递归 1024 层，
    /// 仍在栈容量内；真有这么长的公式，多半是生成出来的，判它不合法比让它爆栈好
    static final int MAX_NODES = 1024;

    /// 编译结果按文本驻留：`Formula` 因此不必自己带着语法树走，序列化与拷贝都不会捎上它
    private static final Map<String, Node> CACHE = new ConcurrentHashMap<>();

    private Compiler() {
    }

    /// 编译一段公式；空串、语法错误、超过上限都返回 `null`
    static @Nullable Node compile(@Nullable String source) {
        if (source == null || source.isBlank()) {
            return null;
        }

        Node cached = CACHE.get(source);

        if (cached != null) {
            return cached;
        }

        Node node = parse(source);

        if (node == null) {
            return null;
        }

        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }

        CACHE.put(source, node);
        return node;
    }

    private static @Nullable Node parse(String source) {
        Reader reader = new Reader(source);

        try {
            Node node = reader.expression();
            reader.skipSpaces();
            return reader.end() ? fold(node) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // region 常量折叠

    /// 常量折叠：把只由字面量构成的子树先算出来，解释器于是少走几个节点。
    ///
    /// 三条边界：
    /// - **上限按折叠前的树算**（折叠跑在 [Reader] 之后）：上限管的是"公式文本的复杂度"，
    ///   不该因为一个优化而漂移；解析期的内存与递归深度也因此始终受 [MAX_NODES] 约束
    /// - **只折确定且逐位可复现的运算**：四则、取模、`sqrt`，以及只由它们拼出来的内置函数
    ///   （见 [Builtins.Builtin#foldable()]）。`random` 与 `Math.pow` / 超越函数一律不折——
    ///   前者折了就固化成编译那一刻的一个数，后者跨平台只保证 1 ulp 内
    /// - **折不动就原样留着**：任何异常都吞掉。一次优化绝不能让合法公式变成非法
    private static Node fold(Node node) {
        return switch (node) {
            case Node.Const constant -> constant;
            case Node.Name name -> name;
            case Node.Neg neg -> {
                Node operand = fold(neg.operand());
                yield operand instanceof Node.Const constant
                        ? new Node.Const(-constant.value())
                        : new Node.Neg(operand);
            }
            case Node.Binary binary -> foldBinary(binary);
            case Node.Builtin builtin -> foldBuiltin(builtin);
            case Node.Custom custom -> new Node.Custom(custom.name(), foldAll(custom.arguments()));
        };
    }

    private static Node foldBinary(Node.Binary binary) {
        Node left = fold(binary.left());
        Node right = fold(binary.right());

        if (left instanceof Node.Const first && right instanceof Node.Const second) {
            Float value = compute(() -> binary.operator().apply(first.value(), second.value()));

            if (value != null) {
                return new Node.Const(value);
            }
        }

        return new Node.Binary(binary.operator(), left, right);
    }

    private static Node foldBuiltin(Node.Builtin builtin) {
        List<Node> arguments = foldAll(builtin.arguments());
        Builtins.Builtin function = builtin.builtin();

        if (!function.foldable()) {
            return new Node.Builtin(function, arguments);
        }

        float[] values = new float[arguments.size()];

        for (int i = 0; i < values.length; i++) {
            if (!(arguments.get(i) instanceof Node.Const constant)) {
                return new Node.Builtin(function, arguments);
            }

            values[i] = constant.value();
        }

        Float value = compute(() -> function.operator().apply(values));
        return value == null ? new Node.Builtin(function, arguments) : new Node.Const(value);
    }

    private static List<Node> foldAll(List<Node> nodes) {
        List<Node> folded = new ArrayList<>(nodes.size());

        for (Node node : nodes) {
            folded.add(fold(node));
        }

        return List.copyOf(folded);
    }

    /// 试算一次；出任何岔子都当作没折过（返回 null，调用方保留原节点）
    private static @Nullable Float compute(FloatOperation operation) {
        try {
            return operation.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface FloatOperation {
        float get();
    }

    // endregion

    /// 递归下降：加减 → 乘除模 → 一元 → 括号 / 数字 / 名字 / 函数调用。
    /// 每解析出一段就返回一个不可变节点，**求值不在这一层发生**
    private static final class Reader {
        private final String source;
        private int index;
        /// 当前嵌套层数：括号、实参、一元符号链都算
        private int nesting;
        /// 已经产出的节点数
        private int nodes;

        private Reader(String source) {
            this.source = source;
        }

        /// 进一层嵌套：括号、实参、一元符号链都算。超上限抛出，让整条公式判为不合法
        private void enter() {
            if (nesting >= MAX_NESTING) {
                throw new IllegalArgumentException("expression nested too deeply at " + index);
            }

            nesting++;
        }

        /// 加减（括号与实参都从这里进，所以也是主要的"嵌套一层"计数点；一元符号链在 [Reader#unary] 里单独计）
        private Node expression() {
            enter();

            try {
                Node left = multiplicative();

                while (true) {
                    skipSpaces();

                    if (take('+')) {
                        left = binary(Node.Operator.ADD, left, multiplicative());
                    } else if (take('-')) {
                        left = binary(Node.Operator.SUBTRACT, left, multiplicative());
                    } else {
                        return left;
                    }
                }
            } finally {
                nesting--;
            }
        }

        /// 乘除模
        private Node multiplicative() {
            Node left = unary();

            while (true) {
                skipSpaces();

                if (take('*')) {
                    left = binary(Node.Operator.MULTIPLY, left, unary());
                } else if (take('/')) {
                    left = binary(Node.Operator.DIVIDE, left, unary());
                } else if (take('%')) {
                    left = binary(Node.Operator.MODULO, left, unary());
                } else {
                    return left;
                }
            }
        }

        /// 一元正负。
        ///
        /// **符号链自己也递归**（`----1` 是四层），所以要跟括号一样计嵌套：
        /// 只靠节点数上限的话，`new Node.Neg(unary())` 会先把整条链递归到底才轮到计数，
        /// 一条几万字符的符号链就是几万层栈帧
        private Node unary() {
            skipSpaces();
            char sign = end() ? '\0' : source.charAt(index);

            if (sign == '-' || sign == '+') {
                index++;
                enter();

                try {
                    Node operand = unary();
                    // 一元正号是恒等：不建节点，省一层求值
                    return sign == '-' ? count(new Node.Neg(operand)) : operand;
                } finally {
                    nesting--;
                }
            }

            return primary();
        }

        /// 括号 / 数字 / 名字 / 函数调用
        private Node primary() {
            skipSpaces();

            if (take('(')) {
                Node value = expression();
                skipSpaces();
                require(')');
                return value;
            }

            if (!end() && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                return count(new Node.Const(number()));
            }

            String name = identifier();

            if (take('(')) {
                return call(name);
            }

            return count(new Node.Name(name));
        }

        /// 函数调用：内置函数在这里定下来（含参数个数校验），其余留给运行期查自定义函数
        private Node call(String name) {
            List<Node> arguments = new ArrayList<>();
            skipSpaces();

            if (!take(')')) {
                do {
                    arguments.add(expression());
                    skipSpaces();
                } while (take(','));

                skipSpaces();
                require(')');
            }

            Builtins.Builtin builtin = Builtins.get(name);

            if (builtin == null) {
                return count(new Node.Custom(name, List.copyOf(arguments)));
            }

            if (!builtin.accepts(arguments.size())) {
                throw new IllegalArgumentException(Builtins.arityText(builtin));
            }

            return count(new Node.Builtin(builtin, List.copyOf(arguments)));
        }

        private Node binary(Node.Operator operator, Node left, Node right) {
            return count(new Node.Binary(operator, left, right));
        }

        /// 记一个节点；超上限就抛出，让整条公式判为不合法
        private Node count(Node node) {
            if (++nodes > MAX_NODES) {
                throw new IllegalArgumentException("expression too large at " + index);
            }

            return node;
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

            while (!end() && Expression.nameChar(source.charAt(index))) {
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
}
