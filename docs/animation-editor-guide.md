# 相机动画与编辑器 GUI 阅读手册

面向要读/改这套代码的人：先讲清分层与数据流，再按包给出每个类的职责与关键实现，
最后给一条从零到能改的阅读顺序与扩展步骤。

- 运行环境：NeoForge 26.1.2 / Minecraft 26.1.2，Java 25
- 主 mod：id `free_camera_api_tripod`，根包 `cn.anecansaitin.free_camera_api_tripod`
- 附属 mod：id `free_camera_api_tripod_modernui`，gradle 子项目 `addon-modernui`
- 主 mod 的编辑器是**自绘 GUI**（不依赖原版控件），除语言键外没有资源依赖；
  附属 mod 用 Modern UI 渲染的界面读的是同一份数据
- 构建：主 mod `./gradlew build`，附属 mod `./gradlew :addon-modernui:build`；
  `libs/` 里的前置 jar 不入库（见 `.gitignore`），新克隆的仓库需要自备这些 jar，
  或者把依赖改成配置里注释掉的 maven 坐标

---

## 1. 分层总览

```
api/                          对外契约：数据模型 + 扩展点（不依赖 core）
├── camera/                   相机数据接口：TripodData / TripodStates / ControlScheme
├── animation/                动画数据模型
│   ├── Keyframe/Keyframec/TrackKey/Evaluator/EvaluateMode/WeightedMode
│   ├── curve/                Curve / Curvec / MultiKeyframe / Clip / WrapMode
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

addon-modernui/              附属 mod（独立 gradle 子项目）
└── modernui/                 Modern UI 版编辑器：界面外壳 + 自绘控件 + 逐帧刷新器
```

**依赖方向**：`editor / cmd_camera / io` → `api`；`api` 不引用 `core`。
`addon-modernui` 只允许 import 主 mod 的 `api.*`，禁止碰 `core.*`；
主 mod 完全不认识附属 mod，反向依赖只有一条：主 mod 的 `EditorUiHost` 会被动接受注册。
新增公共数据模型请放 `api`，只在编辑器内部用的（面板、控件）放 `core.editor`。

### 数据流

```
F6 ──► CameraEditorScreen.open()
        │
        ├─ EditorConfig.MODERN_UI_COMPAT 开启，且 EditorUiHost 里有已注册的后端
        │     └─► 后端接管界面（例如 addon-modernui），内置界面不再打开
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
| `Keyframe` | 可写关键帧（setter 返回自身，链式）；静态工厂 `create/linear/step/hermite` |
| `core` 内的实现 | `curve/MultiKeyframe`——`Curve` 内部真正持有的键，可原地 `set(Keyframe)` |

`EvaluateMode`：`LINEAR` / `STEP` / `HERMITE`；`WeightedMode`：`NONE` / `IN` / `OUT` / `BOTH`。

### 2.2 曲线 `curve/Curve`

一条 float 曲线，内部是升序的 `MultiKeyframe` 列表。

- `key(time, value)` / `key(Keyframe)`：按时间二分查找插入或覆盖，返回索引
- `moveKey` / `removeKey` / `smoothTangents`：编辑操作；`smoothTangents` 在两端用差分、中间点用 Catmull-Rom 斜率
- `evaluate(time)`：先按 `preMode` / `postMode`（`WrapMode`：CLAMP / LOOP / PING_PONG）把时间映射进有效区间，
  再取相邻两键按左键的 `EvaluateMode` 插值；HERMITE 分支按 `WeightedMode` 缩放切线（`weight / (weight + 3)`）
- **健壮性处理**：相邻键时间相同、线段长度退化、切线为无穷时直接取左值，避免 `0/0` 产生 NaN 污染整条通道
- 命中位置用 `lastIndex` + 方向标记做局部近似，退化时才回到二分

`curve/Curvec` 是它的只读视图（`evaluate` / `size` / `key`），只读取值的场景优先用视图。

### 2.3 曲线集合 `curve/Clip`

`属性名 → Curve` 的集合，另存片段名与时长：

- `duration()`：显式设置过（> 0）直接用，否则按所有曲线最后一个键的时间实时计算
- `evaluate(property, time)` / `evaluate(time, Evaluator)`：单个属性或按 `Evaluator.properties()` 批量取值后 `build`
- `evaluateAll(time)`：带缓存的全量求值，播放时避免重复分配

### 2.4 路径 `path/`

- `PathNodec`：只读节点（位置、入/出切线、`PathMode`、是否自动平滑）
- `PathNode`：可写节点；`inTangent/outTangent` 在 `smooth` 开启时互为反向，保证拖动一侧另一侧跟随
- `PathMode`：`LINEAR` / `BEZIER` / `CATMULL_ROM`，**由每段起点节点的模式决定该段插值方式**
- `Path`：节点表 + **弧长表**（每段长度、累计长度）
  - `node/insertNode/removeNode/moveNode` 后按受影响范围重建弧长表（`updateArcLengthTable(begin, end)` 或整体重建）
  - `evaluate(distance, dest)`：按弧长二分定位分段 → 归一化参数 → 按模式调 `util/SplineUtils` 取点
  - `evaluate(dest, progress)`：按 0~1 进度取点（内部乘总长）
  - 退化保护：线段长度为 0 时参数取 0，索引越界时夹到有效分段
- `Pathc`：只读视图（`name/size/node/totalLength/evaluate`），渲染等只读场景用它

### 2.5 轨道抽象 `track/`

编辑器（时间轴、曲线图、关键帧面板）**只依赖 `AnimationTrack` 接口**，以后加事件轨道/特效轨道无需改编辑器：

- `AnimationTrack`：`type/id/label/color/duration/keyCount/key/addKey/removeKey/moveKey`，
  另有 `valueAt(time)`（不产生 float 的轨道返回 NaN）与 `curve()`（曲线轨道返回底层 `Curve`，其他返回 null）
- `CurveTrack`：把 `Curve` 适配成轨道的默认实现；`addKey(time)` 会继承前一个键的插值与加权模式
- `TrackType` + `TrackTypeRegistry`：轨道类型元数据与注册表（当前只注册 `curve`）
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
- `copyFrom(other)`：读档时**原地替换**内容——动画实例被播放器与编辑器各处持有，不能换对象
- `CameraAnimationc`：只读视图（`name/duration/motionMode/distanceMode/path/tracks/curveTracks/distanceToLength`）

---

## 3. 求值与播放（`core.cmd_camera`）

| 类 | 职责 |
| --- | --- |
| `CmdCamera` | 相机插件入口：持有 `CameraAnimation` / `CameraPlayer` / `CameraEditorModel` / `CameraInfo`，`update(partialTicks)` 每帧把姿态应用到相机修饰器 |
| `playback/CameraPlayer` | 只做时间推进与姿态求值：`tick(deltaSeconds)`、`evaluatePose(dest)`、`play/pause/toggle/stop/seek/speed/loop` |
| `CameraPose` | 位置 + YXZ 旋转 + FOV 的结果结构，带"该分量是否有效"标记（无键的轴不接管，沿用原相机值） |
| `edit/CameraEditorModel` | 编辑状态与操作：视图模式、自由姿态、选中轨道/关键帧/路径节点，增删改键与路径点、录制相机姿态 |
| `edit/Selected` | 路径节点的选中项（节点 / 入切线 / 出切线） |
| `info/CameraInfo` | 把动画与播放状态整理成可直接显示的只读文本（状态栏、视口叠加、摘要） |
| `PathRender` | 只在编辑器打开时把路径线、节点方块、控制点画进世界；带脏标记缓存，几何变化时重建 |
| `CmdCameraKeyMapping` | 快捷键：**F6** 打开编辑器、**F7** 播放/暂停、**F8** 停止、**F9** 打开路径编辑器（均限游戏内） |
| `CameraCommand` | 客户端命令注册 |

`CameraPlayer.evaluatePose` 的关键分支：坐标模式下逐轴判断"有没有键"，有键才写该轴；
路径模式下先 `distanceToLength` 再 `Path.evaluate`，并对非有限值做兜底。

---

## 4. 持久化（`core.animation.io`）

### 4.1 `AnimationCodec`——JSON 编解码

- 顶层字段：`name`、`motionMode`、`distanceMode`、`tracks`（通道数组）、`path`
- 每个通道：`property`、`preMode`、`postMode`、`keys`
- 每个键：时间、取值、入/出切线、入/出权重、`evaluateMode`、`weightedMode`
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
- 多人游戏或未进世界时所有静态方法安全返回空列表 / null

---

## 5. 编辑器 GUI（`core.editor` 内置 + `api.editor` 对外契约）

### 5.1 屏幕家族

| 屏幕 | 说明 |
| --- | --- |
| `CameraEditorScreen` | 主编辑器：顶部菜单（文件/编辑/视图/播放/帮助）+ 可拖拽停靠的面板布局；同时是 `F6` 的入口，自己渲染还是交给界面后端由它判断（见 5.8） |
| `PathEditorScreen` | 路径编辑器：默认「视口 / 节点列表 / 节点详情」三列 + 顶部路径工具栏 |
| `WorldViewScreen` | 世界内查看：面板全部收起，底部一条操作栏，左键进入环视（Esc 先退出环视再返回） |
| `FileBrowserScreen` | 资源管理器式的本地文件保存/打开：面包屑（含同级目录下拉）+ 可编辑地址栏 + 列表 |
| `StorageBrowserScreen` | 存档内数据浏览：同样用面包屑，条目支持 `/` 多层文件夹 |
| `CameraScreens` | 判定"当前是否在相机编辑界面"，供渲染与输入分流使用（按屏幕实例判断，不受 init 顺序影响） |

### 5.2 `EditorContext`——一切共享状态

屏幕与面板都通过它拿数据与公共服务：

- 数据：`animation()`、`player()`、`editor()`、`info()`
- 时间轴视图：`pixelsPerSecond`、`viewStartTime`、`snapEnabled`、`snapStep`、`majorTickStep()`、`snapTime()`、`formatTick()`
- 曲线图相关：`bezierSymmetric`（切线是否对称）
- 路径距离口径：`distancePercent()`
- 交互反馈：`notify(Component)` 状态行提示、`confirm(...)` 二次确认弹窗
  ——内置界面由屏幕统一绘制与派发；外部界面从 `EditorSession.pendingConfirm()` 取走自己画，
  确认时调 `confirmPending()`。为此 `ConfirmDialog` 的 `confirm()` 与 `message()` 是公开的
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
| `KeyframePanel` | `keyframe` | 选中关键帧的属性、插值模式、贝塞尔对称设置 |
| `GraphPanel` | `graph` | 曲线图：取值曲线与切线手柄的绘制与拖拽 |
| `TimelinePanel` | `timeline` | 时间轴：轨道行、折叠分组、播放头、键的拖拽与右键菜单 |

> 新增面板：继承 `EditorPanel`，实现 `layoutWidgets` 与 `renderContent`；
> 重建控件前必须 `widgets.clear()`（`WidgetHost` 不会自动清理，否则控件会叠加）。

### 5.4 布局 `layout/`

- `UiRect`：自绘 UI 的统一矩形结构
- `DockLayout`：上/下两排 + 排内单元（横向权重）+ 单元内纵向叠放（纵向权重）；
  面板标题栏可拖到任意单元的四侧重新停靠，也可转为悬浮窗口（带最小尺寸与级联偏移）；
  布局序列化为一段字符串存进 `EditorConfig`，串里带 `v=` 版本号，版本不符整串作废并回落默认布局

### 5.5 控件 `widget/`

| 控件 | 说明 |
| --- | --- |
| `EditorWidget` | 控件基类：矩形、可见/可用、悬停、焦点、工具提示 |
| `WidgetHost` | 控件容器：焦点管理与事件分发（后加入的优先命中） |
| `ButtonWidget` | 按钮：**按下再抬起且指针仍在按钮上才触发**；默认文字色随主题取 |
| `TextFieldWidget` | 文本输入：点击进入编辑、双击全选、右键清空、回车提交、Esc 取消；编辑中不被外部刷新覆盖 |
| `NumberFieldWidget` | 数值输入：拖拽/输入，用于坐标、FOV 等 |
| `ContextMenu` | 自绘右键菜单：图标 + 文本、勾选项、分隔线、二级菜单（悬停展开且不会移开即消失） |
| `ConfirmDialog` | 二次确认弹窗（覆盖确认、模式切换确认等） |
| `BreadcrumbBar` | 路径面包屑：每段可点击跳转，段尾箭头展开**同级目录下拉**（可滚动、点空白收起），与资源管理器地址栏一致 |

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
  `modern_ui_compat`（是否允许界面后端接管，默认开）、`dev.test_keys`
- 附属 mod 不再维护自己的语言文件，它按同一个前缀去取主 mod 的键（`ModernUiText`），
  所以面板标题、按钮、提示这些两边共用一份译文；只有附属 mod 独有的文案用 `modern_ui.` 前缀

### 5.8 界面后端与附属 mod（`api.editor`）

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

**为什么共享的是数据而不是绘制**：两套界面的绘制接口不兼容（原版 `GuiGraphicsExtractor`
对 ModernUI `Canvas`，而且 ModernUI 的 Canvas 没有 `drawText`），能共用的只有时间↔像素
换算这类薄薄一层，会话上已经给全了。所以抽出来的是**数据与动作**，绘制各自实现。

**附属 mod `addon-modernui`**

| 类 | 职责 |
| --- | --- |
| `ModernUiAddon` | mod 入口，构造时 `EditorUiHost.register(new ModernUiEditorBackend())` |
| `ModernUiEditorBackend` | `priority() = 100`，无条件接管，直接开界面 |
| `ModernUiEditorScreen` | 界面骨架：菜单条 + 三列主体 + 时间轴 + 状态栏 |
| `ModernUiPanels` | 各面板的搭建；会变的信息行用 `liveField` 挂到刷新器上 |
| `ModernRulerView` | 标尺：刻度线、时间标签、播放头，兼水平操作（拖动移动播放头、Ctrl+滚轮缩放、Shift+滚轮平移） |
| `ModernKeyStripView` | 单轨关键帧条：点选、拖动改时间、右键删除 |
| `ModernCurveView` | 曲线图：折线 + 关键帧方块，点方块选中对应关键帧 |
| `ModernUiRefresher` | 逐帧刷新器与脏标记 `Gate` |
| `ModernUiWidgets` / `ModernUiColors` / `ModernUiText` | 控件工厂 / 配色 / 文案取用 |

**保留模式的刷新模型**：Modern UI 的控件搭好之后不会自己跟数据走，`EditorSession` 也没有
变化回调通道，所以只能由界面主动去看。`ModernUiRefresher` 用 `View.postOnAnimation` 挂一个
自我重投的回调，每帧把所有登记的动作跑一遍，但每个动作先过 `Gate.changed(...)`——
值没变就直接返回，既不 `setText` 也不 `invalidate`。所以「每帧轮询」不等于「每帧重绘」。

- 会变的标量（数值、状态文本、二次确认）走 `liveField` / 各自的 `Gate`
- 会变的列表（轨道、路径节点）在集合签名（数量 + id / 名称）变化时整体重建
- 播放中播放头每帧都在动，标尺与关键帧条确实逐帧重绘，这是播放本身的要求，避不掉

---

## 6. 扩展指南

**加一条相机通道**
1. 在 `AnimationChannelRegistry` 静态块 `register(属性名, 语言键, 颜色, 默认值[, 分组])`
2. 需要参与求值时，在 `CameraPlayer.evaluatePose` 里加分支，或让外部通过 `Evaluator` 取值

**加一种轨道类型**（事件触发器、后处理特效等）
1. 实现 `api.animation.track.AnimationTrack`（`type/id/label/color/duration/key/addKey/removeKey/moveKey`）
2. 定义 `TrackType` 并在 `TrackTypeRegistry.register` 注册（也可留 `factory = null` 只作占位）
3. 用 `CameraAnimation.addExtensionTrack(...)` 接入；时间轴、曲线图、关键帧面板会自动识别

**加一个面板**
1. 继承 `EditorPanel`，给出 `id`（语言键、布局串都按 id 索引）与标题
2. 实现 `layoutWidgets` / `renderContent`，事件用 `contentXxx` 钩子；重建控件前 `widgets.clear()`
3. 在 `CameraEditorScreen` 里创建并交给 `DockLayout` 管理（默认布局串与 `LAYOUT_VERSION` 同步更新）
4. 面板要展示的数据如果外部界面也该看到，顺手在 `EditorSession` 上开一个只读成员；
   只想给内置界面看的就不用管

**接入另一套界面框架（写一个界面后端）**
1. 新建独立的 gradle 子项目（可参考 `addon-modernui`），依赖主 mod 的 jar，
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

1. **数据模型**：`api/animation/curve/Keyframe → MultiKeyframe → Curve`，再 `path/PathNode → Path`
   ——先把"键怎么插值、路径怎么按弧长取点"读明白，后面都建立在它上面
2. **轨道与顶层**：`track/AnimationTrack → CurveTrack → TrackTypeRegistry`，再 `CameraAnimation`（关注两种模式与通道集合）
3. **持久化**：`AnimationCodec`（JSON 结构）→ `AnimationFiles`（本地）→ `AnimationSavedData`（存档 + 层级）
4. **运行时**：`CmdCamera`（谁持有谁）→ `CameraPlayer`（求值）→ `CameraEditorModel`（编辑操作）→ `PathRender`（世界渲染）
5. **GUI**：`EditorContext` → `CameraEditorScreen`（屏幕骨架、菜单、快捷键）→ `panel/EditorPanel` → 一个具体面板（建议从 `TimelinePanel` 入手）
   → `layout/DockLayout` → `widget/*` → `theme/Draw`
6. **外部界面契约**：`api/editor/EditorSession`（外部能读什么、能做什么）→ `EditorUiBackend` / `EditorUiHost`（怎么接进来）
   → `CameraEditorScreen.open()`（分流点）→ `BuiltinEditorSession`（内置界面怎么把自己适配出去）
7. **附属 mod**：`ModernUiAddon` → `ModernUiEditorBackend` → `ModernUiEditorScreen` → `ModernUiPanels`
   → `ModernUiRefresher`（刷新模型，这层是保留模式的关键）→ `ModernRulerView` / `ModernKeyStripView` / `ModernCurveView`
8. **辅助界面**：`FileBrowserScreen` / `StorageBrowserScreen`（面包屑与列表）、`WorldViewScreen`、`PathHandleDrag`、`ViewportTakeover`

---

## 8. 容易踩的坑

- **控件生命周期**：面板 `rebuild` 前必须 `widgets.clear()`，否则控件叠加、点哪个都像没反应
- **动画实例是共享的**：读档只能 `CameraAnimation.copyFrom(...)` 原地更新，不能替换对象（播放器与面板都持有引用）
- **主题是可变静态字段**：不要缓存 `Draw.XXX` 到 `static final`，切主题后不会更新
- **键是可变共享对象**：`Curve.key(int)` 返回的是内部 `MultiKeyframe`，直接改它等于改数据（这也是编辑生效的方式）
- **路径索引缓存**：改节点后必须让 `Path` 重建弧长表（用它的增删方法即可，不要绕过它们直接改内部表）
- **只读场景用只读视图**：渲染、信息展示优先用 `Pathc` / `Curvec` / `CameraAnimationc`，避免误改数据
- **右键菜单的绘制顺序**：由屏幕在最后统一绘制，面板只持有；否则菜单会被其它面板盖住
- **会话的编辑动作都作用于"当前选中的轨道"**：`addKey` / `moveKey` / `removeKey` 没有 track 参数，
  外部界面调它们之前得先 `selectTrack(...)`；否则返回 -1 或 false，看起来像"点了没反应"
- **二次确认必须被呈现**：`switchToPathMode` / `switchToCoordinateMode` 这类动作只是设置了一个待确认项，
  真正执行在 `confirmPending()` 里。外部界面不画 `pendingConfirm()` 就等于永远执行不了
- **保留模式要自己驱动刷新**：Modern UI 的控件不会跟着数据动，也没有变化回调；
  忘了挂 `ModernUiRefresher` 的话界面会一直停在打开那一刻的样子
- **附属 mod 只能 import 主 mod 的 `api.*`**：一旦依赖 `core.*`，主 mod 内部重构就会把附属 mod 一起弄坏，
  等于把两个 mod 绑死成一个
