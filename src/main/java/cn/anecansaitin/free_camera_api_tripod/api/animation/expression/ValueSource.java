package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.Scope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 一个数值的来源：固定值、公式，或（只作变量取值来源的）曲线轨道读数。
///
/// 求值约定：
/// - 作用域为 `null` 表示**静态求值**——只有固定数值可用，公式一律算不出来（返回 NaN）
/// - 公式非法、变量取不到值、轨道不存在，一律返回 [Float#NaN]；**本接口不做兜底**，
///   要不要退回固定数值由调用方决定（求值链上唯一的回退点是 [ValueSource#evaluateOrFallback]）
///
/// 用**值源对象**而不是"数值 + 字段枚举"来存动态值，好处是公式跟着它自己的数值走，
/// 新增一个可动态的字段不需要动枚举、也不需要改两个类里的读写三件套。
///
/// 三种来源各自实现自己的行为（[withConstant] / [copy] 都是实例方法），
/// 外面不必为一个"该按哪种来源处理"的 switch 操心——加第四种来源时改的是它自己那一个类。
@NullMarked
public sealed interface ValueSource permits ConstantValue, FormulaValue, TrackValue {
    /// 求值；算不出来返回 NaN
    float evaluate(@Nullable Scope scope);

    /// 该来源携带的固定数值：常量就是它本身，公式是它的回退值，轨道引用没有（NaN）
    float constant();

    /// 把固定数值写进这个值源：能就地改的就地改（固定值改它本身、公式改它的回退值），
    /// 改不了的换成一个新的固定值源。**返回的是应当持有的那个值源**——它可能不是原对象
    ValueSource withConstant(float value);

    /// 深拷贝：值源是可变的，装进别的对象时要给副本
    ValueSource copy();

    /// 求值并回退固定数值——求值链上唯一的回退点
    static float evaluateOrFallback(ValueSource source, @Nullable Scope scope) {
        float value = source.evaluate(scope);

        if (!Float.isNaN(value)) {
            return value;
        }

        float fallback = source.constant();
        return Float.isNaN(fallback) ? 0f : fallback;
    }
}
