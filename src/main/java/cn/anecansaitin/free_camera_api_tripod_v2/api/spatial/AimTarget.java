package cn.anecansaitin.free_camera_api_tripod_v2.api.spatial;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
/// ## 在时间轴上的位置
///
/// 文档里不再有“可空单个注视目标”字段：机位的注视是 [AimState] 的有类型 STEP 轨道
/// （`CameraRig.aim`），`Off` 与 `Target(AimTarget)` 都是作者写下的状态。
/// 这使“0 秒注视 A、10 秒注视 B”“1 秒关闭注视、交还旋转通道”都由时间轴直接表达，
/// 不需要 Tick 事件、公式或按时间返回不同目标的自定义 `Anchor`（主设计 §3.1.1）。
///
/// 播放请求与预览按 `RigId` 覆盖注视，编辑器另有不写文档的预览目标。
/// 协议必须区分“继承当前时刻状态”“显式禁用”“替换为固定 Target”三种意图
/// （主设计 §7.1、§6.3），因此覆盖类型是
/// `api.animation.playback.AimOverride`（`Inherit` / `Disabled` / `Replace`，
/// Q5 裁决），而不是“可空 AimTarget”——可空无法表达“显式禁用”。
/// 覆盖只在宿主层应用，不回写状态轨道；求值层只接收已解析的生效 `AimState`。
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

    /// 结构编码：`target / mask / offset`，锚点经类型注册表分派。
    public static MapCodec<AimTarget> mapCodec(DocumentRegistries registries) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                registries.anchorCodec().fieldOf("target").forGetter(AimTarget::target),
                RotationMask.CODEC.fieldOf("mask").forGetter(AimTarget::mask),
                Vec3.CODEC.fieldOf("offset").forGetter(AimTarget::offset)
        ).apply(instance, AimTarget::new));
    }

    /// 独立解析注视配置时使用的 Codec。
    public static Codec<AimTarget> codec(DocumentRegistries registries) {
        return mapCodec(registries).codec();
    }
}
