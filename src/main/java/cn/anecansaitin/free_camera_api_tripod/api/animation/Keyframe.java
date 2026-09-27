package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ConstantValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.FormulaValue;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;

/// 曲线上的一个关键帧：时间 + 值，以及两端各一条贝塞尔曲柄。
///
/// 曲柄由「长度 + 斜率」两个数描述：长度是相邻帧间隔 1/3 的倍数（默认 1，即基准长度），
/// 斜率是曲柄相对该键的方向。曲线图上曲柄的屏幕位置就是这两个数——拖拽改的是它们，求值读的也是它们。
///
/// 取值、斜率、长度这五个数值各自是一个 [ValueSource]——固定值或一条公式。
/// 对外因此有两套读写：
/// - `value()` / `value(float)` 这一组看的是**固定数值**（公式的回退值）：几何、绘制与界面编辑用它
/// - `valueSource()` / `valueSource(ValueSource)` 这一组给的是值源本身：求值与序列化用它
///
/// 可变对象：[cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve] 直接持有实例，
/// 改时间 / 值 / 曲柄都是就地修改；需要只读视图时用 [Keyframec]。
@NullMarked
@SuppressWarnings("unused")
public class Keyframe implements Keyframec {
    /// 按时间升序比较，曲线靠它维持关键帧有序
    public static final Comparator<Keyframe> TIME_COMPARATOR = (Keyframe k1, Keyframe k2) -> Float.compare(k1.time(), k2.time());
    /// 曲柄的默认长度：1 倍基准长度，也就是相邻帧间隔的 1/3
    public static final float DEFAULT_LENGTH = 1f;

    private float time;
    private ValueSource value;
    private ValueSource inSlope;
    private ValueSource inLength;
    private ValueSource outSlope;
    private ValueSource outLength;
    private EvaluateMode evaluateMode;

    public Keyframe(float time, float value) {
        this(time, value, 0, 0);
    }

    public Keyframe(float time, float value, float inSlope, float outSlope) {
        this(time, value, inSlope, DEFAULT_LENGTH, outSlope, DEFAULT_LENGTH, EvaluateMode.LINEAR);
    }

    public Keyframe(float time, float value, float inSlope, float inLength, float outSlope, float outLength,
                    EvaluateMode evaluateMode) {
        this.time = time;
        this.value = new ConstantValue(value);
        this.inSlope = new ConstantValue(inSlope);
        this.inLength = new ConstantValue(inLength);
        this.outSlope = new ConstantValue(outSlope);
        this.outLength = new ConstantValue(outLength);
        this.evaluateMode = evaluateMode;
    }

    /// 从任意只读关键帧拷贝一份：曲线收下外部关键帧时用它转成自己的可变副本。
    /// 是 [Keyframe] 时连公式一并拷过来
    public Keyframe(Keyframec keyframe) {
        this(keyframe.time(), keyframe.value(), keyframe.inSlope(), keyframe.inLength(),
                keyframe.outSlope(), keyframe.outLength(), keyframe.evaluateMode());

        if (keyframe instanceof Keyframe source) {
            copySources(source);
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
        return value.constant();
    }

    /// 只改固定数值（公式保留，换的只是它的回退值）
    public Keyframe value(float value) {
        this.value = ValueSource.withConstant(this.value, value);
        return this;
    }

    @Override
    public float inSlope() {
        return inSlope.constant();
    }

    public Keyframe inSlope(float inSlope) {
        this.inSlope = ValueSource.withConstant(this.inSlope, inSlope);
        return this;
    }

    @Override
    public float outSlope() {
        return outSlope.constant();
    }

    public Keyframe outSlope(float outSlope) {
        this.outSlope = ValueSource.withConstant(this.outSlope, outSlope);
        return this;
    }

    @Override
    public float inLength() {
        return inLength.constant();
    }

    public Keyframe inLength(float inLength) {
        this.inLength = ValueSource.withConstant(this.inLength, inLength);
        return this;
    }

    @Override
    public float outLength() {
        return outLength.constant();
    }

    public Keyframe outLength(float outLength) {
        this.outLength = ValueSource.withConstant(this.outLength, outLength);
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

    /// 覆盖式拷贝：把另一个关键帧的全部字段写到本实例上，曲线用它更新同时间的已有帧。
    /// 来源是 [Keyframe] 时连公式一并拷过来，否则（只读视图）只剩固定数值，公式清空
    public Keyframe set(Keyframec keyframe) {
        this.time = keyframe.time();
        this.evaluateMode = keyframe.evaluateMode();

        if (keyframe instanceof Keyframe source) {
            copySources(source);
        } else {
            this.value = new ConstantValue(keyframe.value());
            this.inSlope = new ConstantValue(keyframe.inSlope());
            this.inLength = new ConstantValue(keyframe.inLength());
            this.outSlope = new ConstantValue(keyframe.outSlope());
            this.outLength = new ConstantValue(keyframe.outLength());
        }

        return this;
    }

    // endregion

    // region 值源

    public ValueSource valueSource() {
        return value;
    }

    public Keyframe valueSource(ValueSource source) {
        this.value = source;
        return this;
    }

    public ValueSource inSlopeSource() {
        return inSlope;
    }

    public Keyframe inSlopeSource(ValueSource source) {
        this.inSlope = source;
        return this;
    }

    public ValueSource outSlopeSource() {
        return outSlope;
    }

    public Keyframe outSlopeSource(ValueSource source) {
        this.outSlope = source;
        return this;
    }

    public ValueSource inLengthSource() {
        return inLength;
    }

    public Keyframe inLengthSource(ValueSource source) {
        this.inLength = source;
        return this;
    }

    public ValueSource outLengthSource() {
        return outLength;
    }

    public Keyframe outLengthSource(ValueSource source) {
        this.outLength = source;
        return this;
    }

    /// 是否有任一个数值挂了公式
    public boolean dynamic() {
        return value instanceof FormulaValue
                || inSlope instanceof FormulaValue
                || outSlope instanceof FormulaValue
                || inLength instanceof FormulaValue
                || outLength instanceof FormulaValue;
    }

    private void copySources(Keyframe source) {
        this.value = ValueSource.copy(source.value);
        this.inSlope = ValueSource.copy(source.inSlope);
        this.inLength = ValueSource.copy(source.inLength);
        this.outSlope = ValueSource.copy(source.outSlope);
        this.outLength = ValueSource.copy(source.outLength);
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

    /// 贝塞尔插值：只给斜率，曲柄长度取默认的基准长度
    public static Keyframe hermite(float time, float value, float inSlope, float outSlope) {
        return create(time, value).inSlope(inSlope).outSlope(outSlope).evaluateMode(EvaluateMode.HERMITE);
    }

    // endregion
}
