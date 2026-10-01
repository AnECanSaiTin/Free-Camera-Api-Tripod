package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 名字解析：变量名、自定义函数名，外加轨道读数。
///
/// 从 [Expression] 的嵌套接口提到了顶层：写在 `expression` 包里比 `Expression.Resolver` 少一层拐弯，
/// 而且"变量绑的轨道也要按名字取"本来就是同一件事，所以它直接继承 [TrackLookup]。
///
/// 原来的 `eval.Scope` 上那个"按本环境求值一段公式"的便捷方法回到了 [Expression]：
/// 求值需要解析器，解析器从这里拿。
@NullMarked
public interface Resolver extends TrackLookup {
    /// 名字 → 一个数。内置量 `t` / `p` / `wt` 由实现先认掉；未知名字返回 [Float#NaN]
    float resolve(String name);

    /// 自定义函数查询；没有重写时一律当作"不存在"。函数名不在内置清单里时调用时查这里
    @Nullable CustomFunction function(String name);
}
