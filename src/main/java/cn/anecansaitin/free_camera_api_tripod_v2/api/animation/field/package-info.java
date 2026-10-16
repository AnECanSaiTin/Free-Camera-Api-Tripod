/**
 * 值源、曲线与相机通道的语义键（主设计 §6.1、§3.1）。
 *
 * <h2>封闭值源层级</h2>
 *
 * <pre>
 * ValueSource permits NumberSource, TrackRef
 * NumberSource permits Constant, Formula
 * </pre>
 *
 * <p>关键帧字段由 {@code NumberSource} 驱动；{@code TrackRef} <b>不实现</b> NumberSource，
 * 因此在类型上根本进不了关键帧字段，只能作为变量的取值来源。
 *
 * <h2>没有隐式回退</h2>
 *
 * <p>主设计 §6.1 与 §10.2 明确删除「求值失败就换回固定数值」的路径。
 * 公式只存文本；失败必须如实上报为 {@code ERROR}，不能伪装成合法数值（不变量 I9）。
 * 因此本包不提供 {@code evaluateOrFallback} 一类入口。
 *
 * <h2>时间与曲线</h2>
 *
 * <p>时间<b>始终以秒存储</b>，不量化。曲线只描述数据，<b>不含插值算法</b>：
 * 求值由 {@code api.animation.eval} 的采样器按显式求值上下文完成，
 * 保证曲线、数值列与姿态共用同一求值规则（不变量 I6）。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import org.jspecify.annotations.NullMarked;