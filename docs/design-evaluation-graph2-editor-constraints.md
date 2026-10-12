# 求值依赖图 v2：让编辑器的写入绕不过图

范围：`api.animation.eval.EvaluationGraph2`、`api.animation.expression.*`（`Variable` / `CustomFunction` /
`SymbolTable`）、`api.animation.Keyframe` / `curve.Curve`、`api.animation.CameraAnimation`、
`api.editor.EditorSession`、`core.editor.*`（各面板、`ExpressionEditorWindow`、`EditorHistory`）、
`core.animation.io.AnimationCodec`。

**前提：允许破坏性修改，不背向前兼容。** 这一条直接决定了几种手段的可行性——`api` 里那些"谁都能调"的
写方法可以收窄，只读契约（`CameraAnimationc` / `Keyframec` / `EditorSession`）继续公开。

结论先写在第一节，后面是不变量、今天的写入面清单、六种手段的取舍、比较过的其他路线、落地顺序与验收。

---

## 1. 结论

**靠"作者记得调"不行；但也不必上事务系统。把唯一写入口与可见性收窄做掉之后，"绕过图"在编译期就通不过；
再用对账兜住类型管不到的那条路（整份替换）。**

按落地顺序五条：

| 阶段 | 做什么 | 治的是哪一类问题 |
| --- | --- | --- |
| P0 | sealed 结果类型（§4） | 忘记处理"被引用 / 成环"两支 |
| P0 | 对账 `reconcile`（§5） | 漏调、传错参数、整份替换后失联——一律当场暴露 |
| P1 | 唯一写入口 `AnimationEdits`（§6） | 写入点散落二十来处、顺序（先图后数据）由面板各自决定 |
| P2 | 收窄数据类的写方法（§7） | "绕过图直接改数据"从纪律问题变成编译错误 |
| P3 | 批量替换自动重建（§8）+ 编辑脚本测试（§9） | 撤销/重做、读档；把要求变成 CI 门禁 |

一句话版本：**把"图的要求"从注释搬进类型（sealed 结果 + 收窄可见性），再用对账兜住类型管不到的地方。**

**已定：不允许绕过。** 外置界面与内置界面走同一个门面（`AnimationEdits` 属于 `api.editor`），
`api` 里那些"谁都能调"的写方法全部收窄——§7 的收窄范围因此是全面的、对外不留口子。
这条同时决定了 `EditorSession` 必须补齐变量 / 函数 / 轨道的写动作（现在只开到关键帧与路径点），
否则默认实现自己也编辑不了；详见 [`design-api-default-editor.md`](design-api-default-editor.md)。

现在不推荐做的：凭证 token（§10.3）——与 P1+P2 目标重叠，代价大一档。

---

## 2. 要维持的四条不变量（整份设计从这里长出来）

依赖图 v2 的类文档写着两条"配套编辑器要求"（查引用才能删、改前判环）。把它们拆成可检查的形式，是下面四条：

| 编号 | 不变量 | 破了会怎样 |
| --- | --- | --- |
| **I1** | 图的节点集合 ≡ 数据里的变量 ∪ 函数 ∪ 轨道（按名字） | 少节点 → 公式对它的引用被当成"未定义"滤掉 → **判环漏判**；多节点 → **误报**，而且同名的新数据会"复活"旧节点的边 |
| **I2** | 每个节点的出边 ≡ 它当前来源解析出的引用：变量看 `ValueSource`、函数看函数体（形参不算）、**轨道看各槽位公式引用的并集（按公式计数）** | 少边 → **漏判**（该拦的环放过去，最危险）；多边 → 误报，且 `hasCycle()` 会报出数据里并不存在的环 |
| **I3** | 写入期无环：会成环的改动既不进图、也不落数据 | 数据里出现环 → 求值退回 NaN、编辑器显示与播放结果对不上 |
| **I4** | I1–I3 在**任何时刻**成立，包括整份替换之后（撤销/重做、切换动画、读档） | 一次撤销就让图与数据脱节，之后所有判断都建立在错的图上 |

关键在 I2 的**方向**：漏边是"静默放行"，不会报错也不会崩，只会在某一天让人发现"这个环居然建起来了"。
加固的首要目标就是把这个方向堵死。

---

## 3. 今天的写入面：靠自觉的地方有多少

### 3.1 依赖相关的写入点（粗数二十来处，分布在十来个类里）

| 类别 | 写入点 |
| --- | --- |
| 变量 | `VariablePanel.java:331` 新建 / `:351` 删除 / `:391` 改名 / `:269` 换固定值 / `:437` 绑轨道 / `:448` 挂公式；`ExpressionEditorWindow.java:980,989` 自己的变量表也能增删 |
| 函数 | `FunctionPanel.java:212` 新建 / `:231` 删除 / `:256` 改名；函数体走 `ExpressionEditorWindow`（`Subject.Function`，`:193`） |
| 关键帧槽位 | `KeyframePanel.java:297` 与 `ExpressionFieldWidget` 的 `accessor.source(...)`（`:99` 数值框 / `:214` 模式切换 / `:239` 公式窗口） |
| 轨道 | `CameraAnimation.java:88` `addChannel` / `:113` `addCurveTrack` / `:130` `renameCurveTrack` / `:162` `removeChannel`；`TimelinePanel.java:1119,1140`；`CameraEditorScreen.java:666` |
| 关键帧本体 | `CameraEditorModel.java:189` 加键 / `:230,270` 删键 / 拖动；`TimelinePanel.java:864,985,1047,1059,1066`；`EditorSession.java:110-119` |
| 读档 | `AnimationCodec.java:178` `addFunction` / `:387` `addChannel` / `:396` `removeChannel` / `:433` `curve.removeKey` / `:457` `addVariable` / `:460` `variable.source(...)` |

### 3.2 两条不是"纪律问题"的洞

- **整份替换**：`EditorHistory.restore` 走 `animation.copyFrom(loaded)`（`EditorHistory.java:89-107`），
  `SymbolTable.replaceFrom`（`SymbolTable.java:296`）同理。这一类不经过任何增量入口，
  增量维护的图在这条路上**必然**脱节——不是作者忘了，而是没有可忘的地方。
- **粘贴**：`CameraEditorScreen.pasteKey`（`:630`）把 `KeyClip` 里**五个槽位的来源快照**一起插进轨道
  （`KeyClip` 定义在 `:671`），也就是说一次粘贴可能同时带进多条公式，甚至按 id 重建一条轨道。
  这类"一次动作改多处引用"的入口，正是最容易漏调图的地方。

### 3.3 为什么不能让数据自己问图

最诱人的方案是"把判环下沉到 `Variable.source(...)` 这类 setter 里，数据自己问图"。**包环这条理由其实不硬**：
这套代码里已经有一处 `track ↔ eval` 的包环——`CurveTrack.java:5` import 了 `eval.CurveSampler`，
而 `EvaluationGraph2` import 了 `track.CurveTrack`。文档把它当成"有意少欠的债"记录过一次
（`docs/design-keyframe-value-source-target.md:695`，用 `ExpressionScope.of` → `CurveSampler.scope`
避掉 `eval → track → eval`），但既然已经欠了一笔，再多一笔 `expression ↔ eval` 也不是不能谈。

真正让"数据自查"不成立的是另外两条：

1. **setter 不知道该问哪张图。** 一张动画一张图（§8），而 `Variable` 不知道自己在哪张动画里。
   给数据对象挂一个图引用，等于把图的生命周期绑进数据，而撤销 / 重做是"整份替换数据"
   （`CameraAnimation.copyFrom`），很容易留下指向旧图的引用——那正是 §5 要抓的一类脱节，
   却被"数据自查"引到了数据对象内部，反而更难查。
2. **setter 的签名放不下判据。** `void source(ValueSource)` 只有"抛异常拒绝"一条路，
   而编辑器需要的是结构化信息：环在哪一条、被谁引用、是拒绝还是可以断开引用。
   现在的 `CheckResult` / `Removal`（§4）就是为这个设计的。要把这些塞进 setter，就得再造一个返回值类型——
   那已经是门面了，不如把门面放在数据外面（§6）。

所以加固只能在**调用侧**做：sealed 结果、对账、门面 / 可见性收窄，三选若干。

---

## 4. 手段一：sealed 结果类型（P0，成本最低）

### 4.1 形状

```java
// EvaluationGraph2 内
public sealed interface Removal {
    /// 删掉了（连带删除时为整条链，含起点）
    record Done(Set<Node> removed) implements Removal { }
    /// 被引用，没删；编辑器据此弹"断开引用 / 整条删除"的确认框
    record Blocked(Set<Node> referrers) implements Removal { }
    /// 本来就不在图里
    record Absent() implements Removal { }
}

public Removal removeNode(Node node);
public Removal removeNodeCascade(Node node);
// breakReferences 没有"被拒"这一支，仍旧返回被断开的引用者集合
public Set<Node> breakReferences(Node node);

public sealed interface Edit {
    record Applied() implements Edit { }                      // 将来要挂凭证就挂在这里，见 §10.3
    record Rejected(List<List<Node>> cycles) implements Edit { }
}

public Edit applyTrackFormula(String trackId, @Nullable String oldExpression, @Nullable String newExpression);
```

### 4.2 为什么比现在的 `boolean` 强

现在 `removeNode` 返回 `boolean`，被拒时引用者要**另外**调一次 `referrers(node)` 才知道——两步之间可以做错两次：
忘了第二步（提示不出来，只能给一句"删不掉"），或者拿了引用者却自己拼提示文案（与图的判断不一致）。
sealed 结果只有一种处理方式：`switch` 穷尽三支，`Blocked` 带着引用者一起来。

`apply*` 那边现在是 `CheckResult`：SUCCESS 不带任何信息，CYCLE 才带环，调用方得先看类型再决定读不读
`cycles()`；而 `ERROR` 这一支从来没有被生产过（`EvaluationGraph2` 只用 `SUCCESS` 与 `cycle(...)`），
是纯占位。sealed 之后只剩两支，`Rejected` 一定带环。

### 4.3 改动面与验收

- 图侧：`removeNode` / `removeNodeCascade` 两个删方法 + 三个 `apply*` 换返回类型；
  `breakReferences` 保持原样；`check*` 保持 `CheckResult` 不变（它是只读试算，没有"落不落地"这一说）。
- 编辑器侧：目前还没有调用点（v2 未接入），所以在接入时一并按新类型写。
- 验收：`switch` 穷尽；测试断言 `Blocked` 里的引用者与 `referrers(node)` 一致。

---

## 5. 手段二：对账 `reconcile`（P0，兜底与验收工具）

### 5.1 形状

```java
/// 一处不一致
public record Drift(Kind kind, Node node, Object expected, Object actual) {
    public enum Kind {
        MISSING_NODE,   // 数据里有、图里没有（漏了 addNode）
        EXTRA_NODE,     // 图里有、数据里没有（漏了 removeNode）
        MISSING_EDGE,   // 该有的引用没连上（少了 apply*，或旧表达式传错）
        EXTRA_EDGE,     // 图里多了一条引用（数据已改，图没跟着退）
        COUNT_MISMATCH  // 边对但引用计数不对：下一次 applyTrackFormula 会算错
    }
}

/// 拿数据现状与本图对账：按构造期同一套口径重算节点、边与轨道计数，逐项比较。空表 = 一致。
public List<Drift> reconcile(List<Variable> variables, List<CustomFunction> functions, List<AnimationTrack> tracks);
```

### 5.2 实现要点

- **影子图直接用构造器**：`new EvaluationGraph2(variables, functions, tracks)`，再把影子图的节点集合、边集合、
  轨道计数与本图 diff。同一个口径只写一遍，对账不会自己走偏。
- **比集合不比顺序**：节点与边用 `Set` 比；环信息按"每条环规范化之后排序"的集合比（测试里那套 `canonical` 逻辑
  可以抽成图自己的 `cycleKey()`），`cycleInfo` 的顺序不该参与判断。
- **计数也要比**（`COUNT_MISMATCH`）：只比边的话，"边还对但计数漂了"会潜伏到下一次 `applyTrackFormula` 才爆，
  那时已经定位不到是哪次编辑弄坏的。

### 5.3 什么时候调

| 时机 | 目的 |
| --- | --- |
| 每次编辑提交之后（调试构建） | 漏调当场炸在出问题的那一步，而不是几十步之后 |
| 保存 / 导出之前 | 别把脱节的图当成"已经校验过"的依据写进文件 |
| 撤销 / 重做 / 读档之后 | 抓 I4（整份替换） |
| 测试每一步之后 | §9 的编辑脚本冒烟 |
| v2 接入编辑器的验收 | 一条命令给出"还有多少处写入没走图" |

### 5.4 它管不到的两件事（要老实写进文档）

- **未定义引用**：公式里写了个还不存在的名字，两边（本图与影子图）都看不见这条引用，所以对账也看不见——
  这正是"新建一个已被别的公式提到的名字"那条要在编辑器侧拦的原因（见 `EvaluationGraph2` 类文档的"边界"）。
- **根本没调对账**：如果编辑器某条路连数据都改了却从不触发对账，它就不会说话。所以对账是**兜底**，
  不是替代品——真正把门关上的是 §6 与 §7。

---

## 6. 手段三：唯一写入口 `AnimationEdits`（P1）

### 6.1 为什么需要

写入点有二十来处（§3.1），每一处都要同时做对三件事：调对图的方法、传对参数（尤其 `applyTrackFormula` 的旧表达式）、
保持"先图后数据"的顺序。让每个面板各自负责这三件事，就是"全看作者水平"的根源。

**先图后数据是安全方向**：图超前于数据（图里还留着一条数据里已经没了的边）只会**误报**环；数据超前于图
（数据里已经挂上公式、图还不知道）就会**漏判**。所以门面的骨架固定为：

```text
① 图侧 apply/remove（内部先判环/先查引用）→ 被拒就到此为止，数据不动
② 数据落地（setter 不抛异常，这一步不会失败）
③ 需要时（批量路径）整图重建
```

### 6.2 方法清单（对着 §3.1 一条一条给出口）

```java
public final class AnimationEdits {
    // 变量（新建没有"被拒"这一支，名字冲突由数据层回 null，故直接返回建出来的对象）
    @Nullable Variable addVariable();
    Edit removeVariable(String name, RemovalMode mode);    // REFUSE / BREAK / CASCADE
    Edit renameVariable(String from, String to);           // 见 §6.4：改完重建，成环就改回去
    Edit setVariableSource(String name, ValueSource source);// applyVarFormula → 写 source
    Edit bindTrack(String variable, String trackId);       // 同上，source = TrackRef

    // 函数
    @Nullable CustomFunction addFunction();
    Edit removeFunction(String name, RemovalMode mode);
    Edit renameFunction(String from, String to);
    Edit setFunctionBody(String name, List<String> parameters, String body);

    // 轨道
    @Nullable CurveTrack addTrack(String baseName);
    Edit removeTrack(String id, RemovalMode mode);
    Edit setKeySource(String trackId, int keyIndex, KeyField field, NumberSource source); // applyTrackFormula(旧→新)
    Edit addKey(String trackId, float time);
    Edit removeKey(String trackId, int keyIndex);          // 先把它五个槽位的公式逐条退掉
    Edit pasteKey(String trackId, float time, Map<KeyField, NumberSource> sources, EvaluateMode mode);

    // 整份替换（撤销/重做、读档、切换动画）
    Edit replaceAll(CameraAnimationc source);
}
```

`RemovalMode { REFUSE, BREAK, CASCADE }` 就是把要求 1 的三条出口（拒绝 / 断开引用 / 沿引用链全删）
摆成调用方必须选的参数——不选就编译不过。

### 6.3 与 `EditorSession` 的关系

`api.editor.EditorSession` 已经是这类门面的**现成先例**：外置界面（附属 mod 的界面）拿到的是它，
能做的只有"选中 / 加键 / 移键 / 删键 / 记录路径点"这类受控动作（`EditorSession.java:89-121`），
读模型只给只读契约 `CameraAnimationc` / `Pathc`。也就是说"唯一写入口"在这套代码里不是新概念，只是**目前开出去的写动作不含变量 / 函数 / 轨道来源**。

**已定（不允许绕过）：`AnimationEdits` 属于 `api.editor`，是外置与内置共用的唯一写入口。** 两条后果：

1. `EditorSession` 必须补齐写动作——变量增删改名 / 换来源 / 绑轨道、函数增删改名 / 改形参与函数体、
   轨道增删改名、关键帧槽位来源、加键 / 删键 / 移键、粘贴、整份替换（对照 §3.1 的写入点清单）。
   现在它只开到"关键帧与路径点"。
2. 内置界面也必须走这份契约。它今天走的是 core 的 `EditorContext`（宽），默认实现因此**不遵守自己对外发的契约**；
   把默认实现注册成一个后端（优先级最低）就能把这条裂缝合上，做法见
   [`design-api-default-editor.md`](design-api-default-editor.md) §3.1。

### 6.4 三条容易写错的入口（增量模型下只有整图重建才照得出来）

1. **增**：新建变量 / 函数 / 轨道之后**不要只 `addNode`，顺手整图重建**。理由有两层：
   新节点没有出边，永远闭合不了环（要求 1 的前提成立）；而重建会按新节点集合重新解析一遍所有公式，
   把"这个名字其实早就被别的公式写到了"这类引用一次照出来——增量 `addNode` 看不见它们
   （公式里未定义的名字在两边都不成边，见 §5.4）。
2. **改名**：一律"先改数据、再整图重建"，**重建后发现成环就把名字改回去并返回 `Rejected`**。
   改名和新建一样会让**本来未定义的引用突然成立**（公式里写着 `b`、`b` 还不存在，此时把某个变量改名成 `b`），
   这是增量 `renameNode` 那种"搬节点身份 + 处理入边"的做法照不到的；加上轨道改名改指向、
   变量 / 函数改名让旧公式落空，两类语义本就不同（见 §7 末段）。
3. **断开引用**：`breakReferences` 只抹掉"这一个引用"，同一条公式里引用到的别的名字还留在轨道计数里
   （图侧文档已写明这个残余偏保守）。所以编辑器改写那条公式之后，应当按槽位再调一次
   `setKeySource(trackId, keyIndex, field, Constant(0))`（`applyTrackFormula(旧 → 空)`）把计数校准。

---

## 7. 手段四：收窄数据类的写方法（P2，真正的"编译期关死"）

把门面建起来之后，**把没用门面的路拆掉**：

| 类 | 收窄哪些 | 现位置 |
| --- | --- | --- |
| `Variable` | `source(...)`、`name(...)` | `Variable.java:43,53` |
| `CustomFunction` | `body(...)`、`parameters(...)`、`name(...)` | `CustomFunction.java:36-54` |
| `SymbolTable` | `add*` / `remove*` / `rename*` / `rebindTrack` / `unbindTrack` / `replaceFrom` | `SymbolTable.java:77-314` |
| `Keyframe` | `source(KeyField, NumberSource)`、`constant(...)`、`time(...)`、`evaluateMode(...)` | `Keyframe.java:150-221` |
| `Curve` | `key(...)`、`removeKey`、`moveKey` | `Curve.java:121-185` |
| `CameraAnimation` | `addChannel` / `removeChannel` / `addCurveTrack` / `renameCurveTrack` / `addExtensionTrack` / `removeExtensionTrack` / `renameExtensionTrack` / `copyFrom` | `CameraAnimation.java:88-235` |

手段（不必只选一种）：

- **包内可见**：把写方法降到包内，门面与它们同包；跨包调用在编译期就通不过。
- **换包**：把这些可变实现挪到 `api.animation.edit`（或 `internal`）包，只读契约 `CameraAnimationc` /
  `Keyframec` 留在原地公开。允许破坏性修改之后这条最省事，也最彻底。
- **只读视图照旧**：`CameraAnimationc` / `Keyframec` / `EditorSession.animation()` 继续公开，
  外置界面读得到、写不了——这本来就是它们存在的意义。

收窄是**全面的**（已定"不允许绕过"）：`api` 只留只读契约与 `EditorSession` / `AnimationEdits` 里的受控动作，
不留第二条改数据的路。外置 mod 要做同样的编辑就走会话；想复用默认界面则走"默认后端 + 包装"，见
[`design-api-default-editor.md`](design-api-default-editor.md) §3.1 与 §6 第 2 条。

**判据（可以做成 CI 检查）**：收窄之后，`core/editor/**` 里搜不到 `.source(`、`symbols().add`、
`.removeVariable(`、`.removeFunction(`、`addChannel(` 这类写入；出现即视为绕过门面。
`grep` 一条命令就能守住，比靠 review 可靠。

**改名的口径**见 §6.4 第 2 条（先改数据、再整图重建，成环就改回去）：图分不清一条入边来自 `TrackRef`
绑定还是公式文本，所以不要试图增量搬运节点。

---

## 8. 手段五：批量替换处自动重建（P1 一起做）

三条"整份换掉"的路，各自在收尾处重建图，**不依赖任何面板作者记得**：

| 入口 | 现在做什么 | 改成 |
| --- | --- | --- |
| `EditorHistory.restore` → `CameraAnimation.copyFrom` | 原地替换全部内容 | 替换完由 `copyFrom` 自己重建图 |
| `SymbolTable.replaceFrom`（切换动画 / 读档） | 整批换变量与函数 | 同上 |
| `AnimationCodec` 读档收尾 | 读完即算完成 | 读完建图，并按 v1 现成的口径把"成环"挡在读档这一步（`docs/animation-editor-guide.md` 的 2.7：四个入口都过一遍依赖图，读档是其一） |

**图的归属**：一张动画一张图，挂在 `CameraAnimation` 上（`evaluationGraph()` 现在每次现建一张 v1 的图；
v2 要变成"跟着动画走的实例"）。挂上去之后，"数据整体替换 ⇒ 图整体重建"就写死在这三处，
而不是一条要传给所有面板作者的规矩。

---

## 9. 手段六：测试与 CI（P0 起）

对账要能证明自己有用，否则它只是个装饰。两类用例：

**① 编辑脚本冒烟**：一串动作按顺序走完，**每步之后 `reconcile` 必须为空**：

```text
新建变量 → 挂公式 → 绑轨道 → 加轨道 → 加键 → 给槽位挂公式 → 改键的公式（旧→新）
→ 复制粘贴到另一条轨道 → 删键 → 删变量（被引用：先拒，再断开引用）→ 撤销 → 重做 → 读档
```

**② 故意破坏用例**（每条都要报出对应 `Drift`，报不出说明对账有盲区）：

| 破坏方式 | 应报 |
| --- | --- |
| 新建变量但没 `addNode` | `MISSING_NODE` |
| 删了变量但没 `removeNode` | `EXTRA_NODE` |
| 挂公式没调 `applyVarFormula` | `MISSING_EDGE` |
| 摘公式没调（图里留着旧边） | `EXTRA_EDGE` |
| `applyTrackFormula` 旧表达式传错 | `COUNT_MISMATCH` 或 `EXTRA_EDGE` |
| `copyFrom` 之后不重建图 | 一整套 `Drift` |

这六条用例在 CI 里全绿，才算"编辑器遵循了图的要求"——比任何注释都硬。

---

## 10. 比较过的其他路线

### 10.1 每次改完重建（rebuild-on-write）

"每次写完数据就整图重建"听起来最省心（一致性由构造保证，`trackRefCounts` 的计数、旧 / 新表达式这些记账
全都不需要）。但它**替代不了增量**：要求 2 是"**改之前**先判环"，而重建发生在改之后——
要么把数据先拷一份、把改动应用在副本上再重建（等于引入快照），要么退回 `check*` 的近似口径
（`checkTrackFormula` 判的是"整组换成这一条"，与"只加这一个槽位"在**无环图**上判据一致，但终究是近似）。

结论：**增量用于"改"（`apply*` 就是"判环 + 落地"这一对），整份重建用于批量路径（§8）**。
两条各管一段，而不是二选一。

### 10.2 数据自查

见 §3.3：setter 不知道该问哪张图，签名也放不下环路径这类判据，走不通。

### 10.3 凭证 token

```java
public sealed interface Permit permits Permit.Write, Permit.Remove { }   // 构造器私有，只有图能铸造
variable.source(source, permit.Write);        // 没有凭证编译不过
symbols.removeVariable(name, permit.Remove);  // 被引用时图不发 Remove 凭证
```

能把"没问过图就写不进去"变成编译错误，但要求凭证**单次有效、绑定参数**（否则存一个下来到处用），
再往下就是编辑事务系统。而 P1+P2（门面 + 收窄可见性）已经把"绕过"堵死了，
凭证只是把同一件事换个更重的形式——所以列在这里备查，不推荐先做。

### 10.4 图当纯派生视图（v1 现在的形态）

v1 的 `EvaluationGraph.of(animation)` 每次现建，**天然满足 I1–I4**，一行纪律都不需要。代价是三个入口
（删前查引用、改前试算、报环）每次都要重建一遍图；几十上百节点其实也能接受。
v2 走增量的理由是"编辑器要反复问"（拖拽时每帧查引用、试算要连问几次），
所以真正的问题不是"增量对不对"，而是"增量的纪律用什么补"——本文的答案就是 P0+P1+P2。

---

## 11. 落地顺序与验收

| 阶段 | 改动面 | 验收判据 |
| --- | --- | --- |
| P0-a sealed 结果 | `EvaluationGraph2` 两个删方法（`removeNode` / `removeNodeCascade`）+ 三个 `apply*` 换返回类型；接入时编辑器按 `switch` 写 | `switch` 穷尽；测试断言 `Blocked` 的引用者与 `referrers` 一致 |
| P0-b 对账 | 新增 `reconcile` / `Drift` + `cycleKey()` 规范化；`EditorConfig` 加调试开关 | §9 的六条破坏用例全部报出对应 `Drift` |
| P1 门面 | 新增 `AnimationEdits`（§6.2）；面板的写入点逐个改走它 | 对账在编辑脚本每一步都为空；`RemovalMode` 三支各有用例 |
| P2 收窄 | §7 的清单；只读契约不变 | 原来的绕过写法**编译不过**；`core/editor/**` 的 grep 判据通过；外置界面改走 `EditorSession` / `AnimationEdits` 仍能完成原有动作（补一个"用会话走一遍编辑脚本"的用例） |
| P3 批量与 CI | `copyFrom` / `replaceFrom` / 读档收尾重建图；编辑脚本进 CI | 撤销、重做、读档之后 `reconcile` 为空；六条破坏用例在 CI 里全绿 |

一个可执行的验收动作（跑在 CI 或本地）：`.\gradlew test` + 编辑脚本冒烟用例；两者全绿即视为达标。

---

## 12. 已定与待定

**已定**：

1. **不允许绕过**：改数据只有 `AnimationEdits` 一条路，外置与内置共用（§6.3）。
2. **门面放 `api.editor`**：与 `EditorSession` 同一层，外置 mod 能拿到；数据类的写方法全面收窄（§7）。

**待定**：

1. **对账的开关与时机**：只在调试构建常开，还是给一个配置项（例如保存前总是对一次账）？
2. **撤销粒度与重建开销**：`EditorHistory` 现在 250ms 节流（`EditorHistory.java:21`），
   重建图的开销要不要计入这个节流判断？
3. **`EvaluationGraph2` 的最终归属**：继续由编辑器持有，还是像 §8 说的挂到 `CameraAnimation` 上
   （读档与编辑器共用一张；播放不用它，播放走的是 `CurveSampler`）？这一条决定 §8 能否只用一处钩子。
