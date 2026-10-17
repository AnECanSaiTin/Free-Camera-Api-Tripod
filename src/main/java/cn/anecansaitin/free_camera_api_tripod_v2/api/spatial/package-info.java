/**
 * 空间模式、锚点数据与参考系变换契约（主设计 §2.1、§6.3–§6.4）。
 *
 * <h2>内容</h2>
 *
 * <ul>
 *   <li>不可变数学值：{@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3}（双精度）、
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation}（弧度，YXZ）、
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask}（逐轴掩码）。</li>
 *   <li>空间分支：{@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space}（World / Local）。</li>
 *   <li>锚点：{@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Anchor}（开放不可变契约）、
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ResolvedAnchor}（一帧内解析结果）。</li>
 *   <li>机位空间设置：{@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget}（注视配置）、
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState}（某一时刻的注视状态：
 *       {@code Off} / {@code Target}）、
 *       {@link cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings}（避障默认值）。</li>
 * </ul>
 *
 * <h2>为什么不暴露 JOML</h2>
 *
 * <p>主设计 §3.2：对外不得暴露可变数组、集合、<b>JOML 向量</b>或可变 Component 实例。
 * JOML 可变类型只作为计算缓冲区，在 core 的求值内部复用，不进入文档、历史与网络。
 *
 * <h2>单位与转换</h2>
 *
 * <p>内核与文档统一用<b>弧度</b>；基础相机 API（CameraModifier 一类）以度为单位，
 * 转换只发生在 core 的宿主边界（主设计 §6.4）。欧拉角顺序统一为 YXZ，
 * 权威转换实现集中在 core 的旋转工具里。
 *
 * <h2>开放类型的注册分离</h2>
 *
 * <p>锚点类型标识用 net.minecraft.resources.Identifier，注册表分派 Codec 与校验器。
 * 类型 ID、MapCodec、StreamCodec 在 <b>common 侧</b>注册；锚点解析器
 * （需要世界数据的那一半）在<b>客户端单独注册</b>，不得在 common 初始化时加载客户端类
 * （主设计 §6.3、§7.7）。
 *
 * <p>本包当前只含不可变值与分支定义，注册表与解析器契约随 P3/P4 阶段补齐。
 */
@NullMarked
package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;
