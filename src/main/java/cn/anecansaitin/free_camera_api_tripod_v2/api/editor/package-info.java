/**
 * 编辑器会话、视图状态、交互请求与 UI 后端契约（主设计 §5、§9；UI §3、§6）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.editor.EditorSession}：快照、提交、
 *       草稿事务与 {@code EditDraftState}、预览、撤销 / 重做、播放控制、视图状态、交互请求与关闭。</li>
 *   <li>{@code SessionViewState} / {@code TimelineView} / {@code SnappingSettings} /
 *       {@code SnapSource} / {@code PreviewRange} / {@code TrackVisibility} /
 *       {@code EditorToolState}：会话 / 视图状态，不写文档、不进撤销历史。</li>
 *   <li>{@code InteractionId} / {@code InteractionOption} / {@code InteractionRequest}：带选项的
 *       交互请求，消息使用不可变 LocalizedMessage。</li>
 *   <li>{@code EditorUiBackend} / {@code EditorUiHost} / {@code EditorBackendSelector}：后端注册、
 *       选择、实际 open 钩子与打开流程；不暴露客户端渲染类型。</li>
 * </ul>
 *
 * <h2>边界</h2>
 *
 * <p>本包只定义通用契约，不依赖客户端 UI 类型、`core` 实现或 `editor.internal`；具体屏幕、
 * 输入路由、组件与渲染在 `editor` / `editor.spi`。会话不直接修改文档，所有写入经
 * `EditSubmission` / 文档存储；播放覆盖只属于预览，不改写文档默认值。所有方法限宿主主线程。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.editor;

import org.jspecify.annotations.NullMarked;
