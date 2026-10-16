/// 编辑器：内置屏幕、面板、布局、主题与默认界面后端（主设计 D1 / §2.1）。
///
/// ## 分区
///
/// | 子包 | 职责 | 兼容承诺 |
/// | --- | --- | --- |
/// | `editor`（本包） | 内置界面实现：屏幕装配、默认后端 | 不承诺 |
/// | [cn.anecansaitin.free_camera_api_tripod_v2.editor.spi] | 第三方可依赖的面板注册与可复用组件契约 | **承诺** |
/// | [cn.anecansaitin.free_camera_api_tripod_v2.editor.internal] | 路由、焦点、捕获、覆盖层与组件容器实现 | 不承诺 |
///
/// ## 依赖方向
///
/// ```text
/// editor / editor.spi  →  api 契约与纯内核
/// core 宿主实现        →  api 契约与纯内核
/// ```
///
/// `core` 与 `editor` **不得互相依赖**（主设计 §2.1、§2.3）：
///
/// - 合成预览不通过导入 `core` 的合成实现完成，而是走 `api` 的
///   `CameraCompositionService` 契约。
/// - `editor.spi` **不得**依赖 `editor.internal`；公开组件需要内部实现时
///   由工厂返回契约实例。
///
/// 该约束以编译产物依赖检查或架构测试作为正式门禁，文本搜索只作辅助
/// （主设计 §2.3）。
///
/// ## 本包现状
///
/// 现有业务界面仍在 `core.editor.*`。迁移按 UI 设计 §9 的 U1–U4 分阶段进行：
/// 每个迁移后的屏幕必须把**全部**事件交给宿主，不能只迁移点击而保留旧滚轮分支。
/// 本包当前只有分区说明，不作为已迁移完成的标志。
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.editor;

import org.jspecify.annotations.NullMarked;
