package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// 一段动画的符号表：[变量][Variable]与[自定义函数][CustomFunction]两张表，外加它们之间的引用关系。
///
/// 这里管的不只是"两个列表"，而是**名字空间与引用完整性**：
/// - 名字唯一：新增要查重，改名要挡住内置名字与同名冲突，见 [functionNameTaken]
/// - 环的含义：变量之间按名字互引，成环由 [cycle] 检查，求值时再兜底返回 NaN
/// - 指向轨道的引用跟着轨道走：轨道改名要一起改指向（[rebindTrack]），
///   轨道删掉要解绑（[unbindTrack]），否则变量会变成空指向、取值恒为 NaN
///
/// 关键帧上的公式**不属于**这张表：它们是键的数据，不是动画级的名字定义。
/// 表只提供查它们是否引用某个名字的静态方法（[references]），由持有曲线的调用方遍历。
@NullMarked
public final class SymbolTable implements SymbolTablec {
    /// 自动命名的新变量的名前缀，序号从 1 开始
    public static final String VARIABLE_NAME_PREFIX = "var";
    /// 自动命名的新函数的名前缀，序号从 1 开始
    public static final String FUNCTION_NAME_PREFIX = "f";
    private static final int NAME_LIMIT = 1000;

    /// 顺序表：界面上的显示顺序，也是序列化写出的顺序
    private final List<Variable> variables = new ArrayList<>();
    /// 按名字查变量。公式按名字引用，求值每次都要查，所以另留一份索引，别每次扫一遍表
    private final Map<String, Variable> variablesByName = new HashMap<>();
    private final List<CustomFunction> functions = new ArrayList<>();
    private final Map<String, CustomFunction> functionsByName = new HashMap<>();

    // region 查询

    @Override
    public List<Variable> variables() {
        return List.copyOf(variables);
    }

    @Override
    public @Nullable Variable variable(String name) {
        return variablesByName.get(name);
    }

    @Override
    public List<CustomFunction> functions() {
        return List.copyOf(functions);
    }

    @Override
    public @Nullable CustomFunction function(String name) {
        return functionsByName.get(name);
    }

    @Override
    public boolean functionNameTaken(String name) {
        return Expression.isBuiltinFunction(name) || function(name) != null;
    }

    @Override
    public @Nullable List<String> cycle() {
        return VariableGraph.findCycle(variables);
    }

    // endregion

    // region 变量

    /// 新增一个变量，名字取 `var1`、`var2`……；表里满到放不下时返回 null。默认不绑定轨道，固定值为 0
    public @Nullable Variable addVariable() {
        for (int i = 1; i < NAME_LIMIT; i++) {
            Variable variable = addVariable(VARIABLE_NAME_PREFIX + i);

            if (variable != null) {
                return variable;
            }
        }

        return null;
    }

    /// 新增一个变量；名字为空或已被占用时返回 null。默认不绑定轨道（固定值模式），固定值为 0
    public @Nullable Variable addVariable(String name) {
        String trimmed = name == null ? "" : name.strip();

        if (trimmed.isEmpty() || variable(trimmed) != null) {
            return null;
        }

        Variable variable = new Variable(trimmed);
        variables.add(variable);
        variablesByName.put(trimmed, variable);
        return variable;
    }

    /// 删除一个变量，返回是否删掉了
    public boolean removeVariable(String name) {
        Variable variable = variable(name);

        if (variable == null || !variables.remove(variable)) {
            return false;
        }

        variablesByName.remove(name);
        return true;
    }

    /// 把变量名改掉；新名字为空或已被其它变量占用时返回 false。
    /// 表达式是按名字引用变量的，改名后旧公式里的名字就取不到值了，由调用方提示用户
    public boolean renameVariable(String name, String newName) {
        String trimmed = newName == null ? "" : newName.strip();
        Variable variable = variable(name);

        if (variable == null || trimmed.isEmpty() || name.equals(trimmed)) {
            return false;
        }

        Variable occupied = variable(trimmed);

        if (occupied != null && occupied != variable) {
            return false;
        }

        variablesByName.remove(name);
        variable.name(trimmed);
        variablesByName.put(trimmed, variable);
        return true;
    }

    // endregion

    // region 自定义函数

    /// 新建一个函数，名字取 `f1`、`f2`……；默认两个参数 a、b，函数体就是 `a + b`（拿过来就能用）。
    /// 名字只是个占位，改成什么名字由调用方负责提示用户同步公式
    public @Nullable CustomFunction addFunction() {
        for (int i = 1; i < NAME_LIMIT; i++) {
            String name = FUNCTION_NAME_PREFIX + i;

            if (!functionNameTaken(name)) {
                return addFunction(name, List.of("a", "b"), "a + b");
            }
        }

        return null;
    }

    /// 按给定内容新建一个函数（读档也走这里）；名字已被占用时返回已有的那个，不覆盖
    public @Nullable CustomFunction addFunction(String name, List<String> parameters, String body) {
        CustomFunction existing = function(name);

        if (existing != null) {
            return existing;
        }

        CustomFunction function = new CustomFunction(name, parameters, body);
        functions.add(function);
        functionsByName.put(name, function);
        return function;
    }

    public boolean removeFunction(String name) {
        CustomFunction function = function(name);

        if (function == null || !functions.remove(function)) {
            return false;
        }

        functionsByName.remove(name);
        return true;
    }

    /// 把函数名改掉；新名字为空、已被别的函数占用、或撞上内置函数名时返回 false。
    /// 公式是按名字调用的，改名后旧公式里的调用会取不到值，由调用方提示用户
    public boolean renameFunction(String name, String newName) {
        String trimmed = newName == null ? "" : newName.strip();
        CustomFunction function = function(name);

        if (function == null || trimmed.isEmpty() || name.equals(trimmed) || functionNameTaken(trimmed)) {
            return false;
        }

        functionsByName.remove(name);
        function.name(trimmed);
        functionsByName.put(trimmed, function);
        return true;
    }

    // endregion

    // region 引用完整性

    /// 曲线轨道改名时调用：把指向旧 id 的变量一起改指向。
    /// 漏了这一步的话，变量绑定的名字在轨道表里已经不存在，取值恒为 NaN
    public void rebindTrack(String oldId, String newId) {
        for (Variable variable : variables) {
            if (variable.source() instanceof TrackValue value && value.trackId().equals(oldId)) {
                variable.source(new TrackValue(newId));
            }
        }
    }

    /// 曲线轨道被删除时调用：把指向它的变量解绑成固定值 0。
    /// 留着指向的话，引用这些变量的公式全都算不出来
    public void unbindTrack(String trackId) {
        for (Variable variable : variables) {
            if (trackId.equals(variable.trackId())) {
                variable.source(new ConstantValue(0));
            }
        }
    }

    /// 用另一份表的内容整体替换自身；变量与函数都是可变对象，装进来的是副本，避免两份动画共享同一个实例
    public void replaceFrom(SymbolTablec other) {
        variables.clear();
        variablesByName.clear();

        for (Variable variable : other.variables()) {
            Variable copy = variable.copy();
            variables.add(copy);
            variablesByName.put(copy.name(), copy);
        }

        functions.clear();
        functionsByName.clear();

        for (CustomFunction function : other.functions()) {
            CustomFunction copy = function.copy();
            functions.add(copy);
            functionsByName.put(copy.name(), copy);
        }
    }

    // endregion

    // region 静态扫描

    /// 关键帧上任一挂了公式的数值是否引用了该名字；五个数值（取值与两片曲柄的长度、斜率）都算。
    ///
    /// 放在这里是因为"某个键上的公式提到某个名字"正是引用完整性的另一半：变量自增时被引用，
    /// 由 `CameraAnimation#selfReferencing` 判定
    public static boolean references(Keyframec key, String name) {
        return references(key.valueSource(), name)
                || references(key.inSlopeSource(), name)
                || references(key.outSlopeSource(), name)
                || references(key.inLengthSource(), name)
                || references(key.outLengthSource(), name);
    }

    /// 数值来源是否以公式的形式引用了该名字；固定值与轨道读数没有公式，永远返回 false
    public static boolean references(ValueSource source, String name) {
        return source instanceof FormulaValue formula && Expression.references(formula.expression(), name);
    }

    // endregion
}
