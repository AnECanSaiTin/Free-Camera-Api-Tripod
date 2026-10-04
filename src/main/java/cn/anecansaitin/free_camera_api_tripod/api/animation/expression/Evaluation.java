package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/// 一次求值的会话：这一趟用哪个 [Resolver]、有哪些参数帧、深度推到哪一层。
///
/// ## 求值入口只有本类的方法
///
/// [Node] 不会自己求值（它不再是闭包），所以绕过深度栈的办法在类型上就不存在：
/// 求值一段公式只能走 [#expression]，它也是深度栈**唯一的推拉点**。原来是闭包的时候，
/// `CustomFunction.invoke` 里两处 `formula.evaluate(resolver)` 就是绕过去的口子。
///
/// ## 参数帧是词法的
///
/// 自定义函数的实参按值绑进一帧，只有**函数体**看得见它——函数体里读到某条轨道上的键公式
/// 时，那一层看不见这些参数名。原来这件事由 `CustomFunction.invoke` 里的匿名 `Resolver`
/// 一层层套着做，现在并进了会话：帧栈从内往外查，查不到才落到 [Resolver]。
///
/// 形参**可以**遮蔽同名变量：那是函数体里最自然的一层局部名字，帧栈先查就是它的实现。
/// 唯一不许遮蔽的是内置量 `t` / `p` / `wt`（写入期由 [SymbolTable#parameterConflict] 挡住），
/// 那是求值环境的名字，不是用户数据。不同函数的形参之间也可以重名——
/// 内层函数体读到外层参数是调用栈上的词法嵌套，一直如此。
///
/// ## 与深度的分工
///
/// 深度是线程级的（见 [EvalDepth]），所以嵌套求值新起的会话照样累加在同一个格上；
/// 参数帧则严格属于一次会话，不跨会话泄漏。
@NullMarked
final class Evaluation {
    private final Resolver resolver;
    /// 参数帧栈；只有自定义函数体会往里压，从内往外查
    private final Deque<Frame> frames = new ArrayDeque<>();
    /// 本线程的深度格，推拉都在 [#expression] 里
    private final int[] depth = EvalDepth.level();

    private Evaluation(Resolver resolver) {
        this.resolver = resolver;
    }

    /// 起一次求值。嵌套求值同样用它——深度在线程级共享，新建会话不会把计数清零
    static Evaluation root(Resolver resolver) {
        return new Evaluation(resolver);
    }

    /// 求值一段公式：**深度栈唯一的推拉点**
    float expression(Node node) {
        if (depth[0] >= EvalDepth.MAX) {
            return Float.NaN;
        }

        depth[0]++;

        try {
            return Evaluator.eval(node, this);
        } catch (RuntimeException e) {
            // 求值中出错一律退回 NaN，由 NumberSource.evaluateOrFallback 一处决定用不用固定数值
            return Float.NaN;
        } finally {
            depth[0]--;
        }
    }

    /// 名字 → 一个数：先查最内层的参数帧，都没有再交给 [Resolver]。
    /// 帧里同名参数取**最先声明的那个**。
    ///
    /// 这就是"形参遮蔽同名变量"的实现：函数体里的 `a` 若与变量 `a` 同名，取的是形参。
    /// 内置量不参与这个遮蔽——`t` / `p` / `wt` 不许做形参名（见 [SymbolTable#parameterConflict]）
    float resolve(String name) {
        Iterator<Frame> iterator = frames.descendingIterator();

        while (iterator.hasNext()) {
            Frame frame = iterator.next();
            int index = frame.names().indexOf(name);

            if (index >= 0) {
                return frame.values()[index];
            }
        }

        return resolver.resolve(name);
    }

    /// 自定义函数查询；调用点已经对过参数个数
    @Nullable CustomFunction function(String name) {
        return resolver.function(name);
    }

    /// 调用自定义函数：实参属于**调用方那条公式**（不推深度），函数体才是新的一层
    float call(CustomFunction function, List<Node> arguments) {
        float[] values = new float[arguments.size()];

        for (int i = 0; i < values.length; i++) {
            values[i] = Evaluator.eval(arguments.get(i), this);
        }

        frames.addLast(new Frame(function.parameters(), values));

        try {
            // 函数体按文本查一次编译缓存：函数体随时可改，改完立刻生效
            Node body = Compiler.compile(function.body());
            return body == null ? Float.NaN : expression(body);
        } finally {
            frames.removeLast();
        }
    }

    private record Frame(List<String> names, float[] values) {
    }
}
