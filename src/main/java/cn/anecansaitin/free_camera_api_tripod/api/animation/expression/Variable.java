package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 动画里的变量：名字 + 一个取值来源 + 一个默认值。
///
/// 取值来源可以是固定值、公式，或某条曲线轨道的读数（见 [ValueSource]）；
/// 公式里按名字引用别的变量，变量之间因此构成一张有向图。**判环不在这里**：
/// 环可以跨过轨道边界（变量绑轨道、那条轨道上的公式又引用该变量），
/// 所以权威判定在 `eval.EvaluationGraph` 上——写入之前就拦住，不必等求值
///
/// 来源**恒非空**：新建的变量直接是 `Constant(0)`，"没有来源"这个状态不存在，
/// 求值与序列化因此都不必再判一次 null。是不是轨道读数由 `instanceof TrackRef` 回答。
///
/// 名字不限定字符集，中文也可以，但它要能被表达式识别为标识符（字母、下划线或非 ASCII 字符开头）。
@NullMarked
public class Variable {
    /// 没设过取值时的固定值（新建变量的来源）
    public static final float DEFAULT_VALUE = 0f;

    private static final String FIELD_NAME = "name";
    private static final String FIELD_SOURCE = "source";

    private String name;
    private ValueSource source;
    private float defaultValue = DEFAULT_VALUE;

    public Variable(String name) {
        this(name, new Constant(DEFAULT_VALUE));
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

    /// 取值来源；恒非空
    public ValueSource source() {
        return source;
    }

    public Variable source(ValueSource source) {
        this.source = source;
        return this;
    }

    /// 名字上的默认取值。来源恒非空之后，它只剩"给界面一个初始数"的用途
    public float defaultValue() {
        return defaultValue;
    }

    public Variable defaultValue(float defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /// 绑定的曲线轨道 id；来源不是轨道读数时返回 null。
    ///
    /// "是不是轨道读数"由 `instanceof TrackRef` 回答——[ValueSource] 是 sealed，模式完备
    public @Nullable String trackId() {
        return source instanceof TrackRef track ? track.trackId() : null;
    }

    /// 这一帧的取值：按来源求值并回退固定数值
    public float evaluate(@Nullable Scope scope) {
        return source.evaluateOrFallback(scope);
    }

    /// 来源是不可变 record，所以拷贝只是换一层包装
    public Variable copy() {
        return new Variable(name, source).defaultValue(defaultValue);
    }

    /// 写成 JSON：`{"name": …, "source": {…} | 数字}`
    public JsonObject write() {
        JsonObject object = new JsonObject();
        object.addProperty(FIELD_NAME, name);
        object.add(FIELD_SOURCE, ValueSource.write(source));
        return object;
    }

    /// 读一个变量：`source` 认数字（固定值）、`expression`（公式）与 `track`（轨道读数）
    public static Variable read(JsonObject object) {
        return new Variable(SourceJson.stringValue(object, FIELD_NAME, ""))
                .source(ValueSource.read(object.get(FIELD_SOURCE), DEFAULT_VALUE));
    }

    @Override
    public String toString() {
        return name + " = " + source;
    }
}
