package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 固定数值：没有公式的普通数值。
///
/// 是不可变 record——原来是可变的 `ConstantValue`，就地改数值正是"一个字段要改回退值时
/// 必须换对象"的根源。现在要换数就换一个实例（见 `Keyframe#constant`）。
@NullMarked
public record Constant(float value) implements NumberSource {
    @Override
    public float evaluate(@Nullable Scope scope) {
        return value;
    }

    @Override
    public float constant() {
        return value;
    }

    @Override
    public NumberSource copy() {
        return this;
    }

    @Override
    public String toString() {
        return Float.toString(value);
    }
}
