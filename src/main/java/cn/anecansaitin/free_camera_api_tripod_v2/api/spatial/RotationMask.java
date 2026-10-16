package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;

/// 逐轴启用掩码：哪几个轴参与锚点旋转跟随 / 注视覆盖。
///
/// 按 `(x, y, z)` 三元组表达，与 [Vec3] 的轴顺序一致。
///
/// ## 用在哪里
///
/// - `Space.Local.followMask`：锚点旋转沿哪几个轴跟随宿主（主设计 D4：参考系支持平移与
///   逐轴启用的旋转，**不支持缩放**）。
/// - `AimTarget.mask`：注视目标接管哪几个旋转轴。主设计 §6.3 只支持 Yaw/Pitch 注视，
///   Roll 保留通道或基础姿态；启用 Roll 注视在静态校验中被拒绝（L3），`none()` 之外的
///   组合由校验器判定，不在本类型里限制——这样第三方以后扩展也不必修公开类型。
///
/// ## 合成语义
///
/// 掩码作用于约定为 **YXZ 顺序**的锚点欧拉角：未启用的轴**置零**后再构造四元数，
/// 已启用的轴由锚点提供。见主设计 §6.4：
/// `qWorld = qAnchorMasked * qLocal`。
///
/// @param x X 轴（Yaw 方向的分量之一，取决于欧拉约定，权威定义在 `core` 的旋转实现里）
/// @param y Y 轴
/// @param z Z 轴（Roll）
@NullMarked
public record RotationMask(boolean x, boolean y, boolean z) {

    /// 三轴都不启用：锚点只提供平移，旋转完全由通道与基础姿态给出。
    public static final RotationMask NONE = new RotationMask(false, false, false);

    /// 三轴全部启用：锚点姿态完整接管旋转。
    public static final RotationMask ALL = new RotationMask(true, true, true);

    /// 只启用水平朝向（Yaw）与俯仰（Pitch）。注视的常用形态。
    public static final RotationMask YAW_PITCH = new RotationMask(true, true, false);

    /// 是否至少启用了一个轴。全部未启用时锚点不参与旋转合成。
    public boolean any() {
        return x || y || z;
    }

    /// 是否三个轴都启用。
    public boolean all() {
        return x && y && z;
    }

    public RotationMask withX(boolean value) {
        return new RotationMask(value, y, z);
    }

    public RotationMask withY(boolean value) {
        return new RotationMask(x, value, z);
    }

    public RotationMask withZ(boolean value) {
        return new RotationMask(x, y, value);
    }
}
