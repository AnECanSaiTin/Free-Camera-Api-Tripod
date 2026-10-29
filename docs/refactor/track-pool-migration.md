# 全局轨道池迁移施工记录

> 目标：主设计 §1.3（2026-10-28）的破坏性轨道迁移。旧文档模型仍在，本文件只记录迁移进度与决策，不把未完成项写成已实现。

## 已确认边界

- 完整迁移到文档级全局轨道池 + 属性显式绑定；
- 提升 `data_version`，旧文档明确拒绝，不做自动迁移；
- 首版默认：
  - 活动、相机来源保留为池内状态轨道，由属性绑定引用；
  - 锚点 / 参考系暂作为机位静态配置；
  - 组合来源引用空轨道时整个来源不可用；
  - 事件轨道执行启用独立于属性绑定；
  - 轨道池顺序只用于展示与事件稳定分派；
  - 不提供旧文档迁移工具。

## 阶段

| 阶段 | 内容 | 状态 |
|---|---|---|
| M1 | 可选 `RenamableTrack`，移除 `ExtensionTrack.withName` 强制方法 | 已完成 |
| M2 | `Quaternion` 数据与最短弧 SLERP | 已完成 |
| M3 | `QuaternionTrack` 基础轨道与 Codec | 已完成（尚未进入文档池） |
| M4 | `PropertyBinding`、数值/布尔/相机来源/四元数来源、位置/旋转/FOV/活动/矩形/避障属性、`RigOutput`、`PlaybackRange` | 已完成 API 基础 |
| M5 | 新 `CameraRig` / `CameraAnimation` 数据形状与 `data_version=2` Codec | 基础已完成：`TrackPoolAnimation` / `PooledRig` / `TrackPoolCodec`；尚未替换旧文档根 |
| M6 | 统一校验、依赖图、求值、帧准备、EditOp 迁移 | 部分：绑定/常量域校验、公式编译/符号表校验与循环检测、属性求值、变量/自定义函数/变量 TrackRef 求值、完整 `t/p/wt` 输入、wrap、播放范围、EditOp、store、文件服务、依赖图、帧准备、相机来源活动检查、World/Local 姿态组装、避障修正、帧合成、播放控制器、恢复调度、共享/深复制与采样端口已完成；真实宿主碰撞未开始 |
| M7 | 编辑器属性面板、共享/影响面、Euler/Quaternion 视图 | 部分：UI 无关编辑会话、文本属性编辑、共享/深复制、删除影响面、机位增删改、四元数/Euler 互转、文件服务与资源目录已完成；GUI 暂不实现 |
| M8 | 旧测试重写、旧模型删除、全量 JVM 验收 | 未开始 |

## M1–M5 交付

- `api/animation/block/RenamableTrack.java`
- `api/spatial/Quaternion.java`
- `api/animation/block/QuaternionTrack.java`
- `api/animation/property/`：
  - `PropertyBinding`
  - `NumericSource`
  - `BooleanSource`
  - `CameraSourceProperty`
  - `QuaternionSource`
  - `PositionProperty`
  - `RotationProperty`
  - `FovProperty`
  - `ActiveProperty`
  - `RectProperty`
  - `ObstacleProperty`
  - `RigProperties`
  - `RigOutput`
  - `PlaybackRange`
- `api/animation/pool/`：
  - `TrackPoolAnimation`：`data_version=2`、全局 `tracks`、机位、相机来源绑定、播放范围；
  - `PooledRig`：静态配置 + `output + properties`；
  - `TrackPoolCodec`：曲线/四元数/布尔/相机来源/扩展轨道同池分派。
- `core/animation/pool/TrackPoolValidator`：重复 ID、缺失引用、绑定类型不匹配（数值/布尔/相机来源/四元数）与 Euler 子来源检查。
- `core/animation/pool/TrackPoolEvaluator`：默认关闭、数值曲线 STEP/LINEAR/HERMITE、wrap、活动/相机来源状态、四元数轨道与 Euler 组合；公式关键帧的 `t` 使用原始文档时间，不受 wrap 后的采样时间影响。
- `core/animation/pool/PropertyFormulaEvaluator`：属性/关键帧有限公式（`t`、算术、确定性内建函数、惰性 `select`）；`p`/`wt`/变量/自定义函数/`sample`/`noise`/`random` 返回结构化错误。
- `api/animation/pool/TrackPoolAnimation`：自动播放时长（未绑定轨道、标记、音频、叠加）与手动播放区间。
- `api/animation/edit/TrackPoolEditOp` + `core/animation/pool/TrackPoolEditApplier`：增删改轨道、输出/属性绑定、相机来源绑定与播放范围；删除仍被引用的轨道拒绝。
- `api/animation/pool/TrackPoolSnapshot` / `TrackPoolEditResult` + `core/animation/pool/TrackPoolDocumentStore`：原子提交、Revision、64 步撤销/重做、监听异常隔离、监听期间重入提交拒绝。
- `core/animation/pool/TrackPoolDocumentCodec` / `TrackPoolFileService`：JSON/NBT/NBT.GZ 往返、旧 data_version 拒绝、8 MiB 读取/解压上限、原子替换。
- `core/animation/pool/TrackPoolDependencyGraph`：属性/相机来源绑定到轨道身份的显式使用索引，供删除影响面与使用次数查询。
- `TrackPoolEvaluator` 支持 CLAMP / LOOP / PING_PONG 曲线 wrap。
- `api/animation/pool/TrackPoolFrame` / `TrackPoolFrameResult` + `core/animation/pool/TrackPoolFrameFactory`：整帧属性求值、任一错误整帧拒绝、播放范围冻结、相机来源 Rig 必须处于活动状态。
- `core/animation/pool/TrackPoolPoseBuilder`：未接管分量继承基础姿态/默认矩形；位置/FOV 覆盖；四元数转 YXZ；ResolvedRect 组装；默认矩形分支必须与求值分支一致；World / Local 锚点平移与逐轴旋转跟随。
- `core/animation/pool/TrackPoolObstacleCorrector`：启用避障时通过 `ObstacleResolver` 修正位置；禁用不调用端口；端口不可用/缺 margin 明确失败。
- `core/animation/pool/TrackPoolFrameComposer`：把求值帧、机位静态配置、基础姿态、默认矩形与锚点组合成 `RigPose` 列表；可选避障端口；`composeVisible` 仅输出启用机位。
- `core/animation/pool/TrackPoolRuntime`：播放控制器 + 帧合成 + 宿主基础姿态/矩形/锚点/采样/避障登记，输出 Prepared/Rejected。
- `TrackPoolEndToEndTest`：轨道池 → 属性绑定 → NBT.GZ 保存/读取 → 播放 → 帧 → 姿态的 JVM 端到端验收。
- `core/animation/pool/TrackPoolRigCopier`：复制机位默认共享引用轨道，只生成新 RigId/名称。
- `core/animation/pool/TrackPoolRigDeepCopier`：为引用内建轨道生成新 TrackId/KeyId 并重写属性绑定；扩展轨道明确拒绝深复制。
- `core/animation/pool/TrackPoolTrackDuplicator`：单条内建轨道深复制；会话提供 `duplicateTrack`。
- `TrackPoolEditOp` 增加机位增删改；相机来源常量/状态键中的 Rig 引用参与校验。
- `core/animation/pool/TrackPoolEditorSession`：UI 无关的提交、撤销/重做、共享/深复制、单轨道复制、删除影响面、属性更新、文档改名与轨道/机位增删改。
- `core/animation/pool/TrackPoolTextEditor`：数值/布尔字段文本编辑、`=公式` / `#轨道` 数值来源、空白关闭属性、Euler 度数旋转文本、四元数 `x,y,z,w` 文本与非法输入拒绝。
- `Quaternion.fromEuler` / `toEuler`：YXZ 欧拉角与四元数互转，供 Euler/Quaternion 双视图使用。
- `core/animation/pool/TrackPoolDocumentService`：文件读取 → 校验 → 编辑会话，保存当前快照；旧 data_version 文件拒绝。
- `core/animation/pool/TrackPoolResourceService`：有界资源枚举、受限名称（拒绝路径穿越）、读写删除。
- `core/animation/pool/TrackPoolPlaybackController`：播放/暂停、推进、按播放区间循环、定位、采样端口、进度/世界时间输入与逐帧准备。
- `core/animation/pool/TrackPoolRecoveryScheduler`：发布防抖保存、周期保存、立即保存与关闭；原子写入，失败不破坏原文件。
- `api/animation/pool/TrackPoolEvaluationInputs` + `PropertyFormulaEvaluator`：`t` / `p` / `wt` 显式输入；`TrackPoolEvaluator` / `TrackPoolFrameFactory` 已支持完整输入入口，旧单时间入口保留为进度 0/世界时间 0 的便捷重载。
- `api/animation/pool/TrackPoolSampleSource`：显式采样端口；无端口时读数为可兜底的 UNKNOWN，采集错误不可兜底。
- `PropertyFormulaEvaluator`：文档变量、自定义函数、变量 TrackRef 读数；递归深度上限 32；`sample(...)` 通过显式采样端口读取，VALUE 参与求值，UNKNOWN 只允许显式 fallback，COLLECTION_ERROR 不可兜底。
- `TrackPoolValidator`：属性公式与曲线关键帧公式做编译检查；常量 FOV/宽高必须为正、避障 margin 必须非负；marker/sample/audio ID 与 sample 名唯一；池内拒绝 AimTrack 与伪造内建类型的扩展轨道；扩展时间轴键严格递增且 KeyId 唯一。
- `TrackPoolSymbolValidator`：变量/函数名字、保留名、重复、公式引用、函数元数、采样名与变量 TrackRef 检查；函数形参在函数体内按局部名处理；变量/函数依赖循环检测。
- `TrackPoolValidator`：全局池曲线禁止携带旧相机通道。

测试：`QuaternionTest`、`QuaternionTrackTest`、`PropertyModelTest`、`TrackPoolAnimationTest`，以及改名能力拒绝测试。

## 验证基线

```powershell
.\gradlew.bat --offline test --rerun --console=plain
```

- **99 suite / 734 用例**
- v2：**93 / 668**
- 失败/错误 0，跳过 1（Windows 符号链接权限）

## 下一步

1. 建 `CameraAnimationV2` / `CameraRigV2` 或直接替换旧 records，先实现 `data_version=2` 结构与 Codec；
2. 轨道池类型统一为 `List<AnimationTrack>`，让曲线/四元数/状态/扩展轨道同池；
3. 属性绑定求值与默认关闭语义；
4. 旧模型与旧测试清理。
