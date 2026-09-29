package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import cn.anecansaitin.free_camera_api_tripod.api.animation.eval.Scope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 固定数值：没有公式的普通数值。
@NullMarked
public final class ConstantValue implements ValueSource {
    private float value;

    public ConstantValue(float value) {
        this.value = value;
    }

    public float value() {
        return value;
    }

    public ConstantValue value(float value) {
        this.value = value;
        return this;
    }

    @Override
    public float evaluate(@Nullable Scope scope) {
        return value;
    }

    @Override
    public float constant() {
        return value;
    }

    /// 就地改这个固定值
    @Override
    public ValueSource withConstant(float value) {
        return this.value(value);
    }

    @Override
    public ValueSource copy() {
        return new ConstantValue(value);
    }

    @Override
    public String toString() {
        return Float.toString(value);
    }
}
