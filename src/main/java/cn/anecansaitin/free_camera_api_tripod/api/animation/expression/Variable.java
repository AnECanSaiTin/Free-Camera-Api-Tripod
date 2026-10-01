package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 动画里的变量：名字 + 一个取值来源 + 一个默认值。
///
/// 取值来源可以是固定值、公式，或某条曲线轨道的读数（见 [ValueSource]）；
/// 公式里按名字引用别的变量，变量之间因此构成一张有向图。**判环不在这里**：
/// 环可以跨过轨道边界（变量绑轨道、那条轨道上的公式又引用该变量），
/// 所以权威判定在 `eval.EvaluationGraph` 上——写入之前就拦住，不必等求值
///
/// 来源**可以为 null**，表示这个变量没绑任何来源，这一帧就取 [defaultValue]。
///
/// 名字不限定字符集，中文也可以，但它要能被表达式识别为标识符（字母、下划线或非 ASCII 字符开头）。
@NullMarked
public class Variable {
    /// 没绑来源时的默认取值
    public static final float DEFAULT_VALUE = 0f;

    private static final String FIELD_NAME = "name";
    private static final String FIELD_SOURCE = "source";

    private String name;
    private @Nullable ValueSource source;
    private float defaultValue = DEFAULT_VALUE;

    public Variable(String name) {
        this(name, null);
    }

    public Variable(String name, @Nullable ValueSource source) {
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

    /// 取值来源；没绑来源时为 null
    public @Nullable ValueSource source() {
        return source;
    }

    public Variable source(@Nullable ValueSource source) {
        this.source = source;
        return this;
    }

    /// 没绑来源时这一帧的取值
    public float defaultValue() {
        return defaultValue;
    }

    public Variable defaultValue(float defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /// 绑定的曲线轨道 id；取值来源不是轨道读数时返回 null
    public @Nullable String trackId() {
        return source == null ? null : source.trackId();
    }

    /// 这一帧的取值：没绑来源就是默认值，否则按来源求值并回退
    public float evaluate(@Nullable Scope scope) {
        return source == null ? defaultValue : source.evaluateOrFallback(scope);
    }

    /// 来源是不可变 record，所以拷贝只是换一层包装
    public Variable copy() {
        return new Variable(name, source).defaultValue(defaultValue);
    }

    /// 写成 JSON：`{"name": …, "source": {…} | 数字}`。
    /// 没绑来源时写成默认值那个数字，磁盘格式因此看不出区别
    public JsonObject write() {
        JsonObject object = new JsonObject();
        object.addProperty(FIELD_NAME, name);
        object.add(FIELD_SOURCE, source == null ? new JsonPrimitive(defaultValue) : ValueSource.write(source));
        return object;
    }

    /// 读一个变量：`source` 认数字（固定值）、`expression`（公式）与 `track`（轨道读数）
    public static Variable read(JsonObject object) {
        return new Variable(SourceJson.stringValue(object, FIELD_NAME, ""))
                .source(ValueSource.read(object.get(FIELD_SOURCE), DEFAULT_VALUE));
    }

    @Override
    public String toString() {
        return name + " = " + (source == null ? Float.toString(defaultValue) : source);
    }
}
