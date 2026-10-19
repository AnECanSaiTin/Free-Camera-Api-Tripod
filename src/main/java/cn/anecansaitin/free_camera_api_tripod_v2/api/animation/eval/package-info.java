/**
 * 求值、帧事实与采样状态（主设计 §3.4、§6，不变量 I5–I7）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@code TrackLookup} / {@code TrackIndex}：按稳定 ID 查轨道；索引绑定快照并携带所属机位。</li>
 *   <li>{@code FrameFacts} / {@code EvaluationContext} / {@code EvaluationKey}：一帧内冻结的世界与
 *       空间事实、求值上下文与缓存键；世界实例身份独立于维度 / 游戏刻。</li>
 *   <li>{@code CurveSampler} / {@code CurveSampleRequest} / {@code SampleStatus} / {@code ChannelSample}：
 *       显式绑定上下文与采样时间的统一采样规则，以及 `ABSENT` / `VALUE` / `ERROR` 三分状态。</li>
 *   <li>{@code DocumentPose} / {@code AxisSample} / {@code SpatialReadings}：文档空间逐轴
 *       `ABSENT / VALUE / ERROR` 结果与公式可读的空间事实；空间锚点 / 目标用
 *       {@code SpatialResolution} 区分不需要 / 成功 / 失败。</li>
 *   <li>{@code DependencyGraph} / {@code EvaluationCache}：文档派生的依赖图与单上下文采样缓存。</li>
 *   <li>{@code FrameFactsProvider} / {@code FrameFactsRequest} / {@code FrameFactsPlan}：帧准备入口；
 *       请求携带动画时间与宿主算出的逐机位锚点 / 目标需求，解析机位空间事实与声明式世界采样。</li>
 *   <li>{@code EvaluationSession} / {@code ExpressionEvaluator} / {@code ExpressionInputs}：公式求值的共享预算、来源与显式输入。</li>
 * </ul>
 *
 * <h2>帧事实不是文档</h2>
 *
 * <p>{@code FrameFacts} 由宿主在帧准备阶段生成，含维度身份、独立世界实例身份、游戏刻与
 * partial tick、实际所需机位锚点 / 目标读数、采样表及有效性；不进入文档、历史或网络文档。
 * 帧内所有机位与公式读取同一份事实（不变量 I5），求值阶段不自行访问世界。同一维度重进世界
 * 也必须生成新的世界实例身份。
 *
 * <p>准备请求必须显式给出**动画时间**与逐机位需求计划：同一游戏刻可以定位到不同动画时刻，
 * 覆盖后的生效锚点 / 注视状态也不能由提供者重新推断（复核 R49-H1）。返回事实绑定请求快照的
 * 发布版本，{@code EvaluationContext} 据此拒绝跨发布混用。
 *
 * <h2>状态与缓存</h2>
 *
 * <p>公共采样结果必须携带 {@code ABSENT} / {@code VALUE} / {@code ERROR} 状态与诊断定位，
 * 不得把失败压缩成缺失位或用 NaN 兼任哨兵（不变量 I9）。失败结果也必须缓存；缓存键包含
 * 快照身份、上下文身份、世界实例身份、帧序号、采样时间、机位上下文、轨道 ID、空间事实身份
 * 与采样表版本。采样请求必须显式携带上下文、轨道与采样时间，不能依赖“当前活动机位”或
 * 当前 store 的隐式绑定。
 *
 * <p>姿态轴使用 {@code AxisSample} 三态，锚点 / 目标使用 {@code SpatialResolution} 三态；
 * 存在掩码只由有值轴派生，不能把错误当缺失，也不能把“不需要”当失败。
 *
 * <h2>依赖方向</h2>
 *
 * <p>本包可依赖动画与表达式模型（主设计 §2.1）；不得依赖 {@code playback}、{@code storage}、
 * {@code editor} 或客户端类型。`block` 不反向依赖本包：轨道索引与绑定在 eval 侧建立。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import org.jspecify.annotations.NullMarked;
