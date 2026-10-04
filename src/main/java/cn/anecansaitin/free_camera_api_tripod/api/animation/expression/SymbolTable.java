package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 一段动画的符号表：[变量][Variable]与[自定义函数][CustomFunction]两张表，外加它们之间的引用关系。
///
/// 这里管的不只是"两个列表"，而是**名字空间与引用完整性**：
/// - 名字唯一：变量之间、函数之间各自查重，改名要挡住内置名字与同名冲突，见 [functionNameTaken]
/// - **内置量不可占用**：变量与函数形参都不得叫 `t` / `p` / `wt`，同一个函数内形参不得重名，
///   见 [parameterConflict]。**形参与用户变量重名是允许的**——那是函数体里最自然的一层局部名字
/// - 环的含义：变量之间按名字互引，成环由 `eval.EvaluationGraph` 检查，求值时再兜底返回 NaN
/// - 指向轨道的引用跟着轨道走：轨道改名要一起改指向（[rebindTrack]），
///   轨道删掉要解绑（[unbindTrack]），否则变量会变成空指向、取值恒为 NaN
///
/// **判定与执行分开**：这里给出判定（[parameterConflict] / [shadowing]），
/// 执行判定的是写入路径——编辑器在改形参名、变量改名时拦，读档在收尾时整表扫一遍。
/// 那两条路都是"整批塞进来"，逐条拦会变成"谁先读进来"的顺序依赖（见 [addVariable(String)]）。
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

    // endregion

    // region 变量

    /// 新增一个变量，名字取 `var1`、`var2`……；表里满到放不下时返回 null。默认不绑定轨道，固定值为 0。
    /// 跳过已被变量占用与内置量的名字；**与某个形参重名不算占用**（形参只在那个函数体里优先）
    public @Nullable Variable addVariable() {
        for (int i = 1; i < NAME_LIMIT; i++) {
            String name = VARIABLE_NAME_PREFIX + i;

            if (!variableNameTaken(name)) {
                return addVariable(name);
            }
        }

        return null;
    }

    /// 新增一个变量；名字为空或已被变量占用时返回 null。默认不绑定轨道（固定值模式），固定值为 0。
    ///
    /// **这里不查内置量**（那是编辑器与读档收尾 [shadowing] 的事）：读档与 [replaceFrom] 是整批
    /// 塞进来的，逐条拦会变成"函数先读还是变量先读"的顺序依赖，还可能出现静默丢数据
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

    /// 把变量名改掉；新名字为空、已被别的变量占用、或是内置量时返回 false。
    /// 与某个形参重名可以（形参只在那个函数体里优先）。
    /// 表达式是按名字引用变量的，改名后旧公式里的名字就取不到值了，由调用方提示用户
    public boolean renameVariable(String name, String newName) {
        String trimmed = newName == null ? "" : newName.strip();
        Variable variable = variable(name);

        if (variable == null || trimmed.isEmpty() || name.equals(trimmed)) {
            return false;
        }

        // 撞变量、撞形参、撞内置量都不能改：前两者会让某个名字有两个含义，后者让这个变量永远取不到值
        if (variableNameTaken(trimmed)) {
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

    /// 按给定内容新建一个函数（读档也走这里）；名字已被占用时返回已有的那个，不覆盖。
    ///
    /// **形参不在这里判冲突**（理由同 [addVariable(String)]）：读档整批塞进来时，
    /// 变量与函数谁先读进来是不确定的。判定的执行点在编辑器（改形参时）与读档收尾（[shadowing]）
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

    // region 名字冲突

    /// 一处名字冲突：谁的名字撞上了内置量，或同一个函数里形参重名。
    ///
    /// [function] 是冲突所在的函数名；**变量自己撞上内置量时它是 null**（那时和函数无关）
    public record Shadowing(@Nullable String function, String name, Kind kind) {
        public enum Kind {
            /// 形参撞上内置量（`t` / `p` / `wt`）
            PARAMETER_BUILTIN,
            /// 同一个函数里两个形参重名
            PARAMETER_DUPLICATE,
            /// 变量撞上内置量
            VARIABLE_BUILTIN
        }
    }

    /// 这组形参挂在 [function] 上合不合法；不合法时给出第一处冲突，否则 null。
    ///
    /// 两条规则：形参不得是内置量（那是求值环境的名字，不是用户数据）、同一函数内不得重名。
    ///
    /// **形参与用户变量重名是允许的**——那是函数体里最自然的一层局部名字，运行期形参优先
    /// （见 `Evaluation#resolve`）。不同函数之间也允许重名：内层函数体读到外层参数是调用栈上的
    /// 词法嵌套，一直如此
    public static @Nullable Shadowing parameterConflict(String function, List<String> parameters) {
        Set<String> seen = new LinkedHashSet<>();

        for (String name : parameters) {
            if (Resolver.isBuiltinName(name)) {
                return new Shadowing(function, name, Shadowing.Kind.PARAMETER_BUILTIN);
            }

            if (!seen.add(name)) {
                return new Shadowing(function, name, Shadowing.Kind.PARAMETER_DUPLICATE);
            }
        }

        return null;
    }

    /// 整张表扫一遍有没有上面那两类冲突（逐条拦不住的两条路——读档、[replaceFrom]——
    /// 在塞完之后用这个判一次）。没有返回 null
    public @Nullable Shadowing shadowing() {
        for (CustomFunction function : functions) {
            Shadowing conflict = parameterConflict(function.name(), function.parameters());

            if (conflict != null) {
                return conflict;
            }
        }

        for (Variable variable : variables) {
            if (Resolver.isBuiltinName(variable.name())) {
                return new Shadowing(null, variable.name(), Shadowing.Kind.VARIABLE_BUILTIN);
            }
        }

        return null;
    }

    /// 这个名字能不能拿来做变量：既没被别的变量占用，也不是内置量。
    /// 与某个形参重名是可以的（形参只在那个函数体里优先，别处仍取变量）
    private boolean variableNameTaken(String name) {
        return variable(name) != null || Resolver.isBuiltinName(name);
    }

    // endregion

    // region 引用完整性

    /// 曲线轨道改名时调用：把指向旧 id 的变量一起改指向。
    /// 漏了这一步的话，变量绑定的名字在轨道表里已经不存在，取值恒为 NaN
    public void rebindTrack(String oldId, String newId) {
        for (Variable variable : variables) {
            if (oldId.equals(variable.trackId())) {
                variable.source(new TrackRef(newId));
            }
        }
    }

    /// 曲线轨道被删除时调用：把指向它的变量解绑成固定值 0。
    /// 留着指向的话，引用这些变量的公式全都算不出来
    public void unbindTrack(String trackId) {
        for (Variable variable : variables) {
            if (trackId.equals(variable.trackId())) {
                variable.source(new Constant(0));
            }
        }
    }

    /// 用另一份表的内容整体替换自身；变量与函数都是可变对象，装进来的是副本，避免两份动画共享同一个实例。
    ///
    /// 整批替换不逐条判冲突，调用方接完内容后用 [shadowing] 扫一次（读档也是这么做的）
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

    /// 数值来源是否以公式的形式**自由引用**了叫 [name] 的变量；固定值与轨道读数没有公式，永远返回 false。
    ///
    /// 依赖分析与判环走 [`eval.EvaluationGraph`][cn.anecansaitin.free_camera_api_tripod.api.animation.eval.EvaluationGraph]，
    /// 那里按**整张图**（变量 + 轨道 + 函数）判；这里只回答"这一段公式引没引用这个变量"这一件小事。
    ///
    /// 判定走 [Expression#names]（语法树），所以形参遮蔽同名变量时不会被误算成引用。
    /// **内置量不算**：`t` / `p` / `wt` 是求值环境的名字，[Expression#names] 把它们单独归到
    /// `builtins` 桶里，这里问的是变量
    public static boolean references(@Nullable ValueSource source, String name) {
        return source instanceof Formula formula && Expression.names(formula.expression()).variables().contains(name);
    }
}
