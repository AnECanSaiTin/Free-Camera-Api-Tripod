package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 公式可读取的空间事实（主设计 §6.1、不变量 I7）。
///
/// 明确区分：
///
/// - 世界坐标 [worldPosition]；
/// - 机位锚点局部坐标 [anchorLocalPosition]；
/// - 锚点到目标的距离 [anchorToTargetDistance]。
///
/// 只读**已准备**的外部锚点 / 目标事实，不反读待求值相机；字段为空表示该读数在当前上下文
/// 不可用（例如没有目标或未声明锚点），依赖它的公式必须报告上下文缺失，不得换个数。
///
/// @param worldPosition         世界坐标；无上下文时为空
/// @param anchorLocalPosition   机位锚点局部坐标；无锚点时为空
/// @param anchorToTargetDistance 锚点到目标的距离；无锚点或目标时为空
@NullMarked
public record SpatialReadings(
        @Nullable Vec3 worldPosition,
        @Nullable Vec3 anchorLocalPosition,
        @Nullable Double anchorToTargetDistance) {

    /// 没有任何空间读数的上下文。
    public static final SpatialReadings EMPTY = new SpatialReadings(null, null, null);

    public SpatialReadings {
        if (anchorToTargetDistance != null && (!Double.isFinite(anchorToTargetDistance) || anchorToTargetDistance < 0)) {
            throw new IllegalArgumentException(
                    "SpatialReadings distance must be finite and non-negative: " + anchorToTargetDistance);
        }
    }

    /// 是否完全没有任何可用读数。
    public boolean isEmpty() {
        return worldPosition == null && anchorLocalPosition == null && anchorToTargetDistance == null;
    }
}
