# 契约复核日志（当前摘要）

> 更新：2026-10-19。**提交 C5** 修正 R12 停路可见机位的零实际尺寸质量状态，并将 R50 的编码成功值改为非空 `DataResult<Unit>`；正式测试 33 suite / 219 用例通过。**提交 C4** 已交付 R9–R12；未审核的**提交 C2 / C3**已分别完成**审核 R52 / R53**。**审核 R50** 的 DataResult<Void> 问题已由 C5 修复，待复核；具体编解码、宿主与客户端端到端尚未验收。施工见[P1 计划](<P1-contract-plan.md>)，规范见[主设计](<../design-animation-editor-framework.md>)。
> 本文按“审核 / 提交”两章独立编号：**审核**继续 R 序列（最新 R53，下一条 R54），**提交**历史保留拆分前的 R 编号（R7–R48），新提交改用 **C 序列（自 C1 起）**。两章编号互不相关，跨章引用须注明“审核”或“提交”；历史编号不重排，归档不作为当前指令。

## 1. 当前结论

| 范围 | 状态及边界 |
| --- | --- |
| 模型/提交/求值/播放 | 契约已交付；C1–C4、playback五项、S1–S3 均按原范围关闭；core算法与IO另验 |
| 网络播放请求（R5） | 提交 R48 已交付接口；审核 R50 发现编码 DataResult<Void> 的 null 成功值问题（P2），待修；具体编解码实现、收包校验与资源解析仍延期 |
| 布局连续属性（Q14） | R31 交付契约：`AnimatedNumber` 两分支、矩形四分量可动画、`ResolvedRect` 冻结已求值矩形、布局曲线计入时长；求值与逐帧检查未实现 |
| 类型注册表 | R31 交付契约：ID 分派 + 结构 Codec + 冻结语义；R32 裁决维持自有实现（O） |
| 宿主服务与能力（R2–R4） | R47 交付接口；审核 R49 的两项 P2 已由**提交 C1** 补齐（帧准备计划/动画时间、解析诊断作用域、事实-快照版本绑定）；provider/resolver/collector 与客户端世界查询仍无运行实现 |
| 编解码与两阶段解码 | R40 复核关闭 R39 的 Float/Double 类型分支修复，原 R38-N1 已关闭，未发现新增确定缺陷；叠加实现、NBT/网络往返、字段级诊断仍延期 |
| 注册表选型 | R32 调研完成并已裁决：维持 O + 能力 B；`N′` 保留为将来演进选项，[选型对比](<registry-selection.md>) |
| 表达式契约（R1） | R46 复核关闭 R45 对 R44-N1/N2 的契约修复，未发现新增确定缺陷；整树缓存过滤、命中重包装及编译器/解释器端到端仍待实现验收，R1 未关闭 |
| editor | R24两项公开通路已由R25/R26关闭，后端open前置条件文案已统一；真实草稿恢复、派发与生命周期延期 |
| EditTools | R29已补快照及捕获信息，R30复核关闭；实际采样/转换/确认/原子提交未实现 |
| 扩展能力（R6–R8） | 提交 C2 已经审核 R52，契约范围无确定阻断；保留原序分派澄清，时间轴编辑/执行器分派/显示派生未实现 |
| 事实来源绑定（R51-N1） | 提交 C3 已由审核 R53 在原范围关闭；原快照引用绑定与同版本异源拒绝成立，provider 实际传递待 core |
| 剩余契约（R9–R12） | 提交 C4 交付类型级校验钩子、结构字段路径、预览覆盖与可见像素质量；C5 修正停路零目标状态；实现/运行验证待 core/editor |
| 网络编码成功值（R50） | 提交 C5 将 `encode` 改为 `DataResult<Unit>`；适配器走标准 `result()` 访问，待复核 |
| 性能与标记 | 不据Stream写法判断热点；A1/A4等已清理，A2条件优化，A3索引实现待后续；标记仍贡献时长，A6改规则需裁决 |
| 新质量需求 | 用户确认按最终可见像素需求自适应分辨率；保持原构图/FOV，不缩小视锥，状态/策略契约与renderer尚未实现 |
| P1 剩余契约 | R1–R12 契约均已交付；2026-10-19 对账见[计划 §6.1](<P1-contract-plan.md>)。剩余工作为实现/运行验证，不再新增 P1 契约类型 |

当前待办仅在[计划§6](<P1-contract-plan.md#p1-follow-up>)维护。

## 2. 审核

> **范围**：只读轮次——契约复核、协议核对、选型调研与裁决；结论可以是修订或关闭，但本轮不改源码。
> **编号**：审核使用 R 序列（下一条 R54）；R1–R49 是拆分前的共用编号，拆分后只用于审核，提交新记录用 C 序列（§3）。跨章引用须注明“审核”或“提交”。
> R1–R6 是早期设计与口径判定，代码落地从提交 R7 起；原 R1–R29 正文及证据见[凝练前归档](<archive/review-log-R1-R29-before-2026-10-18.md>)，早期 R1–R14 的初次归档亦保留，历史不作当前指令。

| 编号 | 日期 | 审核对象 | 结论 / 关闭状态 |
| --- | --- | --- | --- |
| R1 | 10-17 | 数值键与状态键的分工 | 分工保留；“不可合并”“无需修改”由 R4 修订 |
| R2 | 10-17 | Stream 写法与渲染期性能 | 当前未接线；绝对收益/未来零影响由 R5 撤回，不以旧测量作门槛 |
| R3 | 10-17 | marker 是否应参与时长 | “必须退出/必然冻屏”论证由 R6 修订，未改变设计 |
| R4 | 10-17 | 时间有限性与键类型分工修订 | NaN 回归已修、边界测试在 R7 补齐 |
| R5 | 10-17 | 索引、缓存与无变化派生修订 | A1/A4 落地；A2 条件实施，A3 仅契约，不能保证 Optional 零分配 |
| R6 | 10-17 | 标记口径与裁决边界修订 | 默认仍贡献时长，不触发事件不等于不参与派生时长 |
| R12 | 10-18 | C1–C4 契约复核 | 四项契约问题关闭，运行实现仍另验 |
| R15 | 10-18 | playback 五项修复复核 | 五项原契约问题关闭，未发现新增阻断；时间推进、事件执行与合成仍另验 |
| R18 | 10-18 | 十一项协议落实核对 | 契约方向成立；bind/预算/线程/解码等部分项待补 |
| R20 | 10-18 | storage 与 R18 补齐复核 | 2 项 P2 残留及 1 项 P2 契约澄清；表版本入口和准备预算参数已补齐 |
| R22 | 10-18 | R21 修复复核 | S1–S3 在当前契约范围关闭；无新增确定缺陷，运行实现仍另验 |
| R24 | 10-18 | R23 editor 契约复核 | 两项入口缺口已由 R25 修复、R26 关闭 |
| R26 | 10-18 | R25 修复复核 | E1/E2 原入口缺口在契约范围关闭；保留后端打开前置条件的非阻断澄清 |
| R28 | 10-18 | R27 工具契约复核 | T1/T2 已由 R29 修复，R30 契约复核关闭 |
| R30 | 10-18 | R29 复核与尺寸自适应新需求 | T1/T2 契约关闭；新需求仅设计批准，未落代码 |
| R32 | 10-19 | 注册表选型调研（无源码改动） | 复用 NeoForge 注册表 N/N′ 与自有 O 对比；推荐 O + 能力 B，已裁决 |
| R34 | 10-19 | R31–R33 代码复核 | 3 项已复现未修复：叠加静默丢失 P1、整数截断/溢出 P2、扩展轨道类型边界 P2；R31 布局与 R32 选型未发现新增确定缺陷 |
| R36 | 10-19 | R35 修复复核 | C1/C3 与严重级不变量在原范围关闭；C2 部分关闭：高精度小数残留 P2；新增压缩 JSON 往返回归 P2；正式 24/178，隔离探针 5 例 |
| R38 | 10-19 | R37 修复复核 | R36 两项原反例关闭；新增 Float 类型整数数值改变 P2；正式 24/181，另有 4 个隔离探针 |
| R40 | 10-19 | R39 修复复核 | R38-N1 在原范围关闭，未发现新增确定缺陷；正式 24/183，无新增探针 |
| R42 | 10-19 | R41 表达式契约复核 | 两项 P2：共享预算/深度传递、嵌套诊断源码归属；正式 25/189，R1 未关闭 |
| R44 | 10-19 | R43 契约复核 | 共享额度/深度入口关闭；源码来源绑定/字段粒度残留及预算失败缓存冲突两项 P2；正式 26/194 |
| R46 | 10-19 | R45 契约修复复核 | R44-N1/N2 在原契约范围关闭，未发现新增确定缺陷；正式 26/196，R1 未关闭 |
| R49 | 10-19 | R47 宿主服务契约复核 | 两项 P2：有效空间计划/逐项需求、解析失败诊断作用域；实测 28/203 含 R48，未审核 R48 |
| R50 | 10-19 | 提交 R48：网络播放请求契约 | 1 项 P2：编码 DataResult<Void> 的 null 成功值无法安全使用标准 result()；正式 28/203，隔离探针 3 例 |
| R51 | 10-19 | 提交 C1：R49-H1/H2 修复 | 原输入和诊断入口关闭；事实同版本异源残留已由提交 C3 修复，审核 R53 关闭；当时正式 28/209 |
| R52 | 10-19 | 提交 C2：R6/R7/R8 扩展能力 | 当前契约范围未发现确定阻断；分派原序需非阻断文案澄清；正式 31/214 |
| R53 | 10-19 | 提交 C3：事实来源绑定修复 | R51-N1 在同版本异源快照范围关闭，无确定回归；正式 31/214 |

### R50. 提交 R48 网络播放请求契约审核

**日期**：2026-10-19。本条属于**审核**，对象为**提交 R48**。后续审核与提交分别递增，本文不新增提交记录，也不将审核 R50 视为提交 R50。范围为 PlaybackSource / PlaybackAction / PlaybackProtocol / PlaybackLimits / PlaybackProtocolCodec 与正式结构测试。

#### R50.1 发现

**R50-N1（P2）：编码结果使用 DataResult<Void>，成功分支只能携带 null，无法安全使用 DFU 标准结果访问。**

[PlaybackProtocolCodec.java:41](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackProtocolCodec.java#L41>)声明 `DataResult<Void> encode(...)`，并作为公开的可报告结果入口。Java 的 Void 没有正常可构造的非空实例；实现只能使用 `DataResult.success(null)` 表达成功。在当前 DFU 中，这个对象可以创建且 `isError()` 为 false，但调用标准 `result()` 会因 Optional.of(null) 抛 NullPointerException。

隔离探针确认 `DataResult.<Void>success(null).result()` 抛空指针。[streamCodec 编码适配:73](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackProtocolCodec.java#L73>)仅调用 isError()，因此该适配路径暂不触发异常；不能扩大表述为“所有编码调用都会失败”。问题是公开 DataResult 入口没有一个可按标准访问方式消费的成功值，本批正式测试又没有构造成功编码结果来覆盖它。

**建议**：使用非空 Unit（例如 DFU Unit.INSTANCE）、成功回执或封闭 EncodeResult。保留失败为 DataResult.error 的语义，并增加编码成功时的标准结果访问及 StreamCodec 成功/失败分流回归，不以 null 作为成功哨兵。

#### R50.2 其余结论与延期边界

- Start 的有限正速度、有限非负开始时间、覆盖容器复制与来源分支符合本批契约；Embedded 的同实例约束、Local 的接收端资源解析分工成立。未知机位与可播放时间范围继续由本地请求/服务校验，不因未在 Start 中重复检查另报缺陷。
- PlaybackLimits 的正字节预算、非负覆盖数与 total >= document 约束成立；不把尚未实现的逐字节上限检查或深层对象上限误报为已有运行缺陷。
- 隔离适配器测试确认：DataResult.error 即使携带 partial Stop，streamCodec.decode 仍拒绝并保留错误消息；正常完整 Stop 解码成功，编码错误转 EncoderException。未发现部分值被误接受的问题。
- 适配器探针用不读取缓冲区的最小实现验证分流，不是实际协议 Codec、版本分派、字节往返或网络收包验证。具体编解码、锚点参数、资源解析和文档校验链仍按提交 R48 的边界延期。

#### R50.3 实际验证

- 执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL；按 UTF-8 核对：**28 suite / 203 用例**，v2 为 **22 / 137**，失败、错误、跳过均为 0。首次统计请求因工具服务 524 超时未执行，重试后核对成功；这不是测试失败。
- 在构建目录创建[隔离探针](<../../build/review-submit-r48/probe-src/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/SubmissionR48ProbeTest.java>)及临时 init 配置，执行 `./gradlew.bat --offline --no-configuration-cache -I build/review-submit-r48/probe.init.gradle test --tests '*SubmissionR48ProbeTest' --console=plain`，BUILD SUCCESSFUL，**3 个探针通过**。Void 探针断言缺陷现状，不能视为修复通过，不计入正式 203 用例。
- 未修改项目源码、正式测试或构建脚本；本轮仅修改评审日志并产生临时构建验证文件，未启动客户端或执行真实网络往返。

### R51. 提交 C1 宿主准备契约修复审核

**日期**：2026-10-19。属于**审核**，对象为**提交 C1**（代码基线 `4dead79`）。核对 FrameFactsPlan / FrameFactsRequest / FrameFactsProvider、FrameFacts / EvaluationContext、AnchorResolutionContext / AnchorResolver / SpatialResolution 及正式测试。未审核并行新增的提交 C2，不将其交付视为本轮通过。

#### R51.1 原问题关闭范围

| 原问题 | 证据 | 结论 |
| --- | --- | --- |
| 审核 R49-H1：提供者拿不到生效覆盖及逐项空间需求 | [FrameFactsPlan](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFactsPlan.java#L69>)逐机位携带有效 anchor/target 的 Required/NotRequired 与 AimState；所需目标必须等于生效目标。[FrameFactsRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFactsRequest.java#L38>)有 animationTime，并拒绝快照中不存在的机位 | 原请求输入表达能力缺口关闭；只读锚点不强制解析目标，同游戏刻不同动画时间可区分；计划生成及 provider 执行仍待宿主 |
| 审核 R49-H2：解析器没有诊断版本入口 | [AnchorResolutionContext:47–51](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/AnchorResolutionContext.java#L47-L51>)提供本次 Document 作用域，[SpatialResolution.failed](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/SpatialResolution.java#L58>)固定 ERROR、接受可选定位；Provider/Resolver 明确不得读当前 store 或伪造 File | 原版本来源入口缺口关闭；同一 resolver 的正式替身测试验证版本7/8随上下文变化，实际客户端接线未验收 |

#### R51.2 发现

**R51-N1（P2）：新增事实绑定只比较 sourceRevision，仍允许同版本但异源快照的事实混入上下文。**

[EvaluationContext:50–55](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationContext.java#L50-L55>)只检查 `facts.sourceRevision().equals(snapshot.revision())`；[FrameFacts:24–26](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFacts.java#L24-L26>)将其描述为来源绑定，但结果只保存 Revision，没有原始快照或请求身份。不同 store 的相同版本数值不能证明同源。

触发：两个合法快照 A、B 均为 Revision(1)，provider 正确根据 request(A) 产生 factsA；创建 B 上下文时传入 factsA、lookupB、indexB。索引引用检查 `indexB.snapshot() == B` 成立，事实版本检查也成立，B 接受了 A 的空间计划/目标或采样读数。不要求 provider 违反其准备契约。不同动画时间/计划的同版本事实混用是同一绑定问题的延伸，不另报第二项。

建议事实保留原来源快照引用或不可混淆的准备绑定身份，并与上下文绑定做一致性检查；sourceRevision 可保留作诊断，不能替代来源身份。补“同版本不同快照/不同准备绑定的事实被拒绝”和正确来源成功测试。当前[EvalContractTest:191–194](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvalContractTest.java#L191-L194>)只覆盖版本不一致，未覆盖此同版本反例。本项为新加来源检查的契约残留，不是要求提前完成延期 core。

#### R51.3 实际验证与边界

- 实际执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL；按 UTF-8 报告统计：**28 suite / 209 用例**，v2 为 **22 / 143**，失败、错误、跳过均为 0。未新增隔离探针；R51-N1 依据 record 字段与构造检查分支直接核对，不声称已有正式用例覆盖。
- [HostServiceContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/HostServiceContractTest.java>)覆盖逐项需求、容器隔离、时间/未知机位拒绝、同刻9/10秒、覆盖结果承载及上下文作用域失败工厂。它不证明真实覆盖映射、世界采样节奏或 provider 已运行。
- 不将无默认局部锚点的启动校验、注册客户端实现、真实世界加载/插值或能力查询实现延期报作本轮缺陷；没有修改源码、正式测试或构建配置，只记录审核。
- 正式测试统计在提交 C2 的并行新增被观察到之前完成；C2 的条目及其 31/214 交付数字保留为提交者记录，不混入本次 C1 的 28/209 复核基线。审核 R50 对提交 R48 的问题仍维持原状态，不在本轮重审。

### R52. 提交 C2 扩展能力契约审核

**日期**：2026-10-19。属于**审核**，对象为**提交 C2**（代码 `738d592`）；核对 ExtensionTimelineTrack、EventExecutor / Registry、ChannelDescription / Registry 及三个正式测试。范围仅派生视图、注册形状与显示默认描述，不扩展到延期的 core 执行/编辑实现。

**结论**：在当前交付范围未发现确定阻断问题；本条不关闭 R6–R8 的运行实现验收。

- [ExtensionTimelineTrack](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/ExtensionTimelineTrack.java>)明确稳定 KeyId、严格递增/唯一/不可变派生列表，TimelineKey 拒绝空身份及非有限/负时间；不发放原地修改。第三方列表整体遵约与把编辑意图转换成 EditOp 仍由能力/校验实现保证。
- [EventExecutorRegistry](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/EventExecutorRegistry.java>)复制外层集合，拒绝重复轨道类型、空执行器及内建保留类型；查询按类型 ID。执行器是运行行为对象，不把其闭包可变性误报成文档深不可变缺陷。
- [ChannelDescriptionRegistry](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/ChannelDescriptionRegistry.java>)要求全部 CameraChannel 有且键/描述一致；标签非空、颜色 RGB 范围与容器复制成立。defaultInfo 是显示单位/说明，其中 degrees 不能等同于内部旋转值改用度，实际输入输出转换仍需 UI 适配。

**非阻断澄清 R52-C1**：[EventExecutor:9/18](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/EventExecutor.java#L9>)的“同类型子集/按时间顺序”必须服从 PreparedFrame / EventDispatcher 的原发生序。建议明确只分派原序连续同类型片段或单事件，可多次调用，禁止跨类型或周期聚合重排。现接口不强制整帧同类型聚合，故不能据此认定必然乱序；仲裁/分派实现未交付，不报告运行缺陷。

本轮实际执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL：**31 suite / 214 用例**，v2 **25 / 148**，失败、错误、跳过均为 0。核对 ExtensionTimelineContractTest、EventExecutorRegistryContractTest、ChannelDescriptionRegistryContractTest，覆盖结构与注册拒绝，未证明跨类型/跨周期分派时序、真实显示本地化或扩展轨道编辑链路。`git diff --check` 通过；只修改审核日志。

### R53. 提交 C3 事实来源绑定修复审核

**日期**：2026-10-19。属于**审核**，对象为**提交 C3**（代码 `18638e9`），仅复核审核 R51-N1 的同 revision 异源事实反例。

**结论**：R51-N1 在原问题范围关闭，未发现本提交确定回归。与提交 C2 共用本轮实际 31/214（v2 25/148）正式测试基线，不将共享基线重复计数为两次执行。

| 核对点 | 证据 |
| --- | --- |
| 事实保留来源 | [FrameFacts:40](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFacts.java#L40>)保存非空 sourceSnapshot；sourceRevision() 仅从原快照派生，不能作同源证明 |
| 上下文校验 | [EvaluationContext:50–55](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationContext.java#L50-L55>)以 facts.sourceSnapshot() != snapshot 拒绝；公开构造器与 bind 共用检查，索引原引用检查也保留 |
| 正式反例覆盖 | [EvalContractTest:195–208](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvalContractTest.java#L195-L208>)正确来源成功；在 index 仍绑定原快照的前提下，传同revision/同document新快照来源facts被拒绝，能独立命中此次事实检查；异版本拒绝亦保留 |
| 提供者前置条件 | FrameFactsProvider / FrameFactsRequest 明确结果必须原样持有请求快照引用；尚无真实 provider，未验证客户端准备接线 |

同一快照下不同动画时间/空间计划的请求选择仍属宿主准备绑定责任；当前构造器未承诺对请求时间/计划逐项验真，不将其扩大为新硬缺陷。运行期快照引用生命周期和释放时机需 core 验证；本轮不宣称网络/世界查询或完整求值完成。审核 R50 对提交 R48 的既有问题仍待修，不因 C2/C3 通过而关闭。

## 3. 提交

> **范围**：改动轮次——实施、修复与契约交付（改到源码、契约或测试）。交付契约不等于算法、运行链路或客户端效果已验收。
> **编号**：R7–R48 是拆分前的共用编号，保留原样；后续提交从 **C1** 起独立递增，与审核 R 编号不再对应。C1 序列与 R10–R12 的 C1–C4 契约问题是两套命名，没有对应关系。
> 每次提交是否通过审核，见“交付状态 / 复核关系”列指向的审核编号；新增审核不占用也不推进提交编号。

| 编号 | 日期 | 提交内容 | 交付状态 / 复核关系 |
| --- | --- | --- | --- |
| R7 | 10-18 | A0/A1/A4/A5/A7 实施 | 时间边界、遍历、withRig、命名及标记注释/时长测试落地；运行事件副作用仍延期 |
| R8 | 10-18 | document/edit 契约批次 | 只交付契约，不代表 store/执行器完成；Q13 接受包循环 |
| R9 | 10-18 | eval 契约批次 | 索引/事实/上下文/采样结果/姿态/图/缓存接口，算法与绑定延期 |
| R10 | 10-18 | C1–C4 第一轮实施 | C1/C2 保留；Component.copy 和可空版本方案不足，由 R11 替代 |
| R11 | 10-18 | C3/C4 深层修复 | 改不可变消息树及显式作用域；R12 复核关闭 |
| R13 | 10-18 | playback 契约批次 | 后续发现五项契约缺口，R14 实施修正 |
| R14 | 10-18 | playback 五项修复 | 五项修正完成，R15 复核关闭 |
| R16 | 10-18 | 后续协议 1–5 实施 | 提交标签/重入、编辑目标、试算报告、显式采样、世界身份落地 |
| R17 | 10-18 | 后续协议 6–11 实施 | 空间三态、启动/接管协议、旧帧身份、ResourceId、消息渲染契约落地 |
| R19 | 10-18 | storage 契约与 R18 契约补齐 | 资源/恢复契约及绑定/预算调整；不等同复核通过，见 R20 |
| R21 | 10-18 | R20 三项修正 | 引用同源、接管主线程约束、单资源报告；复核见 R22 |
| R23 | 10-18 | editor 契约 | 会话/视图/交互/后端类型交付；审查见 R24 |
| R25 | 10-18 | R24 E1/E2 修正 | EditDraftState、草稿更新入口、后端 open 钩子；复核见 R26 |
| R27 | 10-18 | EditTools 契约 | 缓动/批量/烘焙/捕获及结果类型交付；复核见 R28 |
| R29 | 10-18 | R28 T1/T2 修正 | 请求快照、捕获模式/覆盖/版本已补；复核见 R30 |
| R31 | 10-19 | 第九批：布局连续属性与类型注册表 | 契约交付；求值/Codec 实现、能力描述与校验钩子待后续 |
| R33 | 10-19 | 第十批：具体类型 Codec 与两阶段解码 | 契约与往返测试交付；叠加/NBT/网络/字段级诊断待后续 |
| R35 | 10-19 | R34 三项修复 | 叠加双向拒绝、严格整数、TrackType 上界收窄、解码结果不变量；正式测试 24/178 |
| R37 | 10-19 | R36 两项残留修复 | 严格整数改 BigDecimal 精确检查；叠加改为声明的可选受检字段，压缩 Ops 往返恢复；正式 24/181 |
| R39 | 10-19 | R38 数值类型分支修复 | Float/Double 走二进制值，其余走精确十进制；正式 24/183 |
| R41 | 10-19 | R1 表达式契约交付 | 编译器 / 引用 / 诊断与求值输入契约交付，无实现；正式 25/189 |
| R43 | 10-19 | R42 两项缺口补齐 | EvaluationSession 共享额度/深度 + ExpressionSource/ExpressionFailure 失败链；正式 26/194 |
| R45 | 10-19 | R44 两项缺口补齐 | 资源失败缓存边界 + ExpressionInputs.source 与字段/KeyId 粒度；正式 26/196 |
| R47 | 10-19 | R2/R3/R4 契约交付 | FrameFactsProvider/FrameFactsRequest、客户端解析/采集注册、TypeCapabilityService；正式 27/199；审核 R49 的两项 P2 已由提交 C1 补齐 |
| R48 | 10-19 | R5 网络播放请求契约 | 审核 R50：1 项 P2（DataResult<Void> 成功模型）待修；其余已审契约未发现确定问题，具体编解码仍延期；正式 28/203 |
| C1 | 10-19 | R49-H1/H2 两项 P2 契约补齐 | 审核 R51：原输入/逐项需求与诊断入口关闭；事实同版本异源绑定残留 R51-N1（P2）；正式 28/209 |
| C2 | 10-19 | R6/R7/R8 契约交付 | 审核 R52：当前契约范围无确定阻断；保留原序分派文案澄清，运行编辑/执行/显示延期；正式 31/214 |
| C3 | 10-19 | R51-N1 事实来源绑定 | 审核 R53：原同版本异源反例关闭，未发现确定回归；实际 provider 传递待 core；正式 31/214 |
| C4 | 10-19 | R9–R12 剩余契约 | TypeValidator/Context、StructureFailed.fieldPath、previewOverrides、RigQualityStatus/OutputPixelRect；正式 33/218；待审核 |
| C5 | 10-19 | R12 停路质量 + R50 编码成功值 | 停路可见机位允许 0×0/密度 0；encode 改 `DataResult<Unit>`；正式 33/219；待审核 |

### C1. R49 两项 P2 契约补齐

**日期**：2026-10-19。本条属于**提交**，对象为**审核 R49** 的 H1 / H2。只改 `api` 契约、正式测试与文档；不含 `FrameFactsProvider` / `AnchorResolver` / `SampleCollector` 实现。

#### C1.1 交付

| 项 | 交付 | 关键约束 |
| --- | --- | --- |
| R49-H1 输入 | [FrameFactsPlan](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFactsPlan.java#L39>) 逐机位给出 `anchor` / `target` 需求（`Required` / `NotRequired`）与生效 `AimState`；[FrameFactsRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFactsRequest.java#L38>) 新增 `animationTime`，用计划取代 `Set<RigId>` | 计划由宿主按动画时间、播放覆盖与实际依赖生成；提供者不得重读文档默认锚点、当前播放器或编辑机位；目标需求必须等于生效目标 |
| R49-H1 绑定 | [FrameFacts](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFacts.java#L39>) 新增 `sourceRevision`；[EvaluationContext](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationContext.java#L51>) 拒绝事实版本与快照版本不一致 | 请求与结果绑定同一快照；不能靠内容相等认定同源，跨发布混用是参数错误 |
| R49-H2 诊断作用域 | [AnchorResolutionContext#diagnosticScope](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/AnchorResolutionContext.java#L51>) 返回 `DiagnosticScope.Document`；[SpatialResolution.failed](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/SpatialResolution.java#L58>) 固定 ERROR 并绑定该作用域 | 版本只能取自本次上下文，不能读当前 store 或伪造文件作用域；对象定位可空 |

#### C1.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**28 suite / 209 用例**，v2 为 **22 / 143**，失败、错误、跳过均为 0；v2 主源码 **211** 个 Java 文件。

[HostServiceContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/HostServiceContractTest.java>) 由 3 例扩为 **9 例**：逐项需求与非法目标拒绝、计划容器隔离、请求动画时间与未知机位拒绝、同游戏刻 9 秒/10 秒可区分、覆盖结果由计划承载、解析器按上下文作用域构造失败诊断；`EvalContractTest` 增加事实版本不匹配的拒绝断言。

#### C1.3 边界与风险

- **没有实现**：`FrameFactsProvider`、任何 `AnchorResolver` / `SampleCollector` 与客户端世界查询；`AimOverride` → 生效 `AimState` 的映射仍属宿主职责，测试只用替身函数验证契约承载。
- 请求只校验“计划机位存在于快照”；“局部空间没有有效锚点必须拒绝开始播放”仍归播放启动 / 预览初始化（主设计 §6.3），本轮不新增宿主契约。
- `diagnosticScope()` 保证解析器拿到的版本来源正确；provider 是否原样使用、是否拒绝跨快照诊断仍待实现批次验证。
- 未启动客户端，未验证真实世界查询、采样节奏、渲染或网络；正式用例仍只覆盖结构不变量。

### C2. R6/R7/R8 扩展能力契约

**日期**：2026-10-19。本条属于**提交**，对象为 P1 计划的 R6/R7/R8；只改 `api` 契约与测试，不含时间轴编辑、事件执行器或通道显示实现。

#### C2.1 交付

| 项 | 交付 | 关键约束 |
| --- | --- | --- |
| R6 扩展时间轴 | [ExtensionTimelineTrack](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/ExtensionTimelineTrack.java>) / `TimelineKey` | 只派生稳定 KeyId 与时间，不发放原地修改；列表按时间严格递增、时间有限非负 |
| R7 事件执行器注册 | [EventExecutor](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/EventExecutor.java>) / [EventExecutorRegistry](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/EventExecutorRegistry.java>) | 按轨道类型分派；重复类型拒绝；内建 curve/aim/active/camera_source 不可注册为事件执行器 |
| R8 通道描述 | [ChannelDescription](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/ChannelDescription.java>) / [ChannelDescriptionRegistry](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/ChannelDescriptionRegistry.java>) | 完整不可变映射；标签用翻译键、颜色 RGB、默认单位/说明；作者覆盖显式存储 |

#### C2.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**31 suite / 214 用例**，v2 为 **25 / 148**，失败、错误、跳过均为 0；v2 主源码 **216** 个 Java 文件。

新增 `ExtensionTimelineContractTest`、`ChannelDescriptionRegistryContractTest`、`EventExecutorRegistryContractTest`；覆盖 KeyId/时间边界、完整注册表与重复/内建类型拒绝。

#### C2.3 边界与风险

- `ExtensionTimelineTrack` 只提供派生键视图；把编辑请求转换成 `EditOp`、冲突检查与原子提交仍待 editor / core。
- `EventExecutorRegistry` 只固定注册形状与内建类型拒绝；按类型分派、执行顺序、异常终止与部分执行报告仍待 `EventDispatcher` / core。
- 通道描述是 common 显示默认值；实际翻译、颜色主题与作者外观覆盖的持久化仍待 editor / storage。

### C3. R51-N1 事实来源绑定

**日期**：2026-10-19。本条属于**提交**，对象为**审核 R51** 的 N1；只改 `api` 契约与测试。

#### C3.1 交付

| 项 | 交付 | 关键约束 |
| --- | --- | --- |
| 事实来源 | [FrameFacts](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFacts.java>) 用 `AnimationSnapshot sourceSnapshot` 替换 `sourceRevision` 组件；`sourceRevision()` 仅作诊断派生 | 事实保留生成它的原快照引用 |
| 上下文校验 | [EvaluationContext](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationContext.java>) 按 `facts.sourceSnapshot() != snapshot` 拒绝 | 同 revision / 同内容的新快照不再通过 |
| Provider 契约 | [FrameFactsProvider](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFactsProvider.java>) 要求返回事实持有请求快照的同一引用 | 不要求 provider 违反准备规则 |

#### C3.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**31 suite / 214 用例**，v2 为 **25 / 148**，失败、错误、跳过均为 0；v2 主源码 **216** 个 Java 文件。

[EvalContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvalContractTest.java>) 增加同版本、不同快照引用的事实拒绝；正确来源成功路径继续由既有上下文测试覆盖。

#### C3.3 边界与风险

- provider 实现尚未存在；实际是否把 `request.snapshot()` 原样写入 facts 仍待 `core.playback` 验证。
- 事实持有快照引用会增加运行期生命周期引用；未进入文档 / 历史 / 网络，释放时机随帧上下文。
- 不同动画时间 / 计划的同版本事实混用仍按同一来源绑定规则处理，不另开第二套身份。

### C4. R9–R12 剩余契约

**日期**：2026-10-19。本条属于**提交**，对象为 P1 计划 R9–R12；只交付契约与结构测试，不含统一校验分派、Codec 路径填充、会话实现或 renderer。

#### C4.1 交付

| 项 | 交付 | 关键约束 |
| --- | --- | --- |
| R9 类型级校验 | `TypeValidator<T>` / `TypeValidationContext`；Track/Anchor/SampleType 可选 `validator()` | 只校验单实例；Revision + 稳定对象 ID + 可选所属机位 + 字段路径；统一 `DocumentValidator` 分派 |
| R10 结构路径 | `DocumentDecodeResult.StructureFailed(message, fieldPath)`，旧单参构造保留 | 空路径只表示解析器未提供；已知字段不得丢弃 |
| R11 预览覆盖 | `SessionViewState.previewOverrides(RigId)`，复用 `rigOverrides` | 锚点 / 目标 / 避障只属于预览，不写文档 |
| R12 可见像素质量 | `OutputPixelRect`、`RigQualityStatus`、`RigRenderStatus.quality`、`DegradeReason.CANVAS_OFFSCREEN` | 完整/可见/请求/实际几何、密度、裁剪降采样、画布外原因；不改 FOV/构图 |

同时将 [EventExecutor](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/EventExecutor.java>) 的分派说明统一为原发生序中连续同类型片段或单事件，避免跨类型/周期聚合重排（R52-C1）。

#### C4.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**33 suite / 218 用例**，v2 为 **27 / 152**，失败、错误、跳过均为 0；v2 主源码 **220** 个 Java 文件。

新增 `TypeValidationContractTest`、`StructureFailureContractTest`、`PlaybackContractTest` 的质量状态用例；`EditorContractTest` 增加 `previewOverrides` 断言。

#### C4.3 边界与风险

- 类型校验器尚未接入 `DocumentValidator`；实际分派顺序 / 层级仍待 core。
- `StructureFailed.fieldPath` 的填充仍待 Codec / 存储适配；当前只提供承载形状。
- 预览覆盖的实际恢复与后端切换仍待 editor 会话实现。
- 可见像素质量策略、裁剪映射、目标池、`CANVAS_OFFSCREEN` 运行判定与 renderer 均未实现；契约测试不代表 R0 验收。

### C5. R12 停路质量与 R50 编码成功值

**日期**：2026-10-19。本条属于**提交**，对象为 R12 的停路质量残留与审核 R50 的编码成功值问题。

#### C5.1 修正

| 项 | 修正 |
| --- | --- |
| R12 停路 | `RigQualityStatus` 允许未呈现的可见机位使用 0×0 实际目标与 0 密度；不把“无目标”误报为 `offscreen`。`RigRenderStatus` 在 `rendered=true` 时仍要求实际尺寸为正，并保持与 quality 尺寸一致 |
| R50 编码 | `PlaybackProtocolCodec.encode` 从 `DataResult<Void>` 改为 `DataResult<Unit>`，成功值固定 `Unit.INSTANCE`；StreamCodec 适配器也通过标准 `result()` 判断成功，不再只检查 `isError()` |

#### C5.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**33 suite / 219 用例**，v2 为 **27 / 153**，失败、错误、跳过均为 0；v2 主源码 **220** 个 Java 文件。

`PlaybackContractTest` 增加“可见、停路、0×0 目标仍可查询几何”的回归；`PlaybackProtocolContractTest` 增加 `Unit.INSTANCE` 标准 `result()` 成功访问。

#### C5.3 边界与风险

- 质量状态仍未由 renderer 实际生成；停路/释放目标的 0×0 只是契约允许，不是运行实现。
- 编码成功值形状修复不代表完整字节编解码、版本分派或收包校验已实现；R50 待复核。
- 网络编码失败仍以异常适配 StreamCodec；缓冲区回滚策略未变。

## R30. R29 复核关闭及尺寸自适应需求

**日期**：2026-10-18。源码基线 `03b1c69`；本次修改设计和施工/复核文档，不实现工具或渲染算法。

### R30.1 T1/T2 契约复核

| 项 | 证据 | 结论 |
| --- | --- | --- |
| T1 | [EasingRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/EasingRequest.java>)、[BatchTransformRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/BatchTransformRequest.java>)已显式携带非空 AnimationSnapshot；EditTools 禁止读当前 store | 原输入文档不可知的缺口关闭，实际目标/冲突处理待core |
| T2 | [AimCaptureRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/AimCaptureRequest.java>)携非空mode/实际override，baseRevision须匹配冻结快照；[AimCaptureMode](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/AimCaptureMode.java>)规定CURRENT/LEFT_TARGET及紧邻左侧不回溯 | 原覆盖来源/显式选择/版本信息缺口关闭；Replace清除、左侧事实准备与转换仍待算法 |

[EditToolsBindingTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/EditToolsBindingTest.java>)只验证快照携带/非空、CURRENT+Inherit及基版本错配拒绝；不证明同ID不同键的实际结果、LEFT_TARGET正向解析、Replace/Disabled区分或原子提交已运行。EditTools 的 UNKNOWN 文案已改为“未显式兜底”，R28该非阻断澄清可关闭。本次未发现此修复引入的确定代码缺陷。

### R30.2 新需求的确认与边界

用户选择“按可见像素需求自动降分辨率”，而非仅按完整矩形或缩小渲染视锥。已入[主设计§7.6.1](<../design-animation-editor-framework.md>)及[UI设计](<../design-editor-ui-framework.md>)：以最终输出像素计算完整矩形和画布交集、保持完整构图和UV映射、按像素/裁剪需求分档并滞回，完全出屏副路可跳过世界绘制但仍求值，当前来源相机不因出屏停止应用。

部分裁剪降低完整纹理密度可能损失可见区域细节，须显示真实降级并允许关闭裁剪面积降采样；不承诺无损或线性性能收益。分辨率档位/阈值需R0实测；不改变FOV/渲染距离、模拟/事件或创作字段。现有状态缺几何/密度信息及专用画布外原因，列为新契约切片，**没有声称代码已经支持**。

### R30.3 实际验证

实际执行 `./gradlew.bat --offline compileJava test --rerun --console=plain`，BUILD SUCCESSFUL：22 个suite / 160 个用例通过，v2为16 / 94，无失败/错误/跳过。166个主源码Java文件；新需求未写代码，不在此测试覆盖内。

## R31. 第九批：布局连续属性与类型注册表契约

**日期**：2026-10-19。源码基线上一次提交 `7b488a2` + 本次工作区改动；本次只改契约与测试，不实现求值、渲染或编解码字节层。

### R31.1 交付内容

| 项 | 交付 | 关键约束 |
| --- | --- | --- |
| 布局连续属性（Q14） | [AnimatedNumber](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/AnimatedNumber.java>)：`Value(NumberSource)` / `Keyed(Curve, defaultValue)` | `Keyed` 只接受 `CLAMP/CLAMP`（§3.1.2 端点保持）；空曲线用显式默认值；`isStatic`/`staticValue` 供静态校验，动态分量留逐帧检查 |
| 可动画矩形 | [RigView.Rect](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/RigView.java>) 四分量改 `AnimatedNumber`；两种坐标模式仍静态互斥 | 静态可判定部分构造期拒绝非有限/非正尺寸；`isStatic`/`endTime`；预设仍产出常数分量 |
| 已求值矩形 | [ResolvedRect](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/ResolvedRect.java>) + [RigPose](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigPose.java>) 改用之 | 准备帧携带数值矩形，不携带曲线；非法求值结果无法构造；画布映射留给合成器 |
| 时长口径 | `CameraRig.contentDuration()` 纳入 `view.layoutDuration()` | 布局曲线计入时长（§3.1）；静态矩形与空曲线贡献 0，末键保持不产生无限时长 |
| 类型注册表 | [TypeRegistry](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/TypeRegistry.java>) 冻结语义 + `TrackType` / `AnchorType` / `SampleType` + 三个注册表 | 冻结后注册拒绝、重复 ID 拒绝、未知类型解码报错；锚点存档与网络 Codec 由同一类型同时提供；能力描述与类型级校验钩子**未写死**（计划 §7.2） |

### R31.2 实际验证

实际执行 `./gradlew.bat --offline test --rerun --console=plain`，`BUILD SUCCESSFUL`：23 个 suite / 170 个用例通过，v2 为 17 / 104，无失败/错误/跳过；176 个主源码 Java 文件。

新增 [LayoutAnimationContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/LayoutAnimationContractTest.java>) 9 例（分支静态/动态、空曲线默认值、`CLAMP` 拒绝、静态几何拒绝与动态放行、`endTime`、布局时长与禁用机位），《播放契约》新增 `ResolvedRect` 1 例。

### R31.3 未覆盖与风险

- **未实现**：布局分量求值（常量/公式/曲线 → 数值）、逐帧有限性与正尺寸检查、静态校验器接入、`ResolvedRect` 到输出像素的映射；注册表的具体 Codec 与运行时冻结实现。
- **未裁决**：注册表的能力描述与类型级校验钩子形状（计划 §7.2，方向 A/B/C 均未采纳）。因此本批解码器只按 ID 查 Codec，文档校验仍走 `DocumentValidator`，不依赖未定形状。
- **风险**：`AnimatedNumber.Keyed` 强制 `CLAMP` 是对 §3.1.2 的直接实现；若将来布局需要循环外插，必须同时改设计、契约与测试，而不是放宽构造器。`RigPose.rect` 类型变更是破坏性调整，但 v2 无外部消费者。
- 叠加内容仍为不透明 `Object`，O1–O3 封闭内容类型未开始。

## R32. 注册表选型调研（无源码改动）

**日期**：2026-10-19。**本轮不改源码、不改测试**；只新增[选型对比文档](<registry-selection.md>)并同步计划 §7.2。

### R32.1 调研结论

| 方案 | 判断 |
| --- | --- |
| N：`api` 直接暴露 NeoForge/MC `Registry` | 不推荐：类型表不承载游戏内容、不需要数据包与注册表同步；`RegistryCodecs` 无分派助手，收益有限而把 26.x 版本耦合带进 `api` |
| N′：保留 `TypeRegistry`，`core` 用自定义注册表实现 | 可选：第三方可用 `DeferredRegister`；需先验证冻结时机、`sync(false)`、独立测试构造与事件顺序 |
| O：自有 `TypeRegistry`（R31 已交付） | 推荐维持：满足当前全部需求，将来有真实需求再按 N′ 演进，`api` 形状不变 |

能力描述与注册表机制**正交**：推荐 B（客户端能力服务），等客户端解析/采集批次再定契约；类型级校验钩子按用户指示等选型后再定；叠加 O1–O3 按用户指示等设计补全字段。

### R32.2 证据（可复现）

- 版本核对：`gradlew dependencies --configuration compileClasspath` → `net.neoforged:neoforge:26.1.2.36-beta`；此前 `build/moddev/artifacts` 里的 21.x jar 是历史残留，未用于结论。
- API 核对：从 `neoforge-26.1.2.36-beta-universal.jar` 与 `minecraft-patched-26.1.2.36-beta-merged.jar` 抽取类后 `javap -p` 读取，涉及 `NewRegistryEvent`、`RegistryBuilder`、`RegisterEvent`、`DeferredRegister`、`DataPackRegistryEvent$NewRegistry`、`Registry`、`RegistryCodecs`、`RegistrySynchronization`、`StreamCodec#dispatch`、`IdDispatchCodec`、`DataMapType`。
- 单独构造可行性：`RegistryBuilder#create()` 为 public，可脱离 FML 事件构造注册表；事件注册路径仍需 FML。

### R32.3 未验证

自定义注册表在 mod 加载序列中的冻结时机、`sync(true)` 与网络 Codec 的强制关系、`DeferredRegister` 对自定义注册表键的实际支持、独立构造是否隐藏 FML 依赖——若选 N′，这些是进入实现前的 R0 探针项（选型文档 §6）。

## R33. 第十批：具体类型 Codec 与两阶段解码

**日期**：2026-10-19。源码基线 `7b488a2` + 本轮工作区改动。本轮只交付**契约、编解码映射与纯内核测试**，不实现存储读写、NBT / 网络 Ops 实测或字段级结构诊断。

### R33.1 交付

| 项 | 内容 |
| --- | --- |
| 编解码工具 | [Codecs](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java>)：枚举按名、有限 / 非负数值、非空串、变体标签、构造器异常转 `DataResult` |
| 注册表组合 | [DocumentRegistries](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/DocumentRegistries.java>)：三张类型表 + `anchorCodec()`（`partialDispatch`，未知锚点类型返回解码错误） |
| 叶子与模型 Codec | 8 个稳定 ID、`Vec3` / `Rotation` / `RotationMask` / `ObstacleSettings`、`PathNode` / `Path`、`Constant` / `Formula` / `TrackRef` / `NumberSource` / `ValueSource` / `Keyframe` / `Curve` / `AnimatedNumber`、`ValueKeyframe`（泛型）、`CurveTrack`、`CameraChannel`、`CameraSource`、`AimTarget` / `AimState` / `Space`、`AimTrack` / `ActiveTrack` / `CameraSourceTrack`、`WorldSample`、`Marker`、`AudioRef`、`Variable` / `CustomFunction` / `Symbols`、`RigView` / `RigView.Rect`、`CameraRig`、`CameraAnimation` |
| 两阶段解码 | [DocumentCodec](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodec.java>) + [DocumentDecodeResult](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentDecodeResult.java>)：结构阶段 → 统一校验阶段 → `Accepted` / `StructureFailed` / `ValidationFailed` |
| 版式 | 多分支类型使用显式标签（`type` / `mode` / `kind`）；字段 snake_case；未知标签与未注册类型均为解码错误 |

### R33.2 实际验证

实际执行 `./gradlew.bat --offline test --rerun --console=plain`，`BUILD SUCCESSFUL`：24 个 suite / 175 个用例通过，v2 为 18 / 109，无失败/错误/跳过；180 个主源码 Java 文件。

新增 [DocumentCodecTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodecTest.java>) 5 个用例：

1. 完整往返（机位 / 通道 / 注视 / 活动 / 布局曲线 / 符号 / 标记 / 采样 / 音轨引用 / 扩展轨道）；
2. 版本不符与版本缺失在结构阶段拒绝；
3. 未知锚点 / 轨道 / 采样类型在结构阶段拒绝；
4. 校验阶段：`ERROR` 拒绝、`WARNING` 接受；
5. 非空叠加编码失败（不静默丢弃）。

依赖门禁：v2 无 `net.minecraft.client`、无旧根、`editor.spi` 无 `editor.internal`，违规 0；`git diff --check` 干净。

### R33.3 未覆盖与风险

- **未实现**：叠加内容 Codec（O1–O3）、字段级结构诊断（当前是 DFU 文本，含路径）、NBT / 网络 Ops 往返、结构 / 深度 / 字节规模上限、`core` 侧注册表实现与冻结时序。
- **已知简化**：解码边界捕获 `IllegalArgumentException` 并转结构失败；非法数据不会崩溃，但错误文本来自构造器消息而非结构化诊断。
- **格式决定**：值源 / 布局数值 / 锚点 / 采样 / 空间 / 矩形分支均用显式标签；这属于新格式 v1（允许破坏性修改，不兼容旧文件）。
- **测试替身**：测试用自写注册表与最小锚点 / 采样 / 轨道类型，验证的是契约与分派，不是 NeoForge 注册表实现的运行行为（按 R32 裁决维持自有实现）。
- **子代理分工**：叶子 Codec 由两个子代理并行起草，其中一个未完成即被收回，剩余文件由主代理补齐；现有测试覆盖的是合并后的代码。

## R34. R31–R33 代码复核

**日期**：2026-10-19。审核 R31 布局连续属性与注册表契约、R32 注册表选型，以及 R33 具体类型 Codec 与两阶段解码的当前工作区代码。本轮只审核，不修改源码或正式测试；以下三项均已通过隔离探针复现，**尚未修复**。

### R34.1 发现

| 编号 | 级别 | 问题 | 证据与触发 | 修正方向 |
| --- | --- | --- | --- | --- |
| C1 | P1 | 不支持的叠加数据在公开编解码路径中被静默丢弃 | [CameraAnimation.mapCodec](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L165>)未读取 `overlays`，解码固定创建空列表；合法 JSON 加非空 `overlays` 后，[DocumentCodec.decode](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodec.java#L77>)仍返回 Accepted，候选叠加为空。反向直接调用公开 `CameraAnimation.codec(registries)` 编码含叠加文档也成功并省略该字段，绕过 DocumentCodec 的编码检查 | 在根 Codec 统一执行双向不支持检查，拒绝非空叠加；补解码拒绝和直接根 Codec 编码拒绝测试。叠加实现延期不等于允许接受后丢弃 |
| C2 | P2 | 整数字段通过 `Codec.INT` 截断或溢出，改变输入并绕过版本检查 | [版本字段](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L168>)实测 `data_version: 1.9`、`4294967297` 均转换成 1 并被接受；[RigView](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/RigView.java#L318>)的 `update_every_frames: 1.9` 转成 1 并被接受。[WorldSample](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/WorldSample.java#L136>)的重采周期使用相同整数 Codec | 转换前检查数学整数性与目标范围，再检查版本或正整数约束；补小数、溢出及溢出后落入合法值的测试 |
| C3 | P2 | 扩展轨道注册泛型过宽，解码可污染 `List<ExtensionTrack>` | [TrackType](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/TrackType.java#L36>)允许 `T extends AnimationTrack`；[文档分派桥接](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L198>)将任意条目的 Codec 强转为扩展轨道 Codec。用 `TrackType<CurveTrack>`、自定义 ID `test:curve` 和 `CurveTrack.MAP_CODEC` 注册，无需 raw type 或调用方 unchecked cast，即可使解码返回 Accepted，随后 `duration()` 遍历时抛 ClassCastException | 将上界收窄为 `AnimationTrack.ExtensionTrack`，消除不成立的桥接；必要时校验解码类型与分派 ID 一致。补注册类型边界测试 |

C3 的触发前提是第三方注册了语义上不应进入扩展表的内建轨道类型；不能描述为合规注册下普通文件必然触发。问题在于公开泛型允许该配置，已实现的分派又未拒绝不兼容结果。

### R34.2 复核边界

- R31 的静态矩形检查、CLAMP 限制、ResolvedRect 数值约束与布局时长合并未发现新的确定缺陷；求值、逐帧检查和渲染仍按既有范围延期。
- R32 无源码改动。维持 O + 客户端能力 B 的裁决与当前契约一致，不据此宣称运行注册表或冻结时序已验证。
- 正常 DocumentCodec 路径会拒绝结构错误且不调用校验器；ERROR → ValidationFailed、WARNING → Accepted 的分流正确。
- `DocumentDecodeResult` 的公开构造器未强制 Accepted 不含 ERROR、ValidationFailed 至少含一条 ERROR。此项仅作为非阻断契约防御建议；正常解码入口未产生该非法结果，不计入上述三项。
- 不将已明确延期的叠加类型实现、core 算法、NBT/网络实测、规模限制或注册表能力服务计为本轮缺陷。

### R34.3 实际验证

1. 执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL；这是现有测试基线验证，不能证明上述反例已覆盖。
2. 在构建目录创建[临时探针](<../../build/review-r31-r33/probe-src/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/ReviewR31R33ProbeTest.java>)及独立 Gradle init 配置，未改正式源码、测试或构建脚本。
3. 最终执行 `./gradlew.bat --offline --no-configuration-cache -I build/review-r31-r33/probe.init.gradle test --tests '*ReviewR31R33ProbeTest' --console=plain`，BUILD SUCCESSFUL，**5 个探针通过**：确认叠加解码丢失、直接根编码丢失、版本小数/溢出、帧数小数及扩展轨道类型污染。探针断言的是缺陷现状，**通过不表示修复完成**。
4. 首轮探针误预期布尔版本也会被接受，出现 1 个断言失败；实际布尔版本被正确拒绝。修正探针预期后重跑，最终无失败、错误或跳过。

临时探针和测试报告位于可清理的构建目录，不属于正式回归基线；修复时应将对应拒绝行为加入正式测试。未启动客户端，未进行运行声音或渲染验证。

## R35. R34 三项修复

**日期**：2026-10-19。修复 R34 的 C1（P1）/ C2（P2）/ C3（P2），并采纳 R34.2 的解码结果防御建议。不改变架构，不新增开放性契约。

### R35.1 修复

| 项 | 修复方式 | 位置 |
| --- | --- | --- |
| C1 叠加静默丢失 | 根 Codec 用 `MapCodec#mapResult` 做**双向守卫**：解码时若 `overlays` 字段存在且为非空列表则报错；编码时若非空则向 `RecordBuilder` 写入错误。`DocumentCodec.encode` 的重复检查移除，统一由根 Codec 拒绝，直接调用 `CameraAnimation.codec(...)` 也无法绕过 | [CameraAnimation.mapCodec](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java>)、`DocumentCodec.encode` |
| C2 整数截断/溢出 | 新增 `Codecs.strictInt()`：先按双精度读取，再检查数学整数性与 `int` 范围，然后才转换；`data_version`、`z_order`、`update_every_frames`、`resample_every_ticks` 全部改用 | [Codecs](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java>)、`CameraAnimation`、`RigView`、`WorldSample` |
| C3 扩展轨道类型越界 | `TrackType<T>` 上界收窄为 `AnimationTrack.ExtensionTrack`；删除 `CameraAnimation.extensionCodec` 的 unchecked 强转桥接。内建轨道不再可能被注册成扩展类型（编译期拒绝） | [TrackType](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/TrackType.java>)、`CameraAnimation.trackCodec` |
| R34.2 建议 | `DocumentDecodeResult.Accepted` 拒绝携带 `ERROR`；`ValidationFailed` 要求至少一条 `ERROR`（公开构造器防御） | [DocumentDecodeResult](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentDecodeResult.java>) |

### R35.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**24 suite / 178 用例**，v2 为 **18 / 112**，无失败/错误/跳过；v2 依赖门禁 0 违规；`git diff --check` 干净。

[DocumentCodecTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodecTest.java>) 由 5 例增至 **8 例**，新增/强化：

1. 非空 `overlays` 解码拒绝（空列表仍接受）；根 Codec 直接编码含叠加文档拒绝；
2. `data_version` 小数与溢出、`update_every_frames` 小数、`resample_every_ticks` 小数均拒绝；
3. `Accepted` 含 `ERROR`、`ValidationFailed` 不含 `ERROR` 均抛 `IllegalArgumentException`。

### R35.3 边界与风险

- `overlays` 为空列表仍接受：字段存在但无数据不构成丢失；非空一律拒绝，O1–O3 后改为封闭内容类型。
- `strictInt()` 用双精度读取：超过 `2^53` 的整数先被 `int` 范围检查拒绝；JSON 布尔/字符串仍由 DFU 数字读取拒绝，不会退化成 `1`。
- C3 是**编译期**修复，无法用单元测试直接断言“`TrackType<CurveTrack>` 不可注册”；边界由泛型上界保证，已在 `TrackType` 文档写明。
- 修复过程中一次脚本化字符串替换损坏了 `WorldSample.java`（大写 `C` 被替换），已按 HEAD 内容重建该文件并复核无残留；最终测试覆盖的是重建后的版本。
- 仍未做：字段级结构诊断、NBT / 网络 Ops 往返、注册表能力/类型级校验钩子（见计划 §6.1）。

## R36. R35 修复复核

**日期**：2026-10-19。复核当前工作区的 R35 修复与正式回归测试；本轮不修改项目源码、正式测试或构建脚本。结论：**R34-C1、R34-C3 及严重级不变量加固在原问题范围关闭；R34-C2 部分关闭，另发现一项压缩 JSON 回归。**

### R36.1 修复状态

| 项 | 复核证据 | 结论 |
| --- | --- | --- |
| R34-C1 叠加静默丢失 | [根 MapCodec 守卫](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L183>)在普通 JsonOps 下拒绝非空叠加；直接根 Codec 编码也返回错误。探针另测非列表值及结构失败不调用校验器 | 原静默丢失问题关闭；守卫引入的压缩 Ops 回归见 R36-N2 |
| R34-C2 整数截断/溢出 | [strictInt](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L74>)已替换版本、层级、帧数及采样周期入口；原 `1.9` / `4294967297` 拒绝，合法 int 端点往返通过 | **部分关闭**：先转 double 仍丢失原始数值精度，见 R36-N1 |
| R34-C3 扩展轨道类型越界 | [TrackType](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/TrackType.java#L39>)上界为 `AnimationTrack.ExtensionTrack`；[文档分派](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L247>)已删除 unchecked 桥接。探针核对泛型上界，正式测试保持合法扩展往返 | 原公开泛型允许内建轨道污染扩展列表的问题关闭；不承诺防御 raw type 或违反 SPI 的任意实现 |
| 严重级不变量 | [DocumentDecodeResult](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentDecodeResult.java#L41>)的 Accepted 拒绝 ERROR，ValidationFailed 要求 ERROR；正式回归覆盖 | 非阻断防御建议已落实 |

### R36.2 残留与回归

| 编号 | 级别 | 问题 | 实测触发与影响 | 修正方向 |
| --- | --- | --- | --- | --- |
| R36-N1 | P2 | `strictInt` 在验证前转 double，非整数可先舍入为整数 | [Codecs.strictInt:75](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L75>)先用 `Codec.DOUBLE`。用 JsonParser 保留输入字面量后，`data_version: 1.0000000000000001`、`0.99999999999999999` 均被解成 1，根文档返回 Accepted；`2147483647.0000001`、`-2147483648.0000001` 也分别被解成合法 int 端点。原始小数在 int 范围附近同样会丢精度，不只发生于大于 `2^53` 的整数 | 从 Ops 保留的原始 Number 执行精确整数性与范围检查，例如对 JSON 十进制保留精度后使用精确转换；不得先 doubleValue 再判断。增加原始 JSON 文本/BigDecimal 构造的近整数与边界反例 |
| R36-N2 | P2 | 新叠加守卫读取未声明的键，破坏压缩 JSON 合法往返 | [CameraAnimation:192](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L192>)读取 `input.get("overlays")`，但 mapResult 没有将该键加入根 MapCodec 的 keys。实测正常无叠加文档经 `JsonOps.COMPRESSED` 编码成功后，解码返回 `StructureFailed[message=Not a json array: 1.0]`：DFU 压缩键表对未知键取得索引 0，守卫实际读到版本号 | 将 overlays 作为正式声明的可选受检字段，或同时实现正确的 keys/压缩空槽编码与解码；保留普通 JsonOps 的双向拒绝。补根 Codec 与 DocumentCodec 的 COMPRESSED 合法往返回归 |

R36-N1 是 R34-C2 的修复残留，不重新报告原 `1.9` 或整数溢出用例仍失败。R36-N2 为 R35 新引入的公开 JSON Ops 回归，不是要求提前完成延期的 NBT/网络实现。压缩格式当前没有业务接线，不宣称普通 JsonOps 文档也无法往返。

### R36.3 实际验证

- 执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL。按 UTF-8 解析报告核对：**24 suite / 178 用例**，v2 为 **18 / 112**，失败、错误、跳过均为 0。首次统计脚本未指定 UTF-8 导致旧中文用例报告解析失败，改正报告读取编码后重新统计；该错误不是测试失败。
- 在构建目录建立[隔离探针](<../../build/review-r35/probe-src/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/ReviewR35ProbeTest.java>)和临时 init 配置，执行 `./gradlew.bat --offline --no-configuration-cache -I build/review-r35/probe.init.gradle test --tests '*ReviewR35ProbeTest' --console=plain`，最终 BUILD SUCCESSFUL，**5 个探针通过**。
- 探针覆盖：普通 JsonOps 叠加双向守卫及结构失败不调用校验器；原版本错误拒绝；高精度小数残留；合法 int 端点及泛型上界；压缩 JSON 往返回归。反例探针断言现有缺陷，因此通过不代表修复完成，不计入正式 178 用例基线。
- 首轮探针将 `overlays: null` 也预期为拒绝，但 DFU 将其作为缺省处理；调整该非数据丢失场景的预期后重跑。非空列表和其他非列表值均按预期拒绝，未将 null 缺省另报缺陷。
- 未启动客户端；未验证延期的 core、渲染、NBT/网络或设备行为。正式回归仍未覆盖高精度 JSON 小数与压缩 JSON 往返。

## R37. R36 两项残留修复

**日期**：2026-10-19。修复 R36-N1（`strictInt` 精度残留）与 R36-N2（叠加守卫破坏压缩 JSON 往返）；不改变延期范围。

### R37.1 修复

| 项 | 修复方式 | 位置 |
| --- | --- | --- |
| R36-N1 高精度小数 | `strictInt` 不再先经 `Codec.DOUBLE`；改为直接 `ops.getNumberValue` 取 Ops 保留的原始 `Number`，按其十进制文本用 `BigDecimal` 做**精确**整数性与 `int` 范围检查后转换。`1.0`、`1e3` 等数值上为整数的写法仍接受；`1.0000000000000001`、`0.99999999999999999`、`2147483647.0000001` 均拒绝；`NaN` / `Infinity` / 非十进制文本拒绝 | [Codecs.strictInt / exactInt](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java>) |
| R36-N2 压缩 Ops 回归 | `overlays` 改为根 Codec **正式声明的可选受检字段**（`Codec.PASSTHROUGH.optionalFieldOf`），删除 `mapResult` 中读取未声明键的守卫。字段进入 `keys()`，压缩键表在编解码两侧一致；空列表 / 缺省 / `null` 接受，非空列表与非列表值拒绝，编码非空叠加仍拒绝 | [CameraAnimation.mapCodec / RawFields](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java>) |

### R37.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**24 suite / 181 用例**，v2 为 **18 / 115**，无失败/错误/跳过；`git diff --check` 干净。

[DocumentCodecTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodecTest.java>) 由 8 例增至 **11 例**，新增：

1. 原始 JSON 字面量的高精度小数（`1.0000000000000001`、`0.99999999999999999`、`2147483647.0000001`、`-2147483648.0000001`）拒绝，`1.0` 接受；
2. `JsonOps.COMPRESSED` 下 `DocumentCodec` 与根 `CameraAnimation.codec(...)` 的合法往返；
3. `COMPRESSED` 下编码含非空叠加的文档仍然失败。

### R37.3 边界与风险

- `BigDecimal` 依据 `Number.toString()`；JSON 经 Gson `LazilyParsedNumber` 保留原始字面量，因此精确；NBT 的浮点按自身精度参与检查（NBT 没有任意精度十进制字面量）。
- `overlays` 空列表或缺省仍接受；非空列表与非列表值拒绝。`null` 由 `optionalFieldOf` 归一为缺省，保持 R36 记录的行为。
- 压缩 JSON 当前没有业务接线；本次只证明公开 JsonOps 的合法往返，不宣称 NBT / 网络 Ops 已实现。
- 仍未做：字段级结构诊断、NBT / 网络 Ops 往返、注册表能力与类型级校验钩子（见计划 §6.1）。

## R38. R37 修复复核

**日期**：2026-10-19。审核当前工作区 R37 的严格整数与叠加字段修复。**R36-N1 的原始 JSON 高精度字面量反例、R36-N2 的压缩往返问题可关闭；发现 R37 精确转换对 Float 类型输入改变数值的新增 P2。** 本轮未修改项目源码、正式测试或构建脚本。

### R38.1 修复复核

| 项 | 证据 | 结论 |
| --- | --- | --- |
| R36-N1 高精度字面量 | [strictInt:81](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L81>)直接读取 Ops 的 Number；[exactInt:98](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L98>)检查 BigDecimal 整数性、范围并精确转换。探针直接测试 near-1 与 int 端点附近小数字面量拒绝，`1.0` / `1e3` / 负零及合法 int 端点接受 | 原 JSON 字面量舍入路径关闭；不同 Number 实现的语义问题另见 R38-N1 |
| R36-N2 压缩往返 | [根 MapCodec:168](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/CameraAnimation.java#L168>)正式声明可选 overlays，flatXmap 双向检查。根 Codec 的 COMPRESSED 合法往返通过；直接修改压缩字段槽注入非空列表、对象、数值和字符串均拒绝，且 DocumentCodec 不调用校验器 | 原未知键索引问题关闭；未发现叠加守卫的新确定缺陷 |
| 原有拒绝边界 | 普通 JsonOps 下非空 overlays 与非列表值拒绝，空列表和 null 缺省接受；正式测试保留双入口编码拒绝、严重级不变量与合法扩展往返 | R34-C1/C3 与 R35 防御加固没有发现回归 |

### R38.2 新发现

**R38-N1（P2）：`Number.toString()` 不保证是原始二进制浮点值的精确十进制表示，`exactInt` 会改变本来合法的 Float 整数输入。**

[Codecs.java:102](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L102>)统一使用 `new BigDecimal(number.toString())`。该方式适合保留 JSON 字面量的 Gson Number，但 `Float.toString()` 输出的是能回读为同一 float 的最短十进制文本，不保证该文本代表完全相同的数学整数。

在当前 Java 25 / JsonOps 环境实测：

| 输入构造 | 实际原数值 | 当前解码结果 |
| --- | --- | --- |
| `JsonOps.INSTANCE.createFloat(100000008f)` | 100000008（可精确表示） | 100000010，来自最短文本 `1.0000001E8` |
| `JsonOps.INSTANCE.createFloat((float) Integer.MIN_VALUE)` | -2147483648（可精确表示） | -2147483600 |

将第一种输入放入正常文档 `view.z_order`，DocumentCodec 返回 Accepted，候选层级为 **100000010**。因此这是公开 JsonOps 输入路径上的数值改变，不以尚未实现的 NBT/网络集成为触发前提；从原始 JSON 文本解析出的相同十进制字面量不是本例的 Float 类型输入，不能混为一谈。

**建议**：区分 Number 的实际类型。对 Float/Double 按其已表示的二进制数值检查有限性、整数性与 int 范围，再转换；Float 可先精确拓宽为 double。对保留任意精度十进制字面量的 Number 继续使用精确十进制转换，避免重新引入 R36-N1。增加 `ops.createFloat`、显式 Float JsonPrimitive、int 最小值和 Float 大整数的直接 Codec 及文档字段回归。

### R38.3 测试与范围

- 执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL。按 UTF-8 统计：**24 suite / 181 用例**，v2 为 **18 / 115**，失败、错误、跳过均为 0。
- 使用构建目录中的[隔离探针](<../../build/review-r37/probe-src/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/ReviewR37ProbeTest.java>)与临时 init 配置，执行 `./gradlew.bat --offline --no-configuration-cache -I build/review-r37/probe.init.gradle test --tests '*ReviewR37ProbeTest' --console=plain`，最终 BUILD SUCCESSFUL，**4 个探针通过**。其中 Float 探针断言缺陷现状，通过不表示已修复，不计入正式 181 用例基线。
- 首轮探针有两处测试构造/预期错误：压缩 keys 未按 DFU 规则去重导致槽位越界；Float 最小值误预期为拒绝，实际被接受为改变后的值。修正探针后重跑，未将探针错误计为项目缺陷。
- 正式高精度测试中的 near-1 反例有鉴别力；端点附近反例放在 data_version 中时，即使错误转换为端点也会因版本不等于 1 被拒绝，不能独立证明整数边界。本轮探针直接测试 strictInt，补足此审核证据。
- 正式压缩测试主要覆盖合法往返与编码拒绝，本轮补查压缩解码的拒绝及不调用校验器；建议将这些行为移入正式回归。
- 未启动客户端，未宣称 core、NBT/网络、渲染或设备能力已验证。审核范围内未发现其他确定缺陷。

## R39. R38 数值类型分支修复

**日期**：2026-10-19。修复 R38-N1：`exactInt` 不再对所有 `Number` 统一按 `toString()` 转十进制。

### R39.1 修复

| 项 | 修复方式 | 位置 |
| --- | --- | --- |
| R38-N1 Float/Double 数值改变 | `exactInt` 按 `Number` 实际类型分支：`Float` / `Double` 使用其**已表示的二进制数值**（`Float` 精确拓宽为 `double`）检查有限性、整数性与 `int` 范围；其它 `Number`（Gson 保留字面量的 `LazilyParsedNumber`、`BigDecimal`、整数类型）继续按十进制文本用 `BigDecimal` 精确检查。这样既修复 Float 的 `1.0000001E8` 文本问题，也不重新引入 R36-N1 的高精度字面量舍入 | [Codecs.exactInt / exactIntFromBinary / exactIntFromDecimalText](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java>) |

### R39.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**24 suite / 183 用例**，v2 为 **18 / 117**，无失败/错误/跳过；`git diff --check` 干净。

[DocumentCodecTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodecTest.java>) 由 11 例增至 **13 例**，新增：

1. `strictInt` 直接用例：`createFloat(100000008f)` → `100000008`（原实现得 `100000010`）；`createFloat((float) Integer.MIN_VALUE)` → `Integer.MIN_VALUE`；`createDouble(100000008d)` → `100000008`；`1.5f`、`5.0e9f`、`(float) Integer.MAX_VALUE` 拒绝；
2. 文档字段用例：`view.z_order` 使用 `JsonPrimitive(100000008f)` 时解码返回 `Accepted` 且层级为 `100000008`，不再被改写。

### R39.3 边界与风险

- 二进制分支只对 `Float` / `Double`；JSON 字面量仍是 Gson `LazilyParsedNumber`，继续走精确十进制路径，R36-N1 的反例不会回归。
- NBT 的浮点按**已表示的二进制数值**判断，符合“输入数值本身就是这个 double/float”的语义；不再尝试用最短文本反推。
- 仍未做：字段级结构诊断、NBT / 网络 Ops 往返、注册表能力与类型级校验钩子（见计划 §6.1）。

## R40. R39 修复复核

**日期**：2026-10-19。复核 R39 的 Number 类型分支修复与新增正式测试。**R38-N1 在原问题范围关闭，审核范围内未发现新增确定缺陷。** 本轮仅修改评审日志，未修改项目源码、正式测试或构建脚本，未新增隔离探针。

### R40.1 复核结论

| 项 | 证据 | 结论 |
| --- | --- | --- |
| Float 实际整数值保持 | [exactInt:103](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L103>)按实际 Number 类型分支；Float 精确拓宽为 double，[二进制检查:113](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L113>)先判断有限性、整数性与 int 范围，再转换。正式[直接 Codec 测试](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodecTest.java#L553>)确认 `100000008f` 和 float 形式的 int 最小值不被文本改写 | R38-N1 原两个数值反例关闭 |
| 文档字段不改写 | [文档字段测试](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/DocumentCodecTest.java#L580>)通过 Float JsonPrimitive 输入 `view.z_order`，Accepted 候选保留 100000008 | 原正常文档入口反例关闭 |
| 小数与越界拒绝 | 二进制分支的范围比较在强转前进行；正式测试拒绝 `1.5f`、`5.0e9f` 及 float 形式的 int 最大值（实际已舍入为 2147483648），同时接受合法 Double 整数 | 未发现截断、饱和转换或 int 边界误收 |
| 十进制精度与先前修复 | [十进制分支:126](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/codec/Codecs.java#L126>)保留 BigDecimal 精确检查，未将 Gson 字面量统一转 double；正式回归仍覆盖 near-1 高精度拒绝、叠加双向拒绝、压缩 JSON 合法往返及结果严重级不变量 | 未发现本次修复使 R34–R37 已关闭路径回归 |

### R40.2 实际验证与边界

- 执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL；按 UTF-8 核对报告：**24 suite / 183 用例**，v2 为 **18 / 117**，失败、错误、跳过均为 0。
- 本次审查集中于 R39 改动及其回归路径，没有重新运行断言旧缺陷现状的临时探针；旧探针不能作为修复后应继续通过的回归测试。
- Float/Double 的检查以传入值实际表示的数值为准，不恢复调用方在构造浮点值之前已经丢失的十进制精度；原始 JSON 字面量由另一分支保留精度，两种语义已区分。
- 结论仅关闭 R38-N1，不代表全部编解码或 P1 已完成。字段级结构诊断、NBT/网络 Ops 往返、规模限制、core 和客户端能力仍按既有计划延期；本轮未启动客户端或验证这些实现。

## R41. R1 表达式编译 / 求值契约交付

**日期**：2026-10-19。按计划 §6.1 的 R1 缺口交付**接口契约**（不含编译器 / 求值器实现）。设计与依赖方向保持：表达式包独立，求值输入在 `eval`。

### R41.1 交付

| 层 | 交付 | 关键约束 |
| --- | --- | --- |
| `api.animation.expression` | `ExpressionCompiler`、`CompilationResult`（Compiled/Rejected）、`CompiledExpression`（不透明句柄）、`ExpressionReferences` / `ExpressionReference` / `ExpressionReferenceKind`、`ExpressionDiagnostic` / `ExpressionDiagnosticCode` | 纯编译；失败不产生可求值句柄；按源码文本有界缓存；只暴露源码与外部引用，**不公开语法树**（§6.1）；不依赖 eval/block/edit/playback（§2.1 依赖方向） |
| `api.animation.eval` | `ExpressionEvaluator`、`ExpressionInputs`、`ExpressionSample` | 显式输入：上下文、可空 `RigId`、`t/p/wt`、`SpatialReadings`、随机种子、工作量/深度预算、`CurveSampler`；结果只有有限数值或结构化失败；`sample(name, fallback)` 仅在 UNKNOWN 时惰性求值；采集错误不可兜底（§6.1、§7.7、D18） |

### R41.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**25 suite / 189 用例**，v2 为 **19 / 123**，无失败/错误/跳过；v2 依赖门禁 0 违规；`git diff --check` 干净。

新增 [ExpressionContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/expression/ExpressionContractTest.java>) **6 例**：引用容器隔离与顺序、引用/区间/实参个数校验、诊断区间与整体覆盖、编译结果两分支不变量、`ExpressionInputs` 时间/进度/预算校验、`ExpressionSample` 不泄漏非有限值。

### R41.3 边界与风险

- **未实现**：编译器（词法/语法/常量折叠/内建函数表/缓存）、解释器（惰性 select、sample fallback、空间读数、TrackRef 机位上下文、预算记账）、`edit.Diagnostic` 包装层、依赖图与试算对编译产物的接入。
- 契约只规定语义要求，具体语法与内建函数版本仍由实现决定；改变内建语义必须提升格式版本（§6.1、§8.1）。
- `ExpressionInputs.progress` 约束为 `[0,1]`、`worldTime` 只要求有限；具体 `wt` 口径（沿用现有“一天进度归一化”）尚需在实现批次与设计确认。
- R1 未关闭：本批只交付契约，按“先接口后实现”等待审核；关闭条件是实现与边界测试通过。

## R42. R41 表达式契约复核

**日期**：2026-10-19。复核 R41 新增的 expression 编译/引用/诊断契约，以及 eval 的公式输入/结果与既有曲线采样接口。**发现两项 P2 公开契约缺口，建议在解释器接线前补齐；不将编译器、解释器本身延期计为缺陷。** 本轮仅修改评审日志，未修改项目源码、正式测试或构建脚本，未新增隔离探针。

### R42.1 发现

| 编号 | 级别 | 问题 | 证据与影响 | 补齐方向 |
| --- | --- | --- | --- | --- |
| R42-E1 | P2 | 帧级工作量和跨采样嵌套深度缺少显式传递/记账协议 | [ExpressionInputs:35](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionInputs.java#L35>)将 remainingWorkUnits 定义为本帧剩余预算，但 record 只有不可变 int 与最大深度；[ExpressionSample](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionSample.java#L26>)只返回数值或诊断，不返回消耗量；[CurveSampleRequest:24](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/CurveSampleRequest.java#L24>)没有共享预算或当前深度。上层无法通过公开结果扣减后续调用额度，公式 → TrackRef → 曲线公式也无法显式继承调用链预算与深度 | 定义独立于不可变事实的求值执行会话/共享预算，并贯穿 ExpressionEvaluator 与 CurveSampler；或提供完整的消耗回传及深度传递协议。明确帧级额度、单次根调用深度、缓存命中计费和失败消费规则，不能依赖未声明的全局/线程状态维持组合 |
| R42-E2 | P2 | 嵌套运行失败只有源码区间，缺少实际源码归属 | [ExpressionDiagnostic:23](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/expression/ExpressionDiagnostic.java#L23>)仅含 code/detail/start/end；[ExpressionSample.Failure:37](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionSample.java#L37>)没有源身份或内层 cause。上层包装 edit.Diagnostic 时仅知道入口表达式，无法可靠判断区间属于入口、函数体还是变量/被引用轨道公式；不能从自由文本 detail 恢复结构化定位 | 在 eval 失败包装中携带不可变源码身份/所有者及区间，必要时保留调用链或结构化 cause；expression 编译诊断仍可保持与文档无关。若只高亮调用点，必须明确区间映射规则并保留实际内层失败来源 |

**E1 场景**：同帧额度 100，两个根公式各消耗 80；首次返回 Value 后，上层拿不到已消费量，不能据返回值给第二次调用传入剩余 20。嵌套读取 TrackRef 时，曲线采样请求也收不到外层剩余量和当前调用深度。现有接口能描述单次上限，尚不能表达本帧共享额度与跨服务调用链。此处审核的是缺失的组合协议，不宣称尚未实现的解释器已经发生预算绕过。

**E2 场景**：入口 `f(2)` 长度 4，函数体为 `100 / (x - x)`。若失败诊断保留函数体除数区间 `[6,13)`，上层无法将其安全映射到入口；若直接换成入口调用区间，则丢失函数体定位。变量公式与 TrackRef 内层错误同理。已有 ChannelSample 可能携带完整对象诊断，转换为轻量表达式失败时也需规定如何保留来源。

### R42.2 其余结论与边界

- 编译成功/拒绝两分支、不透明句柄、引用容器防御性复制、区间与实参个数检查、有限求值结果均符合本批结构目标，未发现新增确定实现缺陷。
- expression 不反向依赖 eval/block/edit/playback 的方向合理；无须为修复上述问题公开 AST，或将文档/客户端类型放入 expression 包。
- 自定义函数可由求值器内部调用帧绑定实参，L3 可根据函数形参过滤名字引用；未将缺少公开参数帧或公开语法树另报问题。
- random、惰性 select、sample UNKNOWN 与 COLLECTION_ERROR 的区分目前仅为语义要求，6 个结构测试不证明这些算法已运行。
- R41 属于契约交付，不据此关闭计划中的 R1。两项接口补齐及编译、依赖绑定、解释器、预算与嵌套诊断运行测试仍需后续验证。

### R42.3 实际验证

执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL。按 UTF-8 统计：**25 suite / 189 用例**，v2 为 **19 / 123**，失败、错误、跳过均为 0。

本轮为源码、接口组合和正式测试审核；未编造解释器替身作为运行缺陷证明，未新增探针，未启动客户端。当前测试覆盖结构不变量，不覆盖共享预算累计、跨采样深度及内层源码诊断回传。

## R43. R42 两项契约缺口补齐

**日期**：2026-10-19。补齐 R42-E1（共享帧预算 / 嵌套深度）与 R42-E2（嵌套失败源码归属）；仍不实现编译器与解释器。

### R43.1 补齐

| 项 | 补齐方式 | 位置 |
| --- | --- | --- |
| R42-E1 共享预算 / 深度 | 新增 `EvaluationSession`：帧级总额度、已消耗、剩余、`tryConsumeWork`（不足不部分扣减）、`tryEnterDepth` / `exitDepth`（成对）。`ExpressionInputs` 以 `session` 替换 `remainingWorkUnits` / `maxDepth`；`CurveSampleRequest` 增加 `session`，公式 → `TrackRef` → 曲线公式必须传同一会话。记账规则写入契约：缓存命中计 0、失败不退回、深度 `try/finally` 配对、会话按帧创建且非线程安全、缓存键不含会话 | [EvaluationSession](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationSession.java>)、`ExpressionInputs`、`CurveSampleRequest`、`ExpressionEvaluator` |
| R42-E2 嵌套失败来源 | 新增 `ExpressionSource`（`Document` / `Channel(TrackId)` / `Layout(RigId)` / `Variable(name)` / `Function(name)`）与 `ExpressionFailure`（源码身份 + 本层诊断 + 由外到内的 `causes`，`innermost()` 取最内层）。`ExpressionSample.Failure` 改持失败链；入口区间与内层区间分别归属各自源码，不再需要从 `detail` 反解析 | [ExpressionSource](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionSource.java>)、[ExpressionFailure](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionFailure.java>)、`ExpressionSample` |

### R43.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**26 suite / 194 用例**，v2 为 **20 / 128**，无失败/错误/跳过；v2 依赖门禁 0 违规；`git diff --check` 干净。

新增 [EvaluationSessionTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationSessionTest.java>) **4 例**：帧额度跨调用共享且不足不部分扣减、零额度行为、深度成对与上限、非法配置/负工作量拒绝。[ExpressionContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/expression/ExpressionContractTest.java>) 增至 **7 例**：`ExpressionInputs` 改为校验会话；新增失败链来源/最内层定位测试。

### R43.3 边界与风险

- 仍**没有**解释器实现：会话如何实际记账、`TrackRef` 是否传同一会话、深度如何配对，只有契约与结构测试，不代表运行行为已验证。
- `EvaluationSession` 是可变对象：明确按帧创建、单线程使用；若将来需要并行预览，必须改为独立会话而不是共享。
- 缓存键排除会话：同帧两次调用即使预算不同也可命中缓存，这是刻意设计（结果身份与预算无关）；命中计 0 的具体实现仍需在解释器批次验证。
- `ExpressionSource` 目前覆盖 Channel / Layout / Variable / Function；叠加（O1–O3）与其它文档级来源待对应批次扩展。
- R1 仍未关闭：编译器、绑定、解释器、预算运行与嵌套诊断的端到端测试仍待实现批次。

## R44. R43 契约补齐复核

**日期**：2026-10-19。复核 EvaluationSession、求值/采样请求中的会话传递，以及 ExpressionSource / ExpressionFailure 与求值入口的连接。**R42-E1 的共享额度/深度传递入口在契约范围关闭；R42-E2 仅部分补齐，另发现预算失败缓存规则冲突，共两项 P2 待补。** 本轮仅修改评审日志，未修改项目源码、正式测试或构建脚本，未新增隔离探针。

### R44.1 修复状态

| 项 | 证据 | 结论 |
| --- | --- | --- |
| R42-E1 显式共享预算 | [EvaluationSession](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationSession.java#L50>)提供共享总量、已用/剩余、非部分扣减及深度进入/退出；[ExpressionInputs](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionInputs.java#L48>)与[CurveSampleRequest](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/CurveSampleRequest.java#L33>)均携带同一个会话；正式测试覆盖扣减、零额度、深度配对与配置拒绝 | 原无法跨服务显式传递额度/深度的接口缺口关闭；解释器实际消费、finally 配对和调用方传同一对象仍待运行验证 |
| R42-E2 失败归属 | [ExpressionFailure](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionFailure.java#L30>)已能保存本层来源、诊断和嵌套原因，变量及函数有具名来源 | 数据承载能力有进展，但入口来源及完整字段地址缺失，不能整体关闭，见 R44-N2 |

### R44.2 发现

**R44-N1（P2）：预算/深度失败依赖执行状态，不能按与会话无关的身份无条件缓存。**

[EvaluationSession:26](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationSession.java#L26>)规定所有失败缓存命中也计 0；[ExpressionEvaluator:34](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionEvaluator.java#L34>)明确缓存键排除执行会话；既有[EvaluationCache:8](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationCache.java#L8>)要求失败结果也必须缓存。与此同时，WORK_LIMIT_EXCEEDED 取决于剩余额度，DEPTH_LIMIT_EXCEEDED 取决于当前调用深度，均不是仅由文档、事实与时间决定的结果。

契约反例：同一快照、上下文、帧、轨道和时间，在额度不足的会话中得到工作量失败；如果按规则将失败缓存，之后相同身份但额度充足的调用会命中旧失败，无法重新求值得到合法数值。深层调用时触发的深度失败，也不能据此认定相同表达式在浅层入口必然失败。R43.3 对“不同预算仍可命中、结果身份与预算无关”的解释只适用于与执行预算无关的结果，不能覆盖上述失败。

建议将失败分类：缓存确定性的数值/领域失败；WORK_LIMIT_EXCEEDED、DEPTH_LIMIT_EXCEEDED 及其传播包装不进入上下文共享结果缓存，或明确限定在带足够执行状态身份的失败缓存内。仅把 session 对象加入键也不能区分同一会话的不同调用深度。保留普通缓存命中计 0 的规则，同时明确资源失败重试、预算消费和失败链包装的缓存边界。此项是契约冲突，不宣称缓存/解释器运行实现已发生污染。

**R44-N2（P2，R42-E2 残留）：求值入口缺少来源输入，现有 Channel/Layout 来源又不足以定位一段具体公式。**

- [ExpressionEvaluator:46](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionEvaluator.java#L46>)只接收 CompiledExpression 与 ExpressionInputs；后者没有 ExpressionSource、TrackId 或当前字段目标。[CompiledExpression](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/expression/CompiledExpression.java#L23>)仅暴露源码及引用，编译缓存按文本共享，不能代替调用位置绑定。同一机位两条通道使用相同源码时，求值器拿到的公开输入无法区分当前属于哪条通道；依赖隐式绑定状态会违背显式输入约束。
- [ExpressionSource.Channel:36](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionSource.java#L36>)只有 TrackId，不能区分该曲线的多个 KeyId；[Layout:45](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionSource.java#L45>)只有 RigId，不能区分 x/y/width/height，也不能区分布局曲线内的键。多个位置可以有相同源码及相同区间，返回后不能可靠反推唯一字段。

建议为每次求值显式传入源码位置描述或 BoundExpression：稳定对象身份、字段/布局分量、需要时的 KeyId，以及与调用上下文绑定的快照身份。编译产物继续按文本共享，不把作者位置写进全局编译缓存。嵌套进入变量、函数体或曲线键时派生新的来源，返回失败时保留各层独立区间；缓存取出的无调用者结果应在当前入口重新包装，避免继承其他调用者的来源链。失败链容器本身不能替代这一绑定通路。

### R44.3 实际验证与边界

- 测试启动最初两次因工具自动审核连接错误（TRANSPORT）未执行；随后重试成功执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL。
- 按 UTF-8 核对报告：**26 suite / 194 用例**，v2 为 **20 / 128**，失败、错误、跳过均为 0。
- 本轮结合源码、接口及正式结构测试审核，没有编造解释器替身证明运行缺陷；未新增探针，未启动客户端。
- 当前失败链测试手工提供 Channel/Function 来源，证明容器能保存这些值，不证明 evaluator 能从其参数确定来源；当前预算测试证明会话记账操作正确，不覆盖不同预算/深度的缓存策略。
- 除上述两项契约问题外，EvaluationSession 的扣减边界、非法配置拒绝、深度配对检查，以及失败链防御性复制未发现新的确定实现缺陷。R1 仍未关闭，编译、绑定、解释器、缓存与诊断端到端行为继续待实现验收。

## R45. R44 两项契约缺口补齐

**日期**：2026-10-19。补齐 R44-N1（资源失败缓存边界）与 R44-N2（求值入口来源绑定与字段粒度）；仍不实现编译器 / 解释器。

### R45.1 补齐

| 项 | 补齐方式 | 位置 |
| --- | --- | --- |
| R44-N1 资源失败缓存 | `ExpressionDiagnosticCode` 增加 `executionStateDependent()`（`WORK_LIMIT_EXCEEDED` / `DEPTH_LIMIT_EXCEEDED`）与 `cacheableInSharedCache()`（资源失败与 `INTERNAL_ERROR` 不可缓存）。`EvaluationSession`、`ExpressionEvaluator`、`ExpressionSample`、`EvaluationCache` 的记账/缓存规则统一：确定性失败按同一身份缓存，资源失败不进入会话无关缓存、允许用新额度重试；缓存键不含会话，但缓存身份必须含入口源码位置 | `ExpressionDiagnosticCode`、`EvaluationSession`、`ExpressionEvaluator`、`ExpressionSample`、`EvaluationCache` |
| R44-N2 来源绑定 | `ExpressionInputs` 新增必填 `source`：每次求值显式声明位置。`ExpressionSource.Channel` 增加可空 `KeyId`，`Layout` 增加必填 `LayoutField`（X/Y/WIDTH/HEIGHT）与可空 `KeyId`；同机位两条通道、同布局四个分量不再无法区分。缓存命中的结果按当前 `source` 重新包装，不继承其它入口来源链 | `ExpressionInputs`、`ExpressionSource` |

### R45.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**26 suite / 196 用例**，v2 为 **20 / 130**，无失败/错误/跳过；v2 依赖门禁 0 违规；`git diff --check` 干净。

[ExpressionContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/expression/ExpressionContractTest.java>) 增至 **9 例**：求值输入必须带来源、布局字段/KeyId 可区分、资源失败不可缓存（含 `NON_FINITE_RESULT` / `SAMPLE_UNKNOWN` 仍可缓存）。

### R45.3 边界与风险

- 仍是**契约层**：没有解释器按 `source` 建链、按 `cacheableInSharedCache` 过滤缓存或重试资源失败；这些规则只有类型与结构测试，不能宣称运行行为已验证。
- `EvaluationKey` 仍是曲线采样身份，不含字段级 `source`；若将来缓存布局公式结果，必须另建含 `ExpressionSource` 的键或按入口分别缓存（类型文档已写明）。
- `ExpressionSource` 仍只覆盖 Channel / Layout / Variable / Function；叠加、文档级派生等来源待对应批次。
- R1 未关闭：编译器、绑定、解释器、预算运行、缓存过滤与来源重包装的端到端测试仍待实现验收。

## R46. R45 契约补齐复核

**日期**：2026-10-19。复核 R45 的资源失败缓存分类、显式源码来源与字段/键身份。**R44-N1、R44-N2 均在既定契约范围关闭，未发现新增确定缺陷；R1 仍未关闭。** 本轮仅修改评审日志，未修改项目源码、正式测试或构建脚本，未新增隔离探针。

### R46.1 修复复核

| 项 | 证据 | 结论 |
| --- | --- | --- |
| R44-N1 资源失败缓存边界 | [ExpressionDiagnosticCode:76](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/expression/ExpressionDiagnosticCode.java#L76>)识别工作量/深度失败，[缓存分类:84](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/expression/ExpressionDiagnosticCode.java#L84>)同时排除 INTERNAL_ERROR。[EvaluationSession:32](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationSession.java#L32>)、[ExpressionEvaluator:34](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionEvaluator.java#L34>)与[EvaluationCache:12](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationCache.java#L12>)一致要求只共享确定性结果，资源失败允许用新额度重试 | 原无条件缓存资源失败的契约冲突关闭；实际缓存过滤与重试待解释器实现验收 |
| R44-N2 入口来源及字段身份 | [ExpressionInputs:45](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionInputs.java#L45>)显式携带必填 source，构造拒绝 null；[Channel:46](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionSource.java#L46>)带 TrackId/KeyId，[Layout:62](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/ExpressionSource.java#L62>)带 RigId/LayoutField/KeyId。编译文本仍可共享，求值来源独立绑定，命中按当前入口重包装 | 原无法区分入口、布局分量及键位置的类型/传递缺口关闭；调用方正确派生来源与重包装待运行验收 |

### R46.2 实现验收提醒（非新增缺陷）

- `ExpressionFailure.code()` 仅是本层码。共享缓存过滤应检查整条 causes 树：任意分支包含资源失败或 INTERNAL_ERROR，传播后的整体失败也不能进入共享缓存；不能只调用根码的分类方法。现有“只保存确定性结果”契约能够表达此行为，不因缺少树级便捷方法重开 R44-N1。后续补根可缓存/内层不可缓存、非最后分支失败及新额度重试测试。
- 缓存命中须按当前入口重新绑定外层来源，同时保留内层真正失败位置；不能把整条来源链都替换成入口。补同文本不同入口、不同布局分量/KeyId、嵌套失败命中重包装的运行用例。
- `EvaluationKey` 是整条曲线的采样身份；给定快照、轨道与时间可确定参与键，不必机械加入每个 KeyId 或布局分量。表达式/布局专用缓存应满足 ExpressionEvaluator 规定的来源身份，不直接假定现有曲线键可表达全部公式缓存。
- 当前新增测试验证 source 输入/非空、字段与键身份，以及枚举级缓存分类；没有解释器/缓存实现，因此不宣称已证明资源失败重试、整树分类或来源重包装运行正确。

### R46.3 实际验证

执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL。按 UTF-8 核对报告：**26 suite / 196 用例**，v2 为 **20 / 130**，失败、错误、跳过均为 0。

未新增探针，未启动客户端。编译器、绑定、解释器、预算记账、缓存过滤与诊断端到端测试仍在后续范围；本次关闭的是 R44 两项契约缺口，不等于 R1 或完整 P1 完成。

## R47. R2/R3/R4 契约交付（帧事实、客户端执行、能力）

**日期**：2026-10-19。按计划 §6.1 交付 **R2 `FrameFactsProvider`**、**R3 客户端锚点解析/采样采集注册**、**R4 类型能力服务**的接口契约；不含实现。

### R47.1 交付

| 项 | 交付 | 关键约束 |
| --- | --- | --- |
| R2 帧事实 | `FrameFactsRequest`（快照、维度、世界实例、游戏刻、partial tick、所需机位、共享会话）与 `FrameFactsProvider.prepare` | 一帧一次、只准备要求的机位、失败逐项落在 `SpatialResolution.Failed` / `SampleReading.Unknown` / `CollectionError`、不触发区块加载、采样节奏与表版本、`valid` 语义、预算沿用 `EvaluationSession` |
| R3 客户端执行 | `AnchorResolver` + `AnchorResolverRegistry`、`AnchorResolutionContext`（实体/玩家当前与上一帧读数）、`SampleCollector` + `SampleCollectorRegistry`、`SampleCollectionContext`（受控查询 + 会话） | common 注册类型 ID/Codec；客户端只注册执行部件；解析器/采集器缺失是**运行能力缺失**，不是解码错误；契约不引用 `net.minecraft.client` |
| R4 能力服务 | `TypeCapabilityService`（anchor / sample / track）与 `TypeCapability` / `TypeCapabilityStatus` | 三态：可用 / 类型未注册（解码错误）/ 实现缺失（可读不可完整播放）；扩展轨道无独立执行器，只有注册与否；能力缺失不改变文档解码与发布校验 |

### R47.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**27 suite / 199 用例**，v2 为 **21 / 133**，无失败/错误/跳过；v2 依赖门禁 0 违规；`git diff --check` 干净；主源码 **205** 个 Java 文件。

新增 [HostServiceContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/HostServiceContractTest.java>) **3 例**：能力三态与工厂不合并、`FrameFactsRequest` 时间/世界身份校验与机位集合防御复制、`EntityReading` 当前/上一帧必填。

### R47.3 边界与风险

- **没有实现**：`FrameFactsProvider`、任何 `AnchorResolver` / `SampleCollector`、`TypeCapabilityService` 的具体实现，以及客户端世界查询；解析器/采集器的客户端注册与加载时序未验证。
- `SampleCollectionContext` 的查询面目前只覆盖方块状态、光照、实体白名单属性与区块已加载；群系 / 天气 / 记分板等设计后续项需要扩展方法，当前不足以支撑全部“后续加入”的采样类型。
- `AnchorResolutionContext.EntityReading` 只给当前/上一帧值，插值规则由解析器实现；瞬移、维度切换与无历史的处理只有文字约束，无运行测试。
- 能力服务对 `track` 只区分注册/未注册；若将来第三方轨道需要客户端执行器，需要扩展状态而不是复用 `RUNTIME_IMPLEMENTATION_MISSING` 的模糊语义。
- 本批不涉及 `ReferenceAudioService`、事件执行器注册、通道描述注册表等后续 §6.1 项。

## R48. R5 网络播放请求契约

**日期**：2026-10-19。按计划 §6.1 交付 **R5**：网络播放动作、来源、协议版本、规模上限与可报告编解码入口；不含具体 `PlaybackProtocolCodec` 实现。

### R48.1 交付

| 契约 | 要点 |
| --- | --- |
| `PlaybackSource` | `Embedded(CameraAnimation)` 随包文档 / `Local(ResourceId)` 本地引用；不传选择器字符串，`Local` 由接收端资源服务解析 |
| `PlaybackAction` | `Start(source, rigOverrides, speed, loop, startTime)` / `Stop`；覆盖保留继承/显式禁用/替换三态；`Start#toRequest` 把线上动作转成本地 `PlaybackRequest`，`Embedded` 必须是同一文档实例，防止静默换档 |
| `PlaybackProtocol` / `PlaybackLimits` | 协议版本 `CURRENT_VERSION = 1`；文档字节、覆盖条目、总负载上限由宿主传入，不在协议里写经验默认值；上限必须 `maxTotal >= maxDocument` |
| `PlaybackProtocolCodec` | `encode` / `decode` 返回 `DataResult`（可报告失败：版本、未知类型、超限）；`streamCodec(limits)` 适配 MC 注册点，异常消息来自同一错误 |

### R48.2 实际验证

`./gradlew.bat --offline test --rerun --console=plain` → `BUILD SUCCESSFUL`：**28 suite / 203 用例**，v2 为 **22 / 137**，无失败/错误/跳过；v2 依赖门禁 0 违规；`git diff --check` 干净；主源码 **210** 个 Java 文件。

新增 [PlaybackProtocolContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackProtocolContractTest.java>) **4 例**：动作参数与覆盖容器、分支/来源区分、`toRequest` 的随包实例约束与本地解析路径、协议版本与规模上限校验。

### R48.3 边界与风险

- **没有实现**：具体 `PlaybackProtocolCodec`（锚点 `StreamCodec`、覆盖三态编码、版本与超限检查、文档 Codec 接入）以及收包后的校验/资源解析流程；没有真实字节往返测试。
- `PlaybackRequest` 仍不含协议版本与来源：网络动作先解码为 `PlaybackAction`，再在宿主侧解析来源并构造执行请求；若将来要求请求本身可直接序列化，需要另行设计而不是复用本批。
- `Local` 资源解析与“随包文档”的整份校验顺序未定；实现批次必须保证失败不发布部分文档。
- 规模上限目前只覆盖文档字节、覆盖条目与总负载；嵌套深度、单类型对象数量等文档级上限仍属 §8.2/§8.3 后续批次。
- 与 R2/R3/R4 无实现依赖；锚点参数编码复用已交付的 `AnchorType#streamCodec`。

## R49. R47 宿主服务契约复核

**日期**：2026-10-19。审核 R47 的帧事实请求/提供者、客户端锚点解析与采样采集注册，以及类型能力服务。**发现两项 P2 契约缺口，建议在宿主接线前补齐。** 审核期间工作区已新增 R48 网络请求契约，因此本次使用 R49 编号，不覆盖或宣称审核通过 R48。

### R49.1 发现

**R49-H1（P2）：帧事实请求不能完整表达本帧已确定的空间输入和实际需求。**

[FrameFactsRequest:35](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFactsRequest.java#L35>)只携带 snapshot、维度/世界实例、游戏刻/partial tick、Set<RigId> 与执行会话；其游戏刻明确是世界采集元数据，不是动画播放时间。请求没有动画时间，也没有已求值并应用覆盖的有效锚点、AimState 或每机位的锚点/目标需求。

[FrameFactsProvider:16](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFactsProvider.java#L16>)要求只准备实际需要的事实、不需要时返回 NotRequired；[FrameFacts.RigFacts:84](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFacts.java#L84>)又要求包含请求覆盖后的注视状态。现有公开入参无法完成这两项要求：

- 同一游戏刻定位动画 9 秒或 10 秒，注视状态分别为 A、B，请求无法表达区别；不能将游戏刻冒充动画时间。
- 相同快照使用 Inherit、Disabled 或 Replace(C)，以及 Local 机位无默认锚点但由 [RigPlaybackOverrides](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigPlaybackOverrides.java#L13>)提供锚点时，provider 都接收不到差异。仅增加动画时间仍不能解决覆盖问题。
- 活动机位引用不活动机位 B 的锚点空间读数，但不依赖 B 的注视目标时，Set<RigId> 只能表示“需要 B”，不能表达“只需锚点、不需目标”。解析 B 的全部配置可能引入非依赖失败，不加入 B 又缺少所需事实。

**建议**：传入宿主已按时间、覆盖和依赖分析生成的不可变准备计划，例如每 Rig 的有效锚点、有效 AimState 与逐项需求；或提供完整动画时间/覆盖/需求输入并明确由 provider 负责合成，避免两处重复决定状态。请求与结果必须绑定同一快照及本帧计划；不能读取隐式当前播放器、当前编辑机位或改写 snapshot 来弥补输入缺失。增加 9/10 秒定位、固定覆盖/禁用、无默认锚点覆盖、仅锚点依赖的契约及后续运行用例。

**R49-H2（P2）：锚点解析器必须返回带作用域的诊断，但没有诊断版本的绑定入口。**

[AnchorResolver:39](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/AnchorResolver.java#L39>)只接收 Anchor 与 AnchorResolutionContext，失败必须返回带 ERROR 的 [SpatialResolution.Failed](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/SpatialResolution.java#L34>)。[Diagnostic](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/Diagnostic.java#L41>)作用域必填，Document 需要文档 Revision，File 用于资源/解码诊断；但 [AnchorResolutionContext:31](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/AnchorResolutionContext.java#L31>)只提供世界身份、partial tick 和实体/玩家读数，没有快照、版本或诊断作用域。

同一个启动期注册的 resolver 分别为快照 R、R+1 或预览候选解析不存在的实体时，无法从参数决定正确的 Document 版本。读取当前 store 会破坏冻结快照绑定，伪造 File 作用域不符合运行文档错误用途；provider 事后重新包装也尚无约定，且 resolver 返回前已经必须构造完整 Diagnostic。

**建议**：在解析上下文/请求中提供与当前快照绑定的诊断作用域，或将解析器失败改为不绑定文档的轻量原因，由帧事实提供者按 request.snapshot 构造最终 Diagnostic。对象定位是可选字段，不因缺少 RigId 本身认定无法构造；本问题核心是必填作用域/版本的来源。补同一 resolver 在不同版本和候选中的失败归属测试。

### R49.2 其余结论与边界

- common 类型表与客户端执行表分离、能力缺失与类型不存在区分的方向合理；不因尚无 registry/provider 的实际实现另报缺陷。
- valid=true 允许 UNKNOWN，不能报告“未使用 UNKNOWN 必然阻断播放”。CollectionError 的全场终止是已有规则，不将其直接视为 R47 新问题。
- 采集查询目前不覆盖群系、天气、记分板等后续能力，日志已明确延期；不要求本批实现这些类型。
- [HostServiceContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/HostServiceContractTest.java>)验证能力三态、输入范围和实体读数非空，不证明时间状态/覆盖计划传递、逐项需求、诊断版本绑定或真实世界查询已运行。

### R49.3 实际验证

执行 `./gradlew.bat --offline test --rerun --console=plain`，BUILD SUCCESSFUL。按 UTF-8 核对报告：**28 suite / 203 用例**，v2 为 **22 / 137**，失败、错误、跳过均为 0。此数量包含工作区新增的 R48；R47 原交付记录的 27/199 保留为历史，不以本次完整测试代替 R48 的代码审核。

本轮仅修改评审日志，未修改项目源码、正式测试或构建脚本，未新增隔离探针；未启动客户端。上述两项是公开接口组合缺口，不宣称尚未实现的 provider/resolver 已发生运行错误。

## 4. 关键基线（全工程 / v2）

| 记录 | suite/用例（全工程） | suite/用例（v2） |
| --- | --- | --- |
| R7 | 14/105 | 8/39 |
| R12 | 18/131 | 12/65 |
| R15 | 19/142 | 13/76 |
| R18 | 19/146 | 13/80 |
| R22 | 20/151 | 14/85 |
| R24 | 21/156 | 15/90 |
| R26 | 21/157 | 15/91 |
| R28 | 21/158 | 15/92 |
| R29–R30 | 22/160 | 16/94 |
| R31 | 23/170 | 17/104 |
| R32 | 23/170 | 17/104（无源码改动） |
| R33 | 24/175 | 18/109 |
| R34 | 24/175 | 18/109（仅复核，无源码改动） |
| R35 | 24/178 | 18/112 |
| R36 | 24/178 | 18/112（仅复核） |
| R37 | 24/181 | 18/115 |
| R38 | 24/181 | 18/115（仅复核；另有 4 个隔离探针） |
| R39 | 24/183 | 18/117 |
| R40 | 24/183 | 18/117（仅复核，无新增探针） |
| R41 | 25/189 | 19/123 |
| R42 | 25/189 | 19/123（仅契约复核，无新增探针） |
| R43 | 26/194 | 20/128 |
| R44 | 26/194 | 20/128（仅契约复核，无新增探针） |
| R45 | 26/196 | 20/130 |
| R46 | 26/196 | 20/130（仅契约复核，无新增探针） |
| R47 | 27/199 | 21/133 |
| R48 | 28/203 | 22/137 |
| R49 | 28/203 | 22/137（仅复核 R47；测试含 R48，无新增探针） |
| 审核 R50（提交 R48） | 28/203 | 22/137（另有 3 个隔离探针，不计正式基线） |
| 提交 C1（R49-H1/H2） | 28/209 | 22/143 |
| 提交 C2（R6/R7/R8） | 31/214 | 25/148 |
| 提交 C3（R51-N1） | 31/214 | 25/148 |
| 提交 C4（R9–R12） | 33/218 | 27/152 |
| 提交 C5（停路质量 + 编码成功值） | 33/219 | 27/153 |

其余历次命令、测试数与原始论据留归档。本次凝练保留R编号和证据，不把历史错误论证回填为当前规则，也不重开已关闭缺陷；新需求另行分期。
