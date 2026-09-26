package cn.anecansaitin.free_camera_api_tripod.api.animation.curve;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Evaluator;
import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ExpressionContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;

@NullMarked
public class Clip {
    /// 动画片段时长，根据曲线自动计算则为负数，用户设置则为正数
    private float duration;
    /// 动画名称
    private String name;
    /// 曲线
    private final HashMap<String, Curve> curves;

    public Clip() {
        this(0, "", new HashMap<>());
    }

    public Clip(String name) {
        this(0, name, new HashMap<>());
    }

    public Clip(float duration, String name, HashMap<String, Curve> curves) {
        this.duration = duration;
        this.name = name;
        this.curves = curves;
    }

    /// 添加或替换曲线
    ///
    /// @param property 属性名称 (例如 "position.x")
    /// @param curve    曲线
    public void addCurve(String property, Curve curve) {
        curves.put(property, curve);
    }

    /// 获取曲线
    ///
    /// @param property 属性名称 (例如 "position.x")
    /// @return 曲线
    public Curve curve(String property) {
        return curves.get(property);
    }

    public void removeCurve(String property) {
        curves.remove(property);
    }

    public float evaluate(String property, float time) {
        return evaluate(property, time, null);
    }

    /// 带上下文的求值：该属性上挂了公式的关键帧按公式算
    public float evaluate(String property, float time, @Nullable ExpressionContext context) {
        Curve curve = curves.get(property);
        return curve != null ? curve.evaluate(time, context) : 0;
    }

    public <T> T evaluate(float time, Evaluator<T> evaluator) {
        return evaluate(time, evaluator, null);
    }

    public <T> T evaluate(float time, Evaluator<T> evaluator, @Nullable ExpressionContext context) {
        String[] properties = evaluator.properties();
        float[] values = new float[properties.length];

        for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
            String property = properties[i];
            Curve curve = curves.get(property);
            values[i] = curve != null ? curve.evaluate(time, context) : 0;
        }

        return evaluator.build(values);
    }

    /// 动画片段时长。
    ///
    /// 用户显式设置时为正数，直接返回；否则（自动模式）按当前曲线实时计算，
    /// 这样移动键或直接编辑曲线后时长会立即更新。
    public float duration() {
        if (duration > 0) {
            return duration;
        }

        return calculateDuration();
    }

    public Clip duration(float duration) {
        this.duration = duration;
        return this;
    }

    private float calculateDuration() {
        float length = 0;

        for (Curve curve : curves.values()) {
            if (curve.size() <= 0) {
                continue;
            }

            Keyframe key = curve.key(curve.size() - 1);
            length = Math.max(length, key.time());
        }

        return length;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public boolean addKey(String property, Keyframe key) {
        Curve curve = curves.get(property);

        if (curve == null) {
            return false;
        }

        return curve.key(key) >= 0;
    }

    public boolean removeKey(String property, int index) {
        Curve curve = curves.get(property);

        if (curve == null) {
            return false;
        }

        return curve.removeKey(index);
    }
}
