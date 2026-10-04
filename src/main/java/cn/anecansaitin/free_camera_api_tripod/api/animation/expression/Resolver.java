package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// 名字解析：变量名、自定义函数名，外加轨道读数。
///
/// 从 [Expression] 的嵌套接口提到了顶层：写在 `expression` 包里比 `Expression.Resolver` 少一层拐弯，
/// 而且"变量绑的轨道也要按名字取"本来就是同一件事，所以它直接继承 [TrackLookup]。
///
/// 原来的 `eval.Scope` 上那个"按本环境求值一段公式"的便捷方法回到了 [Expression]：
/// 求值需要解析器，解析器从这里拿。
///
/// **内置量的名字挂在这里**（而不是求值环境上）："实现必须先认掉这三个名字"是解析器的契约，
/// 所以名字空间规则（变量与形参不得取这些名字）在 `expression` 层就能判，不必反向依赖 `eval`
@NullMarked
public interface Resolver extends TrackLookup {
    /// 内置量：当前求值时间
    String TIME_VARIABLE = "t";
    /// 内置量：播放进度（0~1）
    String PROGRESS_VARIABLE = "p";
    /// 内置量：世界时间（0~1）
    String WORLD_TIME_VARIABLE = "wt";
    /// 内置量的名字；实现必须先认掉它们，变量与函数形参都不能取这些名字
    List<String> BUILTIN_NAMES = List.of(TIME_VARIABLE, PROGRESS_VARIABLE, WORLD_TIME_VARIABLE);

    /// 这个名字是不是内置量
    static boolean isBuiltinName(String name) {
        return BUILTIN_NAMES.contains(name);
    }

    /// 名字 → 一个数。内置量 `t` / `p` / `wt` 由实现先认掉；未知名字返回 [Float#NaN]
    float resolve(String name);

    /// 自定义函数查询；没有重写时一律当作"不存在"。函数名不在内置清单里时调用时查这里
    @Nullable CustomFunction function(String name);
}
