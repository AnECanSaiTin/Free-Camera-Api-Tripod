package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 编译产物：一棵**只有结构、不会自己求值**的语法树。
///
/// 原来的编译产物是一个 [`Expression.Formula`] 闭包——它既是"树"又是"求值器"，于是任何拿到
/// 闭包的地方都能 `formula.evaluate(resolver)`：求值入口无法唯一，"求值深度"也就没有落点
/// （`CustomFunction.invoke` 里直调闭包的函数体与实参就是这么漏出去的）。拆成节点树之后：
///
/// - 求值只能由 [Evaluator] 做，入口唯一，深度栈因此能只在一处推拉（见 [Evaluation]）；
/// - 树可以枚举，编译期就能算出嵌套深度与节点数（见 [Compiler] 的两条上限）；
/// - 内置函数在编译期解析成 [Node.Builtin]，运行期不必再按名字 switch。
///
/// 节点全部不可变，且**只在 `expression` 包内可见**：外部拿不到 `Node`，也就无法绕过
/// [Expression] 与 [Evaluation] 的求值入口。
@NullMarked
sealed interface Node permits Node.Binary, Node.Builtin, Node.Const, Node.Custom, Node.Name, Node.Neg {
    /// 数字字面量
    record Const(float value) implements Node {
    }

    /// 变量名：内置量（`t` / `p` / `wt`）、变量，或函数体的参数名——具体是哪一个由 [Evaluation#resolve] 决定
    record Name(String name) implements Node {
    }

    /// 一元负号；一元正号在编译期就被吃掉了（`+x` 与 `x` 同义）
    record Neg(Node operand) implements Node {
    }

    /// 四则与取模。**不重排、不合并**：浮点加法不满足结合律，重排会改变结果
    record Binary(Operator operator, Node left, Node right) implements Node {
    }

    /// 内置函数调用：函数本身在编译期就定下了，参数个数也已校验
    record Builtin(Builtins.Builtin builtin, List<Node> arguments) implements Node {
    }

    /// 自定义函数调用：名字留到运行期查（函数随时可加、可改），参数个数在运行期对
    record Custom(String name, List<Node> arguments) implements Node {
    }

    /// 四则与取模。**不重排、不合并**：浮点加法不满足结合律，重排会改变结果。
    ///
    /// 运算本身挂在这里，[Evaluator] 与 [Compiler] 的常量折叠因此共用同一份实现——
    /// 折叠算出来的数与运行期算出来的数必然一致
    enum Operator {
        ADD {
            @Override
            float apply(float left, float right) {
                return left + right;
            }
        },
        SUBTRACT {
            @Override
            float apply(float left, float right) {
                return left - right;
            }
        },
        MULTIPLY {
            @Override
            float apply(float left, float right) {
                return left * right;
            }
        },
        DIVIDE {
            @Override
            float apply(float left, float right) {
                return left / right;
            }
        },
        MODULO {
            @Override
            float apply(float left, float right) {
                return left % right;
            }
        };

        abstract float apply(float left, float right);
    }
}
