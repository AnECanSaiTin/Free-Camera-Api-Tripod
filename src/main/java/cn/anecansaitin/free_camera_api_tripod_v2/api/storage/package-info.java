/**
 * 资源身份、保存 / 载入结果与恢复契约（主设计 §8.2）。
 *
 * <p>当前阶段只交付 {@link cn.anecansaitin.free_camera_api_tripod_v2.api.storage.ResourceId}：
 * 文件诊断不再使用自由字符串，后续资源服务、恢复槽与编解码批次在此基础上扩展。
 *
 * <p>本包不得依赖 {@code editor} 或客户端实现；资源解码不得绕过文档强制校验。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.storage;

import org.jspecify.annotations.NullMarked;
