package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import org.jspecify.annotations.NullMarked;

/// 动画里的变量：名字 + 一个取值来源。
///
/// 取值来源二选一，由 {@link #trackId()} 是否为空决定：
/// - 绑定轨道：播放时取该轨道在当前时刻的读数
/// - 固定值：直接用 {@link #value()}（没绑轨道，也没有轨道可读时用）
///
/// 名字不限定字符集，中文也可以，但它要能被表达式识别为标识符（字母、下划线或非 ASCII 字符开头）。
@NullMarked
public class Variable {
    private String name;
    private String trackId;
    /// 未绑定轨道时的固定取值
    private float value;

    public Variable(String name) {
        this(name, "", 0f);
    }

    public Variable(String name, float value) {
        this(name, "", value);
    }

    public Variable(String name, String trackId, float value) {
        this.name = name;
        this.trackId = trackId;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public Variable name(String name) {
        this.name = name;
        return this;
    }

    /// 绑定的曲线轨道 id；空串表示不绑定，改用固定值
    public String trackId() {
        return trackId;
    }

    public Variable trackId(String trackId) {
        this.trackId = trackId;
        return this;
    }

    /// 是否绑定了轨道
    public boolean bound() {
        return !trackId.isEmpty();
    }

    /// 未绑定轨道时的固定取值
    public float value() {
        return value;
    }

    public Variable value(float value) {
        this.value = value;
        return this;
    }

    public Variable copy() {
        return new Variable(name, trackId, value);
    }
}
