# P1 契约计划与新契约目录（`_v2`）

> 本文是**施工约定**，不是设计文档。设计以 `docs/` 下三份设计为准；
> 本文固定三件事：新契约放哪、叫什么、怎么写，以及本批交付了什么供审核。
>
> 状态：**P1 第二批契约已提交（含 Q4–Q7 裁决落地），待人工审核**。首批（包边界 + 不可变内核）
> 已按最新设计补齐有类型状态轨道、相机来源轨道与采样读数；审核通过后再填充实现
> （`core` / `editor`）。注视姿态捕获（§7.4）仍属后续 edit / editor 批次，不能据此认定
> 源码已满足新设计（见 §1.4、§7.4）。
> 设计文档中的包名一律按**最终形态**书写（`api.animation.block` 等），不写 `_v2`；
> 临时差异只记在本文 §1.4。

---

## 1. 新契约目录：`free_camera_api_tripod_v2`（临时）

### 1.1 为什么临时多一层根包

主设计 D1 规定顶层包为 `api` / `core` / `editor`，并要求新契约占用
`CameraAnimation`、`Path`、`Curve`、`Keyframe`、`ValueSource` 等**设计里的名字**。
这些名字在旧实现里已被可变版本占用，同包同名无法共存。

曾试两条路：

| 方案 | 问题 |
| --- | --- |
| 旧类加 `Legacy` 后缀 | 新旧混在同一棵包树里，审阅时难以分辨哪些是待审内容；改名本身还污染了大量旧跟踪文件 |
| **新契约放 `_v2` 根（当前）** | 旧代码零改动、新内容集中可审；代价是临时多一层包名 |

额外好处：新契约可以直接用设计里的干净名字（`CameraAnimation`、`Path`、`Curve`…），
**不需要 V2 后缀**，也不再有同名冲突。

### 1.2 收尾动作（去 `_v2`）

1. 删除旧实现（存量 `core.editor.*` 等）。
2. 把 `_v2` 下各包**整体上移一层**。
3. 全局替换包名：`cn.anecansaitin.free_camera_api_tripod_v2.`
   → `cn.anecansaitin.free_camera_api_tripod.`。

这是纯机械操作，由 §6.2 的三条门禁保证不出错。

### 1.3 当前目录结构

```text
cn/anecansaitin/free_camera_api_tripod_v2/
  api/animation/id/          稳定 ID（D20）
  api/animation/field/       值源、曲线、关键帧、通道语义键、变量、自定义函数
  api/animation/path/        路径几何数据
  api/animation/expression/  预留：编译与符号契约（当前只有包说明，见 §1.4）
  api/animation/eval/        求值层最小查询面（TrackLookup；其余待下一批）
  api/animation/block/       不可变文档模型（文档根、有类型状态轨道、采样声明/参数/读数）
  api/animation/playback/    播放裁决类型（AimOverride；其余待下一批）
  api/spatial/               空间、锚点、姿态值、注视状态
  editor/                    编辑器分区（本包 + spi + internal）
```

最终形态还会增加（P1 余项）：`api/animation/edit`、`api.animation.document`、
`api.storage`，以及 `core` 与业务 `editor`；`eval` / `playback` 补齐设计列出的其余契约。

### 1.4 与设计文档的差异清单

本文是唯一允许记录临时差异的地方。当前差异：

| 项 | 设计文档 | 当前 | 收尾动作 |
| --- | --- | --- | --- |
| 根包名 | `cn.anecansaitin.free_camera_api_tripod` | `...free_camera_api_tripod_v2` | 去掉 `_v2`（§1.2） |
| `field` 与 `expression` 分工 | §2.1 把值源 / 编译 / 符号都放在 `expression` | 值源、曲线、关键帧、符号条目在 `api.animation.field`；`expression` 只有包说明（预留编译器批次） | 编译器 / 符号批次再合并或迁移，届时更新 §1.3 |
| `Variable` / `CustomFunction` 位置 | 未指定（§6.1 只提“共享符号”） | `api.animation.field` | 若 `eval` 批次认为应独立，可迁到 `api.animation.symbol` |
| 音频数据结构 | A0–A1 阶段实施 | 只提交 `AudioRef` / `AudioRefId` 身份与边界，无 Codec、无播放路径 | 无需动作；符合 D25「前期只保留边界」 |
| 布局矩形动画 | §3.1.2 要求 `AnimatedNumber` 的 x/y/width/height 可随时间变化 | `RigView.Rect` 仍是静态几何；P1 首批未列此项 | 随布局 / 编辑批次补 `field.AnimatedNumber` 并改写 `RigView.Rect`（Q11） |
| 采样参数 Codec | 类型自身的 `MapCodec` + common 侧注册 | `SampleArgs` 名义接口已交付（Q7）；Codec 与注册表未写 | 随 S1 采样类型注册表批次补齐 |
| 叠加元素内容 | O1–O3 阶段定义各类字段 | `OverlayElement.content` 仍是不透明 `Object` | 随 O1–O3 改为按种类拆分的封闭内容类型（Q7：不提前定型） |
| `eval` / `playback` 包完整性 | 设计列出完整契约 | 只交付裁决所需最小类型：`eval.TrackLookup`（Q6）、`playback.AimOverride`（Q5） | 随 eval / playback 批次补齐其余类型 |
| 注视姿态捕获 | 可按冻结事实一次写 XYZ 键与 Off（§4.7） | 仅交付 `AimState.Off` 与 Off 交还语义；无捕获结果/复合 `EditOp` | 后续 edit/editor 批次补齐，不宣称本次已实现 |

本轮已补齐、**不再是差异**的项（保留在此便于追溯上一版计划）：

| 项 | 现落点 |
| --- | --- |
| 时间轴状态轨道 | `AimTrack` / `ActiveTrack` / `CameraSourceTrack`（sealed `KeyframeTrack`，仅 STEP，共用 `TrackId` / `KeyId`） |
| 相机来源取代固定主机位 | `CameraAnimation.cameraSource`（默认 `Player`，无 `primaryRigId`） |
| 注视 Off / Target | `api.spatial.AimState` + `CameraRig.aim` |
| 世界采样读数 | `SampleReading`（VALUE / UNKNOWN / COLLECTION_ERROR）+ 两个原因枚举 |
| 播放时长口径 | `CameraAnimation.duration()` 与 `CameraRig.contentDuration()` 计入状态轨道与来源轨道 |
| `TrackLookup` 位置（Q6） | 迁到 `api.animation.eval`；文档根不再有 `trackLookup()`，消除 `block ↔ expression` 包循环 |
| 注视请求覆盖位置（Q5） | `api.animation.playback.AimOverride`（`Inherit` / `Disabled` / `Replace`），eval 只接收生效 `AimState` |
| 扩展轨道形态（Q4） | 维持 sealed `AnimationTrack` + non-sealed `ExtensionTrack`；补“不得使用内建类型 ID”约束 |

---

## 2. 已确认的决策（用户）

| 项 | 结论 |
| --- | --- |
| 顶层包（最终形态） | `cn.anecansaitin.free_camera_api_tripod.{api,core,editor}`；`editor` 与 `core` **平级**，不再是 `core.editor` |
| 顶层包（**当前临时形态**） | 新契约集中在 `cn.anecansaitin.free_camera_api_tripod_v2.*`，便于分离审阅；旧代码清理后去掉 `_v2`（§1） |
| 旧代码处置 | 旧实现**原样保留、不接线**；新契约只新增在 `_v2` 根下。工程随时可编译、可运行客户端 |
| 首批范围 | P1：包边界 + 不可变内核契约 |
| 第二批范围（本轮） | 按最新设计补齐 `block` / `spatial`：有类型状态轨道（aim / active / cameraSource）、`AimState`、`CameraSource`、世界采样读数；并落地 Q4–Q7 裁决：扩展轨道维持、`AimOverride` 入 playback、`TrackLookup` 迁 eval、采样参数用 `SampleArgs` 名义接口。布局 `AnimatedNumber` 与其余余项仍留后续批次（§1.4、§7.2） |
| 同名冲突 | 因新契约独立在 `_v2` 根下，**不需要**给旧类加 `Legacy` 后缀；新契约直接用设计里的名字 |

---

## 3. 与设计文档的映射

| 设计依据 | 本文落点 | 状态 |
| --- | --- | --- |
| 主设计 D1 / §2.1 包职责 | §4 包清单 | 已建立 |
| 主设计 D20 / §3.1 稳定 ID | `api.animation.id` | **已交付** |
| 主设计 I2 / §3.2 深不可变 | 全部契约 record + 防御性复制 | **已交付** |
| 主设计 §6.1 值源与曲线 | `api.animation.field` | **已交付** |
| 主设计 §3.1 / §3.1.1 文档形状与有类型轨道 | `api.animation.block`（含 `KeyframeTrack` / `AimTrack` / `ActiveTrack` / `CameraSourceTrack` / `ValueKeyframe` / `CameraSource` / `SampleArgs`） | **已交付**（余类型注册表与布局动画） |
| 主设计 §7.7 世界采样声明与读数 | `WorldSample`（`SampleArgs` 参数）+ `SampleReading` + 原因枚举 | **已交付**（余采样类型注册表、采集器、参数 Codec） |
| 主设计 §2.1 / §6.3 空间、锚点与注视状态 | `api.spatial`（含 `AimState`；`AimTarget` 文档指向 `playback.AimOverride`） | **已交付**（余注册表与解析器） |
| 主设计 §6.3 / §7.1 注视覆盖 | `api.animation.playback.AimOverride`（`Inherit` / `Disabled` / `Replace`） | 部分交付（Q5；其余播放契约待下一批） |
| 主设计 §3.4 / §6.1 求值最小查询面 | `api.animation.eval.TrackLookup` | 部分交付（Q6；帧事实与采样器待下一批） |
| 主设计 §4.7 注视交还与捕获 | `AimState.Off` 已表达交还；捕获工具属 edit | 部分交付（见 §1.4） |
| 主设计 §3.3 快照与只读句柄 | `api.animation.document` | 待下一批 |
| 主设计 §4.1–4.2 编辑与校验 | `api.animation.edit` | 待下一批（**下一批首要**） |
| 主设计 §3.4 / I5–I7 求值与帧事实 | `api.animation.eval`（余项） | 部分交付 |
| 主设计 §7.1–7.3 播放 | `api.animation.playback`（余项） | 部分交付 |
| 主设计 §8.1–8.4 存储 | `api.storage` | 待下一批 |
| 主设计 §5.1–5.3、§9.2–9.4 会话与扩展面 | `api.editor`、`editor.spi` | 分区已建立，契约待 U1–U3 |

---

## 4. 包清单

标注：**已交付** = 本批已提交审核；**余项** = 同包内尚未写的类型；**待下一批** = 整包未写。

### 4.1 `api.animation.id` —— 稳定 ID（D20）**已交付**

`DocumentObjectId`（sealed 标记）permits：
`RigId`、`TrackId`、`KeyId`、`PathNodeId`、`MarkerId`、`SampleId`、`AudioRefId`、`OverlayId`。
配套 `IdGenerator`（复制对象时生成新 ID 的宿主契约）。

### 4.2 `api.animation.field` —— 值源、曲线与符号表条目 **已交付**

| 文件 | 说明 |
| --- | --- |
| `ValueSource` | sealed：`NumberSource \| TrackRef` |
| `NumberSource` | sealed：`Constant \| Formula` |
| `Constant` / `Formula` / `TrackRef` | record |
| `CameraChannel` | 相机通道语义键枚举（`PATH_DISTANCE` / `POSITION_X..Z` / `ROTATION_X..Z` / `FOV`） |
| `Keyframe` | record（`keyId` / time / `NumberSource` value / 四曲柄参数 / 插值模式）；**仅数值曲线使用**，状态值用 `block.ValueKeyframe`；`value` 收窄为 `NumberSource`，`TrackRef` 类型上进不来（主设计 §6.1） |
| `Curve` | record（严格递增键 + 前后 wrap）+ 嵌套 `Interpolation` / `Wrap`；`span()` 只表示段落跨度，轨道结束时间取末键时刻 |
| `Variable` / `CustomFunction` | 符号表条目（**不含** `defaultValue`，见 §5 约定 8） |

**余项**：`AnimatedNumber`（常量值源或数值曲线的封闭分支，服务布局 x/y/width/height，
主设计 §3.1.2；随布局 / 编辑批次补齐，见 §1.4）。

### 4.3 `api.animation.path` —— 路径几何数据 **已交付**

`Path`、`PathNode`、`PathMode`。**纯数据，不含几何算法**（弧长与参数化在 `core`）。

### 4.4 `api.animation.expression` —— 编译与符号契约（预留）**已建立**

当前只有 `package-info.java`：设计 §2.1 把值源 / 编译 / 符号放在本包，本轮拆分把
值源、曲线、关键帧与符号条目放在 `api.animation.field`（见 §1.4）；本包留给
编译器、语法树与符号解析批次。求值层最小查询面按 Q6 裁决迁到 `api.animation.eval`。

### 4.5 `api.animation.block` —— 不可变文档模型（主设计 §3.1）**已交付**

| 文件 | 说明 |
| --- | --- |
| `CameraAnimation` | 文档根（机位 / 相机来源轨道 / 文档级轨道 / 符号 / 标记 / 采样 / 音轨引用 / 叠加） |
| `CameraRig` / `RigView` | 机位与其画面合成设置（含注视轨道与活动轨道） |
| `AnimationTrack` / `CurveTrack` | 轨道契约与内建数值曲线轨道；`ExtensionTrack` 不得使用内建类型 ID（Q4）；通道轨道必须显式提供文档内唯一 `TrackId`（无“用语义键当 ID”的便利工厂） |
| `KeyframeTrack` / `ValueKeyframe` | 有类型 STEP 轨道契约与原生值关键帧（共用 `TrackId` / `KeyId`） |
| `AimTrack` / `ActiveTrack` / `CameraSourceTrack` | 三个内建状态轨道（sealed 分支，类型 ID 固定） |
| `CameraSource` | 相机来源值：`Player \| Rig(RigId)`（默认 `Player`） |
| `MotionMode` / `DistanceMode` | 位置的来源与路径口径 |
| `Symbols` | 文档级符号表 |
| `Marker` / `MarkerKind` | 文档级标记（**不触发事件**） |
| `WorldSample` / `SampleArgs` | 声明式世界采样声明 + 参数名义接口（`kind == args.type()`，Q7） |
| `SampleReading` / `SampleUnknownReason` / `SampleCollectionErrorReason` | 采样读数（VALUE / UNKNOWN / COLLECTION_ERROR）与两类原因 |
| `AudioRef` | 参考音轨引用（仅数据与边界） |
| `OverlayElement` / `OverlayKind` | 成片叠加（遮幅 / 字幕 / 图片）；`endTime()` 保留原始区间，时长读 `durationContribution()`；`content` 暂为不透明引用，O1–O3 定型 |
| `KeyframeSequences`（包内） | 状态键排序 / 时间唯一 / 防御性复制的共用整理；不属公开契约 |

**余项**：`TrackTypeRegistry`（轨道类型注册表）、布局 `AnimatedNumber`
（`RigView.Rect` 的 x/y/width/height 随时间变化，随布局 / 编辑批次，见 §1.4）、
叠加内容类型（O1–O3）。

### 4.6 `api.spatial` —— 空间、锚点与姿态值 **已交付**

| 文件 | 说明 |
| --- | --- |
| `Vec3` / `Rotation` / `RotationMask` | 不可变数学值（双精度 / 弧度 YXZ / 逐轴掩码） |
| `Space` | sealed：`World \| Local` |
| `Anchor` | 开放不可变契约（`Identifier type()`） |
| `ResolvedAnchor` | 一帧内解析结果（位置 + 旋转 + 是否应用 partial tick） |
| `AimTarget` | 注视配置（目标 + 掩码 + 世界轴偏移）；请求覆盖见 `playback.AimOverride`（Q5） |
| `AimState` | 某一时刻的注视状态：`Off \| Target(AimTarget)`（供状态轨道与帧事实使用） |
| `ObstacleSettings` | 避障默认设置 |

**余项**：`AnchorTypeRegistry`（common 侧类型注册）、`AnchorResolver`（客户端解析器）、
`WorldPose` / `DocumentPose`（姿态类型，主设计 §6.2）。

### 4.7 `api.animation.edit` —— 编辑与诊断（主设计 §4）**待下一批**

`EditOp`（sealed 操作全集）、`EditOutcome`、`EditResult`、`Diagnostic`、`DiagnosticCode`、
`Severity`、`DocumentValidator`、`ValidationLayer`、`TrialRun`、`TrialResult`、
`TrialStatus`、`EditTools`（缓动 / 批量 / 烘焙 / 注视姿态转旋转键并关闭注视）。

### 4.8 `api.animation.document` —— 快照、发布与历史（主设计 §3.3、§4.5）**待下一批**

`AnimationSnapshot`、`AnimationHandle`、`AnimationDocumentStore`、`DocumentListener`、
`Revision`、`SessionSwitch`、`UndoHistory`、`EditTransaction`、`EditTarget`、
`PreviewState`、`Selection`、`KeySelection`。

### 4.9 `api.animation.eval` —— 求值与帧事实（主设计 §3.4、§6、I5–I7）**部分交付**

已交付：`TrackLookup`（Q6 从 expression 迁入；求值层最小查询面）。
它读某一份快照，绑定与失效由求值环境负责，文档根不再提供 `trackLookup()`。

**余项**：`FrameFacts`、`EvaluationContext`、`EvaluationKey`、`CurveSampler`、
`SampleStatus`、`ChannelSample`、`DocumentPose`、`SpatialReadings`、`DependencyGraph`、
`EvaluationCache`。

### 4.10 `api.animation.playback` —— 播放（主设计 §7.1–7.3）**部分交付**

已交付：`AimOverride`（Q5：`Inherit` / `Disabled` / `Replace`）。覆盖只在宿主层应用，
求值层只接收生效的 `AimState`；固定 `Replace` 不能靠写文档 `Off` 关闭。

**余项**：`PlaybackRequest`、`RigPlaybackOverrides`、`PlaybackService`、`PlaybackState`、
`PreparedFrame`、`RigPose`、`PlaybackFailure`、`PlaybackClock`、`EventBudget`、
`EventDispatcher`、`CameraControlClaims`、`CameraProbe`、`CameraCompositionService`、
`CompositionRequest`、`CompositionResult`、`RigRenderStatus`、`DegradeReason`。

### 4.11 `api.storage` —— 资源与恢复（主设计 §2.2、§8）**待下一批**

`AnimationResourceService`、`AnimationResourceEntry`、`ResourceId`、`LoadResult`、
`SaveResult`、`RecoverySlot`、`RecoveryService`、`RecoveryDecision`、`DiagnosticReport`。

### 4.12 `api.editor` —— 会话与后端（主设计 §5、§9）**待下一批**

`EditorSession`、`EditorUiBackend`、`EditorUiHost`、`InteractionRequest`、`InteractionId`、
`InteractionOption`、`SessionViewState`、`TimelineView`、`SnappingSettings`、`SnapSource`、
`PreviewRange`、`TrackVisibility`、`EditorToolState`、`EditorBackendSelector`。

### 4.13 `editor` / `editor.spi` / `editor.internal` —— 分区占位 **已建立**

本批只写三个 `package-info.java`（分区说明与依赖门禁口径）。
组件与宿主契约属 U1/U2。

---

## 5. 写作约定

1. **语言**：JavaDoc 用简体中文，与现有代码一致；类型名、方法名用英文。
2. **空标记**：受检包统一 `@NullMarked`（`org.jspecify.annotations`），可空处显式 `@Nullable`。
3. **不可变**：契约一律 `record` / `sealed interface`；集合字段在构造器里 `List.copyOf`，
   **不保留调用方容器**；不暴露可变数组或 JOML 可变类型。
4. **不写实现**：接口只声明契约；`record` 只做规范构造与派生（`with*`、查询）。
   需要算法的地方留到 `core`。
5. **不引用旧类型**：新契约不 import 旧根（不带 `_v2`）的任何类型。
6. **不引用客户端**：`api.*` 不 import `net.minecraft.client`。
   允许 `net.minecraft.resources.Identifier`、`net.minecraft.network.chat.Component`、
   DFU 的 `Codec` / `MapCodec`。
7. **`TrackRef` 不进关键帧字段**：`TrackRef` 只实现 `ValueSource`，不实现 `NumberSource`；
   `field.Keyframe.value` 与 `withValue` 的静态类型是 `NumberSource`，轨道读数在类型上
   进不了关键帧（审核修正 P1）。变量读轨道仍走 `Variable` 的 `ValueSource`。
8. **删除隐式兜底**：不提供 `evaluateOrFallback` 一类“失败就换个数”的入口；
   变量也不带 UI 初始值字段（主设计 §6.1：初始值不得作为运行期替代值）。
9. **序列化**：契约里只声明 `Codec` / `MapCodec` 的归属，不在本批校验往返。
10. **不接线**：不改任何旧文件，不新增实现类；新增文件全部落在 `_v2` 根下的新包内。
11. **显式身份**：新建机位 / 文档 / 状态轨道的工厂方法要求调用方提供 `TrackId`（`RigId` 同理），
    契约层不隐式生成 ID；只有复制类操作经宿主注入的 `IdGenerator` 生成新 ID。
12. **状态值不进数值通道**：注视 / 活动 / 来源使用原生值，不实现 `ValueSource`、
    不进 `field.Keyframe` 字段、不被 `TrackRef` 读取；禁止用浮点数或哈希伪装对象与布尔。
13. **读数不进文档**：`SampleReading` 是帧事实（`FrameFacts` 的采样表），
    文档只存 `WorldSample` 声明；读数不参与撤销、不随文档分享。
14. **名义参数接口**：世界采样参数实现 `SampleArgs`，必须是深不可变、值相等的静态参数；
    `WorldSample.kind` 与 `args.type()` 必须一致（构造器校验）。禁止在契约里暴露可变容器、
    数组或活对象（主设计 §3.2、§7.7）。
15. **类型 ID 保留**：内建轨道类型 ID（`curve` / `aim` / `active` / `camera_source`）
    不对扩展轨道开放；`ExtensionTrack.type()` 返回保留值属于 L2 错误（Q4 裁决）。

---

## 6. 交付与验证

### 6.1 本批交付文件

首批 53 个文件中 1 个移出（`expression/TrackLookup` 迁到 `eval`）；第二批累计修订 13 个文件
（含本轮 Q4–Q7 的少量补充与审核修正）；第二批累计新增 17 个主源码文件
（11 个在有类型轨道 / 采样读数切片，6 个在 Q4–Q7 裁决切片）。
`_v2` 现有 **69 个 Java 文件**：
58 个公开顶层类型 + 10 个 `package-info.java` + 1 个包内工具 `KeyframeSequences`。

第二批新增（有类型轨道 / 读数切片）：

- 有类型轨道：`KeyframeTrack`、`ValueKeyframe`、`AimTrack`、`ActiveTrack`、`CameraSourceTrack`
- 状态值：`spatial.AimState`、`block.CameraSource`
- 采样读数：`SampleReading`、`SampleUnknownReason`、`SampleCollectionErrorReason`
- 包内工具：`KeyframeSequences`（状态键排序 / 时间唯一 / 防御性复制）

Q4–Q7 裁决新增：

- `eval/TrackLookup`（从 `expression` 迁入，Q6）与 `eval/package-info`
- `playback/AimOverride`（Q5）与 `playback/package-info`
- `block/SampleArgs`（Q7）
- `expression/package-info`（迁移后保留包说明，预留编译器批次）

第二批修订：`CameraAnimation`（移除 `trackLookup()`）、`CameraRig`、`RigView`、
`AnimationTrack`（扩展轨道保留 ID 约束）、`CurveTrack`（移除无 ID 便利工厂）、
`WorldSample`（`SampleArgs` + `kind == args.type()`）、`OverlayElement`（访问器语义与时长贡献分离）、
`block/package-info`、`AimTarget`、`field.Curve`、`field.Keyframe`（`NumberSource` 收窄）、
`id.RigId`、`spatial/package-info`。
其中 `CurveTrack.duration()` 修正为“末键时刻”（原先返回 `Curve.span()`，单键曲线会得到 0 秒）。

### 6.1.1 本轮审核修正（review findings）

| 级别 | 问题 | 修正 |
| --- | --- | --- |
| P1 | `Keyframe.value` / `withValue` 仍为 `ValueSource`，`TrackRef` 可进入关键帧字段，`isFormula()` 与 `constantValue()` 行为失真 | 收窄为 `NumberSource` + 构造器非空检查；Javadoc 写明类型边界；新增 `block` 外的 `field/KeyframeContractTest`（反射断言 record 组件类型、`TrackRef` 非 `NumberSource`） |
| P2 | `OverlayElement.endTime()` 重写访问器，未启用时返回 0，创作数据被隐藏，访问器重建对象会得到非法区间 | 删除访问器重写，`endTime()` 恢复原始区间；新增派生查询 `durationContribution()`，`CameraAnimation.duration()` 改读它；新增 `OverlayElementContractTest` |
| P2 | `CurveTrack.ofChannel(CameraChannel)` 用语义键当 `TrackId`，多机位同通道会重复身份 | 删除该便利工厂；通道轨道必须显式提供文档内唯一 `TrackId`（`ofChannel(TrackId, String, CameraChannel, Curve)`）；新增双机位同通道测试 |

v2 契约测试类 5 个（26 个用例）；包含旧实现测试的全工程基线为 11 个 suite / 92 个用例：

- `block/StateTrackContractTest`：STEP 语义、键时间唯一、时长口径、统一轨道目录、读数三分支、
  双机位同通道需不同 `TrackId`
- `block/SampleArgsContractTest`：参数名义接口与 `kind == args.type()`（Q7）
- `block/OverlayElementContractTest`：访问器保留原始区间、时长贡献、文档时长集成（P2）
- `field/KeyframeContractTest`：`NumberSource` 类型边界、常量 / 公式键（P1）
- `playback/AimOverrideContractTest`：`Inherit` / `Disabled` / `Replace` 三态（Q5）

见 §4.1–4.6、§4.9–4.13 标 **已交付** / **部分交付** / **已建立** 的条目。

### 6.2 门禁验证

```powershell
# JAVA25 由环境提供（本机：D:\Running Enviroment\Java\25\Oracle）
.\gradlew.bat --offline compileJava test --console=plain
```

三条结构性门禁（每次改动后都应通过）：

1. **隔离**：`_v2` 下不得出现 `free_camera_api_tripod.`（不带 `_v2`）的引用；
   即新契约不依赖任何旧类型。
2. **客户端隔离**：`_v2` 下不得 import `net.minecraft.client.*`
   （主设计 I8 / §2.3）。
3. **旧代码零改动**：`git status --porcelain` 中旧根（`...free_camera_api_tripod/`）的已跟踪
   文件无条目。本批修订的是 `_v2` 已跟踪文件，因此 `M` 条目应全部位于 `_v2` 下；
   新增文件为 `??`。

验收标准：`BUILD SUCCESSFUL` + 上述三条门禁通过（本轮已通过：69 个主源码文件、11 个测试 suite / 92 个用例全绿；`_v2` 内无包循环）。

### 6.3 下一批（P1 余下契约）

按依赖顺序：

1. **`api.animation.edit`**：`EditOp`（sealed 操作全集，含状态轨道键的增删 / 移动与
   跨轨粘贴值类型兼容，以及编辑目标不存在的明确拒绝）、`EditOutcome`、`EditResult`、`Diagnostic`、`DocumentValidator`、
   `TrialRun`，以及 §4.7 的注视姿态捕获结果与复合操作。
   **这是提交语义的核心，建议单独一批审核。**
2. `api.animation.document`：快照、文档存储、历史、事务、预览与选择。
3. `api.animation.eval`（补齐余项）：帧事实、求值上下文、采样状态与采样表版本、姿态类型、
   依赖图、缓存（缓存身份须含有效 `AimState`、来源与活动集合）；`TrackLookup` 绑定由本包建立。
4. `api.animation.playback`（补齐余项）：播放请求、每路覆盖（`RigPlaybackOverrides` 引用
   已交付的 `AimOverride`）、准备帧、合成与相机契约；覆盖在宿主层应用，求值层只收生效状态。
5. `api.storage`：资源服务与恢复槽。
6. `api.editor`：会话与后端契约（含基于稳定 ID 的选择、状态轨道时间轴与配置）。
7. `block` / `spatial` 余项：`TrackTypeRegistry`、`AnchorTypeRegistry`、`AnchorResolver`、
   `WorldPose` / `DocumentPose`；`field.AnimatedNumber` 与 `RigView.Rect` 的布局动画
   （可与 UI / 编辑批次合并，见 §1.4）；采样参数 `MapCodec` 与注册表（S1）；叠加内容类型（O1–O3）。

<a id="p1-follow-up"></a>
### 6.4 后续代码修复、验证与优化清单

本节依据[复核日志](<review-log.md>) R4–R6 落实后续工作。这里只更新施工要求，不代表实现或新测试已完成，也不扩大 Q12 本批交付范围。

| 顺序 | 编号 / 批次 | 动作 | 完成条件 |
| --- | --- | --- | --- |
| 1 | A0：契约验证（高） | Keyframe 时间检查已恢复 !Float.isFinite，补构造/withTime 和数值/状态曲线边界测试 | NaN、±Infinity、负数均拒绝；±0 正规化且同轨冲突；合法乱序输入正确整理；新测实际执行通过后记录结果 |
| 2 | A4：模型清理（低） | withRig 未找到或同值替换返回原实例；真实替换保序，评估中间列表分配 | assertSame 验证无变化；真实替换不污染原文档；编辑目标不存在由提交层诊断，不能静默成功 |
| 3 | A1：查询清理（低至中） | duration 直接遍历 rigs 内联静态 enabled，不为内部遍历物化 enabledRigs 列表 | 与当前时长规则逐项等价，不删除 marker 贡献、不漏来源/状态/扩展/音轨/叠加，不以固定性能倍数验收 |
| 4 | A3：eval 绑定（中） | 建立快照绑定的 TrackId 索引，携带所属 Rig；不要让 block 反向依赖 eval | 重复 ID 拒绝，跨机位引用进入被引用轨道上下文；换档/撤销/预览候选一致重绑，不串旧文档 |
| 5 | A2：eval / 宿主按需 | 重复查询有证据时缓存静态 enabledRigs、curveTracks 和派生 duration | 根身份只用于文档派生数据；active(t)/来源/姿态/公式遵守主设计完整缓存键；缓存有界且生命周期释放 |
| 6 | A5、A7：契约说明/测试（低） | 命名对照说明；标记注释改为无直接执行副作用，时长仍按现设计 | 不把数值 STEP 错分为状态对象；测试远端标记延长当前时长及不产生事件副作用（后者随执行器批次验证） |
| 独立 | A6：产品裁决 | 标记是否退出播放时长、是否分离编辑范围 | **默认不改现规则**。另行决定后同步全部时长文档、定位/循环/越界标记可达性及新测试 |

性能调整先确认调用位置和频率；不得把 v1 调用当成 v2 已有热点。需要量化结论时保留可复现源码、真实数据规模、JDK/GC 参数、多轮结果与分配依据；原替身微基准的绝对值和比值均不作门槛。不要仅因 Stream 写法改循环，也不永久禁止某种实现。

本次文档同步前已复查 Keyframe 最新有限性检查，但尚未新增或重跑时间边界测试；§6.1.1、§6.2 的 92 用例是上一轮基线（含 v2 的 26 个用例），不能作为 A0 关闭依据。修复完成后按复核日志规则追加记录。

---

## 7. 审核要点与待决

本计划的审核要点须结合[最新复核日志](<review-log.md>) R4–R6 与 §3 当前待办阅读；历史“通过”不代表后续修改自动通过，待执行项见 §6.4。

### 7.1 审核要点（请重点看这几处）

1. **ID 类型粒度**：8 个独立 ID record 是否够用，是否需要 `ChannelKey` 之类第九种。
2. **`CameraChannel` 用枚举**：是否需要为第三方通道开放（开放则改用 `Identifier` + 注册表）。
3. **`Space.Local` 的默认锚点可空**：与“机位可无默认锚点”一致，
   但播放前必须由请求补锚点。
4. **`CameraAnimation.duration()` 的口径**：本批按“启用机位的通道 / 注视 / 活动轨道 +
   相机来源 + 文档轨道 + 标记 + 音轨 + 叠加”的最大结束时间实现，静态布局不延长。
   **禁用机位是否也该算进去？**
5. **有类型轨道形态**：`KeyframeTrack<T>` 是 sealed 接口 + `AimTrack` / `ActiveTrack` /
   `CameraSourceTrack` 三个 record；`ValueKeyframe<T>` 与数值 `field.Keyframe` 分离；
   `type()` 固定三个内建 ID，不走开放注册表。`at()` / `keyAt()` 是纯 STEP 查询，
   放在契约层供求值、预览与 UI 共用（时间域检查仍属求值层）。
6. **相机来源取代固定主机位**：`cameraSource` 轨道取代 `primaryRigId`；
   请确认“来源机位必须在全部被选区间活动、不得自动改选”的校验口径。
7. **`api.animation.document` 与 `api.animation.block` 的分工**：
   前者是快照 / 发布 / 事务，后者是纯数据。
8. **`SampleArgs` 名义接口与 `kind` 一致性**：采样参数已从 `Object` 改为 `SampleArgs`
   （Q7），`WorldSample` 构造器要求 `kind == args.type()`。请确认把一致性检查放在构造器
   是否可接受（另一种做法是留给 L1 校验器）。叠加 `content` 暂留不透明引用，O1–O3 定型。
9. **扩展轨道与类型 ID**：维持 sealed `AnimationTrack` + non-sealed `ExtensionTrack`（Q4）；
   请确认“扩展轨道的 `type()` 不得使用内建 ID”由 L2 校验拒绝的落点（批量校验而非构造器）。
10. **`SampleReading` 三分支**：`COLLECTION_ERROR` 与 eval 的 `SampleStatus` 如何对齐
    （本批先在读数层区分“可兜底 UNKNOWN”和“不可兜底的采集错误”）；`sampledAtTick`
    统一用游戏刻。
11. **`CurveTrack.duration()` 修正**：现为“末键时刻”，原先返回 `Curve.span()`，
    单键曲线会得到 0 秒。请确认末键时刻是期望的轨道结束时间口径。
12. **工厂方法要求显式 `TrackId`**：`CameraAnimation.create` / `CameraRig.create` 由调用方
    提供轨道 ID（不隐式生成）。若更希望接收 `IdGenerator`，可在实现批次调整。
13. **新建文档的相机来源**：`CameraAnimation.create` 按设计默认 `Player`，不再像旧实现那样
    自动把首台机位设为“主机位”。请确认这是期望的编辑器创建行为（作者随后显式把来源设为
    该机位，属于一次正常编辑）。
14. **`AimOverride` 三态位置与语义**：按 Q5 放在 `api.animation.playback`
    （`Inherit` / `Disabled` / `Replace`）；覆盖在宿主层应用，求值层只接收生效 `AimState`。
    请确认“固定 `Replace` 不能靠写文档 `Off` 关闭、须先清除覆盖”的提示落在 editor 命令。
15. **`TrackLookup` 迁 eval 后的绑定**：按 Q6 迁入 `api.animation.eval`，文档根已移除
    `trackLookup()`。绑定工厂归属（`EvaluationContext` 或独立工厂）在 eval 批次确定；
    请确认文档不再提供“把快照包成查询器”的便捷方法这一取舍。

### 7.2 已裁决（用户）

| # | 问题 | 结论 |
| --- | --- | --- |
| Q1 | 新旧类型同名同包冲突 | **新契约集中到 `_v2` 临时根包**，旧代码零改动；因此新契约直接用设计里的名字，**不给旧类加 `Legacy` 后缀**（§1.1） |
| Q2 | 音频数据结构是否进首批 | 数据结构**不进**（随 A0/A1 定）；但 `AudioRef` / `AudioRefId` 的**身份与边界**已按 D25「前期只保留边界」提交 |
| Q3 | 符号表（变量 / 自定义函数）是否进首批 | **进**。`block.Symbols` + `field.Variable` / `field.CustomFunction` 已提交，文档根 `CameraAnimation` 因此可以落地 |
| Q4 | 扩展轨道是否继续挂在 sealed `AnimationTrack` 下 | **维持现状**：`AnimationTrack` sealed + `non-sealed ExtensionTrack`；另补“扩展轨道 `type()` 不得使用内建类型 ID（`curve` / `aim` / `active` / `camera_source`）”的 L2 校验 |
| Q5 | 注视请求覆盖三态放哪 | **`api.animation.playback.AimOverride`**（`Inherit` / `Disabled` / `Replace(AimTarget)`）：覆盖只在宿主层应用，求值层只接收生效 `AimState`；不放进 `api.spatial` |
| Q6 | `TrackLookup` 放哪 | **迁 `api.animation.eval`**（方向 b1）；同时移除 `CameraAnimation.trackLookup()`，消除 `block ↔ expression` 包循环，`expression` 保留包说明留给编译器批次 |
| Q7 | `args` / `content` 用 `Object` 还是泛型 | **名义接口方向（C）**：采样参数改 `SampleArgs` 并校验 `kind == args.type()`；叠加 `content` 暂留不透明引用，随 O1–O3 定义封闭内容类型；**不采用泛型方案** |
| Q8 | 有类型状态轨道的 Java 形态 | **sealed `KeyframeTrack<T>` + 三个 record**（`AimTrack` / `ActiveTrack` / `CameraSourceTrack`）；类型 ID 固定、编译期排除值类型错配、键共用 `ValueKeyframe<T>` 与 `KeyId` |
| Q9 | 采样读数是否三分支 | **VALUE / UNKNOWN / COLLECTION_ERROR**：采集器缺失、异常、非法输出与超预算不可被 `sample(name, fallback)` 兜底 |
| Q10 | `sampledAt` 时间基准 | **游戏刻 `long`**，与 `resampleEveryTicks` 和 `FrameFacts` 同口径 |
| Q11 | 布局 `AnimatedNumber` 是否本轮补 | **本轮只记录为已知差异**（§1.4），随布局 / 编辑批次补 `field.AnimatedNumber` 与 `RigView.Rect` 改写 |
| Q12 | 本批范围 | **只补 aim / 来源 / 活动相关契约 + 世界采样读数**；`block` / `spatial` 其余余项与后续包留待下一批（§6.3） |

### 7.3 后续批次仍需定型（非本轮裁决）

Q4–Q7 已裁决关闭，以下属于实现批次内的形状问题，不影响本轮契约审核：

- **采样类型注册表 / 参数 `MapCodec` 形状**（S1）：`SampleArgs` 已定，注册表如何返回
  `MapCodec<? extends SampleArgs>`、两阶段解码（先 `kind` 后参数）如何写。
- **`TrackTypeRegistry` 形状与保留 ID 校验落点**（edit 批次 L2）：扩展轨道伪造内建
  `type()` 的拒绝方式（统一校验器，而非构造器）。
- **叠加内容封闭类型字段**（O1–O3）：遮幅比例、字幕文本 / 字体 / 对齐、图片资源与布局。
- **`AimOverride` 的编码**（playback 批次）：在 `RigPlaybackOverrides`、播放请求与网络包中的
  `StreamCodec`；以及“固定 `Replace` 覆盖”在捕获工具里的提示文案。
- **`TrackLookup` 绑定工厂**（eval 批次）：由 `EvaluationContext` 还是独立工厂提供，
  缓存身份如何包含快照身份，并按 §6.4 A3 携带轨道所属机位；重复 ID 不得被索引静默覆盖。
- **布局 `AnimatedNumber`**（布局 / 编辑批次）：`field.AnimatedNumber` 分支与
  `RigView.Rect` 的改写。

### 7.4 已确认：有类型时间轴关键帧与注视交还

本节替代旧版“本轮不做注视目标随时间变化”的结论。以主设计 §3.1.1、§4.7、§6.4 为准。
**本批已把该设计落为契约**（`KeyframeTrack`、`AimTrack`、`ActiveTrack`、`CameraSourceTrack`、
`AimState`、`ValueKeyframe`、`CameraSource`、`playback.AimOverride`）并有契约测试；
`core` / `editor` 实现与注视姿态捕获工具仍未开始。

- 注视、机位活动与玩家相机来源均为正式 AnimationTrack 的有类型关键帧轨道，共用稳定 ID、时间排序、编辑操作、选择、吸附、复制、历史与 Codec/校验。`KeyframeTrack<T>` 为契约名，不另建 StepProperty 存储或第二套状态键编辑器。
- 值类型分别为 Off / Target(AimTarget)、boolean、Player / Rig(RigId)，只允许 STEP；数值 Curve/Keyframe/KeyField 不必容纳这些原生值，不以数字伪装对象。TrackRef 只接受受支持数值通道。轨道只持久化一次，通用目录从各所有者派生。
- 目标 A/B 切换通过两个注视键表达，不需要 Tick 事件、公式或自定义时变 Anchor。按目标选择结果收集实际依赖，未选中的目标不要求当前存在；来源轨道与活动轨道按键边界整体校验。
- 前 1 秒注视、后 1 秒旋转：0 秒 Target(A)，1 秒 Off；旋转轨道 1 秒 R₁、2 秒 R₂。从恰好 1 秒起用当前旋转，不恢复历史值；Roll 始终按未被覆盖轴规则处理，未编写分量用基础姿态。
- 显式姿态捕获工具冻结基版本与 t 的事实，按关闭前有效目标求姿态、逆变换到文档参考系，一次写入 t 的完整 XYZ 常量键和 Off。若 t 已有 Off，可显式选左侧有效目标配置，但姿态仍求 t 时刻；覆盖/公式替换须确认，固定 Target 请求覆盖（`AimOverride.Replace`）先清除再重算。任何失败不部分提交，一次撤销完整恢复。
- 该工具固化编辑时世界事实，不保证动态目标重播时连续；不引入运行期上一帧姿态继承、平滑注视或镜头混合。

契约补齐顺序：field/block 的有类型轨道和值 → 编辑能力与冲突/Codec → eval/playback 无历史状态采样 → 捕获结果及复合 EditOp → editor 命令与诊断。**第一段已完成**（本批：有类型轨道、状态值、文档形状、采样读数，以及 Q4–Q7 落地的扩展轨道约束、`AimOverride`、`TrackLookup` 归位与 `SampleArgs` 名义接口）；后续切片不得与已交付字段并存两套模型，也不能在新旧两套模型中分别维护状态。
