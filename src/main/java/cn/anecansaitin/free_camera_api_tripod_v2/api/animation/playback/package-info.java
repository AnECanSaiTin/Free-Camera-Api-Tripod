/**
 * 时间推进、播放请求、帧准备与播放服务契约（主设计 §7.1–7.3）。
 *
 * <h2>当前状态（部分交付）</h2>
 *
 * <p>本批只交付 Q5 裁决所需的最小类型
 * {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride}
 * （注视覆盖的继承 / 禁用 / 替换三态）。它证明并固定了“请求策略属于播放层、
 * 求值层只消费已解析状态”的分层。
 *
 * <p>以下契约待下一批：{@code PlaybackRequest}、{@code RigPlaybackOverrides}、
 * {@code PlaybackService}、{@code PlaybackState}、{@code PreparedFrame}、
 * {@code RigPose}、{@code PlaybackFailure}、{@code PlaybackClock}、
 * {@code EventBudget}、{@code EventDispatcher}、{@code CameraControlClaims}、
 * {@code CameraProbe}、{@code CameraCompositionService}、{@code CompositionRequest}、
 * {@code CompositionResult}、{@code RigRenderStatus}、{@code DegradeReason}。
 *
 * <h2>依赖方向</h2>
 *
 * <p>本包可依赖动画、求值与空间契约（主设计 §2.1）；不得依赖 {@code storage}、
 * {@code editor} 或客户端渲染实现。求值层不得反向依赖本包：覆盖在宿主层应用，
 * 求值只接收生效的 {@code AimState} 与来源 / 活动集合。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import org.jspecify.annotations.NullMarked;
