package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Constant;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.Formula;
import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.NumberSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

/// 曲线上的一个关键帧：时间 + 值，以及两端各一条贝塞尔曲柄。
///
/// 曲柄由「长度 + 斜率」两个数描述：长度是相邻帧间隔 1/3 的倍数（默认 1，即基准长度），
/// 斜率是曲柄相对该键的方向。曲线图上曲柄的屏幕位置就是这两个数——拖拽改的是它们，求值读的也是它们。
///
/// **字段是「槽位 → 数值来源」，全部按 [KeyField] 寻址**：读用 [#constant(KeyField)]（挂了公式时给回退值），
/// 写用 [#constant(KeyField, float)] 与 [#source(KeyField, NumberSource)]。
/// 五个槽位因此不再各有五个读写方法。
///
/// ## 一处刻意的取舍：写数值会清掉公式
///
/// [#constant(KeyField, float)] **总是换成新的 [Constant]**，也就是"写数值会清掉该槽位原有的公式"。
/// 原来的 `withConstant` 语义是"改回退值、公式保留"，但那要求值源自己是可变的、只能就地改，
/// 正是它让"一个字段要改回退值时必须换对象"这件事漏到了接口上。现在：
///
/// - **要保留公式、只改回退值** → `source(field, new Formula(f.expression(), v))`
/// - 界面上的语义由 `ExpressionFieldWidget` 自己挑：数值输入框提交时调 `constant(field, v)`
///   （数值模式本来就没有公式），公式编辑窗口提交时调 `formula(field, text)`
///
/// 也就是说"公式会不会被弄丢"从**数据层的隐式行为**变成了**调用点显式选择**。
///
/// 可变对象：[cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve] 直接持有实例，
/// 改时间 / 值 / 曲柄都是就地修改；需要只读视图时用 [Keyframec]。
///
/// 是 `final` 的：[Keyframec] 是 `sealed permits Keyframe`，`eval` 里"这个键是不是具体实现"
/// 的判断因此天然穷尽，五个取数的 `switch` 也不必写 `default`
@NullMarked
@SuppressWarnings("unused")
public final class Keyframe implements Keyframec, JsonSerializable {
    /// 按时间升序比较，曲线靠它维持关键帧有序
    public static final Comparator<Keyframe> TIME_COMPARATOR = (Keyframe k1, Keyframe k2) -> Float.compare(k1.time(), k2.time());
    /// 曲柄的默认长度：1 倍基准长度，也就是相邻帧间隔的 1/3
    public static final float DEFAULT_LENGTH = 1f;
    /// 槽位缺省来源：没有来源等于固定值 0
    private static final NumberSource ZERO = new Constant(0f);
    private static final String FIELD_TIME = "time";
    private static final String FIELD_EVALUATE_MODE = "evaluateMode";
    private static final String FIELD_EXPRESSION = "expression";
    private static final String FIELD_FALLBACK = "fallback";

    private float time;
    private EvaluateMode evaluateMode;
    /// 五个槽位的来源；没写过某个槽位时它缺省是 [ZERO]，所以这里不必预先填满
    private final EnumMap<KeyField, NumberSource> sources = new EnumMap<>(KeyField.class);

    public Keyframe(float time, float value) {
        this(time, value, 0, 0);
    }

    public Keyframe(float time, float value, float inSlope, float outSlope) {
        this(time, value, inSlope, DEFAULT_LENGTH, outSlope, DEFAULT_LENGTH, EvaluateMode.LINEAR);
    }

    public Keyframe(float time, float value, float inSlope, float inLength, float outSlope, float outLength,
                    EvaluateMode evaluateMode) {
        this.time = time;
        this.evaluateMode = evaluateMode;
        this.sources.put(KeyField.VALUE, new Constant(value));
        this.sources.put(KeyField.IN_SLOPE, new Constant(inSlope));
        this.sources.put(KeyField.IN_LENGTH, new Constant(inLength));
        this.sources.put(KeyField.OUT_SLOPE, new Constant(outSlope));
        this.sources.put(KeyField.OUT_LENGTH, new Constant(outLength));
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

    // region 读数

    @Override
    public float time() {
        return time;
    }

    @Override
    public float value() {
        return constant(KeyField.VALUE);
    }

    @Override
    public float inSlope() {
        return constant(KeyField.IN_SLOPE);
    }

    @Override
    public float outSlope() {
        return constant(KeyField.OUT_SLOPE);
    }

    @Override
    public float inLength() {
        return constant(KeyField.IN_LENGTH);
    }

    @Override
    public float outLength() {
        return constant(KeyField.OUT_LENGTH);
    }

    @Override
    public EvaluateMode evaluateMode() {
        return evaluateMode;
    }

    /// 某个槽位的固定数值；挂了公式时给的是它的回退值
    @Override
    public float constant(KeyField field) {
        return source(field).constant();
    }

    /// 五个槽位里有没有挂公式的
    @Override
    public boolean hasFormula() {
        for (NumberSource source : sources.values()) {
            if (source.isFormula()) {
                return true;
            }
        }

        return false;
    }

    // endregion

    // region 写数

    public Keyframe time(float time) {
        this.time = time;
        return this;
    }

    public Keyframe evaluateMode(EvaluateMode evaluateMode) {
        this.evaluateMode = evaluateMode;
        return this;
    }

    /// 把一个槽位写成固定数值。**会清掉该槽位原有的公式**，理由见类文档：
    /// 要保留公式、只改回退值，走 [#source(KeyField, NumberSource)] 自己造一个 [Formula]
    public Keyframe constant(KeyField field, float value) {
        return source(field, new Constant(value));
    }

    /// 取值槽位的便捷写数；语义与 `constant(KeyField.VALUE, value)` 完全一致。
    ///
    /// 这几个单槽位名字留着只是为了调用点读得顺（`key.value(v)` 比
    /// `key.constant(KeyField.VALUE, v)` 短），**不是第二套字段入口**：
    /// 字段本身仍然只有"槽位 + 来源"这一对，五个槽位也没有各配一组读写三件套
    public Keyframe value(float value) {
        return constant(KeyField.VALUE, value);
    }

    public Keyframe inSlope(float inSlope) {
        return constant(KeyField.IN_SLOPE, inSlope);
    }

    public Keyframe outSlope(float outSlope) {
        return constant(KeyField.OUT_SLOPE, outSlope);
    }

    public Keyframe inLength(float inLength) {
        return constant(KeyField.IN_LENGTH, inLength);
    }

    public Keyframe outLength(float outLength) {
        return constant(KeyField.OUT_LENGTH, outLength);
    }

    /// 覆盖式拷贝：把另一个关键帧的全部字段写到本实例上，曲线用它更新同时间的已有帧。
    /// 来源是 [Keyframe] 时连公式一并拷过来，否则（只读视图）只剩固定数值，公式清空
    public Keyframe set(Keyframec keyframe) {
        this.time = keyframe.time();
        this.evaluateMode = keyframe.evaluateMode();

        for (KeyField field : KeyField.values()) {
            this.sources.put(field, keyframe instanceof Keyframe source
                    ? source.source(field)
                    : new Constant(keyframe.constant(field)));
        }

        return this;
    }

    // endregion

    // region 来源（只在可写类上）

    /// 该槽位的数值来源；每个槽位恒有来源（缺省是固定值 0）
    public NumberSource source(KeyField field) {
        return sources.getOrDefault(field, ZERO);
    }

    public Keyframe source(KeyField field, NumberSource source) {
        sources.put(field, source);
        return this;
    }

    /// 挂公式；表达式为空表示不挂公式、退回该槽位当前的固定数值
    public Keyframe formula(KeyField field, @Nullable String expression) {
        float fallback = constant(field);

        return source(field, expression == null || expression.isBlank()
                ? new Constant(fallback)
                : new Formula(expression, fallback));
    }

    /// 五个槽位来源的不可变快照（复制粘贴、调试用）。
    /// 来源本身不可变，所以**快照即拷贝**，不需要额外的 `copy()`
    public Map<KeyField, NumberSource> sources() {
        EnumMap<KeyField, NumberSource> snapshot = new EnumMap<>(KeyField.class);

        for (KeyField field : KeyField.values()) {
            snapshot.put(field, source(field));
        }

        return Map.copyOf(snapshot);
    }

    private void copySources(Keyframe source) {
        for (KeyField field : KeyField.values()) {
            this.sources.put(field, source.source(field));
        }
    }

    // endregion

    // region 序列化

    /// 写出一个关键帧：时间、五个槽位各一份来源，最后是插值模式。
    ///
    /// 槽位的字段名见 [Keyframe#jsonName(KeyField)]，固定值写成数字，公式写成
    /// `{"expression": …, "fallback": …}`。
    ///
    /// **字段顺序刻意与旧格式一致**（插值模式排在五个槽位之后）：JSON 对象的键本无序，
    /// 但设计里那条不变量是"磁盘格式一字不改"，顺序一致才谈得上逐字节等价
    @Override
    public JsonObject write() {
        JsonObject object = new JsonObject();
        object.addProperty(FIELD_TIME, time);

        for (KeyField field : KeyField.values()) {
            object.add(jsonName(field), writeSource(source(field)));
        }

        object.addProperty(FIELD_EVALUATE_MODE, evaluateMode.name());
        return object;
    }

    private static JsonElement writeSource(NumberSource source) {
        return switch (source) {
            case Constant constant -> new JsonPrimitive(constant.value());
            case Formula formula -> {
                JsonObject object = new JsonObject();
                object.addProperty(FIELD_EXPRESSION, formula.expression());
                object.addProperty(FIELD_FALLBACK, formula.fallback());
                yield object;
            }
        };
    }

    /// 槽位在 JSON 里的字段名（`VALUE` → `value`、`IN_SLOPE` → `inSlope`…）。
    /// 名字挂在枚举上，写与读共用一处
    public static String jsonName(KeyField field) {
        return switch (field) {
            case VALUE -> "value";
            case IN_SLOPE -> "inSlope";
            case OUT_SLOPE -> "outSlope";
            case IN_LENGTH -> "inLength";
            case OUT_LENGTH -> "outLength";
        };
    }

    /// 读一个关键帧：时间、插值模式缺字段就用默认值，五个槽位各读一份来源
    public static Keyframe read(JsonObject object) {
        Keyframe key = create(numberValue(object, FIELD_TIME, 0), 0)
                .evaluateMode(evaluateModeValue(object));

        for (KeyField field : KeyField.values()) {
            key.source(field, NumberSource.read(object.get(jsonName(field)), 0));
        }

        return key;
    }

    private static float numberValue(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);

        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }

        return element.getAsFloat();
    }

    private static EvaluateMode evaluateModeValue(JsonObject object) {
        JsonElement element = object.get(FIELD_EVALUATE_MODE);

        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String name = element.getAsString();

            for (EvaluateMode mode : EvaluateMode.values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
        }

        return EvaluateMode.LINEAR;
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
