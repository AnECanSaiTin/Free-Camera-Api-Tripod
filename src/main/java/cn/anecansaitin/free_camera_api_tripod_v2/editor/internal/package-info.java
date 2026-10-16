/// 编辑器内部实现：**不承诺兼容**（主设计 §2.1、UI 设计 §3.2）。
///
/// ## 计划提供什么
///
/// 按 UI 设计 §3.1 的构件表，本包最终包含：
///
/// | 构件 | 职责 |
/// | --- | --- |
/// | `EditorScreenHost` | 组合组件、布局、覆盖层、输入、绘制和生命周期；**不持有文档写方法** |
/// | `InputRouter` | 单点接入全部输入，按相同层序命中和派发 |
/// | `FocusManager` | 当前焦点作用域、唯一键盘焦点、Tab 导航和焦点恢复 |
/// | `PointerCapture` | 鼠标按下后的拖动、释放和取消归属 |
/// | `OverlayHost` | 菜单、下拉、模态、提示的层序与输入屏障 |
///
/// 停靠工作区实现（`DockWorkspace`、`DockGeometry`、`DockController`）按 UI 设计 §8 归属本包或
/// `editor.layout`，具体分区在 U4 阶段确定。
///
/// ## 硬约束
///
/// - **不得**被 [cn.anecansaitin.free_camera_api_tripod_v2.editor.spi] 依赖。
/// - 组件容器只承担容器、裁剪和路由关系，**不负责自动推导业务依赖**；
///   对外返回只读子组件视图，替代旧实现的可修改列表暴露
///   （UI 设计 §5.1）。
/// - 绘制阶段不得提交文档、创建业务弹窗或清空重建组件（UI 设计 §5.2）。
/// - 不建设 DOM 式完整捕获/冒泡模型：只提供命中目标、有限父容器回退和明确屏障
///   （UI 设计 §4.1）。
///
/// 现有实现仍在 `core.editor.*`；迁移按 UI 设计 §9 的 U1–U4 分阶段进行。
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.editor.internal;

import org.jspecify.annotations.NullMarked;
