/**
 * 不可变文档模型：一份动画的全部创作数据（主设计 §3.1）。
 *
 * <h2>形状</h2>
 *
 * <pre>
 * CameraAnimation            文档根
 *   rigs: 有序 CameraRig 集合
 *   cameraSource: 文档级来源 STEP 轨道（Player / Rig），默认 Player
 *   tracks: 文档级有序扩展轨道（事件等）
 *   symbols / markers / samples / audioRefs / overlays
 *
 * CameraRig                  机位
 *   channels / path / motionMode / distanceMode
 *   space
 *   aim: 注视 STEP 轨道（Off / Target），默认 Off
 *   obstacle
 *   view: 静态启用 + 活动 STEP 轨道 + 矩形 + 层级 + 质量
 * </pre>
 *
 * <h2>三类轨道</h2>
 *
 * <p>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack} 是数值曲线
 * （相机通道或供变量读取的通用曲线）；{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.KeyframeTrack}
 * 是仅有 STEP 的有类型状态轨道，三个内建分支分别为注视、机位活动与相机来源；
 * 其余第三方数据走 {@code AnimationTrack.ExtensionTrack}；扩展轨道必须实现纯派生的
 * {@code withName(String)}，供通用改名操作使用。需要统一时间轴编辑的扩展轨道可
 * 额外实现 {@code ExtensionTimelineTrack}，只暴露稳定键与时间，不发放原地修改方法。
 *
 * <p>状态轨道只持久化一次：它们是机位 / 文档上的有类型访问入口，不在 {@code tracks}
 * 通用表里另存一份副本。统一时间轴目录由这些入口与文档级轨道派生，全部 {@code TrackId}
 * 在文档内唯一（主设计 §3.1.1）。
 *
 * <h2>两种键类型的命名对照</h2>
 *
 * <p>本包与 {@code field} 各有一种键，名字相近但语义不同；不要把旧根（v1）的
 * {@code Keyframe} / {@code Keyframec} 套到它们身上：
 *
 * <ul>
 *   <li>{@code field.Keyframe}：数值曲线键，值为 {@code NumberSource}（常量或公式），
 *       带曲柄与 STEP / LINEAR / HERMITE 插值；不可变 record。</li>
 *   <li>{@code block.ValueKeyframe<T>}：有类型状态键，持有原生值（{@code AimState} /
 *       {@code Boolean} / {@code CameraSource}），无曲柄、无插值模式，仅 STEP；
 *       不可变 record。</li>
 *   <li>v1 的 {@code Keyframe} / {@code Keyframec} 是可变实现与只读视图，不属于新契约；
 *       公式结果由求值层采样器产出，不靠键对象携带。</li>
 * </ul>
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
 * <h2>声明与读数分离</h2>
 *
 * <p>{@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample} 只是文档里的
 * <b>声明</b>，其参数是名义值
 * {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleArgs}
 * （不允许不透明 {@code Object} 或可变容器；{@code kind} 必须等于 {@code args.type()}）；
 * 实际读数 {@link cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.SampleReading}
 * 由宿主在帧准备阶段采集，属于本帧事实，不进文档、历史与网络文档本身。
 *
 * <h2>不可变（不变量 I2）</h2>
 *
 * <p>全部类型是 record 或 sealed interface，集合在构造时防御性复制，不保留调用方容器。
 * 对外不暴露可变数组、集合、JOML 向量或可变 Component 实例。
 * {@code with*} 返回派生对象，只复制变化路径，允许共享未变化的不可变对象。
 *
 * <p>第三方开放类型（扩展轨道、采样参数）必须自行满足不可变与值相等契约；框架保证自己持有
 * 的外层容器被复制，但不能宣称由开放 Java 接口完全强制（主设计 §3.2）。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import org.jspecify.annotations.NullMarked;
