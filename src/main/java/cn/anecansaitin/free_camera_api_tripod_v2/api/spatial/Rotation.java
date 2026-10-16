package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;

/// 欧拉旋转：**弧度**，顺序固定为 YXZ。
///
/// ## 为什么是弧度
///
/// 主设计 §6.4：内核与文档统一用弧度，欧拉角顺序与转换实现集中定义。
/// 基础相机 API（`CameraModifier` 一类）以度为单位，转换只发生在 `core` 的宿主边界，
/// 不散落到契约里——否则“这个字段是度还是弧度”会变成每个调用点各自判断。
///
/// ## 顺序
///
/// YXZ 指**先绕 Y、再绕 X、最后绕 Z**。权威转换实现放在 `core` 的旋转工具类里，
/// 本类型只承载数值；这样文档里的人为可读值与渲染结果不会因为两处实现不同而漂移。
///
/// 字段名沿用相机习惯：
///
/// - `yaw` —— 绕 Y 轴（水平朝向）
/// - `pitch` —— 绕 X 轴（俯仰）
/// - `roll` —— 绕 Z 轴（翻滚，主设计 §3.3 明确支持，Aperture 等同类也有）
///
/// 注视只覆盖 Yaw 与 Pitch，Roll 保留通道或基础姿态（主设计 §6.3）。
///
/// @param yaw   绕 Y 轴，弧度
/// @param pitch 绕 X 轴，弧度
/// @param roll  绕 Z 轴，弧度
@NullMarked
public record Rotation(double yaw, double pitch, double roll) {

    /// 单位旋转：三轴皆零。
    public static final Rotation NONE = new Rotation(0, 0, 0);

    public Rotation {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || !Double.isFinite(roll)) {
            throw new IllegalArgumentException(
                    "Rotation components must be finite: " + yaw + ", " + pitch + ", " + roll);
        }
    }

    /// 按掩码归零：未启用的轴取 0。
    ///
    /// 与 `Vec3.masked` 同一条规则（主设计 §6.4：未启用轴置零后再构造四元数），
    /// 但作用在欧拉角上而不是向量分量上。
    public Rotation masked(RotationMask mask) {
        return new Rotation(
                mask.y() ? yaw : 0,
                mask.x() ? pitch : 0,
                mask.z() ? roll : 0);
    }

    /// 最短弧插值：实体与玩家锚点的旋转按本帧 partial tick 插值，沿最短弧
    /// （主设计 §6.3），避免在 ±π 附近绕远路。
    ///
    /// 固定锚点不插值；瞬移、维度切换或无有效历史时直接用当前宿主状态，不跨维度平滑。
    public Rotation lerp(Rotation other, double t) {
        return new Rotation(
                lerpAngle(yaw, other.yaw, t),
                lerpAngle(pitch, other.pitch, t),
                lerpAngle(roll, other.roll, t));
    }

    /// 单轴最短弧插值。差角先折到 `(-π, π]` 再插值。
    private static double lerpAngle(double from, double to, double t) {
        double delta = to - from;
        // 折到 (-π, π]
        delta -= 2 * Math.PI * Math.floor((delta + Math.PI) / (2 * Math.PI));
        return from + delta * t;
    }

    /// 三轴角度的度值，顺序为 `(yaw, pitch, roll)`。
    ///
    /// 返回 [Vec3] 只是复用“三个 double 的不可变载体”，**不表示方向向量**；
    /// 只在宿主边界使用（主设计 §6.4：基础 `CameraModifier` API 的角度边界统一转换为度）。
    public Vec3 asDegreesTuple() {
        return new Vec3(Math.toDegrees(yaw), Math.toDegrees(pitch), Math.toDegrees(roll));
    }
}
