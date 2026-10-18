package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 已补全并完成参考系合成的最终相机姿态（主设计 §6.2、§6.4）。
///
/// 与 `eval.DocumentPose` 的分工：
///
/// - `DocumentPose` 是**文档空间**的逐轴采样结果，仍带存在掩码与诊断；
/// - 本类型是已用基础姿态补全、完成锚点 / 参考系合成后可交给相机的**完整**姿态。
///
/// 位置与旋转都必须是有限值；FOV 为正的有限角度（单位与宿主相机 API 的边界转换在 `core`）。
/// 本类型不携带运行时掩码、错误或降级状态：失败必须在准备阶段报告，不能伪装成合法姿态。
///
/// @param position 世界坐标
/// @param rotation 世界旋转（弧度，YXZ）
/// @param fov      视场角，度；必须有限且为正
@NullMarked
public record WorldPose(Vec3 position, Rotation rotation, float fov) {

    public WorldPose {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");

        if (!Float.isFinite(fov) || fov <= 0f) {
            throw new IllegalArgumentException("WorldPose fov must be finite and positive: " + fov);
        }
    }
}
