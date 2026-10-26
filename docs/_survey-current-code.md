# 现状代码勘察报告（不可变文档重构前）

> 历史资料导航（2026-10-26 同步）：本报告仅描述重构前旧根包，以下“当前”、文件数、行号及阻塞项均指勘察当时，不适用于 v2 当前完成度。`4a08774` 加 C22 已有不可变模型及文档/编辑/求值/播放准备/存储恢复 core 实现，真实宿主接线和完整验收仍未完成。目标行为见[主设计](<design-animation-editor-framework.md>)、[UI 设计](<design-editor-ui-framework.md>)与[音频设计](<design-spatial-audio-api.md>)；当前批次与 R68/R69 审核边界见 [P1 契约、实施与验收](<refactor/P1-contract-plan.md>)和 [P1 审核记录](<refactor/P1-review.md>)。保留下文原始证据供迁移追溯，不将旧实现缺陷自动归到 v2。

范围：`src/main/java/cn/anecansaitin/free_camera_api_tripod/`（137 个 Java 文件，约 1.15 MB）。
本报告只做事实陈述：字段、可变性、身份、调用链、阻塞项。所有行号取自当前工作区文件。
路径一律相对仓库根，例如 `api/animation/CameraAnimation.java:88` 实际位于
`src/main/java/cn/anecansaitin/free_camera_api_tripod/api/animation/CameraAnimation.java`。

勘察时刻说明：本报告描述的是**勘察时（重构开工前）已提交的存量代码**。勘察期间工作区出现了并行新增的
未跟踪目录（`docs/refactor/`、`api/animation/id/`、`api/animation/field/`），它们不属于本报告范围；
本报告未修改任何源文件，`git status` 中对已跟踪文件无改动。

参考：设计文档已存在（`docs/design-animation-editor-framework.md`、`docs/design-editor-ui-framework.md`、
`docs/design-spatial-audio-api.md`、`docs/review-feature-parity.md`）。其中 `design-animation-editor-framework.md:755`
已明确要求「`EvaluationGraph` 与未接入的 `EvaluationGraph2` 统一为文档派生依赖图」——与第 4 节实测一致
（`EvaluationGraph2` 在本仓库内零调用点）。

---

## 1. 数据模型清单

### 1.1 顶层容器：`CameraAnimation`（`api/animation/CameraAnimation.java`）

| 字段 | 行 | 类型 | 可变性 |
| --- | --- | --- | --- |
| `name` | 58 | `String` | 有 `name(String)`（491） |
| `path` | 59 | `Path` | 有 `path(Path)`（315），整对象替换 |
| `motionMode` | 60 | `MotionMode`（PATH / COORDINATE） | `motionMode(MotionMode)`（329）会**增删通道**；`restoreMotionMode`（415）只改标记 |
| `distanceMode` | 61 | `DistanceMode`（ABSOLUTE / PERCENT） | `distanceMode(...)`（431）会**就地换算所有键值**；`restoreDistanceMode`（425）只改标记 |
| `tracks` | 62 | `LinkedHashMap<String, AnimationTrack>`（`final`，内容可变） | 顺序 = 时间轴显示顺序 = 序列化顺序 |
| `symbols` | 65 | `SymbolTable`（`final`，内容可变） | 变量 + 自定义函数 + 名字唯一性 + 轨道引用完整性 |

构造（71-85）默认建 7 条通道（`position.x/y/z`、`rotation.x/y/z`、`fov`），**不建任何初始关键帧**；
默认 `MotionMode.COORDINATE`、`DistanceMode.ABSOLUTE`、`new Path("Path")`（73）。

可变入口（全部在同一个类上，无命令/事务对象）：
`addChannel`(88)、`addCurveTrack`(113)、`renameCurveTrack`(130，重建整张 `LinkedHashMap` 并
`symbols.rebindTrack`(156))、`removeChannel`(162，附带 `symbols.unbindTrack`(169))、
`addExtensionTrack`(210)、`removeExtensionTrack`(220)、`renameExtensionTrack`(235，调用
`renamable.rename(newId)`(251) **就地修改轨道对象**) 、`symbols()`(278，返回**可写**实例)、
`moveTrack`(354)、`moveTracks`(384)、`path(Path)`(315)、`motionMode`(329)、`distanceMode`(431)、
`restoreMotionMode`(415)、`restoreDistanceMode`(425)、`copyFrom`(469)、`name(String)`(491)。

只读视图 `CameraAnimationc`（`api/animation/CameraAnimationc.java`）**泄漏可变对象**：
`tracks()`(34) 与 `extensionTracks()`(40) 返回 `List<? extends AnimationTrack>`（元素可变）；
`curveTracks()`(37) 返回 `List<CurveTrack>`（`CurveTrack.curve()` 给出可变 `Curve`，`CurveTrack.java:106`）；
**`curve(String)`(46) 直接声明返回可变类 `Curve`**，而非 `Curvec`——这是只读契约上最大的一个口子；
`symbols()`(49) 返回 `SymbolTablec`，但其 `variables()` / `functions()`（`SymbolTablec.java:19,25`）返回的
`Variable` / `CustomFunction` 都是可变对象（`SymbolTable.java:47,57` 只是 `List.copyOf`，浅拷贝）。

`duration()`(300) = 所有轨道 `duration()` 的最大值，每次现算（无缓存）。

### 1.2 `Curve`（`api/animation/curve/Curve.java`）与 `Keyframe`

`Curve` 字段（14-20）：

| 字段 | 行 | 说明 |
| --- | --- | --- |
| `keys` | 15 | `ArrayList<Keyframe>`，**升序不变量靠 `TIME_COMPARATOR` 维持**（46） |
| `preMode` / `postMode` | 16-17 | **public 可变字段**，`WrapMode`；`AnimationCodec` 直接赋值（`io/AnimationCodec.java:428-429`） |
| `lastIndex` / `positive` | 19-20 | **求值期缓存写在数据对象里**（`findFloorIndex` 375），使 `Curve` 在"文档"之外还带可变求值状态 |
| `searchingCache` | 378 | `private final Keyframe`，二分查找用的**共享可变探针对象**（`binarySearch` 381 改写它的 `time`） |

求值：`evaluate(float time, KeyValues values)`(54) 只做纯数值插值，公式取值交给 `KeyValues`。
插值实现：线性(96)、STEP、三次贝塞尔（`evaluateBezier` 109 + 牛顿反解 `solveParameter` 403 + `bezier` 438）。
数值守卫：空曲线返回 0(57-59)；单键返回该键值(61-63)；段时长 ≤ 0 取左值(77-79)；切线无穷视为 STEP(81-84)；
柄长上限 `MAX_HANDLE_LENGTH = 1.5f`(386) + `handleLength` 夹取(390)。

可变入口：`key(float,float)`(121)、`key(Keyframe)`(129，二分命中时 `keys.get(index).set(key)`(139)
**就地覆盖已有键并丢弃传入对象**)、`moveKey(int,float)`(161，先 `remove(index)` 再按时间重插，
**同样会合并覆盖，对象身份不保留**)、`removeKey(int)`(178)、`smoothTangents(float)`(187) /
`smoothTangents(int,float)`(193)（就地改 `inSlope`/`outSlope`，240-241）。
静态工厂：`constant`(443)、`easeInOut`(451，**实现与 `constant` 相同，没有缓动**)、`linear`(459)。

`Keyframe`（`api/animation/Keyframe.java`）：**唯一 `final` 且 `sealed permits` 的具体键类**，
`implements Keyframec, JsonSerializable`。

| 字段 | 行 | 说明 |
| --- | --- | --- |
| `time` | 56 | `float`，有 `time(float)`(150) |
| `evaluateMode` | 57 | `LINEAR`/`STEP`/`HERMITE`，有 setter(155) |
| `sources` | 59 | `final EnumMap<KeyField, NumberSource>` —— **槽位 → 数值来源**；缺省 `Constant(0)`（`ZERO` 50） |

读写入口：`constant(KeyField)`(130)、`hasFormula()`(136)、`constant(KeyField,float)`(162，**换来源即清掉公式**)、
`value/inSlope/outSlope/inLength/outLength`(171-189，全部转调 `constant`)、`set(Keyframec)`(193 覆盖式拷贝)、
`source(KeyField)`(211)、`source(KeyField, NumberSource)`(215)、`formula(KeyField,String)`(221)、
`sources()`(231，**不可变快照**，注释明确「快照即拷贝」)。
序列化内聚在数据类自己身上：`write()`(259)、`read(JsonObject)`(296)、`jsonName(KeyField)`(285)、
槽位字段顺序刻意与旧格式一致（KEY 文档 251-257）。

身份：**`Keyframe` 上没有 id**。`Keyframec`（`api/animation/Keyframec.java:17`）是 `sealed permits Keyframe`，
只暴露 5 个数值 + `evaluateMode` + `hasFormula` + `constant(KeyField)`，**不发放 `NumberSource`**（10-12 注释）。

### 1.3 `Path` / `PathNode`

`Path`（`api/animation/path/Path.java`）：字段 `name`(14)、`nodes: ArrayList<PathNode>`(15)、
`segmentLengths: FloatArrayList`(16)、`cumulativeLengths: DoubleArrayList`(17)、`totalLength: double`(18)、
`lastIndex`/`positive`(20-21，同 `Curve`，求值缓存写在对象里)。

可变入口：`node(PathNode)`(83 追加)、`node(int, PathNode)`(104 替换)、`updateNode(int, NodeUpdater)`(109，
**把活的 `PathNode` 交给回调**)、`insertNode(int, PathNode)`(119)、`removeNode(int)`(131)、`moveNode(int,int)`(162，
顺序变化后整表重建 170)、`name(String)`(363)、`clear()`(368)。
弧长表：`updateArcLengthTable()`(174) / `updateArcLengthTable(begin,end)`(192，增量) / `calculateLength`(221)；
`evaluate(distance,dest)`(32) 按 `pathMode` 分派 LINEAR / BEZIER(`SplineUtils.bezier`) / CATMULL_ROM，
0 长度段退化为段起点(54-56)；空路径抛 `IllegalStateException`(36)。
`Pathc`（`path/Pathc.java`）是真正的只读视图：`node(int)` 返回 `PathNodec`(15) —— 与曲线那边不同，
路径这边**没有**泄漏可写类。

`PathNode`（`api/animation/path/PathNode.java`）：`final Vector3f position/inTangent/outTangent`(15-17)、
`pathMode`(18)、`smooth`(20)。因为向量本身可变，`position()` 返回的 `Vector3fc` 就是**活对象**（38-40）。
写入口：`position(x,y,z)`(42)、`inTangent`(52) / `outTangent`(67)（`smooth` 开启时互相镜像 55-57、70-72）、
`pathMode`(82)、`smooth(boolean)`(92，置真时把出切线设为入切线反向)、`restoreSmooth`(103，反序列化专用)。

### 1.4 轨道：`AnimationTrack` / `CurveTrack` / `TickTrack` / `CommandTrack`

`AnimationTrack`（`api/animation/track/AnimationTrack.java`）接口全部是**按索引的可变操作**：
`type()`(20)、`id()`(23)、`label()`(26)、`color()`(29)、`duration()`(32)、`keyCount()`(34)、`key(int)`(37)、
`addKey(float)`(42)、`addKey(float, Scope)`(49 默认转前者)、`removeKey(int)`(53)、`moveKey(int,float)`(56)。

- `CurveTrack`（`track/CurveTrack.java`）：`final property/label/color` + `final Curve`（15-18），
  结构上近不可变，但 `curve()`(106) 交出可变 `Curve`，`key(int)`(59) 直接返回活 `Keyframe`。
  `addKey(float,float)`(80) 沿用前键插值模式(86-90)；`addKey(float,Scope)`(73) 走
  `CurveSampler.sampleOnce`(74)。
- `TickTrack`（`track/TickTrack.java:15`）：`advance(float fromTime, float toTime)`，区间 `(from, to]`，语义是"经过即触发"。
- `CommandTrack`（`core/animation/track/CommandTrack.java`）：`implements AnimationTrack, TickTrack, JsonTrack, RenamableTrack`。
  `Key` 是不可变 record（50）；`id`(54) 与 `keys: ArrayList<Key>`(56) 可变。
  `addKey(float,String)`(65 同时间覆盖)、`command(int)`(122)、`command(int,String)`(127)、
  `rename(String)`(187)、`advance`(214 按时间顺序执行 `(from,to]` 内非空指令)、
  `execute(String)`(227 以 `LocalPlayer.connection.sendCommand` 发出；无玩家时静默跳过 228-232)。
- `TrackType`（record，13）/ `TrackTypeRegistry`（静态可变 `LinkedHashMap`，19、33）/ `AnimationChannelRegistry`
  （静态可变 `HashMap`，24-25，47-57）：**通道的显示名/颜色/默认值来自注册表而非文档**；
  未注册通道回退到 `名字 + 名字哈希派生色`（72-88）。
- `RenamableTrack`(11) / `JsonTrack`(13，`writeKeys`/`readKeys`)：按能力拆分的可选接口。

### 1.5 同一对象被多个持有者共享的位置

- `CmdCamera`（`core/cmd_camera/CmdCamera.java:30-34`）持有**唯一** `CameraAnimation`，并把同一个实例交给
  `CameraPlayer`(31)、`CameraEditorModel`(32)、`CameraInfo`(33)；`EditorContext` 又在构造时接住它
  （`core/editor/EditorContext.java:46-52`）。这是"只能原地改、不能换对象"的根源。
- `CameraAnimation.copyFrom`（469-484）：`this.path = other.path`(473) 让两份动画**共享同一个 `Path` 实例**；
  轨道表按引用搬运（478-480），因此 `other` 的 `AnimationTrack` / `Curve` / `CommandTrack` 实例转入本对象；
  只有符号表是深拷贝（`symbols.replaceFrom`(483) → `SymbolTable.copy`）。
- `renameCurveTrack`(130) 新建 `CurveTrack` 外壳但**复用同一个 `Curve`**（143）。
- `EditorContext.frameScope`(51) 与 `CameraPlayer.scope`(34) 各自长期持有 `ExpressionScope`，
  `ExpressionScope` 按引用持有 `SymbolTablec`（`eval/ExpressionScope.java:87-91`）与 `CurveLookup`（62）。
- `EditorHistory`（`core/editor/EditorHistory.java`）只存 JSON 字符串（23），但恢复时用 `copyFrom` 原地换内容（98），
  于是所有被面板/控件捕获的对象引用（见第 5 节）在撤销后指向游离对象。
- `CurveSample`（`eval/CurveSample.java`）按 `(Curve 身份, size, Scope 身份, version)` 复用缓存（66-84），
  缓存值来自活键对象（139）。
- `CameraEditorModel`（`core/cmd_camera/edit/CameraEditorModel.java:43,56`）持有 `animation` 并向所有面板暴露
  `animation()` / `path()`(60)。

---

## 2. 身份与查找

### 2.1 轨道

- 身份 = **`id` 字符串**，同时也是 `LinkedHashMap<String, AnimationTrack>` 的键（`CameraAnimation.java:62`），
  也是序列化写出的 `id`（`io/AnimationCodec.java:214,309`），也是 `TrackRef.trackId`（`expression/TrackRef.java:24`）。
- 相机通道的 id 是**语义常量**且不可改名：`CHANNEL_*`（33-40）+ `CAMERA_CHANNELS`(102-103) + `isCameraChannel`(106)。
- 查找入口：`curve(String)`(175)、`track(String)`(180)、`trackById(String)`(185)；`tracks()`(191) 复制成 `List`。
- 改名 = 换 key（`renameCurveTrack` 143-153；`renameExtensionTrack` 244-252），曲线轨道**新建外壳对象**，
  扩展轨道**就地 `renamable.rename(newId)`**(251)。改名会同步 `symbols.rebindTrack`(156)，但 `CameraEditorModel.selectedTrackId`
  需要调用方自己跟着换（`TimelinePanel.java:1248`）。
- **可变引用当身份**：面板普遍缓存 `AnimationTrack` 对象引用并按 `track.id().equals(...)` 反查
  （`TimelinePanel.java:637,1287`、`CameraEditorModel.selectedTrack()` 149、`multiSelection` 里的 `KeyRef.track()`）。

### 2.2 关键帧

- **没有稳定 id**。当前三种等价身份混用：
  1. **下标**：`CameraEditorModel.selectedKeyIndex`(49)、`Selected`(index,type)、`KeyRef(track,index)`(264)、
     `CurveSample` 的 `index * FIELDS + field.ordinal()` 缓存槽（`CurveSample.java:126`）、`AbstractTrack.key(int)`；
  2. **对象引用**：`Curve.key(int)` 返回活 `Keyframe`(149)；`KeyframePanel` 把 `Keyframe` 捕获进控件回调
     （`KeyframePanel.java:114,137-138,284,292-298`）；`GraphPanel.dragKey`(67) + `curve.key(dragKey)`(647)；
  3. **时间值**：`Curve.key(Keyframe)` 用 `binarySearch(TIME_COMPARATOR)` 按 `time` 找插入/覆盖位（134），
     `CommandTrack.indexOf(time)`(138) 按时间线性查重。
- 下标不稳：`Curve.key(Keyframe)` 二分插入会整体平移（143-145）；`removeKey`(178) / `moveKey`(161) 同样平移；
  `moveKey` 在撞时间时还会**销毁被移动的键对象**（139 的 `set`）。
- 时间也不稳：`Keyframe.time(float)`(150) 就地改时间且不重排，`KeyframePanel.java:137-138` 的曲线键时间输入框
  **直接调 `key.time(...)`**（不走 `moveKey`），升序不变量与二分查找因此可能被破坏；同一面板对非曲线键
  才走 `context.editor().moveKey(...)`（186，注释见 180-181）。

### 2.3 路径节点

- 身份 = **下标**：`Selected(index, type)`（`core/cmd_camera/edit/Selected.java:3`，`type ∈ {NODE, IN, OUT}`）、
  `CameraEditorModel.selectedPathNode`(50)、`Path.node(int)`(96)、`nodeDistance(int)`(341)、`updateNode(int,...)`(109)、
  `PathHandleDrag.index`(50)、`PathNodeListPanel` 行下标(105-111)、`CameraCommand` 的所有 `index<int>` 参数
  （`core/cmd_camera/CameraCommand.java:39-60`）。`PathNode` 上**没有 id**。
- 顺序即身份：`moveNode`(162) / `PathNodeListPanel.moveSelectedNode`(157) 改变下标含义，选中项靠显式
  `selectPathNode(new Selected(target,...))` 跟随（`CameraEditorModel.java:394`）。

### 2.4 变量与函数

- 身份 = **名字字符串**（可变）。`SymbolTable` 同时维护两份：顺序表 `variables`(38) 与索引
  `variablesByName`(40)（函数同理 41-42）。
- 改名 = 就地把 `Variable.name` 改掉并重建索引（`SymbolTable.java:134-136`）；`Variable.name(String)`(43) 是
  **public 就地 setter**，任何持有者都能绕过 `SymbolTable` 改名字，从而让两张表不一致。
- 引用按名字：`Expression.names` 返回名字集合（`expression/Expression.java:108-138`），`Resolver.resolve(String)`
  按名字取变量（`expression/Resolver.java:35`），`EvaluationGraph` 的节点 key 是 `v:` / `f:` / `t:` + 名字
  （`eval/EvaluationGraph.java:79-115`）。
- 变量指向轨道用 `TrackRef(trackId)`（`expression/TrackRef.java:24`）；轨道改名/删除由
  `SymbolTable.rebindTrack`(275) / `unbindTrack`(285) 就地改所有变量的来源。

### 2.5 其它沿用下标/可变引用的地方

`EditorSession.selectedKeyIndex()`（`api/editor/EditorSession.java:35`）、`selectedPathIndex()`(38)、
`selectKey(int)`(95)、`selectPathNode(int)`(98)、`moveKey(int,float)`(113)、`removeKey(int)`(116) 全都是下标契约；
`CameraEditorModel.KeyRef`(264)、`TimelinePanel.multiSelection`(114)、`TimelinePanel.validateSelection`(1276 按下标失效即丢弃)、
`CameraEditorScreen.KeyClip`(671，唯一用了稳定键 `trackId` 的剪贴板结构)、`CommandTrack.command(int,...)`(127)、
`CameraInfo.keyCountText(AnimationTrack)`(65)。

---

## 3. 表达式子系统

### 3.1 管线

```
文本 --Compiler.compile--> Node 树 --Evaluation.root(resolver).expression(node)--> float
        (按文本驻留缓存)      (包内可见)         (唯一求值入口 + 深度栈)
Formula.evaluate(scope) --scope==null? NaN--> Expression.evaluate(text, scope.resolver())
   --ExpressionScope.track(id)--> CurveSample(带 scope).evaluateOrFallback
```

| 环节 | 位置 | 关键点 |
| --- | --- | --- |
| 编译 | `expression/Compiler.java:44` | 递归下降 `Reader`(173)；两条编译期上限 `MAX_NESTING=64`(31)、`MAX_NODES=1024`(35)；常量折叠 `fold`(92) 只折四则/取模/`sqrt` 等 `foldable` 内置（`Builtins.java:133,138`） |
| 缓存 | `Compiler.java:38` | `ConcurrentHashMap<String,Node>`，上限 512 后整体清空（28、61-63）；**编译产物按公式文本全局驻留**，不进 `Formula` record（`Formula.java:8-11`） |
| AST | `expression/Node.java:20` | `sealed permits Const/Name/Neg/Binary/Builtin/Custom`；节点全不可变；`Node.Builtin` 在编译期解析并校验参数个数（`Compiler.java:300-310`）；`Node.Custom` 名字留到运行期查 |
| 求值 | `expression/Evaluator.java:20` | 全仓唯一读语法树处；树内递归不推深度，跨公式递归走 `Evaluation.expression` |
| 会话 | `expression/Evaluation.java:35` | 参数帧栈（38，`Frame` 113）、深度格（40）、`expression`(52) 是**深度栈唯一推拉点**、`resolve`(74) 先查帧再落 `Resolver`、`call`(95) 里按文本重新编译函数体（106） |
| 深度 | `expression/EvalDepth.java:18` | `MAX=64`，`ThreadLocal<int[]>`(21)，线程级共享 |
| 门面 | `expression/Expression.java:40` | `BUILTIN_FUNCTIONS`(42)、`valid`(50)、`evaluate`(55)、`names`(117/126)、`validName`(85)、`nameChar`(183) |

求值失败一律返回 `Float.NaN`（`Expression.java:55-58`），异常也在 `Evaluation.expression` 里被吞成 NaN（61-63）。

### 3.2 值来源 sealed 层级

```
ValueSource (sealed, expression/ValueSource.java:23)  permits  NumberSource, TrackRef
├── NumberSource (sealed, NumberSource.java:20)       permits  Constant, Formula
│   ├── Constant(float value)            record，不可变（Constant.java:11）
│   └── Formula(String expression, float fallback)  record，不可变（Formula.java:16）
└── TrackRef(String trackId)             record，不可变（TrackRef.java:24）
```

- 分层意图（类型级安全边界）：`TrackRef` **不实现 `NumberSource`**，因此"轨道读数挂到关键帧槽位"编译期即错
  （`TrackRef.java:6-9`、`NumberSource.java:7-11`）。
- 唯一回退点：`NumberSource.evaluateOrFallback`(29-38) —— NaN → `constant()` → NaN 再兜 0；
  `TrackRef.evaluateOrFallback`(26-29) —— scope 为 null 或轨道缺失/NaN → **0**（没有 fallback 字段）。
- `formula` 不算回退：`Formula.evaluate(null)` 直接 NaN（18-22），即 scope==null 就是**静态求值**。
- `Formula.broken()`(43) 供界面标红（= `Expression.compile(...) == null`）。

### 3.3 符号表职责（`expression/SymbolTable.java`，只读面 `SymbolTablec.java`）

职责：①两张表的顺序（=UI 顺序 =序列化顺序）②名字唯一性 ③内置量占用检查（`t`/`p`/`wt`，
`Resolver.java:21-27`）④形参冲突判定（`parameterConflict` 227，`Shadowing` record 209）
⑤**轨道引用完整性**（`rebindTrack` 275 / `unbindTrack` 285）⑥整表替换（`replaceFrom` 296，深拷贝）
⑦被引用的静态查询（`references` 326，走 `Expression.names` 语法树而非扫文本）。
自动命名：变量 `var1…`(77)、函数 `f1…`(146，默认 `(a,b) -> a + b` 151)。

### 3.4 内置函数注册与调用（`expression/Builtins.java`）

- **表驱动**：`SIGNATURES`(22，29 条签名，供界面模板) + `BY_NAME`(30) + `build()`(78)。
- 参数个数在**编译期**校验（`Builtin.accepts` 39，`Compiler.java:306-308`），个数不符 = 整条公式编译不过；
  自定义函数个数不符是**运行期**给 NaN（`Evaluator.java:60-62`）。
- **急切**：`Builtin.operator()` 在编译期就绑好，运行期不再按名字 switch（`Node.java:38`）；
  实参**急切求值**成 `float[]` 后交给算子（`Evaluator.java:44-53`）。
- **没有任何字符串字面量、没有惰性节点**：`Node` 只有 `Const/Name/Neg/Binary/Builtin/Custom`，
  语法只有 `+ - * / %`、一元 `-`、括号、数字、标识符调用（`Compiler.java:171`）。表达式**无条件分支、
  无字符串**，这也是"递归写不出终止条件"的原因（`CustomFunction.java:16-17`）。
- 不可折叠集合：`pow`、全部超越函数、`random`（`addUnfoldable`，110-127，理由见 108-109、123-124）。

### 3.5 `TrackRef` 如何解析

`TrackRef.evaluateOrFallback(scope)` → `scope.resolver().track(trackId)`（`TrackRef.java:27`）；
`ExpressionScope.track(String)`（`eval/ExpressionScope.java:218-227`）按 id 取 `Curve`，缺则 NaN，
否则 `trackSamples.computeIfAbsent(curve, k -> CurveSample.at(k, this))` —— **用带 scope 的采样**，
即轨道上的公式照常参与，与"该轨道作为属性播放"同一条求值路径。
`Scope` 只暴露 `time/progress/worldTime/version/resolver`（`expression/Scope.java:16-33`）；`Resolver extends TrackLookup`
（`Resolver.java:19`，`TrackLookup.track(String)` 是单方法接口）。

---

## 4. 求值与依赖图

### 4.1 `EvaluationGraph`（`eval/EvaluationGraph.java`，**在用**）

- 不可变、**每次现算**：`EvaluationGraph.of(CameraAnimationc)`(118)，由 `CameraAnimation.evaluationGraph()`(286)
  与 `cycle()`(293) 暴露；不缓存（类注释 75）。
- 三类节点用前缀 key：`v:`(79)/`f:`(81)/`t:`(83)；`deps(变量)=公式引用变量 ∪ 公式调用函数 ∪ 绑定轨道`，
  `deps(轨道)=该轨道所有键所有槽位公式的引用`(318-338)，`deps(函数)=函数体引用（形参不算）`(169-172)。
- 判定：Kahn 拓扑 `markResolved`(381)，一轮走不完的节点即环（`cycle()` 183 / `cycleText()` 188）。
- 试算（无副作用）：`allowsTrackFormula`(203)、`allowsVariableSource`(219)、`allowsFunctionBody`(247)——
  都是"换掉一个节点零件后重判一次"。
- 调用方：`AnimationCodec.animationFromJson` 载入收尾拒绝带环文件（270-276）；
  `ExpressionEditorWindow.formulaProblem`（`core/editor/ExpressionEditorWindow.java:834-844`）；
  `VariablePanel.bindTrack`（`panel/VariablePanel.java:430-435`）；`VariablePanel.rebuild` 取 `cycle()` 标红（143）；
  `EditorContext`/`CameraAnimation` 间接（`cycle()`）。
- 失败的兜底：`ExpressionScope` 的 `visiting` 栈（79-81、187-190、239-253）在求值期把自嵌套记成环并返回 NaN。

### 4.2 `EvaluationGraph2`（`eval/EvaluationGraph2.java`，**零调用点**）

- Guava `MutableGraph<Node>`(59) 的可变版：节点 `Node.Var/Func/Track`(77-89)；轨道按**引用计数**记账
  `trackRefCounts`(66)，以支持"某个槽位公式从 old 换成 new"（`applyTrackFormula` 316）；
  另有 `applyVarFormula`(345)、`applyFuncFormula`(364)、`check*`(406/424/439)、
  `referrers`(187)/`dependents`(199)/`removeNode`(241)/`breakReferences`(261)/`removeNodeCascade`(285)。
- 全仓 grep 只有自身内部引用（无 `new EvaluationGraph2`）。设计文档 `design-animation-editor-framework.md:755`
  已把它列为待统一对象。

### 4.3 "一次采样"当前返回什么

| 入口 | 返回 | 失败信号 / 兜底 |
| --- | --- | --- |
| `CurveSampler.sample(Curvec, time, Scope)`(`eval/CurveSampler.java:31`) | `float` | 空曲线 → 0（`Curve.java:57-59`）；公式失败 → `evaluateOrFallback` |
| `CurveSampler.sampleOnce(...)`(37) | `float`，不复用缓存 | 同上 |
| `CurveSampler.sampleStatic(...)`(47) | `float`，`scope=null` 分支 | 公式键取**回退值** |
| `CurveSampler.sample(animation,time,Evaluator<T>,scope)`(53) | `T`（如 `Vector3f`） | 缺通道 → 该分量 **NaN** 交给 `Evaluator.build`(59) |
| `CurveSampler.scope(animation,time,worldTime)`(26) | `Scope`（`ExpressionScope`） | — |
| `ExpressionScope.track(id)`(`eval/ExpressionScope.java:218`) | `float` | 轨道缺失 → **NaN** |
| `Expression.evaluate(text, resolver)`(`expression/Expression.java:55`) | `float` | 编译失败/求值异常/超深 → **NaN** |
| `NumberSource.evaluateOrFallback(scope)`(29) | `float` | **NaN → `constant()` → 0**（唯一回退点） |
| `TrackRef.evaluateOrFallback(scope)`(26) | `float` | NaN 或静态 → **0**（不是 fallback 字段） |
| `Path.evaluate(distance,dest)`(`path/Path.java:32`) | `Vector3f`（写入 dest） | 空路径 → `IllegalStateException`(36)；0 长度段 → 段起点(54-56) |
| `CameraPlayer.evaluatePose(CameraPose)`(95) | `CameraPose`（`positionValid`/`fovValid` 标记） | 见第 8 节 |

`CurveSample`（`eval/CurveSample.java:34`）实现 `KeyValues`：按 `(键, 槽位)` 惰性缓存（FIELDS=5，36），
未解析槽位放 NaN（42-43、78），`resolve`(120) 对越界下标返回 0（121-124）、曲线为 null 返回 0（135-137），
值来自 `sourceOf(...).evaluateOrFallback(scope)`(139)；`sourceOf`(143) **是全仓唯一需要
`key instanceof Keyframe` 的地方**（只读视图没有 `NumberSource`，退化为 `Constant(key.constant(field))`）。

`ExpressionScope`（`eval/ExpressionScope.java`）另外承担：内置量解析（161-172）、
变量值**每帧一次**缓存（69、174-204）、变量循环兜底（187-190）、
`frame(time,progress,worldTime)`(106) 换帧并 `version++` + 清缓存（缓存失效靠版本号，`Scope.java:25-30`）。
`Evaluation`/`EvalDepth` 的深度上限（64）是跨公式递归的最终兜底。

---

## 5. 编辑器写入路径

**总结论：没有命令对象、没有事务、没有校验层。面板直接持有 `EditorContext`，通过 `context.editor()`
（`CameraEditorModel`）、`context.animation()`（可变 `CameraAnimation`）、`context.player()` 与
`context.symbols()` 直接改数据；`EditorHistory` 只在每帧渲染开头被动拍一份 JSON 快照。**

### 5.1 时间轴：拖动关键帧（`panel/TimelinePanel.java`）

```
mouseClicked → contentMouseClicked(744)
   命中键(835-848): clearMultiSelection() → selectTrackRow(track)(1153) → editor.selectKey(keyIndex)
                    → drag = Drag.KEY, dragKey = keyIndex
mouseDragged → contentMouseDragged(886) case KEY(894-906)
   → context.editor().moveKey(trackRow.track(), dragKey, context.snapTime(xToTime(...)))
     └ CameraEditorModel.moveKey(242) → track.moveKey(index,newTime)（[AnimationTrack.java:56]）
        └ CurveTrack.moveKey(101) → Curve.moveKey(161)
             keys.remove(index) → key(keyframe.time(newTime))
                └ Curve.key(Keyframe)(129) → 二分：命中即 keys.get(i).set(key)(139)（旧对象被覆盖/丢弃）
                                           未命中即 keys.add(insertIndex, new Keyframe(key))(144)
     └ 返回新下标 → 若该轨道处于选中态则 selectedKeyIndex = moved(245-247)
```
其它直接写入点（同一文件）：
- 多选整体平移：`dragSelection`(1304) → `context.editor().moveKeys(multiSelection, targets)`(1338)
  → `CameraEditorModel.moveKeys`(299)（快照 `Keyframe` 引用 321-336 → 按下标从大到小 `curve.removeKey`(342-344)
  → `keyframe.time(...)`(349) → `curve.key(keyframe)`(350) 重插）；引用是 `KeyRef(track,index)`(1411)。
- 删除：`contentKeyPressed`(976-1002) → `editor.removeKeys(multiSelection)`(985) 或 `editor.removeSelectedKey()`(996)；
  右键菜单 1057-1069 同上。
- 新增：双击空白(859-869) 或右键菜单(1046-1050) → `editor.addKey(track, time, context.scope())`(864/1047)
  → `CameraEditorModel.addKey`(189) → 位置三轴走 `curveTrack.addKey(time, cameraAxis(axis))`(194)，
  其余走 `track.addKey(time, scope)`(204) → `CurveTrack.addKey(float,Scope)`(73) → `CurveSampler.sampleOnce`(74)
  → `Curve.key(Keyframe)`(92)。
- 轨道插入/移除/改名：`insertCurveTrack`(1117 → `animation.addCurveTrack`(1119))、`insertTrack`(1130 →
  `addExtensionTrack`(1132))、`removeTrack`(1138 → `removeChannel`/`removeExtensionTrack`)、
  `commitRename`(1218 → `renameCurveTrack`/`renameExtensionTrack`(1238-1239))。
- 轨道排序：`dragTrackOrder`(1474) → `moveDragged`(1493) → `context.animation().moveTracks/moveTrack`(1495/1498)
  ——**绕过 `CameraEditorModel` 直接用 `CameraAnimation`**。
- 播放头/视图：`context.player().seek(...)`(775/829/891)、`context.viewStartTime/ pixelsPerSecond`（会话状态）。

### 5.2 数值字段与公式字段（`panel/KeyframePanel.java`）

- 曲线键：`rebuild`(102) 取 `selected instanceof Keyframe`(114) 并**把该对象捕获进控件回调**，
  在 `expressionField`(282-285) 里生成 `value -> key.constant(field, value)`、
  `KeyframeAccessor`(289-299) 生成 `key.source(field, source)`；
  时间字段（137-138）直接 `value -> key.time(context.snapTime(value))`（**就地改时间，不重排、不走 model**）。
- 插值模式：`modeRow`(157) → `key::evaluateMode`（即 `Keyframe.evaluateMode(EvaluateMode)` 155）。
- 非曲线键：`timeRow`(182-191) → `context.editor().moveKey(track, editor.selectedKeyIndex(), snapTime(v))`(186)；
  指令文本 `commandRow`(194-203) → `track.command(index, value)`(198)。
- 控件与回调面：`NumberFieldWidget(rect, value, FloatSetter)`（`widget/NumberFieldWidget.java:31`，提交在
  `commit()` 171-183 → `onChange.set(value)`）、`TextFieldWidget(rect, value, Consumer<String>)`（`widget/TextFieldWidget.java:47`，
  `commit()` 294-306）、`ExpressionFieldWidget.Accessor {NumberSource source(); void source(NumberSource); }`
  （`widget/ExpressionFieldWidget.java:35-39`）。

### 5.3 表达式编辑（`widget/ExpressionFieldWidget.java` + `core/editor/ExpressionEditorWindow.java`）

```
点击动态模式预览框(ExpressionFieldWidget.mouseClicked 179) → openEditor()(226)
  → context.openExpressionEditor(label, expr, Subject.Track(trackId), null, this::applyExpression)
    （EditorContext.java:248 → new ExpressionEditorWindow(...) 249）
窗口"确定"(ExpressionEditorWindow.confirm 847)
  → formulaProblem(813)：①函数可用性 ②Expression.valid(826) ③selfReferencing(830)
     ④animation.evaluationGraph()(834) → allowsTrackFormula / allowsVariableSource / allowsFunctionBody(835-842)
  → finished = true; onConfirm.accept(stripped)(865-866)
回调落盘：ExpressionFieldWidget.applyExpression(236) → accessor.source(new Formula/Constant)(239)
        KeyframePanel.KeyframeAccessor.source(296) → key.source(field, source)(297)
        VariablePanel.openFormula(441) → variable.source(...)(448-449)
        FunctionPanel.openBodyEditor(191) → function::body(204) + parameters.set → function.parameters(202)
```
窗口内还**直接改文档**：变量增删 `addVariable`(979 → `symbols().addVariable()`) / `removeVariable`(988)，
且"取消"不回滚这些增删（`design-editor-ui-framework.md:27` 已列为不符事务语义）。
模式切换按钮 `toggleMode`(210-224) 是"写数值清公式 / 写公式保回退"的显式选择点。

### 5.4 曲线图（`panel/GraphPanel.java`）

- 拖动键本体：`contentMouseDragged`(621) case KEY(654-666) → `editor.moveKey(...)`(655) 后**再直接**
  `movedKey.value(yToValue(...))`(664)。
- 拖动曲柄：`dragTangent`(682-699) → `setLength`(515-521) / `setSlope`(527-533) →
  `key.outLength(517)/inLength(519)/outSlope(529)/inSlope(531)` —— **完全绕过 model 与历史**。
- 同一文件里 `Curve.MAX_HANDLE_LENGTH`(688) 与 `handleLength`(508) 是唯一复用曲线几何常数的地方。

### 5.5 路径编辑

```
PathNodePanel.setPosition(388)/setTangent(406) → context.editor().updatePathNode(index, node -> ...)(403/421)
   └ CameraEditorModel.updatePathNode(399) → Path.updateNode(109) → updater.update(nodes.get(index))(114)
                                                + updateArcLengthTable(115) → PathRender.markDirty()(404)
PathNodeListPanel: addNodeFromCamera(169 → context.recordPathNode)、removeSelectedNode(173 →
   editor.removePathNode(index) 174)、moveSelectedNode(157 → editor.movePathNode(index,offset) 158)
PathHandleDrag.update(138) → context.editor().updatePathNode(index, updated -> position/inTangent/outTangent)(176-186)
PathEditorScreen: removePathNode(290)、newPath(299 → context.editor().path().clear() 300)、
   applyPath(431 → context.editor().path(loaded) 439)（**整对象替换 Path**）
CameraEditorModel 内部：addPathNode(361)、insertPathNode(367)、addPathNodeAt(417)、capture(...)(459 →
   track.curve().key(Keyframe.create(time,value))(465) 直接插键)、applyRecordedPosition(431)、
   captureRotation(438)、captureFov(450)。
```
视图/编辑状态：`CameraEditorModel.ViewMode`(36)、`freePose`(46)、`selectedTrackId`(48)、
`selectedKeyIndex`(49)、`selectedPathNode`(50) —— 全部在该类内可变，面板直接读写。

### 5.6 面板使用的其它输入面（`panel/EditorPanel.java`、`widget/*`）

- `EditorPanel.layoutWidgets(UiRect)`(213)、`renderContent(...)`(216)、
  `contentMouseClicked`(333)/`contentMouseDragged`(337)/`contentMouseScrolled`(341)/`contentKeyPressed`(345)；
  控件重建触发条件：主题版本或内容区尺寸变化（163-168）。
- `WidgetHost`（`widget/WidgetHost.java`）：`add`(18)、`clear`(23)、`removeIf`(30)、`focus`(46)、
  `render`(62)、`mouseClicked`(71)、`mouseReleased`(85)、`mouseDragged`(99)、`mouseScrolled`(103)、
  `keyPressed`(115)、`charTyped`(119)。
- 回调形态：`ButtonWidget(Runnable onPress)`（按键抬起时执行，`widget/ButtonWidget.java:38,151`）、
  `LabelWidget.onClick(Runnable)`(`widget/LabelWidget.java:70`)、`NumberFieldWidget.FloatSetter`、
  `TextFieldWidget.Consumer<String>`、`ExpressionFieldWidget.Accessor`。
- 右键菜单：`EditorPanel.openMenu(ContextMenu,...)`(229) + `menuMouseClicked`(242)，由屏幕最上层统一绘制/派发。
- 面板刷新模式：每帧构造 revision 字符串决定"是否重建控件"（`KeyframePanel.java:89`、`VariablePanel.java:124`、
  `FunctionPanel.java:81`、`AnimationPanel.java:66`、`PathNodePanel.java:92`、`PathNodeDetailPanel.java:70`、
  `PathNodeListPanel.java:72`），数值则靠 `refreshers`（每帧读活模型）——**没有文档版本/失效信号**。

### 5.7 历史与存读档（会话级写入）

- 快照：`CameraEditorScreen.extractRenderState`(230) 每帧 `history.capture(context.animation())`(235)
  → `EditorHistory.capture`(35)：250 ms 节流（21、42-44）+ 与当前快照字符串比较（46-50），
  内容是 `AnimationCodec.animationToJson`(46) 的**整份 JSON**，上限 64 份（19、61-64）。
- 撤销/重做：`CameraEditorScreen.undo`(583) / `redo`(594) → `EditorHistory.restore`(88)
  → `AnimationCodec.animationFromJson`(89)（会重新解析整份文档）→ `animation.copyFrom(loaded)`(98)
  → `clearSelectionAfterRestore`(605) 清空下标型选中。
- 保存/打开：`openAnimationFile`(462)、`saveAnimationTo`(487，**先 `animation().name(name)` 再序列化** 491-492)、
  `saveAnimationToStorage`(513，同上 515-516)、`applyAnimation`(545，`copyFrom` 554 后清选中并
  `context.choosePathAfterLoad()` 564)。
- 剪贴板：`copySelectedKey`(616 → `KeyClip(track.id(), time, keyframe.sources(), evaluateMode)` 625) /
  `pasteKey`(630 → `Keyframe.create(...)`+`clip.sources().forEach(key::source)`(643) → `target.curve().key(key)`(644)) /
  `resolveClipTrack`(664，轨道不在就 `addChannel(trackId)` 666)。**这是唯一以稳定 id 记录引用关系的结构**。

### 5.8 指令写入路径（客户端命令）

`core/cmd_camera/CameraCommand.java`（注册于 `RegisterClientCommandsEvent`，25-63）把
`cmd_camera path ...` 全部映射到 `CmdCamera.INSTANCE.editor()`：
`editor().path(new Path(name))`(72)、`editor().path().clear()`(80)、`path().name(name)`(91)、
`selectPathNode`(107/124/141)、`addPathNode`(157/167)、`insertPathNode`(178)、`removePathNode`(188/204)、
`updatePathNode(index, NODE_POS_UPDATER/NODE_MODE_UPDATER/NODE_SMOOTH_UPDATER)`(286/304/323/342/361/388/399/419/429)。
即**第二条独立写入路径**，与 UI 共享同一个 `CameraEditorModel`，同样不走事务。

---

## 6. 会话 / UI 后端接口面

### 6.1 契约

`api/editor/EditorSession.java`（153 行）：只读状态 `animation()`(22，`CameraAnimationc`)、`path()`(25，`Pathc`)、
`duration()`(28)、`selectedTrack()`(32，返回**活 `AnimationTrack`**)、`selectedKeyIndex()`(35)、
`selectedPathIndex()`(38)、`pathMode()`(41)；时间轴视图 `pixelsPerSecond`(48/51)、`viewStartTime`(54/57)、
`majorTickStep`(60)、`formatTick`(63)、`snapTime`(66)；播放 `playheadTime`(73)、`playing`(76)、
`togglePlay`(79)、`stopPlayback`(82)、`seek`(85)；选择与编辑 `selectTrack(String)`(92)、`selectKey(int)`(95)、
`selectPathNode(int)`(98)、`switchToPathMode`(101)、`switchToCoordinateMode`(104)、`recordPathNode`(107)、
`addKey(float)`(110)、`moveKey(int,float)`(113)、`removeKey(int)`(116)、`removeSelectedKey`(119)；
二次确认 `pendingConfirm`(130)/`confirmPending`(133)/`dismissPending`(136)；提示与关闭 `notify`(143)/
`statusMessage`(147)/`close`(150)。——**契约里唯一的身份就是下标**。

`api/editor/EditorUiBackend.java`：`priority()`(12，默认 0) + `openEditor(EditorSession)`(20，返回是否接管)。
`api/editor/EditorUiHost.java`：静态注册表 `BACKENDS`(18)、`register`(24，按优先级降序)、`hasBackend`(34)、
`open(EditorSession)`(39，依次询问，全拒则 false)。

### 6.2 实现者与持有者

- `EditorSession` 全仓**唯一实现** = `core/editor/BuiltinEditorSession.java`（18），构造于
  `CameraEditorScreen.open()`(1240)：`EditorUiHost.open(new BuiltinEditorSession(context))`。
- `EditorUiBackend` 本仓库内**没有实现**（留给附属 mod；`EditorConfig.MODERN_UI_COMPAT`，`EditorConfig.java:72-75`
  控制是否询问后端）。
- `EditorContext`（`core/editor/EditorContext.java`，416 行）是真正的共享状态容器：
  模型 `editor`/`player`/`animation`/`info`(27-30)、视图参数 `pixelsPerSecond=80f`(32)、`viewStartTime`(33)、
  `snapEnabled=true`/`snapStep=0.1f`(34-35)、`bezierSymmetric=true`(36)、`flySpeed=12f`(37)、
  状态提示 `statusMessage`+`statusUntil`(39-40)、模态 `dialog`(42) 与 `expressionEditor`(44)、
  帧级 `frameScope`(202)/`frameDirty`(204)/`frameSampler`(206)。
- `BuiltinEditorSession` 是**纯适配器**：读写全部转给 `context`；值得注意的实现细节：
  `animation()`(26) 把可变 `CameraAnimation` 当只读返回；`selectedPathIndex()`(117) 取 `Selected.index()`；
  `removeSelectedKey()`(177) **绕过 `CameraEditorModel` 直接 `track.removeKey(index)`**；
  `confirmPending()`(187) 先 `closeDialog()` 再执行 `dialog.confirm()`；`close()`(214) 走
  `Minecraft.getInstance().screen.onClose()`。
- 编辑器的状态驻留：播放头/播放态在 `CameraPlayer`（`time` 36、`state` 35、`speed` 37、`loop` 38、
  `worldTime` 41），缩放/平移/吸附在 `EditorContext`，选中在 `CameraEditorModel`，布局在 `DockLayout`
  并持久化到 `EditorConfig`（`CameraEditorScreen.restoreLayout` 180 / `persistLayout` 192）。
- 首帧/每帧钩子：`EditorContext.beginFrame()`(213)（版本号 +1 使求值缓存失效，注释 208-212）、
  `scope()`(219)、`sampler()`(230)、`evaluateExpression`(261)；`CameraScreens.editing()`（`core/editor/CameraScreens.java:18`）
  以"当前 Screen 类型"判定编辑器是否打开（供相机接管与渲染使用）。

---

## 7. 持久化

### 7.1 `AnimationCodec`（`core/animation/io/AnimationCodec.java`，594 行，**手写 Gson**）

- 顶层字段：`name`、`motionMode`、`distanceMode`、`tracks`（数组）、`variables`、`functions`、`path`（104-131）。
  **没有 `dataVersion` / 版本字段**（Gson pretty printing，97）。
- 轨道数组按动画顺序写出，两类轨道混排用 `type` 区分（115-126）：曲线轨道写
  `type/id/preMode/postMode/keys`（305-324），扩展轨道写 `type/id/keys`，键交回 `JsonTrack.writeKeys()`（203-217）。
- 一个数值的三种写法（由 `SourceJson` 内聚，`expression/SourceJson.java:13-21`）：`1.5`、
  `{"expression":"…","fallback":1.5}`、`{"track":"fov"}`；**关键帧槽位不认 `track`**，
  读到按回退值降级并 `LOGGER.warn`（`SourceJson.java:51-67`，`NumberSource.read` 54 转调）。
  键的字段顺序与旧格式逐字对齐（`Keyframe.java:251-257`）。
- 路径是纯几何：`name/nodes[]`，节点 `position/inTangent/outTangent/pathMode/smooth`（330-365）。
- **解码直接构造并就地改对象**（关键事实）：
  `new CameraAnimation(...)`(227) → `readTracks`(231)：`animation.addChannel(id).curve()`(387) 后
  `readCurve` 直接写 `curve.preMode/postMode`(428-429)、循环 `curve.removeKey(0)` 清空(432-434)、
  `curve.key(Keyframe.read(...))` 逐键插入(444)；JSON 里没有的通道被 `animation.removeChannel(...)` 剪掉(394-398)；
  扩展轨道按 `type` 取工厂 `type.factory().create(id)`(412) → `jsonTrack.readKeys(...)`(421) → `addExtensionTrack`(424)；
  变量先 `symbols().addVariable(name)`(457) 再就地 `variable.source(...).defaultValue(...)`(460)；
  路径 `new Path(...)`(466) + `path.node(...)`(475)；节点 `new PathNode(new Vector3f())`(483) + 逐个 setter，
  写切线前先 `restoreSmooth(false)` 再恢复(489-496)。
- 载入后两道整体校验：`symbols().shadowing()` 非 null 即拒（259-265）、
  `evaluationGraph().cycleText()` 非 null 即拒（270-276）——**都返回 null（整份文件作废）**。
- 宽松解析：字段缺失/类型错/枚举非法一律取默认（`enumValue` 553、`motionModeValue` 574 默认 `PATH`、
  `distanceModeValue` 585 默认 `ABSOLUTE`、`WrapMode.CLAMP` 428-429、`EvaluateMode.LINEAR`（`Keyframe.java:330`））。
- 只写路径的入口 `pathToJson`(281)/`pathFromJson`(286)；`JsonSerializable`（`api/animation/JsonSerializable.java:14`）
  把"字段名与缺省值"下放到数据类。

### 7.2 `AnimationFiles`（`core/animation/io/AnimationFiles.java`）

本地文件，游戏目录下与 screenshots 平级：`camera_animations/*.animation.json`(26、30、41)、
`camera_paths/*.path.json`(28、31、46)。入口 `listAnimations`(51)/`listPaths`(56)/`saveAnimation`(61)/
`savePath`(66)/`loadAnimation`(71)/`loadPath`(76)/`saveTo(Path,String)`(81)/`loadAnimationFrom(Path)`(101)/
`loadPathFrom`(106)/`withSuffix`(114)/`stem`(121)/`isAnimationFile`(126)/`isPathFile`(131)。
UTF-8 明文，IO 异常内部消化（93-97、165-175），文件名非法字符替换为下划线（35、233-240）。

### 7.3 `AnimationSavedData`（`core/animation/io/AnimationSavedData.java`，NBT）

- 存档内 `SavedData`：`DataStorage.computeIfAbsent(TYPE)`（61-81、87-89），落盘
  `data/free_camera_api_tripod/animations.dat`（33、40-47）；内容 = 两个 `CompoundTag`
  `animations` / `paths`，**键 = 名字，值 = 动画/路径的 JSON 字符串**（49-50、138、149、286-291）。
- 层级用名字里的 `/` 推导（37、92-101、302-329），空文件夹写占位键 `".folder"`（39、122）。
- 入口：`listFolders`(92)/`listFiles`(98)/`exists`(104)/`createFolder`(115)/`join`(127)/`saveAnimation`(131)/
  `savePath`(142)/`loadAnimation`(153，另有服务端重载 159)/`loadPath`(163)/`deleteEntry`(169)/
  `deleteFolder`(197)/`renameFolder`(224)。
- **没有 dataVersion**；解码层只认字符串条目，非字符串按损坏跳过（293-298）。

### 7.4 其它持久化

- 编辑器布局：`EditorConfig`（`EditorConfig.java`）里 `LAYOUT`/`LAYOUT_BOTTOM_HEIGHT`/`LAYOUT_COLLAPSED`/
  `PATH_LAYOUT`/`PATH_LAYOUT_COLLAPSED` 等客户端配置；布局串自带版本 `v=3`（`layout/DockLayout.java:48`，
  序列化 165-185，恢复 218-302），版本不符整串作废。
- 网络：`registry/CameraPlaybackPayload.java`（record，`Kind ∈ {LOCAL, STORAGE, STOP}`，按序数收发 34-36）+ 
  `registry/ModPayload.java:18` 注册为 playToClient；服务端 `core/cmd_camera/CameraPlayCommand.java`
  只发送"播哪一段"，动画 JSON 随包（STORAGE）或由客户端自读（LOCAL）。

---

## 8. 播放与相机应用

### 8.1 每帧做什么

`CmdCamera.update(float partialTicks)`（`core/cmd_camera/CmdCamera.java:47`）：

1. `player.worldTime(worldTime())`(50)（一天进度归一化，114-115）；
2. `player.playing()` 时 `player.tick(deltaSeconds)`(52-54)；
3. `CameraScreens.editing()`(58) 为真且自由视角 → `pose.set(editor.freePose())` + `apply()` 返回（61-65）；
4. 编辑器打开或播放器非 STOPPED → `syncPosePositionFromPlayer()`(70，把姿态位置预置为玩家眼位，供
   "没有关键帧的轴"沿用 80-89) → `player.evaluatePose(pose)`(71) → `apply()`(72)；
5. 否则 `modifier.disable()`(76)。

`CameraPlayer.tick(deltaSeconds)`（`playback/CameraPlayer.java:52`）：只在 `PLAYING` 推进（53-55）；
越过时长时循环回绕并**分段派发** `(previous,duration]` + `(0,time]`(66-70)；不循环则停在末尾、
`state = STOPPED`、并把最后一段走完(71-78)。区间派发 `advanceTracks`(82-92) 只给
`extensionTracks()` 里实现 `TickTrack` 的轨道（`CommandTrack.advance` 因此在播放中**执行指令**，
`CommandTrack.java:214-224`）。

`CameraPlayer.evaluatePose(CameraPose dest)`（95-154）：

- 每帧 `this.scope.frame(time, ExpressionScope.progressOf(animation, time), worldTime)`(98) —— 作用域跨帧复用；
- COORDINATE 模式(100-124)：取 `position.x/y/z` 三条曲线，**逐轴 `hasKeys(x)`(106/111/115/119) 判断是否接管该轴**，
  取值为 `finiteOr(sampler.sample(x, time, scope), position.x)`(112 等)，最后 `dest.positionValid(any)`(124)；
- PATH 模式(125-140)：`distanceCurve = animation.curve(CHANNEL_POSITION)`(127) →
  `animation.distanceToLength(sampler.sample(...))`(128，百分比口径在此乘总长，`CameraAnimation.java:462`) →
  `path.evaluate(distance, posCache)`(129) → 非有限值则 `positionValid(false)`(132-137)；
  路径为空同样 `positionValid(false)`(138-139)；
- 旋转：`sampler.sample(animation, time, rotEvaluator, scope)`(142)（`RotEvaluator` 254-272，
  三个通道缺失时该分量为 NaN，`CurveSampler.java:59`）；
- FOV：`fovCurve.size() > 0` 才 `dest.fov(...)`+`fovValid(true)`(145-147)，否则 `fovValid(false)`(148-151，
  注释说明"求值会得到 0，会把画面压成一个点")。

`apply()`(91-109)：`positionValid` 决定 `modifier.enablePos().setPos` 还是 `disablePos()`（94-99）；
`setRotationYXZ`(101)；`fovValid` 决定 `enableFov().setFov` 还是 `disableFov()`(103-108)。

### 8.2 静默替换 / 兜底清单（播放链）

| 位置 | 行为 |
| --- | --- |
| `CameraPlayer.java:112/116/120` `finiteOr(...)` | 非有限采样值 → 沿用当前姿态分量 |
| `CameraPlayer.java:106,124` | 某轴无关键帧 → **不接管该轴**（视为"不修改"），只有三轴都空才 `positionValid(false)` |
| `CameraPlayer.java:132-137` | 路径求值出 NaN/Inf → 位置整体失效（不写位置） |
| `CameraPlayer.java:145-151` | fov 通道无键 → 交还原版 FOV（`fovValid(false)`） |
| `CmdCamera.java:94-108` | `positionValid/fovValid` 为假 → `disablePos()/disableFov()` |
| `Curve.java:57-59` | 空曲线 → 0（对 fov 就是"压成一点"的隐患，靠上面那条规避） |
| `Curve.java:77-84` | 段时长 ≤ 0 或切线无穷 → 取左值（隐式 STEP） |
| `CurveSample.java:121-137` | 下标越界 / 曲线为 null → 0 |
| `NumberSource.java:29-38` | 公式失败 → `constant()` → 0 |
| `TrackRef.java:26-29` | 轨道缺失或静态求值 → 0 |
| `Expression.java:55-58` | 公式编译失败/异常 → NaN |
| `CommandTrack.java:228-232` | 无玩家（标题界面）→ 指令静默丢弃 |
| `CommandTrack.java:220` | 空指令键 → 跳过 |
| `CameraPlaybackClient.java:38-47` | 本地文件缺失 / JSON 非法 → 动作栏提示并**不播放** |
| `AnimationCodec.java:259-276` | 名字冲突或依赖成环 → 整份文件拒绝载入（返回 null） |
| `CameraEditorModel.java:419-421,459-463` | 非有限坐标/时间 → 拒绝记录（不写 NaN 进数据） |
| `PathRender.java:94,124-127` | 路径不可渲染（总长非正/非有限、无界面打开）→ 跳过采样 |

### 8.3 相机接管与视口

`CameraScreens.editing()`（`core/editor/CameraScreens.java:18-24`）按当前 `Screen` 类型判定
（`CameraEditorScreen` / `PathEditorScreen` / `WorldViewScreen`），并取消手部渲染（31-36）。
视口是真帧抓取贴回：`ViewportPipRenderer`（`core/editor/render/ViewportPipRenderer.java:47`）在 PiP 阶段把
主渲染目标拷进离屏纹理（`renderToTexture` 77-100，alpha 处理见类注释 42-46），`blitTexture`(103-124) 贴回；
`ViewportPanel.renderFrame`(219-227) 提交 `ViewportPipRenderer.State`。自由飞行/鼠标锁定在
`core/editor/ViewportTakeover.java`（`takeOver` 41、`tick` 72、鼠标换算 130-160、光标钉回 180-197）。

---

## 9. 重构前旧模型的不可变性阻塞项（不作为 v2 现状结论）

按类别列出具体 API。**括号内是可复现的最小路径**。

### A. 文档对象本身到处是可写 setter（无 `with*`、无 ID）

1. `CameraAnimation.name(String)` — `api/animation/CameraAnimation.java:491`（保存时先改名再序列化：`CameraEditorScreen.java:491`）。
2. `CameraAnimation.path(Path)` — `CameraAnimation.java:315`（`EditorContext.bindPath` 363 整对象替换）。
3. `CameraAnimation.motionMode(MotionMode)` — `CameraAnimation.java:329`（内部 `removeChannel/addChannel` 336-347，**删掉另一模式的通道**）。
4. `CameraAnimation.distanceMode(DistanceMode)` — `CameraAnimation.java:431`（445-453 **就地遍历并改 `key.value(...)`**）。
5. `CameraAnimation.restoreMotionMode/restoreDistanceMode` — `CameraAnimation.java:415,425`（反序列化旁路）。
6. `CameraAnimation.addChannel/addCurveTrack/removeChannel/addExtensionTrack/removeExtensionTrack` — `:88,113,162,210,220`。
7. `CameraAnimation.renameCurveTrack/renameExtensionTrack` — `:130,235`（改名 = 换 map 键 + 就地 `renamable.rename`(251) + `symbols.rebindTrack`(156)）。
8. `CameraAnimation.moveTrack/moveTracks` — `:354,384`（重排 = 重建整张 map；顺序是文档数据，见 350-353 注释）。
9. `CameraAnimation.copyFrom(CameraAnimation)` — `:469`（**`this.path = other.path`(473) 共享 Path；轨道按引用搬运(478-480)**）。
10. `CameraAnimation.symbols()` 返回可写 `SymbolTable` — `CameraAnimation.java:278`（对外只读面 `CameraAnimationc.symbols()` 49 是 `SymbolTablec`，但任何拿到具体类的地方都能改）。
11. `Curve.key(Keyframe)` / `key(float,float)` / `key(int)`（返回活键） / `moveKey` / `removeKey` / `smoothTangents` — `api/animation/curve/Curve.java:129,121,149,161,178,187,193`。
12. `Curve.preMode`/`postMode` 是 **public 可变字段** — `Curve.java:16-17`（`AnimationCodec.java:428-429` 直接赋值）。
13. `Curve` 把求值缓存（`lastIndex`/`positive`）与二分探针（`searchingCache`）**存在数据对象里** — `Curve.java:19-20,378`（同一对象在多线程/多视口求值下被写）。
14. `Keyframe.time/evaluateMode/constant/value/inSlope/outSlope/inLength/outLength/set/source/formula` — `api/animation/Keyframe.java:150,155,162,171,175,179,183,187,193,215,221`（**且 `time(float)` 不重排，破坏升序不变量**）。
15. `Path.node(PathNode)/node(int,PathNode)/updateNode/insertNode/removeNode/moveNode/name/clear` — `api/animation/path/Path.java:83,104,109,119,131,162,363,368`；`updateNode` 把**活节点**交给回调(114)。
16. `PathNode` 的 `final Vector3f` 三件套 + setter，且 `position()`(38) 返回的就是活向量 — `api/animation/path/PathNode.java:15-17,38,42,52,67,82,92`。
17. `SymbolTable.addVariable/removeVariable/renameVariable/addFunction/removeFunction/renameFunction/rebindTrack/unbindTrack/replaceFrom` — `api/animation/expression/SymbolTable.java:93,107,121,162,175,188,275,285,296`。
18. `Variable.name/source/defaultValue` — `api/animation/expression/Variable.java:43,53,63`（**`name(String)` 可绕过符号表改名字**）。
19. `CustomFunction.name/parameters/body` — `api/animation/expression/CustomFunction.java:36,46,56`。
20. `CommandTrack.addKey/removeKey/moveKey/command(int,String)/rename` — `core/animation/track/CommandTrack.java:65,89,100,127,187`。
21. `AnimationTrack` 接口本身就只提供按索引的写方法 — `api/animation/track/AnimationTrack.java:42,53,56`。

### B. 只读视图泄漏可变对象

22. **`CameraAnimationc.curve(String)` 声明返回可变 `Curve`** — `api/animation/CameraAnimationc.java:46`（对比 `Curvec` 才是只读面）。
23. `CameraAnimationc.curveTracks()` → `CurveTrack.curve()` → 可变 `Curve` — `CameraAnimationc.java:37` + `track/CurveTrack.java:106`。
24. `AnimationTrack.key(int)` 返回活 `TrackKey`；`CurveTrack.key(int)` 返回活 `Keyframe` — `AnimationTrack.java:37`、`CurveTrack.java:59`。
25. `SymbolTablec.variables()/functions()` 返回的 `Variable`/`CustomFunction` 可变（`List.copyOf` 只是浅拷贝） — `SymbolTablec.java:19,25` + `SymbolTable.java:47,57`。
26. `EditorSession.selectedTrack()` 返回活轨道 — `api/editor/EditorSession.java:32`（实现 `BuiltinEditorSession.java:107`）。
27. `BuiltinEditorSession.animation()` 把可变 `CameraAnimation` 当只读视图交出去 — `core/editor/BuiltinEditorSession.java:26-28`。
28. `CameraEditorModel.animation()`/`path()` 返回可变实例 — `core/cmd_camera/edit/CameraEditorModel.java:56,60`。
29. `KeyField.values` 是 public static 数组 — `api/animation/KeyField.java:34`（可被外部改写）。

### C. 索引身份（无法在版本切换后安全引用）

30. `Selected(index, type)` — `core/cmd_camera/edit/Selected.java:3`；用于所有路径选中/编辑。
31. `CameraEditorModel.KeyRef(track, index)` — `CameraEditorModel.java:264`；`TimelinePanel.multiSelection`(114) 与
    `validateSelection`(1276，**下标失效即静默丢弃**)。
32. `CameraEditorModel.selectedKeyIndex`(49) / `selectKey(int)`(156) / `removeKey(track,index)`(230) / `moveKey(track,index,...)`(242)。
33. `EditorSession.selectKey(int)`(95) / `moveKey(int,float)`(113) / `removeKey(int)`(116) / `selectedKeyIndex()`(35) / `selectedPathIndex()`(38)。
34. `CurveSample` 缓存下标 = `index*FIELDS + field.ordinal()` — `eval/CurveSample.java:126`。
35. `Curve.key(Keyframe)`/`moveKey` 会在同时间时**销毁对象并覆盖到旧键上** — `Curve.java:139,173`。
36. `TimelinePanel.dragKey`(96)/`GraphPanel.dragKey`(67) 在拖拽期间缓存下标，移动后靠返回值续接（`TimelinePanel.java:900-902`）。
37. `CommandTrack.command(int,...)`(127) 与 `CameraCommand` 的全部 `index<int>` 参数（`CameraCommand.java:39-60`）。

### D. 可变对象引用被 UI 长期捕获（跨版本悬挂）

38. `KeyframePanel` 把 `Keyframe` 捕获进控件回调 — `panel/KeyframePanel.java:114` + `137-138`(直接 `key.time(...)`) +
    `282-285`(`key.constant(field,value)`) + `289-299`(`key.source(field,source)`)：
    撤销/读档（`copyFrom`）之后这些回调仍在改**已经不在文档里的键对象**。
39. `GraphPanel` 直接对捕获的 `Keyframe` 写数值/曲柄 — `panel/GraphPanel.java:664,515-521,527-533,689-698`。
40. `CameraEditorModel.moveKeys` 先快照 `Keyframe` 引用、`removeKey`、再 `keyframe.time(...)` 重插 — `CameraEditorModel.java:321-351`
    （对象在移除期间脱离文档）。
41. `CameraEditorScreen.KeyClip` 保存 `sources()` 快照 + `trackId`（唯一稳定做法） — `CameraEditorScreen.java:625,671`；
    但 `pasteKey` 落到 `target.curve().key(key)`(644)，仍按时间覆盖。
42. `AnimationCodec` 解码期就地改临时对象（不算泄漏，但说明"构造即可变"） — `io/AnimationCodec.java:387,428-434,444,457-460,475,483-496`。

### E. 求值缓存按对象身份 + 帧版本失效（不是按文档版本）

43. `CurveSample.reset` 以 `(curve 身份, size, scope 身份, scope.version)` 决定是否清缓存 — `eval/CurveSample.java:66-84`
    （就地改键不会使缓存失效；`CurveSampler.clear()`（`eval/CurveSampler.java:69`）是唯一手动手段，注释 65-68 明说
    只在"就地改了某个键的公式"时才需要）。
44. `ExpressionScope` 按引用持有 `SymbolTablec` 并按帧版本清变量缓存 — `eval/ExpressionScope.java:87-91,106-115,174-204`
    （换文档只能靠重建/重绑，`trackSamples` 同理 70-77,226）。
45. `CameraPlayer` 与 `EditorContext` 各自长期持有 `ExpressionScope` — `playback/CameraPlayer.java:34,45`、
    `core/editor/EditorContext.java:51,202`。
46. `EditorContext.beginFrame`/`scope()` 以"每帧 +1 版本"代替文档版本 — `EditorContext.java:208-226`。

### F. 全局可变注册表（文档语义依赖进程状态）

47. `TrackTypeRegistry` 静态 `LinkedHashMap` + 运行期 `register` — `track/TrackTypeRegistry.java:19,33`（`FreeCameraApiTripodClient.java:20` 注册 `CommandTrack.TYPE`）。
48. `AnimationChannelRegistry` 静态表提供通道显示名/颜色/默认值 — `track/AnimationChannelRegistry.java:24-25,47-57`；
    `CameraAnimation.addChannel` 在创建时把 label/color 拷进轨道（`CameraAnimation.java:96`），**同一份 JSON 在不同注册表状态下得到不同 label**。
49. `TrackType.factory()` 在解码期被调用（`AnimationCodec.java:412`）——文档解码依赖全局工厂。
50. `Compiler.CACHE` 全局按文本驻留编译结果（512 上限后整体清空） — `expression/Compiler.java:38,61-63`。

### G. 没有事务/校验/版本

51. 面板直接改模型，无 `EditOp`/候选文档：`TimelinePanel.java:864,898,1047,1119,1132,1140,1238,1495`、
    `KeyframePanel.java:137-138,186,198,284,297`、`PathNodePanel.java:317,330,403,421`、`PathNodeDetailPanel.java:182,194,228,246`、
    `VariablePanel.java:269,332,351,391,407,437,448`、`FunctionPanel.java:202,204,212,231,256`、
    `AnimationPanel.java:98`、`GraphPanel.java:655,664,689-698`。
52. 表达式窗口在"取消"路径上不回滚副作用 — `core/editor/ExpressionEditorWindow.java:979-993`（变量增删直接落文档）。
53. 撤销靠整份 JSON 快照 + `copyFrom`（无文档版本号、无基版本检查） — `core/editor/EditorHistory.java:35-67,88-107`。
54. 载入/播放也靠 `copyFrom` 原地替换 — `CameraEditorScreen.java:554`、`playback/CameraPlaybackClient.java:50`。
55. `EvaluationGraph2` 是一套**已写好但未接线**的可变编辑器图 — `eval/EvaluationGraph2.java:58`（零调用点）。
56. 关键帧时间可就地改而破坏升序 — `KeyframePanel.java:137-138`（配合 `Curve.findFloorIndex` 334 与 `key(Keyframe)` 134 的二分假设）。
57. 编辑期"非有限值"防护是零散的，不是统一校验：`CameraEditorModel.java:419-421,459-463`、`CameraPlayer.java:162`、`CameraEditorModel.java:470-472`。

---

## 10. 可复用资产（建议保留原样）

| 资产 | 位置 | 说明 |
| --- | --- | --- |
| 三次贝塞尔反解与求值 | `curve/Curve.java:403`（`solveParameter`）、`426`（`parameterX`）、`432`（`parameterSlope`）、`438`（`bezier`） | 纯静态、无状态；牛顿迭代 8 次 + 斜率下限保护，退化时返回当前值 |
| 柄长夹取 | `Curve.java:386,390`（`MAX_HANDLE_LENGTH`、`handleLength`） | 保"横坐标单调 → 反解唯一"的关键不变量 |
| 时间 wrap 映射 | `Curve.java:250-325`（`mapTime`/`mapPreTime`/`mapPostTime`，CLAMP/LOOP/PING_PONG） | 含浮点边界修正（307-310） |
| 插值分派 | `Curve.java:89-93` + `evaluateLinear`(96) | 线性/STEP/贝塞尔三态 |
| 切线平滑 | `Curve.java:187,193`（Catmull-Rom 加权 `Mth.lerp`） | 就地写回，搬迁时需改成 `with*` |
| 弧长表（增量更新） | `path/Path.java:174,192,221` | 高斯-勒让德 5 点积分求贝塞尔/CR 段长 |
| 弧长→参数定位 | `path/Path.java:250`（带方向缓存的 `findFloorIndex`）+ `296`（手写 `binarySearch`） | 逻辑可留，缓存字段需移出文档 |
| 样条数学 | `util/SplineUtils.java:18,46,62,91`（`bezierLength`/`bezier`/`catmullRomLength`/`catmullRom`）+ 私有导数 31,75 | 5 点高斯积分 + `Mth.catmullrom`，纯函数、无分配泄漏（少量临时 Vector3f） |
| 采样器门面 | `eval/CurveSampler.java:20,26,31,37,47,53,69` | "解析 + 插值"的唯一入口；缓存策略需改为按文档身份/版本 |
| 键值读取器抽象 | `curve/KeyValues.java:15` + `Curve.evaluate(time, KeyValues)` | 让曲线保持纯数值插值，公式取值外置；是重构时可原样保留的接缝 |
| 表达式编译器 | `expression/Compiler.java:92`（常量折叠）、`173`（`Reader`）、`44`（按文本驻留 + 两条上限） | 与可变文档无关，可直接沿用；`Node` 不可变且包内可见 |
| 解释器与会话 | `expression/Evaluator.java:20`、`expression/Evaluation.java:52,74,95` | 唯一求值入口 + 词法参数帧 + 线程级深度（`EvalDepth.java:18`） |
| 内置函数表 | `expression/Builtins.java:22,38,78,133,138` | 签名/实现/可折叠性一处定义；`random` 用 `ThreadLocalRandom` |
| 引用分析 | `expression/Expression.java:117,126`（`names`）+ `140`（语法树遍历） | 形参遮蔽正确、区分变量/函数/内置三桶，是依赖图与判环的基础 |
| 值来源层级 | `expression/ValueSource.java:23`、`NumberSource.java:20`、`Constant.java:11`、`Formula.java:16`、`TrackRef.java:24` | 已是不可变 record；`TrackRef` 不进关键帧的类型边界值得原样保留 |
| 依赖图判定（不可变版） | `eval/EvaluationGraph.java:118,203,219,247,381` | 三类节点前缀 key、拓扑判环、三个"试算"入口；按文档派生即可 |
| 节点级 JSON 自描述 | `api/animation/JsonSerializable.java:14` + `Keyframe.java:259/296` + `Variable.java:86/94` + `SourceJson.java:33/51/70` | 字段名与缺省值随数据走，`AnimationCodec` 只剩容器职责 |
| 序列化容器 | `io/AnimationCodec.java:104,220,281,286,305,330,369,427,450,465` | 顶层容器/轨道表/路径表的结构可留，需加 dataVersion 并改为"构造不可变值" |
| 文件与存档层 | `io/AnimationFiles.java`（全部）、`io/AnimationSavedData.java`（全部） | 前缀/后缀规整、目录层级（`/` + `.folder` 占位）、损坏数据跳过等逻辑与文档模型无关 |
| 布局引擎 | `layout/DockLayout.java`（序列化 165、恢复 218、布局 411、拖拽 624/659/669、落点 707/794） | 与动画数据无关的纯 UI 状态，自带版本号 |
| 绘制工具 | `theme/Draw.java`、（`render/FloatFill.java` 38 行）、`theme/Icons.java` | 面板自绘基础；`Draw.num`、`Draw.truncatedAt`、图层常量被广泛复用 |
| 视口抓帧 | `render/ViewportPipRenderer.java:47,127,135` | 主目标 → 离屏纹理 → GUI 贴回，含 alpha 修正；与数据模型无关 |
| 视口接管手感 | `core/editor/ViewportTakeover.java:41,72,130,180` | 鼠标锁定 + 与原版一致的灵敏度换算 + 光标回中 |
| 路径命中/反投影 | `core/editor/PathHandleDrag.java:76,138,207,320` | 命中优先级、固定深度平面反投影、`tanHalfFov` 回退（367-375） |
| 视口与路径渲染 | `core/cmd_camera/PathRender.java:82,86,98,113`（含 `SAMPLE_INTERVAL=0.25`、`MAX_SAMPLES=10000`）、`cmd_camera/CameraPose.java`（姿态值对象） | 渲染缓存与脏标记逻辑可留；`PathRender` 目前以静态 `DIRTY` 标记驱动 |
| 命令 DSL | `util/CommandBuilder.java`（被 `CameraCommand`/`CameraPlayCommand` 使用） | 与数据模型无关 |
| 信息文本 | `core/cmd_camera/info/CameraInfo.java:31-80` | 只读格式化，重构后可直接复用（把数据源换成快照） |

---

## 附录 A：skim 层文件速查（角色 + 公共入口）

| 文件 | 角色 | 关键入口 |
| --- | --- | --- |
| `core/editor/CameraEditorScreen.java`（1252 行） | 主编辑器 Screen：文件栏菜单、8 个面板的 `DockLayout`、模态（确认框 / 表达式窗口）、快捷键、存读档、撤销 | `CameraEditorScreen()`(117)、`(EditorContext)`(121)、`static open()`(1231，内含 `EditorUiHost.open(new BuiltinEditorSession(context))` 1240)、`extractRenderState`(230，每帧 `history.capture` 235)、`applyAnimation`(545)、`KeyClip`(671) |
| `core/editor/PathEditorScreen.java`（1035 行） | 路径专用编辑器 Screen：4 面板（视口 / 节点列表 / 节点详情 / 路径信息）、路径工具栏、路径存读档 | `PathEditorScreen(EditorContext)`(99)、`static open()`(1025)、`removePathNode`(290)、`newPath`(299)、`savePathTo`(339)、`applyPath`(431) |
| `core/editor/BrowserScreen.java`（937 行，abstract） | 浏览器类界面基类（列表 + 名称输入 + 新建文件夹 + 底部按钮 + 滚动），供文件/存档/管理三处复用 | `extractRenderState`(542)、`mouseClicked`(709)、`onClose`(928)；子类：`FileBrowserScreen.open(save,dir,defaultName,suffix,Consumer<Path>)`(`FileBrowserScreen.java:59`)、`StorageBrowserScreen.open(save,path,defaultName,Consumer<String>)`(`StorageBrowserScreen.java:39`)、`StorageManagerScreen.open()`(`StorageManagerScreen.java:41`)、`StorageDataScreen` |
| `core/editor/ExpressionEditorWindow.java`（1100 行） | 模态表达式窗口：公式输入 + 变量表（含增删）+ 函数表 + 实时预览 + 参数栏；**公式唯一的编译/校验点** | 构造(209)、`Parameters`(84)、`Subject{Track,Variable,Function}`(95)、`finished()`(297)、`update/render/mouseClicked/mouseReleased/mouseScrolled/keyPressed/charTyped`(247/303/588/604/608/630/660)、`confirm()`(847)、`formulaProblem`(813) |
| `core/editor/ViewportTakeover.java`（202 行） | 视口接管（鼠标锁定 + 自由飞行），三个界面共用 | `takingOver`(36)、`takeOver`(41)、`release`(59)、`tick`(72) |
| `core/editor/render/ViewportPipRenderer.java`（150 行） | 全帧抓取贴回的 PiP 渲染器 + 管线/注册事件 | `renderToTexture`(77)、`blitTexture`(103)、`State`(127)、`Registration.onRegister/onRegisterPipelines`(141/146) |
| `core/cmd_camera/CmdCamera.java`（146 行） | 相机插件：装配唯一 `CameraAnimation` 并每帧驱动 | `update`(47)、`animation/player/editor/info`(131-145)、`apply`(91) |
| `core/cmd_camera/CameraCommand.java`（498 行） | **客户端**命令：全部 `cmd_camera path ...`（创建/清空/改名/选点/增删/取点/设点/模式/自动平滑） | `register(RegisterClientCommandsEvent)`(27-63)、`editor()`(65) |
| `core/cmd_camera/CameraPlayCommand.java`（118 行） | 服务端命令：`cmd_camera play targets local|storage` / `stop`，只发意图包 | `register(RegisterCommandsEvent)`(42)、`playLocal`(57)、`playStorage`(69)、`stop`(90) |
| `registry/CameraPlaybackPayload.java` | 服务端→客户端播放包（`Kind`/`name`/`json`） | record(20)、`STREAM_CODEC`(38)、`local/storage/stop`(45/50/54) |
| `registry/ModPayload.java` | 包注册（playToClient） | `register`(12-19) |
| `registry/ModAttachment.java` | 空的 `AttachmentType` DeferredRegister | `ATTACHMENT_TYPES`(9) |
| `registry/ModArgumentType.java` | 空的命令参数类型 DeferredRegister | `register`(12) |
| `FreeCameraApiTripod.java` | 主入口（common）：只注册 attachment 与 argument type；common 配置被注释掉(15) | 构造(13) |
| `FreeCameraApiTripodClient.java` | 客户端入口：注册 `EditorConfig.SPEC`、配置界面扩展点、`CommandTrack.TYPE` | 构造(16-21) |
| `CameraAdditionConfig.java` | 区块加载半径配置（当前未注册） | `cameraChunkLoadRadius(ServerPlayer)`(21)、`(int)`(32) |
| `EditorConfig.java` | 客户端配置：布局串/下排比例/折叠面板/路径编辑器布局/深色模式/Modern UI 兼容/开发测试键 | `DEFAULT_LAYOUT`(16)、`DEFAULT_PATH_LAYOUT`(22)、`save()`(95) |
| `core/cmd_camera/PathRender.java`（410 行） | 世界内路径/控制点/序号渲染（静态缓存 + 脏标记），只在编辑界面打开时渲染 | `markDirty`(82)、`markDirtySelected`(86)、`canRender`(90)、`reCache`(98) |
| `core/cmd_camera/info/CameraInfo.java` | 只读信息文本（状态/时间/进度/轨道与路径摘要/键数/姿态） | `stateText`(31)、`timeText`(39)、`trackSummary`(53)、`keyCountText`(65)、`poseText`(73) |
| `core/cmd_camera/CameraPose.java` | 姿态值对象（位置/旋转/FOF + 两个有效性标记） | `set(Vector3fc,Vector3fc,float)`(55)、`set(CameraPose)`(64)、`positionValid/fovValid`(41/50) |
| `util/SplineUtils.java` | 贝塞尔/CR 求值与弧长（高斯积分） | 见第 10 节 |
| `core/editor/theme/Draw.java`（417 行） | 自绘主题与工具（配色、`num`、文字截断、图层、`beginFrame`、`darkMode/toggleTheme`） | 被所有面板与屏幕使用 |
| `core/Data.java` | `TripodData` 实现（state 位 + 控制方案），与动画数据无关 | `enableChunkLoader/disableChunkLoader/controlScheme`(14/20/26) |

## 附录 B：本报告未覆盖但可能相关的文件

- `api/camera/`（`ControlScheme`/`TripodData`/`TripodStates`）与 `core/control_scheme/`、`mixin/`（4 个 cmd_camera 渲染 mixin +
  2 个 control_scheme mixin）：与相机渲染/控制方案有关，不涉及动画文档模型。
- `core/editor/WorldViewScreen.java`（671 行）与 `core/editor/FileBrowserScreen.java`/`Storage*.java`：
  属 UI 宿主层，仅在本报告附录 A 记入口。
- `api/editor/` 的第三方后端（`EditorUiBackend` 实现）不在本仓库内，因此"外部界面如何写数据"无法从源码确认；
  只能确认它拿到的 `EditorSession` 就是下标 + 直接动作的契约（第 6.1 节）。
