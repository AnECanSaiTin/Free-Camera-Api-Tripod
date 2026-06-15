package cn.anecansaitin.free_camera_api_tripod.core.animation;

import cn.anecansaitin.free_camera_api_tripod.api.Keyframe;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@NullMarked
public class Clip {
    /// 动画片段时长，根据曲线自动计算则为负数，用户设置则为正数
    private float duration;
    // 循环模式
//    private WrapMode wrapMode;
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
    /// @return 是否添加成功,仅当属性名称为空字符串时返回false
    public boolean addCurve(String property, Curve curve) {
        if (property.isEmpty()) {
            return false;
        }

        curves.put(property, curve);
        updateDuration();
        return true;
    }

    /// 获取曲线
    ///
    /// @param property 属性名称 (例如 "position.x")
    /// @return 曲线
    public Curve curve(String property) {
        return curves.get(property);
    }

    public @Nullable Curve removeCurve(String property) {
        Curve removed = curves.remove(property);

        if (removed != null) {
            updateDuration();
        }

        return removed;
    }

    public float evaluate(String property, float time) {
        Curve curve = curves.get(property);
        return curve != null ? curve.evaluate(time) : 0;
    }

    public <T> T evaluate(float time, Evaluator<T> evaluator) {
        String[] properties = evaluator.properties();
        float[] values = new float[properties.length];

        for (int i = 0, propertiesLength = properties.length; i < propertiesLength; i++) {
            String property = properties[i];
            Curve curve = curves.get(property);
            values[i] = curve != null ? curve.evaluate(time) : 0;
        }

        return evaluator.build(values);
    }

    private final Object2FloatOpenHashMap<String> evaluateCache = new Object2FloatOpenHashMap<>();

    public Object2FloatOpenHashMap<String> evaluateAll(float time) {
        for (Map.Entry<String, Curve> entry : curves.entrySet()) {
            evaluateCache.put(entry.getKey(), entry.getValue().evaluate(time));
        }

        return evaluateCache;
    }

    public float duration() {
        return Math.abs(duration);
    }

    private void updateDuration() {
        if (duration > 0) {
            return;
        }

        duration = -calculateDuration();
    }

    private float calculateDuration() {
        float length = 0;

        for (Curve curve : curves.values()) {
            if (curve.size() <= 0) {
                continue;
            }

            length = Math.max(length, curve.key(curve.size() - 1).time());
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

        boolean success = curve.key(key) >= 0;

        if (success) {
            updateDuration();
        }

        return success;
    }

    public boolean removeKey(String property, int index) {
        Curve curve = curves.get(property);

        if (curve == null) {
            return false;
        }

        boolean result = curve.removeKey(index);

        if (result) {
            updateDuration();
        }

        return result;
    }
}
