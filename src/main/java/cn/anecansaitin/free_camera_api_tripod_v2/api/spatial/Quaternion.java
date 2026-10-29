package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 整体相机旋转的四元数表示（主设计 §1.3.5、D34）。
///
/// 旋转在新模型中是一个整体属性，不再拆成 Yaw / Pitch / Roll 三个独立接管属性。
/// 本类型只承载四元数数值与纯几何操作；Euler 只是 UI 的输入/显示视图，持久化与轨道
/// 运行语义仍以四元数为准。`q` 与 `-q` 表示同一朝向，[slerp] 在插值前按点积取符号。
///
/// 分量顺序为 `(x, y, z, w)`，满足 `x² + y² + z² + w² = 1` 的才是单位四元数；构造器
/// 只检查有限性，[normalized] 负责单位化并对零范数报错。轨道键必须由调用方保证单位化。
@NullMarked
public record Quaternion(double x, double y, double z, double w) {

    /// 单位旋转。
    public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

    /// 结构编码：`{x, y, z, w}`，四分量均为有限双精度。
    public static final MapCodec<Quaternion> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.finiteDouble().fieldOf("x").forGetter(Quaternion::x),
            Codecs.finiteDouble().fieldOf("y").forGetter(Quaternion::y),
            Codecs.finiteDouble().fieldOf("z").forGetter(Quaternion::z),
            Codecs.finiteDouble().fieldOf("w").forGetter(Quaternion::w)
    ).apply(instance, Quaternion::new));

    /// 独立解析四元数。
    public static final Codec<Quaternion> CODEC = MAP_CODEC.codec();

    public Quaternion {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w)) {
            throw new IllegalArgumentException("Quaternion components must be finite");
        }
    }

    /// 范数。
    public double length() {
        return Math.sqrt(x * x + y * y + z * z + w * w);
    }

    /// 单位化；零范数无法单位化，明确拒绝。
    public Quaternion normalized() {
        double length = length();

        if (length == 0 || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Quaternion cannot be normalized: " + this);
        }

        return new Quaternion(x / length, y / length, z / length, w / length);
    }

    /// 取反；与自身表示同一朝向。
    public Quaternion negate() {
        return new Quaternion(-x, -y, -z, -w);
    }

    /// 共轭；单位四元数下即逆旋转。
    public Quaternion conjugate() {
        return new Quaternion(-x, -y, -z, w);
    }

    /// 点积。
    public double dot(Quaternion other) {
        Objects.requireNonNull(other, "other");
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    /// 由 YXZ 欧拉角（弧度）构造四元数；与 [toEuler] 互逆。
    public static Quaternion fromEuler(Rotation rotation) {
        Objects.requireNonNull(rotation, "rotation");
        double halfYaw = rotation.yaw() / 2.0;
        double halfPitch = rotation.pitch() / 2.0;
        double halfRoll = rotation.roll() / 2.0;
        Quaternion qy = new Quaternion(0, Math.sin(halfYaw), 0, Math.cos(halfYaw));
        Quaternion qx = new Quaternion(Math.sin(halfPitch), 0, 0, Math.cos(halfPitch));
        Quaternion qz = new Quaternion(0, 0, Math.sin(halfRoll), Math.cos(halfRoll));

        return qy.multiply(qx).multiply(qz).normalized();
    }

    /// 转为 YXZ 欧拉角（弧度）；`q` 与 `-q` 可能选择不同等价分支。
    public Rotation toEuler() {
        Quaternion q = normalized();
        double m02 = 2.0 * (q.x() * q.z() + q.w() * q.y());
        double m12 = 2.0 * (q.y() * q.z() - q.w() * q.x());
        double m22 = 1.0 - 2.0 * (q.x() * q.x() + q.y() * q.y());
        double m10 = 2.0 * (q.x() * q.y() + q.w() * q.z());
        double m11 = 1.0 - 2.0 * (q.x() * q.x() + q.z() * q.z());
        double pitch = Math.asin(Math.clamp(-m12, -1.0, 1.0));
        double yaw = Math.atan2(m02, m22);
        double roll = Math.atan2(m10, m11);

        return new Rotation(yaw, pitch, roll);
    }

    /// Hamilton 乘积：先应用 `other`，再应用 `this`。
    public Quaternion multiply(Quaternion other) {
        Objects.requireNonNull(other, "other");

        return new Quaternion(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z);
    }

    /// 最小弧球面插值。
    ///
    /// @param other 目标四元数
    /// @param t     插值比例，`[0, 1]`
    /// @return 单位化插值结果
    public Quaternion slerp(Quaternion other, double t) {
        Objects.requireNonNull(other, "other");

        if (!Double.isFinite(t) || t < 0.0 || t > 1.0) {
            throw new IllegalArgumentException("Quaternion.slerp t must be in [0, 1]: " + t);
        }

        Quaternion from = normalized();
        Quaternion to = other.normalized();
        double dot = from.dot(to);

        if (dot < 0.0) {
            to = to.negate();
            dot = -dot;
        }

        if (dot > 0.9995) {
            return new Quaternion(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    from.w + (to.w - from.w) * t).normalized();
        }

        double theta = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        double sinTheta = Math.sin(theta);
        double fromWeight = Math.sin((1.0 - t) * theta) / sinTheta;
        double toWeight = Math.sin(t * theta) / sinTheta;

        return new Quaternion(
                from.x * fromWeight + to.x * toWeight,
                from.y * fromWeight + to.y * toWeight,
                from.z * fromWeight + to.z * toWeight,
                from.w * fromWeight + to.w * toWeight).normalized();
    }
}
