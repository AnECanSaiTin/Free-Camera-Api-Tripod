/**
 * 本地资源存储：资源身份路径边界与 JSON 文件资源服务。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>{@code ResourcePathResolver}：资源身份的词法校验、根内解析以及读写路径的符号链接边界。</li>
 *   <li>{@code ResourcePathException}：携带资源身份与稳定原因文本的路径拒绝。</li>
 *   <li>{@code FileAnimationResourceService}：本地 JSON 载入/保存、元数据、临时文件与原子替换。</li>
 *   <li>{@code FileRecoveryService}：独立恢复根下的自包含恢复槽、异步捕获与结构恢复。</li>
 * </ul>
 *
 * <h2>边界</h2>
 *
 * <p>{@code FileAnimationResourceService} 与 {@code FileRecoveryService} 都只做结构编解码，
 * 不执行统一文档校验，也不发布文档；载入/恢复结果仍须交给文档发布链路。当前不实现 NBT、
 * 网络、压缩 Ops、资源规模/深度限制，也不消除路径解析与真正打开文件之间的 TOCTOU 窗口。
 *
 * <p>本包只依赖 {@code api} 与 JDK，不依赖 {@code net.minecraft.client} 或其他客户端类。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.core.storage;

import org.jspecify.annotations.NullMarked;
