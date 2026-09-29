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

主线是「把动态求值从插值内核挪出去」，详见 `.workbuddy/memory/2026-09-28.md` 与
`2026-09-29.md`。已完成两步：

1. 抽出 `api/animation/eval/`（`Scope` / `ExpressionScope` / `ResolvedKeys` / `CurveSampler`），
   曲线与 Clip 退回纯数值插值 → eval → curve 单向依赖
2. 抽出 `api/animation/expression/SymbolTable{c}`：变量表 + 函数表 + 名字唯一性 +
   轨道引用重定向/解绑 + 键上的静态引用扫描

剩余：Clip 与 tracks 合并、值源分型、`Scope` 接口拆分。

## 已知不要再试的事

- 表达式求值**不要**改成「扁平指令数组 + switch 解释器」：实测比闭包树慢 2~3 倍（数据见 09-28 记录）。
  要提速只有生成 JVM 字节码或等将来的优化。
