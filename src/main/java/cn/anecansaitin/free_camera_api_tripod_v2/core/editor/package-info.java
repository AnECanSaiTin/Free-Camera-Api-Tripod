/**
 * 纯 JVM 编辑器会话实现：事务草稿、预览、视图状态、交互与播放控制委托。
 *
 * <p>本包只依赖 {@code api}、{@code core.animation.document} 的公开预览入口与 JDK，
 * 不依赖客户端、屏幕、渲染或纹理类。文档写入一律经 {@code AnimationDocumentStore}；
 * 预览候选不发布、不写历史。会话对象只在创建它的宿主线程上使用。
 */
package cn.anecansaitin.free_camera_api_tripod_v2.core.editor;
