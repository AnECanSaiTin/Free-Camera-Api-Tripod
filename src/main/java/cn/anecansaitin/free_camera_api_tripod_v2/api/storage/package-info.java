/**
 * 资源身份、保存 / 载入结果、诊断报告与自动恢复契约（主设计 §8.2、§8.4）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@code ResourceId}：非空白的相对资源身份；文件诊断不再使用自由字符串。</li>
 *   <li>{@code AnimationResourceEntry} / {@code AnimationResourceService}：资源元数据、载入与
 *       保存 / 异步保存入口。</li>
 *   <li>{@code LoadResult} / {@code SaveResult} / {@code DiagnosticReport}：解码 / 保存结果与
 *       一次操作的诊断汇总；拒绝不返回部分文档。</li>
 *   <li>{@code RecoverySlot} / {@code RecoveryService} / {@code RecoveryDecision}：自动恢复槽
 *       的捕获、恢复、丢弃与用户决定。</li>
 * </ul>
 *
 * <h2>边界</h2>
 *
 * <p>存储只负责资源定位、字节 / Ops 转换、调用 Codec 与报告错误，不解释文档子对象字段。
 * 载入成功仍须经过完整文档校验后才能发布；自动恢复不覆盖正式文件，不保存渲染缓存或撤销栈。
 * 资源根、路径安全、大小 / 数量限制与原子替换由实现负责；`ResourceId` 的非空白检查不等于
 * 绝对路径或越界路径已被拒绝。
 *
 * <p>本包不得依赖 {@code editor} 或客户端实现；资源解码不得绕过文档强制校验。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import org.jspecify.annotations.NullMarked;
