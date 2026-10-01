package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 符号表的只读视图：一段动画里定义的[变量][Variable]与[自定义函数][CustomFunction]，外加它们的静态检查结果。
///
/// 变量与函数是**动画级**的定义，不属于任何一条曲线轨道：公式按名字引用它们，
/// 求值层（见 `eval.ExpressionScope`）只读这里。把它们与轨道分开的依据是——轨道随动画增删、
/// 变量引用轨道，反过来轨道不引用变量，所以这里是单向依赖的终点。
///
/// 读写两类方法分在两级：增删改名在 [SymbolTable]，这里只有查询，
/// 因此播放器与只读面板拿到这份视图也改不了表的内容。
@NullMarked
public interface SymbolTablec {
    /// 变量表：表达式按名字引用，取值为其绑定轨道在当前时刻的读数
    List<Variable> variables();

    /// 按名字取变量；不存在返回 null
    @Nullable Variable variable(String name);

    /// 自定义函数表：公式里按名字调用，形参见 [CustomFunction]
    List<CustomFunction> functions();

    /// 按名字取函数；不存在返回 null
    @Nullable CustomFunction function(String name);

    /// 名字是否已被内置函数或别的自定义函数占用
    boolean functionNameTaken(String name);
}
