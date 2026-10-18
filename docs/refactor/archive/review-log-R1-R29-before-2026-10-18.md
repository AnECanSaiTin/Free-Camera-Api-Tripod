# 历史归档：R1–R29 凝练前记录

> 非当前施工指令。当前状态见[复核日志](<../review-log.md>)；保留被修订结论与原始证据。

# 契约复核日志（凝练版）

> 更新：2026-10-18。当前施工边界见[P1 计划](<../P1-contract-plan.md>)；设计规则见[主设计](<../../design-animation-editor-framework.md>)。本日志只保存结论、证据与复核范围。
> 最新复核见 R29：R28 的 T1/T2 已补（缓动/批量输入快照绑定，捕获覆盖/左侧模式/基版本一致）。**不宣称 core 存储、播放、事件、仲裁、渲染、编辑器 UI 或工具算法已实现**。
> 原始 R1–R14 正文、微基准表及历史被推翻论证保存在[历史归档](<review-log-R1-R14-2026-10-18.md>)。历史不作施工指令，编号保留可追溯；后续新发现追加编号，实施记录不自动等于复核通过。

## 1. 当前结论

| 事项 | 结论 |
| --- | --- |
| 两种键 | 数值键 NumberSource + 可选 STEP/LINEAR/HERMITE；状态键原生值 + STEP。保留具体类型，不禁止共享接口/基础设施 |
| 性能 | 不以 Stream 写法判热点；孤立替身微基准不能证明稳定倍数或未来帧时间。A1/A4 已清理，A2 按证据，A3 待实现 |
| 标记 | 无 Tick/指令/发声直接副作用，仍按现设计贡献 duration；A6 改规则需另行裁决 |
| 文本与诊断 | LocalizedMessage 封闭不可变树；Document/File 显式作用域，禁止 File 对象定位/Fix，修复版本匹配 |
| playback | 声明可取姿态；帧快照和合成设置冻结；事件按发生顺序且绑定快照；已呈现画面带采样身份 |

当前待办仅在[计划 §6](<../P1-contract-plan.md#p1-follow-up>)维护：A2 条件缓存、A3 绑定实现、A6 产品裁决及各 core 运行验证。A0/A1/A4/A5、A7 的注释/时长测试已完成；A7 事件副作用验证延期。

## 2. 历史编号摘要（R1–R14）

各条的核查日期、原始源码位置与完整论证见[归档](<review-log-R1-R14-2026-10-18.md>)；本表只保留最终解释，避免重复已失效指令。

| 编号 | 日期 | 原主题 | 最终状态 / 修订关系 |
| --- | --- | --- | --- |
| R1 | 10-17 | 数值键与状态键 | 分工保留；“不可合并”“无需修改”由 R4 修订 |
| R2 | 10-17 | Stream 性能 | 当前未接线；绝对收益/未来零影响由 R5 撤回，不以旧测量作门槛 |
| R3 | 10-17 | marker 时长 | “必须退出/必然冻屏”论证由 R6 修订，未改变设计 |
| R4 | 10-17 | 时间有限性与分工修订 | NaN 回归已修、边界测试在 R7 补齐 |
| R5 | 10-17 | 索引/缓存/无变化派生 | A1/A4 落地；A2 条件实施，A3 仅契约，不能保证 Optional 零分配 |
| R6 | 10-17 | 标记口径修订 | 默认仍贡献时长，不触发事件不等于不参与派生时长 |
| R7 | 10-18 | A0/A1/A4/A5/A7 实施 | 时间边界、遍历、withRig、命名及标记注释/时长测试落地；运行事件副作用仍延期 |
| R8 | 10-18 | document/edit 交付 | 只交付契约，不代表 store/执行器完成；Q13 接受包循环 |
| R9 | 10-18 | eval 交付 | 索引/事实/上下文/采样结果/姿态/图/缓存接口，算法与绑定延期 |
| R10 | 10-18 | C1–C4 第一轮实施 | C1/C2 保留；Component.copy 和可空版本方案不足，由 R11 替代 |
| R11 | 10-18 | C3/C4 深层修复 | 改不可变消息树及显式作用域；R12 复核关闭 |
| R12 | 10-18 | C1–C4 复核 | 四项契约问题关闭，运行实现仍另验 |
| R13 | 10-18 | playback 交付 | 后续发现五项契约缺口，R14 实施修正 |
| R14 | 10-18 | playback 五项修复 | 实施完成，本轮 R15 复核关闭 |
| R16 | 10-18 | 后续协议 1–5 实施 | 提交标签/重入、编辑目标、试算报告、显式采样、世界身份落地 |
| R17 | 10-18 | 后续协议 6–11 实施 | 空间三态、启动/接管协议、旧帧身份、ResourceId、消息渲染契约落地 |
| R18 | 10-18 | 十一项协议核对 | 契约方向成立；bind/预算/线程/解码等部分项待补 |
| R19 | 10-18 | storage 契约与 R18 契约补齐 | 实施记录：资源/恢复契约及绑定/预算调整；不等同复核通过，见 R20 |
| R20 | 10-18 | storage 与 R18 补齐复核 | 2 项 P2 残留及 1 项 P2 契约澄清；表版本入口和准备预算参数已补齐 |
| R21 | 10-18 | R20 三项修正 | 实施记录；引用同源、接管主线程约束、单资源报告，复核见 R22 |
| R22 | 10-18 | R21 修复复核 | S1–S3 在当前契约范围关闭；无新增确定缺陷，运行实现仍另验 |
| R23 | 10-18 | editor 契约 | 实施记录；会话/视图/交互/后端类型交付，审查见 R24 |
| R24 | 10-18 | R23 editor 复核 | 2 项公开契约缺口：草稿/预览输入链路与后端打开钩子；待修正 |
| R25 | 10-18 | R24 E1/E2 修正 | 实施记录；EditDraftState、草稿更新入口、后端 open 钩子，复核见 R26 |
| R26 | 10-18 | R25 修复复核 | E1/E2 原入口缺口在契约范围关闭；保留后端打开前置条件的非阻断澄清 |
| R27 | 10-18 | EditTools 契约 | 实施记录；缓动/批量/烘焙/捕获及结果类型交付，复核见 R28 |
| R28 | 10-18 | R27 工具契约复核 | 2 项 P2：缓动/批量输入快照绑定，捕获覆盖来源及显式左侧选择；待修正 |
| R29 | 10-18 | R28 T1/T2 修正 | 请求快照、AimCaptureMode/override/baseRevision；待复核 |

### 2.1 C1–C4 最终方案（R12）

| 编号 | 原缺陷 | 最终核查 |
| --- | --- | --- |
| C1 | 换档重置版本可使旧请求重新匹配 | Revision / SessionSwitch / Store 一致要求同 store 继续递增；core 仍需测旧请求拒绝 |
| C2 | 同帧不同时间采样命中同键 | EvaluationKey 增 sampleTime，有限非负、负零正规化；同帧 t0/t1 不等 |
| C3 | Component 及嵌套子组件/翻译参数泄漏 | LocalizedMessage / Argument sealed，record 叶子不可变、列表防御性复制；消息与全部标签不保存 Component |
| C4 | 无 Fix 对象诊断缺版本 | DiagnosticScope.Document 强制版本；File 不许对象 ID/Fix；Fix 与 Document 版本一致 |

源码：[版本](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/Revision.java>)、[缓存键](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationKey.java>)、[消息树](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/LocalizedMessage.java>)、[诊断](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/Diagnostic.java>)。

R12 的非阻断测试空白已在 R13 补充：消息输出列表不可修改、多层嵌套输入隔离、Document(null) 拒绝、无 Fix 文档对象诊断合法分支。消息渲染、异步作用域与候选绑定未实现，不因结构测试通过而关闭运行验证。

## R15. playback 五项修复复核关闭

**日期**：2026-10-18。**性质**：代码与测试复核，不是新产品裁决。核查范围为第五批已列契约；时间推进、仲裁、事件执行与 renderer 算法按原计划延期。

| # | 原问题 | 本轮证据与结论 |
| --- | --- | --- |
| 1 | 接管声明无自由视角姿态入口 | [CameraControlClaims](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/CameraControlClaims.java>)登记 PoseProvider，winningPose 同帧返回 Claim + WorldPose，异常变准备失败；原入口缺口关闭 |
| 2 | 合成输入缺层级/作者质量请求 | [RigPose](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigPose.java>)冻结 zOrder/resolutionScale/updateEveryFrames；值有领域检查，禁止绘制时查当前 store；缺口关闭 |
| 3 | 事件列表全局轨道排序 | [PreparedFrame](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PreparedFrame.java>)明确周期/区间 → 时间递增 → 同时刻轨道序；不再全局排序；契约冲突关闭 |
| 4 | 事件解析缺捕获快照绑定 | 帧带 AnimationSnapshot，[EventDispatcher](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/EventDispatcher.java>)接收整帧并强制从该快照解析；S1姿态/S2载荷混用路径不再是许可协议 |
| 5 | 复用旧画面无采样身份 | [RigRenderStatus](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigRenderStatus.java>)呈现必须带 SampleIdentity，区分帧序号/动画时间/游戏刻；FRAME_SKIPPED与RIG_PAUSED一致性检查有效；缺口关闭 |

### R15.1 实际验证

```powershell
.\gradlew.bat --offline compileJava test --rerun --console=plain
```

本轮实际重跑 BUILD SUCCESSFUL；19 个 suite / 142 个用例全部通过，v2 为 13 / 76，无失败/错误/跳过；源码126个Java文件。[PlaybackContractTest](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackContractTest.java>)验证字段冻结、快照携带、尺寸/质量与采样身份约束；不是完整 renderer 或事件/仲裁算法测试。

### R15.2 关闭边界与后续验证

- 五项原契约问题关闭，本轮未发现新增确定阻断问题；不等于完整播放链路或 P1 全部通过。
- PoseProvider 每帧只读一次、捕获期间释放/登记的线性化及异常转失败仍须 core 测试；区分“释放后新调用/缓存禁止”与“已开始捕获返回结果”，不以文案推定重入安全已实现。
- 事件需要测试多周期/零事件/同刻跨轨顺序、S1准备后发布S2仍用S1、异常停止与部分执行报告；事件预算需在准备阶段限制区间工作量，不能到展开完再限制。
- 合成需要测试文档序与zOrder反向、同层稳定顺序、作者质量与降级状态分离、同tick不同采样、活动/来源交接使旧纹理失效及资源释放。
- 同一 PreparedFrame 的事实有效性、来源/活动集合与诊断一致性由后续准备器保证；当前构造测试不等于这些全局关系已验证。

## R16. 后续协议第 1 批（1–5）

**日期**：2026-10-18。**类型**：实施记录，不是新复核；不改主设计文档。

| # | 协议 | 落地 |
| --- | --- | --- |
| 1 | document 提交 | `EditSubmission` 携带动作标签；同步 `submit` 通知期间返回 `REENTRANT_SUBMIT`；新增 `submitAfterNotifications` 延迟入口，通知结束后重查基版本 |
| 2 | 编辑目标与能力 | `EditOp.SetStateKey` 单键改状态值，由轨道能力校验；`EditTarget.Field` 保留对象种类与字段路径 |
| 3 | 试算 | `TrialResult` 强制 FAILED 含 ERROR、PASSED/INCOMPLETE 不含 ERROR、INCOMPLETE 必填原因；报告区间、策略、检查量、固定事实 |
| 4 | eval 绑定 | `CurveSampler` 改收显式 `CurveSampleRequest`（上下文 + 轨道 + 机位 + 时间）；`EvaluationContext.bind` 统一绑定 |
| 5 | 世界与缓存身份 | `FrameFacts.worldInstanceId`、`EvaluationKey.worldInstanceId`；`EvaluationKey.from(CurveSampleRequest, ...)` 统一生成键 |

**验证**：实际执行 `.\gradlew.bat --offline compileJava test --console=plain`，BUILD SUCCESSFUL；19 个 suite / 145 个用例，v2 13 / 79；128 个主源码 Java 文件。新增/更新测试覆盖提交标签、状态键/字段目标、试算一致性、显式采样请求与世界身份。`core` 的队列、能力分派与身份分配仍需运行验证。

## R17. 后续协议第 2 批（6–11）

**日期**：2026-10-18。**类型**：实施记录，不是新复核；不改主设计文档。

| # | 协议 | 落地 |
| --- | --- | --- |
| 6 | 空间与姿态错误 | 新增 `AxisSample`（ABSENT/VALUE/ERROR）与 `SpatialResolution`（NotRequired/Resolved/Failed）；`DocumentPose` 逐轴三态、掩码派生；`FrameFacts.RigFacts` 改用三态解析 |
| 7 | playback 启动/执行 | `PlaybackService.start` 明确先校验/准备再换档，失败保持原文档、序号、状态与相机控制；执行事件仍绑定捕获快照 |
| 8 | 接管边界 | `winningPose` 明确按捕获边界线性化：捕获期间的释放/登记影响下一次；释放后禁止新调用/缓存复用；provider 异常转准备失败 |
| 9 | 渲染与旧画面 | `SampleIdentity` 增加世界实例、快照、活动区间代际与来源代际；已呈现必须携带，任一不匹配失效 |
| 10 | 存储/序列化 | 新增 `storage.ResourceId`；`DiagnosticScope.File` 改用 `ResourceId`，不再用自由字符串 |
| 11 | 消息显示 | 新增无状态 `LocalizedMessageRenderer`，每次独立渲染 Component，保留翻译键/参数/嵌套样式 |

**验证**：BUILD SUCCESSFUL；19 个 suite / 146 个用例，v2 13 / 80；133 个主源码 Java 文件；门禁 0/0/0。新增/更新测试覆盖轴/空间三态、旧帧身份与 ResourceId；消息渲染器与三态消费仍需 core / 宿主验证。

## R19. storage 契约与 R18 契约补齐

**日期**：2026-10-18。**类型**：实施记录；交付第六批 storage 契约，并补齐 R18 点的三项契约缺口。

### R19.1 交付

- `storage`：`AnimationResourceEntry`、`AnimationResourceService`、`LoadResult`、`SaveResult`、`DiagnosticReport`、`RecoverySlot`、`RecoveryService`、`RecoveryDecision`。
- `eval`：`TrackIndex.snapshot()`，`EvaluationContext` 校验索引与快照同源；`EvaluationKey.from` 从 `FrameFacts` 取采样表版本，移除可传入不一致版本的参数。
- `playback`：`prepareFrame` 接收 `EventBudget`，要求展开区间时限制；服务明确所有方法与帧准备限主线程。

### R19.2 验证

- `.\gradlew.bat --offline compileJava test --console=plain`：BUILD SUCCESSFUL；20 个 suite / 151 个用例，v2 14 / 85；141 个主源码 Java 文件。
- 新增 `storage/StorageContractTest`；更新 Eval 测试的索引同源与 Key 表版本断言。
- 门禁 0/0/0；尚未验证路径安全、Codec、原子写入、恢复调度、主线程运行检查与预算展开实现。

## 3. 测试基线沿革（全工程 / v2）

| 记录 | suite / 用例（全工程） | suite / 用例（v2） |
| --- | --- | --- |
| R7 | 14 / 105 | 8 / 39 |
| R8 | 16 / 117 | 10 / 51 |
| R9 | 17 / 123 | 11 / 57 |
| R10 | 17 / 127 | 11 / 61 |
| R11–R12 | 18 / 131 | 12 / 65 |
| R13 | 19 / 140 | 13 / 74 |
| R14–R15 | 19 / 142 | 13 / 76 |
| R16 | 19 / 145 | 13 / 79 |
| R17 | 19 / 146 | 13 / 80 |
| R18 | 19 / 146 | 13 / 80 |
| R19 | 20 / 151 | 14 / 85 |
| R20 | 20 / 151 | 14 / 85 |
| R21 | 20 / 151 | 14 / 85 |
| R22 | 20 / 151 | 14 / 85 |
| R23 | 21 / 156 | 15 / 90 |
| R24 | 21 / 156 | 15 / 90 |
| R25 | 21 / 157 | 15 / 91 |
| R26 | 21 / 157 | 15 / 91 |
| R27 | 21 / 158 | 15 / 92 |
| R28 | 21 / 158 | 15 / 92 |
| R29 | 22 / 160 | 16 / 94 |

早期记录中的命令、依赖门禁及原始位置保留归档；本轮未重新宣称运行级门禁/客户端效果已验证。后续记录应保存实际执行命令与结果，不沿用旧数字证明新代码。

## 4. 文档凝练说明

本次按用户要求将计划与日志凝练：保留 Q1–Q14、A/C状态、R编号、最终修复、测试沿革、待定协议及完整历史归档。去除当前文档中的重复类型清单、被推翻的长篇性能论证和多处互相冲突的“当前待办”。凝练不改变产品决定，也不修改源码；新记录从 R16 继续追加。


## R18. 十一项协议落实核对与设计同步

**日期**：2026-10-18。按最新用户要求验证 11 项并同步设计，更新 Q15 当时“暂不改主设计”的阶段限制；R16/R17 保留历史，不把实施记录当运行验收。

结论：11 项方向均已确认；公开契约大部分已调整，但“全部落实”只在契约方向意义上成立。逐项状态见[建议文档落实表](<../P1-protocol-decisions-proposal.md>)，施工补齐见[计划 §7](<../P1-contract-plan.md>)。

- 1/2：提交、标签、状态单键、类型化对象字段入口存在；队列、能力分派、字段路径验证未实现，Field 仅 owner 类型化而非字段枚举。
- 3：TrialResult 状态/ERROR/原因强约束已实现；固定事实身份/说明可双空以容纳无世界依赖，世界依赖须生产者保证提供实际输入，当前构造器不按依赖图判断。
- 4/5：显式采样、worldInstanceId 与键工厂存在；EvaluationContext.bind 直接包装调用方对象，无同源检查。EvaluationKey.from 仍另收快照/事实/tableVersion，现测试可传与 facts 不同的表版本，故身份绑定闭环尚未验证。
- 6：AxisSample / SpatialResolution 与 hasError 存在；轴/FOV 诊断列表由生产者汇总，合成器未实现。
- 7/8：启动原子边界、事件序列和接管线性化有契约；prepareFrame 未承载准备阶段预算，需定义注入方式；主线程推荐未进入源码线程约束，暂不认定运行错误。
- 9：七项旧帧身份及呈现状态约束存在，绘制顺序/Player底图和纹理失效待宿主。
- 10：只有 ResourceId / File 作用域先落类型；完整 Codec/注册、两阶段解码、未知/非法拒绝与深度/数量/字节限额尚无执行代码/解码测试，属于已计划延期。
- 11：仅无状态 Renderer 接口，无真实渲染验证。

设计同步：主设计 §3.3/§4.1–§4.3/§5.1/§6/§7/§8，UI §6.2/世界读数/试算与合成状态。已写清规范要求与当前保证强度；没有修改源码、没有补写虚假的运行完成记录。

本轮实际运行 `./gradlew.bat --offline compileJava test --rerun --console=plain`，BUILD SUCCESSFUL：全工程 19 个 suite / 146 个用例通过，v2 为 13 / 80，无失败、错误、跳过。测试覆盖契约结构和已列边界，不证明绑定同源、区间展开预算、线程仲裁、Codec 或 Renderer 的运行行为。

## R20. storage 契约与 R18 补齐复核

**日期**：2026-10-18。**范围**：提交 `a0435f3` 的 storage 契约与 eval/playback 补齐；对照主设计 §3.4、§7、§8。本节为审查结论，不是实施记录；以下未关闭项尚未修改代码。

### R20.1 待处理项

| 编号 | 级别 | 位置与问题 | 触发、影响及修正要求 |
| --- | --- | --- | --- |
| S1 | P2 | [EvaluationContext:42](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationContext.java#L42>) 使用 `trackIndex.snapshot().equals(snapshot)` 校验同源 | AnimationSnapshot 为 record；不同 store 或正式/候选可产生 revision 与 document 等值但来源不同的两个快照，equals 仍通过。内容相等不能证明绑定同源。要求校验真实绑定身份：当前可约定索引保留原快照引用并比较引用，或使用独立来源 token；补同值异源拒绝及同源成功测试。 |
| S2 | P2 | [CameraControlClaims](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/CameraControlClaims.java#L23>) 未声明接管方法及 provider 的宿主主线程前置条件 | PlaybackService 的线程条款不能约束独立 Claims 接口。工作线程调用 winningPose 并执行 provider 仍未被其契约禁止。补 register/release/winner/winningPose 的线程约束，明确 provider 在同一线程运行；具体非法线程检查和捕获线性化测试仍留 core。 |
| S3 | P2，契约澄清 | [DiagnosticReport:20](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/storage/DiagnosticReport.java#L20>) 未定义报告资源与各项诊断作用域/资源的一致性 | 报告头为资源 A，却能携带 File(B) 或 Document 作用域诊断并进入 Load/Save 拒绝结果，消费方可能错误定位。若定义为单资源 IO/解码报告，构造时要求全部为 File 且 resource 与报告相等；若允许关联资源/文档校验诊断，须明确其合法性、定位及 Fix 展示规则，不能保持隐含混用。补相应边界测试，未定前不强制选择某种聚合模型。 |

S1/S2 是已确认残留；S3 是本批报告协议的定型项，不直接断言所有跨资源诊断均非法。它们不是重复要求实现已延期的 IO、Codec 或仲裁算法。

### R20.2 已补齐与延期边界

- [EvaluationKey.from](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationKey.java#L67>) 已移除独立 sampleTableVersion 参数，直接从请求上下文 facts 读取；原入口错配路径已消除，快照/事实身份生成仍待 core。
- [PlaybackService.prepareFrame](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackService.java#L56>) 已承载 EventBudget，并规定在区间展开/事件生成时限制；PlaybackService 自身已声明所有方法限宿主主线程。预算执行及线程检测仍须实现级验证，不将延期视为缺陷。
- storage 的 Loaded 明确仅代表解码成功，发布前仍需完整校验；恢复槽不得覆盖正式资源，KEEP 不可隐式恢复或删除。未发现这些规则的确定缺陷。
- ResourceId 根/路径映射和安全校验、实际 IO、Codec、原子替换及恢复调度按计划延期；异步保存可由完成回调闭包关联捕获版本，未据当前签名断言无法保护新编辑。

### R20.3 验证与覆盖限制

本轮实际执行 `./gradlew.bat --offline compileJava test --rerun --console=plain`，BUILD SUCCESSFUL：20 个 suite / 151 个用例全部通过，v2 为 14 / 85，无失败、错误、跳过；`git diff --check` 通过。

核对[StorageContractTest](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/storage/StorageContractTest.java>)及[EvalContractTest](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvalContractTest.java>)：现有用例未覆盖等值异源快照拒绝、资源报告作用域错绑；线程仲裁与预算展开没有运行实现，测试通过不能证明这些行为。本次仅记录审查，不修改源码。

文档同步待办：[契约计划 §7](<../P1-contract-plan.md>)引言仍保留 Key.from 可另传表版本、prepareFrame 未承载预算等旧状态，应按 R19/R20 更新；不得把 S1 的 equals 检查写成已经可靠证明同源。

## R21. R20 三项契约修正

**日期**：2026-10-18。**类型**：实施记录，待复核。

| 编号 | 修正 | 验证 |
| --- | --- | --- |
| S1 | `EvaluationContext` 改为比较 `trackIndex.snapshot() != snapshot`（引用身份），不再用 record equals；索引须保留原快照引用 | `EvalContractTest` 增加“等值新副本异源拒绝” |
| S2 | `CameraControlClaims` 声明 register/release/winner/winningPose 与 provider 均限宿主主线程，provider 在同一捕获调用内运行、不得异步 | 契约文本；运行线程检查留 core |
| S3 | `DiagnosticReport` 限定为单资源 IO/解码报告：每条诊断必须是 File 作用域且资源等于报告头 | `StorageContractTest` 增加跨资源 / Document 作用域拒绝 |

### R21.1 验证

- `.\gradlew.bat --offline compileJava test --console=plain`：BUILD SUCCESSFUL；20 个 suite / 151 个用例，v2 14 / 85；141 个主源码 Java 文件。
- 门禁 0/0/0；`git diff --check` 通过。
- 不宣称引用身份能替代 core 的候选/世界身份分配，也不宣称线程检查或单资源报告已运行验证。

## R22. R21 修复复核关闭

**日期**：2026-10-18。**类型**：代码与测试复核。对应提交 `f885bd3`，核对 R21 实施记录及 R20 的 S1–S3；本次不修改源码。

**结论**：S1–S3 均在当前契约范围修正，可关闭；本轮未发现新增确定缺陷。关闭不等于 core 算法、IO 或线程运行保障已经实现。

| 项目 | 复核证据 | 关闭范围 |
| --- | --- | --- |
| S1：快照同源 | [EvaluationContext:44–48](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationContext.java#L44-L48>)按引用身份比较；[TrackIndex:25–27](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/TrackIndex.java#L25-L27>)要求保留原快照引用；[测试:185–187](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvalContractTest.java#L185-L187>)拒绝 revision/document 等值的新快照副本 | 原 record equals 放过等值异源快照的路径已消除；TrackLookup 构建、候选/世界身份分配与实际重绑仍待实现 |
| S2：接管线程 | [CameraControlClaims:16–20](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/CameraControlClaims.java#L16-L20>)明确 register/release/winner/winningPose 与 provider 均限宿主主线程，provider 在同一捕获调用内执行且不得异步 | 原线程前置条件缺口关闭；跨线程拒绝、provider 重入和捕获线性化的实际检查随 core 验证 |
| S3：单资源报告 | [DiagnosticReport:30–40](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/storage/DiagnosticReport.java#L30-L40>)强制每项为同一资源的 File 作用域；[测试:70–77](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/storage/StorageContractTest.java#L70-L77>)覆盖跨资源与 Document 作用域拒绝 | 单资源 IO/解码报告的聚合规则已明确并执行构造校验；文档校验诊断走独立文档链路，实际资源载入/保存仍待 IO 实现 |

### R22.1 实际验证

执行 `./gradlew.bat --offline compileJava test --rerun --console=plain`，BUILD SUCCESSFUL：20 个 suite / 151 个用例全部通过，v2 为 14 / 85，无失败、错误、跳过；`git diff --check` 通过。测试数量未增加，本批是在原有测试方法内新增断言。

本轮确认构造校验、快照引用规则与线程契约，不把测试通过扩展为 IO、Codec、恢复调度、仲裁线程或预算执行已可运行。S1–S3 关闭不重新打开此前已关闭的 C1–C4 和 playback 五项修复。

## R23. editor 契约

**日期**：2026-10-18。**类型**：实施记录，待复核。

### R23.1 交付

- `api.editor`：`EditorSession`、`SessionViewState`、`TimelineView`、`SnappingSettings` / `SnapSource`、`PreviewRange`、`TrackVisibility`、`EditorToolState`。
- 交互：`InteractionId`、`InteractionOption`、`InteractionRequest`（不可变消息、唯一非空选项）。
- 后端：`EditorUiBackend`、`EditorUiHost`、`EditorBackendSelector`（auto / 指定 ID），不依赖客户端渲染类型。
- 包说明明确：会话不直接写文档、视图状态不进历史、所有方法限主线程、交互过期拒绝。

### R23.2 验证

- `.\gradlew.bat --offline compileJava test --console=plain`：BUILD SUCCESSFUL；21 个 suite / 156 个用例，v2 15 / 90；156 个主源码 Java 文件。
- 新增 `EditorContractTest`：交互选项、时间轴 / 预览区间 / 吸附校验、轨道显隐、视图容器复制、后端选择。
- 门禁 0/0/0；未验证会话实现、后端注册/选择、草稿持久化、交互回调、线程与关闭生命周期。

## R24. R23 editor 契约复核

**日期**：2026-10-18。**范围**：提交 `ef0a61a` 的 api.editor 全部 15 个文件及 EditorContractTest，对照主设计 §5/§9、UI 设计 §3/§6/§7。实施记录不等同审核通过；本次未修改源码。

### R24.1 待修正项

| 编号 | 级别 | 位置与触发 | 影响与建议 |
| --- | --- | --- | --- |
| E1 | P1 | [EditorSession:32–42](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorSession.java#L32-L42>)只有 beginEdit、只读 preview、commit/cancel；[EditTransaction](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/EditTransaction.java>)只保存 ID、基版本、目标与标签。接口集中没有草稿读写、预览候选或操作更新入口 | 第三方面板在 beginEdit 后输入文本/拖动值，无法将内容传入会话事务，commit 无从知道编辑结果；直接 submit 则绕过预览/取消协议。补充按事务读取/更新草稿与生成候选或操作的公开契约，保留不可解析文本和诊断，commit 使用该事务候选；明确目标/版本校验、取消丢弃及后端切换恢复。 |
| E2 | P2 | [EditorUiBackend:19–20](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorUiBackend.java#L19-L20>)只有 canOpen；[EditorUiHost:12–19](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorUiHost.java#L12-L19>)只有按选择器打开/打开默认界面，没有与选定后端关联的公开 opener | 宿主选中 canOpen=true 的第三方后端后，不能仅通过契约调用其实际打开行为，只能下转具体类型、硬编码或滥用探测副作用。新增不引用客户端 UI 类型的打开/挂载钩子，或公开描述符与 opener 工厂的注册绑定；保持 canOpen 为探测，明确打开失败及指定后端不自动切换规则。 |

这是公开接口的数据/调用通路不足，不是要求提前完成会话、注册表或 Screen 实现。E1、E2 尚未修正，不宣称 R23 已通过。

### R24.2 已核对与后续验证

- SessionViewState、TrackVisibility、SnappingSettings、InteractionRequest 的集合有防御性复制；消息为 LocalizedMessage；数值边界与交互选项唯一性未发现确定缺陷。
- EditorBackendSelector 区分 auto/指定 ID；指定已注册后端拒绝不回退、未注册才提示并回内置的文字约定与设计一致。实际注册、排序、转发与打开调用待接口补齐后实现。
- 不额外将 core/editor 的草稿存储、线程检测、交互过期处理或关闭资源释放尚未实现计作缺陷；仍需后续行为测试。

### R24.3 实际验证

执行 `./gradlew.bat --offline compileJava test --rerun --console=plain`，BUILD SUCCESSFUL：21 个 suite / 156 个用例全部通过，v2 为 15 / 90，无失败、错误、跳过；`git diff --check` 通过。

[EditorContractTest](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorContractTest.java>)目前覆盖交互选项、数值范围、显隐、容器复制和 selector 值，不覆盖 begin→更新→预览→commit/cancel、非法草稿恢复或选中第三方后端后的实际派发。接口补齐后须加入对应协议测试，运行实现仍随后续批次验收。

## R25. R24 E1/E2 修正

**日期**：2026-10-18。**类型**：实施记录，待复核；不宣称 R23/R24 已通过。

### R25.1 修正

| 编号 | 修正 |
| --- | --- |
| E1 | 新增 `EditDraftState`（事务、字段原文、候选、诊断）与 `EditorSession.draft` / `updateDraftText` / `updateDraftOperation`；`commit` 明确使用事务已保存候选，无候选或错误时拒绝，保留不可解析文本与诊断 |
| E2 | `EditorUiBackend` 新增 `open(EditorSession)` 实际打开钩子；`EditorUiHost` 明确选中可接管后端后调用该钩子，指定后端失败不回退、未注册才回内置 |

### R25.2 验证

- `.\gradlew.bat --offline compileJava test --console=plain`：BUILD SUCCESSFUL；21 个 suite / 157 个用例，v2 15 / 91；157 个主源码 Java 文件。
- `EditorContractTest` 新增草稿原文复制与候选就绪断言；stub 后端 open 派发、真实 begin→预览→commit 与后端注册排序仍待实现 / 测试。

## R26. R25 E1/E2 修复复核

**日期**：2026-10-18。**范围**：提交 `f9ade98`；核对 EditorSession、EditDraftState、EditorUiBackend、EditorUiHost、EditorBackendSelector 及 EditorContractTest。只复核公开契约和现有测试，不宣称会话/UI 运行完成。

**结论**：R24 的 E1/E2 原始入口缺口已消除，在契约范围可关闭；本轮未发现新增确定阻断缺陷。另有后端打开前置条件的非阻断文案澄清，见下。

| 项目 | 复核证据 | 关闭边界 |
| --- | --- | --- |
| E1：草稿与候选通路 | [EditorSession:34–59](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorSession.java#L34-L59>)提供 beginEdit、draft、updateDraftText、updateDraftOperation、preview、commit/cancel；[EditDraftState](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditDraftState.java>)保存事务、原文、操作候选和诊断并复制容器。commit 明确使用保存的候选，无候选或 ERROR 拒绝 | 第三方面板已有传递输入/操作、恢复原文与提交/取消的公开数据通路。解析策略、候选更新、过期事务拒绝、跨后端恢复和一次撤销仍需会话实现验证 |
| E2：后端派发入口 | [EditorUiBackend:22–26](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorUiBackend.java#L22-L26>)新增 open(session)，[EditorUiHost:14–19](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorUiHost.java#L14-L19>)要求选中可接管后端后调用；指定拒绝/失败不回退，未注册 ID 才回内置 | 原先只有探测、没有实际行为入口的缺口关闭。注册、排序、打开失败报告、包装转发与生命周期运行仍延期 |

### R26.1 非阻断澄清

[EditorUiBackend:24](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorUiBackend.java#L24>)写“canOpen 为真或明确指定该后端后调用”，可能使显式指定被理解为绕过接管探测；Host 与 Selector 同时保留指定后端拒绝接管的规则。建议统一为“自动/指定均仅在 canOpen 为真后调用；指定拒绝不自动回退”。当前文字没有强制 Host 绕过拒绝，且无实际派发实现，不据此报告确定运行错误或重新打开 E2 原入口缺陷。本次仅记录，未修改该注释。

### R26.2 实际验证与未覆盖项

实际执行 `./gradlew.bat --offline compileJava test --rerun --console=plain`，BUILD SUCCESSFUL：21 个 suite / 157 个用例通过，v2 为 15 / 91，无失败、错误、跳过；`git diff --check` 通过。

[EditorContractTest:114–132](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/editor/EditorContractTest.java#L114-L132>)新增输入原文容器隔离、候选就绪和 ERROR 阻止可提交候选的断言；没有真实会话或后端宿主实现，因此 begin→更新→预览→commit/cancel、不可解析文本重建恢复、过期事务拒绝、后端打开派发/排序/转发及失败清理仍待后续行为测试。不把已明确延期的运行验证当作本批缺陷。

本轮仅更新审核日志，未修改源码或其他设计文档。

## R27. EditTools 契约

**日期**：2026-10-18。**类型**：实施记录，待复核；同时落实 R26.1 的文案澄清。

### R27.1 交付

- `EditTools`：`applyEasing`、`applyBatchTransform`、`bake`、`captureAim`，只返回完整候选或整体拒绝。
- `EasingPreset` / `EasingRequest`：显式插值与曲柄，不新增隐式缓动。
- `BatchTransform` / `BatchTransformRequest`：时间偏移、值域缩放/偏移/取反分开表达。
- `BakeRequest`：机位/轨道、区间、步长、插值、最大键数与冻结 `EvaluationContext`。
- `AimCaptureRequest`：机位、时间、基版本与冻结上下文；覆盖为 Target 时须先清除。
- `ToolResult`：`Ready(EditOp, warnings)` 不得含 ERROR；`Rejected` 至少一条诊断。
- `EditorUiBackend.open` 文案统一为：自动 / 指定均仅在 `canOpen` 为真后调用，指定拒绝不回退。

### R27.2 验证

- `.\gradlew.bat --offline compileJava test --console=plain`：BUILD SUCCESSFUL；21 个 suite / 158 个用例，v2 15 / 92；165 个主源码 Java 文件。
- `EditContractTest` 增加预设/批量参数校验与 ToolResult 一致性断言。
- 门禁 0/0/0；未验证采样、烘焙展开、注视转换、覆盖清理、原子提交与 `ToolResult` 的消费流程。

## R28. R27 EditTools 契约复核

**日期**：2026-10-18。**范围**：提交 `c852bf3` 的 EditTools、EasingPreset/Request、BatchTransform/Request、BakeRequest、AimCaptureRequest、ToolResult 及 EditContractTest，对照主设计 §4.6/§4.7。本次仅审查与记录，不修改源码。

**结论**：发现两项 P2 公开契约缺口，建议补齐后再关闭 R27；它们是输入/绑定信息不足，不是将 core 工具算法延期本身计为缺陷。R26.1 后端 open 前置条件已统一为自动/指定均须 canOpen 为真，该文案澄清可关闭。

### R28.1 待修正项

| 编号 | 级别 | 位置与触发 | 影响与修正建议 |
| --- | --- | --- | --- |
| T1 | P2 | [EditTools:12–16](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/EditTools.java#L12-L16>)、[EasingRequest:18](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/EasingRequest.java#L18>)、[BatchTransformRequest:16](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/BatchTransformRequest.java#L16>)只提供轨道/键 ID 与操作意图，没有输入快照，也未规定实例快照绑定 | 两份文档可沿用相同 ID 而具有不同键值、公式或邻键；相同请求不能表明工具要修改哪份文档、如何计算完整候选或检查时间冲突。显式传入冻结 AnimationSnapshot，或提供可审查的实例绑定/生命周期协议；不得在计算中读取可变当前 store。补同 ID 不同文档输入和绑定失效的协议测试。 |
| T2 | P2 | [AimCaptureRequest:21–35](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/AimCaptureRequest.java#L21-L35>)不含 AimOverride 来源及显式左侧目标选项；[FrameFacts.RigFacts](<../../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/FrameFacts.java#L84-L90>)只有覆盖后 AimState。baseRevision 也未与 context.snapshot.revision 校验一致 | 文档 Target(A)+继承 与相同文档+固定 Replace(A) 可产生完全相同请求/上下文，但设计要求后者先清除覆盖、重建环境，工具无法区分合法性。t 已有 Off 时“默认不可捕获”与“显式使用紧邻左侧 Target”也无法表达。补捕获配置来源/覆盖策略及明确的左侧选择，按 t 的位置和目标事实求值；校验基版本与冻结快照一致。补继承/固定替换同目标、Off+左侧选择、无有效左侧目标、基版本错配测试。 |

### R28.2 已核对与延期边界

- 请求集合使用 List.copyOf，预设/批量数值有限性与非负长度、烘焙范围/正步长/最大键数已有局部检查；Ready 不允许 ERROR，Rejected 不返回部分操作且诊断非空。未发现这些局部结构的新缺陷。
- BakeRequest 已携带机位/轨道、区间、步长、插值、最大键数及 EvaluationContext。端点采样、区间替换和 KeyId 生成仍需统一算法/消费协议，本轮未据此追加确定缺陷。
- ToolResult.Ready 不带版本并非单独确证缺陷：调用方可保留原请求/事务的基版本，生成 EditSubmission 时使用它；不能改用最新版本绕过过期检查。版本关联、替换已有键/公式的确认及原子提交仍需实现测试。
- EditTools 总说明将 UNKNOWN 一律列为拒绝，消费时仍须服从主设计“未显式兜底的 UNKNOWN 才使依赖失败”的规则，不得把已成功求值的显式默认值误判为烘焙失败；此处保留为算法前文案澄清，不增加阻断项。

### R28.3 实际验证

实际执行 `./gradlew.bat --offline compileJava test --rerun --console=plain`，BUILD SUCCESSFUL：21 个 suite / 158 个用例全部通过，v2 为 15 / 92，无失败、错误、跳过；`git diff --check` 通过。

[EditContractTest:184–205](<../../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/EditContractTest.java#L184-L205>)主要验证参数领域、列表复制和结果诊断约束，没有 BakeRequest / AimCaptureRequest 的构造与绑定边界测试。采样、烘焙、捕获及原子候选消费的真实算法按原计划留 core；测试通过不证明 T1/T2 已闭环。

## R29. R28 T1/T2 修正

**日期**：2026-10-18。**类型**：实施记录，待复核。

| 编号 | 修正 |
| --- | --- |
| T1 | `EasingRequest` / `BatchTransformRequest` 增加冻结 `AnimationSnapshot`；工具不得读取当前 store；同 ID 不同文档请求不再等价 |
| T2 | `AimCaptureRequest` 增加 `AimCaptureMode`（CURRENT / LEFT_TARGET）、实际 `AimOverride`，并校验 `baseRevision == context.snapshot().revision()`；`Replace` 覆盖须先清除，Off 左侧仍 Off 时不得再向前搜索 |

### R29.1 验证

- `.\gradlew.bat --offline compileJava test --console=plain`：BUILD SUCCESSFUL；22 个 suite / 160 个用例，v2 16 / 94；166 个主源码 Java 文件。
- 新增 `EditToolsBindingTest`：同 ID 不同快照输入、空快照拒绝、捕获 mode/override/基版本错配。
- 门禁 0/0/0；仍未验证工具实际采样、覆盖清理、左侧目标解析、原子提交与 `ToolResult` 消费。
