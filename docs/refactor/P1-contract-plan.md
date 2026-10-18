# P1 契约计划与新契约目录（`_v2`）

> 本文是**施工约定**，不是设计文档。设计以 `docs/` 下三份设计为准；
> 本文固定三件事：新契约放哪、叫什么、怎么写，以及本批交付了什么供审核。
>
> 状态：**P1 第三批 document/edit、第四批 eval 与第五批 playback 契约已交付；C1–C4 修复已通过契约层复核，其他定型事项仍见 §7.5**。
> 第三批声明快照 / 存储 / 历史 / 事务 / 预览 / 选择、纯编辑操作 / 诊断 / 校验 / 试算；
> 第四批声明索引 / 帧事实 / 求值上下文 / 缓存键 / 采样与姿态等契约。这里只交付契约，
> 不代表存储、校验器、采样器、依赖图或缓存实现已完成；2026-10-18 审查四项的最终方案及关闭范围见 §6.5、复核日志 R12。
> `EditTools` 与布局 `AnimatedNumber` 尚未交付；布局按 Q14 安排在后续 block/spatial 批次，
> 不能据此认定源码已满足完整设计（见 §1.4、§4.7、§7.4）。
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
  api/animation/eval/        求值与帧事实（TrackLookup/TrackIndex/FrameFacts/采样/姿态）
  api/animation/block/       不可变文档模型（文档根、有类型状态轨道、采样声明/参数/读数）
  api/animation/playback/    播放请求、帧准备、事件、接管与合成契约
  api/animation/document/    快照、句柄、存储、历史、事务、预览与选择
  api/animation/edit/        纯编辑操作、诊断、分层校验与试算
  api/spatial/               空间、锚点、姿态值、注视状态
  editor/                    编辑器分区（本包 + spi + internal）
```

最终形态还会增加（P1 余项）：`api.storage`，以及 `core` 与业务 `editor`；
`playback` 补齐其余契约，`edit` 补 `EditTools`；`eval` 已列契约完成 C1–C4 范围复核，其余绑定协议待定，索引 / 采样 / 依赖图 / 缓存等实现仍待 `core` 批次。

### 1.4 与设计文档的差异清单

本文是唯一允许记录临时差异的地方。当前差异：

| 项 | 设计文档 | 当前 | 收尾动作 |
| --- | --- | --- | --- |
| 根包名 | `cn.anecansaitin.free_camera_api_tripod` | `...free_camera_api_tripod_v2` | 去掉 `_v2`（§1.2） |
| `field` 与 `expression` 分工 | §2.1 把值源 / 编译 / 符号都放在 `expression` | 值源、曲线、关键帧、符号条目在 `api.animation.field`；`expression` 只有包说明（预留编译器批次） | 编译器 / 符号批次再合并或迁移，届时更新 §1.3 |
| `Variable` / `CustomFunction` 位置 | 未指定（§6.1 只提“共享符号”） | `api.animation.field` | 若 `eval` 批次认为应独立，可迁到 `api.animation.symbol` |
| 音频数据结构 | A0–A1 阶段实施 | 只提交 `AudioRef` / `AudioRefId` 身份与边界，无 Codec、无播放路径 | 无需动作；符合 D25「前期只保留边界」 |
| 布局矩形动画 | §3.1.2 要求 `AnimatedNumber` 的 x/y/width/height 可随时间变化 | RigView.Rect 仍为静态几何，尚未交付 | Q11 为早期暂缓记录，已由 Q14 更新为后续 block/spatial 批次落地 |
| 采样参数 Codec | 类型自身的 `MapCodec` + common 侧注册 | `SampleArgs` 名义接口已交付（Q7）；Codec 与注册表未写 | 随 S1 采样类型注册表批次补齐 |
| 叠加元素内容 | O1–O3 阶段定义各类字段 | `OverlayElement.content` 仍是不透明 `Object` | 随 O1–O3 改为按种类拆分的封闭内容类型（Q7：不提前定型） |
| `eval` / `playback` 包完整性 | 设计要求求值与播放契约及宿主实现 | 第四批已交付 §4.9 所列 eval 契约，第五批已交付 §4.10 所列 playback 契约；算法、事件执行、合成与渲染仍无实现 | §6.5 C1–C4 契约修复已复核；算法与宿主实现留 `core` |
| `edit` 与 `document` 包依赖 | 主设计已按 Q13 同步顶层 edit 与 document 双向引用例外 | 实现与目标设计一致 | 不再是临时差异；仍禁止 api 依赖 core/editor |
| 修订号类型 | 主设计 §3.3 已同步 Revision 包装 | 事务 / 历史 / 快照统一使用，换档不重置 | 已对齐；存储运行实现仍待 core |
| `EditTools` 工具契约 | §4.6 / §4.7 要求缓动、批量、烘焙、注视姿态捕获 | 本批只交付 `EditOp` / 诊断 / 校验 / 试算核心，未定义工具请求 | 随 eval / editor 批次补（依赖冻结事实与采样） |
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
| 第二批范围（历史交付） | 按最新设计补齐 `block` / `spatial`：有类型状态轨道（aim / active / cameraSource）、`AimState`、`CameraSource`、世界采样读数；并落地 Q4–Q7 裁决：扩展轨道维持、`AimOverride` 入 playback、`TrackLookup` 迁 eval、采样参数用 `SampleArgs` 名义接口。布局 `AnimatedNumber` 与其余余项仍留后续批次（§1.4、§7.2） |
| 第三批范围（C1–C4 已复核） | `api.animation.document` + `api.animation.edit` 核心契约：快照 / 句柄 / 存储 / 历史 / 事务 / 预览 / 选择，纯编辑操作 / 诊断 / L1–L4 校验 / 试算；edit 按 P1 计划放顶层，接受与 document 的双向包引用（Q13） |
| 第四批范围（C1–C4 已复核） | eval 的 TrackIndex / FrameFacts / EvaluationContext / EvaluationKey / CurveSampler / ChannelSample / DocumentPose / SpatialReadings / DependencyGraph / EvaluationCache 等契约；实现与条件缓存未交付 |
| 同名冲突 | 因新契约独立在 `_v2` 根下，**不需要**给旧类加 `Legacy` 后缀；新契约直接用设计里的名字 |

---

## 3. 与设计文档的映射

| 设计依据 | 本文落点 | 状态 |
| --- | --- | --- |
| 主设计 D1 / §2.1 包职责 | §4 包清单 | 已建立 |
| 主设计 D20 / §3.1 稳定 ID | `api.animation.id` | **已交付** |
| 主设计 I2 / §3.2 深不可变 | record 与容器防御性复制 | 基础模型已交付；C3 已改用不可变 `LocalizedMessage`（无 Component），C4 已改用 `DiagnosticScope` |
| 主设计 §6.1 值源与曲线 | `api.animation.field` | **已交付** |
| 主设计 §3.1 / §3.1.1 文档形状与有类型轨道 | `api.animation.block`（含 `KeyframeTrack` / `AimTrack` / `ActiveTrack` / `CameraSourceTrack` / `ValueKeyframe` / `CameraSource` / `SampleArgs`） | **已交付**（余类型注册表与布局动画） |
| 主设计 §7.7 世界采样声明与读数 | `WorldSample`（`SampleArgs` 参数）+ `SampleReading` + 原因枚举 | **已交付**（余采样类型注册表、采集器、参数 Codec） |
| 主设计 §2.1 / §6.3 空间、锚点与注视状态 | `api.spatial`（含 `AimState`；`AimTarget` 文档指向 `playback.AimOverride`） | **已交付**（余注册表与解析器） |
| 主设计 §6.3 / §7.1 注视覆盖 | `api.animation.playback.AimOverride`（`Inherit` / `Disabled` / `Replace`） | 部分交付（Q5；其余播放契约待下一批） |
| 主设计 §3.4 / §6.1 求值最小查询面 | eval.TrackLookup 与第四批求值契约 | 契约已交付，C1–C4 修复范围已复核；查询绑定与采样实现未交付 |
| 主设计 §4.7 注视交还与捕获 | `AimState.Off` 已表达交还；捕获工具属 edit | 部分交付（见 §1.4） |
| 主设计 §3.3 快照、文档存储与历史 | `api.animation.document` | **已交付（契约）**（实现留 `core`） |
| 主设计 §4.1–4.5 编辑、校验、历史、工具 | `api.animation.edit` + `document` | **已交付（核心契约）**（余 `EditTools`） |
| 主设计 §4.3 试算 | `edit.TrialRun` / `TrialResult` / `TrialStatus` | **已交付（契约）** |
| 主设计 §3.4 / I5–I7 求值与帧事实 | `api.animation.eval`（`FrameFacts` / `EvaluationContext` / `CurveSampler` / `DocumentPose` / `TrackIndex` 等） | **已交付（契约）** |
| 主设计 §7.1–7.6 播放、事件与合成 | `api.animation.playback`（请求 / 覆盖 / 服务 / 帧 / 事件 / 接管 / 合成） | **已交付（契约）**（实现留 `core`） |
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
| `WorldPose` | 已补全并完成参考系合成的最终姿态（第五批 playback 依赖） |
| `ObstacleSettings` | 避障默认设置 |

**余项**：`AnchorTypeRegistry`（common 侧类型注册）、`AnchorResolver`（客户端解析器）。
`WorldPose` 已在第五批交付；`DocumentPose` 已在第四批 eval 交付，不再列为 spatial 余项。

### 4.7 `api.animation.edit` —— 编辑与诊断（主设计 §4）**已交付（核心）**

`EditOp`（sealed 操作全集：文档 / 机位 / 布局 / 通道 / 轨道 / 关键帧 / 符号 / 路径 /
标记 / 采样 / 音频 / 叠加 / 整份替换 / `Batch`）、`EditOutcome`、`EditResult`、
`LocalizedMessage`（Literal / Translatable + 受限样式）、`DiagnosticScope`（Document / File）、
`Diagnostic`（含 `Location` / `Fix`）、`DiagnosticCode`、`Severity`、`DocumentValidator`、
`ValidationLayer`、`TrialRun`、`TrialResult`、`TrialStatus`。

**余项**：`EditTools`（缓动 / 批量 / 烘焙 / 注视姿态转旋转键并关闭注视）。它依赖 eval 的
采样结果与冻结世界事实契约，随 eval / editor 批次补齐；本批不宣称已实现。

### 4.8 `api.animation.document` —— 快照、发布与历史（主设计 §3.3、§4.5）**已交付（契约）**

`AnimationSnapshot`、`AnimationHandle`、`AnimationDocumentStore`、`DocumentListener`、
`Revision`、`SessionSwitch`、`UndoHistory`（含 `Entry`）、`EditTransaction`、
`EditTarget`（sealed 稳定目标）、`PreviewState`、`Selection`、`KeySelection`。
事务与历史标签使用 `edit.LocalizedMessage`，不保存可变 `Component`。

存储实现（`volatile` 快照、通知队列、64 项 / 32 MiB 历史上限、发布线程）留到 `core`；
本批只声明契约。`AnimationDocumentStore` 与 `EditResult.Applied` 形成 edit ↔ document
双向包引用，按 Q13 接受。

### 4.9 `api.animation.eval` —— 求值与帧事实（主设计 §3.4、§6、I5–I7）**已交付（契约）**

`TrackLookup`（Q6 迁入）、`TrackIndex`（A3：`TrackId` → 轨道 + 所属机位 + 重复 ID 诊断）、
`FrameFacts`（维度 / 游戏刻 / partial tick / 采样表版本 / 机位事实 / 采样读数 / 有效性）、
`EvaluationContext`、`EvaluationKey`、`CurveSampler`、`SampleStatus`、`ChannelSample`、
`DocumentPose`（含 `PositionMask`）、`SpatialReadings`、`DependencyGraph`、`EvaluationCache`。

采样器、依赖图、缓存与姿态合成的实现留到 `core`；本批只声明契约，不宣称帧求值可用。

### 4.10 `api.animation.playback` —— 播放（主设计 §7.1–7.6）**已交付（契约）**

`PlaybackRequest`、`RigPlaybackOverrides`、`AimOverride`（Q5）、`PlaybackService`（含 `StartResult`）、
`PlaybackState`、`PlaybackClock`、`PreparedFrame`（含 `Event`）、`RigPose`、`PlaybackFailure`
（含 `Reason`）、`EventBudget`、`EventDispatcher`（含 `Result`）、`CameraControlClaims`（含 `Claim`）、
`CameraProbe`、`CameraCompositionService`、`CompositionRequest`、`CompositionResult`、
`RigRenderStatus`、`DegradeReason`。

实现（时间推进、事件执行、多路合成、探针与接管仲裁）留到 `core`；本批只声明契约，
不宣称播放链路、渲染或事件执行可用。

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
   允许必要的 Minecraft common 类型（如 Identifier）及 DFU 的 Codec / MapCodec；诊断消息与动作标签使用 LocalizedMessage，不保存 Component，显示适配留在宿主。
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
（含 Q4–Q7 的少量补充与审核修正）；第二批累计新增 17 个主源码文件。
第三批新增 27 个主源码文件（`document` 13 个 + `edit` 14 个；含 C3/C4 追加的
`LocalizedMessage`、`DiagnosticScope`）。
第四批新增 11 个主源码文件（`eval` 余项：`TrackIndex`、`FrameFacts`、`EvaluationContext`、
`EvaluationKey`、`CurveSampler`、`SampleStatus`、`ChannelSample`、`DocumentPose`、
`SpatialReadings`、`DependencyGraph`、`EvaluationCache`）。
第五批新增 18 个主源码文件（`playback` 17 个 + `spatial.WorldPose`）。
`_v2` 现有 **126 个 Java 文件**：
112 个公开顶层类型 + 13 个 `package-info.java` + 1 个包内工具 `KeyframeSequences`。

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

第三批新增（document / edit）：

- `document/`：`Revision`、`AnimationSnapshot`、`AnimationHandle`、`DocumentListener`、
  `UndoHistory`（含 `Entry`）、`SessionSwitch`、`EditTarget`、`EditTransaction`、
  `PreviewState`、`KeySelection`、`Selection`、`AnimationDocumentStore`、`package-info`
- `edit/`：`EditOp`（sealed 操作全集）、`EditOutcome`、`EditResult`、`Diagnostic`
  （含 `Location` / `Fix`）、`LocalizedMessage`、`DiagnosticScope`、`DiagnosticCode`、`Severity`、`ValidationLayer`、
  `DocumentValidator`、`TrialStatus`、`TrialResult`、`TrialRun`、`package-info`

第三批未交付：`EditTools`（缓动 / 批量 / 烘焙 / 注视姿态捕获）与文档存储实现。

第四批新增（eval 余项）：

- `TrackIndex`（A3：含重复 ID 诊断与所属机位）、`FrameFacts` / `EvaluationContext` /
  `EvaluationKey`、`CurveSampler` / `SampleStatus` / `ChannelSample`、`DocumentPose` /
  `SpatialReadings`、`DependencyGraph` / `EvaluationCache`
- `eval/package-info` 更新为完整契约说明

第四批未交付：采样器、依赖图、姿态合成与缓存的实现（`core`）；A2 的条件缓存仍待宿主证据。

第五批新增（playback）：

- `playback/`：`PlaybackRequest`、`RigPlaybackOverrides`、`PlaybackService`、`PlaybackState`、
  `PlaybackClock`、`PreparedFrame`、`RigPose`、`PlaybackFailure`、`EventBudget`、
  `EventDispatcher`、`CameraControlClaims`、`CameraProbe`、`CameraCompositionService`、
  `CompositionRequest`、`CompositionResult`、`RigRenderStatus`、`DegradeReason`
- `spatial/WorldPose`：最终姿态

第五批未交付：时间推进、事件执行器、多路合成、探针与接管仲裁实现（`core`）。

第二批修订：`CameraAnimation`（移除 `trackLookup()`）、`CameraRig`、`RigView`、
`AnimationTrack`（扩展轨道保留 ID 约束）、`CurveTrack`（移除无 ID 便利工厂）、
`WorldSample`（`SampleArgs` + `kind == args.type()`）、`OverlayElement`（访问器语义与时长贡献分离）、
`block/package-info`、`AimTarget`、`field.Curve`、`field.Keyframe`（`NumberSource` 收窄）、
`id.RigId`、`spatial/package-info`。
其中 `CurveTrack.duration()` 修正为“末键时刻”（原先返回 `Curve.span()`，单键曲线会得到 0 秒）。

### 6.1.1 第二批已修正项与当前测试基线

| 级别 | 问题 | 修正 |
| --- | --- | --- |
| P1 | `Keyframe.value` / `withValue` 仍为 `ValueSource`，`TrackRef` 可进入关键帧字段，`isFormula()` 与 `constantValue()` 行为失真 | 收窄为 `NumberSource` + 构造器非空检查；Javadoc 写明类型边界；新增 `block` 外的 `field/KeyframeContractTest`（反射断言 record 组件类型、`TrackRef` 非 `NumberSource`） |
| P2 | `OverlayElement.endTime()` 重写访问器，未启用时返回 0，创作数据被隐藏，访问器重建对象会得到非法区间 | 删除访问器重写，`endTime()` 恢复原始区间；新增派生查询 `durationContribution()`，`CameraAnimation.duration()` 改读它；新增 `OverlayElementContractTest` |
| P2 | `CurveTrack.ofChannel(CameraChannel)` 用语义键当 `TrackId`，多机位同通道会重复身份 | 删除该便利工厂；通道轨道必须显式提供文档内唯一 `TrackId`（`ofChannel(TrackId, String, CameraChannel, Curve)`）；新增双机位同通道测试 |

v2 契约测试类 13 个（74 个用例）；包含旧实现测试的全工程基线为 19 个 suite / 140 个用例：

- `block/StateTrackContractTest`：STEP 语义、键时间唯一、时长口径、统一轨道目录、读数三分支、
  双机位同通道需不同 `TrackId`
- `block/SampleArgsContractTest`：参数名义接口与 `kind == args.type()`（Q7）
- `block/OverlayElementContractTest`：访问器保留原始区间、时长贡献、文档时长集成（P2）
- `block/CameraAnimationDurationTest`：时长取最大、禁用机位不参与、远端标记（A1/A7）
- `block/CameraAnimationReplacementTest`：`withRig` 无变化身份与保序替换（A4）
- `field/KeyframeContractTest`：`NumberSource` 类型边界、时间边界、常量 / 公式键（P1/A0）
- `field/CurveContractTest`：乱序整理、输入不变、`+0/-0` 重复（A0）
- `playback/AimOverrideContractTest`：`Inherit` / `Disabled` / `Replace` 三态（Q5）
- `edit/EditContractTest`：操作时间、`Batch` 复制、拒绝诊断、诊断作用域与修复基版本（第三批）
- `document/DocumentContractTest`：Revision、快照、选择、预览、事务与历史标签（第三批）
- `edit/LocalizedMessageContractTest`：不可变消息、受限样式、翻译参数复制（§6.5 C3）
- `playback/PlaybackContractTest`：请求校验、覆盖默认、时钟、帧复制、事件预算、合成结果、
  `WorldPose`（第五批）
- `eval/EvalContractTest`：`ChannelSample` 三态、`FrameFacts` 复制与 partial tick、
  `DocumentPose` 掩码、`SpatialReadings`、`EvaluationKey` / `EvaluationContext`（第四批）

见 §4.1–4.13 标 **已交付** / **部分交付** / **已建立** 的条目。

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

验收标准为编译、测试及依赖边界检查通过，并关闭对应审查项。第五批 playback 契约后实际运行 `compileJava test`：19 个 suite / 140 个用例通过，其中 v2 为 13 / 74；C1–C4 契约层复核关闭见 R12，playback 契约待人工审核。该基线不代表 time 推进、事件执行器、合成或 core 存储已验证。

### 6.3 下一批（P1 余下契约）

§6.5 C1–C4 契约问题已复核关闭；后续结合 §7.5 的未决绑定/实现事项，按依赖补齐契约：

1. `api.storage`：资源服务、保存 / 载入结果与恢复槽。
2. `api.editor`：会话与后端契约（含基于稳定 ID 的选择、状态轨道时间轴与配置）。
3. `api.animation.edit` 余项：`EditTools`（缓动 / 批量 / 烘焙 / 注视姿态转旋转键并关闭注视）。
   它依赖 eval 的采样、冻结世界事实与 `EditOp` 复合提交；不提前发明工具请求类型。
4. `block` / `spatial` 余项：`TrackTypeRegistry`、`AnchorTypeRegistry`、`AnchorResolver`；
   `field.AnimatedNumber` 与 `RigView.Rect` 的布局动画（Q14：随后落地）；采样参数 `MapCodec`
   与注册表（S1）；叠加内容类型（O1–O3）。

<a id="p1-follow-up"></a>
### 6.4 后续代码修复、验证与优化清单

本节跟踪复核日志 R4–R9 的实施状态；Q12 仅限定历史第二批，第三批由 Q13 扩展，第四批范围见 §2。A3 的契约交付与实现完成分别记录，不能混写。

| 顺序 | 编号 / 批次 | 动作 | 完成条件 |
| --- | --- | --- | --- |
| 已落地 | A0：时间边界测试（R7） | Keyframe 时间检查已恢复 !Float.isFinite，补构造/withTime 和数值/状态曲线边界测试 | NaN、±Infinity、负数均拒绝；±0 正规化且同轨冲突；合法乱序输入正确整理；新测实际执行通过后记录结果 |
| 已落地 | A4：模型清理（R7） | withRig 未找到或同值替换返回原实例；真实替换保序，评估中间列表分配 | assertSame 验证无变化；真实替换不污染原文档；编辑目标不存在由提交层诊断，不能静默成功 |
| 已落地 | A1：查询清理（R7） | duration 直接遍历 rigs 内联静态 enabled，不为内部遍历物化 enabledRigs 列表 | 与当前时长规则逐项等价，不删除 marker 贡献、不漏来源/状态/扩展/音轨/叠加，不以固定性能倍数验收 |
| 部分完成 | A3：索引契约已交付，绑定实现待后续 | 建立快照绑定的 TrackId 索引，携带所属 Rig；不要让 block 反向依赖 eval | 重复 ID 拒绝，跨机位引用进入被引用轨道上下文；换档/撤销/预览候选一致重绑，不串旧文档 |
| 条件待办 | A2：eval / 宿主按需 | 重复查询有证据时缓存静态 enabledRigs、curveTracks 和派生 duration | 根身份只用于文档派生数据；active(t)/来源/姿态/公式遵守主设计完整缓存键；缓存有界且生命周期释放 |
| 已落地 / 部分待验 | A5、A7：说明与时长测试完成，事件验证延期 | 命名对照说明；标记注释改为无直接执行副作用，时长仍按现设计 | 不把数值 STEP 错分为状态对象；测试远端标记延长当前时长及不产生事件副作用（后者随执行器批次验证） |
| 独立 | A6：产品裁决 | 标记是否退出播放时长、是否分离编辑范围 | **默认不改现规则**。另行决定后同步全部时长文档、定位/循环/越界标记可达性及新测试 |

性能调整先确认调用位置和频率；不得把 v1 调用当成 v2 已有热点。需要量化结论时保留可复现源码、真实数据规模、JDK/GC 参数、多轮结果与分配依据；原替身微基准的绝对值和比值均不作门槛。不要仅因 Stream 写法改循环，也不永久禁止某种实现。

当前状态：A0/A1/A4/A5 的模型修正与回归测试已随 R7 落地；A7 的注释和时长测试已完成，事件执行副作用验证仍待执行器。A3 已随 R9 交付 TrackIndex 契约，但索引构建、绑定与运行验证尚未完成；A2 仍等待宿主重复查询证据。R13 记录 140 个全工程用例（v2 74），R11 为 131（v2 65），不再沿用旧数字。

### 6.5 2026-10-18 第三/四批审查：历史问题与最终修复

下表保留最初问题及验收要求，当前状态以 §6.5.1 和复核日志 R12 为准：C1–C4 的契约修复已通过复核，不再列为待修复代码。存储/采样实现尚未交付，不能用契约通过证明未来运行正确。

| 编号 | 级别 | 原问题（历史） | 修正要求与验收 |
| --- | --- | --- | --- |
| C1 | P1 | SessionSwitch 与 AnimationDocumentStore 注释要求换档重建修订号序列，与同 store 单调递增及仅数值基版本检查冲突 | 同 store 换档继续递增，或显式加入作用域并全链校验；旧文档请求在换档后始终被拒绝，撤销/重做不回退发布身份 |
| C2 | P1 | EvaluationKey 含帧序号但没有采样时间，同帧同轨不同时间可能命中同一键 | 增加正规化有限采样时间或明确独立采样身份；同帧固定事实下 t=0 / t=1 键不相等，值与错误缓存均不串用；具体签名在修正时定型 |
| C3 | P2 | Diagnostic.message / Fix.label / EditTransaction.label / UndoHistory.Entry.label 保存并返回可变 Component | 使用不可变消息描述或输入、输出双向隔离；修改传入对象或访问器返回对象不能改变既有记录，仅构造时 copy 不足 |
| C4 | P2 | Diagnostic.Location 无基版本，版本仅在可选 Fix 中；无 Fix 的定位诊断无法检查过期 | 为诊断或强制批次信封携带适用版本，由校验/提交上下文绑定；异步结果和换档同 ID 场景不定位到错误对象，纯文件诊断与候选诊断的作用域明确 |

源码落点：document 的 SessionSwitch / AnimationDocumentStore / EditTransaction / UndoHistory，edit 的 Diagnostic，以及 eval 的 EvaluationKey。修复记录必须与 §6.1.1 的历史已修项分开；不得提前增加“已裁决”编号或宣称已修。

### 6.5.1 C1–C4 最终修正与契约复核（2026-10-18）

| 编号 | 修正内容 | 对应验证 |
| --- | --- | --- |
| C1 | `Revision` / `SessionSwitch` / `AnimationDocumentStore` 契约统一为：同一 store 换档继续递增，不重置、不回退；旧 `expectedRevision` 换档后永久失效；撤销/重做生成新序号 | 契约注释一致；存储实现未交付，暂无运行测试 |
| C2 | `EvaluationKey` 增加 `sampleTime`（有限、非负、`-0.0 → 0.0`）；同帧同轨不同时间不再命中同一键 | `EvalContractTest.evaluationKeyDistinguishesSampleTimesWithinOneFrame`、非法时间拒绝 |
| C3 | 不可变消息描述：新增 `LocalizedMessage`（Literal / Translatable + 文本/数字/布尔/嵌套参数 + 颜色/粗体/斜体/下划线），`Diagnostic.message`、`Fix.label`、`EditTransaction.label`、`UndoHistory.Entry.label` 全部改用该类型；契约中不再保存 `Component`，宿主显示时渲染 | `EditContractTest`（作用域/修复）、`LocalizedMessageContractTest`（参数复制、受限样式、非法值）、`DocumentContractTest`（标签） |
| C4 | 显式作用域：新增 `DiagnosticScope`（`Document(Revision)` / `File(resource)`）；`Diagnostic` 用 `scope` 替代可空基版本；`File` 作用域不得带对象 ID 或 `Fix`，`Document` 作用域的 `Fix` 版本必须与作用域一致 | `EditContractTest.diagnosticWithFixBindsScopeRevision`、`fileScopeRejectsFixAndObjectLocation`、`diagnosticFixCarriesBaseRevision` |

复核结论：C1/C2 在首轮复核中通过，C3/C4 在二次修改后通过；本次只关闭这四项契约问题，不宣称整个 P1 或运行实现完成。C3 最终采用封闭不可变消息树，C4 最终采用非空显式作用域；R10 的双向 Component.copy 与可空版本方案已由 R11/R12 取代，不得回退。

仍待后续验证：同 store 换档旧请求拒绝；消息描述到 Component 的渲染；异步诊断/预览的作用域绑定；缓存和跨机位查询实现。非阻断测试补强：参数输出列表不可修改、多层嵌套消息输入隔离、Document(null) 拒绝、无 Fix 的文档对象诊断合法分支。以上尚未新增用例，不改变 131/65 基线。

---

## 7. 审核要点与待决

本计划的审核要点须结合复核日志 R4–R12、§6.4 实施状态与 §6.5 最终修复阅读；第三/四批相较上次的其余人工审核范围汇总于 §7.5。C1–C4 已关闭不代表其他协议或后续代码自动通过。

### 7.1 审核要点（请重点看这几处）

1. **ID 类型粒度**：8 个独立 ID record 是否够用，是否需要 `ChannelKey` 之类第九种。
2. **`CameraChannel` 用枚举**：是否需要为第三方通道开放（开放则改用 `Identifier` + 注册表）。
3. **`Space.Local` 的默认锚点可空**：与“机位可无默认锚点”一致，
   但播放前必须由请求补锚点。
4. **`CameraAnimation.duration()` 的口径**：本批按“启用机位的通道 / 注视 / 活动轨道 +
   相机来源 + 文档轨道 + 标记 + 音轨 + 叠加”的最大结束时间实现，静态布局不延长。
   按当前设计静态禁用机位不贡献时长，R7 已有测试；改变该规则须另行裁决。
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
16. **`EditOp` 粒度**：现为 sealed + 约 50 个嵌套 record（文档 / 机位 / 通道 / 轨道 /
    关键帧 / 符号 / 路径 / 标记 / 采样 / 音频 / 叠加 + `Batch`）。请确认是否需要更少的
    通用操作（如只留 `ReplaceTrack` / `ReplaceDocument` + 关键帧操作），或保持逐类可审。
17. **`Revision` 与存储方法面**：主设计 §3.3 已对齐 document.Revision 包装；同 store 换档、撤销、重做继续递增是 C1 已关闭契约。AnimationDocumentStore 提供 submit / switchDocument / undo / redo / 监听 / 历史视图，运行验证及回调期间提交协议仍待 core 批次，不再把 long 与包装类型的旧文档差异列为当前问题。
18. **edit ↔ document 双向包引用（Q13）**：`AnimationDocumentStore` 收 `EditOp` / 返回
    `EditResult`，`EditResult.Applied` 持 `AnimationSnapshot`。若架构检查要求单向依赖，
    需把 `EditResult` 拆到 document 或把存储入口迁到 edit。
19. **`EditTarget` / `Selection` 覆盖面**：目标为稳定 ID 的 sealed 分支（文档 / 机位 / 轨道 /
    键 / 标记 / 采样 / 音频 / 叠加 / 变量 / 函数 / 路径节点），选择含机位 / 轨道 / 键 / 标记。
    请确认是否还需布局字段、预览锚点或世界采样参数的细粒度目标。
20. **`FrameFacts` 形状**：维度 `Identifier` + 游戏刻 + partial tick + 采样表版本 +
    机位事实（锚点 / 目标 / 生效 `AimState`）+ 采样读数 + 有效性。请确认世界实例身份是否
    还需要独立字段，以及“实际所需”机位集合是否足以支持缓存失效。
21. **`DocumentPose` / `SpatialReadings`**：位置与旋转分别用 `PositionMask` / `RotationMask`，
    FOV 用 `ChannelSample`；空间读数用可空字段表示上下文缺失。请确认“缺失”与“错误”不会
    在消费端被混淆。
22. **`TrackIndex` / `EvaluationKey` / `EvaluationCache`**：A3 索引接口含所属机位与重复 ID
    诊断；缓存键字段较多且要求非负身份。请确认这些身份字段（快照 / 上下文 / 帧 / 事实 /
    采样表版本）是否应由 `EvaluationContext` 统一分配，避免实现各自拼装。
23. **`LocalizedMessage` 的受限样式**：只支持颜色 / 粗体 / 斜体 / 下划线，不支持 click /
    hover / selector / NBT / 自定义 contents。请确认诊断与动作标签不需要更富的组件结构；
    宿主渲染器负责转换。
24. **`DiagnosticScope` 作用域**：`Document(Revision)` 允许对象 / 字段定位与 `Fix`，
    `File(resource)` 不允许对象 ID 与 `Fix`；`Fix.baseRevision` 必须等于作用域版本。
    请确认文件诊断的资源身份用 `String` 是否足够（storage 批次可能引入 `ResourceId`）。
25. **`PlaybackRequest` 与 `PlaybackService`**：请求持有 `CameraAnimation` 而非快照，`start`
    接收 `Revision` 并由同一 store 换档；`StartResult` 强制 accepted / failure 一致。请确认
    请求载入与文档切换的原子边界，以及未知 `RigId` 覆盖的拒绝位置。
26. **`PreparedFrame.Event` 仅含 `TrackId + time`**：事件载荷留在轨道，执行器按轨道类型解析；
    `EventDispatcher.Result` 报告已分发数与失败。请确认这是否足够支撑第三方 Tick 轨道，
    以及执行器异常时“部分执行”的报告口径。
27. **`WorldPose` / `CompositionResult` / `RigRenderStatus`**：`WorldPose.fov` 用度且必须为正；
    合成结果强制 presented / failure 一致；降级状态带 `sampledAtGameTick`。请确认 FOV 单位、
    渲染目标身份与旧帧时间标识是否足够。

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
| Q13 | `edit` 包位置与第三批节奏 | **按 P1 计划放顶层 `api.animation.edit`**（不采用设计 §2.1 的 `document` 子包建议）；先做 edit + document 一批，接受 `edit ↔ document` 双向包引用；`EditTools` 随后续批次补 |
| Q14 | 布局 `AnimatedNumber` 落地时机 | **本轮随后落地**（block/spatial 余项批次）：新增 `field.AnimatedNumber` 并改写 `RigView.Rect`；不再只记录差异 |

### 7.3 后续批次仍需定型（非本轮裁决）

Q4–Q7、Q13 已裁决关闭，以下仍需在后续批次定型；若涉及 §6.5 正确性问题或 §7.5 的绑定协议，必须先明确契约再接入实现，不能统一视作“不影响审核”：

- **edit ↔ document 依赖拆分**（若架构检查禁止双向引用）：拆 `EditResult` 或迁存储入口。
- **`EditTools` 请求形状**（eval / editor 批次）：烘焙区间 / 步长 / 预算，以及注视捕获的
  冻结事实、覆盖清理与复合 `EditOp`。
- **文档存储实现细节**（`core`）：`volatile` 快照、发布线程、通知排队、64 项 / 32 MiB 历史上限。
- **`EditOp` 与轨道能力分派**：数值 / 状态键、第三方轨道的值类型兼容与跨轨粘贴规则。
- **采样类型注册表 / 参数 `MapCodec` 形状**（S1）：`SampleArgs` 已定，注册表如何返回
  `MapCodec<? extends SampleArgs>`、两阶段解码（先 `kind` 后参数）如何写。
- **`TrackTypeRegistry` 形状与保留 ID 校验落点**（edit 批次 L2）：扩展轨道伪造内建
  `type()` 的拒绝方式（统一校验器，而非构造器）。
- **叠加内容封闭类型字段**（O1–O3）：遮幅比例、字幕文本 / 字体 / 对齐、图片资源与布局。
- **`AimOverride` 的编码**（playback 批次）：在 `RigPlaybackOverrides`、播放请求与网络包中的
  `StreamCodec`；以及“固定 `Replace` 覆盖”在捕获工具里的提示文案。
- **`TrackLookup` 绑定工厂与 `TrackIndex` 实现**（eval 实现批次）：由 `EvaluationContext` 还是
  独立工厂提供；索引须先校验唯一 ID、携带所属机位，重复 ID 不得静默覆盖；`Optional` 分配次数
  不作承诺。
- **布局 `AnimatedNumber`**（Q14，block/spatial 余项批次）：`field.AnimatedNumber` 分支与
  `RigView.Rect` 的改写。

### 7.4 已确认：有类型时间轴关键帧与注视交还

本节替代旧版“本轮不做注视目标随时间变化”的结论。以主设计 §3.1.1、§4.7、§6.4 为准。
**第二批已把该设计落为契约**（`KeyframeTrack`、`AimTrack`、`ActiveTrack`、`CameraSourceTrack`、
`AimState`、`ValueKeyframe`、`CameraSource`、`playback.AimOverride`）；
**第三批已补齐编辑 / 文档链路**（`EditOp`、`EditOutcome`、`EditResult`、`Diagnostic`、
`DocumentValidator`、`TrialRun`、`AnimationSnapshot` / `AnimationDocumentStore` / 历史 / 事务 /
预览 / 选择）；`core` / `editor` 实现与注视姿态捕获工具仍未开始。

- 注视、机位活动与玩家相机来源均为正式 AnimationTrack 的有类型关键帧轨道，共用稳定 ID、时间排序、编辑操作、选择、吸附、复制、历史与 Codec/校验。`KeyframeTrack<T>` 为契约名，不另建 StepProperty 存储或第二套状态键编辑器。
- 值类型分别为 Off / Target(AimTarget)、boolean、Player / Rig(RigId)，只允许 STEP；数值 Curve/Keyframe/KeyField 不必容纳这些原生值，不以数字伪装对象。TrackRef 只接受受支持数值通道。轨道只持久化一次，通用目录从各所有者派生。
- 目标 A/B 切换通过两个注视键表达，不需要 Tick 事件、公式或自定义时变 Anchor。按目标选择结果收集实际依赖，未选中的目标不要求当前存在；来源轨道与活动轨道按键边界整体校验。
- 前 1 秒注视、后 1 秒旋转：0 秒 Target(A)，1 秒 Off；旋转轨道 1 秒 R₁、2 秒 R₂。从恰好 1 秒起用当前旋转，不恢复历史值；Roll 始终按未被覆盖轴规则处理，未编写分量用基础姿态。
- 显式姿态捕获工具冻结基版本与 t 的事实，按关闭前有效目标求姿态、逆变换到文档参考系，一次写入 t 的完整 XYZ 常量键和 Off。若 t 已有 Off，可显式选左侧有效目标配置，但姿态仍求 t 时刻；覆盖/公式替换须确认，固定 Target 请求覆盖（`AimOverride.Replace`）先清除再重算。任何失败不部分提交，一次撤销完整恢复。
- 该工具固化编辑时世界事实，不保证动态目标重播时连续；不引入运行期上一帧姿态继承、平滑注视或镜头混合。

契约进度：field/block 第二批、document/edit 第三批、eval 第四批及 playback 第五批类型均已交付；第三/四批的 §6.5 C1–C4 已通过契约复核；§7.5 其余定型事项仍待审核。A3 仅完成索引契约，core 绑定实现未交付。第五批 playback 也只交付契约，时间推进、事件执行、合成与探针实现仍在 `core`；后续补 storage / editor、按 Q14 补布局动画，再推进 EditTools。不得将契约交付写成算法、发布或播放链路已实现。


### 7.5 相较上一轮新增的人工审核清单

本节是待审核清单，不是用户已经裁决的事项；§6.5 C1–C4 为已关闭的契约修复，不再作为待裁决项；本表其余协议和实现事项仍待定型。

| 范围 | 本轮需审核内容 | 需明确的产物 |
| --- | --- | --- |
| document 存储/事务 | 同步 EditResult 与回调期间排队语义；动作标签如何从事务进入历史；Revision 包装与换档入口 | 不阻塞发布线程的提交协议、标签传递路径、C1 的版本规则 |
| edit 操作能力 | 约 50 个操作的粒度、状态键单键改值入口、跨轨粘贴/替换能力与类型兼容 | 可落地的能力分派及原子失败规则，不强迫状态进入数值值源 |
| 试算 | FAILED/错误诊断与 PASSED/无错误的一致性；采样范围/环境报告；hasError 注释与实现口径 | TrialResult 不变量与覆盖报告载体，不把点数等同完整覆盖 |
| 求值绑定 | CurveSampler 方法无 context 参数时实例如何绑定快照/事实/轨道所属 Rig；跨机位与换帧如何重绑 | 显式采样请求或明确实例绑定协议；不把缺参数直接当作不可实现 |
| 上下文与世界身份 | 同维度重进世界、撤销/候选变化时，快照/上下文/帧/事实/采样表身份由谁生成 | 统一绑定/身份分配规则；TrackLookup、TrackIndex 与缓存键同源，时间身份采用 C2 已加入的 sampleTime，生成/绑定协议仍待实现 |
| 空间与姿态错误 | FrameFacts 的合法空值与解析失败、失败原因传递；DocumentPose 轴 ERROR 与 ABSENT、FOV 错误汇总 | 准备结果诊断通道及合成阻断规则，不能把缺失二值掩码当作错误兜底 |
| 轨道索引 | 唯一 ID 拒绝、TrackLookup 不能绕过冲突、TrackRef 在被引用机位求值 | A3 实现批次的跨机位/换档/候选一致性验收 |
| playback 帧准备与执行 | `PlaybackRequest` 的文档切换边界、`PreparedFrame.Event` 仅 trackId/time、`StartResult`/`CompositionResult` 一致性、`WorldPose` FOV 单位与降级状态 | 事件载荷解析协议、执行器异常的部分执行报告、合成失败与降级区分 |

Q13 已接受的双向包引用无需重新请求确认；store、校验器、采样器、依赖图、缓存和 EditTools 的实现延期仍保持。Q11 是早期暂缓记录，Q14 已更新后续实施安排；本次更正不将任何新增审核项写为 Q15 等“用户已裁决”。
