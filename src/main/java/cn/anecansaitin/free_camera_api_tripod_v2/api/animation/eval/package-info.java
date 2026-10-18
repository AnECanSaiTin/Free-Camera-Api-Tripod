/**
 * 求值、帧事实与采样状态（主设计 §3.4、§6，不变量 I5–I7）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@code TrackLookup} / {@code TrackIndex}：按稳定 ID 查轨道；索引绑定快照并携带所属机位。</li>
 *   <li>{@code FrameFacts} / {@code EvaluationContext} / {@code EvaluationKey}：一帧内冻结的世界与
 *       空间事实、求值上下文与缓存键。</li>
 *   <li>{@code CurveSampler} / {@code SampleStatus} / {@code ChannelSample}：统一采样规则与
 *       `ABSENT` / `VALUE` / `ERROR` 三分状态。</li>
 *   <li>{@code DocumentPose} / {@code SpatialReadings}：文档空间逐轴结果与公式可读的空间事实。</li>
 *   <li>{@code DependencyGraph} / {@code EvaluationCache}：文档派生的依赖图与单上下文采样缓存。</li>
 * </ul>
 *
 * <h2>帧事实不是文档</h2>
 *
 * <p>{@code FrameFacts} 由宿主在帧准备阶段生成，含维度身份、游戏刻与 partial tick、实际所需
 * 机位锚点 / 目标读数、采样表及有效性；不进入文档、历史或网络文档。帧内所有机位与公式读取
 * 同一份事实（不变量 I5），求值阶段不自行访问世界。
 *
 * <h2>状态与缓存</h2>
 *
 * <p>公共采样结果必须携带 {@code ABSENT} / {@code VALUE} / {@code ERROR} 状态与诊断定位，
 * 不得把失败压缩成缺失位或用 NaN 兼任哨兵（不变量 I9）。失败结果也必须缓存；缓存键包含
 * 快照身份、上下文身份、帧序号、机位上下文、轨道 ID、空间事实身份与采样表版本。
 *
 * <h2>依赖方向</h2>
 *
 * <p>本包可依赖动画与表达式模型（主设计 §2.1）；不得依赖 {@code playback}、{@code storage}、
 * {@code editor} 或客户端类型。`block` 不反向依赖本包：轨道索引与绑定在 eval 侧建立。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;
