package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// 三维向量：不可变数学值，**双精度**。
///
/// ## 为什么不用 JOML
///
/// 主设计 §3.2 明确禁止对外暴露 JOML 可变向量：`Vector3f` 是可变的，
/// 一旦进入文档，任何持有者都能在不经过文档存储的情况下改写已发布数据（不变量 I2）。
/// JOML 可变类型只允许作为**计算缓冲区**（`PoseBuffer` 一类），在求值内部复用，
/// 不进入文档、历史与网络。
///
/// ## 为什么是双精度
///
/// 锚点与世界坐标取自宿主世界坐标（原本就是双精度），路径节点与姿态经度换算后
/// 精度损失会累积到避障与注视方向判定上。`api.spatial` 的公开契约统一用双精度；
/// 需要与宿主相机交互时在 `core` 边界转成 float，转换点集中，不散落在契约里。
///
/// @param x X 分量
/// @param y Y 分量
/// @param z Z 分量
@NullMarked
public record Vec3(double x, double y, double z) {

    /// 世界原点 / 零向量。
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    /// X 单位向量。注视与切线方向判定用得上。
    public static final Vec3 UNIT_X = new Vec3(1, 0, 0);

    /// Y 单位向量。
    public static final Vec3 UNIT_Y = new Vec3(0, 1, 0);

    /// Z 单位向量。
    public static final Vec3 UNIT_Z = new Vec3(0, 0, 1);

    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vec3 components must be finite: " + x + ", " + y + ", " + z);
        }
    }

    /// 三轴同值构造，用于缩放与锚点偏移的常见写法。
    public static Vec3 splat(double value) {
        return new Vec3(value, value, value);
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    /// 逐轴取反。`RotationMask` 未启用的轴可以由此归零。
    public Vec3 negate() {
        return new Vec3(-x, -y, -z);
    }

    public Vec3 scale(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    /// 欧几里得长度。锚点到注视目标的距离读数用它。
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /// 单位化。零向量无法单位化，返回空——调用方据此报“方向非有限/重合”错误
    /// （主设计 §6.4：目标重合或方向非有限是错误），不要用某个默认方向顶替。
    public Optional<Vec3> normalize() {
        double length = length();

        if (length == 0 || !Double.isFinite(length)) {
            return Optional.empty();
        }

        return Optional.of(new Vec3(x / length, y / length, z / length));
    }

    /// 线性插值；`t` 为 0 给自身，为 1 给 `other`。
    ///
    /// 实体锚点与注视目标按本帧 partial tick 插值位置（主设计 §6.3），
    /// 固定锚点不插值。
    public Vec3 lerp(Vec3 other, double t) {
        return new Vec3(
                x + (other.x - x) * t,
                y + (other.y - y) * t,
                z + (other.z - z) * t);
    }

    /// 逐轴应用掩码：未启用的轴取 0。
    ///
    /// 主设计 §6.4：掩码作用于约定为 YXZ 顺序的锚点欧拉角，未启用轴置零后再构造四元数。
    /// 本方法只负责“置零”这一步，旋转合成在 `core` 的数学实现里。
    public Vec3 masked(RotationMask mask) {
        return new Vec3(
                mask.x() ? x : 0,
                mask.y() ? y : 0,
                mask.z() ? z : 0);
    }
}
