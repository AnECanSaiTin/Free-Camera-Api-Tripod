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
│   ├── Keyframe/Keyframec/TrackKey/Evaluator/EvaluateMode/WeightedMode
│   ├── curve/                Curve / Curvec / Clip / WrapMode
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

**依赖方向**：`editor / cmd_camera / io` → `api`；`api` 不引用 `core`。
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
| `Keyframec` | 只读关键帧：时间、取值、入/出切线、入/出权重、加权模式、插值模式 |
| `Keyframe` | 可写关键帧（可变类；setter 返回自身可链式）；静态工厂 `create/linear/step/hermite`；`set(Keyframec)` 覆盖式拷贝 |
| `Keyframec` / `Keyframe` 的分工 | 与 `PathNodec` / `PathNode` 一致：读方只认只读接口，`Curve` 内部直接持有可变的 `Keyframe` |

`EvaluateMode`：`LINEAR` / `STEP` / `HERMITE`；`WeightedMode`：`NONE` / `IN` / `OUT` / `BOTH`。

关键帧还可以给取值 / 切线 / 权重**挂公式**（见 2.7）：`expression(DynamicField)` 读、`expression(DynamicField, String)` 写，
`value(ExpressionContext)` 这一组带上下文的 getter 会先按公式算，算不出来再回退固定数值。

### 2.2 曲线 `curve/Curve`

一条 float 曲线，内部是升序的 `Keyframe` 列表。

- `key(time, value)` / `key(Keyframe)`：按时间二分查找插入或覆盖，返回索引
- `moveKey` / `removeKey` / `smoothTangents`：编辑操作；`smoothTangents` 在两端用差分、中间点用 Catmull-Rom 斜率
- `evaluate(time)`：先按 `preMode` / `postMode`（`WrapMode`：CLAMP / LOOP / PING_PONG）把时间映射进有效区间，
  再取相邻两键按左键的 `EvaluateMode` 插值；HERMITE 分支按 `WeightedMode` 缩放切线（`weight / (weight + 3)`）
- `evaluate(time, ExpressionContext)`：多带一个求值上下文，挂了公式的字段按公式取值；上下文为 `null` 就是纯固定数值求值
- **健壮性处理**：相邻键时间相同、线段长度退化、切线为无穷时直接取左值，避免 `0/0` 产生 NaN 污染整条通道
- 命中位置用 `lastIndex` + 方向标记做局部近似，退化时才回到二分

`curve/Curvec` 是它的只读视图（`evaluate` / `size` / `key`），只读取值的场景优先用视图。

### 2.3 曲线集合 `curve/Clip`

`属性名 → Curve` 的集合，另存片段名与时长：

- `duration()`：显式设置过（> 0）直接用，否则按所有曲线最后一个键的时间实时计算
- `evaluate(property, time [, context])` / `evaluate(time, Evaluator [, context])`：单个属性或按 `Evaluator.properties()` 批量取值后 `build`
- `evaluateAll(time)`：带缓存的全量求值，播放时避免重复分配

### 2.4 路径 `path/`

- `PathNodec`：只读节点（位置、入/出切线、`PathMode`、是否自动平滑、挂了公式的分量 `expressions()`）
- `PathNode`：可写节点；`inTangent/outTangent` 在 `smooth` 开启时互为反向，保证拖动一侧另一侧跟随；
  `smooth(true)` 打开开关的瞬间会把出切线对齐到入切线（出 = -入），`restoreSmooth` 只改开关、不动切线（供反序列化）；
  坐标与切线的每个分量都可以挂公式（`DynamicField.NODE_*`）
- `PathMode`：`LINEAR` / `BEZIER` / `CATMULL_ROM`，**由每段起点节点的模式决定该段插值方式**
- `Path`：节点表 + **弧长表**（每段长度、累计长度）
  - `node/insertNode/removeNode/moveNode` 后按受影响范围重建弧长表（`updateArcLengthTable(begin, end)` 或整体重建）
  - `evaluate(distance, dest [, context])`：按弧长二分定位分段 → 归一化参数 → 按模式调 `util/SplineUtils` 取点
  - `evaluate(dest, progress [, context])`：按 0~1 进度取点（内部乘总长）
  - 退化保护：线段长度为 0 时参数取 0，索引越界时夹到有效分段
  - **弧长表一律按固定数值算**，不接求值上下文：弧长是静态度量，否则"路径总长"会随时间变，百分比口径就失去意义
- `Pathc`：只读视图（`name/size/node/totalLength/evaluate`），渲染等只读场景用它

### 2.5 轨道抽象 `track/`

编辑器按能力分层依赖：时间轴只认 `AnimationTrack`，曲线图与关键帧面板要改曲线时才认 `CurveTrack`。
以后加事件轨道 / 特效轨道不必改时间轴，曲线图会对它们显示"无可编辑曲线"：

- `AnimationTrack`：`type/id/label/color/duration/keyCount/key/addKey/removeKey/moveKey`。
  **只放所有轨道都成立的成员**：曲线、指令、事件这类专属能力不进接口——需要"经过即触发"就实现 `TickTrack`，
  需要交出底层曲线就在自己的实现里提供（`CurveTrack.curve()`），调用方按能力判断
- `CurveTrack`：把 `Curve` 适配成轨道的默认实现；`addKey(time)` 会继承前一个键的插值与加权模式；
  取值与取键都走它自己暴露的 `curve()`
- `TickTrack`：可选能力接口，`advance(fromTime, toTime)` 由播放器每帧带着推进区间调用（命令轨道即此类）
- `JsonTrack`：可选能力接口，`writeKeys()` / `readKeys(JsonArray)` 由轨道自己决定键的存档形态（不实现就不落盘）
- `RenamableTrack`：可选能力接口，`rename(id)` 让时间轴可以就地改轨道标识（曲线轨道的标识与相机属性绑定，不实现）
- `TrackType` + `TrackTypeRegistry`：轨道类型元数据与注册表（当前注册 `curve` 与 `command`）
- `AnimationChannelRegistry`：**通道元数据**——显示名、时间轴颜色、新建时的默认值、所属折叠分组
  （`position` / `position.x~z` / `rotation.x~z` / `fov` 在静态块注册，未注册的属性回退为"属性名 + 由名字派生的稳定颜色"）

### 2.6 顶层模型 `CameraAnimation`

由三部分组成：`Clip`（float 通道）+ `Path`（位置通道驱动的三维路径）+ 扩展轨道列表。

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
- 实现了 `JsonTrack` 的轨道随动画一起进 JSON；`copyFrom` 会整体替换轨道表（撤销栈与读档都依赖这一点）
- `variables()`：变量表（见 2.7），`variable(name)` / `addVariable(name)` / `removeVariable(name)` /
  `renameVariable(name, newName)` 负责增删改；名字要能被表达式当标识符读（`Expression.validName`），重名与空名一律拒绝
- `copyFrom(other)`：读档时**原地替换**内容——动画实例被播放器与编辑器各处持有，不能换对象；变量表也一并换成副本
- `CameraAnimationc`：只读视图（`name/duration/motionMode/distanceMode/path/tracks/curveTracks/extensionTracks/variables/distanceToLength`）

### 2.7 表达式与变量 `expression/`

让"数值"可以随时间变化：一个数值既可以是一个固定的数，也可以挂一条公式，播放时按当前时间算出来。

| 类型 | 职责 |
| --- | --- |
| `Expression` | 极简求值器：四则运算、`%`、括号、一元正负、变量、函数 `min/max/sin/cos/random`。每次求值重新扫描字符串（表达式很短，省掉 AST 更划算）；**任何失败一律返回 NaN**，调用方据此回退到固定数值 |
| `Expression.Resolver` | 变量取值入口：`resolve(name)`，未知变量返回 NaN |
| `Variable` | 变量 = 名字 + 取值来源。来源二选一：绑定的曲线轨道 id（非空）或固定值 `value`（不绑轨道时用） |
| `ExpressionContext` | 求值上下文：内置变量 `t`（当前时间）+ 动画里的变量。构造时把变量表索引成 Map，并对本次求值的变量取值做缓存 |
| `DynamicField` | **可以挂公式的数值字段**的枚举：关键帧的 `KEY_VALUE / KEY_IN_TANGENT / KEY_OUT_TANGENT / KEY_IN_WEIGHT / KEY_OUT_WEIGHT`，路径节点的 `NODE_{X,Y,Z} / NODE_IN_{X,Y,Z} / NODE_OUT_{X,Y,Z}` |

设计要点：

- **公式挂在数据上，不挂在求值链上**：`Keyframe` / `PathNode` 各存一份 `Map<DynamicField, String>`，
  `Curve` / `Clip` / `Path` 的 `evaluate` 只是多带一个 `@Nullable ExpressionContext`；传 `null` 就是纯固定数值求值
- **求值失败永远是回退，不是中断**：公式非法、引用了不存在的变量、算出来是 NaN——统统退回原来的固定数值，
  播放不会因为一条写错的公式而崩掉或整段失效
- **变量只读静态曲线**：`ExpressionContext` 取变量值时用 `curve().evaluate(time)`（不带上下文）。
  这顺带把自嵌套挡在门外——"变量 V 绑轨道 A、A 的键又引用 V"不会无限递归，
  因为求值链在变量处就断了，A 上的公式在这一步被忽略、用键上的固定数值。
  代价是同一个键"作为相机属性播放"与"作为变量被引用"可能得出不同的值；
  `CameraAnimation.selfReferencing(variable)` + `Expression.references(source, name)` 就是这套检查，
  变量面板会把这类变量的取值标成警告色并在悬停时说明
- **一次求值内每个变量只算一次**：上下文绑定了一个固定时间，变量值在其生命周期内不会变，
  所以 `resolve` 的结果缓存进 `Map`。同一条公式里写 3 次、或一帧内几十个字段引用同一个变量，都只算一次；
  上下文是每帧新建的，不存在过期问题。界面侧由 `EditorContext.beginFrame()`（两个编辑器屏幕渲染开头各调一次）
  提供同一份帧内上下文，跨帧重算——不能只看播放头时间，因为暂停时时间不动而变量随时可能被改
- **轨道被删时变量自动解绑**：`removeChannel` 会把指向它的变量 `trackId` 清空，变量随即回到固定值模式
- **时间不进 `DynamicField`**：公式本来就是按时间求值的，时间自己再挂公式只会绕回自己


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

`CameraPlayer.evaluatePose` 的关键分支：每帧先构造一份 `ExpressionContext`（当前时间 + 动画变量），
再把它传给所有 `evaluate` 重载，挂了公式的关键帧与路径节点由此按当前时刻取值。
坐标模式下逐轴判断"有没有键"，有键才写该轴；路径模式下先 `distanceToLength` 再 `Path.evaluate`，并对非有限值做兜底。

---

## 4. 持久化（`core.animation.io`）

### 4.1 `AnimationCodec`——JSON 编解码

- 顶层字段：`name`、`motionMode`、`distanceMode`、`tracks`（轨道数组）、`variables`（变量数组）、`path`
- 轨道数组按动画里的轨道顺序写出（拖拽排序会反映到文件里），两类轨道混排、用 `type` 区分：
  - 曲线轨道：`type` = `free_camera_api_tripod:curve`，字段为 `id`（相机属性名）、`preMode`、`postMode`、
    `keys`（每个键含时间、取值、入/出切线、入/出权重、`evaluateMode`、`weightedMode`）
  - 扩展轨道：`type` = 该类型 id，字段为 `id`（轨道标识）与 `keys`（键的字段由轨道自己定，见 `JsonTrack`）
- 变量数组：每项含 `name`（表达式里引用的名字）、`track`（绑定的曲线轨道 id，空串表示不绑定）
  与 `value`（不绑定轨道时用的固定值）
- 挂了公式的数值字段写在 `expressions` 对象里（键是 `DynamicField` 枚举名、值是公式文本），
  关键帧与路径节点各带一份；一条公式都没有时不写出该字段
- 读档按 `type` 分派：curve 走通道与曲线，其余查 `TrackTypeRegistry` 用工厂造实例再交回 `readKeys`；
  类型未注册、没有工厂、id 重复或不实现 `JsonTrack` 的条目跳过
- 路径：`name` + `nodes`（位置、入/出切线、`pathMode`、`smooth`）
- 反序列化一律宽松（字段缺失/类型错误/枚举名非法都回退默认值），只有整份 JSON 解析失败才返回 null
- **`tracks` 是权威集合**：JSON 里没出现的通道会在读取后被移除，避免构造动画时的默认通道残留
- 序列化只读数据：`animationToJson(CameraAnimationc)` / `pathToJson(Pathc)`

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
| `WorldViewScreen` | 世界内查看：面板全部收起，底部一条操作栏，左键进入环视（Esc 先退出环视再返回）；从路径编辑器进来时不放播放控制 |
| `BrowserScreen` | **浏览界面基类**：标题行 + 面包屑行 + 可选第二行 + 列表 + 底栏 + 提示行，以及选中/滚动/输入分派、新建文件夹；子类只实现数据与语义 |
| `FileBrowserScreen` | 资源管理器式的本地文件保存/打开：顶栏只有面包屑（点段跳转、段尾箭头选同级目录），列本地目录与符合后缀的文件 |
| `StorageDataScreen` | **存档数据中间基类**：层级、面包屑、条目避重名与建文件夹——浏览与管理两个界面共用 |
| `StorageBrowserScreen` | 存档内数据浏览（打开 / 保存）：条目支持 `/` 多层文件夹 |
| `StorageManagerScreen` | 存档数据管理：第二行切换动画 / 路径，建文件夹与「删除」移除选中条目（文件夹连同内容一起删） |
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
- 表达式：`openExpressionEditor(label, expression, onConfirm)` / `expressionEditor()` / `evaluateExpression(expr)`
  ——表达式编辑窗口与二次确认一样是**模态**的，屏幕在最上层绘制并优先派发输入
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
| `KeyframePanel` | `keyframe` | 选中关键帧的属性、插值模式、贝塞尔对称设置；非曲线键显示时间与轨道自带的内容（如指令文本） |
| `GraphPanel` | `graph` | 曲线图：取值曲线与切线手柄的绘制与拖拽 |
| `TimelinePanel` | `timeline` | 时间轴：轨道行、折叠分组、播放头、键的拖拽与右键菜单（「插入轨道 ▸」、重命名、删除扩展轨道；名称列双击可改名） |
| `VariablePanel` | `variables` | 变量表：新建/删除变量、改名、选择取值来源（固定值 / 某条曲线轨道），并实时显示取值 |

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
| `EditorWidget` | 控件基类：矩形、可见/可用、悬停、焦点、工具提示 |
| `WidgetHost` | 控件容器：焦点管理与事件分发（后加入的优先命中） |
| `ButtonWidget` | 按钮：**按下再抬起且指针仍在按钮上才触发**；默认文字色随主题取 |
| `TextFieldWidget` | 文本输入：点击进入编辑、双击全选、右键清空、回车提交、Esc 取消；编辑中不被外部刷新覆盖 |
| `NumberFieldWidget` | 数值输入：拖拽/输入，用于坐标、FOV 等 |
| `ExpressionFieldWidget` | 数值输入 + 模式切换按钮：默认数值模式，可切成动态模式挂公式；动态模式下数值框变成预览框（公式 + 当前取值），点击打开表达式编辑窗口。时间这类不允许动态的字段仍用 `NumberFieldWidget` |
| `ContextMenu` | 自绘右键菜单：图标 + 文本、勾选项、分隔线、二级菜单（悬停展开且不会移开即消失） |
| `ConfirmDialog` | 二次确认弹窗（覆盖确认、模式切换确认等） |
| `BreadcrumbBar` | 路径面包屑：每段可点击跳转，段尾箭头展开**同级目录下拉**（可滚动、点空白收起），与资源管理器地址栏一致 |

> `ExpressionEditorWindow`（在 `core.editor` 下）是表达式编辑窗口（模态）：第一行窗口标题 + 实时求值结果，
> 第二行「属性：xxx」说明在给哪个数值写公式；下面左栏是公式输入区，右栏上为变量表（带 `+` / `−`，点名字插进公式）、
> 下为函数表（点一下插入模板并把光标停进括号）。输入区上界与变量区顶部齐平、下界与函数表底部齐平，
> 三块区域都在内容超出时显示滚动条。它不自绘到面板里，而是由屏幕在最上层绘制并优先派发输入。

### 5.6 绘制与主题 `theme/`、`render/`

- `Draw`：全部配色与绘制辅助。配色是**可变静态字段**，`applyTheme` 整体赋值，`beginFrame()` 每帧按 `EditorConfig.DARK_MODE` 同步；
  文本绘制会按当前主题给文字附加阴影样式（浅色主题用浅灰阴影，深色主题沿用原版）
- `Icons`：复用字体的符号字形，不引入额外纹理
- `render/FloatFill`：浮点坐标矩形填充（原版只接受整数坐标，曲线会被量化成阶梯）
- `render/ViewportPipRenderer`：把整帧游戏画面拷进离屏纹理再由 GUI 通道贴回视口（PiP）
- `ViewportTakeover`：锁定光标后的视角转向与 WASD 飞行，视口面板 / 世界内查看 / 路径编辑共用
- `PathHandleDrag`：视口里路径点与控制点的命中、屏幕位移↔世界位移换算与写回（用抓取时刻的视深平面反投影）

### 5.7 语言与配置

- 文本统一走 `EditorLang.t(key)`，语言键前缀 `free_camera_api_tripod.editor.`，文件在 `assets/free_camera_api_tripod/lang/{zh_cn,en_us}.json`
- `EditorConfig`：客户端配置，含布局串 `layout.dock`、下排高度、折叠面板、视口提示收起、深色模式、
  `layout.path_dock` / `layout.path_collapsed`（路径编辑器自己的一套布局）、
  `modern_ui_compat`（是否允许界面后端接管，默认开）、`dev.test_keys`

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
- **路径索引缓存**：改节点后必须让 `Path` 重建弧长表（用它的增删方法即可，不要绕过它们直接改内部表）
- **只读场景用只读视图**：渲染、信息展示优先用 `Pathc` / `Curvec` / `CameraAnimationc`，避免误改数据
- **右键菜单的绘制顺序**：由屏幕在最后统一绘制，面板只持有；否则菜单会被其它面板盖住
- **会话的编辑动作都作用于"当前选中的轨道"**：`addKey` / `moveKey` / `removeKey` 没有 track 参数，
  外部界面调它们之前得先 `selectTrack(...)`；否则返回 -1 或 false，看起来像"点了没反应"
- **二次确认必须被呈现**：`switchToPathMode` / `switchToCoordinateMode` 这类动作只是设置了一个待确认项，
  真正执行在 `confirmPending()` 里。外部界面不画 `pendingConfirm()` 就等于永远执行不了
- **外部界面后端只能 import 主 mod 的 `api.*`**：一旦依赖 `core.*`，主 mod 内部重构就会把后端一起弄坏，
  等于把两边的发布绑死成一个
