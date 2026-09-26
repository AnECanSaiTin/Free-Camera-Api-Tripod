package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.DynamicField;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ExpressionContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

/// 曲线上的一个关键帧：时间 + 值，以及决定插值的切线、权重与求值模式。
///
/// 可变对象：{@link cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve} 直接持有实例，
/// 改时间 / 值 / 切线都是就地修改；需要只读视图时用 {@link Keyframec}，
/// 时间轴上的各类键（含未来的事件键、特效键）统一由 {@link TrackKey} 描述。
///
/// 常用静态工厂建关键帧：{@link #create} 建默认插值的关键帧，
/// {@link #linear} / {@link #step} / {@link #hermite} 直接给出对应的求值模式。
@NullMarked
@SuppressWarnings("unused")
public class Keyframe implements Keyframec {
    /// 按时间升序比较，曲线靠它维持关键帧有序
    public static final Comparator<Keyframe> TIME_COMPARATOR = (Keyframe k1, Keyframe k2) -> Float.compare(k1.time(), k2.time());
    /// Hermite 插值的默认权重
    private static final float DEFAULT_WEIGHT = 1f / 3f;

    private float time;
    private float value;
    private float inTangent;
    private float inWeight;
    private float outTangent;
    private float outWeight;
    private WeightedMode weightedMode;
    private EvaluateMode evaluateMode;
    /// 挂了公式的字段：播放时按公式求值，没挂公式的字段用上面的固定数值
    private final Map<DynamicField, String> expressions = new EnumMap<>(DynamicField.class);

    public Keyframe(float time, float value) {
        this(time, value, 0, 0);
    }

    public Keyframe(float time, float value, float inTangent, float outTangent) {
        this(time, value, inTangent, DEFAULT_WEIGHT, outTangent, DEFAULT_WEIGHT, WeightedMode.NONE, EvaluateMode.LINEAR);
    }

    public Keyframe(float time, float value, float inTangent, float inWeight, float outTangent, float outWeight,
                    WeightedMode weightedMode, EvaluateMode evaluateMode) {
        this.time = time;
        this.value = value;
        this.inTangent = inTangent;
        this.inWeight = inWeight;
        this.outTangent = outTangent;
        this.outWeight = outWeight;
        this.weightedMode = weightedMode;
        this.evaluateMode = evaluateMode;
    }

    /// 从任意只读关键帧拷贝一份：曲线收下外部关键帧时用它转成自己的可变副本
    public Keyframe(Keyframec keyframe) {
        this(keyframe.time(), keyframe.value(), keyframe.inTangent(), keyframe.inWeight(),
                keyframe.outTangent(), keyframe.outWeight(), keyframe.weightedMode(), keyframe.evaluateMode());

        if (keyframe instanceof Keyframe source) {
            expressions.putAll(source.expressions);
        }
    }

    // region 读写

    @Override
    public float time() {
        return time;
    }

    public Keyframe time(float time) {
        this.time = time;
        return this;
    }

    @Override
    public float value() {
        return value;
    }

    public Keyframe value(float value) {
        this.value = value;
        return this;
    }

    @Override
    public float inTangent() {
        return inTangent;
    }

    public Keyframe inTangent(float inTangent) {
        this.inTangent = inTangent;
        return this;
    }

    @Override
    public float outTangent() {
        return outTangent;
    }

    public Keyframe outTangent(float outTangent) {
        this.outTangent = outTangent;
        return this;
    }

    @Override
    public float inWeight() {
        return inWeight;
    }

    public Keyframe inWeight(float inWeight) {
        this.inWeight = inWeight;
        return this;
    }

    @Override
    public float outWeight() {
        return outWeight;
    }

    public Keyframe outWeight(float outWeight) {
        this.outWeight = outWeight;
        return this;
    }

    @Override
    public WeightedMode weightedMode() {
        return weightedMode;
    }

    public Keyframe weightedMode(WeightedMode weightedMode) {
        this.weightedMode = weightedMode;
        return this;
    }

    @Override
    public EvaluateMode evaluateMode() {
        return evaluateMode;
    }

    public Keyframe evaluateMode(EvaluateMode evaluateMode) {
        this.evaluateMode = evaluateMode;
        return this;
    }

    /// 覆盖式拷贝：把另一个关键帧的全部字段写到本实例上，曲线用它更新同时间的已有帧
    public Keyframe set(Keyframec keyframe) {
        this.time = keyframe.time();
        this.value = keyframe.value();
        this.inTangent = keyframe.inTangent();
        this.inWeight = keyframe.inWeight();
        this.outTangent = keyframe.outTangent();
        this.outWeight = keyframe.outWeight();
        this.weightedMode = keyframe.weightedMode();
        this.evaluateMode = keyframe.evaluateMode();
        expressions.clear();

        if (keyframe instanceof Keyframe source) {
            expressions.putAll(source.expressions);
        }

        return this;
    }

    // endregion

    // region 动态字段

    /// 该字段挂的公式；没挂返回 null
    public @Nullable String expression(DynamicField field) {
        return expressions.get(field);
    }

    /// 给字段挂公式（null 或空白表示回到固定数值）
    public Keyframe expression(DynamicField field, @Nullable String expression) {
        if (expression == null || expression.isBlank()) {
            expressions.remove(field);
        } else {
            expressions.put(field, expression.strip());
        }

        return this;
    }

    /// 该字段是否挂了公式
    public boolean dynamic(DynamicField field) {
        return expressions.containsKey(field);
    }

    /// 公式表副本，供序列化与界面判断使用
    public Map<DynamicField, String> expressions() {
        return Map.copyOf(expressions);
    }

    /// 求值时的取值：挂了公式就按公式算，算不出来（公式非法或变量缺失）回退固定值
    public float value(ExpressionContext context) {
        return resolve(DynamicField.KEY_VALUE, value, context);
    }

    public float inTangent(ExpressionContext context) {
        return resolve(DynamicField.KEY_IN_TANGENT, inTangent, context);
    }

    public float outTangent(ExpressionContext context) {
        return resolve(DynamicField.KEY_OUT_TANGENT, outTangent, context);
    }

    public float inWeight(ExpressionContext context) {
        return resolve(DynamicField.KEY_IN_WEIGHT, inWeight, context);
    }

    public float outWeight(ExpressionContext context) {
        return resolve(DynamicField.KEY_OUT_WEIGHT, outWeight, context);
    }

    private float resolve(DynamicField field, float fallback, ExpressionContext context) {
        String expression = expressions.get(field);

        if (expression == null) {
            return fallback;
        }

        float evaluated = context.evaluate(expression);
        return Float.isNaN(evaluated) ? fallback : evaluated;
    }

    // endregion

    // region 静态工厂

    public static Keyframe create(float time, float value) {
        return new Keyframe(time, value);
    }

    public static Keyframe linear(float time, float value) {
        return create(time, value).evaluateMode(EvaluateMode.LINEAR);
    }

    public static Keyframe step(float time, float value) {
        return create(time, value).evaluateMode(EvaluateMode.STEP);
    }

    public static Keyframe hermite(float time, float value, float inTangent, float outTangent) {
        return create(time, value).inTangent(inTangent).outTangent(outTangent).evaluateMode(EvaluateMode.HERMITE);
    }

    public static Keyframe hermite(float time, float value, float inTangent, float inWeight, float outTangent, float outWeight, WeightedMode weightedMode) {
        return create(time, value).inTangent(inTangent).inWeight(inWeight).outTangent(outTangent).outWeight(outWeight).weightedMode(weightedMode).evaluateMode(EvaluateMode.HERMITE);
    }

    // endregion
}
