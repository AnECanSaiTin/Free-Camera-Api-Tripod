/**
 * 快照、只读句柄、文档存储、校验入口、撤销历史与会话编辑状态（主设计 §3.3、§4.5、§5）。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot}：
 *       文档 + 发布序号的单次读取结果（不变量 I3）。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationHandle}：
 *       只读句柄，不提供发布方法。</li>
 *   <li>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationDocumentStore}：
 *       唯一写入口；检查基版本、执行纯编辑操作、校验、更新历史与快照、通知监听者。</li>
 *   <li>历史、事务、目标、预览与选择：会话状态，不进入文档、不序列化为 JSON。</li>
 * </ul>
 *
 * <h2>编辑契约</h2>
 *
 * <p>纯编辑操作与结果在 {@code api.animation.edit}；本包只消费其结果，不发放原地修改方法。
 * 文档、历史与发布序号更新完成后才通知监听者（{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.DocumentListener}）。
 *
 * <h2>不属于本包</h2>
 *
 * <p>播放状态、播放头、预览锚点、循环区间与视图参数属于会话 / 宿主，不进入文档历史；
 * 渲染目标、FrameFacts 与运行降级状态也不序列化。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document;

import org.jspecify.annotations.NullMarked;
