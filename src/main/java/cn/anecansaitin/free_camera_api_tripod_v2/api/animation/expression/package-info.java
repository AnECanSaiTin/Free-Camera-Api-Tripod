/**
 * 表达式与符号契约（主设计 §2.1）：值源、编译、符号与函数。
 *
 * <h2>当前状态</h2>
 *
 * <p>本包目前**没有类型**。设计把“值源、编译、符号与函数契约”放在
 * {@code api.animation.expression}；本轮拆分中，值源（{@code ValueSource} /
 * {@code NumberSource}）、曲线、关键帧、通道语义键与符号表条目落在
 * {@code api.animation.field}，差异记录在 P1 契约计划 §1.4。
 *
 * <p>按 Q6 裁决，求值层最小查询面 {@code TrackLookup} 已迁到
 * {@code api.animation.eval}；本包预留给编译器、语法树与符号解析契约
 * （P3 / 后续批次），届时再决定是否把 {@code field} 中的部分类型迁回本包。
 *
 * <h2>依赖方向</h2>
 *
 * <p>本包不得反向依赖 {@code eval}、{@code playback}、{@code storage} 或
 * {@code editor}（主设计 §2.1、§2.3）。编译器不是稳定扩展接口（主设计 §6.1），
 * 实现类不进入公开契约。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression;

import org.jspecify.annotations.NullMarked;
