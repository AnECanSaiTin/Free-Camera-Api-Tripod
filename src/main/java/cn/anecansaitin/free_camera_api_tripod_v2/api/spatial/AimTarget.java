package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import org.jspecify.annotations.NullMarked;

/// 注视目标：让相机朝向某个锚点，而不是照着旋转通道 K 帧（主设计 §6.3）。
///
/// ## 三个字段
///
/// - `target`：复用 [Anchor]，所以注视实体、玩家、固定坐标走的是同一套解析机制，
///   锚点类型注册表新增类型时注视自动可用。
/// - `mask`：注视接管**哪几个旋转轴**。主设计 §6.3 只支持 Yaw/Pitch 注视，
///   Roll 保留通道或基础姿态；启用 Roll 注视在静态校验（L3）中被拒绝。
///   这样“注视目标 + 手 K 的 Roll”可以共存。
/// - `offset`：沿**世界轴**的偏移，用来瞄目标头顶、脚下或手中物品。
///
/// ## 与通道的关系
///
/// 合成分两步（主设计 §6.4）：
///
/// ```text
/// 先用 VALUE 分量覆盖基础姿态 → 得到 WorldPose
/// → 再按注视覆盖世界 Yaw/Pitch，保留 Roll
/// ```
///
/// 被注视覆盖的那两个轴上，**原旋转通道仍然必须通过错误检查**：
/// 注视不是“跳过该通道求值”的理由，否则一个坏公式会被注视悄悄掩盖（不变量 I9）。
///
/// ## 退化
///
/// 目标与相机位置重合、或方向非有限，是**错误**，不是“保持不变”；
/// 垂直方向的 Yaw 使用**本帧注视前**的 Yaw，不读上一帧输出
/// （否则会引入跨帧状态，让同一时刻两次求值结果不同）。
///
/// ## 覆盖语义
///
/// 每机位可有默认注视目标，播放请求按 `RigId` 覆盖，编辑器另有不写文档的预览目标。
/// 协议必须区分“继承默认”“显式禁用”“替换”三种意图（主设计 §7.1），
/// 因此覆盖类型是一个三态分支，而不是“可空 AimTarget”——可空无法表达“显式禁用”。
///
/// @param target 注视目标锚点
/// @param mask   接管哪几个旋转轴
/// @param offset 沿世界轴的偏移
@NullMarked
public record AimTarget(Anchor target, RotationMask mask, Vec3 offset) {

    /// 无偏移的注视目标。
    public static AimTarget of(Anchor target, RotationMask mask) {
        return new AimTarget(target, mask, Vec3.ZERO);
    }

    /// 启用水平与俯仰注视、无偏移的常用形态。
    public static AimTarget lookAt(Anchor target) {
        return new AimTarget(target, RotationMask.YAW_PITCH, Vec3.ZERO);
    }
}
