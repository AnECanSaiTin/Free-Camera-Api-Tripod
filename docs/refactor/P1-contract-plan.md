# P1 契约施工计划（`_v2`）

> 更新：2026-10-19。本文记录施工边界与状态，不替代[动画主设计](<../design-animation-editor-framework.md>)、[UI 设计](<../design-editor-ui-framework.md>)和[音频设计](<../design-spatial-audio-api.md>)。
> 当前：第一至第九批已交付；第十批交付**具体类型 Codec 与两阶段解码**（各数据类型自身 Codec、`DocumentRegistries`、`Codecs`、`DocumentCodec`），注册表机制已裁决维持自有 `TypeRegistry`（O）、能力描述走客户端服务（B）；提交 **C2** 补齐 R6/R7/R8（扩展时间轴、事件执行器注册、通道描述），提交 **C3** 补齐 R51-N1 事实来源引用绑定。新增“按可见像素需求自适应分辨率”仅进入设计，尚未实现。其余保证仍为调用方 / core 责任，见 §7。**core 算法、存储发布、事件执行、仲裁、多机位渲染与 codec 的 NBT/网络实测尚未实现**。
> 最近实际验证：31 个 suite / 214 个用例通过，其中 v2 为 25 / 148；216 个 v2 主源码 Java 文件。详情见[复核日志](<review-log.md>) 提交 C3；该基线不覆盖新质量需求。

## 1. 目录与迁移约定

当前根包为 `cn.anecansaitin.free_camera_api_tripod_v2`，最终迁回 `cn.anecansaitin.free_camera_api_tripod`，顶层为 `api / core / editor`。旧实现原样保留、不接线、不改 Legacy 名；新契约直接使用设计名称。清理旧实现后整体上移包并替换引用，最终必须检查编译依赖，不以机械搬包代替职责分离。

| 包 | 当前职责 / 交付 |
| --- | --- |
| api.animation.id | Rig / Track / Key / PathNode / Marker / Sample / AudioRef / Overlay 稳定 ID，IdGenerator |
| api.animation.field | sealed ValueSource / NumberSource、Constant / Formula / TrackRef、数值 Keyframe / Curve、CameraChannel、Variable / CustomFunction、**AnimatedNumber（常量值源 / 数值曲线封闭分支）**、ChannelDescription / Registry |
| api.animation.path | Path / PathNode / PathMode；几何算法不在数据对象中 |
| api.animation.block | 文档、机位、布局（**矩形分量可动画**）、轨道（含 ExtensionTimelineTrack）、符号、标记、采样声明/读数、音频边界与叠加 |
| api.spatial | Vec3 / Rotation / 掩码 / Space / Anchor / ResolvedAnchor / AimTarget / AimState / ObstacleSettings / WorldPose |
| api.animation.registry | TypeRegistry 冻结语义、TrackType / AnchorType / SampleType 描述（ID + MapCodec，锚点另含 StreamCodec）、三个 common 注册表接口，**DocumentRegistries**；**R47**：客户端 `AnchorResolver` / `SampleCollector`（+注册表与上下文）与 `TypeCapabilityService` / `TypeCapability` |
| api.animation.codec | **第十批新增**：编解码公共工具（枚举按名、有限/非负数值、非空串、构造器异常转 `DataResult`） |
| api.animation.edit | EditOp（含 SetStateKey）/ 结果 / LocalizedMessage / LocalizedMessageRenderer / DiagnosticScope / Diagnostic / 分层校验 / 试算 / EditTools |
| api.animation.document | Revision / 快照 / 句柄 / store / EditSubmission / 监听 / 历史 / 事务 / 预览 / 稳定选择，**DocumentCodec（两阶段解码）+ DocumentDecodeResult** |
| api.animation.eval | 查询/索引、FrameFacts（含世界实例身份）、显式采样请求、AxisSample / SpatialResolution、DocumentPose / SpatialReadings、依赖图与缓存接口；**R47**：`FrameFactsProvider` / `FrameFactsRequest` |
| api.animation.playback | 请求/覆盖/服务/时钟/准备帧、事件预算/分发/**EventExecutorRegistry**、接管/探针、合成请求/结果/状态（含旧帧采样身份）、**ResolvedRect（已求值矩形）**；**R48**：`PlaybackAction` / `PlaybackSource` / `PlaybackLimits` / `PlaybackProtocolCodec` |
| api.storage | ResourceId、资源条目/服务、Load/SaveResult、DiagnosticReport、RecoverySlot/Service/Decision |
| api.animation.expression | **第十一批**：`ExpressionCompiler` / `CompilationResult` / `CompiledExpression`（不透明）/ `ExpressionReferences` / `ExpressionDiagnostic`；不公开语法树，依赖仅为 JDK |
| api.editor | 会话 / 草稿 EditDraftState / 视图状态 / 交互请求 / 后端与宿主契约；客户端 UI 与实现未交付 |
| editor / editor.spi / editor.internal | 仅分区说明；UI 实现未交付 |

### 1.1 当前差异与延期（唯一施工差异表）

| 项 | 当前形态 | 后续动作 |
| --- | --- | --- |
| 临时根包 | _v2 | 清理旧实现后去掉 _v2 |
| field / expression / symbol 分工 | 值源/曲线/符号条目在 field；expression 只放编译与引用契约；符号表条目在 block/field | 求值入口在 eval（`ExpressionEvaluator` / `ExpressionInputs`（含必填 `source`）/ `EvaluationSession` / `ExpressionFailure`）；实现批次复核是否迁移符号条目 |
| 轨道/锚点/采样注册与 Codec | 第九批交付注册表契约；第十批交付具体类型 Codec 与两阶段解码；**R35/R37/R39 修复**：叠加双向拒绝并声明为可选受检字段、`strictInt` 按 Number 类型精确检查、`TrackType` 上界收窄、解码结果严重级不变量 | **仍未交付**：叠加内容 Codec（O1–O3）、注册表能力描述（B，客户端批次）、类型级校验钩子、字段级结构化解码诊断（当前结构错误为 DFU 文本） |
| 存储资源身份 | `storage.ResourceId`、资源服务、载入/保存结果、恢复槽契约已交付 | 资源根/路径安全、原子替换、Codec 与恢复调度仍待实现 |
| 叠加内容 | OverlayElement.content 暂为 Object | O1–O3 改封闭内容类型；不提前认定深不可变验收完成 |
| 音频 | AudioRef / AudioRefId 仅身份与边界 | 全部非音频阶段完成后实施独立音频 API、可选接入、波形与同步 |
| R6/R7/R8 扩展能力 | **C2 已交付**：`ExtensionTimelineTrack`、`EventExecutor`/`EventExecutorRegistry`、`ChannelDescription`/`ChannelDescriptionRegistry` | 时间轴编辑、事件执行器调用与通道显示派生待 core / editor |
| EditTools / 注视捕获 | 工具请求 / 结果契约已交付；R29 补输入快照与捕获配置 | 缓动、批量、烘焙与注视捕获实现待 core / editor |
| 可见像素自适应质量（新） | 仅设计，当前RigRenderStatus缺请求/可见几何与密度状态，原因枚举无专用出屏状态 | 先补输出像素需求/质量计划/结果状态契约，再做R0 renderer验证；不缩小视锥、不改FOV |
| 算法与服务实现 | 已交付类型/接口不等于运行实现 | core 补 store / validator / eval / playback / renderer；不得记成已运行 |

Q14 已关闭：布局 `AnimatedNumber`、可动画矩形与已求值矩形已交付（第九批），不再作为临时差异。

Revision 包装、顶层 edit 及 edit ↔ document 例外已与主设计对齐，不再作为临时差异。api → core/editor、客户端类依赖仍禁止。

## 2. 已确认决策（保留 Q 编号）

下表只记既有用户裁决，不把本轮复核或未定协议写为新裁决。

| # | 问题 | 结论 |
| --- | --- | --- |
| Q1 | 新旧类型同名同包冲突 | 新契约集中到 _v2 临时根包，旧代码不接线；新契约使用设计名称，不给旧类加 Legacy 后缀（§1） |
| Q2 | 音频数据结构是否进首批 | 数据结构**不进**（随 A0/A1 定）；但 `AudioRef` / `AudioRefId` 的**身份与边界**已按 D25「前期只保留边界」提交 |
| Q3 | 符号表（变量 / 自定义函数）是否进首批 | **进**。`block.Symbols` + `field.Variable` / `field.CustomFunction` 已提交，文档根 `CameraAnimation` 因此可以落地 |
| Q4 | 扩展轨道是否继续挂在 sealed `AnimationTrack` 下 | **维持现状**：`AnimationTrack` sealed + `non-sealed ExtensionTrack`；另补“扩展轨道 `type()` 不得使用内建类型 ID（`curve` / `aim` / `active` / `camera_source`）”的 L2 校验 |
| Q5 | 注视请求覆盖三态放哪 | **`api.animation.playback.AimOverride`**（`Inherit` / `Disabled` / `Replace(AimTarget)`）：覆盖只在宿主层应用，求值层只接收生效 `AimState`；不放进 `api.spatial` |
| Q6 | `TrackLookup` 放哪 | **迁 `api.animation.eval`**（方向 b1）；同时移除 `CameraAnimation.trackLookup()`，消除 `block ↔ expression` 包循环，`expression` 保留包说明留给编译器批次 |
| Q7 | `args` / `content` 用 `Object` 还是泛型 | **名义接口方向（C）**：采样参数改 `SampleArgs` 并校验 `kind == args.type()`；叠加 `content` 暂留不透明引用，随 O1–O3 定义封闭内容类型；**不采用泛型方案** |
| Q8 | 有类型状态轨道的 Java 形态 | **sealed `KeyframeTrack<T>` + 三个 record**（`AimTrack` / `ActiveTrack` / `CameraSourceTrack`）；类型 ID 固定、编译期排除值类型错配、键共用 `ValueKeyframe<T>` 与 `KeyId` |
| Q9 | 采样读数是否三分支 | **VALUE / UNKNOWN / COLLECTION_ERROR**：采集器缺失、异常、非法输出与超预算不可被 `sample(name, fallback)` 兜底 |
| Q10 | `sampledAt` 时间基准 | **游戏刻 `long`**，与 `resampleEveryTicks` 和 `FrameFacts` 同口径 |
| Q11 | 布局 AnimatedNumber 早期安排 | 第二批暂记差异；后由 Q14 更新，当前实施安排见 §1.1 / §6 |
| Q12 | 第二批历史范围 | 当时只补 aim / 来源 / 活动与世界采样读数；不是对第三至第五批的范围限制（§3） |
| Q13 | `edit` 包位置与第三批节奏 | **按 P1 计划放顶层 `api.animation.edit`**（不采用设计 §2.1 的 `document` 子包建议）；先做 edit + document 一批，接受 `edit ↔ document` 双向包引用；`EditTools` 随后续批次补 |
| Q14 | 布局 `AnimatedNumber` 落地时机 | **本轮随后落地**（block/spatial 余项批次）：新增 `field.AnimatedNumber` 并改写 `RigView.Rect`；不再只记录差异 |
| Q15 | 后续 11 项协议 | 全部按提案推荐方向落实，分两批；原“暂不改主设计”为当时施工限制，已由本次“补充进设计文档”请求更新，不改变其余裁决 |

## 3. 交付批次与阅读入口

| 批次 | 交付范围 | 边界 |
| --- | --- | --- |
| 第一批 | 包边界、稳定 ID、不可变数据内核 | 旧实现不接线 |
| 第二批 | AimTrack / ActiveTrack / CameraSourceTrack、ValueKeyframe、AimState、CameraSource、采样三态、AimOverride、TrackLookup 归位 | 布局动画/注册表延期 |
| 第三批 | document 13 个文件 + edit 14 个文件（含最终消息/作用域） | store / 编辑执行 / validator 仅契约；EditTools 延期 |
| 第四批 | eval 新增 11 个类型，含 TrackIndex、FrameFacts、EvaluationContext/Key、采样/姿态/图/缓存 | A3 只完成接口，构建与绑定仍待实现 |
| 第五批 | playback 新增 17 个文件 + WorldPose；五项修复已复核 | 时间推进、事件执行、合成、探针、仲裁均待 core |
| 第六批 | storage：ResourceId、资源条目/服务、Load/SaveResult、DiagnosticReport、RecoverySlot/Service/Decision | 路径安全、编解码、原子写入与恢复调度待实现 |
| 第七批 | editor：EditorSession、SessionViewState、时间轴/吸附/预览区间/轨道显隐、交互请求、后端选择/宿主 | 屏幕、输入、组件与 core 会话实现待后续 |
| 第八批 | edit：EditTools、EasingPreset/Request、BatchTransform/Request、BakeRequest、AimCaptureRequest/Mode、ToolResult | 工具算法、冻结求值、原子提交待 core |
| 第九批 | `field.AnimatedNumber`、`RigView.Rect` 改可动画分量、`playback.ResolvedRect` 与 `RigPose` 改写、`api.animation.registry`（`TypeRegistry` 冻结语义 + `TrackType` / `AnchorType` / `SampleType` + 三个注册表） | 具体类型 Codec 实现、注册表的**能力描述 / 类型级校验钩子**、客户端解析与采集部件延期 |
| 第十批 | 具体类型 Codec（ID / 空间值 / 路径 / 值源 / 曲线 / 关键帧 / 布局数值 / 符号 / 标记 / 采样 / 音轨引用 / 机位 / 布局 / 文档根）、`DocumentRegistries`、`Codecs`、`DocumentCodec` 两阶段解码与往返测试 | 叠加内容、注册表能力与类型级校验钩子、字段级结构诊断、未知类型之外的 Codec 兼容矩阵延期 |
| 第十一批 | 表达式契约：`ExpressionCompiler` / `CompilationResult` / `CompiledExpression` / `ExpressionReferences` / `ExpressionDiagnostic`；`eval` 的 `ExpressionEvaluator` / `ExpressionInputs` / `ExpressionSample`；R43 增 `EvaluationSession`、`ExpressionSource` / `ExpressionFailure`；R45 增来源字段/`KeyId` 与 `cacheableInSharedCache` 规则 | 编译器与解释器实现、内建函数版本、`edit.Diagnostic` 包装、依赖图与试算接入延期 |
| 第十二批 | R2/R3/R4：`FrameFactsProvider` + `FrameFactsRequest`；客户端 `AnchorResolver` / `SampleCollector` 及注册表与上下文；`TypeCapabilityService` / `TypeCapability` | 三个服务的实现、客户端世界查询与注册时序、群系/天气/记分板查询扩展延期 |
| 第十三批 | R5：`PlaybackAction` / `PlaybackSource` / `PlaybackLimits` / `PlaybackProtocolCodec`（版本、规模上限、可报告编解码入口） | 具体编解码实现、收包校验与 `Local` 资源解析流程、文档级深度/数量上限延期 |

源码入口：[数据模型](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/>)、[文档](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/>)、[编辑](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/>)、[求值](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/>)、[播放](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/>)。

## 4. 契约约束（修改时必须保留）

- 中文 JavaDoc，受检包 NullMarked、可空处 Nullable；数据对象深不可变，集合防御性复制，不泄漏数组/JOML/可变 Component。开放第三方类型须遵守不可变 SPI，Java 类型本身不能证明第三方实现。
- 数值键只收 NumberSource（Constant / Formula），TrackRef 仅用于变量等受支持入口；状态键用原生 AimState / Boolean / CameraSource，仅 STEP。二者共用 TrackId/KeyId、时间轴编辑和历史，不把状态编码成浮点数。
- 键时间秒基准、有限非负、同轨唯一、负零正规化；乱序输入整理但不改变调用方容器。数据构造不替代编辑层结构化冲突诊断。
- 新建对象由调用方提供唯一 ID，复制经 IdGenerator 生成新身份。语义键仅在机位内唯一，TrackId 在整份文档唯一；状态轨道只持久化一次。
- EditOp 只描述纯意图，Batch 一次构造完整候选、一次校验发布；无变化不添历史，目标不存在由提交层明确诊断。不要静默夹取、覆盖、量化或运行期 fallback。状态键单值修改用 `SetStateKey`，草稿字段用 `EditTarget.Field` 保留对象种类。
- 提交使用 `EditSubmission` 携带不可变动作标签；同步 `submit` 在监听通知期间返回 `REENTRANT_SUBMIT`，延迟写入用 `submitAfterNotifications` 并在通知结束后重查基版本。标签只随成功提交进入历史。
- `TrialResult` 的 `FAILED` 必须含 ERROR，`PASSED` / `INCOMPLETE` 不得含 ERROR；`INCOMPLETE` 必须给出原因；报告包含区间、采样策略、实际检查量、固定事实身份或说明。
- `CurveSampler` 只接受显式 `CurveSampleRequest`（上下文 + 轨道 + 机位 + 时间），不依赖当前活动机位或当前 store；`EvaluationContext.bind` 统一绑定快照、事实与索引，并校验 `TrackIndex.snapshot()` 与快照同源。`FrameFacts` 携带独立 `worldInstanceId`，同一维度重进世界也变化；`EvaluationKey.from` 从事实取采样表版本，不再允许另传不一致版本。
- 同一 store 换档/撤销/重做继续递增 Revision；快照一次读取文档与序号。发布线程与通知队列由 core 实现，不反向依赖编辑器。
- LocalizedMessage 为封闭不可变消息树；DiagnosticScope 区分 Document(非空 Revision) / File(ResourceId)。File 不携带对象 ID 或 Fix，Fix 版本须等于 Document 版本；显示时才通过无状态 `LocalizedMessageRenderer` 转 Component。
- SampleReading 区分 VALUE / UNKNOWN / COLLECTION_ERROR，只有 UNKNOWN 可被作者显式 sample 默认值兜底。读数不入文档/历史，SampleArgs 深不可变且 kind 与 args.type 一致。
- EvaluationKey 含快照/上下文/世界实例/帧/sampleTime/机位/轨道/事实/采样表身份；`EvaluationKey.from(CurveSampleRequest, ...)` 是统一键入口。TrackIndex 先拒重复 ID，TrackRef 用被引用轨道所属机位上下文；文档结构缓存不能代替动态帧缓存。
- 姿态逐轴使用 AxisSample 的 ABSENT / VALUE / ERROR；锚点 / 目标使用 SpatialResolution 的 NotRequired / Resolved / Failed。错误不得压成缺失，存在掩码只由有值轴派生；任一实际依赖错误阻止姿态合成。
- 旧画面 `SampleIdentity` 同时绑定渲染帧序号、动画时间、游戏刻、世界实例、快照、机位活动区间代际与来源代际；任一不匹配必须失效，不得跨活动区间/来源交接复用。
- storage.ResourceId 是文件诊断的资源身份；ResourceId 非空白，不用绝对文件系统路径。资源载入成功仍须完整文档校验；保存 / 恢复使用临时文件与原子替换，恢复槽不覆盖正式文件，不保存渲染缓存/撤销栈。
- PlaybackService 所有方法与 prepareFrame 在宿主主线程调用；prepareFrame 必须接收 EventBudget 并在展开区间时就限制，不得先完整展开后检查。
- EditorSession 也不得绕过 EditSubmission / 文档存储写文档；视图状态、轨道显隐、预览覆盖只属于会话，不写文档、不进撤销历史。交互请求过期 / 无效选项必须拒绝；后端选择按 auto / 指定 / 拒绝 / 未注册规则处理，不能静默切换。
- editor 契约已交付；EditTools 只生成 `ToolResult.Ready(EditOp)` / 整体拒绝，不发布、不读当前 store。缓动/批量请求显式绑定 `AnimationSnapshot`；注视捕获携带 `AimCaptureMode` 与生效 `AimOverride`，且 `baseRevision` 必须等于冻结快照版本。公式不得静默转 literal，固定 Target 覆盖必须先清除。
- 内建轨道类型 ID curve / aim / active / camera_source 保留，ExtensionTrack 伪造由 L2 拒绝。Codec 归成员类型，common 注册和客户端执行分离；本批尚无完整编解码验收。
- 布局连续属性用 `field.AnimatedNumber`：`Value`（常量 / 公式）或 `Keyed`（数值曲线 + 显式默认值）。`Keyed` 只接受 `CLAMP / CLAMP`，非空曲线首末之外保持端点；空曲线使用显式默认值，不得隐式取 0。静态可判定部分（常量、空曲线默认值）在构造期检查，公式与曲线产生的值必须留到运行期逐帧检查；两种坐标模式静态互斥。布局数值不通过 TrackRef 对外提供读数，也不新增 `TrackId`。
- 准备帧携带**已求值**矩形（`playback.ResolvedRect`），不携带 `AnimatedNumber`：求值在准备阶段、画布到像素映射在合成阶段；非法求值结果（非有限、非正宽高）构造失败，不能伪装成合法准备帧。布局曲线计入文档时长，静态布局与末键保持不延长。
- 开放类型经 `api.animation.registry` 的冻结注册表按 `Identifier` 分派：冻结后注册拒绝、重复 ID 拒绝、未知类型解码报错；锚点的存档 Codec 与网络 Codec 由同一 `AnchorType` 同时提供；common 侧只放 ID 与 Codec，客户端解析 / 采集执行部件单独注册。注册表的能力描述与类型级校验钩子形状未裁决前不得写入公开契约（§7.2）。
- 编解码归**数据类型自身**：每个受序列化类型声明自己的 `Codec` / `MapCodec`；文档根 `CameraAnimation.mapCodec(registries)` 只组合成员 Codec，不复制子对象字段规则（§8.1）。多分支封闭类型用**显式标签**（`type` / `mode` / `kind`）分派，未知标签与未注册类型一律解码错误，不回退默认分支。
- `data_version` 必填且当前只接受 `CURRENT_DATA_VERSION`；缺失、较旧或更高都在结构阶段拒绝。结构约束由值类型构造器执行，解码边界把 `IllegalArgumentException` 转成结构失败，不让异常穿透。
- 文档解码分两阶段：**结构阶段**（Codec + 局部约束 + 类型分派）→ **统一校验阶段**（`DocumentValidator`，任一 `ERROR` 拒绝）。结构失败时没有候选文档，也不调用校验器；两阶段都通过才产出可发布候选（§4.2、§8.1）。
- **叠加在 O1–O3 前不进入格式，且双向拒绝**：`overlays` 是根 Codec **正式声明的可选受检字段**（进入 `keys()`，压缩 Ops 键表一致），解码拒绝非空列表与非列表值、编码拒绝非空叠加；直接调用 `CameraAnimation.codec(registries)` 也无法绕过。空列表 / 缺省 / `null` 仍接受（R34 C1 / R35 / R36-N2 / R37）。不得静默丢弃作者数据，该限制随封闭内容类型解除。
- 整数字段使用 `Codecs.strictInt()`，并**按 `Number` 实际类型分支**：`Float` / `Double` 使用其已表示的二进制数值（`Float` 精确拓宽），其余（含 Gson 保留字面量的 `LazilyParsedNumber` 与 `BigDecimal`）按十进制文本用 `BigDecimal` 精确检查。既能拒绝高精度小数字面量（R36-N1），也不会把 `Float` 整数改写成另一个数（R38-N1 / R39）。`data_version`、`z_order`、`update_every_frames`、`resample_every_ticks` 均使用它。
- `TrackType<T>` 的上界是 `AnimationTrack.ExtensionTrack`：内建轨道不可能被注册成扩展类型，扩展表也不接受“内建轨道冒充扩展”的条目（R34 C3 / R35，编译期保证）。
- `DocumentDecodeResult` 的公开构造器强制 `Accepted` 不含 `ERROR`、`ValidationFailed` 至少含一条 `ERROR`（R34.2 建议）。
- **表达式契约分层**：`api.animation.expression` 只放编译、外部引用与轻量诊断，不依赖 `eval` / `block` / `edit` / `playback`；不公开语法树（§6.1）。编译失败不产生可求值句柄；引用携带源码区间与函数实参个数，供 L3 校验与依赖图使用。
- **表达式求值显式输入**：求值入口在 `api.animation.eval`（`ExpressionEvaluator` / `ExpressionInputs`），输入含上下文、**必填的来源位置 `ExpressionSource`**（通道可带 `KeyId`，布局含 `LayoutField` 与可空 `KeyId`）、可空 `RigId`、`t/p/wt`、空间读数、随机种子、曲线采样器与**本帧共享的 `EvaluationSession`**。结果只有有限数值或带来源的失败链（`ExpressionFailure`：`ExpressionSource` + 本层诊断 + 由外到内的 `causes`），`NaN`/`Infinity` 不得作为值返回；`sample(name, fallback)` 仅在 `UNKNOWN` 时惰性求值，采集错误不可兜底（§6.1、§7.7、D18）。**资源失败（工作量 / 深度）取决于执行状态，不得写入会话无关缓存**；确定性失败仍按同一身份缓存（R44-N1 / R45）。
- 当前结构错误只有 DFU 文本（含字段路径），尚未转成 `Diagnostic`；叠加、NBT / 网络 Ops 往返、规模与深度上限仍待后续批次。

### 4.1 playback 已定协议

- 请求覆盖按 RigId 定位，未知 ID 拒绝；锚点覆盖失败不回退默认；AimOverride 为继承/禁用/固定替换。正速、非负有限开始时间，不支持反向。
- PreparedFrame 捕获 AnimationSnapshot 与 FrameFacts；RigPose 冻结姿态、矩形、zOrder、resolutionScale、updateEveryFrames。执行/合成不重新读取当前 store。
- 事件保留“推进区间/周期顺序 → 区间内时间递增 → 同一发生时刻按轨道序”；EventDispatcher 接收整帧，以帧快照解析载荷，原样消费。零事件、跨多周期、上限及部分执行仍需运行测试。
- CameraControlClaims 登记 PoseProvider，winningPose 一次捕获 Claim + WorldPose；provider 失败转准备失败，不静默退回 Player。释放后不得发起新调用或复用旧缓存；捕获过程中释放/新登记的同帧边界按接口实现，并在 core 批次验证。
- 已呈现画面必须携带 SampleIdentity（renderFrameIndex、animationTime、gameTick、worldInstanceId、snapshotId、activityGeneration、sourceGeneration）；FRAME_SKIPPED 必须呈现旧画面，RIG_PAUSED 不呈现；任一身份不匹配时旧区间画面失效。
- start 必须先校验请求并准备初始条件，成功后才切换文档/状态；失败时原文档、序号、播放状态与相机控制不变。接管按捕获边界线性化，provider 异常转准备失败。`prepareFrame` 接收 `EventBudget`，预算在区间展开时执行；所有方法与帧准备限主线程。
- WorldPose 旋转弧度 YXZ、FOV 为正有限角度（度）。降级可见/可恢复且不改创作数据，不得掩盖求值或渲染错误。

## 5. 验证基线与门禁

```powershell
.\gradlew.bat --offline compileJava test --rerun --console=plain
```

2026-10-19 提交 C3 实际执行成功：31 个 suite / 214 个用例，v2 为 25 / 148，无失败/错误/跳过；216 个 v2 主源码 Java 文件。测试集中在 record 结构、不变量、容器隔离与 Codec 往返，不能替代 core 的时序、仲裁、事件/渲染运行验证，也不覆盖 NBT / 网络 Ops。第十批新增 [DocumentCodecTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodecTest.java>) 5 个用例（完整往返、版本拒绝、未知类型拒绝、校验拒绝与警告、叠加编码拒绝）；第九批新增 [布局连续属性](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/LayoutAnimationContractTest.java>) 9 个用例、`ResolvedRect` 1 个用例。

必查：api 不依赖旧根、net.minecraft.client、core/editor；editor.spi 不依赖 internal。文本扫描仅辅助，正式依赖门禁检查编译产物。旧根不接线批次不应修改旧实现。按 Q13 允许 edit ↔ document 包引用，不能将此误报为禁止依赖。补丁格式检查使用 git diff --check。

主要测试：[数值键](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/KeyframeContractTest.java>)、[曲线](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/CurveContractTest.java>)、[状态轨道](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/StateTrackContractTest.java>)、[时长](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimationDurationTest.java>)、[无变化替换](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimationReplacementTest.java>)、[编辑](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/EditContractTest.java>)、[消息](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/LocalizedMessageContractTest.java>)、[文档](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentContractTest.java>)、[求值](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvalContractTest.java>)、[播放](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackContractTest.java>)。

<a id="p1-follow-up"></a>
## 6. 当前待办与后续批次

| 项 | 当前状态 | 后续验收 |
| --- | --- | --- |
| A0/A1/A4/A5 | 时间边界、内部遍历、withRig no-op 身份、命名说明已完成 | 保留回归，不按孤立微基准强制改 Stream |
| A7 | 标记注释与远端标记延长时长测试完成 | 无事件副作用随执行器验证 |
| A2 | 静态列表/时长缓存为条件优化 | 先有重复查询证据，缓存有界、生命周期释放；不缓存为动态 active(t)/姿态 |
| A3 | 索引契约已交付 | 实现唯一 ID 校验、所属机位绑定、换档/撤销/候选重绑 |
| A6 | 标记退出 duration 为待裁决产品项，非缺陷 | 未裁决前保持全部标记参与时长，不新增 affectsDuration；若改则定义编辑范围、定位/循环和同步全部口径 |
| C1–C4 | 契约修复关闭（R12） | store 旧请求拒绝、异步诊断绑定、消息渲染及实际缓存随后续验证 |
| playback 五项 | 契约修复关闭（R15） | 仍需仲裁/事件顺序/快照切换/旧帧失效的 core 运行测试 |
| 后续协议 1–11 | 已按 Q15 调整公开契约，部分保证仍为文字/调用方责任（R18） | Key 表版本、TrackIndex 同源与 prepareFrame 预算已在 R19 补齐；队列/能力分派/试算/仲裁/渲染仍待 core |
| storage 契约 | 第六批已交付（R19） | 路径安全、Codec、原子写入与恢复调度实现 |
| editor 契约 | 第七批已交付；R25 补齐 E1/E2 | 会话实现、草稿持久化、后端注册/选择与 open 派发运行验证 |
| EditTools | 第八批已交付，R30已关闭T1/T2契约问题 | 缓动/批量/烘焙/注视捕获算法、冻结求值、覆盖清理与原子提交待 core |
| R6/R7/R8 | 提交 C2 已交付扩展时间轴、事件执行器注册、通道描述注册表 | 时间轴编辑、执行器分派、通道显示派生待 core / editor |
| R51-N1 | 提交 C3 已改为 `FrameFacts.sourceSnapshot` 引用绑定 | 同版本异源拒绝已加测试；provider 实际传递仍待 core |
| 布局连续属性（Q14） | **第九批已交付**：`AnimatedNumber` 两分支、矩形四分量可动画、`ResolvedRect` 冻结已求值矩形；布局曲线计入时长 | 求值（分量→数值）、逐帧有限性/正尺寸检查、静态校验器接入与映射到输出像素待 eval / core |
| 类型注册表（轨道/锚点/采样） | 第九批交付契约；第十批交付 `DocumentRegistries` 与锚点分派 Codec | 能力描述（B，客户端批次）、类型级校验钩子、`core` 侧的注册表实现与冻结时序 |
| 编解码与两阶段解码 | **第十批交付**：各数据类型自身 Codec、文档根 `CameraAnimation.mapCodec(registries)`、`DocumentCodec`（结构 → 校验 → Accepted/StructureFailed/ValidationFailed） | 叠加内容（O1–O3）、字段级结构诊断（当前为 DFU 文本）、NBT/网络 Ops 往返实测、规模上限与深度限制 |
| 可见像素自适应质量 | 用户确认新需求，主设计§7.6.1已记录，无实现 | 输出像素/画布交集、档位/滞回、状态可查询、出屏副路跳绘/来源保留，R0测量性能与画质 |

注册表机制已裁决维持自有实现（O）、能力描述走 B（见 §7.2 与[选型文档](<registry-selection.md>)）。剩余工作见 §6.1 清单；叠加 O1–O3 按用户指示**等设计补全字段**；音频保持最后实施。不能把这份顺序理解为 core 已交付。

### 6.1 P1 剩余契约清单（2026-10-19 对账）

逐项比对设计 §2.1 包职责、§2.2 宿主服务、§4–§8 行为要求后，仍缺的**契约**（不含 core 实现）：

| # | 剩余契约 | 设计依据 | 现状 | 阻塞关系 |
| --- | --- | --- | --- | --- |
| R1 | **表达式编译 / 求值契约（R41 交付接口，R43 补齐共享预算/深度与失败来源，R45 补齐缓存边界与字段级来源；实现待 core）**：编译入口与结果、结构化诊断、编译缓存；求值环境（内置量 `t/p/wt`、空间读数、`sample(name[,fallback])`、`TrackRef` 机位上下文）；内置函数（`step`、惰性 `select`、`noise(seed,t)`）、arity/作用域静态校验、深度/工作量上限 | §2.1、§4.2、§4.3、§6.1、§7.7 | `api.animation.expression` **只有 package-info**；只有 `Formula`（文本）与 `TrackLookup` | **最大缺口**：`CurveSampler`、`TrialRun`、`DependencyGraph` 都假设“有编译后的表达式与求值环境”，现在无法真正求值公式 |
| R2 | `FrameFactsProvider` 服务契约（**R47 交付接口，C1 补 H1，C3 补来源绑定**） | §2.2、§7.2 | R47 交付 `FrameFactsProvider` / `FrameFactsRequest`；C1 新增 `FrameFactsPlan`、`animationTime`、`FrameFacts.sourceSnapshot` 与 `EvaluationContext` 校验；C3 改为按快照引用身份拒绝同版本异源，`sourceRevision()` 仅诊断 | 阻塞 `core.playback` 的帧准备；实现批次验证采样节奏、表版本与失败编码 |
| R3 | 客户端执行部件注册：`AnchorResolver`（+注册表）、`SampleCollector`（+注册表）（**R47 交付接口，提交 C1 补齐 H2**） | §2.2、§6.3、§7.7 | R47 交付解析/采集契约与上下文；C1 补 `AnchorResolutionContext#diagnosticScope` 与 `SpatialResolution.failed(...)`；客户端实现待后续批次 | 阻塞第三方锚点解析与采样采集；阻塞 S1/S2 |
| R4 | 能力服务契约（B 裁决）：按类型查询解析/采集能力（**R47 已交付接口**） | §2.2、§7.7、§7.2 | R47 交付 `TypeCapabilityService` / `TypeCapability`；实现待宿主装配 | 解码错误与运行能力缺失已分三态；实现批次验证编辑器/播放宿主的接入口径 |
| R5 | 播放请求 **`StreamCodec` / 协议契约**（**R48 已交付接口**）：动作与来源、按 RigId 的覆盖（含锚点完整参数与三态）、速度/循环/开始时间、协议版本与规模上限、可报告失败 | §8.2、§8.3 | R48 交付 `PlaybackAction` / `PlaybackSource` / `PlaybackLimits` / `PlaybackProtocolCodec`；具体编解码与收包校验待实现 | 阻塞多人播放与请求失败诊断；实现批次验证字节往返与超限拒绝 |
| R6 | 扩展轨道的**纯派生时间轴能力接口**（**C2 已交付**） | §4.1 | `ExtensionTimelineTrack` / `TimelineKey` 已交付；扩展轨道可选择实现 | 时间轴编辑实现待 editor / core |
| R7 | 第三方**事件执行器注册**契约（**C2 已交付**） | §7.4 | `EventExecutor` / `EventExecutorRegistry` 已交付；重复类型与内建类型拒绝 | 执行器分派、异常与部分执行待 core |
| R8 | **通道描述注册表**（标签/颜色/默认信息）（**C2 已交付**） | §8.1 | `ChannelDescription` / `ChannelDescriptionRegistry` 已交付；注册表要求完整且一致 | 编辑器显示派生待 editor |
| R9 | 类型级校验钩子（`TypeValidator<T>` vs 复用 `DocumentValidator`） | §2.2、§4.2 | 未定形；文档校验只走 `DocumentValidator` | 阻塞第三方轨道/采样类型的类型级校验 |
| R10 | 结构解码的**字段级诊断**：`StructureFailed` 目前只有文本 | §4.2、§8.2 | `DocumentDecodeResult.StructureFailed(String)` | 解码错误无法定位对象/字段，不能进诊断面板 |
| R11 | 会话按 RigId 的**预览锚点/目标/避障**契约 | §6.3、§9.1 | `SessionViewState` 有选择/时间轴/吸附/区间/显隐/`activeRigId`，无预览覆盖 | 阻塞多机位草稿恢复与预览一致性 |
| R12 | 可见像素自适应质量的**新契约切片**（请求/可见几何、密度、专用出屏原因） | §7.6.1（R30 新需求） | 仅设计，`RigRenderStatus` 不足 | 阻塞 R0 渲染验证与降级可视化 |

按设计/用户裁决**明确延期**、不计入本轮缺口：叠加内容封闭类型（O1–O3，等设计补字段）、音频 `ReferenceAudioService` 与音频数据（A0–A2）、编辑器 SPI（`PanelRegistry`、公开组件，U1/U2）、采样实时读数/洋葱皮/构图辅助等面板模型（S1/S2、E1）。

建议实施顺序：**R1 → R2 + R3 + R4 → R5 → R6/R7/R8 → R9/R10 → R11/R12**。R1 是所有求值相关契约的前置，应单独成批审核。

## 7. 契约补齐与实现级验证（推荐方向已定）

Q15 的推荐方向已定，不再重复裁决；R18/R20 指出的可落契约缺口已补齐：`EvaluationContext` 按快照引用身份校验索引同源、`EvaluationKey.from` 从 facts 取表版本、`prepareFrame` 承载预算、`CameraControlClaims` 声明主线程约束、`DiagnosticReport` 限定单资源 File 作用域。以下仍需在 core / 宿主实现中验证；已关闭契约不等于整个 P1、完整播放或客户端验收完成。

| 范围 | 实现级验证 |
| --- | --- |
| document 提交 | 通知期间 `REENTRANT_SUBMIT`、延迟队列重查基版本、标签进历史且拒绝不入历史 |
| 编辑能力 | `SetStateKey` 的轨道能力分派、`EditTarget.Field` 的字段定位、跨轨类型兼容 |
| 试算 | FAILED/PASSED/INCOMPLETE 与诊断一致性的运行路径、报告实际覆盖量与固定事实 |
| eval 绑定 | `EvaluationContext` 用快照引用身份校验 TrackIndex 与 FrameFacts 同源；`Key.from` 从 facts 取表版本。统一生成快照/事实身份、验证 TrackLookup 同源与候选隔离仍需实现 |
| 空间与姿态错误 | AxisSample / SpatialResolution 三态在求值与合成中的消费，FOV 错误汇总与阻断 |
| playback 启动/执行 | start 原子切换、prepareFrame 已接收 EventBudget 并要求展开时限制；CameraControlClaims 已声明主线程与 provider 同线程约束。事件/部分执行、运行线程检查、捕获边界与旧帧失效仍待 core |
| 存储/序列化 | storage 契约已交付；DiagnosticReport 限定同一资源的 File 作用域。ResourceId 资源服务、参数 MapCodec/两阶段解码、未知类型与规模限制待实现 |
| editor 会话/后端 | EditorSession 提交/草稿/预览/交互/视图状态契约已交付；EditDraftState 与后端 open 钩子已补。后端注册与选择、草稿持久化、线程与关闭生命周期待实现 |
| EditTools | 缓动/批量请求绑定冻结快照，捕获携带 mode/override/baseRevision；`ToolResult` 已交付。算法、冻结事实消费、覆盖清理与原子提交待 core / editor |
| 布局连续属性 | `AnimatedNumber` 两分支求值（常量/公式/曲线、空曲线默认值、端点保持）、逐帧有限性与正尺寸检查、静态可判定部分的校验接入、`ResolvedRect` 映射到输出像素 |
| 类型注册表 | 冻结后注册被拒、重复 ID 拒绝、未知类型解码报错、锚点存档 Codec 与网络 Codec 由同一类型同时就位、客户端执行部件缺失与解码错误的诊断分离 |
| 扩展轨道/事件/通道描述 | `ExtensionTimelineTrack`、`EventExecutorRegistry`、`ChannelDescriptionRegistry` 已交付；时间轴编辑、按类型执行器分派与通道显示派生待 core / editor |
| 消息显示 | LocalizedMessageRenderer 的翻译/参数/嵌套样式、语言切换与显示缓存边界 |

这些是实现验证项，不计作已知运行缺陷；已关闭修复不代表 core 已实现。

### 7.1 可见像素需求自适应分辨率（新契约切片）

以完整显示矩形R与输出画布交集V确定需求，不考虑机位互相遮挡；保持完整R宽高比、FOV和UV，降低分辨率不重新构图。完全出屏只跳过副路绘制，不跳过依赖求值/文档事件，不停止来源相机。按尺寸分档/池化/滞回，恢复可见/身份变化先出有效新帧。

新增输入/状态应能表达输出像素、完整/可见矩形、请求/实际分辨率或密度、裁剪降采样和出屏未提交原因；不直接操作GL，不把宿主目标放进common文档。现有RigPose/CompositionRequest可提供基本几何和作者质量，但结果状态不足；具体新增类型名留实现前契约审核，不预写为已交付。

待实测的阈值/函数与最低纹理尺寸不进文件语义；关闭裁剪降采样时仍按请求矩形尺寸适配。部分裁剪降密度是有损质量策略，UI需明确；实际视锥裁剪/非对称投影不在此次确认范围。

### 7.2 注册表：机制选型、能力描述与类型级校验钩子（待裁决，未写入契约）

第九批只交付了注册表的 **ID 分派 + 结构 Codec + 冻结语义**。主设计 §2.2 把“能力描述与校验”也列在注册表职责内，但两者的接口形状存在先决问题，因此**没有**先写进公开契约。

**机制选型（用户已裁决）**：维持 **O（自有 `TypeRegistry`）** 作为 `api` 契约；能力描述选 **B（客户端能力服务）**，服务契约随客户端解析 / 采集批次定义；类型级校验钩子暂缓，等客户端执行部件批次一并裁决。**N′（`core` 用 NeoForge 自定义注册表实现接口）保留为将来演进选项**，仅在出现数据包扩展 / 注册表同步 / 别名迁移的真实需求时启用。

选型依据与 A/B 对比见[注册表选型](<registry-selection.md>)。概要：

| 方案 | 结论 |
| --- | --- |
| N：`api` 直接暴露 NeoForge/MC `Registry` | 不推荐：类型表不承载游戏内容、不需要数据包与注册表同步；`RegistryCodecs` 无分派助手，收益有限而把 26.x 版本耦合带进 `api` |
| N′：保留 `TypeRegistry`，`core` 用自定义注册表实现 | 将来可选：第三方可用 `DeferredRegister`；需先验证冻结时机、`sync(false)`、独立测试构造与事件顺序 |
| O：自有 `TypeRegistry`（当前，**已裁决维持**） | 满足当前全部需求；`api` 不绑 MC 注册表类型；纯内核测试无启动成本 |

**能力描述**与注册表机制正交，仍待裁决：

| 方向 | 先决问题 | 不写死的理由 |
| --- | --- | --- |
| A：描述对象上声明能力 ID 集合 | 能力是**运行期**概念，需要区分“客户端执行部件缺失”“宿主不支持”“参数非法”三类 | 把能力做成类型数据，会让可恢复的缺失与不可恢复的错误混在一起（D18/I9） |
| B：客户端能力服务按类型查询（推荐） | 服务契约需要“已注册执行部件”作为事实来源 | 与注册表选型解耦；等客户端解析/采集批次一起定义 |

**类型级校验钩子**（用户指示：等注册表选型后决定）同样未写死：

| 方向 | 先决问题 |
| --- | --- |
| C：`TypeValidator<T>`（`ValidationLayer` + `Revision` 上下文，聚合成 `DocumentValidator`） | 会改变 §4.2 的校验分层入口 |
| 暂缓：第三方用整份 `DocumentValidator` 自行筛类型 | 不动现有契约，但第三方要遍历全文档 |

在裁决前，解码器按类型 ID 查 Codec，文档校验仍走 `DocumentValidator`，两者都不依赖未定形状。

**叠加内容**：按用户指示**等设计补全字段再定型**，`OverlayElement.content` 仍为不透明引用（见 §1.1）。

## 8. 证据与文档维护

- [复核日志](<review-log.md>)保存当前结论、关闭项与 R 编号摘要；新发现追加编号并更新当前状态，不复制施工清单。
- [凝练前计划](<archive/P1-contract-plan-before-condense-2026-10-18.md>)保留旧章节、类型清单和当时措辞，**不作为当前指令**。后续改动只更新当前计划，避免再产生多份互相矛盾的“当前状态”。
- 设计负责行为规范，计划负责交付/批次/例外，日志负责发现与验证；不要用测试通过数替代未覆盖的行为证明。
