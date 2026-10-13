# 求值依赖图 v2：让编辑器的写入绕不过图

范围：`api.animation.eval`（`EvaluationGraph2` 与自定义图结构）、`api.animation.expression.*`（`Variable` /
`CustomFunction` / `SymbolTable`）、`api.animation.Keyframe` / `curve.Curve`、`api.animation.CameraAnimation`、
`api.editor.EditorSession` / `AnimationEdits`、`core.editor.*`（各面板、`ExpressionEditorWindow`、`EditorHistory`）、
`core.animation.io.AnimationCodec`。

**前提：允许破坏性修改，不背向前兼容。** 这一条直接决定了几种手段的可行性——`api` 里那些"谁都能调"的
写方法可以收窄，只读契约（`CameraAnimationc` / `Keyframec` / `EditorSession`）继续公开。

结论先写在第一节，后面是不变量、今天的写入面清单、七种手段的取舍、比较过的其他路线、落地顺序与验收。

---

## 1. 结论

**靠"作者记得调"不行；但也不必上事务系统。把图的实现改成自己的结构、把唯一写入口与可见性收窄做掉之后，
"绕过图"在编译期就通不过；再用对账兜住类型管不到的那条路（整份替换）。**

按落地顺序六条：

| 阶段 | 做什么 | 治的是哪一类问题 |
| --- | --- | --- |
| P0 | 自定义图结构（§4） | 影子状态、简单图语义、`eval → track` 的反向依赖——三类结构性阻力一次去掉 |
| P0 | sealed 结果类型（§5） | 忘记处理"被引用 / 成环"两支 |
| P0 | 对账 `reconcile`（§6） | 漏调、传错参数、整份替换后失联——一律当场暴露 |
| P1 | 唯一写入口 `AnimationEdits`（§7） | 写入点散落二十来处、顺序（先图后数据）由面板各自决定 |
| P2 | 收窄数据类的写方法（§8） | "绕过图直接改数据"从纪律问题变成编译错误 |
| P3 | 批量替换自动重建（§9）+ 编辑脚本测试（§10） | 撤销/重做、读档；把要求变成 CI 门禁 |

一句话版本：**把"图的要求"从注释搬进类型（sealed 结果 + 收窄可见性），把图的容器换成跟设计同构的结构，
再用对账兜住类型管不到的地方。**

**已定三条**：

1. **不允许绕过。** 外置界面与内置界面走同一个门面（`AnimationEdits` 属于 `api.editor`），
   数据的写方法全面收窄——§8 的收窄范围因此是全面的、对外不留口子。
   这条同时决定了 `EditorSession` 必须补齐变量 / 函数 / 轨道的写动作（现在只开到关键帧与路径点），
   否则默认实现自己也编辑不了；详见 [`design-api-default-editor.md`](design-api-default-editor.md)。
2. **图的容器自己写**（§4）。理由是设计需要的"引用重数"在 `MutableGraph` 里表达不了，
   而现在的绕法（图 + 图外的 `trackRefCounts`）本身就是对账要抓的那类影子状态。
3. **图是运行期的判据缓存，不是权威状态**（§2.1）。产物与图无关，所以图可以随时重建；
   唯一不能容忍的是它变成"第二份真身"。

现在不推荐做的：凭证 token（§11.3）——与 P1+P2 目标重叠，代价大一档。

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

### 2.1 图是什么：一份判据缓存，不是权威状态

这一条要在动手之前说清楚，否则上面四条会被读成"必须时刻维护一份权威状态"。

**一个完全正确的动画，保存完之后不需要图就能播。** 代码里能验到三件事：

- `AnimationCodec.animationToJson` 写的是名称、运动模式、轨道、变量、函数、路径（`AnimationCodec.java:103-131`），
  **里面没有图**；
- `EvaluationGraph2` 的**构造器本身就是一次完整重建**——图可以从数据完全重新推导；
- `reconcile` 之所以成立，靠的正是"影子图可以直接 `new` 出来"。

所以：**图不产出动画，它只判断"这次改动能不能落"**。它的生命周期跟着编辑会话，不是跟着文件走。

但**不能顺着推出"那就每次重建"**：纯派生会撞上 I3 的时态——"**改之前**先判环"，而改之前没有改后的数据可派生。
要么先拷一份数据、把改动应用在副本上再重建（等于引入快照），要么退回 `check*` 的近似口径。
所以"增量记账 + 对账兜底"这个结论不变（§11.1 已比较过两条路线）。

**真正的推论是另一句**：产物与图无关 ⇒ 图不是权威状态 ⇒ **唯一不能容忍的是它变成"第二份真身"**，
也就是有人在门面之外改了数据，让**判据与产物说的不是同一件事**。图与数据脱节本身不会让产物出错
（产物里没有图），它只会让编辑器**放行一个本该拦住的改动**——那个改动才是产物里的错。

这条推论落到三处，后面各节会反复用到：对账的定位（§6）、撤销重做里图的位置（§9.3）、图的归属（§9.1）。

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
（**顺带说：这笔债在 §4 会顺手还掉一半。**）

真正让"数据自查"不成立的是另外两条：

1. **setter 不知道该问哪张图。** 一张动画一张图（§9），而 `Variable` 不知道自己在哪张动画里。
   给数据对象挂一个图引用，等于把图的生命周期绑进数据，而撤销 / 重做是"整份替换数据"
   （`CameraAnimation.copyFrom`），很容易留下指向旧图的引用——那正是 §6 要抓的一类脱节，
   却被"数据自查"引到了数据对象内部，反而更难查。
2. **setter 的签名放不下判据。** `void source(ValueSource)` 只有"抛异常拒绝"一条路，
   而编辑器需要的是结构化信息：环在哪一条、被谁引用、是拒绝还是可以断开引用。
   现在的 `CheckResult` / `Removal`（§5）就是为这个设计的。要把这些塞进 setter，就得再造一个返回值类型——
   那已经是门面了，不如把门面放在数据外面（§7）。

所以加固只能在**调用侧**做：自定义结构、sealed 结果、对账、门面 / 可见性收窄，四选若干。

---

## 4. 手段一：自定义图结构（P0，最先做）

### 4.1 为什么不再用 `MutableGraph`

现在图用 Guava 的 `MutableGraph`（`EvaluationGraph2.java:8-9`），而 Guava 的 `MutableGraph` 是
**简单图语义**：一对节点之间只有一条边，没有重数。这份设计需要的恰好是重数——一条轨道上可以有多个槽位
引用同一个变量。于是现在的代码把重数放在**图外面**：

```java
/// 每条轨道上「每个被引用到的节点」被几条公式提到过；TRACK 节点的出边由它现算。
private final Map<Node, Map<Node, Integer>> trackRefCounts = new HashMap<>();
```

这份计数表是**权限所在**，图只是它的投影：构造期 `trackEdges(...)` 填计数、再 `putAllEdges(nodeU, counts.keySet())`；
`applyTrackFormula` 在计数的副本上退旧进新、**拿副本的 keySet 判环**，通过才 `commitRefs` 把边搬上图；
`breakReferences` 删不掉一个重数，只能"遍历所有计数表把这一项抹掉"。

**于是同一份信息有两个载体**，就必然有一类"两边不一致"的错误——`COUNT_MISMATCH` 与"断引用后要再校准一次
计数"都是这个拆分的直接产物。Guava 在这个设计里没有帮忙，它只是被绕过去了。

### 4.2 形状：邻接表就是正身

```java
/// 自定义可变图：**重数是一等公民**。
/// out[u][v] = u 引用 v 的次数，0 表示没有这条边；in 是它的反向表，两者永远同步增删。
/// 一个节点没有出边就没有后继；「有没有入边」由 in 回答——删除前的查引用与判环都读它。
final class RefGraph {
    private final Map<Node, Map<Node, Integer>> out = new LinkedHashMap<>();
    private final Map<Node, Map<Node, Integer>> in = new LinkedHashMap<>();

    boolean addNode(Node node);
    boolean contains(Node node);
    boolean hasIncoming(Node node);                 // 取代图上的 predecessors().isEmpty()
    Set<Node> referrers(Node node);                 // 直接引用者 = in 的键集
    Set<Node> dependents(Node node);                // 沿 in 反向走（原来的 dependents）
    Set<Node> outgoing(Node node);                  // = out 的键集（计数 > 0）

    void addRef(Node from, Node to);                // 计数 +1，边不存在则建
    boolean removeRef(Node from, Node to);          // 计数 -1，归零才摘边
    void removeRefs(Node from, Node to);            // 该项整体删除（断引用用）
    Set<Node> removeNode(Node node);                // 返回被一并清掉出边的后继
}
```

关键差别只有一句：**"计数"不再是图旁边的第二份状态，它就是边本身**。于是：

- `applyTrackFormula` 不再需要"计数副本 + commit 两段式"，试算变成对图的一次浅拷贝（或在自己的副本上走一遍）；
- `breakReferences` 变成"删一项"；
- §7.4 那条"断开引用之后要再校准一次计数"的步骤从**必须记得**降级为**不必存在**；
- `COUNT_MISMATCH` 这一类漂移在结构上不可能出现（§6）。

### 4.3 顺手还掉的两笔债

**(a) 切断 `eval → track` 的反向依赖。** `trackEdges` 的第一步是 `instanceof CurveTrack`，这是
`api.animation.eval` 反过来认识 `track.CurveTrack` 的唯一原因，也是测试必须造真实 `CurveTrack`、
连带 `Component` / `Identifier` 才能跑的原因（`EvaluationGraph2Test.java:935-936` 专门写了注释说明这件事）。
自定义结构只要收一个最小视图，就能把依赖翻过来：

```java
/// 图眼里的"可被引用的轨道"：只要键数与各槽位公式，不要具体轨道类型
public interface TrackRefSource {
    String id();
    int keyCount();
    @Nullable Keyframe key(int index);
}
```

`CurveTrack` 实现它（或在 eval 侧做一次适配），图不再 import `track.*` 的**具体类**，测试可以用桩件。
这个接口必须公开（`CurveTrack` 在另一个包），但它是**窄的只读视图、只发数据不发可变表示**，
与 §8 收窄的方向一致——它换掉的是"图认识轨道实现"，得到的是"图只认识一种可被引用的东西"。

**(b) 可变面由自己定。** §8 要把数据的写方法收窄，而 §9 又倾向让图挂在 `CameraAnimation` 上，
两条合起来意味着**图的类型会出现在 `api` 的公开面上**。`MutableGraph` 是控制器对象，接口很大
（`putEdge` / `removeNode` / `addNode` 全在），暴露出去等于把"绕过门面直接改图"的口子开在最难看的地方。
自己的结构则可以把只读面（`referrers` / `dependents` / `hasCycle` / `cycleInfo` / `allows*`）与
可变面（`addRef` / `removeRef` / `removeNode`）分开，可变面只留给门面。

### 4.4 代价与风险

- **规模**：与现在维护 `trackRefCounts` 的代码同量级，是小改，不是"重写一遍图"。
  `walker` / `dsfResolve` / `dependents` 这些**算法本来就是手写的、且不依赖容器语义**，
  改动集中在取邻居与改邻居的那几处（`graph.predecessors(...)` → `in.get(...)`、
  `putEdge` / `removeEdge` → `addRef` / `removeRef`）；`commitRefs` 那套"先清空出边再重连"可以整段退休。
- **多出来的自有不变量要配测试**：自环、平行边、计数减到 0 要摘边、遍历中改结构、节点删除后两张表都干净。
  `EvaluationGraph2Test` 现成的用例是安全网，P0 阶段还应把"计数与边永远同步"作为**结构自检**（调试断言）。
- **唯一的负收益方向是顺手把它做成增量图引擎**（脏标记、惰性判环、增量环检测）。那会把 §11.1 已经否掉的
  复杂度重新引进来。图很小（几十上百节点），整体重算足够。
- **时机**：v2 尚未接入编辑器（§5.3），这是它最便宜的时刻——换容器不用背任何调用点。

### 4.5 一个看起来相关、但明确不做的事

自定义结构会让"把未定义的名字也存下来"变得很容易。**不要顺手做。** 它会让新增节点具备"回头看"的能力，
于是新建节点可能凭空连出边、可能成环，要求 1（削去新节点的判环）当场失效——这正是 §6.5 与类文档"边界"
明确划出去的那一块，对账的盲区也来自同一处。结构能力变强不等于该用。

---

## 5. 手段二：sealed 结果类型（P0，成本最低）

### 5.1 形状

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
    record Applied() implements Edit { }                      // 将来要挂凭证就挂在这里，见 §11.3
    record Rejected(List<List<Node>> cycles) implements Edit { }
}

public Edit applyTrackFormula(String trackId, @Nullable String oldExpression, @Nullable String newExpression);
```

### 5.2 为什么比现在的 `boolean` 强

现在 `removeNode` 返回 `boolean`，被拒时引用者要**另外**调一次 `referrers(node)` 才知道——两步之间可以做错两次：
忘了第二步（提示不出来，只能给一句"删不掉"），或者拿了引用者却自己拼提示文案（与图的判断不一致）。
sealed 结果只有一种处理方式：`switch` 穷尽三支，`Blocked` 带着引用者一起来。

`apply*` 那边现在是 `CheckResult`：SUCCESS 不带任何信息，CYCLE 才带环，调用方得先看类型再决定读不读
`cycles()`；而 `ERROR` 这一支从来没有被生产过（`EvaluationGraph2` 只用 `SUCCESS` 与 `cycle(...)`），
是纯占位。sealed 之后只剩两支，`Rejected` 一定带环。

### 5.3 改动面与验收

- 图侧：`removeNode` / `removeNodeCascade` 两个删方法 + 三个 `apply*` 换返回类型；
  `breakReferences` 保持原样；`check*` 保持 `CheckResult` 不变（它是只读试算，没有"落不落地"这一说）。
- 编辑器侧：目前还没有调用点（v2 未接入），所以在接入时一并按新类型写。
- 验收：`switch` 穷尽；测试断言 `Blocked` 里的引用者与 `referrers(node)` 一致。

---

## 6. 手段三：对账 `reconcile`（P0，兜底与验收工具）

### 6.1 它是什么：拿两套独立算出来的答案对一遍

对账不是一套系统，是两套答案的 diff：

1. **本图**：编辑器一路增量维护出来的那份记账（各调各的 `addRef` / `apply*` / `removeNode`）。
2. **影子图**：`new EvaluationGraph2(variables, functions, tracks)`——**从数据现状整体重算**，
   谁也没碰过它，所以它必然是对的。

然后逐项比：节点集合、边集合（含重数）、环集合（规范化后按集合比）。空表 = 一致。

**它为什么必要**：增量图是"记账"，不是"事实"。账会被弄坏，而且弄坏的方式不止"忘了调"——
传错旧表达式、整份替换后图还停在旧数据上，都会让它错；而图一旦错了，`hasCycle()` 就会给出错答案，
**错的方向还是最坏的那个：漏判**。对账是唯一能发现账坏了的手段。

### 6.2 形状

```java
/// 一处不一致
public record Drift(Kind kind, Node node, @Nullable Object expected, @Nullable Object actual) {
    public enum Kind {
        MISSING_NODE,   // 数据里有、图里没有（漏了 addNode）
        EXTRA_NODE,     // 图里有、数据里没有（漏了 removeNode）
        MISSING_EDGE,   // 该有的引用没连上（少了 apply*，或旧表达式传错）
        EXTRA_EDGE      // 图里多了一条引用（数据已改，图没跟着退）
    }
}

/// 拿数据现状与本图对账：按构造期同一套口径重算节点、边与环，逐项比较。空表 = 一致。
public List<Drift> reconcile(List<Variable> variables, List<CustomFunction> functions, List<AnimationTrack> tracks);
```

**与原设计的差别：`COUNT_MISMATCH` 这一类被删掉了。** 它治的是"边对但引用计数不对"——
那是图与计数表分离才有的病（§4.1）。自定义结构里计数就是边，这一类不一致不存在。

### 6.3 实现要点

- **影子图直接用构造器**：同一个口径只写一遍，对账不会自己走偏。
- **比集合不比顺序**：节点与边用 `Set` / `Map` 比；环信息按"每条环规范化之后排序"的集合比
  （测试里那套 `canonical` 逻辑可以抽成图自己的 `cycleKey()`），`cycleInfo` 的顺序不该参与判断。
- **边要比重数**：只比"有没有这条边"的话，"该两条公式引用、实际只记了一条"会潜伏到下一次
  `applyTrackFormula` 才爆，那时已经定位不到是哪次编辑弄坏的。

### 6.4 什么时候调，以及**不该**什么时候调

| 时机 | 目的 |
| --- | --- |
| 每次编辑提交之后（调试构建） | 漏调当场炸在出问题的那一步，而不是几十步之后 |
| 撤销 / 重做 / 读档之后 | 抓 I4（整份替换） |
| 测试每一步之后 | §10 的编辑脚本冒烟 |
| v2 接入编辑器的验收 | 一条命令给出"还有多少处写入没走图" |

**"保存 / 导出之前对一次账"这一行要划掉**（原设计有，现在撤回）：对账保证的是"**以后判环的判据是对的**"，
与被写出去的那份文件是否合法毫无关系——产物里没有图（§2.1）。把对账挂在保存上会让人误以为
"对账通过 ⇒ 文件没问题"，那是两件事。产物合法性由读档收尾那次判环负责（§9）。

**开关的真相源必须是静态布尔，配置项只是绑上去。** `EditorConfig` 是 NeoForge 的 `ModConfigSpec`
（`EditorConfig.java:78-87` 已有 `dev` 段可照抄），读它需要加载好的配置上下文；而 §10 的编辑脚本用例
跑在 test source set（`EvaluationGraph2Test` 已经不需要 Minecraft 上下文），拿不到 `ModConfig`。
若开关只有配置项一条路，CI 里想开对账就得把 NeoForge 配置也拉起来。正确形状：

```java
// api.animation.eval.EvaluationGraph2（或新的 eval 侧调试开关类）
private static volatile boolean reconcileEnabled = false;   // 真相源
public static void reconcileEnabled(boolean enabled) { ... }

// EditorConfig 的 dev 段：editor.reconcile = false，只负责把它绑到配置上
```

### 6.5 它管不到的三件事（要老实写进文档）

- **未定义引用**：公式里写了个还不存在的名字，两边（本图与影子图）都看不见这条引用，所以对账也看不见——
  这正是"新建一个已被别的公式提到的名字"那条要在编辑器侧拦的原因。
- **编译不过的公式**：`Expression.names` 对编译失败的公式返回 `Names.EMPTY`，零引用，两边同样看不见。
  界面靠 `Formula.broken()` 标红。
- **根本没调对账**：如果编辑器某条路连数据都改了却从不触发对账，它就不会说话。所以对账是**兜底**，
  不是替代品——真正把门关上的是 §7 与 §8。

**图到底负责哪些"播不对"的问题**，一张表说清（这张表同时是给编辑器作者的边界声明）：

| 会造成静默降级（求值退回 fallback）的成因 | 谁负责 | 图看得见吗 |
| --- | --- | --- |
| 环（含自引用、跨轨道闭合） | 四条不变量 + `apply*`（§2、§5） | **是，图唯一能拦的** |
| 删掉仍被引用的节点 | 要求 1（拒 / 断开 / 连带删，§5.1） | 是 |
| 名字未定义 | 编辑器侧（§7.4） | **不** |
| 公式编译不过 | `Formula.broken()` / 表达式编辑窗口 | **不** |
| 名字冲突（形参撞内置量、函数形参重名） | `SymbolTable.shadowing()` + 读档统一扫一次 | 不 |
| 变量名与函数名同名 | 图分三类命名空间，本就不算冲突 | 不适用 |
| 键的时间 / 值本身有问题 | 谁都不是（求值层接受） | 不，图里没有时间和值 |

求值侧的口径是"**公式非法、变量取不到值、轨道不存在一律返回 NaN，`evaluateOrFallback` 是唯一的回退点**"
（`ValueSource` 类文档）。所以这些毛病**都不会让动画播不出来，只会让它在某几帧悄悄用旧数值**——
这才是加固的靶子，而图只覆盖其中第一、二行。

---

## 7. 手段四：唯一写入口 `AnimationEdits`（P1）

### 7.1 为什么需要

写入点有二十来处（§3.1），每一处都要同时做对三件事：调对图的方法、传对参数、保持"先图后数据"的顺序。
让每个面板各自负责这三件事，就是"全看作者水平"的根源。

**先图后数据是安全方向**：图超前于数据（图里还留着一条数据里已经没了的边）只会**误报**环；数据超前于图
（数据里已经挂上公式、图还不知道）就会**漏判**。所以门面的骨架固定为：

```text
① 图侧 apply/remove（内部先判环/先查引用）→ 被拒就到此为止，数据不动
② 数据落地（setter 不抛异常，这一步不会失败）
③ 需要时（批量路径）整图重建
```

### 7.2 方法清单（对着 §3.1 一条一条给出口）

```java
public final class AnimationEdits {
    // 变量（新建没有"被拒"这一支，名字冲突由数据层回 null，故直接返回建出来的对象）
    @Nullable Variable addVariable();
    Edit removeVariable(String name, RemovalMode mode);    // REFUSE / BREAK / CASCADE
    Edit renameVariable(String from, String to);           // 见 §7.4：改完重建，成环就改回去
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
    /// 只给新来源：旧表达式由门面自己从数据侧读（见 §7.3），调用方没有传错的机会
    Edit setKeySource(String trackId, int keyIndex, KeyField field, NumberSource source);
    Edit addKey(String trackId, float time);
    Edit removeKey(String trackId, int keyIndex);
    Edit pasteKey(String trackId, float time, Map<KeyField, NumberSource> sources, EvaluateMode mode);

    // 整份替换（撤销/重做、读档、切换动画）
    Edit replaceAll(CameraAnimationc source);
}
```

`RemovalMode { REFUSE, BREAK, CASCADE }` 就是把要求 1 的三条出口（拒绝 / 断开引用 / 沿引用链全删）
摆成调用方必须选的参数——不选就编译不过。

### 7.3 一条接口修正：旧表达式不要由调用方交

原设计里 `setKeySource` 的注释是 `applyTrackFormula(旧→新)`，也就是让调用方同时交出旧表达式。
这是整套设计里最易错的参数——**旧表达式传错就记错引用**，而图一旦记错，之后所有判环都建立在这个错上，
且错的方向是漏判（§2 的 I2）。而旧表达式**完全可以从数据侧读出来**：

```java
NumberSource old = track.key(keyIndex).source(field);   // 门面自己读，调用方只给新的
```

所以 `AnimationEdits` 的签名收成"只给新来源"，`applyTrackFormula(trackId, old, new)` 保持
"收旧新两条表达式"的低层口径不变（它是给门面用的），但**门面之上没有这个参数**。
§10 里"旧表达式传错"那条破坏用例仍然保留——它考的是图自己的记账，不是门面的接口。

### 7.4 三条容易写错的入口（增量模型下只有整图重建才照得出来）

1. **增**：新建变量 / 函数 / 轨道之后**不要只 `addNode`，顺手整图重建**。理由有两层：
   新节点没有出边，永远闭合不了环（要求 1 的前提成立）；而重建会按新节点集合重新解析一遍所有公式，
   把"这个名字其实早就被别的公式写到了"这类引用一次照出来——增量 `addNode` 看不见它们
   （公式里未定义的名字在两边都不成边，见 §6.5）。
2. **改名**：一律"先改数据、再整图重建"，**重建后发现成环就把名字改回去并返回 `Rejected`**。
   改名和新建一样会让**本来未定义的引用突然成立**（公式里写着 `b`、`b` 还不存在，此时把某个变量改名成 `b`），
   这是增量 `renameNode` 那种"搬节点身份 + 处理入边"的做法照不到的；加上轨道改名改指向、
   变量 / 函数改名让旧公式落空，两类语义本就不同（见 §8 末段）。
3. **粘贴**：一次 `pasteKey` 会同时带进五个槽位的来源，所以图侧要**逐槽位**退旧进新
   （不能只按"一条公式"处理），并且整条路径要走同一个事务边界（§10 的用例专门覆盖它）。

### 7.5 与 `EditorSession` 的关系

`api.editor.EditorSession` 已经是这类门面的**现成先例**：外置界面（附属 mod 的界面）拿到的是它，
能做的只有"选中 / 加键 / 移键 / 删键 / 记录路径点"这类受控动作（`EditorSession.java:89-121`），
读模型只给只读契约 `CameraAnimationc` / `Pathc`。也就是说"唯一写入口"在这套代码里不是新概念，只是**目前
开出去的写动作不含变量 / 函数 / 轨道来源**。

**已定（不允许绕过）：`AnimationEdits` 属于 `api.editor`，是外置与内置共用的唯一写入口。** 两条后果：

1. `EditorSession` 必须补齐写动作——变量增删改名 / 换来源 / 绑轨道、函数增删改名 / 改形参与函数体、
   轨道增删改名、关键帧槽位来源、加键 / 删键 / 移键、粘贴、整份替换（对照 §3.1 的写入点清单）。
   现在它只开到"关键帧与路径点"。
2. 内置界面也必须走这份契约。它今天走的是 core 的 `EditorContext`（宽），默认实现因此**不遵守自己对外发的契约**；
   把默认实现注册成一个后端（优先级最低）就能把这条裂缝合上，做法见
   [`design-api-default-editor.md`](design-api-default-editor.md) §3.1。

**`AnimationEdits` 不缓存图。** 它只做"对动画对象方法的转发"，图实例取自动画（§9）。
理由：外置 mod 的会话与内置编辑器可能各自建一个 `AnimationEdits` 叠在同一份数据上，
若各自持一张图，对账会立刻报出一堆假 `Drift`——那正是 §3.3 拿来说"数据自查"不成立的那条理由，
搬到了会话上。

### 7.6 断开引用的口径（`RemovalMode.BREAK`）

`breakReferences` 只抹掉"指向这个节点的那一条引用"，同一条公式里引用到的别的名字还留在轨道上
（图侧文档把它记为残余，方向偏保守：只会多报环，不会漏报）。**自定义结构之后这条残余依然存在**——
它来自"断的是引用、不是公式"，与计数表的分离无关。所以门面要按槽位把话说完整：

```text
breakReferences(node) → 拿到引用者名单
  → 对每个引用者（轨道 / 变量 / 函数）改写它的来源
  → 数据落地；轨道按**槽位**重读旧公式、用旧公式退计数（§7.3 的读法）
```

也就是说"断引用"这个动作的语义是**改写引用者的来源**，而不是"从图里删一条边"；
图侧只需要把"谁引用了它"交回来。

---

## 8. 手段五：收窄数据类的写方法（P2，真正的"编译期关死"）

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
[`design-api-default-editor.md`](design-api-default-editor.md) §3.1 与 §7 第 2 条。

**影响面比上表看起来大**——收窄之后，下面这些类必须同包或改走门面（它们是 §3.1 的实际调用方）：

| 类 | 现在直接调什么 |
| --- | --- |
| `AnimationCodec` | `animation.addChannel` / `removeChannel` / `addExtensionTrack` / `symbols().addVariable` / `addFunction` / `variable.source` |
| `EditorContext` | `animation.motionMode(...)`（内部走 `addChannel` / `removeChannel`，同样改图的节点集合） |
| `CameraEditorModel` | 加键 / 删键 / 移键 / 拖动的数据落地 |
| `EditorHistory` | `animation.copyFrom(loaded)` |
| `CameraPlaybackClient` | `camera.animation().copyFrom(loaded)`（**这条不在编辑器里**，见 §9） |
| `VariablePanel` / `FunctionPanel` / `TimelinePanel` / `KeyframePanel` | 各类增删改名与槽位来源 |
| `ExpressionEditorWindow` | 自己的变量表增删（`:980,989`）+ 槽位来源 |
| `CameraEditorScreen` | `addChannel`（`:666`）、`copyFrom`（`:554`）、粘贴（`:630`） |

**判据（可以做成 CI 检查）**：收窄之后，`core/editor/**` 里搜不到 `.source(`、`symbols().add`、
`.removeVariable(`、`.removeFunction(`、`addChannel(` 这类写入；出现即视为绕过门面。
`grep` 一条命令就能守住，比靠 review 可靠。

**改名的口径**见 §7.4 第 2 条（先改数据、再整图重建，成环就改回去）：图分不清一条入边来自 `TrackRef`
绑定还是公式文本，所以不要试图增量搬运节点。

---

## 9. 手段六：批量替换处自动重建（P1 一起做）

### 9.1 图挂在哪里：已定挂在 `CameraAnimation` 上

**判据是"图该住在哪，才能让所有会整份替换数据的路径都在它作用域内"**，不是"它是唯一真身"（它不是，§2.1）。
三条整份替换的路：

| 入口 | 现在做什么 | 改成 |
| --- | --- | --- |
| `EditorHistory.restore` → `CameraAnimation.copyFrom` | 原地替换全部内容 | 替换完由 `copyFrom` 自己重建图 |
| `SymbolTable.replaceFrom`（切换动画 / 读档） | 整批换变量与函数 | 同上（`CameraAnimation.copyFrom` 里就调它，所以钩子仍在 `copyFrom`） |
| `AnimationCodec` 读档收尾 | 读完即算完成 | 读完按当前口径判一次环（见 §9.2） |

**为什么不能挂在编辑器 / 会话上**：`copyFrom` 有四个调用点，其中
`CameraPlaybackClient.java:50` 在 `core.cmd_camera.playback`——**出于播放目的，不在编辑器内**。
图若归编辑器持有，播放路径要重建图就得走 `AnimationEdits.replaceAll`，等于让
`core.cmd_camera.playback` 依赖 `api.editor`，是分层翻转。挂在动画上，`copyFrom` 一句话就把这条路径一起管住。

**挂在动画上不会让图进文件**：图不是序列化字段，`animationToJson` 不会碰它（§2.1）。
`CameraAnimationc` 也不暴露它——只读契约照旧只有数据。

### 9.2 读档收尾这一处可以更省

`AnimationCodec.java:270` 现在是 `animation.evaluationGraph().cycleText()`——**为查一次环而现建一张 v1 图**。
如果图是动画持有的实例，这次查环可以直接用那个实例（或直接问 `hasCycle()`），
"读完再建一张"这个钩子就不需要单独存在。省下的是每次读档 / 撤销的一次建图。

### 9.3 撤销重做里的图：`EditorHistory` 一行都不该有图的逻辑

现在已经如此（`EditorHistory` 只做 JSON 快照栈与 `copyFrom`），**这是优点，不要为了重建把它弄脏**。
它今天有两个真正粗糙的点，外加一处与"重建"叠在同一条路径上的浪费：

1. **节流漏捕**：`lastCaptureMillis` 只在成功压栈时更新（`EditorHistory.java:66`），节流命中时提前返回不推进
   （`:42-44`）。于是"距上次压栈不足 250ms"被反复重算，不是滑动窗口——一次操作落在窗口内会被**整个漏掉**，
   撤销栈里根本没有它。
2. **捕获点绑在帧循环上**：`CameraEditorScreen.extractRenderState` 每帧调一次 `capture()`（`:235`）。
   于是撤销粒度按时间切、不按用户意图切：一次拖拽跨越两个 250ms 窗口就产生两个撤销项，
   用户要按两次 Ctrl+Z 才回到拖拽前；而 64 个快照的上限在持续拖拽下约 16 秒就吃满。
3. **一次撤销建两张图**：`restore` 先 `animationFromJson`（读档收尾判一次环、建一张图），
   再 `copyFrom`（重建图）。前两条与图无关，这一条是 §9.2 的直接后果。

**建议按代价排的三步**（详见 [`design-api-default-editor.md`](design-api-default-editor.md) §6.3）：

1. `capture()` 从帧循环挪到**编辑动作边界**（各面板动作收尾，或 `AnimationEdits` 出口统一调一次）；
   拖拽这类连续操作用"开始捕获 / 结束合并"两句话解决。**这一步与图无关，是纯收益。**
2. 暂时不改结构的话，让节流命中时也刷新 `lastCaptureMillis`，先堵住漏捕。
3. **一次撤销不要建两张图**：`EditorHistory.restore` 先 `animationFromJson`（读档收尾会判一次环），
   再 `copyFrom`（重建图），是两次完整建图。让 `restore` 复用/继承同一张，或让读档路径跳过重复判环。

命令模式（每个用户动作一个撤销项 + 反向命令）是另一个数量级的工程；它解决的问题（粒度、内存）
在前两步里能解决大半，所以**先不排队**。

### 9.4 图的重建不进快照节流

原设计的待定项之一是"重建图的开销要不要计入 250ms 节流"。**现在定为：不计入，两者不相关。**

- 图的输入只有**节点名**（变量 / 函数 / 轨道 id）、`Variable.source`、函数体 + 形参、各槽位公式。
  **键的时间与值不在图里**，所以拖时间轴、拖键值时数据在变、图不变——那类操作本来就不需要重建。
- 重建的触发点是**数据整体替换**（`copyFrom` / `replaceFrom` / 读档），不是"要不要压快照"。
  把两者捆在一起会得到一个错的口径：撤销会被节流挡住压制重建，而 §9.1 恰恰要求替换当场重建。
- 真正的开销大头在别处：`refreshCycles()` 在**每次改图末尾整体重算一遍 DFS**，而调试构建下对账还要再建一张
  影子图——也就是"每次编辑两张图 + 两次 DFS"。这才是该记账的数字（§10 的 CI 用例会给出实测）。

---

## 10. 手段七：测试与 CI（P0 起）

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
| `applyTrackFormula` 旧表达式传错 | `EXTRA_EDGE`（重数比出来） |
| 同一槽位被两条公式引用、只记了一条 | `EXTRA_EDGE`（**重数**比出来，原设计归在 `COUNT_MISMATCH`） |
| `copyFrom` 之后不重建图 | 一整套 `Drift` |

当年那句"这七条用例在 CI 里全绿，才算'编辑器遵循了图的要求'"是这套设计里最硬的一条验收，
**比任何注释都硬**——而且现在它只需要一份开关（静态布尔），不需要 Minecraft 运行环境。

---

## 11. 比较过的其他路线

### 11.1 每次改完重建（rebuild-on-write）

"每次写完数据就整图重建"听起来最省心（一致性由构造保证，重数、旧 / 新表达式这些记账全都不需要）。
但它**替代不了增量**：要求 2 是"**改之前**先判环"，而重建发生在改之后——
要么把数据先拷一份、把改动应用在副本上再重建（等于引入快照），要么退回 `check*` 的近似口径
（`checkTrackFormula` 判的是"整组换成这一条"，与"只加这一个槽位"在**无环图**上判据一致，但终究是近似）。

结论：**增量用于"改"（`apply*` 就是"判环 + 落地"这一对），整份重建用于批量路径（§9）**。
两条各管一段，而不是二选一。注意这条结论与 §2.1"图是缓存"并不冲突：缓存可以随时重建，
**但写入判据那一处必须新鲜**——这就是 P1 / P2 把关门面的意义。

### 11.2 数据自查

见 §3.3：setter 不知道该问哪张图，签名也放不下环路径这类判据，走不通。

### 11.3 凭证 token

```java
public sealed interface Permit permits Permit.Write, Permit.Remove { }   // 构造器私有，只有图能铸造
variable.source(source, permit.Write);        // 没有凭证编译不过
symbols.removeVariable(name, permit.Remove);  // 被引用时图不发 Remove 凭证
```

能把"没问过图就写不进去"变成编译错误，但要求凭证**单次有效、绑定参数**（否则存一个下来到处用），
再往下就是编辑事务系统。而 P1+P2（门面 + 收窄可见性）已经把"绕过"堵死了，
凭证只是把同一件事换个更重的形式——所以列在这里备查，不推荐先做。

### 11.4 图当纯派生视图（v1 现在的形态）

v1 的 `EvaluationGraph.of(animation)` 每次现建，**天然满足 I1–I4**，一行纪律都不需要。代价是三个入口
（删前查引用、改前试算、报环）每次都要重建一遍图；几十上百节点其实也能接受。
v2 走增量的理由是"编辑器要反复问"（拖拽时每帧查引用、试算要连问几次），
所以真正的问题不是"增量对不对"，而是"增量的纪律用什么补"——本文的答案就是 P0+P1+P2。

**§2.1 之后这条要补一句**：v1 与 v2 并存这件事本身要收敛。v2 挂上动画之后，
`VariablePanel` / `ExpressionEditorWindow` / `AnimationCodec` 三处读环的入口应当统一走 v2，
v1 只保留给"只读契约侧要一个环文本"的场景（或一并退休，见 §13）。

---

## 12. 落地顺序与验收

| 阶段 | 改动面 | 验收判据 |
| --- | --- | --- |
| P0-a 自定义图结构 | 新增内部 `RefGraph`；`EvaluationGraph2` 换容器；抽 `TrackRefSource` 去掉 `instanceof CurveTrack` | 原 `EvaluationGraph2Test` 全绿；新增"计数与边永远同步"的结构自检用例；`eval` 不再 import `track.*` 具体类 |
| P0-b sealed 结果 | 两个删方法（`removeNode` / `removeNodeCascade`）+ 三个 `apply*` 换返回类型；接入时编辑器按 `switch` 写 | `switch` 穷尽；测试断言 `Blocked` 的引用者与 `referrers` 一致 |
| P0-c 对账 | 新增 `reconcile` / `Drift` + `cycleKey()` 规范化；静态布尔开关 + `EditorConfig` 绑定 | §10 的七条破坏用例全部报出对应 `Drift` |
| P1 门面 | 新增 `AnimationEdits`（§7.2）；面板的写入点逐个改走它 | 对账在编辑脚本每一步都为空；`RemovalMode` 三支各有用例 |
| P2 收窄 | §8 的清单与调用方；只读契约不变 | 原来的绕过写法**编译不过**；`core/editor/**` 的 grep 判据通过；外置界面改走 `EditorSession` / `AnimationEdits` 仍能完成原有动作（补一个"用会话走一遍编辑脚本"的用例） |
| P3 批量与 CI | 图挂到 `CameraAnimation`（§9.1）；读档收尾复用同一张图（§9.2）；撤销捕获点改到动作边界（§9.3）；编辑脚本进 CI | 撤销、重做、读档之后 `reconcile` 为空；七条破坏用例在 CI 里全绿 |

一个可执行的验收动作（跑在 CI 或本地）：`.\gradlew test` + 编辑脚本冒烟用例；两者全绿即视为达标。

---

## 13. 已定与待定

**已定**：

1. **不允许绕过**：改数据只有 `AnimationEdits` 一条路，外置与内置共用（§7.5）。
2. **门面放 `api.editor`**：与 `EditorSession` 同一层，外置 mod 能拿到；数据类的写方法全面收窄（§8）。
3. **图的容器自己写**（§4）：重数是一等公民，顺带切断 `eval → track` 的反向依赖、把可变面收进手里。
   v2 尚未接入编辑器，这是换容器最便宜的时刻。
4. **图的归属：挂在 `CameraAnimation` 上**（§9.1）。判据是"所有整份替换路径都在它作用域内"——
   `CameraPlaybackClient` 那次 `copyFrom` 不在编辑器里，图归编辑器会让播放路径依赖 `api.editor`。
5. **图是运行期的判据缓存，不是权威状态**（§2.1）：产物与图无关，所以"图会不会脱节"不是产物风险，
   "判据与产物说的不是同一件事"才是。`EditorHistory` 里不放图的逻辑。
6. **对账的开关与时机**（§6.4）：静态布尔作真相源、`EditorConfig` 只负责绑；时机是"编辑提交后（调试构建）
   / 撤销重做读档后 / 测试每一步"，**不含"保存 / 导出之前"**。
7. **重建不进快照节流**（§9.4）：两者不相关，重建跟着"数据整体替换"走。
8. **旧表达式不由调用方交**（§7.3）：门面从数据侧读，消掉最易错的参数。

**待定**：

1. **v1 图（`EvaluationGraph`）的归宿**：v2 挂上动画并统一三处读环入口之后，v1 是保留成
   "只读契约侧的环文本"薄封装，还是直接退休（`EvaluationGraphc` 之类只读接口是否要留）？
   这一条不影响 P0–P3 的排期，但影响 §8 收窄时哪些读方法必须保持公开。
2. **`EvaluationGraph2` 是否改名**：自定义结构落地后这个名字只剩历史含义（v2 是相对 v1 说的）。
   改名会动 `EvaluationGraph2Test` 与两份设计文档的引用，等 P0-a 落地后再定更省事。
