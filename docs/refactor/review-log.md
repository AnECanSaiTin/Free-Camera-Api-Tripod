# 契约复核日志（凝练版）

> 更新：2026-10-18。当前施工边界见[P1 计划](<P1-contract-plan.md>)；设计规则见[主设计](<../design-animation-editor-framework.md>)。本日志只保存结论、证据与复核范围。
> 本轮按 Q15 落实后续 11 项协议（两批），不修改主设计文档；R16/R17 记录实施与验证，**不宣称 core 播放、事件、仲裁或渲染已实现**。
> 原始 R1–R14 正文、微基准表及历史被推翻论证保存在[历史归档](<archive/review-log-R1-R14-2026-10-18.md>)。历史不作施工指令，编号保留可追溯；后续新发现追加编号，实施记录不自动等于复核通过。

## 1. 当前结论

| 事项 | 结论 |
| --- | --- |
| 两种键 | 数值键 NumberSource + 可选 STEP/LINEAR/HERMITE；状态键原生值 + STEP。保留具体类型，不禁止共享接口/基础设施 |
| 性能 | 不以 Stream 写法判热点；孤立替身微基准不能证明稳定倍数或未来帧时间。A1/A4 已清理，A2 按证据，A3 待实现 |
| 标记 | 无 Tick/指令/发声直接副作用，仍按现设计贡献 duration；A6 改规则需另行裁决 |
| 文本与诊断 | LocalizedMessage 封闭不可变树；Document/File 显式作用域，禁止 File 对象定位/Fix，修复版本匹配 |
| playback | 声明可取姿态；帧快照和合成设置冻结；事件按发生顺序且绑定快照；已呈现画面带采样身份 |

当前待办仅在[计划 §6](<P1-contract-plan.md#p1-follow-up>)维护：A2 条件缓存、A3 绑定实现、A6 产品裁决及各 core 运行验证。A0/A1/A4/A5、A7 的注释/时长测试已完成；A7 事件副作用验证延期。

## 2. 历史编号摘要（R1–R14）

各条的核查日期、原始源码位置与完整论证见[归档](<archive/review-log-R1-R14-2026-10-18.md>)；本表只保留最终解释，避免重复已失效指令。

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

### 2.1 C1–C4 最终方案（R12）

| 编号 | 原缺陷 | 最终核查 |
| --- | --- | --- |
| C1 | 换档重置版本可使旧请求重新匹配 | Revision / SessionSwitch / Store 一致要求同 store 继续递增；core 仍需测旧请求拒绝 |
| C2 | 同帧不同时间采样命中同键 | EvaluationKey 增 sampleTime，有限非负、负零正规化；同帧 t0/t1 不等 |
| C3 | Component 及嵌套子组件/翻译参数泄漏 | LocalizedMessage / Argument sealed，record 叶子不可变、列表防御性复制；消息与全部标签不保存 Component |
| C4 | 无 Fix 对象诊断缺版本 | DiagnosticScope.Document 强制版本；File 不许对象 ID/Fix；Fix 与 Document 版本一致 |

源码：[版本](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/document/Revision.java>)、[缓存键](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/eval/EvaluationKey.java>)、[消息树](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/LocalizedMessage.java>)、[诊断](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/edit/Diagnostic.java>)。

R12 的非阻断测试空白已在 R13 补充：消息输出列表不可修改、多层嵌套输入隔离、Document(null) 拒绝、无 Fix 文档对象诊断合法分支。消息渲染、异步作用域与候选绑定未实现，不因结构测试通过而关闭运行验证。

## R15. playback 五项修复复核关闭

**日期**：2026-10-18。**性质**：代码与测试复核，不是新产品裁决。核查范围为第五批已列契约；时间推进、仲裁、事件执行与 renderer 算法按原计划延期。

| # | 原问题 | 本轮证据与结论 |
| --- | --- | --- |
| 1 | 接管声明无自由视角姿态入口 | [CameraControlClaims](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/CameraControlClaims.java>)登记 PoseProvider，winningPose 同帧返回 Claim + WorldPose，异常变准备失败；原入口缺口关闭 |
| 2 | 合成输入缺层级/作者质量请求 | [RigPose](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigPose.java>)冻结 zOrder/resolutionScale/updateEveryFrames；值有领域检查，禁止绘制时查当前 store；缺口关闭 |
| 3 | 事件列表全局轨道排序 | [PreparedFrame](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PreparedFrame.java>)明确周期/区间 → 时间递增 → 同时刻轨道序；不再全局排序；契约冲突关闭 |
| 4 | 事件解析缺捕获快照绑定 | 帧带 AnimationSnapshot，[EventDispatcher](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/EventDispatcher.java>)接收整帧并强制从该快照解析；S1姿态/S2载荷混用路径不再是许可协议 |
| 5 | 复用旧画面无采样身份 | [RigRenderStatus](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigRenderStatus.java>)呈现必须带 SampleIdentity，区分帧序号/动画时间/游戏刻；FRAME_SKIPPED与RIG_PAUSED一致性检查有效；缺口关闭 |

### R15.1 实际验证

```powershell
.\gradlew.bat --offline compileJava test --rerun --console=plain
```

本轮实际重跑 BUILD SUCCESSFUL；19 个 suite / 142 个用例全部通过，v2 为 13 / 76，无失败/错误/跳过；源码126个Java文件。[PlaybackContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackContractTest.java>)验证字段冻结、快照携带、尺寸/质量与采样身份约束；不是完整 renderer 或事件/仲裁算法测试。

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

早期记录中的命令、依赖门禁及原始位置保留归档；本轮未重新宣称运行级门禁/客户端效果已验证。后续记录应保存实际执行命令与结果，不沿用旧数字证明新代码。

## 4. 文档凝练说明

本次按用户要求将计划与日志凝练：保留 Q1–Q14、A/C状态、R编号、最终修复、测试沿革、待定协议及完整历史归档。去除当前文档中的重复类型清单、被推翻的长篇性能论证和多处互相冲突的“当前待办”。凝练不改变产品决定，也不修改源码；新记录从 R16 继续追加。
