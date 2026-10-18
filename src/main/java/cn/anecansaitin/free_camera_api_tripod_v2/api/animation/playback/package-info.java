/**
 * 时间推进、播放请求、帧准备、事件执行与多路合成契约（主设计 §7.1–§7.6）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest} /
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides}：
 *       请求与每路覆盖；未知 `RigId` 拒绝。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackService} /
 *       {@code PlaybackState} / {@code PlaybackClock} / {@code PreparedFrame} / {@code RigPose}：
 *       生命周期、共享时钟与帧准备。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.EventBudget} /
 *       {@code EventDispatcher}：每帧事件与区间预算；执行器异常终止后续分发。</li>
 *   <li>{@code CameraProbe} / {@code CameraControlClaims}：宿主基础姿态与编辑器接管仲裁。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.CameraCompositionService} /
 *       {@code CompositionRequest} / {@code CompositionResult} / {@code RigRenderStatus} /
 *       {@code DegradeReason}：多路合成与可见降级状态。</li>
 *   <li>{@code PlaybackFailure}：按一次汇总的失败原因与时间。</li>
 * </ul>
 *
 * <h2>边界</h2>
 *
 * <p>内核不读取世界、不写相机、不执行指令；宿主负责环境数据、锚点解析、参考系合成、相机应用
 * 与事件执行。准备阶段只使用同一份 `FrameFacts` 快照；任一路失败时整帧不执行，也不应用部分
 * 新姿态。降级是可见、可恢复的运行状态，不改写文档。
 *
 * <p>本包可依赖动画、求值与空间契约（含 `document.Revision`）；不得依赖 `storage`、
 * `editor` 或客户端渲染实现。求值层不得反向依赖本包：覆盖在宿主层应用，求值只接收生效状态。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;
