/**
 * 开放数据类型的注册表与运行能力（主设计 §2.2、§8.1、D7、D16）。
 *
 * <h2>common 注册 / 客户端执行分离</h2>
 *
 * <p>本包同时声明<b>两张表</b>的契约，但两侧的缺项语义不同：
 *
 * <ul>
 *   <li>common 侧：{@code AnchorTypeRegistry} / {@code TrackTypeRegistry} /
 *       {@code SampleTypeRegistry}，只含类型 ID 与 Codec；缺项是<b>解码错误</b>。</li>
 *   <li>客户端侧：{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolverRegistry} /
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectorRegistry}，
 *       只含运行期执行部件；缺项是<b>运行能力缺失</b>，文档仍可读写。</li>
 * </ul>
 *
 * <p>本包的接口<b>不引用</b> {@code net.minecraft.client}，因此 common 侧可以安全地持有这些
 * 契约；真正的解析器 / 采集器实现只在客户端启动时注册（主设计 §6.3、§7.7）。
 *
 * <p>客户端执行部件只处理宿主已经要求的事实：锚点 / 目标是否需要由帧准备计划决定，解析失败
 * 的诊断作用域必须取自 {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolutionContext#diagnosticScope}
 * （复核 R49-H1 / R49-H2）。
 *
 * <h2>能力描述</h2>
 *
 * <p>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeCapabilityService}
 * 把两张表的状态合并成稳定的三态：可用 / 类型未注册 / 实现缺失。编辑器与播放宿主据此
 * 区分“文档结构有问题”和“本环境不能完整运行”，不能合并报告（不变量 I9）。
 *
 * <h2>冻结与未知类型</h2>
 *
 * <p>注册表在启动阶段注册并冻结（主设计 §4.2）。冻结后注册被拒绝、查询只读且可并发；
 * 解码遇到未知类型<b>报错</b>，不静默构造默认值（主设计 §8.1、§7.7）。
 *
 * <h2>本批边界</h2>
 *
 * <p>类型级校验钩子已交付：{@code TypeValidator} 与 {@code TypeValidationContext} 由类型描述的
 * 可选 {@code validator()} 暴露，统一文档校验负责分派。校验器只处理已解码实例，跨对象引用、
 * 区间与文档级规则仍归 {@code DocumentValidator}。解码器仍按类型 ID 查 Codec；
 * 客户端解析器 / 采集器的<b>实现</b>与具体世界查询留给客户端批次。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry;

import org.jspecify.annotations.NullMarked;
