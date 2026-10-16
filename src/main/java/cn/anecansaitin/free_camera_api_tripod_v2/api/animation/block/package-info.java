/**
 * 不可变文档模型：一份动画的全部创作数据（主设计 §3.1）。
 *
 * <h2>形状</h2>
 *
 * <pre>
 * CameraAnimation            文档根
 *   rigs: 有序 CameraRig 集合
 *   primaryRigId: 可空 RigId
 *   tracks: 文档级有序轨道（事件等）
 *   symbols: 共享变量与函数
 *   markers / samples / audioRefs / overlays
 *
 * CameraRig                  机位
 *   channels / path / motionMode / distanceMode
 *   space / aimTarget / obstacle / view
 * </pre>
 *
 * <h2>三套身份，互不替代（主设计 §3.1）</h2>
 *
 * <ul>
 *   <li><b>类型标识</b>：{@code net.minecraft.resources.Identifier}，走注册表分派 Codec 与校验器。</li>
 *   <li><b>文档对象身份</b>：{@code api.animation.id} 下的稳定 ID，在各自作用域内唯一并持久化。</li>
 *   <li><b>用户名称</b>：{@code String}，可改、可本地化，改名不改 ID。</li>
 * </ul>
 *
 * <p>顺序由<b>集合自身顺序</b>表达，不由 ID 表达：机位顺序服务显示，机位内通道顺序服务序列化，
 * 文档级轨道顺序服务事件的稳定分派。
 *
 * <h2>不可变（不变量 I2）</h2>
 *
 * <p>全部类型是 record 或 sealed interface，集合在构造时防御性复制，不保留调用方容器。
 * 对外不暴露可变数组、集合、JOML 向量或可变 Component 实例。
 * {@code with*} 返回派生对象，只复制变化路径，允许共享未变化的不可变对象。
 *
 * <p>第三方开放类型（扩展轨道）必须自行满足不可变契约；框架保证自己持有的外层容器被复制，
 * 但不能宣称由开放 Java 接口完全强制（主设计 §3.2）。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;