# 注册表选型：复用 NeoForge 注册表 vs 自有 `TypeRegistry`

> 状态：**已裁决（2026-10-19）**。用户指示“按建议继续实现”：维持 **O（自有 `TypeRegistry`）**、
> 能力描述走 **B（客户端能力服务）**；`N′` 保留为将来演进选项。裁决记录见文末 §8。
>
> 本文回答“能否复用 NeoForge 注册表系统”，并与能力描述方案 A / B 做对比。
> 依据：设计 §2.2（注册表职责）、§4.2（注册与冻结）、§6.3（锚点注册）、§7.7（采样注册）、
> §8.1/§8.3（Codec 与网络）、D7/D12/D16/D24；实测对象为锁定依赖 **NeoForge 26.1.2.36-beta
> + Minecraft 26.1.2**（`gradle.properties`），证据见 §2。

## 1. 我们需要注册表做什么

| # | 需求 | 设计依据 |
| --- | --- | --- |
| R1 | 三个**开放类型表**：轨道、锚点、世界采样；ID 为 `Identifier`，第三方可注册 | D16、§2.2 |
| R2 | 启动阶段注册、之后**冻结**；运行期只读 | §4.2 |
| R3 | 文档 Codec 按 `type` 分派；**未知类型解码报错**，不静默默认 | §8.1 |
| R4 | 锚点另需**网络 Codec**（播放请求携带类型与完整参数） | D12、§8.3 |
| R5 | common 只放 ID 与 Codec；客户端解析器 / 采集器**单独注册** | §6.3、§7.7 |
| R6 | 纯内核测试**不需要**启动客户端或游戏世界 | §10 P1 验收 |
| R7 | 世界采样本轮**不跨端**（无服务端采样表） | D24 |
| R8 | “能力描述与校验”是注册表职责，但形状待裁决 | §2.2、计划 §7.2 |

关键观察：我们的注册表里放的是**类型描述对象**（`AnchorType` / `SampleType` / `TrackType`：
ID + Codec + 将来的能力/校验），**不是**游戏内容实例；锚点/采样的数据实例活在文档里，
不进注册表。

## 2. 实测：NeoForge 26.1.2 / MC 26.1.2 提供了什么

核对方式：`gradlew dependencies --configuration compileClasspath` 确认实际依赖为
`net.neoforged:neoforge:26.1.2.36-beta`；从
`neoforge-26.1.2.36-beta-universal.jar` 与 `minecraft-patched-26.1.2.36-beta-merged.jar`
抽取类文件后用 `javap -p` 读签名（历史残留的 21.x jar 不参与结论）。

| 能力 | 签名（节选） | 对我们的意义 |
| --- | --- | --- |
| 自定义注册表 | `NewRegistryEvent.create(RegistryBuilder<T>) → Registry<T>` | 可直接建三张类型表 |
| 注册表构建 | `RegistryBuilder`：`defaultKey`、`sync(boolean)`、`maxId`、`disableRegistrationCheck`、`onAdd/onBake/onClear` | 冻结前回调、ID 上限、同步开关 |
| 第三方注册入口 | `DeferredRegister.create(ResourceKey<Registry<T>>, namespace)`、`RegisterEvent.register(...)` | 与其它 NeoForge mod 一致的注册体验 |
| 查询与别名 | `Registry<T>`：`freeze()`、`getOptional(Identifier)`、`byNameCodec()`、`keySet()`、`addAlias/resolve` | ID 查询与**类型 ID 的 Codec 现成** |
| DFU 分派 | `RegistryCodecs` 只有 `homogeneousList`，**没有**通用的 `dispatch` 助手 | 分派仍需自己写 `Codec#dispatch` |
| 网络分派 | `StreamCodec#dispatch`、`IdDispatchCodec`（按数值 id 分派） | 网络侧有现成分派原语 |
| 注册表同步 | `RegistrySynchronization`、`DataPackRegistriesHooks.getSyncedCustomRegistries()`、`RegistryBuilder#sync(true)` | 自定义注册表**可**同步，但需要网络 Codec 与双端内容一致 |
| 数据包注册表 | `DataPackRegistryEvent.NewRegistry.dataPackRegistry(key, codec[, networkCodec][, builder])` | 用于“条目来自数据包”的注册表 |
| 条目附加数据 | `DataMapType` / `IRegistryExtension#getDataMap(...)` | 可给条目挂数据包 JSON 元数据 |
| 独立构造 | `RegistryBuilder#create()` 可脱离 FML 事件创建并 `freeze()` | 单元测试可造独立注册表，不必启动游戏 |

## 3. 三种落法

| 方案 | 形态 | 说明 |
| --- | --- | --- |
| **N** | `api` 直接暴露 `Registry<AnchorType<?>>` 等 MC 类型 | 完全复用注册表系统 |
| **N′** | 保留自有 `TypeRegistry` 接口；`core` 用 NeoForge 自定义注册表**实现**该接口 | 契约稳定 + 标准注册体验 |
| **O** | 自有 `TypeRegistry` 接口 + 自有实现（第九批已交付） | 最小依赖 |

### 3.1 对比

| 维度 | N（直接暴露 Registry） | N′（接口 + Registry 实现） | O（全自有） |
| --- | --- | --- | --- |
| 契约稳定性 | 差：`api` 绑死 MC/NeoForge 注册表类型，26.x 每次变动都波及 | 好：`api` 只有 5 个方法 | 好 |
| 第三方注册体验 | 标准（`DeferredRegister`/`RegisterEvent`） | 标准（同上，经接口读取） | 自造 API，需文档教学 |
| 冻结语义 | 由 NeoForge 在加载末期统一冻结 | 接口 `frozen()` 委托注册表状态 | 自实现 |
| ID 的 Codec | `byNameCodec()` 现成 | 同 N（实现内部用） | 自写（几行） |
| 解码分派 | 仍需写 `Codec#dispatch`（`RegistryCodecs` 无助手） | 同 N | 同 O |
| 网络分派 | `StreamCodec#dispatch` / `IdDispatchCodec` 可用；`sync(true)` 可让 id 双端一致 | 同 N，但我们**不需要**（R4 用类型自带 StreamCodec） | 类型自带 StreamCodec |
| 纯内核测试 | 可直接 `RegistryBuilder#create()`（**不必**启动游戏），但事件注册路径仍需 FML | 测试可用假实现或独立注册表 | 假实现即可 |
| 包依赖 | `api` 依赖 NeoForge 事件与 MC 注册表类型 | `api` 干净；`core` 依赖 NeoForge | 全干净 |
| 别名 / 数据包 / 数据映射 | 免费 | 经实现可用 | 无（本轮不需要） |
| 复杂度 | 低（无适配层），但把版本风险放进 `api` | 中（接口 + 适配 + 事件顺序 + 冻结映射） | 低 |
| 额外风险 | `sync(true)` 需网络 Codec 且要求双端注册内容一致；数据包注册表会把“类型缺失”变成数据包问题，与 R3 的“未知类型解码报错”冲突 | 两套视图需保证同一份数据、冻结时机一致 | 无 |

### 3.2 结论性判断

- **不建议 N**：我们的类型表不承载游戏内容、不需要数据包、不需要注册表同步；
  把 `Registry<T>` 搬进 `api` 只会引入版本耦合，而 `RegistryCodecs` 又不提供分派助手，
  N 省下的代码有限。
- **N′ 是唯一值得考虑的复用方式**：它把“标准注册体验 + 别名 + 冻结回调”留给 `core`，
  契约仍是第九批的小接口。代价是一个适配层与事件/冻结顺序的验证成本。
- **O 已满足当前全部需求**：三张表规模小、无数据包/同步需求；代价是第三方要学一个小 API。

## 4. 能力描述（A/B）与注册表选型正交

无论 N/N′/O，`AnchorType`/`SampleType` 都是“我们自己的描述对象”；能力描述放哪，
与注册表机制无关：

| | A：描述对象上声明能力 ID 集合 | B：客户端服务按类型查询能力 |
| --- | --- | --- |
| 表达位置 | `type.requiredCapabilities(): Set<Identifier>` | `SamplerCapabilities.missing(typeId)` 一类服务 |
| 谁能回答 | 解码 / 校验阶段（common） | 运行期（客户端，已注册执行部件后） |
| “客户端缺采集器” | 会被编码成类型数据，**可恢复缺失与数据非法混在一起** | 天然区分：缺执行部件 ≠ 参数非法 |
| 需要同步吗 | 不需要（ID 集合随代码） | 不需要 |
| 成本 | 契约多一个方法 + 能力 ID 命名空间 | 多一个服务契约与注册路径 |
| 与 D18/I9 | 有冲突风险（能力缺失被当成数据属性） | 一致（运行期错误/能力独立报告） |

补充：NeoForge 的 `DataMapType` 能让“条目附加数据”来自数据包，但它表达的是**静态数据**，
不是“本机是否装了某个采集器”；用它承载能力会把运行期能力伪装成数据。

## 5. 推荐

**分两步：**

1. **注册表机制：维持 O（自有 `TypeRegistry`）作为 `api` 契约**，第三方按现有契约注册；
   若后续出现“需要数据包扩展类型 / 注册表同步 / 别名迁移”的真实需求，再按 N′ 演进
   —— 演进时只需给 `core` 增加一个实现，`api` 与文档形状不变。
2. **能力描述：选 B（客户端能力服务）**，与注册表选型解耦；服务契约等到客户端
   解析器/采集器批次一起定义（那里才有“实际能否采集”的事实来源）。

若你更看重“第三方 mod 用 `DeferredRegister` 注册”的一致性，则选 **N′**：契约不变、
`core` 提供 `Registry` 支撑实现；需要额外验证 §6 的清单。

## 6. 若选 N′ 必须先验证（R0 探针）

1. 自定义注册表在 mod 加载序列中的**冻结时机**，与 `TypeRegistry.frozen()` 的映射；
2. `RegistryBuilder#sync(true)` 是否要求网络 Codec / 是否强制双端内容一致（我们倾向 `sync(false)`）；
3. `DeferredRegister.create(自定义 registryKey, modid)` + `register(IEventBus)` 在实际加载中可用；
4. 独立单元测试用 `RegistryBuilder#create()` + `freeze()` 是否无隐藏 FML 依赖；
5. 事件顺序：`NewRegistryEvent` → `RegisterEvent` → 冻结，与文档解码开始的先后关系。

## 7. 对已交付契约的影响

- 选 **O / B**：`api.animation.registry` **不变**，无需返工；只需在 §7.2 记录能力服务待补。
- 选 **N′**：`api.animation.registry` 的**接口不变**，新增 `core` 实现；`TypeRegistry` 的
  `register/freeze/frozen` 语义要注明“由注册表实现支撑”。
- 选 **N**：需要改写已交付的三个注册表接口与各自的类型描述（`api` 暴露 `Registry`），
  属于破坏性调整，不推荐。

## 8. 裁决（2026-10-19）

| 项 | 结论 |
| --- | --- |
| 注册表机制 | **O**：`api.animation.registry` 的 `TypeRegistry` 与三个类型描述保持现状；`core` 日后可直接实现它们，不引入 NeoForge `Registry` 类型到 `api` |
| 能力描述 | **B**：客户端能力服务；服务契约随客户端解析器 / 采集器批次定义（能力是运行期事实，不写进 common 类型描述） |
| 类型级校验钩子 | 暂缓：等客户端执行部件批次一并裁决；在此之前文档校验只走 `DocumentValidator` |
| N′ 演进 | 保留为选项：出现数据包扩展 / 注册表同步 / 别名迁移的真实需求时，仅新增 `core` 实现，`api` 与文档形状不变；启用前需完成 §6 的五项探针 |

据此，第十批（具体类型 Codec 与两阶段解码）直接基于自有注册表实现，不需要 `Registry` 适配层。
