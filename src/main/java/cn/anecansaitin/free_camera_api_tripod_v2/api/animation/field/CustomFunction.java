package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 文档里的自定义函数：名字 + 形参表 + 函数体公式（主设计 §4.2 L3、§6.1）。
///
/// ## 函数体是公式文本
///
/// `body` 是表达式文本而不是编译产物：编译结果属于派生数据，按文本在编译缓存里驻留
/// （主设计 §3.4「编译结果：按公式文本缓存，使用有界 LRU」），不进文档。
///
/// 编译失败不阻止保存：作者要能看见并修改自己写错的内容；但求值必须报 `ERROR`，
/// 且静态校验应报告语法错误而不是等运行期才失败。
///
/// ## 形参与作用域
///
/// `parameters` 是形参名表，**在函数体内屏蔽同名变量**。依赖图构建必须正确处理这层作用域：
/// 形参名在函数体里不算对同名变量/函数的引用（主设计 §4.2：依赖图从编译后语法树构建，
/// 正确处理函数形参作用域）。
///
/// 形参个数在**提交期**检查：调用点实参个数与形参表必须一致（主设计 §6.1）。
///
/// 形参名与用户变量重名是**允许**的（那是函数体里最自然的一层局部名字）；
/// 不允许的是形参表自身重复，以及函数名与内置函数或其它函数冲突。
///
/// ## 递归
///
/// 表达式没有条件写法，递归本来就写不出终止条件；函数之间的**环**由依赖图在提交前拒绝
/// （主设计 §4.2：未定义引用与环分别报告）。运行期仍有调用深度与工作量上限。
///
/// @param name       函数名；文档内唯一，非空白
/// @param parameters 形参名表，顺序即调用点实参顺序；名字互不相同
/// @param body       函数体公式文本
@NullMarked
public record CustomFunction(String name, List<String> parameters, String body) {

    public CustomFunction {
        if (name.isBlank()) {
            throw new IllegalArgumentException("CustomFunction name must not be blank");
        }

        if (body.isBlank()) {
            throw new IllegalArgumentException("CustomFunction body must not be blank");
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        parameters = List.copyOf(parameters);

        for (String parameter : parameters) {
            if (parameter.isBlank()) {
                throw new IllegalArgumentException("CustomFunction parameter must not be blank");
            }
        }

        // 形参表自身重复在构造期就能判定，不必等 L3；这是纯结构约束
        for (int i = 0; i < parameters.size(); i++) {
            for (int j = i + 1; j < parameters.size(); j++) {
                if (parameters.get(i).equals(parameters.get(j))) {
                    throw new IllegalArgumentException(
                            "CustomFunction parameter duplicated: " + parameters.get(i));
                }
            }
        }
    }

    /// 形参个数。调用点校验用它，不必每次取列表长度。
    public int arity() {
        return parameters.size();
    }

    public CustomFunction withBody(String newBody) {
        return new CustomFunction(name, parameters, newBody);
    }

    public CustomFunction withParameters(List<String> newParameters) {
        return new CustomFunction(name, newParameters, body);
    }
}
