# 项目长期记忆：FreeCameraAPIAddition

NeoForge / Minecraft 模组工程，含一个相机动画编辑器。包名根
`cn.anecansaitin.free_camera_api_tripod`。

## 构建环境（必读）

- **不要在 Bash 里跑 gradlew**：该环境的 Bash 缺 coreutils（`ls`/`head`/`grep`/`uname`/`xargs` 全无）。
  用 PowerShell 工具，且它的 stdout 不返回，需重定向到文件再 Read。
- 编译（**必须显式指定 JDK 25**，系统默认 JAVA_HOME 是 JDK 21）：
  ```powershell
  Set-Location D:\Code\Java\FreeCameraAPIAddition
  $env:JAVA_HOME = "D:\Code\Java\Jdk\Oracle25"
  & .\gradlew.bat compileJava --offline 2>&1 | Out-File compile-out.txt -Encoding utf8
  ```
- 本机 JDK 目录：`D:\Code\Java\Jdk\{microsoft-jdk-17, microsoft-jdk-21, Oracle25, Oracle26}`
- `--offline` 仍会联网拉 Mojo 版本清单，但其余全部走 `~/.gradle` 缓存，整体约 30 秒。

## 代码约定

- 只读接口一律加 `c` 后缀：`CameraAnimationc` / `Keyframec` / `Curvec` / `Pathc` / `SymbolTablec`
- `@NullMarked` 包 + `@Nullable` 标注，注释用 Javadoc 的 `///`
- 可读性优先：注释解释「为什么这么定」，行不写废话

## 正在进行的大改造：动态关键帧 / 表达式求值解耦

主线是「把动态求值从插值内核挪出去，并拆掉职责过多的几个类」，详见
`.workbuddy/memory/2026-09-28.md` 与 `2026-09-29.md`。**四步已全部完成并提交**：

1. 抽出 `api/animation/eval/`（`Scope` / `ExpressionScope` / `ResolvedKeys` / `CurveSampler`），
   曲线退回纯数值插值 → eval → curve 单向依赖
2. 抽出 `api/animation/expression/SymbolTable{c}`：变量表 + 函数表 + 名字唯一性 +
   轨道引用重定向/解绑 + 键上的静态引用扫描
3. 删掉 `curve/Clip`：曲线只存一份（在 `CurveTrack` 里），按名入口是 `CameraAnimationc#curve(id)`
4. 值源行为（withConstant / copy）下沉为实例方法；`Scope` 只管环境（时间 + 轨道读数），
   名字解析由 `resolver()` 交给 `Expression.Resolver`

## 已知不要再试的事

- 表达式求值**不要**改成「扁平指令数组 + switch 解释器」：实测比闭包树慢 2~3 倍（数据见 09-28 记录）。
  要提速只有生成 JVM 字节码或等将来的优化。

## 工具踩坑（两条血泪）

- **改一段就提交一段**：出过一次事故——`src/main/java` 整个目录从工作区消失
  （`build/` 与 `src/main/resources` 都在，只有源码没了），未提交的一整天重构全部丢失，
  只能从 HEAD 恢复。**不要攒到最后再提交。**
- **Edit 同文件并发会互相覆盖**：一次消息里对同一个文件发多条 Edit，后面的基于旧内容写入，
  会把前面的改动抹掉（工具仍报 success）。同一文件必须一条一条串行改，改完 grep 复查一遍。
