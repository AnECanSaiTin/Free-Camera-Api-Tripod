/**
 * 求值与帧事实（主设计 §3.4、§6、不变量 I5–I7）。
 *
 * <h2>当前状态（部分交付）</h2>
 *
 * <p>本批只交付求值层的最小查询面
 * {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackLookup}
 * （按 Q6 裁决从 {@code expression} 迁入）。文档包不再提供
 * {@code trackLookup()} 一类便捷工厂，绑定由本包在建立求值环境时完成。
 *
 * <p>以下契约待下一批：{@code FrameFacts}、{@code EvaluationContext}、
 * {@code EvaluationKey}、{@code CurveSampler}、{@code SampleStatus}、
 * {@code ChannelSample}、{@code DocumentPose}、{@code SpatialReadings}、
 * {@code DependencyGraph}、{@code EvaluationCache}。
 *
 * <h2>依赖方向</h2>
 *
 * <p>本包可依赖动画与表达式模型（主设计 §2.1）；不得依赖 {@code playback}、
 * {@code storage}、{@code editor} 或客户端类型。空间事实与采样表由宿主在帧准备阶段
 * 注入，本包只读，不自行访问世界（主设计 §7.2、§7.7）。
 *
 * <h2>状态口径</h2>
 *
 * <p>采样结果区分 {@code ABSENT} / {@code VALUE} / {@code ERROR}（主设计 §6.1），
 * 断不能把失败压缩成缺失位；状态轨道的有效值（{@code AimState}、来源、活动）由
 * 求值层按 STEP 规则取当前时刻状态，覆盖只在宿主层应用。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;
