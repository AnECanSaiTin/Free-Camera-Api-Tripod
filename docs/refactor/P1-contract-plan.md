# P1 契约、实施与验收

> 更新：2026-10-26。最近实施批次为 **C15**（NBT 结构字段路径，未提交）；C14 为 NBT 文件资源服务（已提交 `4bffdc0`，待 R64）；C13 为全文档 KeyId 唯一性（已提交 `40abc7e`，待 R63）；C12 为 NBT 文档编解码（已提交，待 R62）；C11 为编辑器 UI 后端编排（已提交，待 R61）；C10 为播放请求接收入口（已提交，待 R60）；C9 为消息渲染与编辑工具（已提交，待 R59）；C8 为相机接管仲裁与类型能力服务（已提交，待 R58）；C7 为 R56-H1 修复（已提交，R57 已复核）；C6 为 R55 九项修复（已提交）；`95c3dda` 为 2026-10-25 已提交实现批次，经 R55 审核发现九项问题。审核、复核与关闭边界见[P1 审核与复核记录](<P1-review.md>)。
>
> **当前结论：P1 已列 R1–R12 契约均已交付；C5 两项修复在原范围复核通过；C7 已按 R56-H1 完成形参作用域修复，R57 独立复核在原范围关闭；C8 已实现相机接管仲裁与类型能力服务，待 R58；C9 已实现消息渲染与四个编辑工具，待 R59；C10 已实现播放请求接收入口，待 R60；C11 已实现编辑器 UI 后端选择编排，待 R61；C12 已实现 NBT 文档编解码与规模限制，待 R62；C13 已实现全文档 KeyId 唯一性，待 R63；C14 已实现 NBT 文件资源服务，待 R64；C15 已补齐 NBT 结构失败字段路径，待 R65。** 这不表示 core、客户端或完整 P1 运行验收完成。C5 审核基线为 **33 suite / 219 用例**（v2 27 / 153）；`95c3dda` 为 **59 suite / 511 用例**（v2 53 / 445）；C6 复核为 **60 suite / 537 用例**（v2 54 / 471）；C7 后全量为 **60 suite / 542 用例**（v2 54 / 476）；C8 后全量为 **62 suite / 566 用例**（v2 56 / 500）；C9 后全量为 **64 suite / 580 用例**（v2 58 / 514）；C10 后全量为 **65 suite / 585 用例**（v2 59 / 519）；C11 后全量为 **66 suite / 593 用例**（v2 60 / 527）；C12 后全量为 **67 suite / 598 用例**（v2 61 / 532）；C13 后全量为 **67 suite / 600 用例**（v2 61 / 534）；C14 后全量为 **68 suite / 604 用例**（v2 62 / 538）；C15 后全量为 **68 suite / 605 用例**（v2 62 / 539），失败/错误 0、跳过 1（Windows 符号链接权限）。
>
> 行为规范仍以[动画主设计](<../design-animation-editor-framework.md>)、[UI 设计](<../design-editor-ui-framework.md>)和[音频设计](<../design-spatial-audio-api.md>)为准；本文维护实际交付、既有裁决、施工记录、施工边界与证据，不新增产品裁决。

## 1. 实现范围与迁移边界

当前根包为 `cn.anecansaitin.free_camera_api_tripod_v2`，顶层分为 `api / core / editor`。旧实现原样保留、不接线、不改 Legacy 名；新契约直接使用设计名称。清理旧实现后再整体迁回 `cn.anecansaitin.free_camera_api_tripod`，替换引用并检查编译依赖；搬包不能代替职责分离。

下表保留 **C5 历史契约交付基线**，其中“尚未实现或验收”描述当时状态，不代表当前全部未实现。当前 core 实现、C6/C7 修复及审核状态以 §4 和审核文档为准；历史范围关闭摘要见审核文档 §1.1。

| 范围         | 已交付                                                                                                          | 尚未实现或验收                                                                     |
|--------------|-----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| 数据内核     | 稳定 ID / IdGenerator；不可变文档、机位、路径、符号、标记、采样声明；数值与原生状态轨道                         | 全文档唯一 ID、跨对象引用及领域规则的统一校验执行                                  |
| 布局与空间   | AnimatedNumber、可动画矩形、ResolvedRect；空间/锚点/注视/避障及姿态值类型                                       | 几何算法、锚点解析、逐帧求值与姿态合成                                             |
| 编辑与文档   | EditOp、EditSubmission、Revision、快照、store/历史/事务/预览契约；诊断、消息树、试算与 EditTools 请求/结果      | store 发布、通知队列、撤销重做、编辑执行、统一 validator、试算与工具算法           |
| 表达式与求值 | 编译器接口、不透明编译产物、引用/诊断；显式求值输入、来源链、共享执行会话；索引/依赖图/缓存接口                 | 编译器、解释器、索引构建、依赖图、实际缓存及候选隔离                               |
| 开放类型     | 自有 TypeRegistry、轨道/锚点/采样类型描述及 Codec、可选 TypeValidator；客户端 resolver/collector 与能力服务契约 | 注册表实现/冻结时序、类型校验分派、客户端执行部件及受控世界查询                    |
| 编解码       | 成员 Codec、DocumentRegistries、根文档组合、严格整数、两阶段 DocumentCodec、结构失败字段路径承载                | Codec 实际填充 fieldPath、NBT/网络 Ops 往返、深度/数量/字节限制及兼容矩阵          |
| 播放与渲染   | 请求/覆盖/时钟/准备帧、事件预算/执行器注册、相机接管、合成与质量状态；网络动作/来源/限额及编解码适配接口        | 时间推进、事件展开与执行、仲裁、真实网络协议、来源解析、多机位 renderer 与质量策略 |
| 存储         | ResourceId、资源服务、载入/保存结果、文件诊断报告、恢复槽与选择契约                                             | 资源根/路径安全、IO、原子替换、恢复调度                                            |
| 编辑器       | 会话、草稿、选择/视图、按 RigId 预览覆盖、交互请求、后端选择与 open 契约                                        | 会话实现、后端装配、草稿恢复、UI/输入/生命周期及预览执行                           |
| 叠加与音频   | OverlayElement 暂存不透明内容；AudioRef / AudioRefId 仅身份与边界                                               | 叠加封闭内容等 O1–O3 补全设计后实施；音频整体最后实施                              |

源码入口：[v2 API](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/>)；回归入口：[v2 测试](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/>)。当前 v2 主源码为 301 个 Java 文件（api / core / editor）。

## 2. 已确认裁决

保留 Q 编号用于解释历史引用；历史阶段安排不再充当当前待办。

| 编号      | 有效结论                                                                                                                              |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------|
| Q1        | 新旧同名类型先在 `_v2` 隔离，旧代码不接线，最后迁回原根包。                                                                           |
| Q2        | 首期不实施音频数据与服务；AudioRef / AudioRefId 只保留身份和边界。                                                                    |
| Q3        | 符号表进入首期：block.Symbols、field.Variable / CustomFunction。                                                                      |
| Q4        | AnimationTrack 保持 sealed，ExtensionTrack 为 non-sealed；扩展不得使用 curve / aim / active / camera_source 内建类型 ID，由 L2 拒绝。 |
| Q5        | AimOverride 位于 playback，分 Inherit / Disabled / Replace；宿主应用覆盖，求值层接收生效 AimState。                                   |
| Q6        | TrackLookup 位于 eval，文档根不提供 trackLookup；expression 只负责编译、引用及轻量诊断。                                              |
| Q7        | 采样参数使用名义接口 SampleArgs，并要求 kind 与 args.type 一致；叠加内容等 O1–O3 定型，不采用泛型替代方案。                           |
| Q8        | sealed KeyframeTrack<T> + AimTrack / ActiveTrack / CameraSourceTrack；状态键共用 ValueKeyframe<T> 与 KeyId，保留原生值类型。          |
| Q9        | 采样三态 VALUE / UNKNOWN / COLLECTION_ERROR；只有 UNKNOWN 可以被作者显式默认值兜底。                                                  |
| Q10       | sampledAt 使用 long 游戏刻，与重采周期及 FrameFacts 同口径。                                                                          |
| Q11 / Q12 | 仅记录早期布局延期与第二批历史范围；不限制后续批次，布局安排由 Q14 更新。                                                             |
| Q13       | edit 位于 api.animation.edit；允许 edit ↔ document 双向引用，仍禁止 api 依赖 core、editor 实现和客户端类。                            |
| Q14       | 布局 AnimatedNumber 已落地，RigView.Rect 四分量可动画，准备帧携带 ResolvedRect。                                                      |
| Q15       | 后续十一项协议均接受原推荐方向，已同步设计；具体运行实现继续分批验证。                                                                |

### 2.1 注册表选型（O + B）

已裁决维持 **O：自有 TypeRegistry**；**B：客户端能力服务**回答实际解析/采集能力。能力服务契约已交付，不再记为“待裁决”。TrackType / AnchorType / SampleType 的可选类型校验钩子也已由 C4 交付；运行分派尚未实现。

类型表保存 ID 与 Codec 等描述对象，不承载游戏内容实例，没有数据包或注册表同步需求。直接在 api 暴露 NeoForge Registry（N）收益有限且扩大版本耦合；RegistryCodecs 没有通用类型分派助手，仍需自行组合。能力缺失是本机运行事实，不作为 common 类型的静态数据。

保留 **N′：core 用 NeoForge 自定义注册表实现既有接口**作为未来选项，仅在出现数据包扩展、同步或别名迁移的真实需求时考虑。启用前验证冻结时机与接口映射、sync(false)/网络 Codec 关系、DeferredRegister 接入、独立测试构造及 NewRegistryEvent → RegisterEvent → 冻结 → 解码顺序。

历史调研锁定 NeoForge 26.1.2.36-beta / Minecraft 26.1.2：以 compileClasspath 和 javap 核对 RegistryBuilder、Registry、DeferredRegister、RegistryCodecs、RegistrySynchronization、DataMapType 与 StreamCodec.dispatch；独立 RegistryBuilder.create 可用，实际 FML 加载/同步时序未验收。该调研不证明自有或 NeoForge 注册表运行实现已完成。

## 3. 必须保持的契约

### 3.1 数据、时间与布局

- 中文 JavaDoc，受检包 NullMarked，可空处 Nullable。文档数据深不可变，集合防御复制，不泄漏数组、JOML 或可变 Component；开放第三方实现须遵守不可变 SPI。OverlayElement.content 的 Object 例外不能记为全部深不可变已验收。
- 新对象由调用方提供唯一 ID，复制经 IdGenerator 生成新身份。TrackId 全文档唯一，语义键仅机位内唯一；状态轨道只持久化一次。
- 数值键只接收 NumberSource（Constant / Formula）；TrackRef 仅用于变量等受支持入口。状态键直接保存 AimState / Boolean / CameraSource，仅 STEP，不编码为浮点值。
- 键时间以秒存储，有限非负、同轨唯一，负零正规化；乱序输入整理但不修改调用方容器。状态轨道首键前用显式默认值，末键后保持。结构构造检查不替代编辑冲突诊断。
- duration 取静态启用机位内容（含布局曲线）、来源、文档轨道、标记及既有范围的最大有限结束时间；静态布局和末键保持不延长。标记无 Tick/指令/发声直接副作用，仍参与时长，退出规则属 A6 待裁决产品项。
- AnimatedNumber 分 Value（常量/公式）与 Keyed（曲线 + 显式默认值）。Keyed 只接受 CLAMP/CLAMP，非空曲线外保持端点，空曲线使用默认值；不新增 TrackId，不通过 TrackRef 提供布局读数。两种矩形坐标模式静态互斥。
- 静态可判定几何在构造时检查；动态求值每帧检查有限性及正宽高。准备阶段产出 ResolvedRect，合成阶段映射输出像素，不能用零尺寸或出屏掩盖求值错误。

### 3.2 编辑、发布、诊断与工具

- EditOp 只表达纯意图；Batch 一次构造完整候选、一次校验发布。无变化不添历史，缺失编辑目标由提交层明确拒绝，不静默覆盖、夹取、量化或运行兜底。状态单键修改用 SetStateKey，草稿字段用保留 owner 类型的 EditTarget.Field。
- 同一 store 换档、撤销、重做继续递增 Revision，不重置或回退；快照一次捕获文档与序号。旧基版本请求不得因换档重新有效。
- EditSubmission 携带不可变动作标签；同步 submit 在通知期间拒绝 REENTRANT_SUBMIT，submitAfterNotifications 延迟到通知结束后重查基版本；标签仅随成功提交进历史。
- LocalizedMessage 是封闭不可变消息树，保留翻译键/fallback、参数顺序、嵌套与受限样式；显示时由无状态 renderer 生成独立 Component，不先转纯文本、不缓存共享可变输出。
- DiagnosticScope 区分 Document(非空 Revision) 与 File(ResourceId)。File 不带对象 ID 或 Fix；Fix 版本必须等于 Document 版本；异步定位/修复必须检查适用版本。
- TrialResult：FAILED 必有 ERROR；PASSED/INCOMPLETE 不得含 ERROR；INCOMPLETE 必有原因。报告包含区间、策略、实际检查量及固定事实身份或说明，不能把有限试算当作全域证明。
- EditTools 只生成 Ready(EditOp) 或整体拒绝，不发布、不读当前 store。缓动/批量请求绑定冻结快照；注视捕获含 mode、实际 AimOverride 与匹配快照的 baseRevision。LEFT_TARGET 只看紧邻左侧 Target，不继续回溯；公式不静默转常量，固定 Replace 需先清除。

### 3.3 表达式、事实与缓存

- expression 包仅依赖 JDK，提供编译/引用/轻量诊断，不公开语法树、不依赖 eval/block/edit/playback。编译失败无可求值句柄；引用含源码区间和函数实参数量。
- 表达式作用域必须一致：函数形参只对本次函数体可见，调用实参在调用方作用域分析，全局变量公式使用空局部表。依赖图、校验与求值器不得互相泄漏作用域，否则真实轨道环会被漏检（R56-H1，C7 修复）。
- ExpressionInputs 显式携带 context、必填 ExpressionSource、可空 RigId、t/p/wt、空间读数、随机种子、sampler 和本帧共享 EvaluationSession。来源区分通道/KeyId、布局分量/可空 KeyId、变量及函数；失败链按外到内保留来源，缓存命中重新包装本次来源。
- 经 TrackRef、曲线公式、变量/函数递归时共享同一工作量额度与深度，不得各自新建额度；失败已用额度不退回。工作量/深度资源失败及包含它们的失败树不得进入会话无关缓存；确定性结果/领域失败按身份缓存。
- CurveSampler 只接受显式 CurveSampleRequest（context + track + rig + sampleTime），不依赖当前机位/store；跨机位 TrackRef 在被引用轨道所属机位求值。
- FrameFactsRequest 含动画时间及逐机位 FrameFactsPlan，显式给出生效锚点/AimState和实际 anchor/target 需求；游戏刻不替代动画时间。provider 不重读默认配置，只准备实际依赖，不因查询加载远端区块。
- FrameFacts.sourceSnapshot 与 TrackIndex.snapshot 必须是绑定快照的同一引用；EvaluationContext 直接拒绝同 revision/同内容的异源快照。sourceRevision 仅作诊断，不证明同源。同快照不同准备计划的正确选择仍由宿主负责。
- 世界实例身份独立分配，同维度重进世界也变化。EvaluationKey 包含快照、上下文、世界实例、帧、sampleTime、机位、轨道、事实与采样表身份；from 从 facts 取采样表版本，时间有限非负且负零正规化。身份分配、TrackLookup 同源及候选隔离仍需 core 实现。
- SampleReading 分 VALUE / UNKNOWN / COLLECTION_ERROR；sample(name, fallback) 只在 UNKNOWN 时惰性执行默认值，采集器缺失、异常、非法输出或预算失败不可兜底。读数不入文档或历史。
- AxisSample 区分 ABSENT / VALUE / ERROR，SpatialResolution 区分 NotRequired / Resolved / Failed；错误不能压成缺失，实际依赖任一错误阻止合成。解析失败诊断版本来自本次 AnchorResolutionContext，不读当前 store。

### 3.4 开放类型与编解码

- 类型注册启动后冻结，重复 ID、冻结后注册及未知类型解码均拒绝。TrackType 上界为 ExtensionTrack，内建轨道不能注册成扩展；锚点存档 MapCodec 与网络 StreamCodec 由同一 AnchorType 提供。common 类型描述与客户端执行部件分开注册。
- 扩展轨道必须实现纯派生的 `withName(String)`：`RenameTrack` 直接调用它并保留 TrackId / 类型 ID / 自身 Codec 可编码字段；不得用框架不认识的包装器绕过类型 Codec。该破坏性扩展点变更要求第三方实现补齐并重新编译。
- TypeValidator 只处理已解码单实例自身约束；TypeValidationContext 携带 Revision、稳定对象 ID、可选所属 Rig 与字段路径，不读当前 store、不修改候选。跨对象引用/区间规则归统一 DocumentValidator，实际分派仍延期。
- 成员类型维护自身 Codec/MapCodec；根文档只组合，不复制子字段规则。封闭分支使用显式 type/mode/kind 标签；未知分支不回退默认。data_version 必填且仅接受 CURRENT_DATA_VERSION。
- DocumentCodec 先结构解码，再统一文档校验；结构失败无候选、不调用 validator，解码边界将构造异常转为 StructureFailed。任一 ERROR 拒绝发布；Accepted 不含 ERROR，ValidationFailed 至少含一条 ERROR。
- StructureFailed 可承载 fieldPath；未提供路径不代表不存在错误。Codec/存储实际填充路径和转换结构化 Diagnostic 仍待后续，不能以承载类型已存在宣称定位完整。
- O1–O3 前 overlays 双向拒绝非空内容，不静默丢失：根 MapCodec 正式声明可选受检字段，进入压缩 Ops 键表；空列表/缺省/null 接受，非空列表与非列表拒绝，直接根 Codec 编码同样受限。
- 整数字段用 strictInt 精确检查整数性与范围：Float/Double 按已表示的二进制值，其他 Number（含 Gson 字面量与 BigDecimal）按十进制文本精确检查；拒绝小数、溢出及高精度小数，不改写 Float 整数。适用于版本、层级、更新帧周期、重采游戏刻周期。

### 3.5 播放、事件、接管与质量

- 请求覆盖按 RigId 定位，未知机位拒绝；锚点覆盖失败不回退。AimOverride 三态不合并为可空值。速度有限为正，开始时间有限非负且在可播放范围，不支持反向。
- start 先检查请求/基版本并准备初始条件，成功后才换档和切换状态；失败保持原文档、序号、播放状态及相机控制。PlaybackService 与相机控制登记/释放/捕获均限宿主主线程。
- PreparedFrame 捕获快照、事实、来源、活动姿态和事件；RigPose 冻结矩形、zOrder、resolutionScale、updateEveryFrames。执行/合成不重读 store；任一路实际依赖失败整帧不执行。
- 事件按推进区间/周期顺序，再按区间内时间，再按同刻文档轨道序；prepareFrame 在展开时消费 EventBudget。EventExecutor 只接收原序连续同类型片段或单事件，不跨类型/周期聚合重排。执行异常报告部分执行并终止，不承诺回滚宿主副作用。
- winningPose 一次捕获获胜 Claim 与 WorldPose，provider 每帧至多调用一次；捕获中的登记/释放影响下次捕获，当前已捕获结果至多应用一次。释放后禁止新调用和复用旧缓存；禁止嵌套捕获/发布，异常转准备失败，不静默回 Player。
- WorldPose 旋转为弧度 YXZ，FOV 为正有限角度（度）。按 zOrder 升序、同层文档序合成；来源 Player 保留正常玩家底图，来源 Rig 优先保留。声音监听器只随实际来源更新一次，副路不重复模拟或执行事件。
- SampleIdentity 绑定渲染帧、动画时间、游戏刻、世界实例、快照、活动区间代际与来源代际。实际呈现（含复用）必须带身份；FRAME_SKIPPED 呈现旧帧，RIG_PAUSED 不呈现；身份不符立即失效，恢复活动/可见须先生成有效新帧。
- 可见像素质量以输出像素中的完整矩形 R 和画布交集 V 为依据，不推断其他机位遮挡；投影宽高比、FOV、UV及完整构图不变。部分裁剪可降低完整纹理密度，属有损降级，允许关闭；函数/最低尺寸/档位/滞回待 R0 实测，不写进文件语义。
- V 为空只跳过副路绘制，不跳过实际依赖求值或吞错误；当前来源仍应用姿态、监听器和必要主世界绘制。CANVAS_OFFSCREEN 与预算停路分开报告。
- C5 后，无目标时实际宽高必须同时为 0，密度为 0；有目标时宽高和密度为正。可见但停路机位仍可携带完整/可见/请求几何，不能伪造 offscreen。rendered=true 仍要求正尺寸、采样身份及可见 quality，内外实际尺寸一致。未呈现但仍保留目标时允许正尺寸/正密度。
- 网络动作分 Start/Stop，来源分 Embedded/Local；Embedded 转本地请求必须沿用同一文档实例，Local 由接收端资源服务解析。协议版本和限额显式传入，覆盖完整保留三态。encode 成功返回 DataResult<Unit> 的 Unit.INSTANCE，StreamCodec 适配失败转 EncoderException/DecoderException；失败缓冲区由调用方丢弃。具体字节协议和收包校验尚未实现。

### 3.6 存储与编辑器

ResourceId 是非空白资源身份，不能把该局部检查当作路径安全已完成。文件诊断报告限定单一资源 File 作用域；载入仍须完整校验。保存/恢复采用临时文件与原子替换，恢复槽不覆盖正式文件、不保存渲染缓存或撤销栈。

EditorSession 写文档必须走 EditSubmission/store；播放头、轨道显隐、选择、预览覆盖只属会话，不入文档/撤销。交互过期或选项无效必须拒绝；后端自动/指定均须 canOpen，通过后调用 open；指定拒绝/失败不静默切换，未注册 ID 才按约定回内置。会话/后端切换恢复仍待实现。

## 4. 实施批次与施工记录

审核与提交不是同一编号空间：提交用 C，审核用 R；早期 R7–R48 混合保留历史编号。审核结论与关闭边界单独维护在[P1 审核与复核记录](<P1-review.md>)，本节只记录实施内容、施工证据与未完成边界。

| 批次 | 日期 / 基线 | 内容 | 审核状态 |
|---|---|---|---|
| C5 | `7095284` | P1 契约与 C5 两项修复 | R54 原范围通过 |
| 2026-10-25 批次 | `95c3dda` | core 实现与自动验收 | R55 发现九项问题 |
| C6 | 基于 `95c3dda`，已提交 `40abc7e` | R55 九项修复 | R56：八项关闭，依赖环部分关闭 |
| C7 | 基于 C6，已提交 `40abc7e` | R56-H1 形参作用域修复 | R57：原范围关闭 |
| C8 | 基于 C7，已提交 `40abc7e` | 相机接管仲裁、类型能力服务、实参作用域测试 | 施工方自检；独立复核待 R58 |
| C9 | 基于 C8，已提交 `40abc7e` | 消息渲染、缓动/批量/烘焙/注视捕获、EditTools 请求扩展 | 施工方自检；独立复核待 R59 |
| C10 | 基于 C9，已提交 `40abc7e` | 播放协议动作接收入口 | 施工方自检；独立复核待 R60 |
| C11 | 基于 C10，已提交 `40abc7e` | 编辑器 UI 后端选择 / 回退编排 | 施工方自检；独立复核待 R61 |
| C12 | 基于 C11，已提交 `40abc7e` | NBT 文档编解码与深度 / 条目 / 字节限制 | 施工方自检；独立复核待 R62 |
| C13 | 基于 C12，已提交 `40abc7e` | 全文档 KeyId 唯一性 | 施工方自检；独立复核待 R63 |
| C14 | 基于 C13，已提交 `4bffdc0` | NBT 文件资源服务 | 施工方自检；独立复核待 R64 |
| C15 | 基于 C14，未提交 | NBT 结构失败字段路径 | 施工方自检；独立复核待 R65 |

### 4.1 历史交付摘要

历史范围、交付内容与关闭边界见审核文档 §1.1；本节不重复 R54/R55/R56 的审核证据。C5 的两项修复范围已由 R54 复核关闭，C6 的九项修复见 §4.3，C7 见 §4.4。

### 4.2 `95c3dda` 实现批次明细（2026-10-25，R55 已审核）

本表保留实现清单，并包含 C6 补充状态；R55 发现九项问题，修复与关闭边界分别见 §4.3 与审核文档 §4。

| 范围 | 实现 | 边界 |
|---|---|---|
| 文档发布 | `CoreAnimationDocumentStore`：提交、历史、换档、重入拒绝、延迟队列、按 Revision 装配 validator | 历史仅 64 项；32 MiB 估算未实现 |
| 编辑操作 | `EditOpApplier`：覆盖全部 `EditOp` 分支与 Batch 原子应用 | 第三方扩展轨道 `withName` 必须保留 TrackId/type，否则拒绝 |
| 统一校验 | `UnifiedDocumentValidator`：L1–L4、ID/引用、来源活动区间、类型校验器分派、表达式引用绑定、依赖环、同轨 KeyId | 全文档 KeyId 跨对象作用域、资源规模、跨文档引用不在本批 |
| 类型注册表 | `FrozenTypeRegistry` 与 Anchor/Track/Sample 三个冻结实现 | 注册期与冻结期语义已测，客户端能力服务未实现 |
| 轨道索引 | `SnapshotTrackIndex`：绑定快照、所属机位、重复 ID 诊断 | 不解析公式、不判断活动区间 |
| 曲线采样 | `ConstantCurveSampler` + `CurveInterpolation`：STEP/LINEAR/HERMITE、三种 wrap | 仅常量键；公式键明确报 `expression_evaluator_unavailable`，不兜底 |
| 公式曲线 | `ExpressionCurveSampler`：常量/公式端点、STEP 只求值左端、失败诊断 | 空间读数仍为空事实；无编译缓存 |
| 播放时间 | `PlaybackTimeline`：速度、循环、夹取、定位 | 未接真实帧准备与相机应用 |
| 播放服务 | `CorePlaybackService`：启动/暂停/恢复/定位/停止/失败终止、线程约束、时钟推进、`clock()` | 完整宿主帧执行/相机应用未接 |
| 帧准备 | `CorePlaybackFrameFactory`：事实、活动机位、通道补全、局部锚点、注视、布局、路径、旋转合成、避障端口、来源活动性、事件接线 | 真实世界碰撞/读数适配未接 |
| 避障端口 | `ObstacleResolver`：起点/期望位置/保留距离，调整或明确不可用 | 真实世界碰撞查询由宿主实现，当前无客户端适配 |
| 旋转合成 | `CoreRotationMath`：YXZ 矩阵、父子旋转合成、注视角、万向节处理 | 与 MC 相机度/象限边界转换仍属宿主 |
| 路径采样 | `CorePathSampler`：LINEAR / BEZIER / CATMULL_ROM、弧长表、绝对/百分比 | 弧长按 64 段近似；无避障/切线朝向应用 |
| 布局求值 | `AnimatedNumberEvaluator` + `ResolvedRectEvaluator`：常量/公式/曲线、STEP/LINEAR/HERMITE、正尺寸检查 | 布局公式默认已走编译缓存 |
| 事件展开 | `PlaybackEventExpander`：非循环/循环区间、零时刻、文档顺序、预算失败 | 每个 timeline key 暂按一个事件；载荷/KeyId 不进结果 |
| 播放请求校验 | `PlaybackRequestValidator`：开始时间、覆盖机位、局部锚点、Replace Roll | 不替代文档统一校验 |
| 依赖图 | `CoreDependencyGraph`：TrackRef/变量/函数展开、未定义引用、arity 与轨道环诊断 | C6 已纳入布局矩形与叠加透明度；构建期每次全量重算；函数形参作用域由 C7 修正 |
| 编辑器会话 | `CoreEditorSession`：提交/事务草稿/预览/撤销重做/视图/交互/播放控制/关闭 | 文本字段解析仅保留原文+诊断；真实 UI/后端未接 |
| 存储路径 | `ResourcePathResolver`：根内解析、遍历/绝对路径/符号链接逃逸拒绝 | 不消除 TOCTOU |
| 载入发布 | `CoreAnimationLoader`：资源服务 → `switchDocument`；A1 端到端入口 | 依赖资源服务的结构解码；不替代统一校验 |
| 帧事实计划 | `FrameFactsPlanBuilder`：按时间求值注视、应用覆盖三态、生成锚点/目标需求 | 不解析世界坐标、不判断实际渲染依赖 |
| 帧事实提供 | `CoreFrameFactsProvider`：逐项解析、采样周期缓存、表版本、预算与 valid | v1 世界查询面保守为空；真实客户端读数未接 |
| 事件分派 | `CoreEventDispatcher`：连续同类型片段、`maxEvents`、缺失执行器/异常终止 | `maxIntervals` 由准备阶段消费；事件载荷解析仍待事件轨道实现 |
| 网络协议 | `FlatPlaybackProtocolCodec`：v1 字节布局、Embedded/Local、覆盖三态、限额与截断拒绝 | 包注册/传输/收包校验/文档校验未做 |
| 表达式编译 | `DefaultExpressionCompiler`：数字/算术/调用/`sample`/内建函数、引用与诊断区间、嵌套/长度上限 | 单次实现本身无缓存 |
| 编译缓存 | `BoundedExpressionCompiler`：源码文本 LRU、成功/失败缓存、并发去重 | 默认接入 evaluator/sampler/布局；graph/validator 未统一注入 |
| 表达式绑定 | `ExpressionValidation`：编译公式并检查变量/函数/样本引用、函数 arity、形参遮蔽 | 运行期求值、空间内建读数仍待 |
| 表达式求值 | `CoreExpressionEvaluator`：算术/内建函数/变量/函数/`sample`/预算深度 | 空间内建读数与真实宿主接线待后续 |
| JSON 资源服务 | `FileAnimationResourceService`：本地 JSON 载入/保存、SHA-256、原子替换、稳定枚举 | 仅结构解码；无 NBT/网络 |
| 资源限制 | `JsonResourceLimits` + `JsonDepthGuard`：读取/解析前限制单文件字节与 JSON 深度 | 目录资源数量、NBT/网络载荷限制未做 |
| 结构定位 | `StructureFieldPath`：整数语义字段/未知类型/overlays/消息字段名定位到 JSON 路径 | 只覆盖有证据的位置；纯 Codec 消息无路径时为空 |
| 恢复服务 | `FileRecoveryService`：独立恢复根、异步捕获、自包含槽、16 槽裁剪、结构恢复 | 仅本地 JSON；真实调度/客户端接线未接 |
| NBT 往返 | `CameraAnimation.codec` 在 `NbtOps` 下完整文档往返测试通过 | 网络包注册/传输、NBT 资源服务未做 |
| 扩展点 | `AnimationTrack.ExtensionTrack.withName(String)` 成为必需方法，`RenameTrack` 直接调用 | 破坏性变更：第三方实现须补齐并重新编译 |

该实现批次当时执行：`clean compileJava test --no-build-cache --rerun-tasks`，**59 suite / 511 用例**（v2 53 / 445），失败/错误 0，跳过 1（Windows 无符号链接权限，JUnit assumption）。本批仅表示实现落地，不代表审核通过或运行验收完成。

### 4.3 C6：R55 九项审核修复（2026-10-26，未提交，R56 已复核）

本轮按审核结论直接修代码，未改主设计。允许的破坏性更新包括：[PlaybackFrameFactory](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/PlaybackFrameFactory.java>) 的 `prepare` 增加未取模终点并新增 `prepareStart`；`FrameFacts` / `FrameFactsRequest` / `FrameEnvironment` 增加世界时间事实；`ExpressionInputs` / `LayoutEvalInput` 的 `wt` 收紧为 `[0,1)`；`Curve` 与状态轨道构造器增加同轨 `KeyId` 唯一检查。第三方帧工厂、帧事实与公式输入构造方须同步重编译。

| 审核问题 | 修复与证据 | 结论 / 边界 |
|---|---|---|
| [高] 启动在初始准备成功前就换档 | [CorePlaybackService](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackService.java>) 改为：请求检查 → 候选快照 → `prepareStart`（事实、锚点、姿态、来源）→ 无 ERROR 才 `switchDocument` → 进入 `PLAYING`。失败只返回 `StartResult`，不改 `request` / `timeline` / `state`。[PlaybackRequestValidator](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/PlaybackRequestValidator.java>) 遍历**全部机位**检查局部空间生效锚点，不再只看覆盖条目。[CorePlaybackServiceTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackServiceTest.java>) 覆盖“旧文档播放中、新文档初始锚点失败仍保持旧状态”；[PlaybackRequestValidatorTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/PlaybackRequestValidatorTest.java>) 覆盖空覆盖表下的局部机位；[CorePlaybackFrameFactoryTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactoryTest.java>) 覆盖 `prepareStart` 的锚点成功/失败 | 原两处反例关闭；候选结构校验仍在 `switchDocument`，但拒绝发生在任何文档/序号/状态变更之前；真实宿主锚点解析未验收 |
| [高] 循环推进先取模，完整周期事件丢失 | [PlaybackTimeline](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/PlaybackTimeline.java>) 保留未取模 `advancedTime`（循环可大于时长，非循环夹在结尾；定位同时重置）；`PlaybackFrameFactory.prepare` 显式接收 `fromTime` / `toTime`；[CorePlaybackService](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackService.java>) 传入相邻未取模终点，[CorePlaybackFrameFactory](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactory.java>) 用最终相位求姿态、用完整区间展开事件 | 原“2→2 丢事件”反例关闭；`PlaybackEventExpander` 本身未改，仍支持跨多周期绝对终点；`float` 终点在超长会话有精度上限，未引入 double 事件 API |
| [高] Local 机位用世界姿态补缺失轴后重复应用锚点 | `CorePlaybackFrameFactory` 改为：基础世界姿态先按参考系旋转的逆变换进入文档参考系 → 逐轴用 `VALUE` 覆盖、`ABSENT` 保留基础值 → 再正变换回世界；新增 [CoreRotationMath#inverse / rotateInverse](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/math/CoreRotationMath.java>) | 原“锚点 (100,0,0)、基础 (10,20,30) 输出 (110,20,30)”反例关闭；测试覆盖无通道时位置回到基础世界位置，以及掩码 ALL/NONE 的位置与朝向；万向节附近欧拉往返误差未做长期数值验收 |
| [高] 锚点旋转跟随掩码及请求掩码覆盖未应用 | 生效掩码按“请求覆盖 → 局部空间文档 `followMask` → 世界空间 `NONE`”确定，`anchor.rotation().masked(mask)` 同时用于位置与旋转的正/逆变换；[CorePlaybackFrameFactoryTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactoryTest.java>) 覆盖文档 NONE、文档 ALL，以及文档 ALL 被请求 NONE 覆盖 | 原“NONE 仍被锚点旋转改变”反例关闭；未启用轴置零后位置与旋转一致；请求掩码对世界机位无意义的行为保持不变 |
| [中] 避障请求覆盖未生效 | 生效 `ObstacleSettings` 与 `margin` 改为“请求覆盖优先、否则文档默认”；关闭时完全不调用 resolver；开启且无 resolver 仍明确失败。测试覆盖文档开/请求关（无 resolver 也可执行）与文档关/请求开（调用 resolver 并修正位置） | 原配置分派缺口关闭；真实世界碰撞查询仍由宿主 `ObstacleResolver` 实现，未接客户端 |
| [高] 统一校验未接入依赖环 | [CoreDependencyGraph](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java>) 在完整候选上遍历轨道公式、变量 / 函数根、布局矩形公式与叠加透明度，新增 `cycleDiagnostics()`；[UnifiedDocumentValidator](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidator.java>) L3 纳入环诊断。测试覆盖未被任何轨道引用的 `a = Formula("a")` 与函数自调用，并分别验证图与统一校验 | 部分关闭：未引用变量自环和函数自调用反例关闭，但函数形参作用域仍可隐藏真实轨道环，见 R56-H1（C7 修复）；未定义引用 / arity 仍由 `ExpressionValidation` 报告，环由依赖图报告；图每次校验全量重算 |
| [高] 同轨重复 `KeyId` 可构造和发布 | [Curve](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/Curve.java>) 增加同曲线 `KeyId` 唯一检查；[KeyframeSequences](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/KeyframeSequences.java>) 覆盖状态轨道；[UnifiedDocumentValidator](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidator.java>) L2 复核通道、布局曲线与状态键；新增 `DUPLICATE_KEY_ID` 诊断码，存储分类不再误报为时间冲突 | 原“替换通道注入同 KeyId 两键”入口在构造期关闭；测试覆盖数值曲线与状态轨道；**全文档跨对象 KeyId 作用域仍未统一**，按审核要求留待后续 |
| [中] 同一帧 `wt` 通道与布局相差 20 倍 | 新增 [WorldTime](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/WorldTime.java>)：宿主提供 `dayTime` 与**可被数据包修改**的 `dayLength`，`progress()` 统一归一化为 `[0,1)`；`FrameEnvironment` / `FrameFactsRequest` / `FrameFacts` 携带该事实；[ExpressionCurveSampler](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/ExpressionCurveSampler.java>) 与布局入口都只读 `facts.worldTime().progress()`；`ExpressionInputs` / `LayoutEvalInput` 强制 `wt ∈ [0,1)`，避免低层调用重新引入单位漂移 | 原 20 倍差关闭，且不写死 24000；测试用 `WorldTime(3000, 12000)` 验证通道与布局同一公式都得到 0.25，并覆盖非法 `wt` 范围；宿主适配器仍需提供真实世界日时间与数据包昼夜长度，未接客户端 |
| [中] 会话不响应换档，旧草稿 / 选择 / 交互残留 | [DocumentListener](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentListener.java>) 增加默认 `onSessionSwitched`；[CoreAnimationDocumentStore](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/document/CoreAnimationDocumentStore.java>) 在同一次通知周期内、文档与序号更新后通知换档；[CoreEditorSession](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreEditorSession.java>) 订阅并清理草稿、当前事务、选择 / 视图、交互请求与已记录选择，非宿主线程通知先挂起 | 原“旧选择 / 对话框出现在新文档”反例关闭；普通编辑、撤销、重做不清理会话；真实 UI 刷新与后端切换未验收；`DocumentListener` 新增默认方法不破坏既有单方法实现 |

本轮验证：`.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，**60 suite / 537 用例**（v2 54 / 471），失败/错误 0、跳过 1（Windows 无符号链接权限）。另以源码扫描确认 v2 api 对 core / 客户端、editor.spi 对 editor.internal 的禁止 import 为 0；C6 新增 [WorldTime](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/WorldTime.java>)，当前编译产物中 v2 API class 数为 386。C6 当时为未提交修改，R56 复核结论见审核文档 §4。

### 4.4 C7：R56-H1 形参作用域修复（2026-10-26，未提交，R57 已复核）

**触发与范围。** R56 将依赖环检查列为部分关闭：函数形参可泄漏到全局变量公式，使真实轨道环在发布前不可见。C7 只修 `core` 依赖图的词法作用域分析，不修改主设计，不改变公开 API，无新增破坏性扩展点；工作树中的 C6 修改保持不变。第三方实现无需因 C7 重编译。

**根因。** [CoreDependencyGraph](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java>) 在展开全局变量公式时把当前 `locals` 继续下传；`resolveFunctionBody` 又把调用方形参并入被调用函数体。实际求值器进入变量公式时使用空局部表，进入函数体时只使用自身形参，因此图与运行期作用域不一致。R56-H1 的精确反例为：

```text
轨道 T 的键值 = Formula("f(1)")
函数 f(x) = v
全局变量 v = Formula("x")
全局变量 x = TrackRef(T)
```

旧图在函数体内解析 `v` 时局部表仍含 `{x}`，于是把真正的全局 `x` 当成形参跳过，`dependenciesOf(T)` 为空，环无法进入 L3。

**修复。**

| 点 | 变更 |
|---|---|
| 全局变量公式 | `resolveVariable` 对 `Formula` 源使用 `Set.of()` 展开，清空局部表；变量定义始终按文档级作用域分析 |
| 函数体 | `resolveFunctionBody` 仅使用 `function.parameters()`，不再继承调用方局部名；每个函数体建立独立词法作用域 |
| 调用实参 | 保持由 `resolveFormula` 按调用方 `locals` 遍历；实参表达式的变量引用仍在调用方作用域展开 |
| 契约说明 | 类 JavaDoc 增加“作用域”一节，明确依赖图必须与 `CoreExpressionEvaluator` 一致，防止后续回归 |

**回归测试。** 新增 5 个用例，全部位于 v2 测试集：

| 测试 | 覆盖 |
|---|---|
| [CoreDependencyGraphTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraphTest.java>) `globalVariableFormulaDoesNotSeeFunctionParameters` | `f(x) = v`、`v = Formula("x")`、全局 `x = TrackRef(B)`：依赖为 `B`，证明全局 `x` 未被形参吞掉 |
| 同上 `trackCycleHiddenByFunctionParameterIsReported` | 把 `x` 换回 `TrackRef(T)`：`dependenciesOf(T)` 含 `T`，图报告 `Track dependency cycle` |
| 同上 `calleeBodyDoesNotInheritCallerParameters` | `f(x) = g(x)`、`g(y) = x`、全局 `x = TrackRef(B)`：被调用函数体不继承调用方形参，依赖为 `B` |
| [UnifiedDocumentValidatorTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidatorTest.java>) `rejectsTrackCycleHiddenByFunctionParameterScope` | 与 R56-H1 相同的完整候选经 L3 强制校验被拒绝，诊断含 `Track dependency cycle` |
| [CoreExpressionEvaluatorTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreExpressionEvaluatorTest.java>) `variableFormulaDoesNotSeeCallerFunctionParameters` | 运行时对照：全局 `x = 7`、`v = Formula("x + 1")`、`f(x) = v`，`f(2) = 8`，证明图与求值器口径一致 |

**施工验证。**

- 定向测试：`.\gradlew.bat --offline test --tests ...CoreDependencyGraphTest --tests ...UnifiedDocumentValidatorTest --tests ...CoreExpressionEvaluatorTest --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL。
- 全量：`.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按 `build/test-results/test` XML 统计 **60 suite / 542 用例**（v2 54 / 476），失败/错误 0、跳过 1（Windows 无符号链接权限；`ResourcePathResolverTest` 的符号链接 assumption）。
- 架构门禁：JDK 25 `jdeps --ignore-missing-deps -verbose:class -filter:none` 检查 **386 个 v2 API class**，对旧根、`net.minecraft.client` 及 v2 core / editor 实现包的禁止类型依赖为 **0**；`editor.spi → editor.internal` 为 **0**。
- 变异检查（临时，已恢复）：把全局变量公式的局部表改回旧行为 `locals` 后，`globalVariableFormulaDoesNotSeeFunctionParameters`、`trackCycleHiddenByFunctionParameterIsReported`、`rejectsTrackCycleHiddenByFunctionParameterScope` 三项失败（定向 52 用例 / 3 失败）；恢复修复后同一命令通过。该检查只回退全局变量公式一行，用于证明新增用例确实覆盖原漏检路径，不替代独立审核。
- `git diff --check` 通过。C7 未新增/修改公开 API，v2 API class 数保持 386。

**关闭边界。**

- C7 修复 R56-H1 的源码调用链并增加动态反例；R57 独立复核确认原问题范围关闭。不改变依赖图“静态引用全部进入依赖”的口径，`select` 两侧仍按设计静态检查。
- R56 及更早已记录的边界继续有效：全文档跨对象 `KeyId` 作用域、超长会话 `float` 事件终点精度、真实宿主锚点解析 / 世界查询 / 碰撞 / 客户端接线未验收；`wt` 主设计的单位、范围与事实来源同步仍为文档维护项。
- R57 已独立复核：R56-H1 在原问题范围关闭，未发现 C7 引入的确定误报、漏检或其他回归；保留边界见审核文档 §5.3。C7 的施工方自检与独立复核分别记录。

### 4.5 C8：相机接管仲裁与类型能力服务（2026-10-26，未提交）

**范围。** 本轮补两处已列 core 契约的默认实现，并落实 R57 的非阻断建议；不改公开 API，未改主设计。C8 只提供宿主装配用的 core 部件，不接 renderer、编辑器 UI 或真实客户端世界查询。

**1. 相机控制声明仲裁。** [CoreCameraControlClaims](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CoreCameraControlClaims.java>) 实现主设计 §7.5 的默认仲裁：

- 数值优先级高者获胜；同优先级按登记顺序，先登记者获胜；释放后立即重新仲裁。
- `winner` 不调用姿态提供器；`winningPose` 先冻结获胜声明，再对该声明的 provider 同步调用一次并返回声明 + 姿态。
- provider 执行期间发生的释放或更高优先级登记只影响下一次捕获；本次仍返回冻结结果。
- provider 返回 `null` 抛 `NullPointerException`，运行时异常原样传播；嵌套 `winningPose` 抛 `IllegalStateException`。
- `register` / `release` / `winner` / `winningPose` 都拒绝非宿主线程；释放未知或伪造令牌抛 `IllegalArgumentException`，不影响其他声明。

[CoreCameraControlClaimsTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CoreCameraControlClaimsTest.java>) 覆盖 12 个用例：优先级、同优先级顺序、provider 调用次数、重复释放、伪造/未知令牌、provider 异常/空姿态、嵌套捕获、捕获期间释放/登记、跨线程拒绝与空输入。

**2. 类型能力服务。** [CoreTypeCapabilityService](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/registry/CoreTypeCapabilityService.java>) 与 [FrozenAnchorResolverRegistry](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/registry/FrozenAnchorResolverRegistry.java>) / [FrozenSampleCollectorRegistry](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/registry/FrozenSampleCollectorRegistry.java>) 实现主设计 §2.2、§6.3、§7.7 的只读能力查询：

- 构造要求 common 三张类型表与客户端解析器 / 采集器表全部冻结，避免装配后新增类型让查询漂移。
- 锚点 / 采样保持三态：common 无类型 → `TYPE_NOT_REGISTERED`；有类型但无解析器 / 采集器 → `RUNTIME_IMPLEMENTATION_MISSING`；两者都有 → `AVAILABLE`。
- 轨道对内建 `curve / aim / active / camera_source` 与已注册扩展类型返回 `AVAILABLE`；非内建且未注册返回 `TYPE_NOT_REGISTERED`。
- 只有解析器 / 采集器、没有 common 类型时仍报告 `TYPE_NOT_REGISTERED`，不把实现误当成类型存在。
- 查询不注册类型、不加载客户端类、不读当前文档或世界。

[CoreTypeCapabilityServiceTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/registry/CoreTypeCapabilityServiceTest.java>) 覆盖 11 个用例：三态、内建轨道、扩展轨道、只有客户端实现、冻结前置条件与空 ID。

**3. R57 非阻断建议。** [CoreDependencyGraphTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraphTest.java#L172>) 新增 `callArgumentsStayInCallerScope`：实参 `a` 是调用方形参、对应轨道 A，被调函数体读取全局 `c`、对应轨道 B；正确结果为 `{B}` 且无环。若实参误按被调函数作用域展开，会读到全局 `a` 并形成 `A → A` 环，因此该用例能独立证明实参作用域，而不依赖集合去重。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **62 suite / 566 用例**（v2 56 / 500），失败/错误 0、跳过 1（Windows 符号链接权限）。C8 新增 24 个用例、2 个 suite；v2 主源码由 288 增至 292 个 Java 文件，API class 仍为 386。JDK 25 jdeps 对 386 个 API class 的禁止类型依赖为 0，`editor.spi → editor.internal` 为 0，`git diff --check` 通过。

**关闭边界。**

- 相机接管仲裁只提供 core 表；尚未接入编辑器会话、renderer、相机控制登记令牌生命周期或真实宿主帧循环。“每帧至多调用一次 provider”仍由宿主按帧调用 `winningPose` 保证；core 只能拒绝同一次捕获内的嵌套调用。
- “provider 不得发布文档”属于宿主契约，core 无法检测任意发布调用；不做隐式拦截。
- 类型能力服务只回答当前冻结快照；编辑器徽标、播放前提示、真实 resolver / collector 注册、受控世界查询与客户端接线都未验收。
- R58 独立复核尚未执行；C8 不能写成审核通过。

### 4.6 C9：消息渲染与编辑工具（缓动/批量/烘焙/注视捕获）（2026-10-26，未提交）

**1. 消息渲染。** [CoreLocalizedMessageRenderer](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreLocalizedMessageRenderer.java>) 实现 `LocalizedMessageRenderer`：

- `Literal` 使用 `Component.literal`，`Translatable` 使用 `Component.translatable` / `translatableWithFallback`，参数按原顺序转换；
- 嵌套 `Argument.Message` 递归渲染，不先转纯文本；
- 只应用 `Formatting` 的 RGB 颜色、粗体、斜体与下划线；未设置样式保持继承；
- 每次调用生成独立组件，不缓存、不回写消息；调用方修改返回值不影响下一次渲染。

[CoreLocalizedMessageRendererTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreLocalizedMessageRendererTest.java>) 覆盖 5 个用例：受限样式、翻译回退与参数顺序、嵌套消息、组件独立性、空文本与空输入。

**2. 编辑工具请求扩展（破坏性）。** 为后续 `CoreEditTools` 提供完整冻结输入：

- [AimCaptureRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/AimCaptureRequest.java>) 增加必填 `WorldPose basePose` 与 `EvaluationSession session`。原因：`EvaluationContext` 只含快照 / 事实 / 索引，没有基础相机姿态；缺失旋转 / 位置通道时无法得到“关闭注视前的有效姿态”，也无法继承共享额度与深度。
- [BakeRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/BakeRequest.java>) 增加必填 `EvaluationSession session`；烘焙的所有采样点必须共享同一额度与深度，不能逐点新建会话。
- 同步更新[主设计 §4.7](<../design-animation-editor-framework.md#L382>)与 [UML 字段](<../design-uml.md#L1349>)；构造器变更要求调用方重新编译，属于本轮允许的破坏性更新。
- [EditToolsBindingTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/EditToolsBindingTest.java>) 更新并覆盖新字段、基版本不一致、空 `basePose` / 空 `session`。

**3. 编辑工具主体。** [CoreEditTools](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/edit/CoreEditTools.java>) 实现四个工具：

- 缓动：目标必须是曲线轨道；预设曲柄长度超过统一校验 L4 上限时整体拒绝；选中的键不存在时拒绝；输出 `Batch(SetKeyInterpolation, SetKeyHandles)`。
- 批量变换：按“先取反、再缩放、最后偏移”计算常量值；公式值显式拒绝，不静默转常量；先计算全部最终时间并检查轨道内冲突；状态轨道只允许时间移动，值域变换拒绝；扩展轨道暂不支持。
- 烘焙：要求轨道属于请求机位；按 `[start, end]` 生成含端点的时间序列，采样前检查 `maxKeys`；所有采样共享 `BakeRequest.session`；`ABSENT` / `ERROR` / 采样器异常整体拒绝；成功输出整条 `ReplaceKeys`。
- 注视捕获：通过 [CorePlaybackFrameFactory#prepareRig](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactory.java>) 复用播放的通道、锚点、注视、参考系与布局求值，使用请求的 `basePose` 补全缺失轴；写同时间旋转 X/Y/Z 常量键与注视 Off 键；`Replace`、显式 `Disabled`、无有效目标、事实无效、缺少旋转通道或 `LEFT_TARGET` 无同刻 Off 键时整体拒绝；已有键替换产生 WARNING，不静默覆盖。

[CoreEditToolsTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/edit/CoreEditToolsTest.java>) 覆盖 9 个用例：缓动的插值/曲柄与缺失键/超长曲柄、批量值变换与时间冲突/公式拒绝、烘焙端点与 ID、烘焙超键数前置拒绝与 ABSENT 拒绝、注视捕获的旋转 + Off 输出与 `Replace` / 缺通道拒绝。

**4. 边界。** `captureAim` 通过冻结事实与基础姿态求值，不再访问当前 store；但 `LEFT_TARGET` 仍要求调用方把左侧 Target 的解析结果放进本帧事实，不能仅凭文档模式临时重解析。已有同时间键的替换以 WARNING 提示，不是显式确认字段；若需要强制二次确认，应在后续编辑器 UI 契约中增加。批量变换的“先取反、再缩放、最后偏移”顺序、烘焙整轨替换与含端点口径在本次固定，后续若要改变属于行为变更。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **64 suite / 580 用例**（v2 58 / 514），失败/错误 0、跳过 1（Windows 符号链接权限）。C9 相对 C8 新增 14 个用例、2 个 suite（渲染器 5 + 工具 9）；v2 主源码 294 个 Java 文件，API class 仍为 386；JDK 25 jdeps 对 386 个 API class 的禁止类型依赖为 0，`git diff --check` 通过。

**关闭边界。** 渲染器尚未接语言切换、UI 宿主或显示缓存；仅支持契约列出的受限样式。R59 独立复核尚未执行。

### 4.7 C10：播放请求接收入口（2026-10-26，未提交）

[CorePlaybackRequestReceiver](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackRequestReceiver.java>) 把已解码的 `PlaybackAction` 接到本地播放服务：

- `Stop` 直接调用 `PlaybackService.stopAndReset()`；不要求发布序号。
- `Start` 的 `Embedded` 沿用同一文档实例，`Local` 通过 `AnimationResourceService.load` 解析；`Start#toRequest` 继续校验 Embedded 实例身份。
- 开始动作统一交给 `PlaybackService.start(request, expectedRevision)`，因此基版本、播放参数、启动准备与文档 store 的统一校验仍走既有链路；接收端不复制校验、不构造默认锚点。
- 本地资源载入拒绝返回 `HOST_CAPABILITY_MISSING` 并携带原 File 诊断；缺少开始序号、未知来源或请求构造失败返回 `EVALUATION_ERROR`；播放服务拒绝时原样返回其 `PlaybackFailure`。
- 不注册网络包、不做传输、不做跨线程排队；`Local` 载入可能阻塞，宿主应在进入播放宿主线程前完成解析。

[CorePlaybackRequestReceiverTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackRequestReceiverTest.java>) 覆盖 5 个用例：Embedded 同实例、Local 解析、Local 载入拒绝、Stop 委派、播放拒绝与缺少序号。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **65 suite / 585 用例**（v2 59 / 519），失败/错误 0、跳过 1（Windows 符号链接权限）。C10 新增 5 个用例、1 个 suite；v2 主源码 295 个 Java 文件，API class 仍为 386；jdeps 对 386 个 API class 的禁止类型依赖为 0，`git diff --check` 通过。

**关闭边界。** 真实网络包注册 / 传输 / 收包线程切换未做；接收端本身不是 `StreamCodec` 处理器，调用方负责先解码并进入播放宿主线程。Local 资源载入是同步调用，未实现异步解析或请求超时；服务端权限、来源授权和客户端资源根仍未验收。R60 独立复核尚未执行。

### 4.8 C11：编辑器 UI 后端选择编排（2026-10-26，未提交）

[CoreEditorUiHost](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreEditorUiHost.java>) 实现主设计 §9.3 的宿主编排：

- `auto`：第三方后端按优先级降序、同优先级按 ID 稳定排序；逐个 `canOpen`，第一个接受者打开；全部拒绝时打开内置；内置不参与自动排序之前。
- 指定已注册且接受：直接打开该后端；指定已注册但拒绝：报告失败，不自动切换、不调用内置。
- 指定未注册：报告警告并打开内置。
- `canOpen` 抛异常视为拒绝；`auto` 继续尝试下一个，指定 ID 报告失败且不回退。
- `open` 抛异常只报告失败，不关闭会话、不遗留令牌由后端自行保证。
- 构造时拒绝重复后端 ID（含内置 ID）；`Reporter` 提供警告 / 失败回调，`noop()` 可用。

[CoreEditorUiHostTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreEditorUiHostTest.java>) 覆盖 8 个用例：auto 优先级/同优先级 ID、全部拒绝回内置、指定接受、指定拒绝不回退、未注册警告回内置、open 失败、canOpen 异常继续、重复 ID。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **66 suite / 593 用例**（v2 60 / 527），失败/错误 0、跳过 1（Windows 符号链接权限）。C11 新增 8 个用例、1 个 suite；v2 主源码 296 个 Java 文件，API class 仍为 386；jdeps 对 386 个 API class 的禁止类型依赖为 0，`git diff --check` 通过。

**关闭边界。** 未实现真实客户端屏幕 / 面板挂载，也没有后端接管令牌或会话关闭；`Reporter` 由宿主实现，默认不写日志。`auto` 中某个后端 `open` 抛异常时当前停止，不再尝试下一个；若产品需要继续回退，应显式修改编排语义。R61 独立复核尚未执行。

### 4.9 C12：NBT 文档编解码与规模限制（2026-10-26，未提交）

[NbtDocumentCodec](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtDocumentCodec.java>) 提供文档与未命名 CompoundTag 字节之间的适配：

- 编码先走 `DocumentCodec.encode(..., NbtOps.INSTANCE)`；结果不是 CompoundTag 或 NBT 深度 / 条目 / 字节超限时返回 `DataResult` 错误。
- 解码先检查字节上限，再用 `NbtAccounter.create(maxBytes)` 读取；随后检查深度 / 条目，最后交给 `DocumentCodec` 执行结构解码与统一文档校验。
- [NbtResourceLimits](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtResourceLimits.java>) 提供 8 MiB / 128 层 / 1,000,000 条目默认值；[NbtTagGuard](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtTagGuard.java>) 在遍历前统计 Compound 键、List 元素与基本数组元素。
- 本类不读写文件、不选择资源根、不做压缩或 SavedData 生命周期；文件 IO / 原子替换仍属资源服务。

[NbtDocumentCodecTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtDocumentCodecTest.java>) 覆盖 5 个用例：NBT 往返 Accepted、解码字节超限、编码字节超限、损坏字节、深度 / 条目守卫。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **67 suite / 598 用例**（v2 61 / 532），失败/错误 0、跳过 1（Windows 符号链接权限）。C12 新增 5 个用例、1 个 suite；v2 主源码 299 个 Java 文件，API class 仍为 386；jdeps 对 386 个 API class 的禁止类型依赖为 0，`git diff --check` 通过。

**关闭边界。** 未实现 NBT 文件资源服务、命名根 / 压缩策略、SavedData 生命周期、原子替换或恢复调度；解码深度 / 条目检查发生在读取之后，读取阶段只由 `NbtAccounter` 限制字节。NBT 结构失败没有 JSON 字段路径，`fieldPath` 为空是如实结果，不是已填充。R62 独立复核尚未执行。

### 4.10 C13：全文档 KeyId 唯一性（2026-10-26，未提交）

[UnifiedDocumentValidator](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidator.java>) 的 L2 键身份检查由“同轨唯一”扩展为**全文档唯一**：

- 统一遍历机位通道曲线、注视 / 活动状态键、布局矩形 X/Y/宽/高的 `AnimatedNumber.Keyed` 曲线、相机来源键，以及实现 `ExtensionTimelineTrack` 的文档级扩展轨道键。
- 命中重复时在第二个位置产生 `DUPLICATE_KEY_ID`，消息包含首次出现的字段路径；同轨重复与跨轨 / 跨对象重复使用同一诊断码。
- 非 `ExtensionTimelineTrack` 的扩展轨道无法派生键身份，仍由注册的类型校验器负责内部约束；本次只覆盖框架可见键。
- 构造期仍只保证单轨唯一；发布 / 载入统一校验负责全局作用域，编辑层按 KeyId 改值不再可能出现多个命中。

[UnifiedDocumentValidatorTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidatorTest.java>) 新增 `rejectsDuplicateKeyIdAcrossTracks` 与 `acceptsDistinctKeyIdsAcrossTracks`；全量 600 用例通过。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **67 suite / 600 用例**（v2 61 / 534），失败/错误 0、跳过 1（Windows 符号链接权限）。C13 新增 2 个用例、suite 数不变；v2 主源码 299 个 Java 文件，API class 仍为 386；jdeps 对 386 个 API class 的禁止类型依赖为 0，`git diff --check` 通过。

**关闭边界。** 真实文档若在多个轨道复用同一 KeyId，现在会被拒绝；这是按主设计 §3.1 的全局作用域收紧，第三方类型校验器若维护自己的键身份表仍需单独检查。跨文档作用域、非时间轴扩展轨道内部键与源文件字段路径不在本次范围。R63 独立复核尚未执行。

### 4.11 C14：NBT 文件资源服务（2026-10-26，未提交）

[NbtAnimationResourceService](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtAnimationResourceService.java>) 实现本地 `.nbt` 资源服务：

- 读取经 [ResourcePathResolver](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/ResourcePathResolver.java>) 做根内 / 符号链接边界检查；字节上限先于读取，`NbtIo` + `NbtAccounter` 读取，`NbtTagGuard` 检查深度 / 条目，再交给结构 `Codec`。**只做结构解码**，统一文档校验仍由 loader / store 执行。
- 保存编码为 NBT，检查字节 / 深度 / 条目后写入同目录临时文件并 `ATOMIC_MOVE` 替换；失败清理临时文件，不留下部分正式文件。
- 生成 SHA-256 / 大小 / 修改时间元数据；`entry` 与 `entries` 只枚举 `.nbt` 普通文件，路径解析失败或不可读项不暴露。
- `saveAsync` 使用公共线程池，完成回调恰好一次；不依赖客户端线程。

[NbtAnimationResourceServiceTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtAnimationResourceServiceTest.java>) 覆盖 4 个用例：保存 / 载入 / 枚举往返、缺失与越界路径、超限保存与损坏 NBT、异步保存回调。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **68 suite / 604 用例**（v2 62 / 538），失败/错误 0、跳过 1（Windows 符号链接权限）。C14 新增 4 个用例、1 个 suite；v2 主源码 300 个 Java 文件，API class 仍为 386；jdeps 对 386 个 API class 的禁止类型依赖为 0，`git diff --check` 通过。

**关闭边界。** 不做 SavedData 生命周期、压缩命名根、恢复槽调度、跨版本兼容矩阵或网络传输；`entries` 只枚举 `.nbt`，不限制目录资源数量；路径校验后文件系统被替换的 TOCTOU 窗口仍存在。载入只保证结构成立，不保证统一校验通过。R64 独立复核尚未执行。

### 4.12 C15：NBT 结构失败字段路径（2026-10-26，未提交）

[NbtStructureFieldPath](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtStructureFieldPath.java>) 为 [NbtDocumentCodec](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtDocumentCodec.java>) 的结构失败补充有证据的 `fieldPath`：

- 检查 `data_version` 是否为合法 `IntTag` 且等于 `CURRENT_DATA_VERSION`；检查 `update_every_frames` / `resample_every_ticks` 的 NBT 类型；
- 对未知类型消息按 `type` 值定位其实际路径；
- 其次按消息中的已知字段名 / 引号字段做唯一键定位；找不到时保留空路径，不猜测。
- `NbtDocumentCodec.decode` 只在结构失败且原路径为空时补路径；校验失败与结构成功路径不变。

[NbtDocumentCodecTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtDocumentCodecTest.java>) 新增 `invalidDataVersionFillsStructureFieldPath`，验证把 `data_version` 改成字符串后得到 `data_version` 路径。

**施工验证。** `.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain`，BUILD SUCCESSFUL；按测试结果 XML 统计 **68 suite / 605 用例**（v2 62 / 539），失败/错误 0、跳过 1（Windows 符号链接权限）。C15 新增 1 个用例、suite 数不变；v2 主源码 301 个 Java 文件，API class 仍为 386；jdeps 对 386 个 API class 的禁止类型依赖为 0，`git diff --check` 通过。

**关闭边界。** 这不是完整 DFU 路径追踪：仍只对已知整数语义字段、未知类型与消息中可唯一匹配的键定位；NBT 资源服务的 `LoadResult.Rejected` 仍只有 File 诊断，没有结构化 fieldPath；JSON 路径逻辑未复用，NBT 与 JSON 的定位证据可能不同。R65 独立复核尚未执行。

## 5. 后续实现与验收清单

<a id="p1-follow-up"></a>

当前没有未交付的已列 P1 契约类型。R56-H1 已由 C7 修复并经 R57 复核；C8 新增相机接管与能力服务 core 部件，待 R58；下表其余条目为实现、接线与运行验证，不因列入清单就视为已知运行缺陷或已经完成。

| 顺序/范围       | 必须验证                                                                                                                                                                                     |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 文档与校验      | 原子候选校验/发布、唯一 ID 与跨对象规则、旧请求拒绝、换档/撤销/重做递增、通知重入与延迟重查、标签/无变化历史；类型钩子按层分派，版本/对象/字段定位正确                                       |
| 表达式/eval     | 编译/arity/作用域/依赖图、step/惰性 select/noise、显式 sample 默认值；TrackRef 所属机位、共享额度/深度、整树资源失败缓存过滤、命中重包装来源；事实/索引/查询同源与候选隔离                   |
| 宿主准备        | 正确生成覆盖后的逐项空间计划、事实原快照传递、采样节奏/表版本/错误编码、resolver/collector 注册与插值、能力三态与受控查询；群系/天气/记分板查询面随后扩展                                    |
| 播放/接管/事件  | start 失败不扰动旧状态、主线程/重入检测、provider 调用与释放边界；事件零次/多周期/同刻顺序、展开预算、执行异常/部分执行、来源区间活动性、旧帧失效                                            |
| R0 renderer     | 原生双路与兼容性、状态隔离/恢复、层序/Player 底图/单监听器；输出像素/负坐标/角落/GUI 缩放、窄可见条/完全出屏/来源保留；分档池化滞回、恢复新帧、可见停路零目标状态、实际画质和性能            |
| 存储与网络      | 资源根/路径安全、临时文件与原子替换/恢复选择；Local/Embedded 载入与统一校验；协议版本、锚点完整参数、覆盖三态、未知类型与超限拒绝；NBT/网络 Ops 往返、深度/数量/字节保护、fieldPath 实际填充 |
| 编辑器与工具    | 草稿原文恢复、过期提交、后端选择/open/失败清理、预览覆盖跨后端保持；状态轨道能力分派、缓动/批量/烘焙/注视捕获、公式保护及原子提交；消息翻译/样式/语言切换                                    |
| 扩展与显示      | 扩展时间轴派生键转编辑意图；事件执行器按原序连续片段分派；通道翻译/单位/颜色/作者覆盖的实际显示与持久化                                                                                      |
| 条件优化/产品项 | A2 有重复查询证据才缓存静态列表/时长，缓存有界且释放；A3 构建快照绑定索引；A6 标记退出时长需另行裁决并同步定位/循环/同步口径                                                                 |

明确后续阶段：叠加 O1–O3 等设计补全字段；编辑器公开 SPI/组件（U1/U2）、采样实时读数/洋葱皮/构图辅助（S1/S2、E1）另行实施。全部非音频阶段完成后才实施独立音频 API、可选接入、音频数据/Codec、波形与同步；现有 AudioRef 不代表音频后端已完成。

C8 已交付 `CoreCameraControlClaims`、`FrozenAnchorResolverRegistry` / `FrozenSampleCollectorRegistry` 与 `CoreTypeCapabilityService`；它们仍是 core 层供宿主装配的部件，不代表编辑器、renderer 或客户端世界查询已接线。

C9 已交付 `CoreLocalizedMessageRenderer` 与 `CoreEditTools` 的缓动、批量变换、烘焙与注视捕获；编辑器 UI、语言切换与真实宿主姿态接线仍未接。

C10 已交付 `CorePlaybackRequestReceiver` 的本地接收入口；真实网络包注册、传输、收包线程切换与授权仍未接。

C11 已交付 `CoreEditorUiHost` 的后端选择编排；实际客户端 UI 挂载、接管令牌与会话生命周期接线仍未接。

C12 已交付 `NbtDocumentCodec` 的 NBT 载荷编解码与规模限制；NBT 文件服务、SavedData 生命周期、压缩与原子替换仍未接。

C13 已把 `KeyId` 唯一性收紧到全文档作用域；非时间轴扩展轨道内部键仍由类型校验器负责。

C14 已交付 `NbtAnimationResourceService` 的本地 `.nbt` 文件资源服务；SavedData 生命周期、压缩、恢复调度与网络传输仍未接。

C15 已为 NBT 结构失败补充部分字段路径；完整 DFU 路径追踪与资源服务结构化路径仍未接。

## 6. 验证基线与维护方式

C5 审核基线实际执行：

```powershell
.\gradlew.bat --offline compileJava test --rerun --console=plain
```

结果 BUILD SUCCESSFUL，33 suite / 219 用例，v2 27 / 153，失败/错误/跳过均为 0。编译任务为 UP-TO-DATE，测试任务重新执行；不把它表述为清空产物后的全量重编译。

2026-10-25 本地实现批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，59 suite / 511 用例，v2 53 / 445，失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。该批次已提交为 `95c3dda`，R55 审核发现九项问题；C6 修复与 R56 复核见 §4.3 与审核文档 §4，不改变 C5 原范围的审核结论。两次检查均确认 384 个 v2 API class 的禁止包引用为 0，`git diff --check` 通过。

2026-10-26 C6 修复批次执行（与上一批相同命令，清空产物后重编译）：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，60 suite / 537 用例，v2 54 / 471，失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。该批次为未提交的本地修复，R56 已复核：八项原反例关闭，依赖环部分关闭，不改变 C5 / R54 原范围的审核结论。施工方 C6 自测时仅以源码扫描确认 v2 api 对 core / 客户端、editor.spi 对 editor.internal 的禁止 import 为 0；R56 随后补做 JDK 25 jdeps 编译产物检查，386 个 v2 API class 的上述禁止类型依赖为 0。

2026-10-26 C7 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **60 suite / 542 用例**（v2 54 / 476），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。新增 5 个用例后 suite 数不变；C7 未修改公开 API，v2 API class 仍为 386。施工方自检命令与证据见 §4.4；R57 已独立复核，结果见审核文档 §5。

2026-10-26 C8 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **62 suite / 566 用例**（v2 56 / 500），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。新增 24 个用例、2 个 suite；v2 主源码 292 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.5；R58 独立复核尚未执行。

2026-10-26 C9 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **64 suite / 580 用例**（v2 58 / 514），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。C9 相对 C8 新增 14 个用例、2 个 suite；v2 主源码 294 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.6；R59 独立复核尚未执行。

2026-10-26 C10 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **65 suite / 585 用例**（v2 59 / 519），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。C10 新增 5 个用例、1 个 suite；v2 主源码 295 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.7；R60 独立复核尚未执行。

2026-10-26 C11 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **66 suite / 593 用例**（v2 60 / 527），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。C11 新增 8 个用例、1 个 suite；v2 主源码 296 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.8；R61 独立复核尚未执行。

2026-10-26 C12 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **67 suite / 598 用例**（v2 61 / 532），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。C12 新增 5 个用例、1 个 suite；v2 主源码 299 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.9；R62 独立复核尚未执行。

2026-10-26 C13 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **67 suite / 600 用例**（v2 61 / 534），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。C13 新增 2 个用例、suite 数不变；v2 主源码 299 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.10；R63 独立复核尚未执行。

2026-10-26 C14 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **68 suite / 604 用例**（v2 62 / 538），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。C14 新增 4 个用例、1 个 suite；v2 主源码 300 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.11；R64 独立复核尚未执行。

2026-10-26 C15 修复批次执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，按测试结果 XML 统计 **68 suite / 605 用例**（v2 62 / 539），失败/错误 0、跳过 1（Windows 无符号链接权限的 JUnit assumption）。C15 新增 1 个用例、suite 数不变；v2 主源码 301 个 Java 文件，v2 API class 仍为 386。施工方自检命令与证据见 §4.12；R65 独立复核尚未执行。

必守门禁：api 不依赖旧根、net.minecraft.client、core/editor 实现；editor.spi 不依赖 editor.internal；按 Q13 允许 edit ↔ document。源码文本扫描只作辅助，依赖门禁检查编译产物。测试目前集中于结构不变量、容器隔离、JSON/压缩 JSON Codec 与替身契约，不代替真实 store/时序/世界/渲染/网络验证。

关键基线保留为里程碑，完整历史命令和原始论证由 Git 追溯：

| 里程碑               | 全工程 suite/用例 | v2 suite/用例 |
|----------------------|-------------------|---------------|
| R7 数据与时间边界    | 14/105            | 8/39          |
| R12 消息/作用域修复  | 18/131            | 12/65         |
| R30 工具契约         | 22/160            | 16/94         |
| R40 Codec 数值修复   | 24/183            | 18/117        |
| R46 表达式契约修复   | 26/196            | 20/130        |
| R48 网络动作契约     | 28/203            | 22/137        |
| C1 宿主计划          | 28/209            | 22/143        |
| C2/C3 扩展与事实来源 | 31/214            | 25/148        |
| C4 剩余契约          | 33/218            | 27/152        |
| C5 / 审核 R54        | 33/219            | 27/153        |
| `95c3dda` / R55（发现九项问题） | 59/511          | 53/445        |
| C6 / R56（八项关闭、环检查部分关闭） | 60/537        | 54/471        |
| C7 / R57（R56-H1 修复，原范围关闭） | 60/542        | 54/476        |
| C8 / 待 R58（相机接管与能力服务） | 62/566        | 56/500        |
| C9 / 待 R59（消息渲染与工具） | 64/580        | 58/514        |
| C10 / 待 R60（播放请求接收） | 65/585        | 59/519        |
| C11 / 待 R61（编辑器 UI 后端编排） | 66/593        | 60/527        |
| C12 / 待 R62（NBT 文档编解码） | 67/598        | 61/532        |
| C13 / 待 R63（全文档 KeyId） | 67/600        | 61/534        |
| C14 / 待 R64（NBT 文件资源服务） | 68/604        | 62/538        |
| C15 / 待 R65（NBT 字段路径） | 68/605        | 62/539        |

后续维护分为两个入口：本文更新契约、实施批次、施工记录与验证基线；[P1 审核与复核记录](<P1-review.md>)更新审核编号、原问题、关闭边界与独立复核结论。行为规则变更同步主设计；审核通过只关闭明确的问题范围，新增需求另行分期。原 R1–R14/R1–R29 归档、协议提案、注册表选型与旧复核日志已并入两份文档；如需原文，可查凝练前提交 `7095284` 的对应路径。
