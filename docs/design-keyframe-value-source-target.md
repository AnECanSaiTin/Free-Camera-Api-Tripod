# 关键帧与数值来源：重构设计

配套：`docs/design-review-keyframe-value-source.md`（问题定位与横向对比）。本文只讲**目标形态**，
按"可破坏性更新"写，不考虑旧签名兼容；唯一要保住的是**磁盘上的 JSON 格式**（第 8 节）。

---

## 1. 三条不变量（整份设计从这里长出来）

| # | 不变量 | 由什么保证 |
| --- | --- | --- |
| I1 | **数值来源只出现在可写类上。** 只读视图 `Keyframec` 只报数，拿不到 `NumberSource` | `Keyframec` 不声明任何 `source` 相关成员 |
| I2 | **一个字段就是「槽位 + 来源」这一对。** 五个数值不再各有五个方法，全部按 `KeyField` 寻址 | `KeyField` 枚举是字段的唯一真理来源 |
| I3 | **回退只有一个点。** "算不出来就用固定值"只写在 `NumberSource.evaluateOrFallback` 里 | 求值、变量、界面三处都调它，不再各写一遍 |

再加一条类型级的安全边界，它是 I1 的具体化：

> **关键帧字段的类型是 `NumberSource`（两态）；变量的类型是 `ValueSource`（三态）。**
> "轨道读数"在类型上根本进不了关键帧字段，不再靠注释约束。

---

## 2. 包层级与依赖方向

```
api/animation/
├── Keyframe.java  Keyframec.java  TrackKey.java      关键帧（可写 / 只读 / 最小契约）
├── KeyField.java                                     五个可动态槽位的枚举（新）
├── CameraAnimation(.c)  Evaluator  EvaluateMode       顶层模型与插值模式
│
├── expression/               ⇢ 无依赖（纯 JDK 类型，连 MC 都不碰）
│   ├── Expression.java          公式解析与求值（Formula / Resolver / 内置函数清单）
│   ├── Scope.java               求值环境：这一帧的时间 + 名字解析 + 轨道读数
│   ├── TrackLookup.java         按 id 取轨道读数（函数式接口）
│   ├── NumberSource.java        两态来源：Constant | Formula
│   ├── ValueSource.java         三态来源：NumberSource | TrackRef（变量专用）
│   ├── Constant.java  Formula.java  TrackRef.java
│   ├── Variable.java  VariableGraph.java
│   └── SymbolTable.java  SymbolTablec.java  CustomFunction.java
│
├── curve/                    ⇢ animation
│   ├── Curvec.java  Curve.java       纯数值插值，不认识公式
│   ├── KeyValues.java                "第 index 个键的某个数是几"
│   └── WrapMode.java
│
├── eval/                     ⇢ animation, expression, curve
│   ├── CurveSampler.java         **求值唯一入口**（对外 API 不变）
│   ├── CurveSample.java          按 Scope 把键解析成 KeyValues（包内实现，缓存）
│   └── ExpressionScope.java      从动画构造的 Scope 实现
│
├── path/                     ⇢ 无依赖（纯几何，不参与求值）
└── track/                    ⇢ animation, curve, eval
    ├── AnimationTrack / CurveTrack / JsonTrack / TickTrack / RenamableTrack
    └── TrackType / TrackTypeRegistry / AnimationChannelRegistry
```

关键改动（相对现状）：

1. **新包 `expression` 收编全部"数值来源"概念**：`Scope`、`ValueSource`、`Variable`、`TrackRef`。
   现在的 `eval.Scope` 是"表达式求值环境"，它本来就属于 expression。
2. **`eval` 不再反向依赖 `animation` 根包**：`ExpressionScope.of(animation, ...)` 挪到
   `CurveSampler.scope(animation, time, worldTime)`。这样 `curve → eval` 这条回边没有环。
3. **`curve` 包不再有 `StaticKeys`**：静态求值只是"没有 Scope 的求值"，是同一段代码的一个分支（第 6.3 节）。
4. `Keyframe` / `Keyframec` 留在 `api/animation` 根（与 `CameraAnimation` 同级），因为它们是顶层数据模型；
   `KeyField` 与它们同包。

依赖方向一览（→ 表示"依赖"）：

```
expression        （无依赖：纯 JDK 类型）
    ↑
animation         Keyframe / Keyframec / KeyField；⇢ expression
    ↑        ↑
 curve    variable        curve ⇢ animation；variable ⇢ expression
    ↑        ↑
        eval              eval ⇢ animation, curve, variable, expression
         ↑
       track              track ⇢ animation, curve, eval
```

一句话：**`expression` 在最底下；`curve` 只认 `animation` 的键、不认识来源；
"来源在哪"这个知识只出现在 `curvesample` 一处；`eval` 仍是唯一入口。**

（一条既有的耦合要说明白：`eval.CurveSampler` 认 `CameraAnimationc`，而 `track.CurveTrack`
调 `CurveSampler` 插键——`track ↔ eval` 是互相依赖，现在就如此。只要 `eval` 不认识 `track`，
这条就不成环；本次重构把 `ExpressionScope.of(animation, ...)` 换成
`CurveSampler.scope(animation, ...)`，正是为了避免 `eval → track → eval` 那种真环。）

---

## 3. `expression` 层：全部公开接口

```java
package cn.anecansaitin.free_camera_api_tripod.api.animation.expression;

/// 求值环境：这一帧是什么时候 + 名字怎么解析 + 轨道读到多少。
/// 把原来的 Scope 与 Expression.Resolver 合成一个——它们本来就只有一个实现（ExpressionScope），
/// 而"只画一条曲线的人不需要解析能力"这个顾虑，用 [TrackLookup] 这个更小的接口解决。
@NullMarked
public interface Scope {
    float time();
    float progress();
    float worldTime();
    long version();                 // 每换一帧 +1，缓存按它失效
    Resolver resolver();            // 名字与自定义函数
}

/// 名字解析：变量名、自定义函数名，外加轨道读数（变量绑的轨道走 track）
@NullMarked
public interface Resolver extends TrackLookup {
    /// 名字 → 一个数；内置量 t / p / wt 由实现先认掉，未知名字返回 NaN
    float resolve(String name);

    @Nullable CustomFunction function(String name);
}

/// 按 id 取轨道读数；不存在返回 NaN。只读一条曲线的场景只需要这个。
@FunctionalInterface
public interface TrackLookup {
    float track(String id);
}
```

> 两个原本是 `Expression` 嵌套接口的类型外移成顶层：`Resolver`（原 `Expression.Resolver`）与新增的
> `TrackLookup`。理由有二：`Scope.resolver()` 的返回类型写在 `expression` 包里比
> `Expression.Resolver` 少一层拐弯；且"只要轨道读数"的读取方（例如只想画一条曲线的人）
> 因此不必认识整个解析器。`Expression.evaluate(Formula, Resolver)` 的位置不变。

### 3.1 数值来源：两态 + 三态

```java
/// 关键帧字段的取值来源：固定值，或一段公式。**没有第三态。**
@NullMarked
public sealed interface NumberSource permits Constant, Formula {
    /// 求值；算不出来返回 NaN。scope 为 null = 静态求值（公式一律算不出来）
    float evaluate(@Nullable Scope scope);

    /// 用不到公式时那个数（公式的回退值）
    float constant();

    /// **求值链上唯一的回退点**：evaluate 失败就用 constant，constant 也是 NaN 时用 0
    default float evaluateOrFallback(@Nullable Scope scope) {
        float value = evaluate(scope);
        if (!Float.isNaN(value)) return value;
        float fallback = constant();
        return Float.isNaN(fallback) ? 0f : fallback;
    }

    /// 是不是一段公式。界面问"这个槽位是数值还是公式"只需要这一个判断，
    /// 不必到处 instanceof —— 求值层因此完全不需要认识具体的实现类型。
    default boolean isFormula() { return false; }
}

public record Constant(float value) implements NumberSource {
    @Override public float evaluate(@Nullable Scope scope) { return value; }
    @Override public float constant() { return value; }
}

public record Formula(String expression, float fallback) implements NumberSource {
    // 编译结果不进 record 的 equals/hashCode，用静态缓存按文本取（见 §7）
    @Override public float evaluate(@Nullable Scope scope) { ... }   // 失败 / scope 为 null → NaN
    @Override public float constant() { return fallback; }
    @Override public boolean isFormula() { return true; }
    public boolean broken() { return Expression.compile(expression) == null; }
}
```

```java
/// 变量的取值来源：可以是固定值、公式，或某条曲线轨道的读数。
@NullMarked
public sealed interface ValueSource permits NumberSource, TrackRef {
    /// 与 NumberSource 同名同义；NumberSource 的实现直接继承过来
    float evaluateOrFallback(@Nullable Scope scope);

    /// 轨道引用返回其 id，数值来源返回 null（原 Variable#trackId 的职责）
    default @Nullable String trackId() { return null; }

    ValueSource copy();     // 三个实现都是不可变 record，copy 可以返回 this
}

/// 轨道读数：只作变量的取值来源，**不实现 NumberSource**，因此进不了关键帧字段
public record TrackRef(String trackId) implements ValueSource {
    @Override @Nullable public String trackId() { return trackId; }
    @Override public float evaluateOrFallback(@Nullable Scope scope) {
        float value = scope == null ? Float.NaN : scope.resolver().track(trackId);
        return Float.isNaN(value) ? 0f : value;
    }
    @Override public ValueSource copy() { return this; }
}
```

### 3.2 变量与符号表

```java
public final class Variable {
    private String name;
    private @Nullable ValueSource source;   // null = 没有来源，取默认值
    private float defaultValue;             // 默认 0

    public String name();
    public Variable name(String name);
    public @Nullable ValueSource source();
    public Variable source(@Nullable ValueSource source);
    public float defaultValue();
    public Variable defaultValue(float value);

    /// 这一帧的取值：没绑来源就是默认值
    public float evaluate(@Nullable Scope scope) {
        return source == null ? defaultValue : source.evaluateOrFallback(scope);
    }
    public Variable copy();
    public JsonObject write();                  // {"name":…, "source": {…} | 数字}
    public static Variable read(JsonObject object);
}
```

`SymbolTable` 的其余成员（`variables` / `variable` / `addVariable` / `removeVariable` / `renameVariable` /
`functions` / `function` / `add*` / `remove*` / `rename*` / `rebindTrack` / `unbindTrack` / `replaceFrom`）不变，
只有两处改：

```java
// rebindTrack / unbindTrack 不再需要 instanceof TrackValue
if (variable.source() instanceof TrackRef ref && ref.trackId().equals(oldId)) {
    variable.source(new TrackRef(newId));
}

/// 关键帧上任一挂了公式的槽位是否引用了该名字（按 KeyField 泛化，见 §4）
public static boolean references(Keyframec key, String name);

/// 任意来源是否以公式形式引用了该名字
public static boolean references(@Nullable ValueSource source, String name);
```

---

## 4. `KeyField`：字段的唯一真理来源

```java
package cn.anecansaitin.free_camera_api_tripod.api.animation;

/// 关键帧上可以挂公式的五个数值槽位。
///
/// 枚举顺序 = 序列化顺序 = 面板行顺序；界面标签、小数位、所属插值模式都挂在这里，
/// 于是"再加一个可动态字段"真的只需要在这里加一行——这是原来那套枚举想做到、
/// 却因为字段分散在五组方法里而没做到的事。
@NullMarked
public enum KeyField {
    VALUE      ("inspector.key.value",     3, EnumSet.of(LINEAR, STEP, HERMITE)),
    IN_SLOPE   ("inspector.key.in_slope",  3, EnumSet.of(HERMITE)),
    OUT_SLOPE  ("inspector.key.out_slope", 3, EnumSet.of(HERMITE)),
    IN_LENGTH  ("inspector.key.in_length", 3, EnumSet.of(HERMITE)),
    OUT_LENGTH ("inspector.key.out_length",3, EnumSet.of(HERMITE));

    KeyField(String labelKey, int decimals, Set<EvaluateMode> modes) { ... }

    public String labelKey();                       // 语言键
    public int decimals();                          // 界面显示小数位
    public boolean activeIn(EvaluateMode mode);     // 该模式下这个槽位是否参与求值 / 是否该显示
    public static Set<KeyField> activeIn(EvaluateMode mode);   // 面板据此决定显示哪几行
}
```

> `VALUE` 是唯一的取值槽位，三种插值模式下都参与求值；四个曲柄槽位只在左键为 `HERMITE`
> 的那一段里被读到（`LINEAR` / `STEP` 段根本不读它们）——这正是现在
> `KeyframePanel:137` 那个 `if (key.evaluateMode() == HERMITE)` 的判据。
> 收进枚举后，"哪些槽位该显示、哪些槽位参与求值"这件事只有一处定义。

---

## 5. `Keyframec` / `Keyframe`：只读契约与可写实现

### 5.1 `Keyframec`：只报数

```java
package cn.anecansaitin.free_camera_api_tripod.api.animation;

/// 只读关键帧。**这里没有也不会有 NumberSource**——只读视图不发放可变表示（见设计评审 §2.2）。
///
/// 有一个槽位挂了公式时 value() 等给的是公式的**回退值**：
/// 本接口是给几何、绘制、界面显示用的；要这一帧真算出来的数，走 eval.CurveSampler。
@NullMarked
public sealed interface Keyframec extends TrackKey permits Keyframe {
    float value();
    float inSlope();
    float outSlope();
    float inLength();
    float outLength();
    EvaluateMode evaluateMode();

    /// 五个槽位里有没有挂公式的（曲线图标蓝点、状态提示用它）。
    /// 只读契约上保留这一个方法：它是**展示层需要的信息**，而表达它所依赖的
    /// "是不是公式"判断已经收进了 NumberSource.isFormula()，不必泄漏来源对象本身
    boolean hasFormula();

    /// 某个槽位的固定数值（挂了公式时是回退值）
    default float constant(KeyField field) {
        return switch (field) {
            case VALUE -> value();
            case IN_SLOPE -> inSlope();
            case OUT_SLOPE -> outSlope();
            case IN_LENGTH -> inLength();
            case OUT_LENGTH -> outLength();
        };
    }
}
```

`sealed permits Keyframe`：五个取数的 `switch` 因此不必写 `default`，
而且 `eval` 里"这个键是不是具体实现"的判断天然是穷尽的。

### 5.2 `Keyframe`：可写实现

```java
/// 可写关键帧。字段是「槽位 → 数值来源」，全部按 [KeyField] 寻址。
@NullMarked
public final class Keyframe implements Keyframec, JsonSerializable {   // JsonSerializable 见 §8
    public static final Comparator<Keyframe> TIME_COMPARATOR = ...;
    public static final float DEFAULT_LENGTH = 1f;
    /// 槽位缺省来源：没有来源等于固定值 0
    private static final NumberSource ZERO = new Constant(0f);

    private float time;
    private EvaluateMode evaluateMode;
    private final EnumMap<KeyField, NumberSource> sources = new EnumMap<>(KeyField.class);

    public Keyframe(float time, float value);
    public Keyframe(float time, float value, float inSlope, float outSlope);
    public Keyframe(float time, float value, float inSlope, float inLength,
                    float outSlope, float outLength, EvaluateMode evaluateMode);

    /// 拷贝构造：连公式一起拷
    public Keyframe(Keyframec other);

    // region 读数（Keyframec）

    @Override public float value()      { return constant(KeyField.VALUE); }
    @Override public float inSlope()    { return constant(KeyField.IN_SLOPE); }
    // ... 其余三个同理：一律转发到 constant(field) → source(field).constant()
    @Override public float constant(KeyField field) { return source(field).constant(); }
    @Override public boolean hasFormula() { ... }   // 五个槽位任一 instanceof Formula

    // endregion

    // region 写数（**改的是槽位里的来源本身**）

    public Keyframe time(float time);
    public Keyframe evaluateMode(EvaluateMode mode);

    /// 把一个槽位写成固定数值（**会清掉该槽位原有的公式**，理由见下）
    public Keyframe constant(KeyField field, float value) {
        sources.put(field, new Constant(value));
        return this;
    }

    // endregion

    // region 来源（**只在可写类上**）

    /// 该槽位的数值来源；每个槽位恒有来源（缺省是 Constant(0)）
    public NumberSource source(KeyField field) { return sources.getOrDefault(field, ZERO); }

    public Keyframe source(KeyField field, NumberSource source) {
        sources.put(field, source);
        return this;
    }

    /// 挂公式；表达式为空表示不挂公式、退回固定值
    public Keyframe formula(KeyField field, @Nullable String expression) {
        float fallback = constant(field);
        return source(field, expression == null || expression.isBlank()
                ? new Constant(fallback)
                : new Formula(expression, fallback));
    }

    /// 覆盖式拷贝：来源一并拷过来
    public Keyframe set(Keyframec other) {
        this.time = other.time();
        this.evaluateMode = other.evaluateMode();
        for (KeyField field : KeyField.values()) {
            this.sources.put(field, other instanceof Keyframe writable
                    ? writable.source(field).copy()
                    : new Constant(other.constant(field)));
        }
        return this;
    }

    /// 五个槽位来源的不可变快照（复制粘贴、调试用）。来源本身不可变，所以快照即拷贝。
    public Map<KeyField, NumberSource> sources() { return Map.copyOf(sources); }

    // endregion

    // region 序列化（见 §8）

    @Override public JsonObject write();
    public static Keyframe read(JsonObject object);
}
```

一处刻意的取舍：`constant(field, value)` **总是换成新的 `Constant`**，也就是"写数值会清掉公式"。
原来的 `withConstant` 语义是"改回退值、公式保留"，但那要求值源自己是可变的、只能就地改——
正是它让 `TrackValue.withConstant` 必须换对象、让接口道歉（评审 §2.1）。现在：

- **要保留公式改回退值** → `source(field, new Formula(f.expression(), v))`，或 `formula(field, f.expression())` 后走固定值入口；
- 界面上的语义由 `ExpressionFieldWidget` 自己挑：数值输入框提交时调 `constant(field, v)`（数值模式本来就没有公式），
  公式编辑窗口提交时调 `formula(field, text)`。

也就是说"公式会不会被弄丢"这件事从**数据层的隐式行为**变成**调用点显式选择**。这是本次重构里
唯一的语义变化，值得在类文档里写清楚。

---

## 6. `curve` 与 `eval`：插值与求值

### 6.1 `curve`：不认识公式

```java
@NullMarked
public interface KeyValues {
    float value(int index);
    float inSlope(int index);
    float inLength(int index);
    float outSlope(int index);
    float outLength(int index);
}

@NullMarked
public interface Curvec {
    float evaluate(float time, KeyValues values);   // 纯数值插值
    int size();
    Keyframec key(int index);                       // 只读视图（越界抛 IndexOutOfBoundsException）
}

public class Curve implements Curvec {
    public Keyframe key(int index);                 // 协变收窄：写方拿具体类
    // key/moveKey/removeKey/smoothTangents/preKey/preMode/postMode/... 不变
}
```

### 6.2 `eval`：求值的两个类

```java
/// 求值的唯一入口。API 与现在一致，实现换了底座。
@NullMarked
public final class CurveSampler {
    private final CurveSample sample = new CurveSample();

    /// 从动画建一份求值环境（原来是 ExpressionScope.of）。
    /// 放在这里而不是 ExpressionScope 上，是为了让 eval 不依赖 track 包
    public static Scope scope(CameraAnimationc animation, float time, float worldTime);

    public float sample(Curvec curve, float time, Scope scope);          // 复用缓存
    public static float sampleOnce(Curvec curve, float time, Scope scope);// 一次性
    public static float sampleStatic(Curvec curve, float time);          // 静态：只有固定数值
    public <T> T sample(CameraAnimationc animation, float time, Evaluator<T> evaluator, Scope scope);
    public void clear();
}
```

```java
/// 包内实现：把「键 + Scope」解析成 KeyValues。只读视图与具体实现在这里分流，
/// **这是全仓唯一需要知道"来源只存在于可写类上"的地方。**
@NullMarked
final class CurveSample implements KeyValues {
    private Curvec curve;
    private @Nullable Scope scope;      // null = 静态求值
    private long version;
    private float[] cache = new float[0];
    private int size;

    /// 换曲线 / 作用域 / 帧；任一变化就清缓存。scope 传 null 就是静态读取
    CurveSample reset(Curvec curve, @Nullable Scope scope) { ... }   // 失效判据：曲线、键数、作用域对象、版本号（与现在一致）

    /// 一次性构造（静态求值路径用）
    static CurveSample at(Curvec curve, @Nullable Scope scope) { ... }

    @Override public float value(int index)      { return resolve(index, KeyField.VALUE); }
    @Override public float inSlope(int index)    { return resolve(index, KeyField.IN_SLOPE); }
    // ... 其余三个同样一行

    private float resolve(int index, KeyField field) {
        if (index < 0 || index >= size) return 0f;              // 键被删掉时索引会失效
        int slot = index * FIELDS + field.ordinal();
        float cached = cache[slot];
        if (!Float.isNaN(cached)) return cached;

        Keyframec key = curve.key(index);
        NumberSource source = key instanceof Keyframe writable
                ? writable.source(field)
                : new Constant(key.constant(field));            // 只读视图只有固定数值
        float value = source.evaluateOrFallback(scope);
        cache[slot] = value;
        return value;
    }
}
```

### 6.3 静态求值不再是另一个类

`sampleStatic` 走的就是"作用域为 `null`"这条路径：`Formula.evaluate(null)` 返回 `NaN`，
`evaluateOrFallback` 接住并给出回退值——**和挂着公式但作用域缺失时是同一条路径**。
于是 `curve.StaticKeys` 这个类可以删掉，"有公式 / 没公式 / 有环境 / 没环境"四种组合收敛到一处。

`ExpressionScope` 读轨道时**自己持一份静态 `CurveSample`**，不走 `CurveSampler`：

```java
@Override public float track(String id) {
    Curve curve = curves.curve(id);
    return curve == null ? Float.NaN
            : curve.evaluate(time, staticSamples.computeIfAbsent(curve, c -> CurveSample.at(c, null)));
}
```

这里有两点是刻意的：

- **静态路径不经过采样器**：`CurveSampler` 的缓存意义是"同一帧内同一条曲线只解析一次"，
  而 `track()` 是求值环境内部的查询，自己按 `Curve` 缓存一份已经够；
  硬要共享就会让 `CurveSampler` 与 `ExpressionScope` 互相持有，得不偿失。
- **求值路径单向**：值源 → `Scope` → 轨道读数 → 静态求值，链路里没有任何回边。

### 6.4 自嵌套：这条规则可以顺手修（可选）

现在"变量读轨道 → 轨道上的公式引用该变量"靠的是"轨道一律静态求值"这条全局规则，
代价是**该键作为属性播放与作为变量被引用会得到不同的值**（`ExpressionScope` 类文档自己承认的语义歧义）。

要修的话，思路是：`Scope.resolver()` 增加一个"当前正在求值哪个变量"的查询，
`CurveSample.resolve` 在准备取某个槽位的公式前问一次——**若这条公式引用了正在求值的那个变量，
就只让这一个槽位退回固定值**，其余槽位、其余键照常按公式求值。
于是规则从"整条轨道降级"收窄成"一个槽位降级"，上面那条歧义消失。

代价是要多一个查询方法、以及"公式引用分析"进入求值热路径（可以用 `Expression.references` 的结果缓存来抵消）。
**建议先不做**：它不解决任何当前报错，只在"变量绑的轨道上又挂了引用该变量的公式"这种写法下才有差别。
设计上留好接口（`Scope` 上加一个默认返回 `null` 的 `resolving()`），下次要动时不必再改签名。

---

## 7. 消费者侧：改完长什么样

### 7.1 面板：五行变成一次循环

```java
// KeyframePanel.rebuild —— 现在是五组手写的 FieldSpec（:131-147）
for (KeyField field : KeyField.activeIn(key.evaluateMode())) {
    widgets.add(new ExpressionFieldWidget(context, rect(field), EditorLang.t(field.labelKey()),
                    new KeyframeAccessor(key, field))            // 见下
            .decimals(field.decimals()));
}

/// 值源读写入口的两个方法体各一行，且**不认识具体是哪个槽位**
private record KeyframeAccessor(Keyframe key, KeyField field) implements ExpressionFieldWidget.Accessor {
    @Override public NumberSource source() { return key.source(field); }
    @Override public void source(NumberSource source) { key.source(field, source); }
}
```

`ExpressionFieldWidget.Accessor` 的签名从 `ValueSource` 收成 `NumberSource`（编译期就挡住轨道读数），
`evaluate` / `broken` / "是不是公式"全在 `NumberSource` 上，写数走 `constant(field, v)`。
这个签名收窄**只影响关键帧面板**：全仓只有 `KeyframePanel` 用它（`VariablePanel` 与 `ExpressionEditorWindow`
自己处理变量来源的三态），所以面板里那对 `Supplier` / `Consumer` 也可以一并删掉。

### 7.2 公式缓存：从字段上挪回解析层

`Formula` 是 record（不可变、可 `equals`），编译结果不能放进去。做法：
`Expression` 内部按公式文本驻留编译结果（它本来就有 512 条上限的缓存），`Formula.evaluate` 每次查一次
（`ConcurrentHashMap` 命中，量级可忽略）。好处是**序列化与拷贝不再带着编译产物走**，
"同一个公式文本只解析一次"这条语义也从"每个字段各存一份"变成"全局一份"。

### 7.3 复制粘贴

```java
private record KeyClip(String trackId, float time, Map<KeyField, NumberSource> sources, EvaluateMode mode) {}

// 复制
KeyClip clip = new KeyClip(track.id(), key.time(), key.sources(), key.evaluateMode());
// 粘贴
Keyframe key = Keyframe.create(time, 0).evaluateMode(clip.mode());
clip.sources().forEach(key::source);
```

`Keyframe.sources()` 返回一份不可变快照（`Map.copyOf`）——**拷贝即快照，不需要 `copy()` 方法**，
因为 `NumberSource` 已经是不可变的。

---

## 8. 序列化：`JsonSerializable` 与磁盘格式

序列化从 `AnimationCodec` 的一大段 `valueToJson` / `readValue` 挪到**数据类自己身上**，
`AnimationCodec` 只做容器（顶层 JSON、轨道表、路径表）：

```java
package cn.anecansaitin.free_camera_api_tripod.api.animation;

/// 能把自己写成 JSON 对象的数据类型
@NullMarked
public interface JsonSerializable {
    JsonObject write();
}
```

```java
// Keyframe.write()
@Override public JsonObject write() {
    JsonObject object = new JsonObject();
    object.addProperty(FIELD_TIME, time);
    object.addProperty(FIELD_EVALUATE_MODE, evaluateMode.name());
    for (KeyField field : KeyField.values()) {          // ← 五个槽位一次写完
        object.add(field.jsonName(), writeSource(sources.get(field)));
    }
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
```

`KeyField.jsonName()`：`VALUE→"value"`、`IN_SLOPE→"inSlope"`…… 名字挂在枚举上，写与读共用一处。

**磁盘格式一字不改**：

| 情况 | JSON |
| --- | --- |
| 固定值 | `"value": 1.5` |
| 公式 | `"value": {"expression": "t * 2", "fallback": 1.5}` |
| 变量绑轨道 | `"source": {"track": "fov"}` |
| 变量的固定值 / 公式 | 同上两种 |

顺手清掉两个历史包袱：`inTangent` / `outTangent` / `inWeight` / `outWeight` / `weightedMode` 的旧字段读取，
以及"关键帧字段认 `{"track": ...}`"这条通路（读到时按 `fallback` 降级成固定值并记一条日志）。

`Variable.write()` / `Variable.read()` 同理，`read` 里才认 `{ "track": ... }`——
**轨道读数从此只有变量读得到，类型与格式两处都对齐。**

---

## 9. 与现状的逐项对照

| 现状 | 目标 | 为什么 |
| --- | --- | --- |
| `Keyframec.valueSource()` 等五个读方法 | 删除；只留 `value()` 与 `constant(KeyField)` | 只读视图不发放可变表示（I1） |
| `Keyframe.valueSource(field)` 等五个写方法 | 合成 `source(KeyField)` / `source(KeyField, NumberSource)` | 字段按槽位寻址（I2） |
| `Variable.valueSource()` 等三个 getter | `Variable.source()` / `source(ValueSource)` | 变量只有一个来源，不需要按槽位 |
| `ValueSource`（三态）+ `withConstant` | `NumberSource`（两态，带 `evaluateOrFallback`）+ `ValueSource`（三态） | 回退归位（I3），轨道读数在类型上进不了关键帧 |
| `ConstantValue` / `FormulaValue` / `TrackValue`（可变类） | `Constant` / `Formula` / `TrackRef`（record） | 来源是不可变值；拷贝即快照 |
| `eval.Scope` + `Expression.Resolver` + `ExpressionScope` 三者 | `Scope` + `Resolver` + `TrackLookup`，实现仍是 `ExpressionScope` | 环境与解析本就一体，但读取方可以只要最小的那个 |
| `curve.StaticKeys` | 删除（`CurveSample` 的 `scope == null` 分支） | 静态求值 = 无环境的求值 |
| `eval.ResolvedKeys`（public，认只读接口） | `eval.CurveSample`（包内，一处 `instanceof Keyframe`） | 把"来源在哪"这个知识收敛到一处 |
| `ValueSource.evaluateOrFallback`（静态工具） | `NumberSource.evaluateOrFallback`（默认方法） | 回退跟着来源走，少一层间接 |
| `Keyframe.value(float)` 隐式保留公式 | `constant(field, v)` 显式覆盖；要留公式走 `formula(field, ...)` | 语义从数据层挪到调用点，调用处看得见 |
| `SymbolTable.references` 五个 getter 串联 | 按 `KeyField` 循环 | 加字段只改枚举 |
| `AnimationCodec` 里的 `valueToJson` / `readValue` | `JsonSerializable` + `Keyframe.write/read` | 数据自己的格式自己管，编解码只做容器 |
| `KeyframePanel` 五组手写 `FieldSpec` | `for (KeyField field : KeyField.activeIn(mode))` | 同上；HERMITE 知识收进枚举 |
| `ExpressionScope.of(animation, ...)` | `CurveSampler.scope(animation, ...)` | 避免 `eval → track → eval` 成环 |
| `TrackValue` 可被读档塞进关键帧字段 | 类型 + 格式双重堵死 | 已接通的非法通路（评审 §2.4） |

---

## 10. 改造顺序与验收

按"能独立编译、每步都能跑"排：

1. **`expression` 层重建**：`Scope` / `Resolver` / `TrackLookup` / `NumberSource`（+`Constant`/`Formula`）/
   `ValueSource`（+`TrackRef`）/ `Variable` / `SymbolTable` 按 §3 落地。
   `Expression` 只加"按文本驻留编译结果"的内部查询，解析器本身不动。
2. **`KeyField` + `Keyframe` / `Keyframec`**：按 §4、§5 重写；`Curve` 只改 `key(int)` 的返回类型。
3. **`eval` 层**：`CurveSample` 取代 `ResolvedKeys` / `StaticKeys`；`ExpressionScope` 实现 `Resolver`；
   `CurveSampler` 增 `scope(animation, ...)`。此时 `api` 应能整体编译通过。
4. **`core` 消费侧**：`AnimationCodec`（改用 `write()` / `read()`，删旧字段兼容）、
   `KeyframePanel` / `ExpressionFieldWidget` / `VariablePanel` / `ExpressionEditorWindow`（改 `instanceof` 与 Accessor 类型）、
   `GraphPanel`（`dynamic()` → `hasFormula()`）、`CameraEditorScreen.KeyClip`、`SymbolTable.references`。
5. **文档**：本文件 + 评审文件合并后的结论写进 `docs/animation-editor-guide.md` 的 2.1 / 2.7 / 2.8 三节。

验收清单（改动量大，这五条是"真的没改坏"的判据）：

- **读档**：手写的旧动画（含 `{"track": ...}` 的键、含 `inTangent` 旧字段）能读进来，非法槽位降级成固定值；
  新写出的 JSON 与旧格式**逐字节等价**（除被清掉的历史字段）。
- **播放**：挂了公式的键在播放与曲线图里给同一个数（`CurveSampler` 仍是唯一入口）。
- **变量**：绑轨道的变量、变量间成环、变量改名 / 轨道改名 / 轨道删除的重定向与解绑行为不变。
- **编辑**：数值输入框改数、公式窗口挂 / 摘公式、模式切换（数值 ↔ 公式）、复制粘贴含公式的键。
- **静态路径**：`sampleStatic` 与"没有 Scope 的 `sample`"给同一个结果（这条能证明 `StaticKeys` 删得掉）。

**风险最高的两点**：一是 `Expression` 的编译缓存改成全局驻留——`random()` 这类非纯函数在语法树里，
编译结果复用不改其行为，但要确认缓存上限（512）在公式数量增长后仍够用；
二是 `Keyframe.constant(field, v)` 会清掉公式（§5.2），这是本次唯一的行为变化，
界面两条提交路径（数值框 / 公式窗口）必须各自走对入口，否则用户会看到"改个数公式就没了"。
