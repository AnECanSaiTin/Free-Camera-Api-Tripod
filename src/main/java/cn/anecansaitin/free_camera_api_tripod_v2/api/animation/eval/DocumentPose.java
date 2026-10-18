package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Rotation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.RotationMask;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 文档空间中的逐轴采样结果（主设计 §6.2、§6.4）。
///
/// 位置与旋转**分别**带三轴存在掩码；FOV 单独用 [ChannelSample] 标记。错误不得压缩成
/// 缺失位：某轴 `ERROR` 时该分量不可用，但不能被当作 `ABSENT` 而用基础姿态静默补全。
///
/// 本类型是文档空间结果；完成锚点 / 参考系合成后的可交给相机的姿态是 `WorldPose`
/// （spatial 余项），不在本类型里提前合成为世界姿态。
///
/// @param position      文档空间位置
/// @param positionMask  位置三轴是否存在
/// @param rotation      文档空间旋转（弧度，YXZ）
/// @param rotationMask  旋转三轴是否存在
/// @param fov           视场角采样
/// @param diagnostics   本姿态的诊断；顺序稳定
@NullMarked
public record DocumentPose(
        Vec3 position,
        PositionMask positionMask,
        Rotation rotation,
        RotationMask rotationMask,
        ChannelSample fov,
        List<Diagnostic> diagnostics) {

    public DocumentPose {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(positionMask, "positionMask");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(rotationMask, "rotationMask");
        Objects.requireNonNull(fov, "fov");

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        diagnostics = List.copyOf(diagnostics);
    }

    /// 位置三轴存在掩码；与旋转掩码分开，避免把位置和旋转的缺失混为一谈。
    ///
    /// @param x 是否编写了 X
    /// @param y 是否编写了 Y
    /// @param z 是否编写了 Z
    public record PositionMask(boolean x, boolean y, boolean z) {
    }
}
