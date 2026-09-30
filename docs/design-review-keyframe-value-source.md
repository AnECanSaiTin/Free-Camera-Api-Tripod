# 设计审阅：关键帧该不该暴露 `ValueSource`

范围：`api.animation.Keyframe` / `Keyframec`、`api.animation.expression.*`、`api.animation.eval.*`、
`core.animation.io.AnimationCodec`、编辑器中相关面板。
结论先写在第一节，后面是依据、横向对比、推荐形态与改造清单。

---

## 1. 结论

**建议：`Keyframec` 不再暴露值源。`value()` 报数，`Keyframe` 拿值源。**

具体三条：

1. 只读契约 `Keyframec` 去掉 `valueSource()` / `inSlopeSource()` / `inLengthSource()` /
   `outSlopeSource()` / `outLengthSource()` 五个读方法（连同 `Keyframe` 上对应的五个写方法），
   只保留"这个字段是几"。
2. 值源本身**保留**，但把 `TrackValue` 从 `ValueSource` 里摘出去——
   值源等于「固定值 或 公式」，"轨道读数"升级成变量专用的 `VariableSource`。
3. 需要值源的两处（求值、序列化）改为认具体类型 `Keyframe`，它们本来就在 `api` / `core` 内部，
   不涉及对外兼容。

一句话版本：**值源是"字段的实现方式"，不是"关键帧的性质"；把它从只读视图移到可写类上，
`Keyframec` 就只剩它承诺的那件事——报数。**

怀疑是对的，但"职责不清晰"的根因不是"暴露了值源"，而是**同一个字段有两套并列的读 API，
而只读视图给出的那套会漏出可变表示**。修法不是给值源加包装，而是把表示从只读契约里移出去。

---

## 2. 现状：三层职责压在同一个对象上

### 2.1 `ValueSource` 自己就是三件事

`api/animation/expression/ValueSource.java:20`

```java
public sealed interface ValueSource permits ConstantValue, FormulaValue, TrackValue {
    float evaluate(@Nullable Scope scope);   // ① 运行期：按这一帧算出一个数
    float constant();                        // ② 数据：不用公式时是几（公式的回退值）
    ValueSource withConstant(float value);   // ③ 编辑：改那个数 / 换掉整个来源
    ValueSource copy();                      // ④ 拷贝

    static float evaluateOrFallback(ValueSource source, @Nullable Scope scope) { ... }  // ⑤ 回退
}
```

这五件事分属三个不同的角色：

| 角色 | 谁需要 | 需要的成员 |
| --- | --- | --- |
| 数据（模型层） | 序列化、拷贝、几何计算 | `constant()` `copy()` |
| 求值（运行期） | `ResolvedKeys` / `ExpressionScope` | `evaluate(scope)` + 回退规则 |
| 编辑（界面层） | `ExpressionFieldWidget` | `withConstant()`、`instanceof FormulaValue` |

三种实现各自只擅长其中一角：

- `ConstantValue.withConstant` 就地改自己（`ConstantValue.java:37`）
- `FormulaValue.withConstant` 就地改回退值、**公式留着**（`FormulaValue.java:64`）
- `TrackValue.withConstant` 改不了，只能**整体换成另一个对象**（`TrackValue.java:43`）

同一个方法名，两种语义（就地改 vs 换对象），注释只能靠"返回的是应当持有的那个值源"这句提醒
（`ValueSource.java:28`）——这已经是接口在替实现道歉了。

### 2.2 `Keyframec` 是唯一的"泄漏点"

`api/animation/Keyframec.java:35-47` 在只读视图上开了五个口子。而 `ValueSource` 的三种实现**全是可变对象**
（`FormulaValue.expression(String)` / `constant(float)`，`ConstantValue.value(float)`，`TrackValue.trackId(String)`）。
于是：

```java
Keyframec view = curve.key(0);       // 说好的只读视图
((FormulaValue) view.valueSource()).expression("t * 999");   // 数据被改了，全程没碰过 Keyframe
```

`SymbolTablec.variables()`（`SymbolTablec.java:19`）把可变的 `Variable` 列表交出去，是同一类毛病，
只是没有 `valueSource()` 这么直接——后者是"每个字段一个改写入口"，粒度最细、最容易被误用。

### 2.3 "唯一的回退点"实际有三处

`ValueSource.java:34` 声称 `evaluateOrFallback` 是求值链上唯一的回退点。实际上：

| 位置 | 在做什么 |
| --- | --- |
| `ValueSource.evaluateOrFallback`（`ValueSource.java:35`） | 求值失败 → 退回固定数值 |
| `ValueSource.withConstant` 各实现 | 把"改成固定值"这件事塞进了值源自己（`TrackValue` 是直接换类型） |
| `ExpressionFieldWidget.constantOf`（`ExpressionFieldWidget.java:88`） | `NaN` → `0`，界面层的第三次兜底 |

再加上 `FormulaValue.evaluate` 在 `scope == null`、编译失败、求值异常三种情况下都返回 `NaN`
（`FormulaValue.java:48-54`），"算不出来"这一个信号被四处解释。规则分散，正是"职责不清晰"的体感来源。

### 2.4 类型上允许、语义上禁止的 `TrackValue`

`TrackValue` 的类文档（`TrackValue.java:9`）写着"不会出现在关键帧 / 路径节点的字段上"——
但它是 `ValueSource` 的合法成员，而关键帧字段的类型就是 `ValueSource`。**约束只写在注释里，类型系统没拦。**

而且已经有真实路径能造出这种键：`AnimationCodec` 的关键帧与变量**共用同一个读取器**
`readValue`（`AnimationCodec.java:239`），其中第 253-257 行认 `{"track": "..."}`；关键帧走的是
`key.valueSource(readValue(...))`（第 505 行）。也就是说，一份 `{"value": {"track": "fov"}}` 的手写文件（或手改过的存档），读进来就是一个"取值恒为 `NaN` → 恒回退固定值"的键，界面还会把它当公式键标蓝。
这不是假想风险，是已经接上的通路。

### 2.5 代价：手写成员 × 5 个字段

值源挂在字段上换来了"加字段不用改枚举"，但也换来每个字段一份手写三件套：

| 位置 | 手写成员数 |
| --- | --- |
| `Keyframe` | 5 读 + 5 写 + `copySources` 里 5 行 |
| `Keyframec` | 5 个方法声明 |
| `ResolvedKeys.resolve` | `switch (field)` 五分支（`ResolvedKeys.java:113-118`） |
| `AnimationCodec` | 写 5 行 + 读 5 行（`:374-378`、`:505-510`） |
| `SymbolTable.references` | 5 个 getter 串联（`:247-251`） |
| `KeyframePanel` | 5 处 `keySource(key::xSource, key::xSource)`（`:132-147`） |
| `CameraEditorScreen.KeyClip` | 5 个 `ValueSource` 字段 + 复制粘贴 10 行（`:675`、`:644-648`） |

字段数量是**编译期固定**的（`ResolvedKeys` 自己就写死了 `FIELDS = 5` 和四个字段常量），
所以这套"加字段不改代码"的收益并没有兑现——加第六个数值，上面七处全都要动。
这不构成推翻值源的理由（值源带来的收益是"公式跟着数值走"，这个是真收益），
但说明"用值源可以把字段做成数据驱动的"这个预期不成立，**不应该为了保住这个预期而把值源留在只读视图上**。

### 2.6 全部外部调用点：只有一处真的依赖只读视图

全仓 `*Source(` 的调用点（`grep` 结果）除 `Keyframe` 自己的定义外只有四处，而且**接收者类型全是具体类**：

| 调用点 | 接收者 | 需要什么 | 改造成本 |
| --- | --- | --- | --- |
| `ResolvedKeys.java:113-118` | `Keyframec key = curve.key(index)`（`Curve.key` 返回 `Keyframe`） | 求值 | 加一处 `instanceof`，或把 `Curve.key` 的返回类型收成 `Keyframe` |
| `AnimationCodec.java:374-378`、`:505-510` | `Keyframe key` | 序列化 | 零（本来就只有具体类有写方法） |
| `CameraEditorScreen.java:623-624`、`:644-648` | `Keyframe keyframe`（`:617` 已经 `instanceof Keyframe` 过） | 复制粘贴 | 零 |
| `SymbolTable.java:247-251` | `Keyframec key`（唯一真正吃只读接口的地方） | 依赖扫描（只问"引用了这个名字吗"） | 拆成两个重载：具体类走值源，只读视图只能返回 `false`（它没有公式） |

也就是说，**这五个 getter 挂在不变量最弱的那个类型上，实际却只被拿到具体类型的三处调用**——
把它们从 `Keyframec` 上摘掉，唯一要动脑的地方是 `SymbolTable.references`（10 行）。
另外全仓没有测试源集，也没有第二个 `Keyframec` 实现；`Curve` 内部只装 `Keyframe`。

---

## 3. 横向对比：同类软件怎么处理"值"与"值的来源"

| 软件 | 关键帧 / 属性的数据形态 | 表达式（动态来源）放在哪 | 只读侧看到什么 |
| --- | --- | --- | --- |
| **Unity** [`Keyframe`](https://docs.unity3d.com/ScriptReference/Keyframe.html) | struct：`time` `value` `inTangent` `outTangent` `inWeight` `outWeight` `weightedMode`——**全是数** | 根本没有（靠 `AnimationCurve` 之外的自定义逻辑 / Timeline 扩展） | 数 |
| **Unreal** [`FRichCurveKey`](https://dev.epicgames.com/documentation/unreal-engine/API/Runtime/Engine/FRichCurveKey) | struct：`Time` `Value` `InterpMode` `TangentMode` `ArriveTangent` `LeaveTangent`——**全是数** | 不在键上（曲线资产与 Blueprint 逻辑分离） | 数 |
| **Blender** F-Curve | `keyframe_points[i].co` = `[frame, value]`，外加 `interpolation` `handle_left/right`——`co[1]` 永远是**静态值** | 挂在 **F-Curve 上**的 `Driver` 对象：`driver.expression`、`driver.variables`；驱动算不出来时用的就是键上的静态值（Blender 4.x 还给驱动变量加了 fallback value，见 [drivers.rst 的 fallback 提交](https://projects.blender.org/mont29/blender-manual/commit/f8c294333c8e789742fe58e63bc02fa1c6f82a9d)） | 数（`co[1]`），要表达式得另外走 `fcurve.driver` |
| **After Effects** | 属性 = 关键帧序列（`Property.keyValue(i)`），**`Property.value` 同时是"关键帧的值"与"求值结果"** | 属性级的一个**可选**字符串 `Property.expression`，与关键帧并列，不是关键帧的一部分（[Property 文档](https://raw.githubusercontent.com/docsforadobe/after-effects-scripting-guide/master/docs/property/property.md)） | 数（`property.value`），表达式要单独问 `property.expression` |
| **Houdini** | 参数的"值"与"表达式"是**同一个参数对象**的两个面 | 同一个参数对象上（`ch()` 取值、`chef()` 取表达式） | 参数对象（值在对象里） |

读出来的规律有两条，都很稳：

1. **五家里没有一家把"来源对象"挂在关键帧的只读视图上。** 要么值就是数（Unity / Unreal），
   要么表达式挂在**上一层容器**（Blender 的 F-Curve / AE 的 Property），关键帧本身只有数。
2. **五家里没有一家的关键帧字段可能有三种形态。** 值源是"固定值 或 公式"两态；
   第三态（轨道读数）在每一家里都属于**变量 / 输入**，不属于关键帧。我们的 `TrackValue` 就是这个第三态。

顺带说一句：Houdini 的形态（值 + 表达式同属一个参数对象）其实就是我们现在的形态，
差别在于**那个对象是参数（= 我们的 `Keyframe` 字段），不是参数的只读投影**。
这正是第 4 节推荐的分界线。

---

## 4. 推荐形态

### 4.1 目标：契约按"谁需要"切开

```
Keyframec（只读契约，谁都拿得到）           Keyframe（可写实现，curves 持有）
  float time()                                time(float)
  float value()            ← 这一个数         value(float) / valueSource(ValueSource)
  float inSlope() ...                         inSlope(float) / inSlopeSource(...)
  EvaluateMode evaluateMode()                 ...
  （没有 *Source()）                          dynamic() / set(Keyframec)
```

- **只读取值的一方**（渲染、几何、信息展示、外部界面后端、第三方扩展）只需要数，
  拿 `Keyframec` 就够，且无法通过它改到数据。
- **求值与序列化**需要来源，它们拿的是 `Keyframe`（`Curve` 内部本来就持有具体类型，
  `Curve.key(int)` 返回的就是 `Keyframe`）。

### 4.2 值源：两态 + 一态分开

```java
/// 关键帧字段的取值来源：固定值，或一段公式（+ 回退值）
public sealed interface ValueSource permits ConstantValue, FormulaValue { ... }   // 从三态收成两态

/// 变量的取值来源：多了"读某条曲线轨道"
public sealed interface VariableSource permits ConstantValue, FormulaValue, TrackValue {
    float evaluate(@Nullable Scope scope);
    @Nullable String trackId();   // 非轨道来源返回 null（原来靠 Variable.trackId() 代劳）
    VariableSource copy();
}
public class Variable {                    // 其余不变，只是字段类型换成 VariableSource
    private VariableSource source;
    public VariableSource source();
    public Variable source(VariableSource source);
}
```

`ValueSource` 自己保留 `evaluate` / `constant` / `withConstant` / `copy` 四个成员，
其中 `withConstant` 的返回类型可以顺着实现收窄（不再需要"可能换对象"这句提醒）。

收益立刻可见：

- `constant()` 的说明不用再解释"轨道读数没有固定值，返回 NaN"——`ValueSource` 里不再有这种成员。
- `withConstant()` 只剩"就地改回退值"一种语义。
- `ExpressionFieldWidget` / `VariablePanel` / `ExpressionEditorWindow` 里的 `instanceof TrackValue` 分支
  从"关键帧字段也可能走到"变成"只有变量能走到"，类型上就分清了。
- **代价**：`Keyframe` 取值与变量取值不能再共用一个字段类型，`SymbolTable.references(Keyframe, name)`
  之类的工具方法要拆成两个重载。可控。

### 4.3 求值侧：一处类型判断，替掉五个 getter

`ResolvedKeys` 是唯一在求值期需要来源的地方（`ResolvedKeys.java:113`）。改造后：

```java
private float resolve(int index, int field) {
    ...
    // 这里按 Curvec.key 的签名收成只读视图；实际装的一定是 Keyframe
    Keyframec key = curve.key(index);
    // 只读视图只有固定数值；具体实现要参与动态求值，得自报值源
    value = key instanceof Keyframe mutable
            ? ValueSource.evaluateOrFallback(sourceOf(mutable, field), scope)   // sourceOf 就是原来那段 switch，搬个地方
            : staticOf(key, field);
    ...
}
```

`ResolvedKeys` 在 `api.animation.eval`，`Keyframe` 在 `api.animation`，同属 `api`，没有依赖方向问题。
自第三方实现只读 `Keyframec` 的键：它们本来也只能是静态的（`Curve` 内部只装 `Keyframe`），
语义与今天完全一致（`StaticKeys` 走的就是这条路，`StaticKeys.java:20`）。

> 如果希望"外部自己实现的键也能挂公式"，那就让 `Keyframe` 可继承或加一个
> `DynamicKeyframec extends Keyframec { ValueSource source(Field field); }` 的能力接口，
> 但**不要**为此把值源放回基础只读契约——那等于为了可能性牺牲掉所有调用方的清晰度。

### 4.4 编辑侧：一对入口，语义写进方法名

`ExpressionFieldWidget.Accessor` 现在直接读写 `ValueSource`（`ExpressionFieldWidget.java:32-36`），
改造后仍可以这样，只是它的实现从 `Keyframe` 的 `valueSource()` 取——**接口不变，实现变**：

```java
// KeyframePanel：现在
keySource(key::valueSource, key::valueSource)
// 之后（Keyframe 上的这一对仍是值源读写，只是不再出现在 Keyframec 上）
keySource(key::valueSource, key::valueSource)
```

也就是说面板一行都不用改（它拿的就是 `Keyframe`，`KeyframePanel.java:107`），
改的只是"这对方法从此不承诺给只读视图"。

顺带建议两个命名上的小修（与本次改造同批做，成本极低）：

| 现在 | 建议 | 理由 |
| --- | --- | --- |
| `Keyframe.value(float)` | 保留，文档写明"只改回退值、公式留着" | 行为本身是对的（`withConstant` 的 copy-on-write），但调用处看不见公式是否还在 |
| `FormulaValue.constant(float)` 与 `Keyframe.value(float)` 同名不同层 | 公式侧改名 `fallback(float)` | "constant" 在公式对象上读起来像"常量"，与 `ConstantValue` 撞词 |
| `Keyframec.value()` 在有公式时给的是回退值 | 文档加一句"公式在场时这是回退值；这一帧的值走 `CurveSampler`" | 现在只有 `Keyframe` 的类文档提了这件事（`Keyframe.java:17`） |

---

## 5. 备选方案与取舍

| 方案 | 做法 | 评价 |
| --- | --- | --- |
| **A. 保持现状**（推荐度：低） | 只读视图继续暴露值源 | 已有真实缺陷（第 2.2 / 2.4 节），且随着外部界面后端（`EditorSession`）走向公开，泄漏面只会变大 |
| **B. 五处只读出口返回快照**（推荐度：中） | `Keyframec.valueSource()` 返回 `source.copy()` | 一行修掉"改只读视图会改数据"，但每次调用都要分配，且调用方无法从类型上知道"我拿到的不是我写的那个"；治标 |
| **C. 把表示移出只读契约**（推荐度：高） | 第 4 节 | 契约与实现各归其位；改动集中在 7 个文件，且每一处都是"少一层间接" |
| **D. 全面重构：值源做成参数对象（Houdini 式）** | `Keyframe` 的五个字段合成一个 `FieldValue[]`，值源挂字段槽位 | 能一次解决重复成员问题，但会动到 `KeyValues` / `ResolvedKeys` / 曲线图的对应关系，收益（少写几十行）与风险不成比例 |

**建议 C，A/B 都不取，D 留待字段数量真的增长时再说。**

---

## 6. 改造清单（按可独立编译的顺序）

1. **拆值源**：`ValueSource` 的 `permits` 去掉 `TrackValue`；新增 `VariableSource`（`TrackValue` 实现它，
   并把 `trackId()` 提到接口上）；`Variable` 换成 `VariableSource`；
   `SymbolTable.rebindTrack` / `unbindTrack` 改用 `VariableSource.trackId()`；
   `AnimationCodec` 的变量读写切到 `VariableSource`（写出的 JSON 不变：`{"track": "..."}` 照旧）。
2. **收窄关键帧入口**：`Keyframec` 删掉五个 `*Source()`；`ResolvedKeys` 加一处 `instanceof Keyframe` 判断
   （也可以顺手把 `Field` 做成一个小枚举/记录，替掉 `switch (field)`）。
3. **收紧读档**：`AnimationCodec` 的关键帧读取不再认 `track` 字段——遇到 `{"track": ...}` 时按
   `fallback`（没有则 0）读成固定值，并记一条日志。**旧文件不会读坏，只是不再产生非法键。**
4. **文档同步**：`docs/animation-editor-guide.md` 的 2.1（第 89-94 行）与 2.7（第 220-230 行）、
   `Keyframe` / `Keyframec` / `ValueSource` / `TrackValue` 的类文档、`README*.md` 里相关段落。
5. **回归点**：读档（含 `{"track": ...}` 的历史文件）、复制粘贴（`KeyClip`）、公式 UI 切换
   （`toggleMode`）、变量绑定轨道改名/删除、`Curve.smoothTangents`（它改的是斜率**回退值**，公式会留下）。

风险与影响面：`api` 的对外签名有变化（五个只读方法消失）。`EditorSession` 目前**没有**把它们露给外部后端
（`EditorSession.java` 只给到 `animation()`），所以对外部后端的影响是零；真要说有，是"少了一个以后可能会被
误用的入口"，这正是本次要消掉的东西。

---

## 7. 一句话总结

值源本身没问题——"公式跟着数值走、回退值就存在字段里"是这套设计最漂亮的地方，
Blender 直到 4.x 才给驱动变量补上 fallback，我们一开始就有。
有问题的是**把它放在了只读视图上**：只读视图于是成了可变对象的发放入口，
而值源还得同时扮演"数据 / 求值器 / 编辑器"三个角色、容忍一个永远不该出现的 `TrackValue`。
把表示移回可写类、把三态收成两态 + 变量专用一态，`Keyframec` 就回到它应有的样子：
**它只回答"这个键在这一刻是几"，不回答"这个几是怎么来的"。**
