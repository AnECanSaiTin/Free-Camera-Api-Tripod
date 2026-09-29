package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 动画里的变量：名字 + 一个取值来源。
///
/// 取值来源可以是固定值、公式，或某条曲线轨道的读数（见 [ValueSource]）；
/// 公式里按名字引用别的变量，变量之间因此构成一张有向图，成环由 [VariableGraph] 检查、
/// 由 [ExpressionScope] 在求值时兜底。
///
/// 名字不限定字符集，中文也可以，但它要能被表达式识别为标识符（字母、下划线或非 ASCII 字符开头）。
@NullMarked
public class Variable {
    private String name;
    private ValueSource source;

    public Variable(String name) {
        this(name, new ConstantValue(0));
    }

    public Variable(String name, ValueSource source) {
        this.name = name;
        this.source = source;
    }

    public String name() {
        return name;
    }

    public Variable name(String name) {
        this.name = name;
        return this;
    }

    /// 取值来源
    public ValueSource source() {
        return source;
    }

    public Variable source(ValueSource source) {
        this.source = source;
        return this;
    }

    /// 绑定的曲线轨道 id；取值来源不是轨道时返回 null
    public @Nullable String trackId() {
        return source instanceof TrackValue track ? track.trackId() : null;
    }

    public Variable copy() {
        return new Variable(name, source.copy());
    }

    @Override
    public String toString() {
        return name + " = " + source;
    }
}
