# P1 审核与复核记录

> 更新：2026-10-26。基线为 **`4a08774` 加工作区 C22**。R68 在原反例范围关闭 R67 两项修复；R69 对已实现部分作整体实现/性能审查，确认 **1项P1、4项P2** 待修，另记录结构性成本和条件优化。当前正式全量70 suite/631用例，v2 64/565，失败/错误0、跳过1；测试通过不表示无缺陷或完整游戏验收。
>
> 本文维护审核编号、问题证据与关闭边界；当前施工清单见[P1实施与验收](<P1-contract-plan.md>)，行为见[主设计](<../design-animation-editor-framework.md>)，导航见[文档索引](<../README.md>)。提交C与审核R独立编号；下次审核从 **R70** 继续。本轮整理保留有效历史结论，完整早期叙述由Git历史追溯，不把历史“待实现/未提交”当当前状态。

## 1. 审核索引与关闭口径

| 审核 | 对象 | 有效结论 |
| --- | --- | --- |
| R1–R7 | 键类型、时间、性能、标记/时长 | 数值/原生状态分工保留；有限性/无变化派生修复。Stream孤立倍数、类型绝不可合并、标记必须退出时长的旧结论被撤回 |
| R12 | 早期四项契约修复 | 同store单调Revision、sampleTime缓存身份、不可变消息、显式诊断作用域在原范围关闭 |
| R15/R18/R20/R22 | playback、十一协议、storage | 快照/事件顺序/预算入口/线程/单资源报告等契约闭合，不等于当时运行实现完成 |
| R24/R26/R28/R30 | editor与工具契约 | 草稿更新/open入口、冻结快照与捕获配置等原缺口关闭；UI/算法随后分期 |
| R32/R34–R40 | 注册表选型与Codec | O+B已裁决；叠加双向拒绝、精确整数、扩展泛型、压缩JSON反例关闭 |
| R42/R44/R46 | 表达式契约 | 共享会话、失败来源、资源失败缓存边界等原缺口关闭 |
| R49/R50/R51–R53 | 宿主/网络/扩展/事实绑定 | 有效空间计划、诊断作用域、快照引用绑定关闭；Void成功值后由C5/R54关闭 |
| R54 | C5 `7095284` | 停路零目标/几何及非空Unit成功值原范围关闭；33/219，v2 27/153 |
| R55 | `95c3dda` | 九项问题，见§2；当时未通过 |
| R56/R57 | C6+C7 | 八项原反例先关闭，形参泄漏残留由C7/R57关闭；60/542，v2 54/476 |
| R58–R65 | C8–C15拟定逐批复核建议 | 未逐条独立执行，后纳入R66/R67/R69合并核查；不拆分共享测试为多次证明 |
| R66 | C8–C16，`391946a` | 四项P2，见§3；68/611，v2 62/545 |
| R67 | C17–C21，`4a08774` | R66-N1–N3关闭；N4数值标签关闭但同名字段残留；新增草稿P1，见§4；70/628，v2 64/562 |
| R68 | C22工作区 | 草稿旧候选与NBT同名字段原反例关闭；恢复异常加固逻辑成立，真实线程边界保留；70/631，v2 64/565 |
| R69 | 同一C22工作区整体审查 | 五项新发现及性能审查，见§6；未修源码，不代表无条件通过 |

“原范围关闭”只表示已列触发及对应修复成立；其他边界不自动验收。历史R1–R12剩余契约编号与审核R编号不是同一套。已关闭问题不因新发现自动重开。

## 2. R55–R57：首轮core实现的九项问题

| R55原问题 | 修复与最终关闭范围 |
| --- | --- |
| 启动准备成功前先换档 | CorePlaybackService先准备候选、失败不改文档/Revision/旧播放状态；R56原反例关闭 |
| 循环先取模丢完整周期事件 | 保留未取模推进终点，姿态取相位、事件走完整区间；R56关闭，长时float精度边界保留 |
| Local缺轴用世界基础值后再次叠加锚点 | 先把基础姿态逆变换到文档参考系，逐轴补全后正变换；R56关闭，真实数值/宿主边界待验 |
| 跟随掩码与请求覆盖未生效 | 使用有效掩码同时控制参考系位置和旋转正逆变换；R56关闭 |
| 避障覆盖不生效 | 请求优先，关闭不调用resolver，启用使用有效margin；R56关闭，真实碰撞未接 |
| 依赖环未进统一校验 | C6接入完整图与符号/布局根，R56发现形参泄漏；C7按词法作用域修正，R57关闭原漏环，不等于复杂度最优 |
| 同轨重复KeyId可发布 | 数值/状态构造与校验拒绝，后C13扩至框架可见全文档KeyId；R56原反例关闭 |
| 通道/布局wt相差20倍 | 两者取WorldTime.progress，dayTime/dayLength由宿主提供，非固定24000；R56关闭，主设计已于本次整理同步 |
| 换档会话旧草稿/选择/交互残留 | DocumentListener换档通知、CoreEditorSession清理；R56正常通知路径关闭，R69另发现监听异常可中断清理 |

R56-H1精确反例：`T=Formula("f(1)")`、`f(x)=v`、`v=Formula("x")`、全局`x=TrackRef(T)`。旧图把全局x误作函数形参，实际求值形成T→f→v→x→T。C7对变量使用空locals、被调函数仅自身形参、实参留调用方作用域；图与统一校验现在报告T自环，无环对照指向B得到{B}。图/evaluator的对应测试通过；R57独立clean全量60/542，API386 class的jdeps禁止依赖0。C8补了实参与被调全局分别指向A/B的对照，避免集合去重掩盖作用域。

## 3. R66：C8–C16合并审查（2026-10-26）

**基线`391946a`，当时确认四项P2，记录时未修；后续关闭见§4/§5。** 不将客户端/renderer/权限等明确延期报为缺陷。

| 编号 | 原反例、根因与建议 | 后续 |
| --- | --- | --- |
| R66-N1 | [接管释放](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CoreCameraControlClaims.java>)的每表ID从0开始，仅Claim.equals；A/B同owner/priority首令牌相等，A.release(B令牌)移除A；精确复制同样成功。须按原对象/登记身份验证 | C17/R67关闭 |
| R66-N2 | [烘焙](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/edit/CoreEditTools.java>)用double ceil后转float，0→0.3、步0.1生成[0,.1,.2,.3,.3]并Ready；maxKeys4又误拒绝。须统一严格递增时间计划、终点一次 | C17/R67关闭 |
| R66-N3 | 捕获用无ObstacleResolver工厂走完整prepareRig；仅启用obstacle就Rejected。规范捕获注视后、避障前旋转，不能依赖碰撞查询 | C17/R67关闭；旋转一致正式断言建议保留 |
| R66-N4 | [NBT定位](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtStructureFieldPath.java>)只认IntTag；合法DoubleTag(1)版本+非法name，报Not a string却定位data_version。须与strictInt数学语义一致且不让无关同名字段抢路径 | C17修数值，C22/R68修已列同名字段 |

R66实际重跑compileJava/test，无clean：68/611，v2 62/545，失败/错误0、跳过1。四项用构建目录独立探针复现，不计正式用例；API386 class禁包字节扫描0，未称jdeps。旧探针可能被clean清理，正文输入/输出是持久证据。接收/UI编排/能力/KeyId在核对范围未见其他确定缺陷，不追认R58–R65全部建议。

## 4. R67：C17–C21复核（2026-10-26）

**基线`4a08774`。R66-N1–N3原范围关闭，N4部分关闭；新增草稿P1与同名字段P2残留。** C18枚举、C19手动Timer调度、C20执行编排当时核查未见其他确定缺陷，不能扩大为真实线程/场景验收。

### 4.1 R67-N1（P1）：非法矩形文本沿用旧候选

旧[CoreDraftTextParser](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreDraftTextParser.java>)只检查double有限后转float；会话先写fieldTexts再换candidate。事务width先输入2再输入1e100，转Infinity抛异常后原文为1e100、hasCandidate=true、诊断空、candidate仍width2；commit Applied并移除事务，当前原文丢失。x/y/height同路径。

验收要求：float转换后有限检查，任何解析/构造失败清候选并给ERROR，保留原文、commit拒绝且文档不变；改正后可提交。C22/R68原范围关闭，见§5。

### 4.2 R67-N2（P2，R66-N4残留）：同名未知字段抢定位

合法文档加入根`update_every_frames="unused"`或`extension={data_version:"unused"}`，DocumentCodec仍Accepted；仅把根name改IntTag(1)，NBT返回Not a string，fieldPath却为无关字段。findInvalidInteger先于消息解析、仅按名全树递归，违反有证据定位。C17修合法Double/Long反例有效，但该残留仍存在；C22限定结构后由R68关闭。

R67实际重跑compileJava/test：70/628，v2 64/562，失败/错误0、跳过1；没有修改正式源码/测试。恢复提供者抛异常导致周期停调度当时只是候选，执行探针被自动审批审查拒绝，未运行、不计发现；后C22加入异常保护。正式测试未验证真实调度线程与关闭竞态。

## 5. R68：C22修复复核（2026-10-26）

**基线`4a08774`加未提交C22。两项已列原反例关闭，未发现该补丁引入的确定回归。**

| 项 | 证据与关闭边界 |
| --- | --- |
| R67-N1 | [float检查](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreDraftTextParser.java#L274>)在转换后拒绝非有限值；[会话兜底](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreEditorSession.java#L237>)清候选/旧诊断、写ERROR。正式测试与隔离复测x/y/width/height：溢出拒绝、原文保留、Revision不变、改为3后可提交 |
| R67-N2 | [定位结构](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/NbtStructureFieldPath.java#L118>)仅根data_version、rigs[i].view.update_every_frames、samples[i].resample_every_ticks；原根/extension反例不抢路径，未知真实位置返回空。不是全部DFU路径追踪证明 |
| 恢复加固 | [异常返回](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/storage/CoreRecoveryScheduler.java#L126>)复位capturing，不把提供者/捕获异常交给周期任务；正式手动Timer测试验证后续再捕获。未验证真实executor周期、并发close或同一资源/快照原子绑定 |

独立执行compileJava/test --no-build-cache --rerun-tasks，无clean：70/631，v2 64/565，失败/错误0、跳过1。隔离探针不计正式用例；未改正式源码/测试，未重跑jdeps。本节补记此前已向用户报告的复核结论，不把它伪装为本轮另跑一次全量。

## 6. R69：已实现部分的整体实现与性能审查（2026-10-26）

**基线仍为`4a08774`+C22；本轮不修源码、不迁移接口，仅审核并整理文档。结论：五项确定问题待修（1项P1、4项P2），纯内核即可证明；另有局部优化、资源风险与真实游戏待验。** 审查覆盖document/edit/validation、expression/eval/path/playback、storage/恢复、会话及扩展边界。没有承诺穷尽所有缺陷，不把“下一步进入游戏”当纯内核问题的延期理由。

### 6.1 R69-N1（P1）：发布监听异常导致已发布却无回执，跳过其他通知与换档清理

[CoreAnimationDocumentStore:283](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/document/CoreAnimationDocumentStore.java#L283>)先更新快照/历史，再调用notify；[监听循环:308](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/document/CoreAnimationDocumentStore.java#L308>)未逐listener隔离异常。一个监听抛RuntimeException后，submit向调用者抛异常，但文档已发布；后续onDocumentChanged及onSessionSwitched不执行。[延迟队列:333](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/document/CoreAnimationDocumentStore.java#L333>)调用submitInternal也在完成回调try之外，可能跳过回调/后续队列。

实际探针输出：submit threw=listener，但文档after、Revision1、laterChanged=0；submitAfterNotifications发布async但completionCount=0；switch更新Revision3但switchCleanups=0。这是成功发布/观察协议及恰好一次完成的破坏，不要求回滚宿主副作用。

验收：逐监听异常隔离、单独报告，继续必要换档清理；发布成功仍给Applied，不回滚快照；排队每项完成恰好一次且一个失败不阻断余项。补监听异常发生在变更、换档、排队提交和完成回调的对照。[隔离探针](<../../build/review-core-readonly/CoreStoreProbe.java>)。

### 6.2 R69-N2（P2）：采样类型校验器收到WorldSample而非参数T

[UnifiedDocumentValidator:162](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidator.java#L162>)把sample传入TypeValidator；[SampleType<T extends SampleArgs>](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/registry/SampleType.java#L37>)声明validator参数为T。注册正常TypeValidator<Args>，对合法Args(1)校验，bridge cast在进入钩子前抛ClassCastException，safeValidate包装ERROR，合法文档被拒绝。

实际输出validator calls=0，诊断含WorldSample cannot be cast to Args；无需raw type或不合规插件。验收：传sample.args()，保持SampleId/owner/字段上下文，合法钩子被调用一次、非法参数保留原诊断、异常仍整体拒绝。建议定位到args字段。[隔离探针](<../../build/review-overall/SampleValidatorProbe.java>)。

### 6.3 R69-N3（P2）：距离比例被直接当曲线参数，路径位置错误

[CorePathSampler:135](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/path/CorePathSampler.java#L135>)仅计算段总长，local=remaining/length后直接at(local)，没有段内累计弧长→t反查。[帧准备:446](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactory.java#L446>)使用该结果。

共线Bezier P0=P1=0、P2=P3=10，单段总长恰好10；ABSOLUTE(2.5)/PERCENT(.25)均返回x=1.5625但distance声称2.5，正确x=2.5。共线单调路径排除64段近似长度误差解释。

验收：每段保存累计弧长样本，按距离反查/插值t；跨段可保存累计长度二分；测25%/75%、非匀速Bezier、Catmull-Rom内部点、零长/多段，距离语义与位置一致。不得只调细分数掩盖错误。[路径/事件探针](<../../build/review-c22/CoreAuditProbe.java>)。

### 6.4 R69-N4（P2）：事件预算在全部临时候选展开与排序后才检查

[appendRange:252](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/PlaybackEventExpander.java#L252>)扫描全部键并构造matched，排序后于:270才检查events.size>maxEvents；collectTimelines也复制/排序全部轨道键。maxEvents=1、100000个匹配键，最终失败只输出2项前已构造全部候选。本次JDK线程分配观测3,682,664字节，仅佐证分配存在，不作固定性能倍数。

K个键、I区间、每区间M命中，当前初始化O(Σkᵢlogkᵢ)/O(K)，重复扫描O(IK)，排序O(ΣMᵢlogMᵢ)，预算拒绝前临时空间O(K+M)，不受输出预算B限制。契约要求展开/生成时限制，不只是拒绝输出部分列表。

验收：在候选超出剩余额度时立即失败；更好使用已排序每轨区间二分、小根堆归并，保持周期/同刻顺序。区分SPI读取成本与框架自身候选分配，预算失败测试要观测展开量而非只看Result。[同一探针](<../../build/review-c22/CoreAuditProbe.java>)。

### 6.5 R69-N5（P2）：合法共享DAG使同步依赖构图指数重复展开

[CoreDependencyGraph:302](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java#L302>)逐引用解析，[变量展开:351](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java#L351>)仅visiting栈防环、退出删除，没有已完成状态/闭包memo；每次还用无缓存DefaultExpressionCompiler重新解析。UnifiedDocumentValidator L3同步调用该图，运行EvaluationSession额度不能保护构图。

合法无环`v0=1; vi=Formula("v(i-1)+v(i-1)")`，输入O(n)，末根T(n)=2T(n-1)+O(1)。隔离探针仅把Builder内部变量HashMap换为行为等价计数Map，运行原build：

| n | 变量总数 | 变量get次数 | 诊断数 |
| --- | --- | --- | --- |
| 8 | 9 | 1,004 | 0 |
| 12 | 13 | 16,356 | 0 |
| 16 | 17 | 262,108 | 0 |

次数精确符合`2^(n+2)-2n-4`，不以未预热耗时推导速度倍数。这是算法复杂度缺陷，不是仅凭“缺缓存”猜热点。

验收：一次构图用节点完成状态/依赖闭包、编译结果复用，保留C7词法作用域和正确环诊断；重复依赖工作受节点/边和必要闭包输出约束，不按展开路径指数增长。对共享DAG、真环、函数参数/全局变量分别回归。[计数探针](<../../build/review-c22/DependencyDagProbe.java>)。

### 6.6 性能成本、条件优化与风险分级

以下不额外计为五项P级缺陷，优先级见实施文档§5。

| 范围 | 证据/成本 | 建议与限制 |
| --- | --- | --- |
| 多键缓动 | CoreEditTools每选键2个op，Applier逐op重建N键曲线；M选键至少Ω(MN)复制/扫描 | 一次最终ReplaceKeys；不声称TimSort每次必为NlogN |
| 索引 | 每帧建SnapshotTrackIndex，但lookup绑定document::curveTrack仍线性；S次采样O(ST) | 快照单槽复用索引并真正用于查询；不永久保留历史 |
| 构图查轨 | 每T轨道线性document.track，空轨仍O(T²) | 构图局部ID map，无须全局缓存 |
| 路径表 | 每sample重建64段长度，选段时重算长度 | 正确弧长表顺带保存段长和累计值，有界复用 |
| duration/符号/样本 | 公式端点重复扫duration；变量/函数/样本每次线性查名字 | 快照绑定派生表；workQuota是抽象工作量，不是CPU时间硬限 |
| NBT列表 | entry/list逐文件readAllBytes后hash，list(0)仍读全部，元数据入口不走载荷maxBytes | 流式hash减少峰值数组；仍需完整读取，不能承诺总IO有界 |
| 编译缓存 | 默认256条，同锁解析，多个服务各建一份；4097字符失败输入仍作为完整key保留 | 超长输入绕过/总字符预算；共享无来源编译产物，诊断本次包装 |
| 预览 | 每preview重新apply完整草稿，尚无代际缓存 | 按base snapshot identity+draftGeneration缓存；只构造预览，不能冒充统一校验通过 |
| 运行结果缓存 | 目前仅API无EvaluationCache实现，TrackRef可能重复求值 | 先定random/预算/资源失败/来源链，再做确定性缓存 |
| AST深度 | 二元AST递归未进入会话maxDepth，只有变量/函数/TrackRef进入深度；长度4096仍有限栈风险 | 未复现StackOverflow，保留结构深度/迭代遍历风险，不列P级 |
| 调度生命周期 | closed/started/句柄为普通字段，发布线程和ScheduledTimer线程无统一同步 | 明确owner线程或同步生命周期，补可控close/onPublished交错；C22异常返回修复不代表并发关闭通过 |
| 已知内存边界 | 历史仅64项、恢复仅16槽，无完整大小/保留期淘汰 | 保留施工项，不重复当新缺陷，也不称已满足字节预算 |

### 6.7 改名能力讨论结论

当前强制ExtensionTrack.withName没有确定性能错误，也不能把过去强制契约误报为违反“可选能力”。推荐讨论独立非泛型RenamableTrack mixin，不extends sealed AnimationTrack：让支持者显式实现、协变返回原类型，无能力明确拒绝；统一入口检查返回ID/type/name/class，再按原owner替换、store校验。接口不能替未知类型自动复制载荷，不用NamedTrack包装器。若产品坚持所有名称都必须可改，维持强制方案合理；需第三方不可改类时才考虑typed注册适配器。方案/迁移测试见[主设计§3.1.3](<../design-animation-editor-framework.md#313-轨道改名能力已确认方向可选能力尚未实现>)及实施文档§3。R69 当时只建议、未改源码；该方向后经 D37 确认，仍未迁移。

### 6.8 实际验证与证据限制

本轮独立执行：

```powershell
.\gradlew.bat --offline compileJava test --no-build-cache --rerun-tasks --console=plain
```

BUILD SUCCESSFUL，无clean，编译/测试任务重跑；**70 suite/631用例，v2 64/565，失败/错误0、跳过1**（Windows符号链接权限）。探针不计正式用例，证明缺陷现状不等于修复通过。未启动客户端，不作真实帧率/显存/兼容性结论。

JDK25 jdeps --ignore-missing-deps -verbose:class -filter:none检查 **387个API class**，旧根、net.minecraft.client、v2 core/editor实现的禁止类型依赖0；editor.spi→internal为0。本轮未宣称全部core/editor相互边界重验。输出在[API依赖证据](<../../build/review-overall/api-jdeps.txt>)与[SPI依赖证据](<../../build/review-overall/spi-jdeps.txt>)，临时文件可被clean清理。

本轮仅生成build隔离证据及整理文档，保留用户原有C22源码/测试改动。git diff --check与文档链接检查见整理收尾；Mermaid未修改结构语法，不把旧parse成功视作本轮重跑。

## 7. 审核与文档维护规则

- 每项关闭写清原输入、输出、修复位置与独立证据；施工方自检、独立审核、游戏验收分开。
- R69五项是当前待修清单；其他成本建议先证明调用频率与规模，再决定优化，不承诺倍数。
- 真实游戏矩阵见实施文档§5.4；尚未实施的renderer/面板/叠加/音频不写成只差运行测试。
- 主设计同步行为，实施文档维护状态和顺序，本文维护证据和编号；历史原文在Git，不恢复多份互相矛盾待办。
- 构建目录探针可能被清理，正文保留可重建反例。新审核从R70继续，提交编号不由审核自动推进。
