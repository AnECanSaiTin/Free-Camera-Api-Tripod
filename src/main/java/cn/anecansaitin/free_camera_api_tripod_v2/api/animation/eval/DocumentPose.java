package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 文档空间中的逐轴采样结果（主设计 §6.2、§6.4）。
///
/// 位置与旋转**逐轴**使用 [AxisSample] 的 `ABSENT / VALUE / ERROR` 三态；FOV 单独用
/// [ChannelSample] 标记。错误不得压缩成缺失位：任一实际依赖轴 `ERROR` 或 FOV 错误时，
/// 姿态合成必须失败，不能用基础姿态静默补全。存在掩码是派生查询，只表示哪些轴有值，
/// 不独自承担错误表示。
///
/// 本类型是文档空间结果；完成锚点 / 参考系合成后的可交给相机的姿态是 `spatial.WorldPose`。
///
/// @param x             位置 X
/// @param y             位置 Y
/// @param z             位置 Z
/// @param pitch         旋转 X（弧度）
/// @param yaw           旋转 Y（弧度）
/// @param roll          旋转 Z（弧度）
/// @param fov           视场角采样
/// @param diagnostics   本姿态的诊断；顺序稳定。轴级错误也应汇总到这里
@NullMarked
public record DocumentPose(
        AxisSample x,
        AxisSample y,
        AxisSample z,
        AxisSample pitch,
        AxisSample yaw,
        AxisSample roll,
        ChannelSample fov,
        List<Diagnostic> diagnostics) {

    public DocumentPose {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(z, "z");
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(yaw, "yaw");
        Objects.requireNonNull(roll, "roll");
        Objects.requireNonNull(fov, "fov");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);
    }

    /// 位置三轴存在掩码，由轴状态派生。
    public PositionMask positionMask() {
        return new PositionMask(x.isValue(), y.isValue(), z.isValue());
    }

    /// 旋转三轴存在掩码，由轴状态派生。
    public RotationMask rotationMask() {
        return new RotationMask(pitch.isValue(), yaw.isValue(), roll.isValue());
    }

    /// 三轴都有值时返回位置，否则为空；错误不会伪装成缺失。
    public Optional<Vec3> position() {
        if (x instanceof AxisSample.Value xv
                && y instanceof AxisSample.Value yv
                && z instanceof AxisSample.Value zv) {
            return Optional.of(new Vec3(xv.value(), yv.value(), zv.value()));
        }

        return Optional.empty();
    }

    /// 三轴都有值时返回旋转，否则为空；错误不会伪装成缺失。
    public Optional<Rotation> rotation() {
        if (pitch instanceof AxisSample.Value pv
                && yaw instanceof AxisSample.Value yv
                && roll instanceof AxisSample.Value rv) {
            return Optional.of(new Rotation(yv.value(), pv.value(), rv.value()));
        }

        return Optional.empty();
    }

    /// 是否存在轴级 / FOV / 汇总错误；任一实际依赖错误都应阻止姿态合成。
    public boolean hasError() {
        return x.isError() || y.isError() || z.isError()
                || pitch.isError() || yaw.isError() || roll.isError()
                || fov.isError()
                || diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Severity.ERROR);
    }

    /// 位置三轴存在掩码；与旋转掩码分开，避免把位置和旋转的缺失混为一谈。
    ///
    /// @param x 是否编写了 X
    /// @param y 是否编写了 Y
    /// @param z 是否编写了 Z
    public record PositionMask(boolean x, boolean y, boolean z) {
    }
}
