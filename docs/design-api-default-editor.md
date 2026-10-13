# 内置编辑器能否成为 api 的默认实现

范围：`api.editor`（`EditorSession` / `EditorUiBackend` / `EditorUiHost`）、`core.editor`（44 个文件、13,162 行）、
`FreeCameraApiTripodClient`（客户端入口）、`EditorConfig`（`ui.modern_ui_compat`）、`build.gradle` 的依赖面。

前提：允许破坏性修改；**已定"不允许绕过"**——编辑器改数据只有一条路，见
[`design-evaluation-graph2-editor-constraints.md`](design-evaluation-graph2-editor-constraints.md)（下称"约束文档"，
引用其**重写后的版本**：图的容器改为自定义结构、对账的时机与开关已定、图的归属已定、撤销重做单独成节）。
结论先写第一节，后面是现状事实、三种"默认提供"的取舍、与"不允许绕过"的联动、迁移步骤与验收、
撤销重做机制的改进与风险。

---

## 1. 结论

**"把内置编辑器改成 api 的默认提供"要拆成三个问题，混在一起谈会得出错误结论：**

| 问题 | 答案 |
| --- | --- |
| 内置界面该不该走统一的注册 / 接管流程（成为 `EditorUiBackend` 的一个默认实现）？ | **该做，而且便宜**：改动约 20 行，收益是"一条路"。 |
| 13k 行 UI 代码该不该搬进 `api` 包？ | **不该**。`api` 现在 58 文件 / 4,910 行、**零 `net.minecraft.client` 依赖**（common 侧安全）；搬上去就变成客户端专属实现，还会把 `core.cmd_camera.*` / `core.animation.io` / 基础 mod 的 `ModifierManager` 一起拖上去——分层整个翻转。 |
| `api` 该不该给出一份"可复用的默认实现"？ | **该，但给的是"会话 + 求值判据 + 动作 + 可复用组件"，不是那层皮肤**。整份界面搬不动：`EditorPanel` 基类自己就吃 `GuiGraphicsExtractor` 与三种输入事件。 |

一句话版本：**默认实现留在主 mod，但要"注册进契约"；`api` 提供的是会话、判据、写动作与可复用组件，
而不是那份皮肤。**

顺带一个现在就存在的裂缝，它是上面第一条的真正价值所在：**内置界面走的是 core 的 `EditorContext`（宽），
外置后端走的是 api 的 `EditorSession`（窄）——默认实现并不遵守它自己对外发放的那份契约**。
把默认实现注册进契约，等于让"默认实现能不能只靠契约跑起来"变成一个可验证的问题；
而约束文档 §6 的 `reconcile` 正是那个验证工具——**它测的恰好是"会话层有没有真的走图"**，
不需要 Minecraft 运行环境（开关是静态布尔，见约束文档 §6.4）。

两份文档的相互关系可以先记住一句：**约束文档解决"写入绕不过判据"，本文解决"谁能写、写在哪个类里"。**
两者共用同一个前提（不允许绕过）与同一个验证工具（编辑脚本 + 对账）。

---

## 2. 现状事实

### 2.1 打开编辑器只有一条硬编码路径

`CameraEditorScreen.open()`（`CameraEditorScreen.java:1231-1245`）：

```java
EditorContext context = createContext();                  // 从 CmdCamera.INSTANCE 取 core 的模型 / 播放器 / 信息
if (EditorConfig.MODERN_UI_COMPAT.get()
        && EditorUiHost.open(new BuiltinEditorSession(context))) {
    return;                                               // 第三方后端接管
}
Minecraft.getInstance().setScreen(new CameraEditorScreen(context));   // 否则硬编码回退
```

也就是说：**内置界面不是"注册的后端"，而是这条 if 之后的兜底分支**。它同时意味着两件事——
外置 mod 无法"包装"内置界面（只能整体接管或完全不接管），而 `EditorUiHost` 自己也拿不到"默认实现"这个概念。

### 2.2 契约与实现的不对称

| | 规模 | 依赖 | 拿到的能力 |
| --- | --- | --- | --- |
| `api.editor`（契约） | 3 文件（`EditorSession` / `EditorUiBackend` / `EditorUiHost`） | 只 `Component`（common）+ jspecify；`EditorUiHost` 连 Minecraft 都不引 | `EditorSession`：只读模型 + 视口/播放/选择 + 加键/移键/删键/记录路径点 |
| `core.editor`（实现） | 44 文件 / 13,162 行 | `net.minecraft.client` 114 处、`blaze3d` 25、`glfw` 12；`core.cmd_camera.edit`(14)、`core.animation.io`(14)、`core.cmd_camera.playback`(2)、`CmdCamera`(2)、`CameraPose`(1)、基础 mod 的 `ModifierManager`(1) | `EditorContext`：动画 + 播放器 + 编辑模型 + 相机信息 + 视图参数 + 提示 + 二次确认 + 表达式编辑窗口 |

**这张表要横着读**：契约那格列的是"今天开出去什么"，而 §4 的结论是它该开出去的是**数据面**
（变量 / 函数 / 轨道 / 槽位来源的写动作），会话级的播放与选择动作只是其中一类。
不对称的真正代价不在行数，而在"实现能做的事比契约多得多"。

`BuiltinEditorSession`（`core/editor/BuiltinEditorSession.java:18`）只是把 `EditorContext` **适配**成窄契约，
给第三方后端用；内置界面自己从来不走它。

### 2.3 体量分布（决定"哪些能抽出来"）

| 子包 | 文件 | 行数 | 说明 |
| --- | --- | --- | --- |
| `core/editor`（根） | 16 | 5,717 | 屏幕、会话适配、上下文、历史、表达式窗口、语言、存储/浏览界面、视口接管 |
| `panel` | 12 | 4,445 | 面板：视口、时间轴、曲线图、变量、函数、关键帧、路径 |
| `widget` | 10 | 1,600 | 通用控件：按钮、文本、数值、表达式字段、菜单、确认框 |
| `layout` | 2 | 863 | 停靠布局与矩形 |
| `theme` | 2 | 368 | 绘制封装与图标 |
| `render` | 2 | 169 | 浮动填充与画中画渲染 |

### 2.4 逻辑与渲染不是干净两层（这条决定了"整体搬迁"不现实）

- `panel/EditorPanel.java:9-12`：面板基类直接引 `GuiGraphicsExtractor`、`CharacterEvent`、`KeyEvent`、
  `MouseButtonEvent`——布局状态与渲染、输入混在同一个类里。
- `core/editor/EditorContext.java:27-51`：上下文字段就是 `CameraEditorModel` / `CameraPlayer` / `CameraAnimation` /
  `CameraInfo`，构造时还要建一帧的求值作用域（`ExpressionScope.of`）。

### 2.5 `api` 的纯度是硬资产

`api` 整包 58 文件 / 4,910 行，`net.minecraft.client` **零引用**（只有 `com.mojang.logging`、
`com.mojang.serialization` 这类 common 侧库）。服务端 / 数据包 / 指令侧引用这套 api 的mod 不受客户端界面影响——
这是"不要把 UI 搬进 api"最实际的理由。

---

## 3. 三种"默认提供"的含义与代价

### 3.1 甲：把内置界面注册成默认后端（推荐）

```java
// core.editor：默认后端，优先级最低，谁都能盖住它
public final class BuiltinEditorBackend implements EditorUiBackend {
    /// 默认实现的优先级：比任何第三方后端都低，只有没人接管时才轮到它
    public static final int PRIORITY = Integer.MIN_VALUE;

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean openEditor(EditorSession session) {
        Minecraft.getInstance().setScreen(new CameraEditorScreen(contextOf(session)));
        return true;
    }
}
```

- `FreeCameraApiTripodClient` 构造里注册它（那里已经是客户端专属登记点，见
  `FreeCameraApiTripodClient.java:16-21`）。
- `CameraEditorScreen.open()` 收敛成"建会话 → `EditorUiHost.open(session)`"，**不再有硬编码回退**。
- `EditorConfig.MODERN_UI_COMPAT` 的语义从"要不要问后端"简化成"允许不允许第三方接管"（或直接删掉，见 §7）。

**收益**：默认与第三方走同一条路；第三方可以"包一层"（先做自己的事，再转发给默认后端）；"默认实现遵守契约"
从此可验证。

**真实工作量在这里**：现在 `EditorContext` 是**先建**的，会话是**后包**的；要反过来，得让默认后端能从
`session` 拿回它需要的东西。两条路：

1. **给会话加 core 侧出口**（`unwrap`）——能跑，但等于承认会话不完整；
2. **把"建上下文"搬进默认后端**：`open()` 只负责"建 animation 的会话 + 询问后端"，默认后端自己建
   `EditorContext` 与屏幕——推荐。会话本身则只依赖 `api`（`CameraAnimationc` 等只读契约 + 写动作），
   不再需要 `CmdCamera.INSTANCE`。

顺带说清一个初始化顺序：`open()` 现在靠 `CmdCamera.INSTANCE == null` 判断"能不能开"
（`CameraEditorScreen.java:1232`）。搬进默认后端时这条判断要么跟着搬，要么改成"会话建不出来就不询问后端"。

### 3.2 乙：把 UI 代码搬进 `api`（不建议）

| 代价 | 具体 |
| --- | --- |
| api 变成客户端专属 | `Screen` / `GuiGraphics` / `glfw` 只在客户端 dist 可用；`api` 从此要带 `Dist.CLIENT` 的加载约束，§2.5 的纯度资产直接归零 |
| 层间翻转 | UI 依赖 `core.cmd_camera.edit` / `core.animation.io` / `CameraPose` / `CameraPlayer` / `CameraInfo`——要么把这些也搬上去，要么在 api 里给它们定接口（后者等于把 §3.3 的工作先做一遍） |
| 跨 mod 依赖 | `core.editor` 引了基础 mod 的 `ModifierManager`（`build.gradle:165` 是 `implementation files("libs/free_camera_api-…jar")` 的硬依赖）；搬上去会让"api 包"依赖基础 mod 的实现细节 |
| 契约与实现同包 | 谁都能引到的 13k 行 UI 会变成事实上的公开 API，以后每次改面板都是破坏性变更 |

真要在未来把编辑器拆成独立模块，正确的落点是**第三个包**（例如 `editor`，与 `api` / `core` 并列），
而不是把它塞进 `api`。

### 3.3 丙：`api` 提供"可复用的默认实现"（该做，但要分层渐进）

拆成两层看，只有第一层是当下能做的：

- **逻辑层（可做）**：会话、求值判据（约束文档 §4 的自定义图结构 + `reconcile`）、受控写动作
  （约束文档 §7 的 `AnimationEdits`）、面板的**模型**（列表项、字段描述、停靠布局树、时间轴换算）。
  目标是尽量不引 Minecraft 客户端类。自定义图结构对这一层是直接加成：它替掉了 Guava 的图容器，
  并顺手切断了 `eval → track` 的反向依赖（约束文档 §4.3），使"逻辑层不引渲染 / 轨道具体类"更接近可达成。
- **渲染层（留在 core）**：`Screen`、面板、控件、主题、输入分发。

阻力在 §2.4：`EditorPanel` 与 `EditorContext` 把"逻辑"和"渲染 / 播放 / 存档"焊在一起。要抽，先得把
`CameraPlayer`、`CameraEditorModel`、`CameraInfo`、存档访问这四项定成 `api` 接口——这是"周"级的活，
不是"小时"级。

**建议的渐进顺序**（每一步都能单独交付）：

1. **求值判据先自立**：把 `EvaluationGraph2` 的容器从 Guava 换成自定义结构（约束文档 §4）。
   它是后面所有动作的地基——收窄数据类的写方法（P2）要求图的类型出现在 `api` 公开面上，
   而 `MutableGraph` 的可变面太大，暴露出去等于给"绕过门面改图"开口子；
2. 会话补写动作（与"不允许绕过"同一件事，见 §4）；
3. 默认实现注册进契约（§3.1）；
4. 把"编辑器里的编辑器"——表达式编辑窗口、变量 / 函数面板——做成 api 上的可复用组件，
   第三方界面能直接摆进去；
5. 最后才谈逻辑与渲染的完整剥离。

第 4 步是**性价比最高的一步**：外置 mod 现在最痛的不是"拿不到整份编辑器"，而是"想省掉写一个表达式编辑器"。

---

## 4. 与"不允许绕过"的联动（这条是前提，不是附注）

约束文档已定：改数据只有一条路（`AnimationEdits`，属于 `api.editor`）。由此推出三个必须同时做的动作：

1. **`EditorSession` 必须补齐写动作**：变量增删改名 / 换来源 / 绑轨道、函数增删改名 / 改形参与函数体、
   轨道增删改名、关键帧槽位来源、加键 / 删键 / 移键、粘贴、整份替换（对照约束文档 §3.1 的写入点清单）。
   现在它只开到"关键帧与路径点"，**默认实现自己都编辑不了变量——它靠的是 `EditorContext` 直连数据**。
2. **默认实现必须走契约**：只有默认实现也走 `EditorSession`，这份契约才被真正验证过；
   而只有契约补齐了动作，默认实现才走得通。两者互为前提。
3. **`AnimationEdits` 不缓存图**：它只做"对动画对象方法的转发"，图实例取自动画（约束文档 §9.1）。
   理由是会话可能不止一个——外置 mod 的会话与内置编辑器各自建一个 `AnimationEdits` 叠在同一份数据上，
   若各自持一张图，对账会立刻报出一堆假 `Drift`。这一条对"默认实现 + 包装"（§3.1）尤其要紧：
   被包装的默认实现与外层后端共用同一个会话对象，所以它们必须共用同一张图。

一句话：**"默认实现注册进契约"与"不允许绕过"是同一件事的两面。**

---

## 5. 迁移步骤与验收

| 步骤 | 改动面 | 验收判据 |
| --- | --- | --- |
| 1 契约补写动作 | `api.animation.eval`（自定义图结构，约束文档 §4）+ `api.editor`：`EditorSession` + `AnimationEdits` | 用会话跑一遍编辑脚本，每步对账为空（约束文档 §10）；图侧原有用例全绿 |
| 2 默认后端注册 | `core.editor.BuiltinEditorBackend` + `FreeCameraApiTripodClient` 注册 + `open()` 收敛 | 不装第三方后端时行为与今天一致；装了第三方后端则按优先级接管；`open()` 里不再有硬编码回退 |
| 3 可包装 | `EditorUiHost` 给一个"交给默认实现"的入口（如 `fallback(session)`） | 写一个只包一层的示例后端，能把默认界面调起来 |
| 4 组件复用 | 表达式编辑窗口 / 变量面板抽成公开组件 | 第三方界面里能直接摆进一个表达式编辑窗口 |
| 5 逻辑与渲染分离（远期） | 会话 / 模型层去掉 Minecraft 依赖 | `api` 仍无 `net.minecraft.client` 引用（grep 判据） |
| 6 撤销重做改进（与约束文档 §9.3 同步） | `EditorHistory` 的捕获点搬到动作边界；节流漏捕；一次撤销不建两张图 | 编辑脚本每步之后快照数按预期增长；一次拖拽只产生一个撤销项 |

---

## 6. 撤销重做：现状、常见做法与三步改进

约束文档 §9.3 把这件事定在"与图无关"上：**`EditorHistory` 里一行图的逻辑都不该有**（现在是如此，
不要为了重建把它弄脏）。它今天有两个真正粗糙的点，外加一处与"重建"叠在同一条路径上的浪费。

### 6.1 现状（`core/editor/EditorHistory.java`）

| 项 | 现状 |
| --- | --- |
| 快照 | `AnimationCodec.animationToJson(animation)` 整份 JSON 字符串，上限 64 个 |
| 恢复 | `animationFromJson` 再 `copyFrom` 原地替换——**不能换对象**，动画实例被播放器与编辑器多处持有 |
| 触发 | `CameraEditorScreen.extractRenderState` **每帧**调一次（`:235`），内部 250ms 节流 |
| 相同判定 | 与当前快照字符串相等则跳过 |

**整份快照本身不算离谱**（见 §6.2），粗糙的是捕获与粒度：

1. **节流漏捕**：`lastCaptureMillis` 只在成功压栈时更新（`:66`），命中节流提前返回时不推进（`:42-44`）。
   于是"距上次压栈不足 250ms"被反复重算，不是滑动窗口——一次操作落在窗口内会被**整个漏掉**，
   撤销栈里没有它（`canUndo` 不会因此变多，但那次编辑也永远不会回来）。
2. **粒度按时间切，不按用户意图切**：一次拖拽跨越两个 250ms 窗口就产生两个撤销项，
   用户要按两次 Ctrl+Z 才回到拖拽前；而 64 个快照在持续拖拽下约 16 秒吃满。
3. **一次撤销建两张图**（这是约束文档 §9.2/§9.3 的交点）：`restore` 先 `animationFromJson`
   （读档收尾会判一次环、建一张图），再 `copyFrom`（重建图），两次完整建图。

顺带一个**不算粗糙但要知道**的：恢复时 `copyFrom` 走 `symbols.replaceFrom`，变量与函数都是**重建出来的
新对象**（`CameraAnimation.java:482` 明确"装进来的是副本"），而 `ExpressionEditorWindow.java:188` 的
`private @Nullable Variable selected` 是**按对象身份**存选中的——撤销之后那个引用悬在旧对象上。
现在各面板的选中多半按名字查（`selectedVariable()` / `selectedFunction()` 都是查名字），所以不会崩，
但新增代码不要引入"存 `Variable` / `CustomFunction` 引用"的用法。

### 6.2 常见软件怎么做（按代价排）

- **命令模式 + 反向命令**（IntelliJ / VS Code / Office / 多数图形编辑器）：每个用户动作一个撤销项，
  粒度最准、内存最省；代价是每种编辑都要写一遍逆操作。
- **持久化数据结构 + 结构共享**（Figma / 部分函数式编辑器）：快照 O(1) 或 O(log n)，可做时间旅行；
  代价是要把数据换成不可变结构。
- **整份快照栈**（小型文档编辑器、3D 工具早期实现）：就是现在这种，简单但内存与 CPU 随文档线性增长；
  **关键差别在捕获点——它们通常在"用户动作边界"捕获，不靠时间戳节流**。
- **操作日志 + 合并**（文本编辑器）：连续输入合并成一"团"（undo grouping）。

### 6.3 建议的三步（与约束文档 §9.3 同一份排期）

| 步 | 做法 | 收益 |
| --- | --- | --- |
| 1 | `capture()` 从帧循环挪到**编辑动作边界**（各面板动作收尾，或 `AnimationEdits` 出口统一调一次）；拖拽类连续操作用"开始捕获 / 结束合并" | 撤销粒度回到用户意图，同时不再每帧序列化整份动画 |
| 2 | 暂不改结构的话，让节流命中时也刷新 `lastCaptureMillis` | 先堵住"窗口内操作被漏掉" |
| 3 | 一次撤销不建两张图：`restore` 复用同一张，或读档路径跳过重复判环 | 撤销 / 读档各省一张图 |

**命令模式先不排队**：它解决的问题（粒度、内存）在第 1、2 步里能解决大半，而代价是每种编辑一份逆操作。

---

## 7. 风险与待定

1. **注册时机与静态表**：`EditorUiHost` 是静态注册表（`EditorUiHost.java:18`）。默认后端与第三方后端
   各自在什么时机注册（mod 构造期？客户端 setup？）？会不会因为热重载 / 多实例而重复？
2. **"包装"与"接管"要分开**：第三方只想加一个面板时，应该能"转发给默认实现"，而不是被迫重写整个界面。
   这需要在契约里明确"一个后端可以把会话交回默认实现"。
3. **`MODERN_UI_COMPAT` 的归宿**：新流程下它是"允许第三方接管"的开关，还是干脆删掉（默认永远询问后端）？
4. **客户端专属约束**：默认后端类要 `Dist.CLIENT`；`api` 里将来若放任何客户端组件，
   需要 client-only 子包 + 加载器注解，且不能让 common 侧引用到。
5. **基础 mod 依赖**：`EditorContext` 引 `ModifierManager`（基础 mod 的实现类）。默认实现留在 core 才自然；
   若哪天要剥离，先抽接口。
6. **`api` 与 `core` 的语义边界**：本文建议 `api` = 契约 + 会话 + 求值判据 + 可复用组件，
   `core` = 默认实现 + 皮肤。这条边界一旦定下，以后新增 UI 代码时"放哪边"就有了判据，不必每次重新争论。
7. **撤销捕获点搬到动作边界之后，谁来保证"改完一定捕获"**：这是一条新的纪律（现在靠每帧调用兜住了所有路径）。
   约束文档 §10 的编辑脚本用例应当同时断言"每步之后 `canUndo`/快照数按预期增长"，
   否则会出现"撤销粒度对了，但某条路忘了捕获"的新裂缝——与图的"漏调"是同一类问题，用同一套用例守。
