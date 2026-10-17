package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// 文档的符号表：共享的变量与自定义函数（主设计 §3.1、§4.2）。
///
/// ## 一份文档一张符号表
///
/// 符号表是**文档级**的，不随多机位复制。原因有两条：
///
/// - `TrackRef`（变量读轨道）要求 `TrackId` 在整份文档内唯一，因此不必把它改成
///   “机位 + 轨道”二元键（主设计 §3.1）。
/// - 共享符号若读取机位空间事实，则**求值与缓存必须带 `RigId`**（主设计 §6.1）：
///   这是求值上下文的事，不是把符号表拆成多份的理由。
///
/// ## 名字唯一性与作用域
///
/// - 变量之间、函数之间各自查重；变量名与函数名可以与对方同名（不同名字空间）。
/// - 与内置量（`t` / `p` / `wt` 一类保留名）或内置函数重名属于 L3 语义问题：
///   校验器报告，而不是在构造期抛异常——因为“合法数据 + 名字冲突”是可修复的候选，
///   不是非法对象。
/// - 变量名与函数**形参**重名是允许的，那是函数体里的局部作用域。
///
/// 因此本 record 只保证**结构**（非空、容器已复制），唯一性与作用域由
/// `DocumentValidator` 的 L2/L3 层报告。
///
/// ## 环不在这里判
///
/// 依赖环可以跨过轨道边界（变量绑轨道、那条轨道上的关键帧公式又引用该变量），
/// 只有同时看得见符号表与轨道表的对象才能判环。所以权威判定在
/// `api.animation.eval` 的依赖图上，由 `CameraAnimation` 派生。
///
/// ## 不可变
///
/// 两张表都是 `List`，构造时防御性复制；查询用的名字索引属于派生数据，
/// 由依赖图或求值层缓存，不在文档对象里维护第二份可独立修改的数据
/// （主设计 §3.1：按 ID 查询表由同一集合派生，不作为第二份可独立修改的数据）。
///
/// @param variables 变量集合，顺序即显示顺序
/// @param functions 自定义函数集合，顺序即显示顺序
@NullMarked
public record Symbols(List<Variable> variables, List<CustomFunction> functions) {

    /// 空符号表。
    public static final Symbols EMPTY = new Symbols(List.of(), List.of());

    public Symbols {
        // 防御性复制：不保留调用方容器（主设计 §3.2）
        variables = List.copyOf(variables);
        functions = List.copyOf(functions);
    }

    /// 按名字找变量。
    public Optional<Variable> variable(String name) {
        for (Variable variable : variables) {
            if (variable.name().equals(name)) {
                return Optional.of(variable);
            }
        }

        return Optional.empty();
    }

    /// 按名字找自定义函数。
    public Optional<CustomFunction> function(String name) {
        for (CustomFunction function : functions) {
            if (function.name().equals(name)) {
                return Optional.of(function);
            }
        }

        return Optional.empty();
    }

    /// 名字是否已被某个变量占用。
    public boolean variableNameTaken(String name) {
        return variable(name).isPresent();
    }

    /// 名字是否已被某个函数占用。
    public boolean functionNameTaken(String name) {
        return function(name).isPresent();
    }

    /// 取出一个变量的取值来源；变量不存在返回空。
    public @Nullable Variable variableOrNull(String name) {
        return variable(name).orElse(null);
    }

    public Symbols withVariables(List<Variable> newVariables) {
        return new Symbols(newVariables, functions);
    }

    public Symbols withFunctions(List<CustomFunction> newFunctions) {
        return new Symbols(variables, newFunctions);
    }
}
