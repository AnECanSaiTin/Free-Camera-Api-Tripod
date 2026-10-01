package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 自定义函数：名字 + 参数名 + 函数体（一段公式文本）。
///
/// 函数体在**调用时**才编译（走 [Expression] 的文本缓存），所以改完函数体立刻生效，
/// 不必去清理别处的编译结果。参数按值绑定：实参先在调用方求值一次，再按参数名喂给函数体。
///
/// 函数体里还能再调用别的自定义函数（查询转回外层求解器），但不支持递归——表达式没有条件写法，
/// 递归也写不出终止条件。
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

    /// 调用：把实参按参数名绑好，再求函数体。
    ///
    /// 函数体编译不出来（语法错）时返回 NaN；实参个数与参数个数不符由 [Expression] 在调用前挡下
    public float invoke(List<Expression.Formula> arguments, Resolver outer) {
        Expression.Formula formula = Expression.compile(body);

        if (formula == null) {
            return Float.NaN;
        }

        return formula.evaluate(new Resolver() {
            @Override
            public float resolve(String name) {
                // 参数名优先，其余名字继续往外层找（变量、内置变量）
                int index = parameters.indexOf(name);

                if (index >= 0 && index < arguments.size()) {
                    return arguments.get(index).evaluate(outer);
                }

                return outer.resolve(name);
            }

            @Override
            public @Nullable CustomFunction function(String name) {
                return outer.function(name);
            }

            @Override
            public float track(String id) {
                return outer.track(id);
            }
        });
    }

    @Override
    public String toString() {
        return name + "(" + String.join(", ", parameters) + ")";
    }
}
