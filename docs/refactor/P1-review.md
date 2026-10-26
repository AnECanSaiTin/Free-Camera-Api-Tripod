# P1 审核与复核记录

> 更新：2026-10-26。本文件只维护审核 / 复核编号、原问题、关闭边界与证据；契约、实施批次、施工记录与验证基线见[P1 契约、实施与验收](<P1-contract-plan.md>)。
>
> **当前结论：R54 对 C5 两项修复在原问题范围通过；R55 对 `95c3dda` 发现九项问题；R56 复核 C6，八项在原反例范围关闭，依赖环检查因 R56-H1 部分关闭；R57 已独立复核 C6+C7，R56-H1 在原问题范围关闭，未发现 C7 引入的确定误报、漏检或其他回归。结合 R56，R55 九项问题可在已列原反例范围关闭。C8 已实现相机接管仲裁与类型能力服务，待 R58；C9 已实现消息渲染与四个编辑工具，待 R59；C10 已实现播放请求接收入口，待 R60；C11 已实现编辑器 UI 后端选择编排，待 R61；C12 已实现 NBT 文档编解码与规模限制，待 R62；C13 已实现全文档 KeyId 唯一性，待 R63；C14 已实现 NBT 文件资源服务，待 R64。** 这不表示 core 全部行为、客户端或完整 P1 运行验收完成。
>
> 审核与提交不是同一编号空间：提交用 C，审核用 R；早期 R7–R48 混合保留历史编号。R1–R12 也曾用于“剩余契约”清单，与审核编号无对应关系。行为规范仍以[动画主设计](<../design-animation-editor-framework.md>)、[UI 设计](<../design-editor-ui-framework.md>)和[音频设计](<../design-spatial-audio-api.md>)为准；本文件不新增产品裁决。

## 1. 审核编号与基线

| 审核 | 对象 | 结论 | 详见 |
|---|---|---|---|
| R54 | C5，`7095284` | 两项原问题在原范围关闭，未发现确定回归 | §2 |
| R55 | `95c3dda` 实现批次 | 发现九项问题 | §3 |
| R56 | C6（未提交） | 八项关闭，依赖环检查部分关闭（R56-H1） | §4 |
| R57 | `95c3dda` 加未提交的 C6+C7 | R56-H1 原范围关闭；结合 R56，九项原反例关闭，未发现确定新增回归 | §5 |
| R58 | C8（未提交） | **尚未执行**；建议独立复核相机接管与类型能力服务 | §6 |
| R59 | C9（未提交） | **尚未执行**；建议独立复核消息渲染与 EditTools 请求扩展 | §7 |
| R60 | C10（未提交） | **尚未执行**；建议独立复核播放请求接收入口 | §8 |
| R61 | C11（未提交） | **尚未执行**；建议独立复核编辑器 UI 后端选择编排 | §9 |
| R62 | C12（未提交） | **尚未执行**；建议独立复核 NBT 文档编解码与规模限制 | §10 |
| R63 | C13（未提交） | **尚未执行**；建议独立复核全文档 KeyId 唯一性 | §11 |
| R64 | C14（未提交） | **尚未执行**；建议独立复核 NBT 文件资源服务 | §12 |

### 1.1 历史范围与关闭边界

下表保留历史交付与关闭摘要，便于解释旧编号；审核结论以各 R 节为准。早期 R7–R48 为混合编号，不应与提交 C 编号互换。

| 历史范围      | 最终交付或修复                                                                                                                    | 当前关闭边界                                                                                        |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| R1–R7 / A0–A7 | 时间有限性、乱序/冲突、duration 内部遍历、withRig 无变化返回自身、命名与标记说明                                                  | A0/A1/A4/A5 已落地；A7 注释/时长测试完成，事件验证待后续；A2 条件缓存、A3 索引实现、A6 产品裁决保留 |
| R8–R12        | document/edit/eval 契约；同 store 单调 Revision、sampleTime 缓存身份、不可变消息树、显式诊断作用域                                | 原四项契约关闭，不恢复 Component.copy 或可空版本旧方案；发布/缓存运行另验                           |
| R13–R22       | playback、Q15 十一项协议、storage；provider 姿态入口、质量冻结、事件快照/顺序、采样身份、索引同源、准备预算与线程约束、单资源报告 | 原问题契约关闭；实际仲裁、事件、队列、IO和试算未验收                                                |
| R23–R30       | editor、草稿更新/后端 open、EditTools 冻结快照与注视捕获输入                                                                      | E1/E2、T1/T2 原入口问题关闭；会话与工具算法未验收                                                   |
| R31–R40       | 布局动画、注册表选型、成员/根 Codec与两阶段解码；叠加双向拒绝、精确整数、扩展泛型边界及压缩 JSON 往返修复                         | 原编解码反例关闭；NBT/网络、路径填充、规模保护未验收                                                |
| R41–R46       | 表达式编译/求值接口、共享额度/深度、失败来源、字段/KeyId 定位及资源失败缓存规则                                                   | 原接口缺口关闭；R1 表达式运行实现仍未完成                                                           |
| R47–R49、C1   | 帧事实/provider、resolver/collector 与能力服务；补动画时间/逐项空间计划和解析诊断作用域                                           | R49-H1/H2 在 R51 关闭；真实客户端准备未验收                                                         |
| C2 / 审核 R52 | 扩展时间轴、事件执行器注册、通道描述；C4 统一原序片段说明                                                                         | 契约无确定阻断，显示/编辑/执行待实现                                                                |
| C3 / 审核 R53 | FrameFacts 持有原快照引用，拒绝同 revision 异源绑定                                                                               | R51-N1 原反例关闭；provider 实际传递待验证                                                          |
| C4 / 本轮 R54 | 类型校验钩子、结构路径承载、按 RigId 预览覆盖、可见像素质量状态                                                                   | 已列 R9–R12 契约均交付；质量停路残留由 C5 关闭，其余运行分派/路径/预览/renderer 延期                |
| C5 / 本轮 R54 | 允许可见停路机位 0×0 目标/0 密度；编码成功改非空 Unit                                                                             | 两项原问题复核关闭，未发现确定回归；不代表真实渲染与网络完成                                        |
| `95c3dda` / R55 | 2026-10-25 core 实现批次                                                                                                          | R55 发现九项问题，见 §3                                                                             |
| C6 / R56      | R55 九项修复                                                                                                                      | 八项关闭；R56-H1 部分关闭，见 §4                                                                    |
| C7 / R57      | R56-H1 形参作用域修复                                                                                                             | 独立复核在原问题范围关闭，未发现确定新增回归；验证与保留边界见 §5                                   |
| C8 / 待 R58   | 相机接管仲裁、类型能力服务、实参作用域测试                                                                                        | 施工方自检通过，独立复核未执行，见实施文档 §4.5 与本文 §6                                           |
| C9 / 待 R59   | 消息渲染、缓动/批量/烘焙/注视捕获、EditTools 请求扩展                                                                            | 施工方自检通过；独立复核未执行，见实施文档 §4.6 与本文 §7                                                 |
| C10 / 待 R60  | 播放请求接收入口                                                                                                                  | 施工方自检通过；真实网络包/传输/授权未做；独立复核未执行，见实施文档 §4.7 与本文 §8                       |
| C11 / 待 R61  | 编辑器 UI 后端选择 / 回退编排                                                                                                     | 施工方自检通过；真实客户端挂载/session 生命周期未做；独立复核未执行，见实施文档 §4.8 与本文 §9             |
| C12 / 待 R62  | NBT 文档编解码、深度 / 条目 / 字节限制                                                                                            | 施工方自检通过；文件服务/SavedData/压缩未做；独立复核未执行，见实施文档 §4.9 与本文 §10                    |
| C13 / 待 R63  | 全文档 KeyId 唯一性                                                                                                               | 施工方自检通过；跨文档/非时间轴扩展内部键未覆盖；独立复核未执行，见实施文档 §4.10 与本文 §11               |
| C14 / 待 R64  | NBT 文件资源服务                                                                                                                  | 施工方自检通过；SavedData/压缩/恢复/网络未做；独立复核未执行，见实施文档 §4.11 与本文 §12                  |

历史被撤回的论证不作施工依据：不据 Stream 写法或孤立替身微基准判断真实热点、承诺固定倍数/未来帧时间；不把 v1 调用当作 v2 已接线；数值与状态键保留具体类型但可复用公共设施；标记参与时长符合当前设计，不作为缺陷删除。

## 2. R54：C5 修复复核（2026-10-19）

**结论：两项原问题在原问题范围复核通过，未发现确定回归；不代表真实渲染与网络完成。** C5 审核基线为 **33 suite / 219 用例**（v2 27 / 153）。

| 原问题                                                             | 修复与证据                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 结论                                                             |
|--------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| 可见副路停路、释放目标后不能同时表达质量几何和 0×0 实际尺寸        | [RigQualityStatus](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigQualityStatus.java>)允许零目标/零密度，拒绝单轴为零及目标/密度矛盾；[RigRenderStatus](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/RigRenderStatus.java>)保留呈现时正目标、可见性与尺寸一致约束。[PlaybackContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackContractTest.java>)覆盖可见 RIG_PAUSED、零目标且 offscreen=false | 原契约反例关闭；释放目标后的几何查询成立，运行 renderer 未验证   |
| 审核 R50：DataResult<Void> 成功值只能为 null，标准 result() 不安全 | [PlaybackProtocolCodec](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackProtocolCodec.java>)改用 net.minecraft.util.Unit，适配器通过 result() 访问；[PlaybackProtocolContractTest](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/playback/PlaybackProtocolContractTest.java>)使用 Unit.INSTANCE 验证标准访问                                                                                                                                                   | 原成功值模型关闭；正式新增用例未执行真实缓冲区或适配器端到端编码 |

未将下列候选升级为缺陷：统一 validator 可由每请求捕获 Revision 的闭包装配；成员 Curve Codec 的构造异常由正式 DocumentCodec 解码边界捕获。上述均不用于扩大两项原问题范围，也不声称任意成员 Codec 独立调用都不会抛异常。

## 3. R55：`95c3dda` 实现批次审核（2026-10-25）

R55 审核 2026-10-25 已提交的 core 实现批次 `95c3dda`，发现九项问题。修复内容与测试证据见实施文档 §4.3；本表只保留问题清单与 R56 复核结论，避免把修复动作写成审核通过。

| 严重度 | R55 原问题 | R56 结论 |
|---|---|---|
| 高 | 启动在初始准备成功前就换档 | 原反例关闭 |
| 高 | 循环推进先取模，完整周期事件丢失 | 原反例关闭；超长会话 float 终点精度边界保留 |
| 高 | Local 机位用世界姿态补缺失轴后重复应用锚点 | 原反例关闭；万向节附近欧拉往返误差未做长期数值验收 |
| 高 | 锚点旋转跟随掩码及请求掩码覆盖未应用 | 原反例关闭 |
| 中 | 避障请求覆盖未生效 | 原反例关闭；真实世界碰撞查询仍由宿主实现 |
| 高 | 统一校验未接入依赖环 | **部分关闭**：R56-H1 |
| 高 | 同轨重复 `KeyId` 可构造和发布 | 原反例关闭；全文档跨对象 KeyId 作用域留待后续 |
| 中 | 同一帧 `wt` 通道与布局相差 20 倍 | 原反例关闭；主设计单位/范围/事实来源待同步 |
| 中 | 会话不响应换档，旧草稿 / 选择 / 交互残留 | 正常换档反例关闭；真实 UI / 后端切换未验收 |

## 4. R56：C6 修复复核（2026-10-26）

**结论：八项在原问题范围关闭，一项部分关闭；C6 当时不能整体通过。** 基线为 `95c3dda` 加当时未提交的 C6 修改。审核只读源码与测试，未修改实现，也未新增动态反例测试；以下关闭不代表客户端、renderer、世界查询或碰撞适配完成。

| R55 原问题 | R56 结论与证据 |
|---|---|
| 启动准备失败前已换档 | 原反例关闭。[CorePlaybackService:99](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackService.java#L99>) 先准备候选，ERROR / 异常时拒绝，成功后才换档和赋播放状态；请求校验已遍历全部 Local 机位。测试覆盖旧文档、Revision、PLAYING 与时钟保持，以及启动锚点成功 / 失败。 |
| 跨完整周期丢事件 | 原反例关闭。[完整区间传递:209](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackService.java#L209>) 保留未取模终点；测试验证 `(2,22]` 内 5 秒事件触发两次，seek 同时重置推进终点。超长会话 float 精度边界仍保留。 |
| Local 缺失轴重复叠加锚点 | 原反例关闭。[参考系转换:333](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactory.java#L333>) 先逆变换基础世界姿态、逐轴补全，再正变换；无通道时保持基础世界位置。 |
| 跟随掩码与请求覆盖未应用 | 原反例关闭。[掩码应用:338](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactory.java#L338>) 用生效掩码生成参考系旋转，并用于位置和朝向的正逆变换；测试覆盖 NONE、ALL 及请求 NONE 覆盖文档 ALL。 |
| 避障请求覆盖未生效 | 原反例关闭。[避障设置:394](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/playback/CorePlaybackFrameFactory.java#L394>) 请求优先且使用生效 margin，关闭时不调用 resolver；测试覆盖文档开 / 请求关与文档关 / 请求开。 |
| 依赖环可进入发布文档 | **部分关闭。** 环诊断已接入 L3，未引用变量自环及函数自调用反例关闭；函数形参作用域仍可隐藏真实轨道环，见 R56-H1。 |
| 同轨重复 KeyId 可发布 | 原反例关闭。[Curve:90](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/field/Curve.java#L90>) 与 [KeyframeSequences:38](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/animation/block/KeyframeSequences.java#L38>) 在构造期拒绝；统一校验复核通道、布局及状态键，存储分类为 DUPLICATE_KEY_ID。跨对象作用域仍按实施文档 §4.3 留待后续。 |
| 通道与布局 wt 相差 20 倍 | 原反例关闭。两入口均读 `facts.worldTime().progress()`；测试以 `WorldTime(3000, 12000)` 验证均得到 0.25。世界日进度的单位、范围和事实来源仍须同步主设计，见下文维护项。 |
| 换档后旧会话状态残留 | 正常换档后的原反例关闭。[会话清理:658](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/editor/CoreEditorSession.java#L658>) 清空草稿、当前事务、选择 / 视图、交互和已记录选择；外线程通知在下一次宿主线程调用前应用。真实 UI / 后端切换未验收。 |

### 4.1 R56-H1（高，R56 时未关闭；C7 修复，R57 原范围关闭）：函数形参泄漏到全局变量公式，真实轨道环仍可发布

[CoreDependencyGraph](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java>) 展开全局变量公式时继续传递调用函数的 `locals`；`resolveFunctionBody` 也累计调用方形参，未建立独立作用域。最小反例：

```text
轨道 T 的键值 = Formula("f(1)")
函数 f(x) = v
全局变量 v = Formula("x")
全局变量 x = TrackRef(T)
```

解析 `v` 时形参集合仍为 `{x}`，真正的全局 `x` 被当作形参跳过，得到 `dependenciesOf(T) = {}`。符号根扫描从 `v` 找到 T 后丢弃依赖集合，且不沿 TrackRef 回到轨道，不能补获此环。名字与 arity 均合法，[统一校验:529](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidator.java#L529>) 因而无法拒绝。

[实际求值器:540](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreExpressionEvaluator.java#L540>) 在进入全局变量定义时清空局部参数，实际链为 `T → f → v → 全局 x → T`，运行期才递归失败。该作用域错误此前已存在；C6 接入强制校验后仍未完整关闭 R55 原问题，违反[主设计 §4.2](<../design-animation-editor-framework.md#L327>)。

修复验收：展开全局变量公式时使用空局部形参集合；函数体仅使用自身形参，调用实参在调用方作用域分析；补充上述图诊断及统一校验拒绝测试。本反例为源码调用链复核结论，R56 未新增或执行专门动态反例测试。

**后续状态（C7 / R57，2026-10-26）：** C7 修复与施工方自检见实施文档 §4.4；R57 已独立复核，R56-H1 在原问题范围关闭，证据及边界见本文 §5。上述 R56 结论保留为历史审核记录，不回写为“当时已关闭”。

### 4.2 R56 维护与边界

- 实施文档 §4.2 已提交为 `95c3dda` 且经 R55 审核，须与未提交的 C6 区分；本文 §1.1 保留历史 C5 契约基线，当前实现以实施文档 §4 为准。
- C6 将 `wt` 明确为世界日进度 `[0,1)`，超出仅统一入口单位的描述；需同步[主设计表达式输入规则](<../design-animation-editor-framework.md#L473>) 的单位、范围与事实来源。R56 只记录审核，不修改主设计；截至 C7 该项仍为文档维护待办。
- 换档先通知所有 `onDocumentChanged`，再调用 `onSessionSwitched`：普通变更监听期间仍能看到旧会话状态，期间创建的新事务随后也会清除。现有契约未明确保证该阶段可创建草稿，本轮只记录时序边界，不升级为独立确定回归。

## 5. R57：C6+C7 修复复核（2026-10-26）

**结论：R56-H1 在原问题范围关闭；未发现 C7 引入的确定误报、漏检或其他回归。** 基线为 `95c3dda` 加复核时未提交的 C6+C7 修改。结合 R56 已关闭的八项，R55 九项问题可在已列原反例范围关闭；不表示完整 P1、core 全部行为或客户端运行验收完成。后续审核从 R58 继续。

本轮独立读取施工记录、实现及测试，执行清理后的全量编译 / 测试和 API 编译产物依赖检查；未修改实现，未新增测试。施工方临时变异检查见实施文档 §4.4，本轮未重复执行，不将其计为独立复核结果。

### 5.1 作用域与原反例

| 核查点 | 结果与证据 |
|---|---|
| 全局变量公式 | [CoreDependencyGraph:371](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java#L371>) 使用 `Set.of()`，不继承调用函数的形参；与 [CoreExpressionEvaluator:540](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreExpressionEvaluator.java#L540>) 的空局部表一致。 |
| 被调用函数体 | [依赖图:425](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java#L425>) 仅使用自身 `function.parameters()`；与 [求值器:668](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreExpressionEvaluator.java#L668>) 新建自身参数表一致。 |
| 调用实参 | [CompiledExpressionImpl:104](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/expression/CompiledExpressionImpl.java#L104>) 收集调用及全部实参引用，[依赖图:308](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraph.java#L308>) 按调用方 `locals` 展开；与 [求值器:629](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreExpressionEvaluator.java#L629>) 使用原 `env` 求实参一致。 |

R56-H1 的精确候选 `T = Formula("f(1)")`、`f(x) = v`、`v = Formula("x")`、`x = TrackRef(T)` 现在得到 `T → T` 依赖并报告轨道环，统一校验产生 ERROR。无环对照 `x = TrackRef(B)` 严格得到 `{B}` 且无诊断。符合[主设计的形参作用域与依赖环要求](<../design-animation-editor-framework.md#L327>)。

| 测试证据 | 验证内容 |
|---|---|
| [CoreDependencyGraphTest:122](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraphTest.java#L122>) | 精确非环对照：全局变量公式不受函数形参遮蔽，依赖为 `{B}` 且无诊断。 |
| [精确漏环反例:138](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraphTest.java#L138>) | 依赖包含自身 `{A}` 且报告 `Track dependency cycle`。 |
| [嵌套函数作用域:156](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreDependencyGraphTest.java#L156>) | `f(x) = g(x)`、`g(y) = x`，被调函数体不继承调用方形参，读取全局 `x`。 |
| [UnifiedDocumentValidatorTest:253](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/validation/UnifiedDocumentValidatorTest.java#L253>) | 同一精确反例进入 L3 强制校验后报告轨道环。 |
| [CoreExpressionEvaluatorTest:237](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/core/animation/eval/CoreExpressionEvaluatorTest.java#L237>) | 全局 `x=7`、`v=x+1`、`f(x)=v`，运行结果 `f(2)=8`，与图的词法作用域一致。 |

### 5.2 独立验证

实际执行：

```powershell
.\gradlew.bat --offline clean compileJava test --no-build-cache --rerun-tasks --console=plain
```

结果 BUILD SUCCESSFUL，清理后重新编译并执行全量测试；按测试结果 XML 独立统计：

| 范围 | suite / 用例 | 失败 / 错误 / 跳过 |
|---|---|---|
| 全工程 | **60 / 542** | **0 / 0 / 1** |
| v2 | **54 / 476** | **0 / 0 / 1** |

跳过项仍为 Windows 符号链接权限限制，与施工记录一致。全量包含 C6 已关闭八项及 C7 新增五项测试，未见用例回退。

JDK 25 `jdeps --ignore-missing-deps -verbose:class -filter:none` 独立检查 **386 个 v2 API class**，对旧根、`net.minecraft.client` 及 v2 core / editor 实现包的禁止类型依赖为 **0**。该检查范围为 API；本轮未据此宣称重新检查了全部架构门禁。`git diff --check` 通过。

### 5.3 保留边界与非阻断建议

- 嵌套函数测试中，调用实参 `x` 与被调函数的全局 `x` 最终都关联 B，集合去重不能独立证明实参作用域；当前实现经源码调用链核对正确，不构成确定缺陷，不阻断关闭。可后续增加两者关联不同轨道的对照测试。C8 已补 `callArgumentsStayInCallerScope`（实参对应 A、被调全局对应 B），待 R58 独立复核；本条保留为 R57 当时的建议。
- R56 已记录的全文档跨对象 KeyId 作用域、超长会话 float 事件终点、真实宿主锚点解析 / 世界查询 / 碰撞 / 客户端接线继续留待后续验收。
- `wt` 的主设计单位、范围及事实来源同步仍为维护待办；换档通知顺序仍为 §4.2 的时序边界。本轮不因 C7 自动关闭这些项目。

## 6. R58（待执行）：C8 实现复核建议

R58 尚未执行。C8 的施工方自检见实施文档 §4.5，不计入审核编号。建议至少覆盖：

1. **相机接管仲裁**：检查 `CoreCameraControlClaims` 的优先级 / 同优先级顺序、`winner` 不调用 provider、`winningPose` 的捕获冻结、捕获期间释放 / 登记只影响下一次、嵌套捕获拒绝、provider 空返回 / 异常传播、伪造与重复释放、跨线程拒绝。
2. **类型能力服务**：检查五张注册表冻结前置条件、锚点 / 采样三态、内建轨道与扩展轨道、只有解析器 / 采集器而 common 无类型时仍为 `TYPE_NOT_REGISTERED`、空 ID 拒绝。
3. **R57 非阻断建议**：检查 `callArgumentsStayInCallerScope` 的实参 A / 被调全局 B 对照，确认若把实参按被调函数作用域展开会出现 `A → A` 环。
4. **独立验证**：重跑全量，确认 62 suite / 566 用例（v2 56 / 500）、失败 / 错误 0、跳过 1；检查 API class 仍为 386，jdeps 禁止依赖为 0。
5. **边界**：相机接管未接入编辑器 / renderer / 宿主帧循环；“每帧至多一次 provider”和“provider 不发布文档”仍是宿主契约；能力服务未接 UI / 客户端查询；不得把 core 自检写成完整运行验收。

## 7. R59（待执行）：C9 实现复核建议

R59 尚未执行。C9 的施工方自检见实施文档 §4.6，不计入审核编号。建议至少覆盖：

1. **消息渲染**：检查 `CoreLocalizedMessageRenderer` 的 Literal / Translatable / fallback / 参数顺序 / 嵌套消息 / 受限样式；确认每次返回独立组件、不缓存、不回写消息；确认只支持契约列出的颜色、粗体、斜体、下划线。
2. **请求扩展**：检查 `AimCaptureRequest.basePose` / `session` 与 `BakeRequest.session` 的构造约束、基版本一致性、null 拒绝；确认主设计 §4.7 与 UML 字段已同步，破坏性重编译范围明确。
3. **工具主体**：检查 `CoreEditTools` 的缓动（插值/曲柄/缺失键/L4 上限）、批量（公式拒绝、时间冲突、值变换顺序、状态轨道限制）、烘焙（机位归属、含端点、maxKeys 前置、ABSENT/ERROR、共享 session）、注视捕获（复用 `CorePlaybackFrameFactory.prepareRig`、basePose 补轴、旋转 XYZ + Off、Replace/Disabled/缺通道拒绝、已有键 WARNING）；确认 C9 不能写成完整客户端编辑器或 P1 完成。
4. **独立验证**：重跑全量，确认 64 suite / 580 用例（v2 58 / 514）、失败 / 错误 0、跳过 1；检查 API class 仍为 386，jdeps 禁止依赖为 0。
5. **边界**：渲染器未接语言切换 / UI 宿主 / 显示缓存；注视捕获依赖调用方准备的 LEFT_TARGET 事实与同刻 Off 键；已有键替换只有 WARNING、没有强制确认字段；批量变换顺序、烘焙整轨替换与含端点口径为 C9 固定行为；客户端 renderer、世界查询与真实翻译表仍未验收。

## 8. R60（待执行）：C10 实现复核建议

R60 尚未执行。C10 的施工方自检见实施文档 §4.7，不计入审核编号。建议至少覆盖：

1. **接收入口**：检查 `CorePlaybackRequestReceiver` 的 `Stop` → `stopAndReset`、`Embedded` 同实例、`Local` 经资源服务解析；确认 `Start#toRequest` 仍拒绝异实例 Embedded。
2. **失败映射**：检查 Local 载入拒绝携带原 File 诊断并返回 `HOST_CAPABILITY_MISSING`；缺少开始序号、未知来源、请求构造失败返回 `EVALUATION_ERROR`；播放服务拒绝原样返回其 `PlaybackFailure`，且不产生部分播放状态。
3. **校验链路**：确认接收端没有复制文档校验或构造默认锚点；基版本、播放参数、启动准备与 store 统一校验仍由 `PlaybackService.start` / store 执行。
4. **独立验证**：重跑全量，确认 65 suite / 585 用例（v2 59 / 519）、失败 / 错误 0、跳过 1；检查 API class 仍为 386，jdeps 禁止依赖为 0。
5. **边界**：真实网络包注册 / 传输 / 收包线程切换、服务端权限与来源授权、Local 异步解析 / 超时仍未实现；接收端不是 `StreamCodec` 处理器；不得写成完整网络链路验收。

## 9. R61（待执行）：C11 实现复核建议

R61 尚未执行。C11 的施工方自检见实施文档 §4.8，不计入审核编号。建议至少覆盖：

1. **auto 选择**：检查优先级降序、同优先级 ID 稳定排序、内置始终最后；全部拒绝时调用内置；`canOpen` 异常在 auto 中继续下一个。
2. **指定选择**：已注册接受直接打开；已注册拒绝报告失败且不调用内置；未注册警告并打开内置；`open` 异常报告失败且不回退。
3. **构造与边界**：重复后端 ID（含内置）拒绝；`Reporter` 警告/失败回调；确认不关闭会话、不实现客户端屏幕挂载或接管令牌。
4. **独立验证**：重跑全量，确认 66 suite / 593 用例（v2 60 / 527）、失败 / 错误 0、跳过 1；检查 API class 仍为 386，jdeps 禁止依赖为 0。
5. **边界**：真实 UI 挂载、后端接管令牌、会话关闭/切换、面板输入与渲染未做；`auto` 中某个后端 `open` 异常当前停止，不继续回退；不得写成编辑器 UI 完成。

## 10. R62（待执行）：C12 实现复核建议

R62 尚未执行。C12 的施工方自检见实施文档 §4.9，不计入审核编号。建议至少覆盖：

1. **NBT 往返**：检查 `NbtDocumentCodec.encode` 经 `NbtOps` / `DocumentCodec` 编码、`decode` 反向解码；确认往返文档相等，结构失败不返回部分文档。
2. **规模限制**：检查字节上限在读取前与写入后生效；`NbtAccounter` 读取；深度 / 条目守卫在遍历前生效；Compound 键、List 元素、基本数组元素计数口径一致。
3. **失败语义**：损坏字节、非 Compound 根、超限、Codec 拒绝均转 `StructureFailed` / `DataResult` 错误；NBT 结构失败没有 JSON fieldPath 是有意边界。
4. **独立验证**：重跑全量，确认 67 suite / 598 用例（v2 61 / 532）、失败 / 错误 0、跳过 1；检查 API class 仍为 386，jdeps 禁止依赖为 0。
5. **边界**：NBT 文件服务、命名根 / 压缩策略、SavedData 生命周期、原子替换、恢复调度未实现；深度 / 条目检查发生在读取之后，读取阶段仅字节配额；不得写成 NBT 资源服务或 P1 存储完成。

## 11. R63（待执行）：C13 实现复核建议

R63 尚未执行。C13 的施工方自检见实施文档 §4.10，不计入审核编号。建议至少覆盖：

1. **全局作用域**：检查通道曲线、注视 / 活动键、布局 `AnimatedNumber.Keyed` 曲线、相机来源键与 `ExtensionTimelineTrack` 键是否共用同一 `KeyId` 表；同轨与跨轨重复都产生 `DUPLICATE_KEY_ID`。
2. **诊断**：确认第二个位置携带对象 / 字段定位，消息含首次出现路径；不静默覆盖、不只检查单轨。
3. **兼容性**：确认既有测试文档没有因全局收紧产生误报；非 `ExtensionTimelineTrack` 扩展轨道仍由类型校验器负责，不把不可见键当作已验证。
4. **独立验证**：重跑全量，确认 67 suite / 600 用例（v2 61 / 534）、失败 / 错误 0、跳过 1；检查 API class 仍为 386，jdeps 禁止依赖为 0。
5. **边界**：跨文档作用域、第三方轨道内部键、源文件 fieldPath 不在此项；不得写成全类型键身份已完成。

## 12. R64（待执行）：C14 实现复核建议

R64 尚未执行。C14 的施工方自检见实施文档 §4.11，不计入审核编号。建议至少覆盖：

1. **文件往返**：检查 `NbtAnimationResourceService` 的保存 / 载入 / `entry` / `entries` 往返；SHA-256、大小与修改时间元数据非空且一致。
2. **路径与规模**：越界 / 符号链接路径拒绝；字节上限在读取前与编码后生效；`NbtAccounter` 读取；`NbtTagGuard` 深度 / 条目检查；损坏 NBT 与结构错误转 File 诊断。
3. **原子保存**：临时文件、`ATOMIC_MOVE`、失败清理；异步保存回调恰好一次；不得留下部分正式文件。
4. **独立验证**：重跑全量，确认 68 suite / 604 用例（v2 62 / 538）、失败 / 错误 0、跳过 1；检查 API class 仍为 386，jdeps 禁止依赖为 0。
5. **边界**：SavedData 生命周期、压缩命名根、恢复槽调度、目录资源数量、网络传输与 TOCTOU 未实现；载入只做结构解码，统一校验仍由 loader/store 执行；不得写成 P1 存储整体完成。

## 13. 审核方法与限制

- R54–R57 只读源码与测试，不修改实现；R56 未新增动态反例测试，R57 读取并运行施工方新增测试，未新增测试。审核结论只关闭明确列举的问题范围，新增需求另行分期。
- 测试通过只表示已执行用例通过，不代替真实 store 时序、世界查询、渲染、网络与客户端接线验证。
- 依赖门禁以编译产物检查为准，源码文本扫描只作辅助；`api` 不依赖旧根、`net.minecraft.client`、v2 core/editor 实现，`editor.spi` 不依赖 `editor.internal`。
- C7 施工方全量测试与 jdeps 结果见实施文档 §4.4、§6；R57 已独立复跑全量构建 / 测试并检查 API 编译产物依赖，结果见本文 §5.2。施工方自检与独立审核仍分别记录。
