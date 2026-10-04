package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 自定义函数：名字 + 参数名 + 函数体（一段公式文本）。
///
/// 函数体在**调用时**才编译（走 [Compiler] 的文本缓存），所以改完函数体立刻生效，
/// 不必去清理别处的编译结果。参数按值绑定：实参先在调用方求值一次，再按参数名喂给函数体。
///
/// **调用不由本类做**：参数帧与函数体求值都在 [Evaluation#call] 里。原来这里有一段匿名
/// [Resolver] 加一次直调闭包的求值——那既是"求值入口不唯一"的口子（深度栈绕不过去），
/// 也让参数帧这件本来就属于"一次求值"的状态寄居在函数对象上。
///
/// 函数体里还能再调用别的自定义函数（查询转回外层求解器）；自我递归 `f(x) = f(x)` 现在会被
/// [EvalDepth] 的上限截断成 NaN，而不是递归到栈溢出——表达式没有条件写法，递归本来就写不出终止条件。
///
/// 可变对象：[cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation] 直接持有实例。
@NullMarked
public final class CustomFunction {
    private String name;
    private List<String> parameters;
    private String body;

    public CustomFunction(String name, List<String> parameters, String body) {
        this.name = name;
        this.parameters = List.copyOf(parameters);
        this.body = body;
    }

    public String name() {
        return name;
    }

    public CustomFunction name(String name) {
        this.name = name;
        return this;
    }

    /// 参数名，按顺序与实参一一对应
    public List<String> parameters() {
        return parameters;
    }

    public CustomFunction parameters(List<String> parameters) {
        this.parameters = List.copyOf(parameters);
        return this;
    }

    /// 函数体的公式文本
    public String body() {
        return body;
    }

    public CustomFunction body(String body) {
        this.body = body;
        return this;
    }

    public CustomFunction copy() {
        return new CustomFunction(name, parameters, body);
    }

    @Override
    public String toString() {
        return name + "(" + String.join(", ", parameters) + ")";
    }
}
