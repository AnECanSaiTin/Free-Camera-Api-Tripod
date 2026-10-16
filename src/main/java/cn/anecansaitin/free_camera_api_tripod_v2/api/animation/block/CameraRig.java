package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CameraChannel;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimTarget;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// 机位：一台可独立取景并出画面的相机（主设计 §3.1、§5.5.1）。
///
/// ## 一份文档若干机位
///
/// 多机位表示**同一时间上的分屏或画中画**，不引入镜头剪辑、切换或过渡。
/// 每帧为每个启用机位渲染一路独立画面，再按作者设定的矩形合成；多路共用同一时钟与启停。
///
/// 机位之间**不复制**文档级数据：事件、音轨、标记、世界采样与成片叠加属于文档，
/// 不属于某一个机位（否则同一段音效会被 N 路各放一次）。
///
/// ## 每机位一份的设置
///
/// `space` / `aimTarget` / `obstacle` / `path` / `view` 都是**每机位一份**：
/// 不同机位可以绑不同实体、看不同目标、各自开关避障、各自设置画面矩形。
///
/// - `space`：参考系与默认锚点（D3：每机位可保存默认锚点、注视目标与避障设置）。
/// - `aimTarget`：注视目标；可空表示该机位不注视任何目标。播放请求可按 `RigId` 覆盖，
///   并区分“继承默认 / 显式禁用 / 替换”三种意图。
/// - `obstacle`：避障默认值，见 [ObstacleSettings]。
///
/// ## channels 的顺序与语义键的唯一性
///
/// `channels` 是**有序**集合：顺序用于序列化，也是时间轴上通道行的显示顺序。
///
/// 相机通道的语义键（[CameraChannel]）在**机位内唯一**：不允许两条轨道驱动同一个量。
/// `TrackId` 则在整个文档内唯一。两者分离，因此相机轨道也能有作者改过的显示名与独立 ID。
///
/// `MotionMode.PATH` 与 `MotionMode.COORDINATE` 互斥地决定位置来源，
/// 该约束属于 L3 语义校验（「模式与通道关系」），不在构造器里强制：
/// 强制会让“先切模式再补通道”的中间态无法构造为候选文档。
///
/// ## 不可变与派生
///
/// 全部字段是 `record` 组件与不可变值。`with*` 返回派生对象，**只复制变化路径**
/// （主设计 §3.2），允许共享未变化的不可变对象。集合在构造时防御性复制，
/// 不保留调用方容器。
///
/// @param id         稳定身份，文档内唯一；移动、排序、改名、撤销都保留它
/// @param name       用户可见名称
/// @param channels   相机通道轨道，顺序即序列化顺序；语义键在机位内唯一
/// @param path       绑定路径（`MotionMode.PATH` 时用于取位置）
/// @param motionMode 位置来源（路径或坐标通道）
/// @param distanceMode 路径模式下“位置通道”的口径（绝对弧长或百分比）
/// @param space      参考系与默认锚点
/// @param aimTarget  注视目标；为空表示不注视
/// @param obstacle   避障默认设置
/// @param view       画面合成设置（矩形、层级、质量、启用）
@NullMarked
public record CameraRig(
        RigId id,
        String name,
        List<CurveTrack> channels,
        Path path,
        MotionMode motionMode,
        DistanceMode distanceMode,
        Space space,
        @Nullable AimTarget aimTarget,
        ObstacleSettings obstacle,
        RigView view) {

    /// 相机通道的三轴分组基准：位置与旋转各有三根轴，顺序固定 X → Y → Z。
    public CameraRig {
        if (name.isBlank()) {
            throw new IllegalArgumentException("CameraRig name must not be blank");
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        channels = List.copyOf(channels);
    }

    /// 新建机位：全屏、每帧、全分辨率、无注视、不避障、世界空间、坐标模式。
    ///
    /// `path` 用空路径而不是 null：位置在路径模式下必须有一个可求值对象，
    /// “路径模式但路径为空”应当是**空路径**（求值时报没有节点），而不是“路径缺失”这种第二态。
    public static CameraRig create(RigId id, String name, List<CurveTrack> channels) {
        return new CameraRig(id, name, channels, Path.EMPTY, MotionMode.COORDINATE, DistanceMode.ABSOLUTE,
                new Space.World(), null, ObstacleSettings.DISABLED, RigView.FULL);
    }

    /// 按语义键找相机通道轨道。
    public Optional<CurveTrack> channel(CameraChannel channel) {
        for (CurveTrack track : channels) {
            if (track.channel() == channel) {
                return Optional.of(track);
            }
        }

        return Optional.empty();
    }

    /// 按轨道 ID 找通道轨道。
    ///
    /// 与 `CameraAnimation` 的文档级查找不同，本方法只在**本机位内**找，
    /// 用于“这条轨道是不是我这个机位的通道”这种判断。
    public Optional<CurveTrack> channelById(TrackId trackId) {
        for (CurveTrack track : channels) {
            if (track.id().equals(trackId)) {
                return Optional.of(track);
            }
        }

        return Optional.empty();
    }

    /// 该机位是否注视某个目标。
    public boolean aiming() {
        return aimTarget != null;
    }

    /// 该机位的位置是否来自路径。
    public boolean onPath() {
        return motionMode == MotionMode.PATH;
    }

    /// 机位自身的结束时间：最后一个通道键的时间。
    ///
    /// 这是**通道**的结束时间，不是机位的播放长度。文档播放长度取启用机位通道、
    /// 文档轨道、标记、音轨范围与叠加范围的有限最大结束时间；静态布局不延长动画
    /// （主设计 §3.1）。
    public float channelsDuration() {
        float duration = 0;

        for (CurveTrack track : channels) {
            duration = Math.max(duration, track.duration());
        }

        return duration;
    }

    public CameraRig withName(String newName) {
        return new CameraRig(id, newName, channels, path, motionMode, distanceMode, space, aimTarget, obstacle, view);
    }

    /// 换通道集合。语义键唯一性与“模式与通道关系”由 L2/L3 校验负责，不在构造器里强制。
    public CameraRig withChannels(List<CurveTrack> newChannels) {
        return new CameraRig(id, name, newChannels, path, motionMode, distanceMode, space, aimTarget, obstacle, view);
    }

    public CameraRig withPath(Path newPath) {
        return new CameraRig(id, name, channels, newPath, motionMode, distanceMode, space, aimTarget, obstacle, view);
    }

    /// 换运动模式。**只改标记，不动通道**：
    /// 通道的增删换算是一次显式编辑操作（切换会丢关键帧，须由作者确认），
    /// 不能藏在 `with*` 里静默发生。
    public CameraRig withMotionMode(MotionMode newMode) {
        return new CameraRig(id, name, channels, path, newMode, distanceMode, space, aimTarget, obstacle, view);
    }

    /// 换距离口径。**只改标记，不换算已有键值**：
    /// 带换算的模式切换是显式编辑操作，且涉及公式时必须保持求值语义
    /// （主设计 §6.4：涉及表达式时不能仅换算 literal）。
    public CameraRig withDistanceMode(DistanceMode newMode) {
        return new CameraRig(id, name, channels, path, motionMode, newMode, space, aimTarget, obstacle, view);
    }

    /// 换参考系。**不换算坐标**：切换 World / Local 只改变空间解释
    /// （主设计 §6.4），界面必须提示并显示预览参考系。
    public CameraRig withSpace(Space newSpace) {
        return new CameraRig(id, name, channels, path, motionMode, distanceMode, newSpace, aimTarget, obstacle, view);
    }

    /// 换注视目标；传 `null` 表示该机位不注视。
    public CameraRig withAimTarget(@Nullable AimTarget newAimTarget) {
        return new CameraRig(id, name, channels, path, motionMode, distanceMode, space, newAimTarget, obstacle, view);
    }

    public CameraRig withObstacle(ObstacleSettings newObstacle) {
        return new CameraRig(id, name, channels, path, motionMode, distanceMode, space, aimTarget, newObstacle, view);
    }

    public CameraRig withView(RigView newView) {
        return new CameraRig(id, name, channels, path, motionMode, distanceMode, space, aimTarget, obstacle, newView);
    }

    /// 换身份，其余不变。只在**复制机位**时由 `IdGenerator` 配套使用：
    /// 复制对象生成新 ID，移动 / 排序 / 改名一律保留原 ID（主设计 §3.1）。
    ///
    /// 注意：复制机位时通道也要一并生成新身份，否则两条机位会共用同一批 `TrackId`，
    /// 破坏“`TrackId` 在整份文档内唯一”。
    public CameraRig withId(RigId newId) {
        return new CameraRig(newId, name, channels, path, motionMode, distanceMode, space, aimTarget, obstacle, view);
    }
}
