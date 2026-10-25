package cn.anecansaitin.free_camera_api_tripod_v2.core.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 表达式语法树节点（core 内部类型，不属于 api 契约）。
///
/// ## 为什么在 core 公开
///
/// `api.animation.expression.CompiledExpression` 只是不透明句柄：设计 §6.1 明确语法树与解释器
/// 不作为稳定扩展接口公开。后续解释器位于 core，需要结构化访问编译结果，因此语法树在
/// `core.expression` 公开，由
/// [CompiledExpressionImpl#ast()] 提供；api 包不出现任何语法树类型。
///
/// ## 不可变与源码区间
///
/// 全部节点都是不可变 record，容器防御性复制，可以跨线程共享。每个节点带 `[start, end)`
/// 源码区间（UTF-16 下标），用于运行期诊断与编辑器定位：
///
/// - 变量节点的区间就是标识符本身；
/// - 函数调用 / 样本读取节点的 `start()` / `end()` 覆盖整个调用表达式，同时用
///   `nameStart()` / `nameEnd()` 单独保留名字出现位置（引用登记用）；
/// - 括号不产生独立节点，因此 `(1 + 2)` 的根节点区间是内部表达式的区间。
///
/// 编译期只接受单词素数字字面量，因此 [NumberLiteral] 的数值必然有限；记录构造器仍再次
/// 检查，避免绕过编译器手工构造非有限值。
@NullMarked
public sealed interface ExpressionNode permits
        ExpressionNode.NumberLiteral,
        ExpressionNode.Quantity,
        ExpressionNode.Variable,
        ExpressionNode.Unary,
        ExpressionNode.Binary,
        ExpressionNode.BuiltinCall,
        ExpressionNode.Call,
        ExpressionNode.Sample {

    /// 源码起始下标（含），非负。
    int start();

    /// 源码结束下标（不含），不小于 [start()]。
    int end();

    /// 数字字面量。
    ///
    /// @param value 字面量数值，有限
    /// @param start 源码起始下标
    /// @param end   源码结束下标（不含）
    record NumberLiteral(double value, int start, int end) implements ExpressionNode {

        public NumberLiteral {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("NumberLiteral value must be finite: " + value);
            }

            requireSpan(start, end);
        }
    }

    /// 内建量 `t` / `p` / `wt`；不登记外部引用。
    ///
    /// @param quantity 内建量
    /// @param start    源码起始下标
    /// @param end      源码结束下标（不含）
    record Quantity(BuiltinQuantity quantity, int start, int end) implements ExpressionNode {

        public Quantity {
            Objects.requireNonNull(quantity, "quantity");
            requireSpan(start, end);
        }
    }

    /// 外部变量引用；编译期不判断符号表中是否存在该名字。
    ///
    /// @param name  变量名，非空白
    /// @param start 源码起始下标
    /// @param end   源码结束下标（不含）
    record Variable(String name, int start, int end) implements ExpressionNode {

        public Variable {
            requireName(name);
            requireSpan(start, end);
        }
    }

    /// 一元运算。
    ///
    /// @param operator 运算符
    /// @param operand  操作数
    /// @param start    源码起始下标（运算符位置）
    /// @param end      源码结束下标（不含，覆盖操作数）
    record Unary(UnaryOperator operator, ExpressionNode operand, int start, int end)
            implements ExpressionNode {

        public Unary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(operand, "operand");
            requireSpan(start, end);
        }
    }

    /// 二元运算；同级左结合。
    ///
    /// @param operator 运算符
    /// @param left     左操作数
    /// @param right    右操作数
    /// @param start    源码起始下标（左操作数起点）
    /// @param end      源码结束下标（不含，右操作数终点）
    record Binary(BinaryOperator operator, ExpressionNode left, ExpressionNode right, int start, int end)
            implements ExpressionNode {

        public Binary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            requireSpan(start, end);
        }
    }

    /// 内建函数调用；实参个数已在编译期校验。
    ///
    /// `select` 的实参全部保留在语法树里：求值器负责只执行选中分支，静态引用与依赖
    /// 仍覆盖所有实参。
    ///
    /// @param function  内建函数
    /// @param arguments 实参，按源码顺序；可为空但不可为 null
    /// @param start     源码起始下标（函数名起点）
    /// @param end       源码结束下标（不含，右括号之后）
    record BuiltinCall(BuiltinFunction function, List<ExpressionNode> arguments, int start, int end)
            implements ExpressionNode {

        public BuiltinCall {
            Objects.requireNonNull(function, "function");
            Objects.requireNonNull(arguments, "arguments");

            if (arguments.size() < function.minArity() || arguments.size() > function.maxArity()) {
                throw new IllegalArgumentException("Builtin function '" + function.symbol()
                        + "' does not accept " + arguments.size() + " arguments");
            }

            arguments = List.copyOf(arguments);
            requireSpan(start, end);
        }
    }

    /// 外部函数调用；编译期不判断符号表中是否存在该函数，也不校验其形参个数。
    ///
    /// @param name       函数名，非空白
    /// @param nameStart  函数名在源码中的起始下标
    /// @param nameEnd    函数名在源码中的结束下标（不含）
    /// @param arguments  实参，按源码顺序
    /// @param start      整个调用表达式的起始下标
    /// @param end        整个调用表达式的结束下标（不含）
    record Call(String name,
                int nameStart,
                int nameEnd,
                List<ExpressionNode> arguments,
                int start,
                int end) implements ExpressionNode {

        public Call {
            requireName(name);
            requireSpan(nameStart, nameEnd);
            Objects.requireNonNull(arguments, "arguments");
            arguments = List.copyOf(arguments);
            requireSpan(start, end);
        }
    }

    /// 样本读取：`sample("名字")` 或 `sample("名字", fallback)`。
    ///
    /// 名字必须是静态字符串字面量。`nameStart` / `nameEnd` 是**字符串字面量 token** 的区间
    /// （含引号），SAMPLE 引用按它登记；`start` / `end` 覆盖整个调用表达式。
    ///
    /// @param name      样本名，非空白
    /// @param nameStart 字符串字面量起始下标（引号）
    /// @param nameEnd   字符串字面量结束下标（不含，右引号之后）
    /// @param fallback  惰性默认值；无默认值时为 null
    /// @param start     整个调用表达式的起始下标
    /// @param end       整个调用表达式的结束下标（不含）
    record Sample(String name,
                  int nameStart,
                  int nameEnd,
                  @Nullable ExpressionNode fallback,
                  int start,
                  int end) implements ExpressionNode {

        public Sample {
            requireName(name);
            requireSpan(nameStart, nameEnd);
            requireSpan(start, end);
        }

        /// 是否带惰性默认值。
        public boolean hasFallback() {
            return fallback != null;
        }

        /// 默认值节点；无默认值时为空。
        public Optional<ExpressionNode> fallbackNode() {
            return Optional.ofNullable(fallback);
        }
    }

    /// 校验半开区间 `[start, end)`。
    private static void requireSpan(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException(
                    "ExpressionNode span must satisfy 0 <= start <= end: [" + start + ", " + end + ")");
        }
    }

    /// 校验名字非空白。
    private static void requireName(String name) {
        Objects.requireNonNull(name, "name");

        if (name.isBlank()) {
            throw new IllegalArgumentException("ExpressionNode name must not be blank");
        }
    }
}
