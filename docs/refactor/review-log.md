# 复核结论记录（`_v2` 契约）

> 本文记录对 `_v2` 契约的**复核结论**，不是设计文档，也不替代
> [`P1-contract-plan.md`](P1-contract-plan.md) 的施工约定。
>
> - 每条结论一个编号（`R1`、`R2`…），**新复核追加在末尾**，不重写既有正文；速查表、当前待办和范围说明随复核更新。
> - **当前执行依据为 R4–R6 与 §3 待办**。R1–R3 保留历史论证；其中绝对性能结论、键类型“不可合并”和标记时长的缺陷判定已被后续修订，不得直接据旧段落修改代码。
> - 结论被推翻时不删旧条目，追加「修订」说明并指向新编号。
> - 每条结论标注**核查位置**与**核查日期**；源码变更后以重新核查为准，
>   不能仅凭旧条目认定现状。
> - 结论分三类：**契约判定**（某类型的职责 / 不变量）、**性能判定**（某写法是否值得改）与
>   **一致性判定**（设计与实现 / 契约之间的口径冲突）。
>   都只陈述「当前源码是什么」＋「为什么」，不承诺已修改；冲突类结论只记录问题，
>   是否改动设计由裁决决定（见 §3 的「待裁决」项）。

---

## 0. 速查

| 编号 | 主题 | 结论 | 是否已改代码 |
| --- | --- | --- | --- |
| [R1](#r1-fieldkeyframe-与-blockvaluekeyframe-的分工) | 两种键类型分工 | 保留具体类型；“不可合并”与有限性保证的原结论由 R4 修订 | 类型边界已修；时间边界测试已补（R7） |
| [R2](#r2-_v2-中-stream-的渲染期性能) | Stream、查询与缓存 | 当前未接线；历史微基准不能证明未来帧耗时或稳定收益，见 R5 | 未优化 |
| [R3](#r3-marker-是否应参与-duration) | 标记与播放时长 | 实现符合当前设计；删除贡献是产品取舍，非既定缺陷，见 R6 | 保留现状 |
| [R4](#r4-修订键类型分工与时间边界回归) | 修订 R1、NaN 回归 | 最新构造器已恢复有限性检查；边界测试已补（R7） | 实现与测试均已修 |
| [R5](#r5-修订性能证据缓存与无变化派生) | 修订 R2、A1–A5、withRig | 优先按快照绑定索引；性能清理分期；无变化派生须避免新根 | A1/A4 已落地（R7）；A2/A3 待后续批次 |
| [R6](#r6-修订标记时长的判定与裁决边界) | 修订 R3、A6 | 默认保留标记时长贡献；注释措辞和覆盖测试可先补 | 注释与远端标记测试已补（R7）；产品语义未改 |
| [R7](#r7-实施记录a0a1a4a5a7-落地与验证) | 实施记录：A0/A1/A4/A5/A7 | 代码与回归测试落地；A2/A3/A6 见正文 | 是（构建与测试通过） |

---

## R1. `field.Keyframe` 与 `block.ValueKeyframe` 的分工

> 历史结论，类型不可合并与有限性已经完整保证的判断已由 R4 修订；不得将本节“无需修改”视作当前验收结论。

**核查日期**：2026-10-17
**核查位置**：[`field/Keyframe.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/Keyframe.java)（95 行）、
[`block/ValueKeyframe.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/ValueKeyframe.java)（77 行）、
[`field/Curve.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/Curve.java)、
[`block/KeyframeTrack.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/KeyframeTrack.java)、
[`field/ValueSource.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/ValueSource.java)

### R1.1 结论

两者是**同一层级的两套键类型**，按「值是否需要插值」分工：

| | `field.Keyframe` | `block.ValueKeyframe<T>` |
| --- | --- | --- |
| 职责 | **连续数值**曲线的键，帧间需要**插值** | **离散状态**轨道的键，整体切换，**不插值** |
| 值形态 | `NumberSource`（`Constant` / `Formula`） | 原生 Java 值 `T`（`AimState` / `Boolean` / `CameraSource`） |
| 附加字段 | 四曲柄 `inSlope` / `outSlope` / `inLength` / `outLength` + `interpolation` | 无曲柄、无插值模式 |
| 插值 | `STEP` / `LINEAR` / `HERMITE` | 仅 `STEP`（由轨道保证，键自身不表达） |
| 宿主容器 | `field.Curve`，挂在 `block.CurveTrack` 上 | `List<ValueKeyframe<T>>`，挂在 sealed `KeyframeTrack<T>` 上 |
| 典型用途 | 相机通道、通用曲线、可供变量读取的数值 | 注视、机位活动、玩家相机来源 |

**判断口径（写入契约评审用）**：这个值需要在两帧之间被算出来吗？
需要 → `field.Keyframe`；只是「从此刻起换成它」→ `ValueKeyframe<T>`。

### R1.2 为什么不能合并（[`ValueKeyframe` 类文档 §10-22](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/ValueKeyframe.java#L10-L22) 已给出三条）

1. **编码成浮点会丢类型**：把「注视哪个目标」「相机来自哪个机位」压进一个 `float`，只能靠魔法数字或哈希区分，`Off` 与「未编写」也会混在一起；
2. **曲柄与 LINEAR / HERMITE 会污染语义**：状态不可插值，让这些参数出现在只允许 STEP 的轨道上等于承诺了不存在的能力；
3. **防止误读**：状态若也走 `NumberSource`，作者会以为注视目标能被公式算出、或能被 `TrackRef` 读到。`TrackLookup` 只返回 `CurveTrack`，`TrackRef` 指向状态轨道必须被静态校验拒绝。

`field.Keyframe.value` 的静态类型是 `NumberSource` 而非 `ValueSource`，正是为了让 `TrackRef` **在类型上**进不来
（[`ValueSource.java:26`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/ValueSource.java#L26) 的 `permits NumberSource, TrackRef`）。

### R1.3 共用的不变量

两者刻意共享同一套时间轴基础设施，复核时不应把它们当成两套独立体系：

- **`KeyId` 共用**：移动、排序、改值、撤销都保留；复制键时由 `IdGenerator` 生成新值；
- **时间口径一致**：秒存储、不隐式量化、有限非负、`-0.0` 归一为 `0.0`；
- **同轨时间唯一且严格递增**，由容器构造时校验并防御性复制：数值侧在
  [`Curve` 构造器](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/Curve.java#L64-L83)，
  状态侧在 [`KeyframeSequences.canonical`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/KeyframeSequences.java#L19)；
- **均为不可变 `record`**，改字段返回派生对象（`withTime` / `withValue` / `withHandles` / `withId`），无原地方法。

### R1.4 命名坑：三组名字必须分清

这是本次复核发现的**主要误读风险**。`Keyframe` 与 `Keyframec` 只差一个 `c`，但语义与 R1 的对照完全不同：

| 名字 | 所在包 | 角色 | 可变性 |
| --- | --- | --- | --- |
| `Keyframe` + `Keyframec` | v1 `api.animation` | **可变实现 vs 只读视图**（`Keyframec` 是 `sealed permits Keyframe`） | 可变 |
| `field.Keyframe` | v2 `...field` | 数值曲线键（有公式 / 曲柄 / 插值） | 不可变 |
| `block.ValueKeyframe<T>` | v2 `...block` | 有类型状态轨道键（仅 STEP） | 不可变 |

补充两点，避免把 v1 的行为套到 v2：

- v1 `Keyframe` 是**可变类**，`Curve` 直接持有实例、就地修改；v2 是**不可变 record**，`with*` 返回新对象。
- v1 `Keyframec` **不发放 `NumberSource`**，挂公式的槽位只能给回退值；真算出来的数要走 `eval.CurveSampler`。
  v2 没有这个只读视图分层。

### R1.5 判定

契约形状**成立**，无需修改。唯一建议：在 `package-info` 或命名评审清单里显式写出 R1.4 的三组名字对照，
`Keyframe` / `Keyframec` 的 `c` 后缀太容易在评审时看漏。

---

## R2. `_v2` 中 Stream 的渲染期性能

> 历史测量与推论，后续执行以 R5 和 §3 为准。本节的稳定倍数、未来渲染保证及“明确不做”不再作为当前结论。

**核查日期**：2026-10-17
**核查 JDK**：Java 25（与 [`build.gradle:63`](../../build.gradle#L63) 的 `JavaLanguageVersion.of(25)` 一致）
**核查位置**：[`CameraAnimation.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java)（329 行，`_v2` 内**唯一**使用 Stream 的文件）

### R2.1 前提更正：`_v2` 当前不在渲染路径上

复核的第一步是纠正问题里的假设：

- `src/main` 内**除 `_v2` 自身外没有任何文件引用 `_v2`**（`grep free_camera_api_tripod_v2` 在
  `free_camera_api_tripod` 树内零命中；引用只出现在 `src/test/..._v2` 的契约测试里）；
- `_v2` 的 `api/animation/eval` 只有 [`TrackLookup`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/TrackLookup.java) 与 `package-info`，**采样器尚未实现**；
- 当前每帧真正驱动相机的是 v1 的 `CmdCamera` + `EvaluationGraph`。

因此**现在这些 Stream 对帧时间的影响是 0**。以下分析是「v2 取代 v1 后会跑在每帧」的**前瞻判定**，
不能据此宣称当前有性能问题。

### R2.2 Stream 出现的位置：3 处，全是派生列表查询

`_v2` 总计只有 3 个 Stream 管道，都在 `CameraAnimation`，且都不是采样热路径：

| 方法 | 形状 | 行号 |
| --- | --- | --- |
| `curveTracks()` | `rigs.stream().flatMap(rig -> rig.channels().stream()).toList()` | [209-211](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L209-L211) |
| `enabledRigs()` | `rigs.stream().filter(rig -> rig.view().enabled()).toList()` | [217-219](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L217-L219) |
| `withRig(rig)` | `rigs.stream().map(existing -> ...).toList()` | [321-326](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L321-L326) |

**真正的每帧路径是零分配、零 Stream 的**：`cameraSourceAt(t)` → `CameraSourceTrack.at(t)` →
[`KeyframeTrack.keyAt`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/KeyframeTrack.java#L82-L102)
的二分查找。这一点决定了 R2 的整体判定：Stream 没有进热点。

### R2.3 实测（非 JMH，只看数量级）

**方法**：按上述三种管道的**精确形状**复刻微基准（`record Rig(int id, boolean enabled, List<Integer> channels)`，
每机位 8 条通道），`-XX:+UseSerialGC -Xms512m -Xmx1g`，200 万次预热 + 2000 万次测量，
结果经 `volatile` 汇总量逃逸以防 DCE。

耗时（ns/op）：

| 操作 | 1 机位 | 4 机位 | 8 机位 | 16 机位 |
| --- | --- | --- | --- | --- |
| `enabledRigs()` stream | 36.0 | 76.8 | **113.6** | 182.1 |
| `enabledRigs()` 手写 loop | 6.6 | 11.8 | **18.8** | 33.5 |
| `curveTracks()` stream | 76.8 | 155.4 | **263.4** | 463.6 |
| `curveTracks()` 手写 loop | 48.5 | 184.2 | **350.1** | 717.5 |
| `withRig()` stream | 42.1 | 64.2 | **91.5** | 146.8 |
| `withRig()` 手写 loop | 19.9 | 33.2 | **51.0** | 85.6 |
| `duration()`（内部调 `enabledRigs()`） | 40.4 | 83.5 | **119.8** | 193.0 |
| `duration()` 内联循环 | 3.2 | 6.3 | **9.7** | 15.8 |
| `keyAt(t)` 二分查找（热路径） | 5.9 | 5.9 | **5.8** | 5.9 |

分配量（`ThreadMXBean.getThreadAllocatedBytes`，B/call）：

| 操作 | 1 机位 | 8 机位 |
| --- | --- | --- |
| `enabledRigs()` stream | 304 | 328 |
| `enabledRigs()` loop | 29 | 72 |
| `curveTracks()` stream | 424 | **1616** |

### R2.4 判定：不影响渲染性能

**帧预算占比**：60 fps 一帧 16,670,000 ns。8 机位调一次 `enabledRigs()` 是 113 ns，占 **0.0007%**。
即便宿主每帧调 100 次，也只有 11 µs、0.07%。

**GC 压力**：最重的 `curveTracks()` 在 8 机位是 1616 B/次；每帧一次、240 fps，约 388 KB/s。
Minecraft 客户端本身的分配速率是 MB/s 量级，这点属于噪声。

两个**反直觉**的发现，会影响后续「优化」的方向：

1. **`curveTracks()` 的 Stream 反而比手写 loop 快**（8 机位 263 ns vs 350 ns）。
   因为 `flatMap + toList()` 一趟就建成不可变列表，而手写版本是 `ArrayList.addAll` 建一遍 + `List.copyOf` 再复制一遍，两趟。
   → **「把 Stream 改成 loop」不是无条件优化，`curveTracks()` 改了是负优化。**
2. **`toList()` 的结果再过 `List.copyOf` 会再复制一次。** 实测 `Stream.toList()` 返回
   `ImmutableCollections$ListN`，但 `List.copyOf` 对它**不走零拷贝快路径**（返回不同实例），
   而 `List.copyOf(List.of(...))` 返回同一实例。原因是 `toList()` 允许 null 而 `List.of()` 不允许，JDK 区别对待
   （`Stream.of("x", null).toList()` 成功；`List.copyOf` 该结果抛 NPE）。
   → `withRig()` 里 `toList()` 的结果进构造器又被 `List.copyOf` 复制一遍，实际是**双份分配**。
   不过这是编辑操作，不在每帧。

### R2.5 真正该改的两处

**（1）`duration()` 为了迭代一次而分配整个列表** —— 最值得改

```java
// CameraAnimation.java:233 现状
for (CameraRig rig : enabledRigs()) {          // 先建一个过滤后的列表
    duration = Math.max(duration, rig.contentDuration());
}
```

实测慢 **12~13 倍**（8 机位 120 ns vs 10 ns），且多一次 328 B 分配。它不像 `curveTracks()` 属于
「调用方主动要一个列表」的 API，而是**纯粹为了遍历**，应把 `view().enabled()` 判断内联进循环。

之所以比另外两处更值得改：`duration()` 在 v1 里**已经是每帧被面板轮询**的——
[`AnimationPanel.java:67`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod/core/editor/panel/AnimationPanel.java#L67) 每帧拼 revision 字符串带它、
[`TimelinePanel.java:259`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod/core/editor/panel/TimelinePanel.java#L259) 一帧调三次、
[`CameraPlayer.java:175`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod/core/cmd_camera/playback/CameraPlayer.java#L175) 播放时每帧判定结尾。
迁移到 v2 后该路径会原样继承。

**（2）`track(TrackId)` 是线性扫描** —— 潜在热点，与 Stream 无关

```java
// CameraAnimation.java:167-195
for (CameraRig rig : rigs) {                   // O(机位 × 通道)
    ...
}
```

一旦 `TrackRef` 在每帧求值里按 ID 解析轨道，复杂度就是 `O(机位数 × 通道数)`，还要分配 `Optional`。
正确的落点不在文档类，而在 `TrackLookup` 的**实现**：它已绑定「某一份快照」、文档版本变化时重建
（[`TrackLookup.java:27-30`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/TrackLookup.java#L27-L30)），
因此可以在那里一次性建 `HashMap<TrackId, CurveTrack>` 索引，之后 O(1) 零分配。

### R2.6 比微优化更大的收益：用不可变性做缓存

`CameraAnimation` 是不可变 `record`，`with*` 只复制变化路径
（[`CameraRig.java:50-54`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraRig.java#L50-L54)
明确「允许共享未变化的不可变对象」，不深拷贝）。这给了**完美的缓存键**：

```java
// 宿主侧
private CameraAnimation cachedFor;
private List<CameraRig> cachedEnabled;

List<CameraRig> enabled() {
    if (cachedFor != animation) {              // 身份比较，一次指针判断
        cachedFor = animation;
        cachedEnabled = animation.enabledRigs();
    }
    return cachedEnabled;
}
```

文档一换实例就自动失效，不需要版本号、不需要失效信号、不可能读到脏数据。
**这比「Stream 改 loop」收益大一个数量级**，并顺手消掉 `curveTracks()` 那个 1616 B 的分配。

### R2.7 什么情况下才会真的出问题

不是现在，是这几种写法：

- 在**每机位循环内部**调 `enabledRigs()` / `curveTracks()` → `O(N²)` 还带 N 倍分配；
  `curveTracks()` 在 8 机位是 1616 B/次，嵌进循环就变成每帧几十 KB；
- **每帧调 `with*` 重建文档**。这才是危险区：`with*` 虽不深拷贝元素，但每次都会新建一份 record 与若干封装；
- 每帧重新 `List.copyOf` 一个已经不可变的集合。

设计侧已有相关约束——[`design-animation-editor-framework.md:511`](../design-animation-editor-framework.md#L511)
写「计算接口支持目标参数写入，复用缓冲区，避免增加每帧临时分配」。但那条针对**姿态 / 旋转 / 避障数学**，
不要把它当成对 `enabledRigs()` 这类查询方法的同等要求。

### R2.8 复现方法

微基准未入库（一次性核查，非长期资产）。复现时按三种形状各写 stream / loop 两版，
用 `volatile` 汇总量逃逸。**注意**：非 JMH 的单一轮测量不能当绝对值，**只看比值与量级**：

```java
static List<Rig> enabledStream(List<Rig> rigs) { return rigs.stream().filter(Rig::enabled).toList(); }
static List<Rig> enabledLoop(List<Rig> rigs)   { /* ArrayList + add */ }
static List<Integer> curvesStream(List<Rig> rigs) {
    return rigs.stream().flatMap(rig -> rig.channels().stream()).toList();
}
```

分配量用 `com.sun.management.ThreadMXBean#getThreadAllocatedBytes` 前后差值除以迭代数，
需要先跑 50 万次预热让 JIT 把管道内联掉，否则测到的是解释执行的开销。

---

## R3. `Marker` 是否应参与 `duration()`

> 历史产品建议，证据及判定已由 R6 修订。不得据本节直接删除标记时长贡献，也不将 v1 消费者当作 v2 已接线证据。

**核查日期**：2026-10-17
**状态**：**仅记录问题，未改代码**
**核查位置**：[`Marker.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/Marker.java)、
[`MarkerKind.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/MarkerKind.java)、
[`CameraAnimation.java:245-247`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L245-L247)、
[`ExpressionScope.java:118-121`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod/api/animation/eval/ExpressionScope.java#L118-L121)、
[`CameraPlayer.java:175`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod/core/cmd_camera/playback/CameraPlayer.java#L175)、
[`design-animation-editor-framework.md:170`](../design-animation-editor-framework.md#L170)

### R3.1 问题

`duration()` 把每个标记的时刻算进可播放时长，而契约同时声明标记「不产生任何运行期行为」。
结果是**一条注释能间接改变画面**。这是设计与契约之间的口径冲突。

### R3.2 标记的用途里没有「内容」

`MarkerKind` 列的四种用途（[MarkerKind.java:7-17](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/MarkerKind.java#L7-L17)）：

| 用途 | 性质 | 是否内容 |
| --- | --- | --- |
| 音频卡点 | 波形候选，**作者确认后**才提交为标记 | 否（参照点） |
| 吸附源 | 时间轴吸附候选，可逐源开关 | 否（编辑辅助） |
| 注释 | 作者写给自己的说明，**不产生任何运行期行为** | 否 |
| 事件参考 | 对齐参照，**本身不触发 Tick 事件** | 否 |

`Marker` 类文档进一步写明「标记本身**不进入播放执行路径**：它不产生 Tick 事件、不执行指令、不发声」
（[Marker.java:13-16](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/Marker.java#L13-L16)）。

### R3.3 现行为是设计明文，不是实现疏漏

- **代码**：`duration()` 里的 marker 循环（[CameraAnimation.java:245-247](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L245-L247)）；
- **设计**：[design-animation-editor-framework.md:170](../design-animation-editor-framework.md#L170)
  「播放时长取……以及相机来源、文档轨道、**标记**和叠加范围的最大有限结束时间」。

因此这不是修 bug，改它等于改设计口径。

### R3.4 矛盾链条

`duration()` 不是纯展示数字，它有两个真实消费者：

1. **表达式内置 `progress`** = `time / duration`
   （[`progressOf`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod/api/animation/eval/ExpressionScope.java#L118-L121)）
   → 影响所有读 `progress` 的公式 → 影响画面；
2. **播放终点与循环判定**（[`CameraPlayer.play`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod/core/cmd_camera/playback/CameraPlayer.java#L175)）
   → 影响成片长度。

**具体例子**：10 秒内容 + 一条作者标在 300 秒的 `GENERIC` 注释（如「TODO 这里要加爆炸」）：

- `duration()` 变成 300 秒 → 10~300 秒停在最后一帧（`postMode = CLAMP`），**冻屏 290 秒**；
- `progress` 在 t=5 秒从 `0.5` 变成 `0.017`，缓动 / 透明度 / 位移公式全部改变。

### R3.5 支持移除的论证

1. **与契约自述矛盾**：「不产生任何运行期行为」被 `duration()` 打破。
2. **设计已用同一理由排除静态布局**：`duration()` 文档写「静态布局不延长动画……
   **否则动一下画中画位置就会改变动画长度**」
   （[CameraAnimation.java:228-229](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L228-L229)）。
   同一句话换成标记同样成立：否则写一条注释就会改变动画长度。
3. **卡点标记的时长贡献冗余**：音频尾部已由 `audioRefs` 的 `endTime` 单独计入
   （[CameraAnimation.java:249-251](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L249-L251)），
   标记再计一次没有增量作用。
4. **旁证：`markers` 是唯一不按时间排序的集合**（`@param markers 文档级标记，顺序即显示顺序`），
   而 `Curve` 与 `KeyframeTrack` 都强制严格递增唯一。各处已把它当自由注释列表，只有 `duration()` 把它当权威内容。

### R3.6 反向考虑（为什么当初会算进去）

设计里 `duration()` 是**派生值、不是存储字段**（明确的不变量），文档没有「显式时长」一栏。
于是任何带时间的东西都容易被当成「作者想做到这里」的证据，`SECTION` 标记尤其像。
但这是把**编辑意图**误当成**内容范围**——内容范围应由能出画面的东西决定
（通道 / 状态轨道、音轨范围、启用的叠加）。

补充：v1 **没有标记概念**（v1 `CameraAnimation.duration()` 只遍历 `tracks`，`src/main` 内无 `Marker` 类型），
所以这是 v2 新引入的口径，**没有历史兼容负担**。

### R3.7 候选方案（待裁决，未实施）

**方案 A（建议）**：标记不参与 `duration()`，另设 `viewExtent()` 供时间轴使用。

```java
/// 可播放时长：只取能出画面的内容；标记不参与。
public float duration() { /* rigs / cameraSource / tracks / audioRefs / overlays */ }

/// 时间轴可滚动范围：标记越界仍须可达。视图 / 会话数据，不进入求值与播放。
public float viewExtent() {
    float extent = duration();
    for (Marker marker : markers) {
        extent = Math.max(extent, marker.time());
    }
    return extent;
}
```

不能只删循环：若时间轴可视范围恒为 `[0, duration]`，越界标记会**够不着、删不掉**，
所以视图范围必须独立派生。这也符合既有的「创作数据 vs 会话数据」划分
（[review-feature-parity.md:522](../review-feature-parity.md#L522)：标记是文档数据，循环区间是会话数据）。

**方案 B**：保留现状，但收紧为「仅 `SECTION` 可作为时长锚点」，同时把 `MarkerKind` 里
「不产生任何运行期行为」改成实话（标记会影响时长）。缺点：等于承认「有些注释能改画面」。

**明确不选**：给 `Marker` 加 `affectsDuration` 布尔——把设计问题转成作者负担。

### R3.8 若裁决为改，三项前置

1. **不得夹取**：`Marker` 只校验有限、非负
   （[Marker.java:37-39](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/Marker.java#L37-L39)），
   不得加 `time <= duration` 上界；越界标记是合法常态，L3 校验也不得拒绝。
2. **补测试**：目前**没有任何测试**覆盖「标记延长 duration」——`OverlayElementContractTest`
   只测叠加元素。该行为从未被执行过一遍。至少补 `markerPastAllContentDoesNotExtendDuration`
   与 `viewExtentIncludesMarkers`。
3. **同步设计文档**：改 [design-animation-editor-framework.md:170](../design-animation-editor-framework.md#L170)
   那一句，否则下次评审会按旧文本改回来。

### R3.9 待裁决

| 选项 | 内容 |
| --- | --- |
| **A** | 移除标记的时长贡献 + 新增 `viewExtent()`（建议） |
| **B** | 保留，但只让 `SECTION` 计入，并修正 `MarkerKind` 措辞 |

裁决前不改代码，也不改设计文档。

---

## 3. 累计待办（复核提出，尚未落代码）

本表是后续代码修改的当前清单，取代 R1–R3 中旧的排期与绝对收益判断。实现问题、验证缺口、优化建议与设计裁决分别标注；不得把“写入待办”记为“代码已修”。施工顺序见 [P1 计划 §6.4](<P1-contract-plan.md#p1-follow-up>)。

| # | 事项 | 落点与验收 | 优先级 / 状态 |
| --- | --- | --- | --- |
| A0 | 固定关键帧时间有限性回归 | `Keyframe` 构造器已恢复 `!Float.isFinite`；补构造/withTime 的 NaN、±Infinity、负数、±0 与曲线冲突测试，见 R4 | **高：已完成（R7）— 边界测试实际执行通过** |
| A1 | `duration()` 内部遍历不物化启用列表 | 遍历 rigs 并内联静态 enabled 判断；时长与原规则等价，含状态/来源/标记/扩展/音轨/叠加 | 低至中：已完成（R7）— 等价口径测试通过，不承诺倍数 |
| A2 | 绑定层缓存静态派生列表及必要时的 duration | 按文档引用缓存 enabledRigs/curveTracks，重复调用有证据时启用；不缓存为 active(t) 或求值结果，换档/候选不串用 | 条件实施：eval / 宿主批次 |
| A3 | 轨道索引与所属机位上下文一起绑定快照 | 校验唯一 ID，索引 TrackId 到曲线及所有者；版本变化一致重绑；不得覆盖重复 ID，不宣称 Optional 零分配 | **中：eval 绑定批次（R7 未提前发明索引 API）** |
| A4 | 先修 withRig 无变化身份，再评估额外复制 | 未找到和替换内容相等时返回原实例；真实替换保序且原文档不变；编辑目标不存在由提交层诊断 | 低：已完成（R7） |
| A5 | 写清两种键与 v1 只读视图的命名区别 | 数值来源/插值能力与原生状态/STEP 分工；不禁止公共接口、组合和基础设施复用 | 低：已完成（R7）— `package-info` 命名对照 |
| A6 | 标记是否退出 duration | **默认保持当前设计**；若另行裁决分离播放/编辑范围，先同步全部时长契约和定位/预览循环，再改代码 | **待裁决：非缺陷修复** |
| A7 | 标记注释与时长测试对齐现设计 | 注释写“无 Tick/指令/发声副作用，仍按文档规则贡献时长”；先测试远端标记延长当前 duration，不预写反向期望 | 低：已完成（R7）— 注释与远端标记测试通过 |

不因使用 Stream 统一改写查询，不把历史替身微基准当验收门槛，也不永久禁止手写循环。性能调整保持语义，必要时记录实际调用频率、分配与多轮测量结果。

---

## 4. 复核范围与未覆盖项

- 本次复核契约层的类型边界、时间约束、查询与文档口径；未实现的求值器、Codec、统一校验器、UI 和多机位宿主不作为已完成能力验收。
- R2 表格保留历史隔离微基准，非客户端剖析；缺完整基准源码、重复轮次与误差，绝对值、比值和因果解释均不能作为稳定结论。
- 上一轮验证执行了 `compileJava test`：11 个 suite / 92 个测试通过，其中 v2 为 5 个 suite / 26 个测试。该结果不覆盖 NaN 时间回归；R4 的最新代码修正仅经源码复查，本次文档同步未重新运行测试。
- 本次只修改文档，不修改源码或新增测试。后续完成待办须记录新测试结果与重新核查日期，不能沿用旧的通过数证明修复完成。
- R7 为实施记录：自该条起已修改源码并新增测试；§4 上述更早描述仅指上一轮复核范围，最新验证结果见 R7。

---

## R4. 修订：键类型分工与时间边界回归

**核查日期**：2026-10-17（23:48 后文档同步）  
**类型**：契约判定修订；替代 R1 的“不可合并”“无需修改”和有限性已经完整保证的判断。

**核查位置**：[数值键](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/Keyframe.java#L55>)、[状态键](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/ValueKeyframe.java#L48>)、[数值键测试](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/KeyframeContractTest.java>)。

1. 维持两种具体键类型合理，但不是所有抽象都“不可合并”。数值键也允许 STEP，应按“NumberSource 与数值插值能力”及“原生状态值与仅 STEP”区分；可共享 ID/time 接口、组合或泛型基础结构，不强制合并到带曲柄的数值 record。
2. 上轮实证的实现使用 `Float.isInfinite(time) || time < 0`，NaN 可通过构造与 withTime；曲线排序/`==` 查重无法拒绝 NaN，两个 NaN 键也可进入，末键时间和跨度为 NaN，并可传播到文档 duration。旧测试全绿不能证明边界正确。
3. **最新源码已恢复 `!Float.isFinite(time) || time < 0`**。本次不再将实现列为未修，但当前 KeyframeContractTest 仍只测取值类型、常量/公式及 null value，缺时间边界回归；A0 验收尚未完成。
4. 后续补构造与 withTime 的 NaN、正负无穷、负数拒绝；正负零均规范为正零，同轨 +0/-0 冲突拒绝；普通乱序键构造后严格递增、原列表不被修改。覆盖数值键和状态键共用不变量，保留合法负数**查询**与非法负数**键时间**的层次区别。

---

## R5. 修订：性能证据、缓存与无变化派生

**核查日期**：2026-10-17  
**类型**：性能判定与一致性判定修订；替代 R2 的未来帧性能保证及绝对排期。

**核查位置**：[文档查询和派生](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java>)、[TrackLookup](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/TrackLookup.java>)、[主设计缓存规则](<../design-animation-editor-framework.md#derived-query-rules>)。

- 当前 v2 未接线，不能以 v1 调用频率断言迁移后原样成为热点；cameraSourceAt/at/keyAt 只是局部查询，不代表完整每帧路径。R2 的帧预算算术不是实际调用频率或性能证明。
- 历史基准用简化 Rig 及单次隔离测量，未留完整源码与统计误差；不能用来保证 duration 全方法 12–13 倍收益、所有 loop 均慢或 flatMap 必然一次构建。Stream.toList 与 List.copyOf 的实例共享/复制是具体 JDK 实现细节，不等于整个操作恰好分配两次。
- A1 是合理的低风险内部遍历清理。完整 duration 还扫描通道、状态与文档范围，高频使用时应考虑在绑定层复用派生时长；不得改变标记等现有时长语义。
- A3 的查找索引适合在 eval 绑定时建立。线性查询成本是机位、通道、状态与文档轨道总量，HashMap 是期望 O(1)；接口仍返回 Optional，不能据此保证零分配。索引须保留所属 Rig，TrackRef 在被引用轨道的空间上下文求值，重复 ID 明确诊断。
- A2 只缓存依赖文档结构的列表和时长；不能仅凭根对象身份缓存来源/活动采样、空间事实或公式结果。开放扩展和暂存不透明叠加内容仍须遵守不可变契约，不能称 record 自动证明全部子对象深不可变。缓存有界并随会话关闭释放。
- [withRig](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L319>) 注释承诺未找到返回自身，实现却无条件新建根。A4 先对齐该承诺，并对内容相等替换返回自身，再评估中间列表复制。未找到的编辑目标由提交层拒绝并给诊断，不能误认为文档校验能从未变文档中找回丢失的操作意图。

---

## R6. 修订：标记时长的判定与裁决边界

**核查日期**：2026-10-17  
**类型**：一致性判定修订；替代 R3 的“必须移除”论证，保留方案作为产品讨论。

**核查位置**：[主设计数据与时长](<../design-animation-editor-framework.md#L170>)、[当前 duration](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L245>)、[MarkerKind](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/MarkerKind.java#L13>)。

1. 标记参与 duration 符合当前设计；“不产生 Tick 事件、不发指令、不发声”不推出“不参与时长推导”。应先将 MarkerKind 的绝对注释收紧为无直接执行副作用，并说明时长按文档规则派生。
2. R3 引用的 progressOf 和 CameraPlayer 是 v1 消费者，不消费 v2。CameraPlayer.play 的该行是开始播放时回绕检查，不是每帧终点判断。远端标记使 v2 duration 延长可以由代码确定，但公式进度变化是未来采用 time/duration 定义时的条件后果；末键公式、世界目标可继续变化，不能称必然冻屏 290 秒。
3. 标记独立于音频，删除/重绑音轨不带走标记，因此时长贡献不必然冗余；列表按显示顺序、不按时间排序也不能证明不应参与时长。带 time 的标记与静态矩形不是同一种范围证据。“没有测试覆盖”不等于“从未被执行”。
4. **未另行裁决前保持现有全部标记参与时长的规则**，A7 先补当前行为测试和注释。A6 可选分离播放时长与编辑范围，或讨论 SECTION 策略，但均非默认执行项。
5. 若决定分离，须同步主设计及 AnimationTrack、CameraRig、KeyframeTrack、Curve 等重复时长口径，定义越界标记的定位、列表导航、时间轴可达范围及预览 out <= duration 的处理，再补新规则测试。viewExtent 若仅由文档派生可作为纯查询，缩放/滚动/留白才是会话状态；未实现 UI 不应被描述为已出现“标记删不掉”。

---

## R7. 实施记录：A0/A1/A4/A5/A7 落地与验证

**记录日期**：2026-10-18  
**类型**：实施记录（非新复核）；结论沿用 R4–R6，未执行项见 §3 表。  
**核查位置**：
[`field/Keyframe.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/Keyframe.java)、
[`block/ValueKeyframe.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/ValueKeyframe.java)、
[`block/CameraAnimation.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java)、
[`block/Marker.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/Marker.java)、
[`block/MarkerKind.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/MarkerKind.java)、
[`block/package-info.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/package-info.java)、
[`field/package-info.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/package-info.java)；
测试：[`field/KeyframeContractTest`](../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/KeyframeContractTest.java)、
[`field/CurveContractTest`](../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/CurveContractTest.java)、
[`block/StateTrackContractTest`](../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/StateTrackContractTest.java)、
[`block/CameraAnimationReplacementTest`](../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimationReplacementTest.java)、
[`block/CameraAnimationDurationTest`](../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimationDurationTest.java)。

### R7.1 A0：关键帧时间边界回归

- 确认 `Keyframe` 与 `ValueKeyframe` 的构造器与 `withTime` 均走 `!Float.isFinite(time) || time < 0`，不是只检查 `isInfinite`。
- 新增/补充测试：构造与 `withTime` 拒绝 `NaN`、`±Infinity`、负时间；`-0.0` 归一为 `+0.0`（按位断言）；数值曲线与状态轨道都拒绝 `+0` / `-0` 归一后的同轨重复时间；非法乱序输入在构造后被整理为严格递增，且原输入列表不被修改；状态轨道保留“负数查询合法、负数键时间非法”的层次。

### R7.2 A1：`duration()` 内部遍历

- [`CameraAnimation.duration()`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java) 改为直接遍历 `rigs` 并内联 `view().enabled()`，不再为内部遍历物化 `enabledRigs()` 列表；未统一改写 `curveTracks()` / `enabledRigs()` 等公共查询的 Stream 写法（R5 口径）。
- 时长规则保持：启用机位的姿态 / 注视 / 活动、相机来源、文档轨道、标记、音轨、启用叠加取有限最大结束时间；禁用机位与禁用叠加不贡献时长；标记仍按当前设计贡献时长。
- [`CameraAnimationDurationTest`](../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimationDurationTest.java) 覆盖上述全部贡献项、禁用机位不参与及远端标记延长。

### R7.3 A4：`withRig` 无变化派生

- [`CameraAnimation.withRig`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java) 未找到 ID 或内容相等时返回 `this`；真实替换才复制列表并按原位置替换，原文档不被修改。Javadoc 明确“编辑目标不存在由提交层诊断”，不把未变化候选当作目标存在证明。
- [`CameraAnimationReplacementTest`](../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimationReplacementTest.java) 用 `assertSame` 验证未找到 / 同值；真实替换保序、共享未变化机位、原文档不变。

### R7.4 A5：命名对照

- [`block/package-info.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/package-info.java) 新增“两种键类型的命名对照”：`field.Keyframe`（数值键，`NumberSource` + 曲柄/插值）与 `block.ValueKeyframe<T>`（原生状态值、仅 STEP），并区分 v1 `Keyframe` / `Keyframec`（可变实现 / 只读视图，不属于新契约）。
- [`field/package-info.java`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/package-info.java) 同步命名提示。

### R7.5 A7：标记注释与时长测试

- [`Marker`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/Marker.java) 与 [`MarkerKind`](../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/MarkerKind.java) 的注释改为“无 Tick / 指令 / 发声等直接执行副作用，但仍按当前设计规则参与 `duration()` 推导；是否分离播放与编辑范围另行裁决”，删除“不产生任何运行期行为”的绝对表述。
- 测试固定“远端标记（300 秒）延长当前时长”，未预写反向期望；事件执行器副作用仍留待执行器批次验证（A7 完成条件的一半）。

### R7.6 本轮未执行

- **A2**：静态派生列表 / 时长缓存仍为条件实施，等 eval / 宿主批次有重复查询证据再按主设计 §3.4 建缓存；不在文档对象里做缓存。
- **A3**：`TrackId` 索引需要在 eval 绑定时派生并携带所属 Rig；当前 `EvaluationContext` 等契约未建立，提前发明索引 API 会超出本轮契约范围，留到 eval 批次。
- **A6**：标记仍默认参与时长，未改产品语义。

### R7.7 验证结果

- 命令：`.\gradlew.bat --offline compileJava test --console=plain`
- 结果：`BUILD SUCCESSFUL`；**14 个测试 suite / 105 个用例全绿**，其中 `_v2` 为 **8 个 suite / 39 个用例**（上一轮基线为 11 / 92，v2 5 / 26）。
- 门禁：`_v2` 旧根引用 0、`net.minecraft.client` 引用 0、旧根已跟踪文件改动 0。
- 其他：`git diff --check` 通过；本轮未修改 R1–R6 正文，仅更新 §0 / §3 状态并追加本节。
