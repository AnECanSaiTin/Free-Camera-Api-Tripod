# P1 实现总结、契约约定与后续验收

> 更新：2026-10-19。源码复核基线：`7095284`（提交 C5）。本文件为 refactor 目录唯一维护入口，合并原施工计划、协议提案、注册表选型、复核日志及历史归档的有效结论；旧文档全文由 Git 历史追溯。
>
> **当前结论：P1 已列 R1–R12 契约均已交付；C5 两项修复在原问题范围复核通过，未发现确定回归。** 这表示公开类型、接口和部分结构/编解码逻辑已完成，不表示 core、客户端或完整 P1 运行验收完成。最近实际验证为 **33 suite / 219 用例**，其中 v2 **27 / 153**，失败、错误、跳过均为 0。
>
> 行为规范仍以[动画主设计](<../design-animation-editor-framework.md>)、[UI 设计](<../design-editor-ui-framework.md>)和[音频设计](<../design-spatial-audio-api.md>)为准；本文维护实际交付、既有裁决、施工边界与证据，不新增产品裁决。

## 1. 实现范围与迁移边界

当前根包为 `cn.anecansaitin.free_camera_api_tripod_v2`，顶层分为 `api / core / editor`。旧实现原样保留、不接线、不改 Legacy 名；新契约直接使用设计名称。清理旧实现后再整体迁回 `cn.anecansaitin.free_camera_api_tripod`，替换引用并检查编译依赖；搬包不能代替职责分离。

| 范围 | 已交付 | 尚未实现或验收 |
| --- | --- | --- |
| 数据内核 | 稳定 ID / IdGenerator；不可变文档、机位、路径、符号、标记、采样声明；数值与原生状态轨道 | 全文档唯一 ID、跨对象引用及领域规则的统一校验执行 |
| 布局与空间 | AnimatedNumber、可动画矩形、ResolvedRect；空间/锚点/注视/避障及姿态值类型 | 几何算法、锚点解析、逐帧求值与姿态合成 |
| 编辑与文档 | EditOp、EditSubmission、Revision、快照、store/历史/事务/预览契约；诊断、消息树、试算与 EditTools 请求/结果 | store 发布、通知队列、撤销重做、编辑执行、统一 validator、试算与工具算法 |
| 表达式与求值 | 编译器接口、不透明编译产物、引用/诊断；显式求值输入、来源链、共享执行会话；索引/依赖图/缓存接口 | 编译器、解释器、索引构建、依赖图、实际缓存及候选隔离 |
| 开放类型 | 自有 TypeRegistry、轨道/锚点/采样类型描述及 Codec、可选 TypeValidator；客户端 resolver/collector 与能力服务契约 | 注册表实现/冻结时序、类型校验分派、客户端执行部件及受控世界查询 |
| 编解码 | 成员 Codec、DocumentRegistries、根文档组合、严格整数、两阶段 DocumentCodec、结构失败字段路径承载 | Codec 实际填充 fieldPath、NBT/网络 Ops 往返、深度/数量/字节限制及兼容矩阵 |
| 播放与渲染 | 请求/覆盖/时钟/准备帧、事件预算/执行器注册、相机接管、合成与质量状态；网络动作/来源/限额及编解码适配接口 | 时间推进、事件展开与执行、仲裁、真实网络协议、来源解析、多机位 renderer 与质量策略 |
| 存储 | ResourceId、资源服务、载入/保存结果、文件诊断报告、恢复槽与选择契约 | 资源根/路径安全、IO、原子替换、恢复调度 |
| 编辑器 | 会话、草稿、选择/视图、按 RigId 预览覆盖、交互请求、后端选择与 open 契约 | 会话实现、后端装配、草稿恢复、UI/输入/生命周期及预览执行 |
| 叠加与音频 | OverlayElement 暂存不透明内容；AudioRef / AudioRefId 仅身份与边界 | 叠加封闭内容等 O1–O3 补全设计后实施；音频整体最后实施 |

源码入口：[v2 API](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/>)；回归入口：[v2 测试](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/>)。当前 v2 主源码为 220 个 Java 文件。

## 2. 已确认裁决

保留 Q 编号用于解释历史引用；历史阶段安排不再充当当前待办。

| 编号 | 有效结论 |
| --- | --- |
| Q1 | 新旧同名类型先在 `_v2` 隔离，旧代码不接线，最后迁回原根包。 |
| Q2 | 首期不实施音频数据与服务；AudioRef / AudioRefId 只保留身份和边界。 |
| Q3 | 符号表进入首期：block.Symbols、field.Variable / CustomFunction。 |
| Q4 | AnimationTrack 保持 sealed，ExtensionTrack 为 non-sealed；扩展不得使用 curve / aim / active / camera_source 内建类型 ID，由 L2 拒绝。 |
| Q5 | AimOverride 位于 playback，分 Inherit / Disabled / Replace；宿主应用覆盖，求值层接收生效 AimState。 |
| Q6 | TrackLookup 位于 eval，文档根不提供 trackLookup；expression 只负责编译、引用及轻量诊断。 |
| Q7 | 采样参数使用名义接口 SampleArgs，并要求 kind 与 args.type 一致；叠加内容等 O1–O3 定型，不采用泛型替代方案。 |
| Q8 | sealed KeyframeTrack<T> + AimTrack / ActiveTrack / CameraSourceTrack；状态键共用 ValueKeyframe<T> 与 KeyId，保留原生值类型。 |
| Q9 | 采样三态 VALUE / UNKNOWN / COLLECTION_ERROR；只有 UNKNOWN 可以被作者显式默认值兜底。 |
| Q10 | sampledAt 使用 long 游戏刻，与重采周期及 FrameFacts 同口径。 |
| Q11 / Q12 | 仅记录早期布局延期与第二批历史范围；不限制后续批次，布局安排由 Q14 更新。 |
| Q13 | edit 位于 api.animation.edit；允许 edit ↔ document 双向引用，仍禁止 api 依赖 core、editor 实现和客户端类。 |
| Q14 | 布局 AnimatedNumber 已落地，RigView.Rect 四分量可动画，准备帧携带 ResolvedRect。 |
| Q15 | 后续十一项协议均接受原推荐方向，已同步设计；具体运行实现继续分批验证。 |

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

## 4. 交付与审查摘要

审核与提交不是同一编号空间：早期 R7–R48 混合保留历史编号；后续提交用 C，审核用 R。本轮审核记为 **R54**；后续审核从 R55、提交从 C6 继续。R1–R12 也曾用于“剩余契约”清单，与审核编号无对应关系。

| 历史范围 | 最终交付或修复 | 当前关闭边界 |
| --- | --- | --- |
| R1–R7 / A0–A7 | 时间有限性、乱序/冲突、duration 内部遍历、withRig 无变化返回自身、命名与标记说明 | A0/A1/A4/A5 已落地；A7 注释/时长测试完成，事件验证待后续；A2 条件缓存、A3 索引实现、A6 产品裁决保留 |
| R8–R12 | document/edit/eval 契约；同 store 单调 Revision、sampleTime 缓存身份、不可变消息树、显式诊断作用域 | 原四项契约关闭，不恢复 Component.copy 或可空版本旧方案；发布/缓存运行另验 |
| R13–R22 | playback、Q15 十一项协议、storage；provider 姿态入口、质量冻结、事件快照/顺序、采样身份、索引同源、准备预算与线程约束、单资源报告 | 原问题契约关闭；实际仲裁、事件、队列、IO和试算未验收 |
| R23–R30 | editor、草稿更新/后端 open、EditTools 冻结快照与注视捕获输入 | E1/E2、T1/T2 原入口问题关闭；会话与工具算法未验收 |
| R31–R40 | 布局动画、注册表选型、成员/根 Codec与两阶段解码；叠加双向拒绝、精确整数、扩展泛型边界及压缩 JSON 往返修复 | 原编解码反例关闭；NBT/网络、路径填充、规模保护未验收 |
| R41–R46 | 表达式编译/求值接口、共享额度/深度、失败来源、字段/KeyId 定位及资源失败缓存规则 | 原接口缺口关闭；R1 表达式运行实现仍未完成 |
| R47–R49、C1 | 帧事实/provider、resolver/collector 与能力服务；补动画时间/逐项空间计划和解析诊断作用域 | R49-H1/H2 在 R51 关闭；真实客户端准备未验收 |
| C2 / 审核 R52 | 扩展时间轴、事件执行器注册、通道描述；C4 统一原序片段说明 | 契约无确定阻断，显示/编辑/执行待实现 |
| C3 / 审核 R53 | FrameFacts 持有原快照引用，拒绝同 revision 异源绑定 | R51-N1 原反例关闭；provider 实际传递待验证 |
| C4 / 本轮 R54 | 类型校验钩子、结构路径承载、按 RigId 预览覆盖、可见像素质量状态 | 已列 R9–R12 契约均交付；质量停路残留由 C5 关闭，其余运行分派/路径/预览/renderer 延期 |
| C5 / 本轮 R54 | 允许可见停路机位 0×0 目标/0 密度；编码成功改非空 Unit | 两项原问题复核关闭，未发现确定回归；不代表真实渲染与网络完成 |

历史被撤回的论证不作施工依据：不据 Stream 写法或孤立替身微基准判断真实热点、承诺固定倍数/未来帧时间；不把 v1 调用当作 v2 已接线；数值与状态键保留具体类型但可复用公共设施；标记参与时长符合当前设计，不作为缺陷删除。

### 4.1 R54：C5 修复复核（2026-10-19）

| 原问题 | 修复与证据 | 结论 |
| --- | --- | --- |
| 可见副路停路、释放目标后不能同时表达质量几何和 0×0 实际尺寸 | [RigQualityStatus](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigQualityStatus.java>)允许零目标/零密度，拒绝单轴为零及目标/密度矛盾；[RigRenderStatus](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigRenderStatus.java>)保留呈现时正目标、可见性与尺寸一致约束。[PlaybackContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackContractTest.java>)覆盖可见 RIG_PAUSED、零目标且 offscreen=false | 原契约反例关闭；释放目标后的几何查询成立，运行 renderer 未验证 |
| 审核 R50：DataResult<Void> 成功值只能为 null，标准 result() 不安全 | [PlaybackProtocolCodec](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackProtocolCodec.java>)改用 net.minecraft.util.Unit，适配器通过 result() 访问；[PlaybackProtocolContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackProtocolContractTest.java>)使用 Unit.INSTANCE 验证标准访问 | 原成功值模型关闭；正式新增用例未执行真实缓冲区或适配器端到端编码 |

未将下列候选升级为缺陷：统一 validator 可由每请求捕获 Revision 的闭包装配；成员 Curve Codec 的构造异常由正式 DocumentCodec 解码边界捕获。上述均不用于扩大两项原问题范围，也不声称任意成员 Codec 独立调用都不会抛异常。

## 5. 后续实现与验收清单

<a id="p1-follow-up"></a>

当前没有未交付的已列 P1 契约类型；以下为实现、接线与运行验证，不能写成已知运行缺陷或已经完成。

| 顺序/范围 | 必须验证 |
| --- | --- |
| 文档与校验 | 原子候选校验/发布、唯一 ID 与跨对象规则、旧请求拒绝、换档/撤销/重做递增、通知重入与延迟重查、标签/无变化历史；类型钩子按层分派，版本/对象/字段定位正确 |
| 表达式/eval | 编译/arity/作用域/依赖图、step/惰性 select/noise、显式 sample 默认值；TrackRef 所属机位、共享额度/深度、整树资源失败缓存过滤、命中重包装来源；事实/索引/查询同源与候选隔离 |
| 宿主准备 | 正确生成覆盖后的逐项空间计划、事实原快照传递、采样节奏/表版本/错误编码、resolver/collector 注册与插值、能力三态与受控查询；群系/天气/记分板查询面随后扩展 |
| 播放/接管/事件 | start 失败不扰动旧状态、主线程/重入检测、provider 调用与释放边界；事件零次/多周期/同刻顺序、展开预算、执行异常/部分执行、来源区间活动性、旧帧失效 |
| R0 renderer | 原生双路与兼容性、状态隔离/恢复、层序/Player 底图/单监听器；输出像素/负坐标/角落/GUI 缩放、窄可见条/完全出屏/来源保留；分档池化滞回、恢复新帧、可见停路零目标状态、实际画质和性能 |
| 存储与网络 | 资源根/路径安全、临时文件与原子替换/恢复选择；Local/Embedded 载入与统一校验；协议版本、锚点完整参数、覆盖三态、未知类型与超限拒绝；NBT/网络 Ops 往返、深度/数量/字节保护、fieldPath 实际填充 |
| 编辑器与工具 | 草稿原文恢复、过期提交、后端选择/open/失败清理、预览覆盖跨后端保持；状态轨道能力分派、缓动/批量/烘焙/注视捕获、公式保护及原子提交；消息翻译/样式/语言切换 |
| 扩展与显示 | 扩展时间轴派生键转编辑意图；事件执行器按原序连续片段分派；通道翻译/单位/颜色/作者覆盖的实际显示与持久化 |
| 条件优化/产品项 | A2 有重复查询证据才缓存静态列表/时长，缓存有界且释放；A3 构建快照绑定索引；A6 标记退出时长需另行裁决并同步定位/循环/同步口径 |

明确后续阶段：叠加 O1–O3 等设计补全字段；编辑器公开 SPI/组件（U1/U2）、采样实时读数/洋葱皮/构图辅助（S1/S2、E1）另行实施。全部非音频阶段完成后才实施独立音频 API、可选接入、音频数据/Codec、波形与同步；现有 AudioRef 不代表音频后端已完成。

## 6. 验证基线与维护方式

本轮实际执行：

```powershell
.\gradlew.bat --offline compileJava test --rerun --console=plain
```

结果 BUILD SUCCESSFUL，33 suite / 219 用例，v2 27 / 153，失败/错误/跳过均为 0。编译任务为 UP-TO-DATE，测试任务重新执行；不把它表述为清空产物后的全量重编译。检查 384 个 v2 API class 的禁止包引用为 0，git diff --check 通过。

必守门禁：api 不依赖旧根、net.minecraft.client、core/editor 实现；editor.spi 不依赖 editor.internal；按 Q13 允许 edit ↔ document。源码文本扫描只作辅助，依赖门禁检查编译产物。测试目前集中于结构不变量、容器隔离、JSON/压缩 JSON Codec 与替身契约，不代替真实 store/时序/世界/渲染/网络验证。

关键基线保留为里程碑，完整历史命令和原始论证由 Git 追溯：

| 里程碑 | 全工程 suite/用例 | v2 suite/用例 |
| --- | --- | --- |
| R7 数据与时间边界 | 14/105 | 8/39 |
| R12 消息/作用域修复 | 18/131 | 12/65 |
| R30 工具契约 | 22/160 | 16/94 |
| R40 Codec 数值修复 | 24/183 | 18/117 |
| R46 表达式契约修复 | 26/196 | 20/130 |
| R48 网络动作契约 | 28/203 | 22/137 |
| C1 宿主计划 | 28/209 | 22/143 |
| C2/C3 扩展与事实来源 | 31/214 | 25/148 |
| C4 剩余契约 | 33/218 | 27/152 |
| C5 / 审核 R54 | 33/219 | 27/153 |

后续只更新本文的实际状态、裁决变化、审核结论与验证证据，避免恢复多份重复待办；行为规则变更同步主设计。审核通过只关闭明确的问题范围，新增需求另行分期。原 R1–R14/R1–R29 归档、协议提案、注册表选型与旧复核日志已并入本文件；如需原文，可查凝练前提交 `7095284` 的对应路径。
