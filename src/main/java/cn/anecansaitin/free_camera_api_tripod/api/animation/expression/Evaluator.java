package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 解释器：按 [Node] 树求值。**这是全仓唯一读语法树的地方**。
///
/// 两条递归线在这里分开对待，这也是拆分之后才能做到的事：
///
/// - **树内**递归（子节点、实参）直接递归，不推深度。一层深度 = 一次"求值一段公式"，
///   同一条公式里的括号与操作符不该各算一层；树本身的深浅已经由 [Compiler] 的静态上限管住
/// - **跨公式**递归（变量取值、轨道上的键公式、自定义函数体）一律经由 [Evaluation#expression]，
///   深度栈只在那一个方法里推拉，实现者（[Resolver] 的实现、自定义函数）都不必自己记账
@NullMarked
final class Evaluator {
    private Evaluator() {
    }

    static float eval(Node node, Evaluation evaluation) {
        return switch (node) {
            case Node.Const constant -> constant.value();
            case Node.Name name -> evaluation.resolve(name.name());
            case Node.Neg neg -> -eval(neg.operand(), evaluation);
            case Node.Binary binary -> binary(binary, evaluation);
            case Node.Builtin builtin -> builtin(builtin, evaluation);
            case Node.Custom custom -> custom(custom, evaluation);
        };
    }

    private static float binary(Node.Binary node, Evaluation evaluation) {
        float left = eval(node.left(), evaluation);
        float right = eval(node.right(), evaluation);

        return switch (node.operator()) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> left / right;
            case MODULO -> left % right;
        };
    }

    private static float builtin(Node.Builtin node, Evaluation evaluation) {
        List<Node> arguments = node.arguments();
        float[] values = new float[arguments.size()];

        for (int i = 0; i < values.length; i++) {
            values[i] = eval(arguments.get(i), evaluation);
        }

        return node.builtin().operator().apply(values);
    }

    private static float custom(Node.Custom node, Evaluation evaluation) {
        CustomFunction function = evaluation.function(node.name());

        // 函数还没定义、或实参个数与形参不符：给 NaN，由调用方回退到固定数值。
        // 内置函数的个数不符是编译期的事（见 Compiler），自定义函数随时可改，只能运行期对
        if (function == null || function.parameters().size() != node.arguments().size()) {
            return Float.NaN;
        }

        return evaluation.call(function, node.arguments());
    }
}
