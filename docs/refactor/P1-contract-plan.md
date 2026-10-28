# P1 契约、实施与验收

> 更新：2026-10-26。当前基线为 **`4a08774` 加工作区 C22**；C22 尚未提交。R68 对 C22 的两项原问题复核关闭；R69 按实现方式与性能整体审查，确认五项待修缺陷及若干结构性成本。正式全量 **70 suite / 631 用例**，v2 **64 / 565**，失败/错误 0、跳过 1（Windows 符号链接权限）。测试通过不代替缺陷关闭或真实游戏验收。
>
> 本文维护唯一当前实施状态与后续清单；详细问题及独立验证见[审核记录](<P1-review.md>)，行为规范见[主设计](<../design-animation-editor-framework.md>)、[UI 设计](<../design-editor-ui-framework.md>)与[音频设计](<../design-spatial-audio-api.md>)。全局导航见[文档索引](<../README.md>)。本轮凝练不修改公开接口或源码，不把设计讨论写成用户新裁决。
>
> **设计变更待实施（2026-10-28）**：主设计 [§1.3](<../design-animation-editor-framework.md#13-相机属性与全局轨道池2026-10-28-变更未实现>) 将属性/轨道改为全局轨道池与显式绑定，属性默认关闭，旋转统一四元数，移除专用注视阶段，改名为可选能力。这是破坏性模型变更，尚未进入本施工清单；本文 §1–§5 仍描述当前旧模型实现与待修项。迁移批次、数据版本与验收矩阵需另行立项，不能把新设计写成已实现。

## 1. 当前实现与边界

临时根包为 `cn.anecansaitin.free_camera_api_tripod_v2`，共 **307 个主源码 Java 文件**（api/core/editor，含包说明）；旧根包保留且未接线。最终清理旧实现后迁回 `cn.anecansaitin.free_camera_api_tripod`，不改 Legacy 名，不以搬包替代职责拆分。api 禁止依赖旧根、客户端、core/editor 实现；edit ↔ document 是 Q13 允许的例外。

| 范围 | 当前实现 | 当前限制 / 验收边界 |
| --- | --- | --- |
| 文档数据 | 稳定 ID、不可变机位/轨道/路径/符号/标记/采样声明；数值和原生 STEP 状态轨道；可动画矩形 | 全文档可见 KeyId 已校验；开放第三方类型须遵守不可变 SPI；叠加 Object 例外保留 |
| 文档发布与编辑 | CoreAnimationDocumentStore、EditOpApplier、Revision/历史/通知/延迟提交、换档清理 | R69-N1 监听异常隔离待修；历史只按 64 项，不等于 32 MiB 估算已做；同步方法不替代宿主线程调度 |
| 校验与依赖 | UnifiedDocumentValidator L1–L4、ID/引用/来源活动性、类型钩子、表达式绑定、依赖环 | R69-N2 采样钩子参数错配；R69-N5 DAG 指数展开；非时间轴扩展内部键归类型校验器 |
| 编译与求值 | DefaultExpressionCompiler、文本 LRU、CoreExpressionEvaluator、常量/公式曲线、STEP/LINEAR/HERMITE/wrap、共享工作/深度会话 | 空间读数仍为空；无 EvaluationCache 运行结果实现；查找/时长/索引有重复成本；AST 结构深度单独待查 |
| 路径与姿态 | LINEAR/BEZIER/CATMULL_ROM、YXZ、局部参考系正逆变换、注视、避障端口、布局逐帧检查 | R69-N3 距离→参数反算错误；真实碰撞、相机角度/象限转换、退化/瞬移/万向节游戏数值验收待接 |
| 帧事实 | FrameFactsPlanBuilder、CoreFrameFactsProvider、显式逐项需求、采样周期缓存与表版本 | 受控世界查询保守为空，不代表真实实体/方块/光照查询；世界/资源/快照生命周期待宿主装配 |
| 播放与事件 | 时间推进、启动前准备、循环完整区间、事件展开/顺序分派/部分执行、相机接管、CoreFrameExecutor | R69-N4 展开预算后置待修；宿主每帧驱动、相机释放/应用、实际事件载荷与 renderer 未接 |
| 类型扩展 | 自有冻结表、Anchor/Track/Sample Codec 和可选 validator、resolver/collector 表、TypeCapabilityService | common/客户端实际注册时序待游戏验证；能力缺失不使文档结构非法 |
| 存储与协议 | JSON/NBT/GZIP 结构编码、字节/深度/条目限制、路径解析、原子替换、SHA-256、有界列表、部分字段路径；FlatPlaybackProtocolCodec、接收编排 | 元数据枚举仍全目录扫描/哈希，NBT 仍整文件分配；NBT 深度/条目守卫在读取之后；TOCTOU、SavedData、真实网络授权/线程/传输未验收 |
| 恢复 | FileRecoveryService 自包含 JSON 槽/原子写入、16 槽裁剪；CoreRecoveryScheduler 防抖/周期/在途去重/异常保护 | 无体积/保留期淘汰；真实线程 start/onPublished/close 竞态、资源身份切换、恢复 UI 待验 |
| 会话与工具 | CoreEditorSession 草稿/事务/交互/预览/后端编排；文本支持文档/机位/轨道部分字段；缓动/批量/烘焙/注视捕获；消息 renderer | C22 修复溢出旧候选；真实 UI/输入法/语言切换未接；缓动多键重复重建；预览只构造候选，不等于统一校验通过 |
| 多机位质量 | ResolvedRect、OutputPixelRect、RigQualityStatus、旧帧采样身份、CANVAS_OFFSCREEN、停路零目标状态 | renderer/显存/帧时/档位/滞回与真实多视角提取均未实现或验收 |
| 叠加与音频 | OverlayElement 暂存；AudioRef/AudioRefId 身份与结构边界 | O1–O3 等设计补全；全部非音频阶段验收后再实施独立音频 API、可选接入、波形与同步 |

源码入口：[v2 API](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/api/>)、[core](<../../src/main/java/cn/anecansaitin/free_camera_api_tripod_v2/core/>)、[测试](<../../src/test/java/cn/anecansaitin/free_camera_api_tripod_v2/>)。

## 2. 有效裁决与契约约束

### 2.1 已确认决策

| 编号 | 有效结论 |
| --- | --- |
| Q1 | `_v2` 隔离新旧类型，旧代码不接线，最后迁回原根包。 |
| Q2 | 音频整体最后实施，AudioRef/AudioRefId 只保留身份与边界。 |
| Q3 | Symbols、Variable、CustomFunction 进入文档内核。 |
| Q4 | AnimationTrack sealed，ExtensionTrack non-sealed；内建类型 ID 保留，扩展伪造由校验拒绝。 |
| Q5 | AimOverride 位于 playback：Inherit/Disabled/Replace；宿主应用覆盖，eval 只消费生效状态。 |
| Q6 | TrackLookup 位于 eval，文档根不提供 trackLookup；expression 独立于 eval/block/edit/playback。 |
| Q7 | SampleArgs 名义接口，kind 与 args.type 一致；Overlay.content 等 O1–O3 定型，不采用泛型替代方案。 |
| Q8 | KeyframeTrack<T> sealed + Aim/Active/CameraSource 三个 record，原生值、共用 KeyId。 |
| Q9 | VALUE/UNKNOWN/COLLECTION_ERROR，只有 UNKNOWN 可由作者显式默认值兜底。 |
| Q10 | sampledAt 使用 long 游戏刻，与重采周期一致。 |
| Q11/Q12 | 早期布局延期和第二批范围的历史说明，不限制当前实施。 |
| Q13 | edit 位于 api.animation.edit，允许 edit ↔ document，其余禁止依赖不放宽。 |
| Q14 | AnimatedNumber 已落地，RigView.Rect 可动画，准备帧用已求值 ResolvedRect。 |
| Q15 | 十一项协议方向已确认并同步设计；契约与运行验收分开记录。 |

注册表选型已定 **O（自有 TypeRegistry）+ B（客户端能力服务）**；不在 api 暴露 NeoForge Registry，不把本机是否有执行器写成类型数据。N′（core 用 NeoForge 注册表实现既有接口）仅为未来有数据包/同步/别名迁移真实需求时的选项，启用前验证冻结/事件顺序、网络关系和独立构造。类型钩子与能力服务均已实现接口/编排，具体缺陷与接线边界见 §1。

### 2.2 修改时必须保留

- 中文 JavaDoc、NullMarked/Nullable、不可变数据及外层容器复制；开放 SPI 不由 Java record 自动证明深不可变。
- 稳定 ID 与显示名/type 分离；TrackId、可见 KeyId 全文档唯一。数值键只收 Constant/Formula，TrackRef 仅在受支持入口；原生状态轨道仅 STEP，持久化一次。
- 秒基准键时间有限非负、负零正规化、同轨时间唯一；整理乱序但不量化/静默覆盖。标记当前仍贡献 duration，不因无执行副作用删除该规则（A6 未另行裁决）。
- EditOp 纯意图，Batch 完整候选一次校验发布；无变化无历史，缺失目标明确拒绝。Revision 在同 store 换档/撤销/重做继续递增；提交标签由 EditSubmission 承载。
- 通知中同步提交拒绝 REENTRANT_SUBMIT，延迟提交完成后重查基版本；发布后监听错误不得破坏回执/其余通知。R69-N1 尚待修。
- LocalizedMessage 为不可变树；Document/File 诊断作用域分开；Fix 版本匹配，显示时生成独立 Component。
- 草稿原文、预览候选、发布文档分离；失败不得沿用旧候选。PASSED/INCOMPLETE 无 ERROR，FAILED 必含 ERROR，Incomplete 报实际覆盖与原因，不能替代逐帧检查。
- CurveSampleRequest 显式上下文/轨道/机位/时间；TrackRef 切到被引用机位。TrackIndex/FrameFacts 原快照引用必须同源；revision 数值不证明身份。wt 来自 WorldTime(dayTime, dayLength).progress，范围 `[0,1)`，不写死 24000。
- 工作/深度会话沿 TrackRef/变量/函数共享；资源失败及含资源失败的树不得进共享结果缓存。确定性结果的缓存需身份完整，不能仅凭公式文本；当前仅有编译缓存。
- AxisSample/SpatialResolution 明确缺失/不需要/值/错误，实际依赖错误阻断整帧。Sample UNKNOWN 可显式惰性兜底，采集错误不可兜底。
- AnimatedNumber 的 Keyed 仅 CLAMP/CLAMP；空曲线显式默认值，非空端点保持；动态矩形逐帧有限/正尺寸。布局不新增 TrackId 或 TrackRef 读数入口。
- Codec 归成员，根只组合；显式分支标签，data_version 必填且当前版本唯一接受。strictInt 精确拒绝小数/溢出；结构失败无候选、不调 validator，统一校验 ERROR 拒绝。部分 fieldPath 仅按证据填，不伪造行列。
- O1–O3 前 overlays 在根 Codec 双向拒绝非空内容；空列表/缺省/null 接受，压缩 Ops 键表一致；不得接受后丢弃。
- 请求按 RigId 覆盖且保留三态，未知机位拒绝。start 准备通过才换档；帧冻结快照/事实/姿态/布局/层级/质量，执行不重读 store。
- 事件按周期/区间→时间→同刻轨道序，连续同类型片段分派；在展开时限制预算（R69-N4 待修），部分执行不可回滚。
- 相机接管主线程捕获 provider，释放后禁止新调用/旧缓存；捕获中变化下次生效。旧纹理身份含世界/快照/活动/来源代际，恢复可见先新帧。
- 质量按完整输出矩形/画布交集计算，不改 FOV/构图；出屏不跳过依赖求值或停止来源。停路无目标时 0×0/密度0，不能伪装 offscreen。
- 资源路径/原子替换/恢复槽分离；有界列表截断须可见，返回条数不等于扫描/IO 上限。网络 encode 成功用非空 Unit.INSTANCE，失败缓冲丢弃。
- 会话/后端/预览覆盖不进文档历史；过期交互拒绝；指定已注册后端拒绝不回退。真正 UI 生命周期仍待客户端。

## 3. 轨道改名：已确认方向与当前实现

**当前实际契约**仍是 ExtensionTrack 强制 `withName(String)`，EditOpApplier 对曲线/三类状态/扩展分别分派；未迁移。方法纯派生、保留 TrackId/type/具体载荷，不能用通用 NamedTrack 包装器替代，否则 Codec 分派可能失败。当前强制设计本身不是性能缺陷。

D37 已确认新增独立可选 `api.animation.block.RenamableTrack` mixin：

```java
public interface RenamableTrack {
    AnimationTrack withName(String name);
}
```

不 extends sealed AnimationTrack，不改 permits；内建与扩展显式实现，具体类型协变返回。它把“有显示名”与“支持改名编辑”分开；具体副本仍由原类型构造，不会消除必需的类型知识。无能力时 UI 禁用、提交层明确 unsupported，不反射探测、不静默忽略。统一改名入口验证非空、ID/type/具体类及请求名称，按原 owner/位置替换后走 store。

| 方案 | 收益 | 成本 / 适用条件 |
| --- | --- | --- |
| 保持所有轨道强制 withName | 契约简单，所有名称保证可编辑 | 第三方固定名/只读类型也必须实现；通用编辑与基础数据绑定 |
| 可选 RenamableTrack（D37 已确认） | 能力可查询，不扩 sealed 树，支持固定名类型 | 新增 unsupported 分支；属于当前“全部名可改”的行为收紧，迁移前需补 UI 拒绝路径 |
| 注册 TrackRenamer<T> 适配器 | 不改第三方类也能提供编辑插件 | 多一套注册/冻结/缺失/类型匹配成本，只有真实集成需求时采用 |

方向已确认，本轮不改源码。迁移时移除/过渡扩展强制方法，内建实现 mixin，第三方显式重新编译或注册原类型适配器；Codec 字段不变，不提升 data_version。补能力支持/不支持、目标缺失、同名 no-op、错 ID/type/name/class、空/异常、Codec 载荷往返、Batch 拒绝与 undo/redo 测试。详见[主设计 §3.1.3](<../design-animation-editor-framework.md#313-轨道改名能力已确认方向可选能力尚未实现>)。

## 4. 施工与复核里程碑

提交 C 与审核 R 分开编号；R58–R65 原逐批建议未分别执行，后以 R66/R67/R69 合并审查，不重复计数为多次验证。凝练前的完整批次叙述和命令可从 Git 历史追溯；下表为当前有效摘要。

| 范围 / 基线 | 交付 | 当前结论 |
| --- | --- | --- |
| C5 / `7095284` | 稳定模型与全部已列 P1 契约；C5 修停路质量和 Unit 成功值 | R54 原范围关闭；33/219（v2 27/153） |
| `95c3dda` | core 文档/编辑/校验/表达式/帧准备/存储等首实现 | R55 九项缺陷，非当时通过；59/511 |
| C6/C7 / `64bc62a`、`40abc7e` 聚合提交 | 启动原子性、完整循环、Local补轴/掩码/避障、环、KeyId、wt、会话清理；形参作用域 | R56/R57 原反例关闭；60/542（v2 54/476），不证明规模最优 |
| C8 / 同上聚合提交 | 接管仲裁、冻结能力服务、实参作用域对照 | 已实现；令牌归属缺陷经 C17/R67 关闭 |
| C9 / `40abc7e` | 消息 renderer、缓动/批量/烘焙/捕获；请求新增 basePose/session | 已实现；R66 工具缺陷经 C17/R67 关闭，性能成本列 §5 |
| C10/C11 / `40abc7e` | Local/Embedded 接收、后端 auto/指定/open 编排 | 已实现；真实网络/UI 未接 |
| C12/C13 / `40abc7e` | NBT 字节适配/守卫、全文档可见 KeyId 唯一性 | 已实现；非可见扩展内部键仍归类型自身 |
| C14 / `4bffdc0` | NBT 文件 IO/原子保存/元数据 | 已实现；元数据分配与 IO 成本列 §5 |
| C15 / `dc7c9cc` | NBT 结构路径 | 经 C17/C22 修数值标签/同名字段反例，R68 原范围关闭 |
| C16 / `391946a` | GZIP有界载荷、`.nbt.gz`、JSON/NBT File 字段路径 | R66 四项问题，不扩大为格式全部兼容 |
| C17 / `4f1a641` | R66令牌/烘焙/捕获/NBT数值修复 | R67 N1–N3关闭，N4部分关闭；68/615 |
| C18 / `bc06d8c` + `ce3e777` | ResourceListing、返回上限/截断、有界堆 | 已实现；68/617；仍全目录扫描与哈希 |
| C19 / `77ca26f` | 防抖/周期恢复调度、在途去重与关闭 | 已实现；69/621；真实并发生命周期待验 |
| C20 / `6dd982f` | 相机应用→事件分派、失败/部分计数 | 已实现；70/625；真实端口未接 |
| C21 / `4a08774` | 部分文本字段解析、合并候选 | R67 发现P1及NBT残留；70/628 |
| C22 / 当前未提交 | float转换后检查、解析异常清候选；NBT定位限定结构；恢复提供者/捕获异常保护 | R68 两项原反例关闭；70/631（v2 64/565） |
| R69 / 同一 C22 工作区 | 整体实现/性能审查与文档整理，无源码修复 | 五项新发现待修，详见审核记录 |

### 4.1 C22 关闭证据与限制

R67-N1：合法 width=2 后输入 1e100，候选清空、ERROR、commit拒绝、原文保留、Revision不变；隔离复测 x/y/width/height，改为合法值后可提交。R67-N2：合法文档的根 update_every_frames 与 extension.data_version 不参与解码；再引入非法 name 后不能抢占路径，无法证明时为空。

恢复异常保护已复位在途标记，正式测试验证提供者失败后的再捕获；不等于真实 ScheduledExecutorService 周期与 start/onPublished/close 并发验收。原 R66-N3 的“开关避障旋转一致”正式断言仍可补强；原文误称“R67-N3”已校正。C22 是施工实现，独立关闭见审核 R68；不把施工方自检当独立审核。

## 5. 后续施工顺序与性能清单

<a id="p1-follow-up"></a>

**先修纯内核确定缺陷，再做可消除成本的局部优化，最后接真实游戏测量。** 本次只审查和整理，没有执行以下修复或能力迁移。

### 5.1 必修（对应审核 R69）

| 顺序 | 项 | 完成条件 |
| --- | --- | --- |
| 1 | R69-N1 监听异常隔离（P1） | 发布后返回成功回执；逐监听失败不影响后续变更/换档清理；延迟队列每请求完成恰好一次；不可回滚已发布快照 |
| 2 | R69-N2 采样 validator 参数（P2） | 将 sample.args() 作为类型实例；合法 Args<T> 验证实际调用，非法参数有原诊断，定位仍归 SampleId/args |
| 3 | R69-N3 距离→曲线参数（P2） | 段内累计弧长表与反查，绝对距离/百分比正确；共线非匀速 Bezier 四分之一位置=2.5，不以64段近似解释该反例 |
| 4 | R69-N4 事件预算后置（P2） | 超出剩余额度即停止候选构造，或按已排序轨道有界归并；保持周期/同刻顺序；测超限前工作与临时空间，不只测输出大小 |
| 5 | R69-N5 DAG 指数展开（P2） | 构图用节点完成状态/闭包memo，保留全局/形参作用域；n=8/12/16共享DAG的工作随节点/边与必要输出增长，不按调用路径指数增长 |

### 5.2 已证明的重复成本（建议优先消除）

| 项 | 当前成本 | 建议与边界 |
| --- | --- | --- |
| 缓动多键 | M个选键产生2M个操作；每次重建N键曲线，至少 Ω(MN)复制/扫描 | 一次构造最终键列表并 ReplaceKeys，仍只校验/发布一次；不泛称每次排序必为 NlogN |
| 索引构建却不用 | 每帧 O(T) 重建 SnapshotTrackIndex，S次轨道采样继续线性查找，最坏 O(ST) | 当前快照引用单槽索引，由索引提供 lookup，换档/候选不可混用 |
| 构图查轨 | 每T条轨道各线性 document.track，空轨也有 O(T²) 查找 | 一次 Builder 内建 ID 映射，不必引入全局缓存 |
| 路径段长 | 每次采样重建64分段总长，选段又重复算长，O(64P)并有向量/Segment分配 | 正确弧长表同时保存段长/跨段累计值，按Path/快照有界复用 |
| 文档时长/符号查找 | E个公式端点反复扫 duration；变量/函数/样本名每节点线性查找 | 一次快照绑定派生 duration/名字表，工作额度不宣称CPU硬上限 |
| NBT 元数据 | entry/list 对每文件 readAllBytes，峰值分配随单文件大小；list(0)也哈希全部 | 流式 SHA-256/元数据；返回上限只限制条目存储，不保证减少总读取 |

### 5.3 条件优化与尚未证明的风险

- 按 base snapshot identity + draft generation 缓存预览构造，避免多面板重复 apply；预览不发布、不替代最终校验。
- validator/graph/evaluator 共享有界编译缓存，只复用无来源句柄，诊断按本次 Revision/路径包装。当前缓存条数上限不限制总字符，4097字符失败源码仍被保留；超长输入绕过缓存或增加字符预算。
- 确定性运行结果缓存需先定义 random、相同入口/采样时间、预算计费、失败树过滤；不能把 compile LRU 当成求值缓存。
- AST 二元链递归并不由变量/函数会话深度覆盖；未动态确认 StackOverflow，列迭代遍历/结构深度检查风险，不记新增P级。
- CoreRecoveryScheduler 的状态/句柄跨定时线程与发布线程尚无统一同步；需明确 owner 线程或同步生命周期，并用可控交错测试 start/onPublished/close，不用真实游戏代替并发测试。
- 同一 provider 跨 store/候选复用时，采样缓存的 revision 签名不等于快照身份；需明确声明变化/资源重解析失效协议。当前不据此新增确定错误。
- 历史64项、恢复16槽尚无内存/总大小/保留期完整限制，已有明确延期，不能当已满足全部内存预算。

### 5.4 真实游戏场景验收矩阵

| 场景 | 必须收集的证据 |
| --- | --- |
| 原生双路 / 多路 | 独立视角提取、状态隔离、异常后主状态恢复，无重复模拟/事件/声音；着色器/优化mod兼容矩阵 |
| 来源与控制 | Player底图，Rig来源切换，接管登记/关闭/世界切换释放，暂停/定位/恢复不复用旧区间纹理 |
| 世界与空间 | 实体/玩家partial tick、瞬移/维度切换、锚点掩码、Local补轴、注视垂直/重合、真实碰撞端口 |
| 画布与质量 | 输出像素/GUI缩放/负坐标/各角落裁剪、完全出屏来源仍工作、窄条、动画尺寸分档/滞回/目标池；帧时、分配、GC、显存和画质 |
| 编辑器 | 文本/公式中间态、输入法/焦点/模态、取消/过期/后端切换、草稿恢复、工具一次历史项、语言切换 |
| 文件与恢复 | 异步IO回调/捕获版本、断写/损坏/路径拒绝、恢复选择、世界/会话关闭；SavedData装配 |
| 网络 | 真实包注册、发送/接收线程、版本/超限/授权、Local资源边界、失败不发布部分状态 |
| 性能测量 | 同一场景/文档/机位/键数/采样数、JDK/GC/分辨率/兼容mod，预热后分布与分配；不从孤立探针推导固定倍数或帧率 |

O1–O3、编辑器SPI与采样面板/洋葱皮等仍为独立功能阶段；音频全部最后实施。纯内核测试、游戏验收和未实施功能三者分开，不能把尚缺实现写成只差场景测试。

## 6. 验证与文档维护

本轮 R69 实际执行：

```powershell
.\gradlew.bat --offline compileJava test --no-build-cache --rerun-tasks --console=plain
```

BUILD SUCCESSFUL，任务重跑但未 clean；70 suite/631 用例，v2 64/565，失败/错误0、跳过1。沿用C22工作区，无源码/正式测试修改；隔离探针不计正式用例。JDK25 jdeps 检查 **387个API class**，旧根/客户端/core/editor禁止类型依赖0；editor.spi→internal为0；不据此宣称所有core/editor边界全重验。探针和测量方法见审核R69。

历史关键基线：R54 33/219；R55 59/511；R57 60/542；R66 68/611；R67 70/628；R68/R69 70/631。较早批次完整日志从Git历史读取，当前不逐批重复全量命令与同一待办。

主设计维护行为，本文维护实际交付和施工顺序，审核文档维护证据/编号；UML维护关系与实现映射。新发现不自动重开已关闭原问题，关闭只按明确反例范围。改名能力仍是建议待定；不得在接口未改时写为已迁移。完整历史文档可从整理前提交 `4a08774` 及之后工作区差异追溯；未提交C22不声称已推送。
