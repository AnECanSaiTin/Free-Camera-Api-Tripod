# 相机动画与编辑器 GUI 阅读手册

面向要读/改这套代码的人：先讲清分层与数据流，再按包给出每个类的职责与关键实现，
最后给一条从零到能改的阅读顺序与扩展步骤。

- 运行环境：NeoForge 26.1.2 / Minecraft 26.1.2，Java 25
- 主 mod：id `free_camera_api_tripod`，根包 `cn.anecansaitin.free_camera_api_tripod`
- 编辑器是**自绘 GUI**（不依赖原版控件），除语言键外没有资源依赖
- 构建：`./gradlew build`；`libs/` 里的前置 jar 不入库（见 `.gitignore`），新克隆的仓库需要自备这些 jar，
  或者把依赖改成配置里注释掉的 maven 坐标

---

## 1. 分层总览

```
api/                          对外契约：数据模型 + 扩展点（不依赖 core）
├── camera/                   相机数据接口：TripodData / TripodStates / ControlScheme
├── animation/                动画数据模型
│   ├── Keyframe/Keyframec/TrackKey/Evaluator/EvaluateMode
│   ├── KeyField/KeyFields    五个可动态槽位的枚举与集合查询
│   ├── JsonSerializable      能把自己写成 JSON 对象的数据类型
│   ├── curve/                Curve / Curvec / KeyValues / WrapMode
│   ├── eval/                 求值层：CurveSampler / CurveSample / ExpressionScope / EvaluationGraph
│   ├── expression/           数值来源：Scope / Resolver / NumberSource / ValueSource /
│   │                         Constant / Formula / TrackRef / Variable / SymbolTable /
│   │                         CustomFunction / Expression
│   ├── path/                 Path / Pathc / PathNode / PathNodec / PathMode
│   └── track/                AnimationTrack / TrackType / CurveTrack
│                             TrackTypeRegistry / AnimationChannelRegistry
└── editor/                   界面扩展点：EditorSession / EditorUiBackend / EditorUiHost

core/                         内部实现（可以依赖 api，反之不行）
├── animation/io/             持久化：AnimationCodec / AnimationFiles / AnimationSavedData
├── cmd_camera/               运行时：命令入口、播放、路径渲染、编辑模型
├── editor/                   内置编辑器 GUI：屏幕、面板、控件、主题、布局
│   └── BuiltinEditorSession  把 EditorContext 适配成 api.editor.EditorSession
├── control_scheme/           操控方案：按键 → 相机位移的换算
└── Data.java                 相机数据的默认实现（实现 api.camera.TripodData）

mixin/                       注入 MC：渲染提交缓存扩展、按键与鼠标接管
registry/                    Neoforge 注册项：网络载荷、数据附件、命令参数类型
util/                        通用工具：样条求值/长度、命令构建
```

依赖方向：`expression` 在最底下（**只依赖 slf4j**，见 2.7），`animation` 根包与 `curve` 建在它上面，
`eval` 同时看得见 `expression` 与 `curve`，`track` 再用 `eval`；
`editor / cmd_camera / io` → `api`，`api` 不引用 `core`。
外部界面后端只允许 import 主 mod 的 `api.*`，禁止碰 `core.*`；
主 mod 完全不认识任何具体后端，反向依赖只有一条：主 mod 的 `EditorUiHost` 会被动接受注册。
新增公共数据模型请放 `api`，只在编辑器内部用的（面板、控件）放 `core.editor`。

### 数据流

```
F6 ──► CameraEditorScreen.open()
        │
        ├─ EditorConfig.MODERN_UI_COMPAT 开启，且 EditorUiHost 里有已注册的后端
        │     └─► 后端接管界面，内置界面不再打开
        │
        └─ 否则
              └─► 打开内置界面 CameraEditorScreen

两种界面拿到的是同一个 EditorSession 实例，所以状态与编辑进度互通
        │
        └─► EditorContext ──┬─► CameraEditorModel   编辑操作（增删键、拖路径点…）
                            ├─► CameraPlayer        时间推进 + 姿态求值
                            └─► CameraAnimation     唯一的数据模型实例

每帧：CmdCamera.update ──► CameraPlayer.evaluatePose ──► 相机修饰器（世界里的相机姿态）
                        └─► PathRender.render         ──► 在世界里画路径线/方块（只有编辑器打开时）

保存：CameraAnimation ──► AnimationCodec（JSON）──┬─► AnimationFiles（本地文件）
                                                └─► AnimationSavedData（随存档保存）
```

**状态是共享的**：`EditorUiHost` 把同一个会话实例交给后端，所以中途换界面（改配置重启、
或后端拒绝接管）不会丢正在编辑的内容——编辑进度全在 `EditorContext` 里，不在界面里。

---

## 2. 动画数据模型（`api.animation`）

### 2.1 关键帧

| 类型 | 职责 |
| --- | --- |
| `TrackKey` | 只读最小契约：`time()`。所有轨道上的键都实现它 |
| `Keyframec` | 只读关键帧：时间、取值、入/出曲柄的斜率与长度倍数、插值模式，以及"某个槽位的固定数值"。**`sealed permits Keyframe`**，且**不发放任何数值来源** |
| `Keyframe` | 可写关键帧，`final`；字段是「槽位 → 数值来源」（`EnumMap<KeyField, NumberSource>`）；静态工厂 `create/linear/step/hermite`；`set(Keyframec)` 覆盖式拷贝 |
| `KeyField` | 五个可动态槽位的**唯一真理来源**：枚举顺序 = 序列化顺序 = 面板行顺序，标签（`labelKey()`）、小数位（`decimals()`）、所属插值模式（`activeIn(mode)`）都挂在它上面 |
| `KeyFields` | `activeIn(mode)` 的集合版本（"该模式下有哪些槽位参与"），面板据此决定显示哪几行。单独一个类是因为 Java 不允许同名的静态与实例方法共存 |

`EvaluateMode`：`LINEAR` / `STEP` / `HERMITE`（`HERMITE` 即按贝塞尔求值）。

**取值 / 入出斜率 / 入出长度倍数这五个数值不再各有五个方法，而是按 `KeyField` 寻址**：

```java
key.constant(KeyField.VALUE);                 // 读固定数值（挂了公式时是它的回退值）
key.constant(KeyField.VALUE, 1.5f);           // 写固定数值
key.source(KeyField.VALUE);                   // 读来源本身（NumberSource）
key.source(KeyField.VALUE, new Formula("t*2", 1.5f));
key.formula(KeyField.VALUE, "t * 2");         // 挂公式；空串表示不挂
key.sources();                                // 五个槽位来源的不可变快照（复制粘贴用）
```

`value()` / `value(float)` / `inSlope()` / `inSlope(float)` 这一组仍在，是 `constant(field, …)`
的薄转发，只为调用点读得顺——**不是第二套字段入口**。

**写数值会清掉该槽位原有的公式**（`constant(field, v)` 总是换成新的 `Constant`）。
这是刻意的取舍：原来 `withConstant` 的语义是"只改回退值、公式保留"，但那要求来源自己可变、
只能就地改，正是它让"改个数要换对象"这件事漏到接口上。现在"公式会不会被弄丢"从**数据层的隐式行为**
变成**调用点显式选择**——数值输入框走 `constant(field, v)`，公式编辑窗口走 `formula(field, text)`。

**时间不进值源**：公式本来就是按时间求值的，时间自己再挂公式只会绕回自己。

四个插值字段就是两端各一条贝塞尔曲柄：**斜率给方向，长度倍数给长度**——
曲柄长度 = 这段时长的 1/3 × 长度倍数（默认 1，即基准长度）。没有单独的「加权模式」开关，
权重总是参与，曲柄在曲线图上能拖多长就生效多长（上限见 2.2）。
只有左键为 `HERMITE` 的那一段才读这四组数（见 `KeyField.activeIn`）。

### 2.2 曲线 `curve/Curve`

一条 float 曲线，内部是升序的 `Keyframe` 列表。

- `key(time, value)` / `key(Keyframe)`：按时间二分查找插入或覆盖，返回索引
- `moveKey` / `removeKey` / `smoothTangents`：编辑操作；`smoothTangents` 在两端用差分、中间点用 Catmull-Rom 斜率，
  只改斜率、不动曲柄长度
- `evaluate(time, KeyValues)`：先按 `preMode` / `postMode`（`WrapMode`：CLAMP / LOOP / PING_PONG）把时间映射进有效区间，
  再取相邻两键按左键的 `EvaluateMode` 插值。`HERMITE` 分支按**曲柄位置**求三次贝塞尔：
  两端的曲柄落在 `端点 + 曲柄长度 × (1, 斜率)` 处，曲柄长度由关键帧上的长度倍数决定（`Curve.handleLength` 夹在 0~1.5 倍）。
  因为横坐标也是自由度，求值要先按时间反解曲线参数（牛顿迭代，见 `Curve.solveParameter`）；
  两侧曲柄长度之和不超过整段时长，横坐标才单调、解唯一，这正是长度上限取 1.5 倍的原因
- **曲线是纯数值插值**：五个数值一律从 `KeyValues` 读，曲线不认识公式，也不认识求值环境
  （见 2.2.1 的 `KeyValues` 与 2.8 的 `CurveSampler`）
- **健壮性处理**：相邻键时间相同、线段长度退化、切线为无穷时直接取左值，避免 `0/0` 产生 NaN 污染整条通道
- 命中位置用 `lastIndex` + 方向标记做局部近似，退化时才回到二分

`curve/Curvec` 是它的只读视图（`evaluate` / `size` / `key`），只读取值的场景优先用视图。

### 2.2.1 数值读取器 `curve/KeyValues`

```java
public interface KeyValues {
    float value(int index);      // 第 index 个键的取值
    float inSlope(int index);    // 入曲柄斜率
    float inLength(int index);   // 入曲柄长度倍数
    float outSlope(int index);
    float outLength(int index);
}
```

曲线只问"第 index 个键的某个数是多少"，**不管这几个数是哪来的**。实现只有一个，
就是包内的 `eval.CurveSample`——它按 `(键, 槽位)` 惰性缓存解析结果：
`scope == null` 时公式一律算不出来，于是走回退值；否则按该时刻的公式求值。
"有公式 / 没公式 / 有环境 / 没环境"四种组合因此收敛在同一处，不再各写一个读取器类。

时间与插值模式不在读取器里——它们不是可动态的字段，曲线直接读键本身。

### 2.3 取曲线：只有一份

曲线**只存一份**，就在 `CurveTrack` 里。按属性名取它的入口是 `CameraAnimation#curve(id)`
（只读接口 `CameraAnimationc#curve(id)` 同样有），没有第二份集合要同步——以前 `curve/Clip`
是一份与轨道表平行的容器，增删改名都要维护两处，已经删掉。

取值一律走 `eval.CurveSampler`，它把"按 `Scope` 解析关键帧"与"曲线本身的纯数值插值"接起来。

### 2.4 路径 `path/`

- `PathNodec`：只读节点（位置、入/出切线、`PathMode`、是否自动平滑）
- `PathNode`：可写节点；`inTangent/outTangent` 在 `smooth` 开启时互为反向，保证拖动一侧另一侧跟随；
  `smooth(true)` 打开开关的瞬间会把出切线对齐到入切线（出 = -入），`restoreSmooth` 只改开关、不动切线（供反序列化）
- **路径是纯几何**：坐标与切线就是固定向量，节点不参与表达式求值。相机随时间变化由曲线通道与路径进度负责，
  节点形状不做动态，弧长与渲染因此始终有确定的值
- `PathMode`：`LINEAR` / `BEZIER` / `CATMULL_ROM`，**由每段起点节点的模式决定该段插值方式**
- `Path`：节点表 + **弧长表**（每段长度、累计长度）
  - `node/insertNode/removeNode/moveNode` 后按受影响范围重建弧长表（`updateArcLengthTable(begin, end)` 或整体重建）
  - `evaluate(distance, dest)`：按弧长二分定位分段 → 归一化参数 → 按模式调 `util/SplineUtils` 取点
  - `evaluate(dest, progress)`：按 0~1 进度取点（内部乘总长）
  - 退化保护：线段长度为 0 时参数取 0，索引越界时夹到有效分段
- `Pathc`：只读视图（`name/size/node/totalLength/evaluate`），渲染等只读场景用它

### 2.5 轨道抽象 `track/`

编辑器按能力分层依赖：时间轴只认 `AnimationTrack`，曲线图与关键帧面板要改曲线时才认 `CurveTrack`。
以后加事件轨道 / 特效轨道不必改时间轴，曲线图会对它们显示"无可编辑曲线"：

- `AnimationTrack`：`type/id/label/color/duration/keyCount/key/addKey/removeKey/moveKey`。
  **只放所有轨道都成立的成员**：曲线、指令、事件这类专属能力不进接口——需要"经过即触发"就实现 `TickTrack`，
  需要交出底层曲线就在自己的实现里提供（`CurveTrack.curve()`），调用方按能力判断。
  `addKey(time, Scope)` 是带求值环境的插键入口（默认实现转给 `addKey(time)`，
  即"拿不到环境就按固定数值插"），编辑器一律走带环境的版本
- `CurveTrack`：把 `Curve` 适配成轨道的默认实现；插键会继承前一个键的插值模式；
  取值与取键都走它自己暴露的 `curve()`
- `TickTrack`：可选能力接口，`advance(fromTime, toTime)` 由播放器每帧带着推进区间调用（命令轨道即此类）
- `JsonTrack`：可选能力接口，`writeKeys()` / `readKeys(JsonArray)` 由轨道自己决定键的存档形态（不实现就不落盘）
- `RenamableTrack`：可选能力接口，`rename(id)` 让时间轴可以就地改轨道标识（曲线轨道的标识与相机属性绑定，不实现）
- `TrackType` + `TrackTypeRegistry`：轨道类型元数据与注册表（当前注册 `curve` 与 `command`）
- `AnimationChannelRegistry`：**通道元数据**——显示名、时间轴颜色、新建时的默认值、所属折叠分组
  （`position` / `position.x~z` / `rotation.x~z` / `fov` 在静态块注册，未注册的属性回退为"属性名 + 由名字派生的稳定颜色"）

### 2.6 顶层模型 `CameraAnimation`

由两部分组成：曲线（就在 `CurveTrack` 里，见 2.3）+ `Path`（位置通道驱动的三维路径）+ 扩展轨道列表。

- 通道常量：`CHANNEL_POSITION`（沿路径弧长）、`CHANNEL_POSITION_X/Y/Z`、`CHANNEL_ROTATION_X/Y/Z`、`CHANNEL_FOV`
- `MotionMode`：`PATH`（位置取自路径）与 `COORDINATE`（位置取自三个坐标通道），**互斥**
  - `motionMode(mode)` 切换时重建对应通道（切到坐标会丢掉位置距离键，反之丢掉 x/y/z 键），不自动补键
  - `restoreMotionMode(mode)` 只改标记，供反序列化使用
- `DistanceMode`：`ABSOLUTE`（格）与 `PERCENT`（0~1 占全程）
  - `distanceMode(mode)` 会把已有位置键**换算**到新口径；`restoreDistanceMode` 只改标记
  - `distanceToLength(value)`：把通道取值换算成弧长，播放器取点前调用
- 轨道顺序就是 `LinkedHashMap` 的顺序（序列化按它写出），`moveTrack` / `moveTracks` 用于拖拽排序
- **轨道表**：曲线通道与扩展轨道（指令、事件、特效…）共用一份有序表，顺序即时间轴上的显示顺序、
  也是序列化写出的顺序，`moveTrack` / `moveTracks` 的拖拽排序对两类轨道一视同仁
- 扩展轨道：`addExtensionTrack(track)` 插入到末尾、`removeExtensionTrack(id)` 移除、
  `renameExtensionTrack(id, newId)` 改名（只对实现 `RenamableTrack` 的轨道生效，改的是标识本身）；
  `extensionTracks()` 是它的只读过滤视图
- **通用曲线轨道**：`addCurveTrack(baseName)` 建一条曲线轨道（名字是 `baseName` 加序号，界面传进来的是
  本地化过的「曲线」这类词，所以叫「曲线1」「Curve1」），它不对应任何相机属性，专门给变量读写用；
  `renameCurveTrack(id, newId)` 改名时会把绑定在这条轨道上的变量一起改指向，
  相机自身的属性通道（`isCameraChannel`）不允许改名
- 实现了 `JsonTrack` 的轨道随动画一起进 JSON；`copyFrom` 会整体替换轨道表（撤销栈与读档都依赖这一点）
- `symbols()`：动画级的符号表，变量与自定义函数都在它身上（见 2.7）：
  `variables()` / `variable(name)` / `addVariable()` / `removeVariable(name)` / `renameVariable(...)`，
  以及 `functions()` / `function(name)` / `addFunction()` / `removeFunction(name)` / `renameFunction(...)`。
  名字唯一性、轨道改名 / 删除时的引用重定向与解绑也都归它管
- **判环不在符号表上**：`evaluationGraph()` 给出当前的求值依赖图，`cycle()` 给出环上的节点名
  （变量名与轨道 id 混在一起）。放在动画上是因为环可以跨过轨道边界，而只有动画同时看得见
  符号表与轨道表——符号表看不见轨道，判不出真正的环（见 2.7 与 2.8）
- `copyFrom(other)`：读档时**原地替换**内容——动画实例被播放器与编辑器各处持有，不能换对象；符号表也一并换成副本
- `CameraAnimationc`：只读视图（`name/duration/motionMode/distanceMode/path/tracks/curveTracks/extensionTracks/curve/symbols/distanceToLength`）

### 2.7 数值来源、表达式、变量与函数 `expression/`

让"数值"可以随时间变化：一个数值既可以是一个固定的数，也可以挂一条公式，播放时按当前时间算出来。

这是**最底层的一个包**。它只依赖 slf4j（`SourceJson` 读到关键帧字段上出现 `{"track": …}`
这条非法通路时要记一条警告），除此之外不碰任何 mod 自己的类——这一层能脱离游戏单测。

#### 两态与三态：`NumberSource` / `ValueSource`

整套设计的支点是**一个字段就是「槽位 + 来源」这一对**，而"来源"分两层表达：

| 接口 | 态 | 实现 | 用在哪 |
| --- | --- | --- | --- |
| `NumberSource` | 两态 | `Constant`（固定值）、`Formula`（公式 + 回退值） | **关键帧字段** |
| `ValueSource` | 三态 | 上面两个，再加 `TrackRef`（某条曲线轨道的读数） | **变量的取值来源** |

**`TrackRef` 有意不实现 `NumberSource`**，所以"把轨道读数挂到关键帧字段上"在**编译期**就是错的，
不必靠注释或运行期检查约束。这是设计里那条类型级安全边界的落点。

三个实现都是**不可变 record**，因此：

- `copy()` 就是 `this`——**拷贝即快照**，装进别的对象时不必复制（`Keyframe.sources()`
  返回的那份快照可以直接长期留着，就是复制粘贴的实现方式）
- 编译结果**不进 `Formula`**：`Expression` 内部按公式文本驻留编译结果（它本来就有缓存），
  `Formula.evaluate` 每次查一次表。于是序列化与拷贝不再带着语法树走，
  "同一段文本只解析一次"也从"每个字段各存一份"变成"全局一份"

求值约定：

- `evaluate(scope)`：算不出来返回 NaN；作用域为 `null` = **静态求值**，公式一律算不出来
- **`evaluateOrFallback(scope)` 是求值链上唯一的回退点**（`NumberSource` 上的默认方法）：
  evaluate 失败就用 `constant()`，`constant()` 也是 NaN 时用 0。求值、变量、界面三处都调它，不再各写一遍
- `constant()`：该来源携带的固定数值——常量就是它本身，公式是它的回退值，轨道读数没有（NaN）
- `isFormula()`：界面问"这个槽位是数值还是公式"只需要这一个判断，不必到处 `instanceof`，
  求值层因此完全不需要认识具体的实现类型
- `trackId()`（`ValueSource` 上）：轨道引用返回其 id，数值来源返回 null

**三个内置变量**：`t`（当前时间）、`p`（播放进度）、`wt`（世界时间），在 `resolve` 里先于用户变量被认出来，
名字由 `ExpressionScope.BUILTIN_VARIABLES` 列出（既不让用户取重名，也直接列在变量面板最上面，
显示为「变量名 + 类型 + 当前取值」三列）。后两个都归一化到 0~1，
世界时间由 `CmdCamera` 每帧写进 `CameraPlayer.worldTime()`——播放器本身不认 Minecraft，换算留在外面。

**内置函数**由 `Expression.BUILTIN_FUNCTIONS` 列出（表达式编辑器直接用这份清单生成可插入的模板，
不必两处同步）：`min/max/clamp/lerp/smoothstep`、`abs/sign/floor/ceil/round/sqrt`、`pow/mod`、
`sin/cos/tan/asin/acos/atan/atan2`、`exp/log/log10`，以及 `random()` 与 `random(a, b)`。
参数个数不对在**编译期**就报错（整条公式算不出来），不会拖到播放时才发作。

**自定义函数**（`CustomFunction`）是动画里的一份小定义：名字 + 参数名 + 函数体文本。
名字不在内置清单里时，调用节点在**求值时**向 `Resolver.function(name)` 查一次
（`ExpressionScope` 从动画的函数表里取），所以函数随时可加、改完立刻生效，也不必去清编译缓存。
参数按值绑定：实参先在调用处求值一次，再按参数名喂进函数体；函数体里还能调用别的自定义函数
（查询转回外层作用域），但不支持递归——表达式没有条件写法，写不出终止条件。
形参个数与实参个数不符时返回 NaN（退回固定数值），不会抛异常。

三者可以这样记：**变量是"值"的名字，函数是"算法"的名字，轨道是"曲线"的容器**——
公式里都能引用，而函数只是把一段公式包起来复用。

三条关键约定：

- **一次求值内每个变量只算一次**：时间是切帧时定下的，变量值在这一帧内不会变，`resolve` 的结果缓存进 Map。
  同一条公式里写 3 次、或一帧内几十个字段引用同一个变量，都只算一次
- **变量读轨道读的是这一帧的真值**：轨道按当前作用域求值，轨道上的公式照常参与，
  所以"变量绑的轨道"与"该轨道作为属性播放"给出**同一个数**。
  代价是变量与轨道构成一张互递归的图，必须由依赖图保证无环（见 `EvaluationGraph`）
- **成环在写入期就被拒绝**，不是求值时静默降级。见下面的依赖图

#### 值源的行为在它自己身上

`Constant`（`value()`）、`Formula`（`expression()` / `fallback()` / `broken()`）、
`TrackRef`（`trackId()`）各自实现自己的行为，外面不必为一个"该按哪种来源处理"的 switch 操心；
求值层只认 `NumberSource` 这两个方法。

#### 依赖图 `eval.EvaluationGraph`

**这是判环的唯一权威**，放在 `eval` 是因为它要同时看得见 `expression` 与 `curve`：

- 节点是**变量与轨道两类**，边是"公式里按名字引用到了谁"
- 轨道上的公式引用的变量名要**展开**到变量节点上，所以：
  `deps(V) = 公式里引用的变量 ∪ deps(V 绑的那条轨道)`，`deps(轨道) = 该轨道所有键、所有槽位公式里引用的变量`
- 图里存的是**零件**（变量的公式引用、变量绑了谁、每条轨道的引用），完整依赖现算——
  这样"如果这样改会怎样"的试算就是换掉零件重算一遍，准确且不必猜哪些下游要跟着变
- 判环用 Kahn 拓扑：入度为 0 的先定值，**一轮下来没被定值的节点就都在环上**，
  正好是界面要报的那串名字（`cycleText()` 串成 `A → B`）
- `allowsTrackFormula(trackId, formula)` / `allowsVariableSource(name, source)`：
  **只试算、不改动任何数据**，供界面在写入前拦一道

为什么不能只装变量（旧 `VariableGraph` 的做法）：环可以跨过轨道边界——
`V` 绑轨道 `fov`，而 `fov` 上的键又挂了引用 `V` 的公式。只看变量之间的引用看不见这条路径。

#### 变量 `Variable`

变量 = 名字 + 一个**可以为 null** 的 `ValueSource` + 一个默认值。来源三选一：固定值、公式、某条曲线轨道；
**没绑来源时取默认值**（`evaluate(scope)` 直接返回 `defaultValue`）。

#### 表达式求值 `Expression`

- 递归下降解析**一次**：解析结果是一棵由闭包拼起来的语法树 `Expression.Formula`，按公式文本驻留缓存（上限 512 条），求值不再扫描字符串
- 支持：`+ - * / %`、括号、一元正负、变量、`min/max/sin/cos/random`
- 失败一律返回 NaN：语法错误、参数个数不对在**编译期**就返回 null；未知变量不抛异常，让 NaN 顺着算术传播
- `validName` / `identifiers` / `references` / `valid`：名字规则与依赖分析用的字符串工具（与解析器共用同一套字符规则）

#### 设计要点

- **公式挂在数据上，不挂在求值链上**：字段持有来源，求值链只把它解析成一个数（见 2.8）
- **结构性问题写入期拦、数值问题运行期回退**：会成环的公式根本写不进去（确认时拒绝并给出环上节点）；
  `0/0` 这类算不出来的仍然静默退回固定数值，播放不会因为一条写错的公式而失效
- **新增一个可动态槽位只改枚举**：字段按 `KeyField` 寻址，加一个槽位就是在枚举里加一行
  （标签、小数位、所属插值模式都挂在那里），不再需要在两个类里各补一组读写方法

### 2.8 求值层 `eval/`

把"按公式算出一个数"与"曲线怎么插值"分成两件事，`Curve` 因此不必认识表达式。
这里只有四个类，**对外入口只有 `CurveSampler` 一个**：

| 类 | 职责 |
| --- | --- |
| `CurveSampler` | **求值的唯一入口**：把"按 `Scope` 解析"与"曲线插值"接起来；从动画建环境的工厂也在这里 |
| `CurveSample` | **包内实现**：把「键 + `Scope`」解析成 `KeyValues`，结果按 (键, 槽位) 惰性缓存。这是全仓唯一需要知道"来源只存在于可写类上"的地方 |
| `EvaluationGraph` | 变量 + 轨道的依赖图与判环（见 2.7），写入期拦环靠它 |
| `ExpressionScope` | `Scope` + `Resolver` 的实现：内置变量、变量缓存、轨道读数缓存都在这里 |

#### 环境接口：`Scope` / `Resolver` / `TrackLookup`

```java
// expression 包
public interface TrackLookup {                 // 按 id 取轨道读数；不存在返回 NaN
    float track(String id);
}

public interface Resolver extends TrackLookup { // 名字解析
    float resolve(String name);
    @Nullable CustomFunction function(String name);
}

public interface Scope {                       // 求值环境：这一帧是什么时候 + 名字怎么解析
    float time();
    float progress();
    float worldTime();
    long version();                            // 帧版本：每换一帧 +1，缓存按它失效
    Resolver resolver();
    default String resolving() { return null; } // 当前正在求值的变量名（断言钩子）
}
```

**三者分开是有意的**：环境是"这一帧是什么时候"，解析是"公式里的名字是什么"，
而只画一条曲线的人只需要最小的 `TrackLookup`。
`ExpressionScope` 把 `Scope` 与 `Resolver` 一起实现，`resolver()` 返回它自己——
解析变量要读轨道，读轨道又需要当前时间，两者在这个实现里天然合一。

`Scope` 上原来那个 `evaluate(expression)` 便捷方法回到了 `Expression`：
求值需要解析器，解析器从 `scope.resolver()` 拿。

**建环境有两个入口，按用途选**：

- `ExpressionScope.of(animation, time, worldTime)` —— 返回具体类型，**要跨帧复用的地方用它**：
  `CameraPlayer` 与 `EditorContext` 各持有一份，之后每帧调 `frame(...)`
- `CurveSampler.scope(animation, time, worldTime)` —— 返回接口类型的**一次性**环境，
  给"只求这一次"的调用方（曲线图、插键）。内部就是转调上面那个

两个都留着是因为用途不同、且都不成环：`ExpressionScope.of` 已经是静态工厂，
所以 `eval` 并没有"为了建环境而依赖 `track`"这回事；
`CurveSampler` 那边多给一份，是因为它是**对外唯一入口**——调用方不必知道
`ExpressionScope` 这个名字，也不必持有具体类型。

> 一条要守住的边界：`ExpressionScope.of` 认 `CameraAnimationc`（建环境要看符号表与曲线表）。
> 这是设计里承认的既有耦合——`CurveSampler.sample` 同样认它。
> 只要 `eval` 不认识 `track` 包，`track → eval → track` 这种真环就不会出现。

**作用域是跨帧复用的**：播放器与编辑器各持有一份，每帧调一次 `frame(time, progress, worldTime)`
换到当前时刻（版本号 +1、清掉上一帧的缓存），不必每帧新建——原先每帧要分配六个集合。
缓存因此**不能只按对象身份失效**：`CurveSample` 除了比曲线、键数、作用域对象，
还要比一次版本号（只比身份会漏掉"同一对象换了一帧"，只比版本会漏掉"两个不同对象恰好版本相同"）。

#### `CurveSampler` 的入口

- `sample(curve, time, scope)`：按作用域取值，复用内部缓存。挂了公式的键按该时刻算
- `sampleOnce(curve, time, scope)`：同上但不复用缓存，适合插键这类偶发取值
- `sampleStatic(curve, time)`：只读固定数值。**只用于"本来就没有求值环境"的场景**
  （命令插键、读档），不是拿来断开变量与轨道之间回边的
- `sample(animation, time, evaluator, scope)`：一次取多条通道，交给 `Evaluator` 组装
- `scope(animation, time, worldTime)`：建一份求值环境
- `clear()`：只在"作用域没换、却想让曲线重新解析"时才需要

**播放、曲线图、插入关键帧、界面预览一律走 `CurveSampler`**，于是"画面上看到的"与"播放出来的"
永远是同一条曲线——改造前曲线图走的是不带作用域的求值，画出来的其实是公式的回退值，与播放结果对不上。

#### 变量读轨道：读的是这一帧的真值

`ExpressionScope.track(id)` 按**当前作用域**求值（`CurveSample.at(curve, this)`），
轨道上的公式照常参与，所以变量读到的数与"该轨道作为属性播放"完全一致——
旧的"轨道一律静态求值"那条规则带来的取值口径歧义就此消失。

三个实现细节：

- **缓存在本类自己身上，不共用 `CurveSampler`**：公式求值可能撞上主采样器算到一半的那条曲线。
  按 `(曲线, 帧版本)` 缓存，`frame()` 时清空
- **递归终止靠写入期的无环保证**，不在每次求值时判环
- **兜底**：万一有编辑路径绕过校验形成了环，`visiting` 栈与 `MAX_TRACK_DEPTH` 让求值退成 NaN
  并记下环（`cycle()`），而不是栈溢出

#### 依赖方向

`eval` → `expression`、`eval` → `curve`、`eval` → `animation`（建环境要用 `CameraAnimationc`）。
`curve` 不认识 `eval`——`KeyValues` 放在 `curve` 包里就是为了这个。

缓存的两个约定：

- **惰性**：一次求值只解析落在区间两端的那两个键，不整条曲线解析
- **自动失效**：换曲线、键数变化、换作用域、同一个作用域换帧（版本号变了）都会清空


---

## 3. 求值与播放（`core.cmd_camera`）

| 类 | 职责 |
| --- | --- |
| `CmdCamera` | 相机插件入口：持有 `CameraAnimation` / `CameraPlayer` / `CameraEditorModel` / `CameraInfo`，`update(partialTicks)` 每帧把姿态应用到相机修饰器 |
| `playback/CameraPlayer` | 只做时间推进与姿态求值：`tick(deltaSeconds)`、`evaluatePose(dest)`、`play/pause/toggle/stop/seek/speed/loop`；`tick` 会把推进的时间区间分发给实现了 `TickTrack` 的扩展轨道 |
| `CameraPose` | 位置 + YXZ 旋转 + FOV 的结果结构，带"该分量是否有效"标记（无键的轴不接管，沿用原相机值） |
| `edit/CameraEditorModel` | 编辑状态与操作：视图模式、自由姿态、选中轨道/关键帧/路径节点，增删改键与路径点、录制相机姿态 |
| `edit/Selected` | 路径节点的选中项（节点 / 入切线 / 出切线） |
| `info/CameraInfo` | 把动画与播放状态整理成可直接显示的只读文本（状态栏、视口叠加、摘要） |
| `PathRender` | 只在编辑器打开时把路径线、节点方块、控制点画进世界；带脏标记缓存，几何变化时重建 |
| `CmdCameraKeyMapping` | 快捷键：**F6** 打开编辑器、**F7** 播放/暂停、**F8** 停止、**F9** 打开路径编辑器（均限游戏内） |
| `CameraCommand` | 客户端命令注册 |

`CameraPlayer.evaluatePose` 的关键分支：每帧把持有的那份 `ExpressionScope` `frame(...)` 到当前时间，
再用 `CurveSampler` 取值，挂了公式的关键帧由此按当前时刻算出来（路径是纯几何，不接求值环境）。
采样器在播放器里是一个实例字段，同一帧内每条曲线的关键帧只解析一次。
坐标模式下逐轴判断"有没有键"，有键才写该轴；路径模式下先 `distanceToLength` 再 `Path.evaluate`，并对非有限值做兜底。

---

## 4. 持久化（`core.animation.io`）

### 4.1 `AnimationCodec`——JSON 编解码

**本类只做容器**：顶层字段、轨道表、变量与函数表、路径表在这里；每个数据类自己怎么写 JSON
由它自己负责（`JsonSerializable`，例如 `Keyframe.write()` / `Keyframe.read(JsonObject)`、
`Variable.write()` / `Variable.read(JsonObject)`）。格式细节跟着数据走，读写写在一起。

- 顶层字段：`name`、`motionMode`、`distanceMode`、`tracks`（轨道数组）、`variables`（变量数组）、
  `functions`（自定义函数数组）、`path`
- 轨道数组按动画里的轨道顺序写出（拖拽排序会反映到文件里），两类轨道混排、用 `type` 区分：
  - 曲线轨道：`type` = `free_camera_api_tripod:curve`，字段为 `id`（相机属性名）、`preMode`、`postMode`、
    `keys`（每个键含时间、取值、两条曲柄的 `inSlope` / `outSlope` 与 `inLength` / `outLength`、`evaluateMode`）
  - 扩展轨道：`type` = 该类型 id，字段为 `id`（轨道标识）与 `keys`（键的字段由轨道自己定，见 `JsonTrack`）
- **一个数值**有两种写法：固定值直接写成数字，公式写成 `{"expression": "…", "fallback": 1.0}`
- **`{"track": "fov"}`（轨道读数）只有变量读得到**：关键帧字段上出现它属于历史遗留的非法通路，
  类型上已经堵死（`TrackRef` 不是 `NumberSource`），格式上读到会按 `fallback` 降级成固定值
  并用 `LogUtils` 记一条警告
- 变量数组：每项含 `name` 与 `source`（数字 = 固定值 / 公式对象 / 轨道对象三选一）
- 函数数组：每项含 `name`、`parameters`（参数名数组，可为空）与 `body`（函数体文本）
- 读档按 `type` 分派：curve 走通道与曲线，其余查 `TrackTypeRegistry` 用工厂造实例再交回 `readKeys`；
  类型未注册、没有工厂、id 重复或不实现 `JsonTrack` 的条目跳过
- 路径是**纯几何**：`name` + `nodes`，节点的 `position` / `inTangent` / `outTangent` 是三个数字的数组，不含公式
- 反序列化一律宽松（字段缺失/类型错误/枚举名非法都回退默认值），只有整份 JSON 解析失败才返回 null
- **读档最后会验一次求值图**：带环的文件整体拒绝（返回 null）并记一条带环上节点的警告。
  环意味着求值会全程退回固定值，与其让用户面对一堆说不清的数字，不如当场拒绝
- **`tracks` 是权威集合**：JSON 里没出现的通道会在读取后被移除，避免构造动画时的默认通道残留
- 序列化只读数据：`animationToJson(CameraAnimationc)` / `pathToJson(Pathc)`

> **历史包袱已清**：旧文件里曲线键的 `inTangent` / `outTangent` / `inWeight` / `outWeight` /
> `weightedMode`（「切线 + 权重 + 加权模式」时代的写法）**不再兼容**，读到时按默认值处理。
> 需要迁移这类旧文件的话，得用能读它们的旧版本打开再另存一次。

### 4.2 `AnimationFiles`——本地文件

- 目录：游戏目录下 `camera_animations`（动画）与 `camera_paths`（路径）
- 后缀：`.animation.json` / `.path.json`；`withSuffix` 会先剥掉用户输入里残留的已知后缀再补齐
- 读严格校验后缀（后缀不符按读不到处理），所有 IO 异常在内部消化

### 4.3 `AnimationSavedData`——存档内数据

- `SavedDataType` 注册，落盘在存档 `data/free_camera_api_tripod/animations.dat`
- 两个复合标签 `animations` / `paths`，内容为 `全名 → JSON 字符串`
- **多层级**：全名用 `/` 分隔（如 `分镜/开场`），`listFolders` / `listFiles` 按前缀列出直接子项
- 空文件夹没有任何条目，`createFolder` 会写入 `.folder` 占位键把层级本身留在存档里（列条目时跳过它）
- `deleteEntry` 删单个条目，`deleteFolder` 删整个层级（连同其中的条目与占位键）
- 多人游戏或未进世界时所有静态方法安全返回空列表 / null

---

## 5. 编辑器 GUI（`core.editor` 内置 + `api.editor` 对外契约）

### 5.1 屏幕家族

| 屏幕 | 说明 |
| --- | --- |
| `CameraEditorScreen` | 主编辑器：顶部菜单（文件/编辑/视图/播放/帮助）+ 可拖拽停靠的面板布局；同时是 `F6` 的入口，自己渲染还是交给界面后端由它判断（见 5.8） |
| `PathEditorScreen` | 路径编辑器：默认「视口 / 节点列表 / 节点详情」三列 + 顶部路径工具栏；布局与主编辑器同款持久化（`EditorConfig.path_dock`） |
| `WorldViewScreen` | 世界内查看：面板全部收起，底部一条操作栏，左键进入环视（Esc 先退出环视再返回）；从路径编辑器进来时不放播放控制。底栏的「设置关键帧属性」在界面上就地弹出一个关键帧属性面板（同一个 `KeyframePanel`，拖标题栏可挪位置，再点按钮收起），不必退回编辑界面 |
| `BrowserScreen` | **浏览界面基类**：标题行 + 面包屑行 + 可选第二行 + 列表 + 底栏 + 提示行，以及选中/滚动/输入分派、新建文件夹、列表右键菜单；子类只实现数据与语义 |
| `FileBrowserScreen` | 资源管理器式的本地文件保存/打开：顶栏只有面包屑（点段跳转、段尾箭头选同级目录），列本地目录与符合后缀的文件 |
| `StorageDataScreen` | **存档数据中间基类**：层级、面包屑、条目避重名与建文件夹——浏览与管理两个界面共用 |
| `StorageBrowserScreen` | 存档内数据浏览（打开 / 保存）：条目支持 `/` 多层文件夹 |
| `StorageManagerScreen` | 存档数据管理：第二行切换动画 / 路径，建文件夹与「删除」移除选中条目（文件夹连同内容一起删）；**右键文件夹可「重命名」**（菜单里目前只有这一项） |
| `CameraScreens` | 判定"当前是否在相机编辑界面"，供渲染与输入分流使用（按屏幕实例判断，不受 init 顺序影响） |

> 这些界面都不暂停游戏（`isPauseScreen()` 返回 false）：取景、取点与播放预览都要在真实运行的世界上进行。

### 5.2 `EditorContext`——一切共享状态

屏幕与面板都通过它拿数据与公共服务：

- 数据：`animation()`、`player()`、`editor()`、`info()`
- 时间轴视图：`pixelsPerSecond`、`viewStartTime`、`snapEnabled`、`snapStep`、`majorTickStep()`、`snapTime()`、`formatTick()`
- 曲线图相关：`bezierSymmetric`（切线是否对称）
- 路径距离口径：`distancePercent()`
- 交互反馈：`notify(Component)` 状态行提示、`confirm(...)` 二次确认弹窗
  ——内置界面由屏幕统一绘制与派发；外部界面从 `EditorSession.pendingConfirm()` 取走自己画，
  确认时调 `confirmPending()`。为此 `ConfirmDialog` 的 `confirm()` 与 `message()` 是公开的
- 表达式：`openExpressionEditor(label, expression, subject, parameters, onConfirm)` / `expressionEditor()` / `evaluateExpression(expr)`
  ——`subject` 说明这条公式挂在谁身上（`ExpressionEditorWindow.Subject.Track` 给关键帧槽位、
  `Subject.Variable` 给变量来源、编辑函数体时给 null），窗口靠它判环；
  `parameters` 是编辑函数体时的参数栏（`ExpressionEditorWindow.Parameters`，普通公式编辑给 null）；
  表达式编辑窗口与二次确认一样是**模态**的，屏幕在最上层绘制并优先派发输入
- 复合动作：`chooseAndBindPath` / `switchToPathMode` / `switchToCoordinateMode` / `recordPathNode` / `syncFreePoseFromCamera` 等
  ——"先选文件、再确认、才改数据"这类流程都收敛在这里，面板只调一个方法

### 5.3 面板体系

- `panel/EditorPanel`（基类）：背景与标题栏、折叠、悬浮矩形与缩放抓手、内容区裁剪，
  统一把输入转发给子类钩子（`renderContent` / `contentMouseClicked` / `contentMouseDragged` / `contentMouseScrolled` / `contentKeyPressed`），
  并持有自己的右键 `ContextMenu`（渲染与事件由屏幕优先派发，保证菜单盖在最上层）
- 具体面板

| 面板 | id | 职责 |
| --- | --- | --- |
| `ViewportPanel` | `viewport` | 画中画显示整帧游戏画面、机位子标签页、预览/自由视角切换、接管鼠标飞行、世界内查看入口 |
| `AnimationPanel` | `animation` | 动画整体信息：名称、运动模式切换 |
| `PathInfoPanel` | `path_info` | 路径整体信息：名称、节点数、总长度 |
| `PathNodePanel` | `path_node` | 路径节点区：按模式显示入/出切线、自动平滑等 |
| `PathNodeListPanel` | `path_node_list` | 节点列表：添加/删除节点、上移/下移排序 |
| `PathNodeDetailPanel` | `path_node_detail` | 当前选中节点的详情与编辑 |
| `KeyframePanel` | `keyframe` | 选中关键帧的属性、插值模式、贝塞尔对称设置。五个可动态槽位**按 `KeyField` 循环生成**（时间单独一行，其余一行两个），所以"哪些槽位该显示"不必在这里再写一遍：标签、小数位、以及"只有贝塞尔才显示曲柄"的判据全挂在枚举上；非曲线键显示时间与轨道自带的内容（如指令文本） |
| `GraphPanel` | `graph` | 曲线图：取值曲线与贝塞尔曲柄的绘制与拖拽。曲柄的屏幕位置就是它的两个数据，上下左右都能拖——横向写长度倍数、纵向写斜率，鼠标拖到哪、曲线就变到哪。曲柄只能在自己那一侧伸缩（出侧朝右、入侧朝左），拖过关键帧就卡在最短长度而不是翻到对面；横向最长到基准的 1.5 倍（再长曲线会随时间折返），最短留一点余量免得斜率爆掉。**动态关键帧**（有字段挂了公式）画成蓝点，它两侧的曲线段转成青蓝虚线；底部图例说明点与线各自的颜色。顶部只有轨道名与「适配」：适配时上下左右都留余量，且选中关键帧时范围收缩到它和左右相邻各一个 |
| `TimelinePanel` | `timeline` | 时间轴：轨道行、折叠分组、播放头、键的拖拽与右键菜单（「插入轨道 ▸」里可选一条**通用曲线轨道**或某个扩展类型，通用曲线轨道可改名与删除；重命名、删除扩展轨道；名称列双击可改名） |
| `VariablePanel` | `variables` | 变量表：最上面三行是内置变量（`t` 当前时间、`p` 播放进度、`wt` 世界时间）——只读，按「变量名 + 类型 + 当前取值」三列展示，与下面的用户变量同一套列宽；下面是新建/删除变量、改名、选取值来源（固定值 / 公式 / 某条曲线轨道），实时显示取值；成环或算不出来（NaN）时**公式原文与取值一起转红**（取值列显示感叹号）并给出说明。绑轨道这条路不经过编辑窗口，所以判环在面板上自己做（`animation.evaluateGraph().allowsVariableSource`），会成环就拒绝并提示。末尾只剩一条命名规则提示 |
| `FunctionPanel` | `functions` | 函数表：每行是「名字 + 预览」，名字可直接改，预览写成 `(a, b) -> a + b`；**参数与函数体都在表达式编辑窗口里改**——点这一行的预览打开窗口（窗口第二行右端是参数输入框）。预览在函数体编译不过时转红。名字要能写进公式，且不能与内置函数重名 |

> 新增面板：继承 `EditorPanel`，实现 `layoutWidgets` 与 `renderContent`；
> 重建控件前必须 `widgets.clear()`（`WidgetHost` 不会自动清理，否则控件会叠加）。

### 5.4 布局 `layout/`

- `UiRect`：自绘 UI 的统一矩形结构
- `DockLayout`：上/下两排 + 排内单元（横向权重）+ 单元内纵向叠放（纵向权重）；
  面板标题栏可拖到任意单元的四侧重新停靠，也可转为悬浮窗口（带最小尺寸与级联偏移）；
  布局序列化为一段字符串存进 `EditorConfig`，串里带 `v=` 版本号，版本不符整串作废并回落默认布局
- **尺寸只有一处来源**：所有栏内按钮（顶栏 / 播放栏 / 底栏）取 `DockLayout.TOOL_BUTTON_HEIGHT`，
  面板内容区的行控件取 `EditorPanel.CONTROL_HEIGHT`（与前者同值）与 `EditorPanel.ROW_HEIGHT`（控件 + 2），
  各自按所在栏 / 行高居中。加新按钮时引用这两个常量，不要再写字面量，否则又会出现「同样是按钮，高度差一像素」
- 面板内容区的行一律对齐到内容区右边界（有滚动条的面板先扣掉滚动条宽度），
  多格平分时不整除的余数给最后一格，避免右侧留出几像素的空档

### 5.5 控件 `widget/`

| 控件 | 说明 |
| --- | --- |
| `EditorWidget` | 控件基类：矩形、可见/可用、悬停、焦点；`tooltip(...)` 设悬停提示，子类在自己 `render` 的末尾调 `renderTooltip(...)` 把它显示出来 |
| `WidgetHost` | 控件容器：焦点管理与事件分发（后加入的优先命中） |
| `ButtonWidget` | 按钮：**按下再抬起且指针仍在按钮上才触发**；默认文字色随主题取 |
| `LabelWidget` | 只读文本：一行文字 + 可选颜色 / `field` 底框 / `suffix` 行尾图标 / `background` 行底色（常态 + 悬停）/ `onClick` / 悬停提示。**面板与窗口里的只读内容一律摆它**——提示行、空态、只读取值、行首选中标记、列表行都用它，不要手绘 |
| `TextFieldWidget` | 文本输入：点击进入编辑并按落点定光标、双击全选、右键清空、回车提交、Esc 取消，编辑中左右键移动光标、Home / End 跳首尾；编辑中不被外部刷新覆盖 |
| `NumberFieldWidget` | 数值输入：拖拽/输入，用于坐标、FOV 等 |
| `ExpressionFieldWidget` | 数值输入 + 模式切换按钮：默认数值模式，可切成动态模式挂公式；动态模式下数值框变成预览框（公式 + 当前取值），点击打开表达式编辑窗口。它读写的是 **`NumberSource`（两态）而不是 `ValueSource`**，所以"把轨道读数挂到关键帧字段上"在编译期就是错的。时间这类不允许动态的字段仍用 `NumberFieldWidget` |
| `ContextMenu` | 自绘右键菜单：图标 + 文本、勾选项、分隔线、二级菜单（悬停展开且不会移开即消失） |
| `ConfirmDialog` | 二次确认弹窗（覆盖确认、模式切换确认等） |
| `BreadcrumbBar` | 路径面包屑：每段可点击跳转，段尾箭头展开**同级目录下拉**（可滚动、点空白收起），与资源管理器地址栏一致 |

> `ExpressionEditorWindow`（在 `core.editor` 下）是表达式编辑窗口（模态）：第一行窗口标题 + 实时求值结果，
> 第二行「属性：xxx」说明在给哪个数值写公式；下面左栏是公式输入区，右栏上为变量表（带 `+` / `−`，点名字插进公式）、
> 下为函数表（内置函数 + 自定义函数，点一下插入模板并把光标停进括号）。输入区上界与变量区顶部齐平、下界与函数表底部齐平，
> 三块区域都在内容超出时显示滚动条。它不自绘到面板里，而是由屏幕在最上层绘制并优先派发输入。
>
> **公式只在点「确定」时编译与校验一次**，编辑过程中不做任何实时校验：
> 「确定」始终可按，按下去才把问题一次说清。查三件事，按"用户最该先知道哪一件"排序：
> 1. **函数本身能不能用**：参数名写不进公式、函数体为空或编译不过
> 2. **语法**：公式编译不过
> 3. **结构**：换上去会不会闭合一个环（交给 `EvaluationGraph` 试算，不改动任何数据）
>
> 被拒的原因挂在标题栏右侧的红字上，**内容一改就消失**（比对被拒时的那份文本，
> 比在每个编辑动作里各清一次可靠——插入、退格、粘贴、剪切都改文本，但不必都记得这件事）。
> 标题栏在没有报错时显示的是"当前文本按这一帧算出来的值"，那只是预览、不是校验：
> 算不出来就照实显示「无法求值」。
>
> 编辑函数体时窗口会多出一条参数栏（第二行右端），**参数与函数体都在这里改**：
> 预览把每个参数一律当 1（`a + b` 显示 2），便于当单位量试算；参数的合法性也在点「确定」时一并检查。
>
> 两个列表的行都是 `LabelWidget`：底板铺满整行、管选中 / 悬停底色与点击，文字层叠在上面（不接点击，事件穿透回底板），
> 所以一行里能用多种文字颜色、可点区域又只有一处。行控件按条目数与滚动位置整批重建，
> 只撤换行、不动 `+` / `−` 与底部按钮（它们要保留「按下 → 松开」的跨帧状态）。

### 5.6 绘制与主题 `theme/`、`render/`

- `Draw`：全部配色与绘制辅助。配色是**可变静态字段**，`applyTheme` 整体赋值，`beginFrame()` 每帧按 `EditorConfig.DARK_MODE` 同步；
  文本绘制会按当前主题给文字附加阴影样式（浅色主题用浅灰阴影，深色主题沿用原版）
- `Icons`：复用字体的符号字形，不引入额外纹理
- **界面层级**：`Draw.LAYER_DOCKED` / `LAYER_FLOATING` / `LAYER_MENU`，屏幕在画每一层之前调 `Draw.layer(...)`。
  被省略号截断的文字登记时会带上当时所在的那一层（`textEllipsized` 自动带），悬停提示只查鼠标当前压住的那一层
  （屏幕用 `surfaceAt` 判断），所以被悬浮窗口或右键菜单盖住的文字不会弹出提示糊在最上面。
  宽度连省略号都放不下时直接不画、不登记，免得溢出到相邻控件上
- `render/FloatFill`：浮点坐标矩形填充（原版只接受整数坐标，曲线会被量化成阶梯）
- `render/ViewportPipRenderer`：把整帧游戏画面拷进离屏纹理再由 GUI 通道贴回视口（PiP）
- `ViewportTakeover`：锁定光标后的视角转向与 WASD 飞行，视口面板 / 世界内查看 / 路径编辑共用
- `PathHandleDrag`：视口里路径点与控制点的命中、屏幕位移↔世界位移换算与写回（用抓取时刻的视深平面反投影）

### 5.7 语言与配置

- 文本统一走 `EditorLang.t(key)`，语言键前缀 `free_camera_api_tripod.editor.`，文件在 `assets/free_camera_api_tripod/lang/{zh_cn,en_us}.json`
- `EditorConfig`：客户端配置，含布局串 `layout.dock`、下排高度、折叠面板、视口提示收起、深色模式、
  `layout.path_dock` / `layout.path_collapsed`（路径编辑器自己的一套布局）、
  `modern_ui_compat`（是否允许界面后端接管，默认开）、`dev.test_keys`

> **开发用测试按键**（`dev.test_keys`，默认关）：自动化脚本送不进 GLFW 的右键与滚轮，用按键顶上。
> 三个界面各自实现（相机编辑器 / 路径编辑器 / `BrowserScreen` 一套浏览界面），
> 且都必须在 `keyPressed` 的**最前面**处理——排在模态窗口或弹窗之后就会失灵。
> - `F9` 时间轴左移一秒（验证 0 秒处的左边界限制）
> - `F10` 在鼠标位置补一次右键（各种右键菜单）
> - `F7` / `F8` 在鼠标位置补一次滚轮上 / 下（列表滚动、时间轴缩放）
>   —— 用 F 系列而不是 PageUp / PageDown，后者送不进 GLFW
> - `F12` 全屏的世界内查看
>
> 位置取自 `lastMouseX/lastMouseY`（最后一次点击处），所以脚本要先点一下目标位置再按键。

### 5.8 界面后端（`api.editor`）

主 mod 不依赖任何界面框架。它只给出三个契约，让别人把界面接走：

| 类型 | 职责 |
| --- | --- |
| `EditorSession` | 一次编辑会话：只读模型 + 受控动作。外部界面只跟它打交道，碰不到主 mod 的内部实现 |
| `EditorUiBackend` | 界面后端：`openEditor(session)` 返回 true 表示已接管，false 就让下一个后端试；`priority()` 越大越先被问 |
| `EditorUiHost` | 后端注册表：按优先级排序，`open(session)` 依次询问；没有后端就直接返回 false |

`EditorSession` 的成员按用途分六组：

| 分组 | 成员 |
| --- | --- |
| 只读状态 | `animation` / `path` / `duration` / `selectedTrack` / `selectedKeyIndex` / `selectedPathIndex` / `pathMode` |
| 时间轴视图 | `pixelsPerSecond`（读写）/ `viewStartTime`（读写）/ `majorTickStep` / `formatTick` / `snapTime` |
| 播放 | `playheadTime` / `playing` / `togglePlay` / `stopPlayback` / `seek` |
| 选择与编辑 | `selectTrack` / `selectKey` / `selectPathNode` / `switchToPathMode` / `switchToCoordinateMode` / `recordPathNode` / `addKey` / `moveKey` / `removeKey` / `removeSelectedKey` |
| 二次确认 | `pendingConfirm` / `confirmPending` / `dismissPending` |
| 提示与关闭 | `notify` / `statusMessage` / `close` |

内置界面自己**不走**这套接口（它直接持有 `EditorContext`，少一层间接），
`BuiltinEditorSession` 只是把同一个 `EditorContext` 适配出去给外部用。
两边拿到的是同一个会话实例，所以状态与编辑进度天然互通。

**为什么共享的是数据而不是绘制**：各家界面框架的绘制接口互不兼容，能共用的只有时间↔像素
换算这类薄薄一层，会话上已经给全了。所以抽出来的是**数据与动作**，绘制由各后端自己实现。

---

## 6. 扩展指南

**加一条相机通道**
1. 在 `AnimationChannelRegistry` 静态块 `register(属性名, 语言键, 颜色, 默认值[, 分组])`
2. 需要参与求值时，在 `CameraPlayer.evaluatePose` 里加分支，或让外部通过 `Evaluator` 取值

**加一种轨道类型**（事件触发器、后处理特效等）
1. 实现 `api.animation.track.AnimationTrack`（`type/id/label/color/duration/key/addKey/removeKey/moveKey`）；
   属于"播放经过就触发"的一类（指令、事件、特效）再实现 `TickTrack`，播放器每帧把推进区间交给它；
   想让键进存档再实现 `JsonTrack`（自己决定 `keys` 数组里放哪些字段）；
   想支持时间轴上的就地改名再实现 `RenamableTrack`（标识即显示名，改名等于换标识）
2. 定义 `TrackType` 并在 `TrackTypeRegistry.register` 注册。`factory` 只在"能凭空造出一条空轨道"时才有意义：
   轨道身份不是属性名、键里存的不是浮点值的类型必须给（写成构造器引用即可），
   曲线通道那种由 `addChannel` 按属性名创建的轨道留 `null`。
   注册调用要放在能引用到实现类的模块入口（api 不依赖 core，内置的命令轨道就在主 mod 客户端入口注册）
3. 编辑器不用改：不提供工厂的类型不会出现在插入菜单里，给了工厂的类型会被时间轴的
   「插入轨道 ▸」子菜单列出，插入后出现在时间轴末尾，可加键、可在关键帧面板里编辑内容。
   内置示例：`core.animation.track.CommandTrack`（指令键 + 到点触发 + 以 `JsonTrack` 存 `time`/`command`）

**加一个面板**
1. 继承 `EditorPanel`，给出 `id`（语言键、布局串都按 id 索引）与标题
2. 实现 `layoutWidgets` / `renderContent`，事件用 `contentXxx` 钩子；重建控件前 `widgets.clear()`
3. 在 `CameraEditorScreen` 里创建并交给 `DockLayout` 管理（默认布局串与 `LAYOUT_VERSION` 同步更新）
4. 面板要展示的数据如果外部界面也该看到，顺手在 `EditorSession` 上开一个只读成员；
   只想给内置界面看的就不用管

**接入另一套界面框架（写一个界面后端）**
1. 新建独立的 gradle 子项目，依赖主 mod 的 jar，
   单向引用主 mod 的 `api.*`，不要碰 `core.*`
2. 实现 `EditorUiBackend`：`openEditor(session)` 里开自己的界面并返回 true；
   不打算接管时返回 false。多个后端按 `priority()` 从大到小被询问
3. 在 mod 的 `@Mod` 构造函数里 `EditorUiHost.register(...)`——注册时机要早于玩家按 F6
4. 界面的全部数据与动作都从 `EditorSession` 取。**别绕过它去直接读游戏状态**，
   否则内置界面里的改动外部看不到，两套界面会各说各话
5. 必须处理 `pendingConfirm()`：像切换运动模式这类动作只有确认后才执行，
   界面上不给出确认/取消的出口就等于按钮点了没反应

---

## 7. 推荐阅读顺序

1. **数据模型**：`api/animation/Keyframe → curve/Curve`，再 `path/PathNode → Path`
   ——先把"键怎么插值、路径怎么按弧长取点"读明白，后面都建立在它上面
2. **轨道与顶层**：`track/AnimationTrack → CurveTrack → TrackTypeRegistry`，再 `CameraAnimation`（关注两种模式与通道集合）
3. **持久化**：`AnimationCodec`（JSON 结构）→ `AnimationFiles`（本地）→ `AnimationSavedData`（存档 + 层级）
4. **运行时**：`CmdCamera`（谁持有谁）→ `CameraPlayer`（求值）→ `CameraEditorModel`（编辑操作）→ `PathRender`（世界渲染）
5. **GUI**：`EditorContext` → `CameraEditorScreen`（屏幕骨架、菜单、快捷键）→ `panel/EditorPanel` → 一个具体面板（建议从 `TimelinePanel` 入手）
   → `layout/DockLayout` → `widget/*` → `theme/Draw`
6. **外部界面契约**：`api/editor/EditorSession`（外部能读什么、能做什么）→ `EditorUiBackend` / `EditorUiHost`（怎么接进来）
   → `CameraEditorScreen.open()`（分流点）→ `BuiltinEditorSession`（内置界面怎么把自己适配出去）
7. **辅助界面**：`FileBrowserScreen` / `StorageBrowserScreen`（面包屑与列表）、`WorldViewScreen`、`PathHandleDrag`、`ViewportTakeover`

---

## 8. 容易踩的坑

- **控件生命周期**：面板 `rebuild` 前必须 `widgets.clear()`，否则控件叠加、点哪个都像没反应
- **动画实例是共享的**：读档只能 `CameraAnimation.copyFrom(...)` 原地更新，不能替换对象（播放器与面板都持有引用）
- **主题是可变静态字段**：不要缓存 `Draw.XXX` 到 `static final`，切主题后不会更新
- **键是可变共享对象**：`Curve.key(int)` 返回的是内部 `Keyframe`，直接改它等于改数据（这也是编辑生效的方式）
- **写数值会清掉该槽位的公式**：`key.constant(field, v)` 总是换成新的 `Constant`。
  要走"只改回退值、保留公式"就显式构造 `new Formula(f.expr(), v)`——
  数值输入框与公式编辑窗口各自走对入口，否则用户会看到"改个数公式就没了"
- **路径索引缓存**：改节点后必须让 `Path` 重建弧长表（用它的增删方法即可，不要绕过它们直接改内部表）
- **只读场景用只读视图**：渲染、信息展示优先用 `Pathc` / `Curvec` / `CameraAnimationc`，避免误改数据
- **取值一律走 `CurveSampler`**：它是求值的唯一入口。图省事用 `sampleStatic`，拿到的就是公式的**回退值**——
  画出来的曲线、插出来的键会和播放结果对不上，而这种偏差只在使用了公式时才出现，很难发现。
  `sampleStatic` 只留给"本来就没有求值环境"的场景（命令插键、读档）
- **作用域是跨帧复用的**：`CameraPlayer` 与 `EditorContext` 各持有一份，每帧 `frame(...)` 切到当前时刻。
  缓存按 `Scope#version` 失效，所以复用是安全的；
  但**自己实现一个可变作用域时，`version()` 必须真的每帧变化**，否则缓存不会重算。
  表达式编辑窗口是唯一例外：它每次 `ExpressionScope.of(...)` 新建一份
- **成环在写入期拦，不在求值期兜**：新增任何会改依赖图的编辑入口，都要过一遍 `EvaluationGraph`
  （挂公式走编辑窗口的确定、绑轨道走变量面板的判环、读档走 `AnimationCodec` 的最后一道校验）。
  漏掉一条，环就会绕过校验，然后表现为"整条链路莫名地全变成回退值"
- **公式的编译与校验只在确认 / 保存时发生**：不要在编辑过程中加实时编译或实时判环——
  那正是这次刻意去掉的（边打边报错会打断输入，而且判环要遍历整张图）。
  要提示就等确认，或者用 `Scope#resolving()` 这类廉价钩子
- **右键菜单的绘制顺序**：由屏幕在最后统一绘制，面板只持有；否则菜单会被其它面板盖住
- **会话的编辑动作都作用于"当前选中的轨道"**：`addKey` / `moveKey` / `removeKey` 没有 track 参数，
  外部界面调它们之前得先 `selectTrack(...)`；否则返回 -1 或 false，看起来像"点了没反应"
- **二次确认必须被呈现**：`switchToPathMode` / `switchToCoordinateMode` 这类动作只是设置了一个待确认项，
  真正执行在 `confirmPending()` 里。外部界面不画 `pendingConfirm()` 就等于永远执行不了
- **外部界面后端只能 import 主 mod 的 `api.*`**：一旦依赖 `core.*`，主 mod 内部重构就会把后端一起弄坏，
  等于把两边的发布绑死成一个
