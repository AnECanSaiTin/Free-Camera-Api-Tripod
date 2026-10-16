package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/// 相机通道的语义键（主设计 §3.1）。
///
/// ## 与稳定 ID 的分工
///
/// 通道的语义键（本枚举）与 `TrackId` 是两套身份：
///
/// - 语义键回答“这条轨道驱动相机的什么量”，**只在机位内唯一**。相机只认识这几个键，
///   所以它们由框架固定，不开放第三方注册（开放的是**轨道类型**，见 `TrackTypeRegistry`）。
/// - `TrackId` 回答“这条轨道是谁”，在整份文档内唯一。
///
/// 持久化时用 [#key] 的字符串，不用枚举序号；显示名与颜色取自通道描述注册表，
/// 作者可覆盖外观但语义键不变（主设计 §8.1）。
///
/// ## 位置通道的两个分支
///
/// `MotionMode` 决定位置来自路径还是坐标，两者互斥：
///
/// - `PATH` 模式只用 `PATH_DISTANCE`（沿路径的弧长或百分比）；
/// - `COORDINATE` 模式只用 `POSITION_X` / `POSITION_Y` / `POSITION_Z`。
///
/// 该约束属于 L3 语义校验（主设计 §4.2：「模式与通道关系」），不在本枚举里强制。
@NullMarked
public enum CameraChannel {
    /// 沿路径的位置：取值口径由 `DistanceMode` 决定（绝对弧长，或归一化进度）。
    PATH_DISTANCE("position", AxisGroup.PATH_DISTANCE),
    POSITION_X("position.x", AxisGroup.POSITION),
    POSITION_Y("position.y", AxisGroup.POSITION),
    POSITION_Z("position.z", AxisGroup.POSITION),
    ROTATION_X("rotation.x", AxisGroup.ROTATION),
    ROTATION_Y("rotation.y", AxisGroup.ROTATION),
    ROTATION_Z("rotation.z", AxisGroup.ROTATION),
    /// 视场角，单位与量纲由字段适配器给出（度）。
    FOV("fov", AxisGroup.FOV);

    /// 相机通道的分组。`Tracks` 的语义分组由它决定；组名与颜色由通道描述注册表提供，
    /// 这里只给枚举分组，不把显示文案写进 `api`。
    public enum AxisGroup {
        POSITION,
        ROTATION,
        FOV,
        PATH_DISTANCE
    }

    /// 位置与旋转各有三根轴；顺序固定为 X → Y → Z，掩码与逐轴补全都按这个顺序。
    public static final List<CameraChannel> POSITION_AXES =
            List.of(POSITION_X, POSITION_Y, POSITION_Z);

    /// 见 [#POSITION_AXES]
    public static final List<CameraChannel> ROTATION_AXES =
            List.of(ROTATION_X, ROTATION_Y, ROTATION_Z);

    private final String key;
    private final AxisGroup group;

    CameraChannel(String key, AxisGroup group) {
        this.key = key;
        this.group = group;
    }

    /// 持久化语义键。与 `TrackId` 无关，仅表达“驱动哪个量”。
    public String key() {
        return key;
    }

    public AxisGroup group() {
        return group;
    }

    /// 是否属于旋转三轴。旋转使用 YXZ 顺序合成，单位统一为弧度（主设计 §6.4）。
    public boolean isRotationAxis() {
        return group == AxisGroup.ROTATION;
    }

    /// 是否属于坐标位置三轴（不含路径距离）。
    public boolean isPositionAxis() {
        return group == AxisGroup.POSITION;
    }

    /// 按持久化语义键反查；未知键返回空。
    ///
    /// 未知键在解码期是**错误**（主设计 §8.1：非法枚举必须报错），
    /// 因此这里返回 `Optional` 让调用方给出诊断，而不是悄悄回退成某个默认通道。
    public static Optional<CameraChannel> byKey(String key) {
        for (CameraChannel channel : values()) {
            if (channel.key.equals(key)) {
                return Optional.of(channel);
            }
        }

        return Optional.empty();
    }

    /// 按轨道 ID 反查通道：轨道 ID 取值与语义键相同时视为该通道。
    ///
    /// 只在“把旧式以语义键当 ID 的轨道接进新模型”时使用；新文档的相机轨道
    /// 允许有独立 `TrackId` 与显示名，此时用轨道自身的语义键字段，不要用本方法猜。
    public static Optional<CameraChannel> byTrackId(TrackId trackId) {
        return byKey(trackId.value());
    }
}
