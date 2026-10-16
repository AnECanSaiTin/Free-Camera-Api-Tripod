/// 编辑器 SPI：面板注册与可复用组件的**公开契约**（主设计 §9.4、UI 设计 §3.2）。
///
/// ## 允许依赖什么
///
/// - `api.*`：会话、编辑操作、文档与播放契约。
/// - 客户端 UI 类型：Minecraft 事件与绘制类型**可以**出现在客户端 SPI 中，
///   但不进入 `api.editor`（主设计 §2.1）。
///
/// ## 禁止依赖什么
///
/// - **不得**依赖 [cn.anecansaitin.free_camera_api_tripod_v2.editor.internal]。
///   公开组件需要内部实现时，由工厂返回契约实例。
/// - 不得直接修改文档：内置面板与第三方面板一律**通过会话编辑**
///   （主设计 §9.4）。
///
/// ## 计划提供什么
///
/// 按 UI 设计 §3.1 与 §8，本包最终包含：
///
/// - `PanelRegistry`：面板描述 ID、标题、工厂与默认布局位置、最小尺寸。
/// - 公开组件契约：稳定 UI ID、边界、可见与启用状态、布局、绘制、
///   **完整输入**（按键、字符、鼠标按下与释放、移动、拖动、滚轮）与生命周期。
/// - 宿主服务接口、主题与度量的只读接口、组件工厂。
///
/// 具体类型属于 U1/U2 阶段（UI 设计 §9），本批只建立分区。
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.editor.spi;

import org.jspecify.annotations.NullMarked;
