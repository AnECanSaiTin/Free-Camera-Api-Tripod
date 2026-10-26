package cn.anecansaitin.free_camera_api_tripod_v2.core.math;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.jspecify.annotations.NullMarked;

/// 相机姿态旋转的权威转换：弧度、YXZ、右-handed。
///
/// 采用 `R = Ry(yaw) * Rx(pitch) * Rz(roll)`。局部机位的位置先按参考系旋转再平移；
/// 姿态按父旋转与局部旋转的矩阵乘积合成。欧拉角提取在 `cos(pitch) ~= 0` 时把 Roll
/// 归零，避免万向节附近两轴互相跳变。
///
/// 本类只做纯数学，不读取世界、不修改输入。
@NullMarked
public final class CoreRotationMath {

    private static final double EPSILON = 1e-9;

    private CoreRotationMath() {
    }

    /// 按 YXZ 顺序旋转一个向量。
    ///
    /// @param rotation 旋转
    /// @param vector   输入向量
    /// @return 旋转后的向量
    public static Vec3 rotate(Rotation rotation, Vec3 vector) {
        double cy = Math.cos(rotation.yaw());
        double sy = Math.sin(rotation.yaw());
        double cp = Math.cos(rotation.pitch());
        double sp = Math.sin(rotation.pitch());
        double cz = Math.cos(rotation.roll());
        double sz = Math.sin(rotation.roll());

        // R = Ry * Rx * Rz
        double m00 = cy * cz + sy * sp * sz;
        double m01 = -cy * sz + sy * sp * cz;
        double m02 = sy * cp;
        double m10 = cp * sz;
        double m11 = cp * cz;
        double m12 = -sp;
        double m20 = -sy * cz + cy * sp * sz;
        double m21 = sy * sz + cy * sp * cz;
        double m22 = cy * cp;

        return new Vec3(
                m00 * vector.x() + m01 * vector.y() + m02 * vector.z(),
                m10 * vector.x() + m11 * vector.y() + m12 * vector.z(),
                m20 * vector.x() + m21 * vector.y() + m22 * vector.z());
    }

    /// 合成父旋转与子旋转：`parent * child`。
    ///
    /// @param parent 参考系旋转
    /// @param child  局部旋转
    /// @return 合成后的 YXZ 欧拉角
    public static Rotation compose(Rotation parent, Rotation child) {
        double[] a = matrix(parent);
        double[] b = matrix(child);
        double[] result = new double[9];

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                double value = 0.0;

                for (int index = 0; index < 3; index++) {
                    value += a[row * 3 + index] * b[index * 3 + column];
                }

                result[row * 3 + column] = value;
            }
        }

        return extract(result);
    }

    /// 求逆旋转：`inverse(r) * r = 单位`。
    ///
    /// 用于把宿主基础姿态转换到文档参考系（主设计 §6.4）：局部机位先用锚点掩码旋转的逆
    /// 变换基础位置与姿态，补全未编写分量后再正变换回世界。欧拉角不能简单取反，
    /// 因为 YXZ 顺序下逆变换不是三轴同时取负；这里通过矩阵转置后重新提取欧拉角。
    ///
    /// @param rotation 旋转
    /// @return 逆旋转
    public static Rotation inverse(Rotation rotation) {
        double[] m = matrix(rotation);

        return extract(new double[]{
                m[0], m[3], m[6],
                m[1], m[4], m[7],
                m[2], m[5], m[8]
        });
    }

    /// 按旋转的逆变换一个向量：`inverse(rotation) * vector`。
    ///
    /// @param rotation 旋转
    /// @param vector   世界向量
    /// @return 参考系内的向量
    public static Vec3 rotateInverse(Rotation rotation, Vec3 vector) {
        return rotate(inverse(rotation), vector);
    }

    /// 把世界方向转换为 Yaw/Pitch。方向按相机前向 `(0, 0, -1)` 解释。
    ///
    /// @param direction 非零方向
    /// @return Yaw/Pitch（Roll 为 0）
    /// @throws IllegalArgumentException 方向为零或非有限
    public static Rotation lookRotation(Vec3 direction) {
        Vec3 normalized = direction.normalize().orElseThrow(() ->
                new IllegalArgumentException("Aim direction must be non-zero and finite"));

        double yaw = Math.atan2(-normalized.x(), -normalized.z());
        double pitch = Math.atan2(normalized.y(), Math.hypot(normalized.x(), normalized.z()));
        return new Rotation(yaw, pitch, 0);
    }

    private static double[] matrix(Rotation rotation) {
        double cy = Math.cos(rotation.yaw());
        double sy = Math.sin(rotation.yaw());
        double cp = Math.cos(rotation.pitch());
        double sp = Math.sin(rotation.pitch());
        double cz = Math.cos(rotation.roll());
        double sz = Math.sin(rotation.roll());

        return new double[]{
                cy * cz + sy * sp * sz, -cy * sz + sy * sp * cz, sy * cp,
                cp * sz, cp * cz, -sp,
                -sy * cz + cy * sp * sz, sy * sz + cy * sp * cz, cy * cp
        };
    }

    private static Rotation extract(double[] m) {
        double sp = clamp(-m[5], -1.0, 1.0);
        double pitch = Math.asin(sp);
        double cp = Math.cos(pitch);

        if (Math.abs(cp) < EPSILON) {
            // 万向节：Yaw 取 0，Roll 吸收水平方向。
            double yaw = 0.0;
            double roll = Math.atan2(-m[1], m[0]);
            return new Rotation(yaw, pitch, roll);
        }

        double yaw = Math.atan2(m[2], m[8]);
        double roll = Math.atan2(m[3], m[4]);
        return new Rotation(yaw, pitch, roll);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
