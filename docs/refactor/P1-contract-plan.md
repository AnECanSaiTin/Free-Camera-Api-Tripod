# P1 契约计划与新契约目录（`_v2`）

> 本文是**施工约定**，不是设计文档。设计以 `docs/` 下三份设计为准；
> 本文固定三件事：新契约放哪、叫什么、怎么写，以及本批交付了什么供审核。
>
> 状态：**P1 首批契约已提交，待人工审核**。审核通过后再填充实现（`core` / `editor`）。
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
  api/animation/expression/  求值层最小查询面（TrackLookup）
  api/animation/block/       不可变文档模型（含文档根 CameraAnimation）
  api/spatial/               空间、锚点、姿态值
  editor/                    编辑器分区（本包 + spi + internal）
```

最终形态还会增加（P1 余项）：`api/animation/edit`、`api.animation/document`、
`api.animation/eval`、`api.animation/playback`、`api.storage`，以及 `core` 与业务 `editor`。

### 1.4 与设计文档的差异清单

本文是唯一允许记录临时差异的地方。当前差异：

| 项 | 设计文档 | 当前 | 收尾动作 |
| --- | --- | --- | --- |
| 根包名 | `cn.anecansaitin.free_camera_api_tripod` | `...free_camera_api_tripod_v2` | 去掉 `_v2`（§1.2） |
| `TrackLookup` 位置 | 未指定（属求值层） | `api.animation.expression` | 随 `eval` 批次复核（§7 待决 Q6） |
| `Variable` / `CustomFunction` 位置 | 未指定（§6.1 只提“共享符号”） | `api.animation.field` | 若 `eval` 批次认为应独立，可迁到 `api.animation.symbol` |
| 音频数据结构 | A0–A1 阶段实施 | 只提交 `AudioRef` / `AudioRefId` 身份与边界，无 Codec、无播放路径 | 无需动作；符合 D25「前期只保留边界」 |

---

## 2. 已确认的决策（用户）

| 项 | 结论 |
| --- | --- |
| 顶层包（最终形态） | `cn.anecansaitin.free_camera_api_tripod.{api,core,editor}`；`editor` 与 `core` **平级**，不再是 `core.editor` |
| 顶层包（**当前临时形态**） | 新契约集中在 `cn.anecansaitin.free_camera_api_tripod_v2.*`，便于分离审阅；旧代码清理后去掉 `_v2`（§1） |
| 旧代码处置 | 旧实现**原样保留、不接线**；新契约只新增在 `_v2` 根下。工程随时可编译、可运行客户端 |
| 首批范围 | P1：包边界 + 不可变内核契约 |
| 同名冲突 | 因新契约独立在 `_v2` 根下，**不需要**给旧类加 `Legacy` 后缀；新契约直接用设计里的名字 |

---

## 3. 与设计文档的映射

| 设计依据 | 本文落点 | 状态 |
| --- | --- | --- |
| 主设计 D1 / §2.1 包职责 | §4 包清单 | 已建立 |
| 主设计 D20 / §3.1 稳定 ID | `api.animation.id` | **已交付** |
| 主设计 I2 / §3.2 深不可变 | 全部契约 record + 防御性复制 | **已交付** |
| 主设计 §6.1 值源与曲线 | `api.animation.field` | **已交付** |
| 主设计 §3.1 文档形状 | `api.animation.block` | **已交付**（余 `SampleReading`、类型注册表） |
| 主设计 §2.1 / §6.3 空间与锚点 | `api.spatial` | **已交付**（余注册表与解析器） |
| 主设计 §3.3 快照与只读句柄 | `api.animation.document` | 待下一批 |
| 主设计 §4.1–4.2 编辑与校验 | `api.animation.edit` | 待下一批（**下一批首要**） |
| 主设计 §3.4 / I5–I7 求值与帧事实 | `api.animation.eval` | 待下一批 |
| 主设计 §7.1–7.3 播放 | `api.animation.playback` | 待下一批 |
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
| `Keyframe` | record（`keyId` / time / value / 四曲柄参数 / 插值模式） |
| `Curve` | record（严格递增键 + 前后 wrap）+ 嵌套 `Interpolation` / `Wrap` |
| `Variable` / `CustomFunction` | 符号表条目（**不含** `defaultValue`，见 §5 约定 8） |

### 4.3 `api.animation.path` —— 路径几何数据 **已交付**

`Path`、`PathNode`、`PathMode`。**纯数据，不含几何算法**（弧长与参数化在 `core`）。

### 4.4 `api.animation.expression` —— 求值层最小查询面 **已交付**

`TrackLookup`。位置见 §1.4 差异清单。

### 4.5 `api.animation.block` —— 不可变文档模型（主设计 §3.1）**已交付**

| 文件 | 说明 |
| --- | --- |
| `CameraAnimation` | 文档根（机位 / 文档级轨道 / 符号 / 标记 / 采样 / 音轨引用 / 叠加） |
| `CameraRig` / `RigView` | 机位与其画面合成设置 |
| `AnimationTrack` / `CurveTrack` | 轨道契约与内建曲线轨道 |
| `MotionMode` / `DistanceMode` | 位置的来源与路径口径 |
| `Symbols` | 文档级符号表 |
| `Marker` / `MarkerKind` | 文档级标记（**不触发事件**） |
| `WorldSample` | 声明式世界采样声明 |
| `AudioRef` | 参考音轨引用（仅数据与边界） |
| `OverlayElement` / `OverlayKind` | 成片叠加（遮幅 / 字幕 / 图片） |

**余项**：`SampleReading` / `SampleUnknownReason`（采样读数与 UNKNOWN 原因）、
`TrackTypeRegistry`（轨道类型注册表）。

### 4.6 `api.spatial` —— 空间、锚点与姿态值 **已交付**

| 文件 | 说明 |
| --- | --- |
| `Vec3` / `Rotation` / `RotationMask` | 不可变数学值（双精度 / 弧度 YXZ / 逐轴掩码） |
| `Space` | sealed：`World \| Local` |
| `Anchor` | 开放不可变契约（`Identifier type()`） |
| `ResolvedAnchor` | 一帧内解析结果（位置 + 旋转 + 是否应用 partial tick） |
| `AimTarget` | 注视目标（目标 + 掩码 + 世界轴偏移） |
| `ObstacleSettings` | 避障默认设置 |

**余项**：`AnchorTypeRegistry`（common 侧类型注册）、`AnchorResolver`（客户端解析器）、
`WorldPose` / `DocumentPose`（姿态类型，主设计 §6.2）。

### 4.7 `api.animation.edit` —— 编辑与诊断（主设计 §4）**待下一批**

`EditOp`（sealed 操作全集）、`EditOutcome`、`EditResult`、`Diagnostic`、`DiagnosticCode`、
`Severity`、`DocumentValidator`、`ValidationLayer`、`TrialRun`、`TrialResult`、
`TrialStatus`、`EditTools`（缓动 / 批量 / 烘焙）。

### 4.8 `api.animation.document` —— 快照、发布与历史（主设计 §3.3、§4.5）**待下一批**

`AnimationSnapshot`、`AnimationHandle`、`AnimationDocumentStore`、`DocumentListener`、
`Revision`、`SessionSwitch`、`UndoHistory`、`EditTransaction`、`EditTarget`、
`PreviewState`、`Selection`、`KeySelection`。

### 4.9 `api.animation.eval` —— 求值与帧事实（主设计 §3.4、§6、I5–I7）**待下一批**

`FrameFacts`、`EvaluationContext`、`EvaluationKey`、`CurveSampler`、`SampleStatus`、
`ChannelSample`、`DocumentPose`、`SpatialReadings`、`DependencyGraph`、`EvaluationCache`。

### 4.10 `api.animation.playback` —— 播放（主设计 §7.1–7.3）**待下一批**

`PlaybackRequest`、`RigPlaybackOverrides`、`PlaybackService`、`PlaybackState`、
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
7. **`TrackRef` 不进关键帧字段**：`TrackRef` 只实现 `ValueSource`，不实现 `NumberSource`。
8. **删除隐式兜底**：不提供 `evaluateOrFallback` 一类“失败就换个数”的入口；
   变量也不带 UI 初始值字段（主设计 §6.1：初始值不得作为运行期替代值）。
9. **序列化**：契约里只声明 `Codec` / `MapCodec` 的归属，不在本批校验往返。
10. **不接线**：不改任何旧文件，不新增实现类；新增文件全部落在 `_v2` 根下的新包内。

---

## 6. 交付与验证

### 6.1 本批新增契约文件（53 个）

见 §4.1–4.5、§4.13 标 **已交付** / **已建立** 的条目。

### 6.2 门禁验证

```powershell
$env:JAVA25 = 'D:\Code\Java\Jdk\Oracle25'
.\gradlew.bat --offline compileJava test --console=plain
```

三条结构性门禁（每次改动后都应通过）：

1. **隔离**：`_v2` 下不得出现 `free_camera_api_tripod.`（不带 `_v2`）的引用；
   即新契约不依赖任何旧类型。
2. **客户端隔离**：`_v2` 下不得 import `net.minecraft.client.*`
   （主设计 I8 / §2.3）。
3. **旧代码零改动**：`git status --porcelain` 中旧根的已跟踪文件无条目
   （只应出现 `??` 的新目录）。

验收标准：`BUILD SUCCESSFUL` + 上述三条门禁通过。

### 6.3 下一批（P1 余下契约）

按依赖顺序：

1. **`api.animation.edit`**：`EditOp`（sealed 操作全集）、`EditOutcome`、`EditResult`、
   `Diagnostic`、`DocumentValidator`、`TrialRun`。
   **这是提交语义的核心，建议单独一批审核。**
2. `api.animation.document`：快照、文档存储、历史、事务、预览与选择。
3. `api.animation.eval`：帧事实、求值上下文、采样状态、姿态类型、依赖图、缓存。
4. `api.animation.playback`：播放请求、每路覆盖、准备帧、合成与相机契约。
5. `api.storage`：资源服务与恢复槽。
6. `api.editor`：会话与后端契约（含 §7 待决 Q5 的三态覆盖与交互请求）。
7. `block` / `spatial` 余项：`SampleReading`、`TrackTypeRegistry`、`AnchorTypeRegistry`、
   `AnchorResolver`、`WorldPose` / `DocumentPose`。

---

## 7. 审核要点与待决

### 7.1 审核要点（请重点看这几处）

1. **ID 类型粒度**：8 个独立 ID record 是否够用，是否需要 `ChannelKey` 之类第九种。
2. **`CameraChannel` 用枚举**：是否需要为第三方通道开放（开放则改用 `Identifier` + 注册表）。
3. **`Space.Local` 的默认锚点可空**：与“机位可无默认锚点”一致，
   但播放前必须由请求补锚点。
4. **`CameraAnimation.duration()` 的口径**：本批按“启用机位通道 + 文档轨道 + 标记 +
   音轨 + 叠加”的最大结束时间实现，静态布局不延长。**禁用机位是否也该算进去？**
5. **`CameraAnimation` 的可空 `primaryRigId`**：是否确实优于各机位一个 `primary`
   布尔值（D22 的说法是）。空值 = 不接管玩家相机。
6. **`api.animation.document` 与 `api.animation.block` 的分工**：
   前者是快照 / 发布 / 事务，后者是纯数据。
7. **`WorldSample.args` / `OverlayElement.content` 用不透明 `Object`**：
   形状由类型注册表的 Codec 定义（见 §7.2 Q7）。
8. **`AnimationTrack.ExtensionTrack` 作为 sealed 分支**：
   是否要求第三方扩展轨道必须显式实现该子接口（见 §7.2 Q4）。

### 7.2 已裁决（用户）

| # | 问题 | 结论 |
| --- | --- | --- |
| Q1 | 新旧类型同名同包冲突 | **新契约集中到 `_v2` 临时根包**，旧代码零改动；因此新契约直接用设计里的名字，**不给旧类加 `Legacy` 后缀**（§1.1） |
| Q2 | 音频数据结构是否进首批 | 数据结构**不进**（随 A0/A1 定）；但 `AudioRef` / `AudioRefId` 的**身份与边界**已按 D25「前期只保留边界」提交 |
| Q3 | 符号表（变量 / 自定义函数）是否进首批 | **进**。`block.Symbols` + `field.Variable` / `field.CustomFunction` 已提交，文档根 `CameraAnimation` 因此可以落地 |

### 7.3 仍待裁决

| # | 问题 | 备选 | 当前处理 |
| --- | --- | --- | --- |
| Q4 | `CurveTrack` 是否保持 sealed 分支 `AnimationTrack.ExtensionTrack` | (a) 维持：扩展轨道必须显式实现 `ExtensionTrack`；(b) 改成两个独立接口 | 本批按 (a) 提交 |
| Q5 | `AimTarget` 的“继承 / 禁用 / 替换”三态放哪 | (a) 覆盖类型放 `api.animation.playback`；(b) 放 `api.spatial` | 只在 `AimTarget` 文档里写明需要三态，类型留到播放批次 |
| Q6 | `TrackLookup` 留在 `api.animation.expression` 还是并入 `api.animation.eval` | (a) 留 `expression`（当前）；(b) 迁到 `eval` | 当前在 `expression`，随 `eval` 批次复核 |
| Q7 | `WorldSample.args` / `OverlayElement.content` 用不透明 `Object` 是否可接受 | (a) 可接受：形状由类型注册表 Codec 定义，通用容器只持有；(b) 改为泛型参数 | 当前用 `Object` + 文档说明 |

### 7.4 本轮明确不做：注视目标随时间变化

**需求**：例如“前 10 秒注视对象 A，后 10 秒注视对象 B”。

**结论（用户裁决）：本轮不做**，靠手 K 旋转三轴达到视觉近似。

这不是“配置没写全”，而是现有设计几处约束叠加的结果，记录如下以便将来重开时不必重新推导：

| 绕行路 | 为什么不通 | 依据 |
| --- | --- | --- |
| 两台机位做硬切 | 多机位是**同时存在**，不是先后切换；本轮明确不做剪辑 / 切换 / 过渡 | 主设计 §1、§5.5.5；review §3.7 |
| 公式插值两个目标 | 空间读数**只有本机位自己的锚点、注视目标与两者距离**；没有“声明多个可选锚点并在公式里按名字读”这个概念 | 主设计 §6.1；review §5.2（缓存键里“锚点帧身份”是单数） |
| 自定义 `Anchor` 类型内部按时间返回 A 或 B | 文档参数不得依赖公式或其他样本，且锚点在**准备阶段**解析、求值需在同一事实快照上完成；锚点解析依赖动画时间会引入循环 | 主设计 §7.2、§7.7 |

**现在能做的近似**：把旋转三轴手 K 关键帧，在第 10 秒前后各打键并按需过渡。视觉可达，但**丢掉“跟随”语义**——A 或 B 一旦移动、或第 10 秒这个时刻被改动，已 K 的角度全部作废。同类 mod（Immersive Cinematics 的跟随注视、Aperture 的跟踪实体、Blockbuster 的 looking at actor）具备该能力，因此这是一处**已知短板**，不是已覆盖能力。

**将来重开时的两个补法**（本次未选）：

- 方案 A：新增“命名锚点声明”列表，让公式能按名字读**任意**锚点的世界位置与距离。表达力最强（`lerp(A, B, t)`、`select(t<10, dist(A), dist(B))`），不动 `AimTarget` 的静态语义；代价是求值上下文、缓存键（事实身份要含多锚点）与不变量 I7 的措辞都要改。
- 方案 B：把机位的 `aimTarget` 从单个值改成**按时间的关键帧序列**。语义对作者更直接、不用写公式；代价是改 §3.1 的 `CameraRig` 形状、§7.1 的覆盖语义（覆盖一个序列还是整条序列）、Codec 与 L3 校验。

本批契约按“不做”提交：`CameraRig.aimTarget` 保持单个可空值，`AimTarget` 文档已写明三态覆盖需求但没有时间维度。
