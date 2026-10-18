/**
 * 纯编辑操作、结构化诊断、静态校验与试算契约（主设计 §4）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp}：
 *       sealed 操作全集；复合修改用 {@code Batch} 一次校验、一次发布。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOutcome} /
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditResult}：
 *       候选构造结果与发布结果。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic} /
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage} /
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope} /
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessageRenderer} /
 *       {@code DiagnosticCode} / {@code Severity}：稳定错误码、级别、不可变消息、作用域、
 *       显示渲染、定位与可选修复。</li>
 *   <li>{@code DocumentValidator} / {@code ValidationLayer}：L1–L4 分层只读校验。</li>
 *   <li>{@code TrialRun} / {@code TrialResult} / {@code TrialStatus}：有预算的试算，不是安全证明；
 *       结果报告检查区间、采样策略、实际检查量、固定事实与未完成原因。</li>
 *   <li>{@code EditTools} / {@code EasingPreset} / {@code BatchTransformRequest} /
 *       {@code BakeRequest} / {@code AimCaptureRequest} / {@code ToolResult}：缓动、批量、烘焙
 *       与注视姿态捕获，只生成原子候选。</li>
 * </ul>
 *
 * <h2>约束</h2>
 *
 * <p>操作与校验都不得修改输入候选、不得执行 IO 或 Tick 副作用。数值键与状态键共用关键帧入口，
 * 值修改由轨道能力分派；状态键单值修改使用 {@code SetStateKey}，内建状态类型不得编码成浮点数。
 * 跨轨粘贴须值类型与语义兼容，否则整体原子拒绝。试算的 `FAILED` 必须含错误诊断，
 * `PASSED` / `INCOMPLETE` 不得含已确认错误；`INCOMPLETE` 必须说明原因。
 *
 * <p>编辑结果引用 {@code api.animation.document.AnimationSnapshot}，文档存储消费本包操作；
 * 两个包按 P1 计划约定互相引用，不引入 {@code core} / {@code editor} 依赖。
 *
 * <h2>不做</h2>
 *
 * <p>本包不发布、不撤销、不保存文件；发布与历史由 {@code api.animation.document} 负责，
 * 会话 / 面板在 {@code api.editor}。编辑器辅助（选择、吸附、视图状态）不属于编辑操作。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import org.jspecify.annotations.NullMarked;
