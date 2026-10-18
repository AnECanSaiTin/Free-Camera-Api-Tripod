# P1 契约施工计划（`_v2`）

> 更新：2026-10-18。本文记录施工边界与状态，不替代[动画主设计](<../design-animation-editor-framework.md>)、[UI 设计](<../design-editor-ui-framework.md>)和[音频设计](<../design-spatial-audio-api.md>)。
> 当前：第一至第六批及 storage 契约已交付；C1–C4、playback 五项、后续 11 项与 R20/S1–S3 已落实；**第七批 `api.editor` 契约及 R24 的 E1/E2 已补**（草稿/候选入口、后端 open 钩子）。其余保证仍为调用方 / core 责任，见 §7。**core 算法、存储发布、事件执行、仲裁与多机位渲染尚未实现**。
> 最近实际验证：21 个 suite / 157 个用例通过，其中 v2 为 15 / 91；157 个主源码 Java 文件。详情见[复核日志](<review-log.md>) R25。

## 1. 目录与迁移约定

当前根包为 `cn.anecansaitin.free_camera_api_tripod_v2`，最终迁回 `cn.anecansaitin.free_camera_api_tripod`，顶层为 `api / core / editor`。旧实现原样保留、不接线、不改 Legacy 名；新契约直接使用设计名称。清理旧实现后整体上移包并替换引用，最终必须检查编译依赖，不以机械搬包代替职责分离。

| 包 | 当前职责 / 交付 |
| --- | --- |
| api.animation.id | Rig / Track / Key / PathNode / Marker / Sample / AudioRef / Overlay 稳定 ID，IdGenerator |
| api.animation.field | sealed ValueSource / NumberSource、Constant / Formula / TrackRef、数值 Keyframe / Curve、CameraChannel、Variable / CustomFunction |
| api.animation.path | Path / PathNode / PathMode；几何算法不在数据对象中 |
| api.animation.block | 文档、机位、布局、轨道、符号、标记、采样声明/读数、音频边界与叠加 |
| api.spatial | Vec3 / Rotation / 掩码 / Space / Anchor / ResolvedAnchor / AimTarget / AimState / ObstacleSettings / WorldPose |
| api.animation.edit | EditOp（含 SetStateKey）/ 结果 / LocalizedMessage / LocalizedMessageRenderer / DiagnosticScope / Diagnostic / 分层校验 / 试算 |
| api.animation.document | Revision / 快照 / 句柄 / store / EditSubmission / 监听 / 历史 / 事务 / 预览 / 稳定选择 |
| api.animation.eval | 查询/索引、FrameFacts（含世界实例身份）、显式采样请求、AxisSample / SpatialResolution、DocumentPose / SpatialReadings、依赖图与缓存接口 |
| api.animation.playback | 请求/覆盖/服务/时钟/准备帧、事件预算/分发、接管/探针、合成请求/结果/状态（含旧帧采样身份） |
| api.storage | ResourceId、资源条目/服务、Load/SaveResult、DiagnosticReport、RecoverySlot/Service/Decision |
| api.animation.expression | 仅包说明，编译与符号解析仍待后续 |
| api.editor | 会话 / 草稿 EditDraftState / 视图状态 / 交互请求 / 后端与宿主契约；客户端 UI 与实现未交付 |
| editor / editor.spi / editor.internal | 仅分区说明；UI 实现未交付 |

### 1.1 当前差异与延期（唯一施工差异表）

| 项 | 当前形态 | 后续动作 |
| --- | --- | --- |
| 临时根包 | _v2 | 清理旧实现后去掉 _v2 |
| field / expression / symbol 分工 | 值源/曲线/符号条目在 field，expression 预留 | 编译/符号批次复核归属，变更时更新本表 |
| 布局动画 | RigView.Rect 仍是静态几何 | 按 Q14 补 field.AnimatedNumber 并改造矩形；Q11 是被更新的早期暂缓记录 |
| 轨道/锚点/采样注册与 Codec | 参数 SampleArgs 已有；注册表/采集器/Codec 未交付 | common 类型与客户端解析分离；S1 / 编解码批次补齐 |
| 存储资源身份 | `storage.ResourceId`、资源服务、载入/保存结果、恢复槽契约已交付 | 资源根/路径安全、原子替换、Codec 与恢复调度仍待实现 |
| 叠加内容 | OverlayElement.content 暂为 Object | O1–O3 改封闭内容类型；不提前认定深不可变验收完成 |
| 音频 | AudioRef / AudioRefId 仅身份与边界 | 全部非音频阶段完成后实施独立音频 API、可选接入、波形与同步 |
| EditTools / 注视捕获 | 未交付工具请求/实现 | 依赖冻结事实、采样结果与原子 EditOp，随 eval/editor 补齐 |
| 算法与服务实现 | 已交付类型/接口不等于运行实现 | core 补 store / validator / eval / playback / renderer；不得记成已运行 |

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
- 内建轨道类型 ID curve / aim / active / camera_source 保留，ExtensionTrack 伪造由 L2 拒绝。Codec 归成员类型，common 注册和客户端执行分离；本批尚无完整编解码验收。

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

2026-10-18 本轮实际执行成功：21 个 suite / 157 个用例，v2 为 15 / 91，无失败/错误/跳过；157 个主源码 Java 文件。测试集中在 record 结构、不变量与容器隔离，不能替代 core 的时序、仲裁、事件/渲染运行验证。

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

下一步按依赖：EditTools；并推进 Q14 布局 AnimatedNumber、轨道/锚点/采样注册与 Codec、O1–O3 内容类型。音频保持最后实施。不能把这份顺序理解为 core 已交付。

## 7. 契约补齐与实现级验证（推荐方向已定）

Q15 的推荐方向已定，不再重复裁决；R18/R20 指出的可落契约缺口已补齐：`EvaluationContext` 按快照引用身份校验索引同源、`EvaluationKey.from` 从 facts 取表版本、`prepareFrame` 承载预算、`CameraControlClaims` 声明主线程约束、`DiagnosticReport` 限定单资源 File 作用域。以下仍需在 core / 宿主实现中验证；已关闭契约不等于整个 P1、完整播放或客户端验收完成。

| 范围 | 实现级验证 |
| --- | --- |
| document 提交 | 通知期间 `REENTRANT_SUBMIT`、延迟队列重查基版本、标签进历史且拒绝不入历史 |
| 编辑能力 | `SetStateKey` 的轨道能力分派、`EditTarget.Field` 的字段定位、跨轨类型兼容 |
| 试算 | FAILED/PASSED/INCOMPLETE 与诊断一致性的运行路径、报告实际覆盖量与固定事实 |
| eval 绑定 | `EvaluationContext` 用快照引用身份校验 TrackIndex 同源；`Key.from` 从 facts 取表版本。统一生成快照/事实身份、验证 TrackLookup 同源与候选隔离仍需实现 |
| 空间与姿态错误 | AxisSample / SpatialResolution 三态在求值与合成中的消费，FOV 错误汇总与阻断 |
| playback 启动/执行 | start 原子切换、prepareFrame 已接收 EventBudget 并要求展开时限制；CameraControlClaims 已声明主线程与 provider 同线程约束。事件/部分执行、运行线程检查、捕获边界与旧帧失效仍待 core |
| 存储/序列化 | storage 契约已交付；DiagnosticReport 限定同一资源的 File 作用域。ResourceId 资源服务、参数 MapCodec/两阶段解码、未知类型与规模限制待实现 |
| editor 会话/后端 | EditorSession 提交/草稿/预览/交互/视图状态契约已交付；EditDraftState 与后端 open 钩子已补。后端注册与选择、草稿持久化、线程与关闭生命周期待实现 |
| 消息显示 | LocalizedMessageRenderer 的翻译/参数/嵌套样式、语言切换与显示缓存边界 |

这些是实现验证项，不计作已知运行缺陷；已关闭修复不代表 core 已实现。

## 8. 证据与文档维护

- [复核日志](<review-log.md>)保存当前结论、关闭项与 R 编号摘要；新发现追加编号并更新当前状态，不复制施工清单。
- [凝练前计划](<archive/P1-contract-plan-before-condense-2026-10-18.md>)保留旧章节、类型清单和当时措辞，**不作为当前指令**。后续改动只更新当前计划，避免再产生多份互相矛盾的“当前状态”。
- 设计负责行为规范，计划负责交付/批次/例外，日志负责发现与验证；不要用测试通过数替代未覆盖的行为证明。
