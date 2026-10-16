package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.expression.TrackLookup;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// 相机动画文档：编辑器与播放器共用的**不可变**创作数据（主设计 §3.1）。
///
/// ```text
/// CameraAnimation
///   dataVersion / name
///   rigs: 有序 CameraRig 集合
///   primaryRigId: 可空 RigId
///   tracks: 文档级有序轨道（事件等）
///   symbols: 共享变量与函数
///   markers / samples / audioRefs / overlays
/// ```
///
/// ## 三套身份，互不替代
///
/// | 身份 | 类型 | 作用域 |
/// | --- | --- | --- |
/// | 类型标识 | `net.minecraft.resources.Identifier` | 注册表分派 Codec 与校验器 |
/// | 文档对象身份 | 本包的 `*Id` | 各自 ID 类型内唯一并持久化 |
/// | 用户名称 | `String` | 可改、可本地化 |
///
/// 顺序由**集合自身顺序**表达，不由 ID 表达（主设计 §3.1）：
///
/// - `rigs` 顺序服务**显示**（层级相同时按此顺序合成）；
/// - 机位内通道顺序服务**序列化**；
/// - `tracks` 顺序服务**文档事件的稳定分派**。
///
/// ## 文档级 vs 每机位
///
/// | 数据 | 归属 | 理由 |
/// | --- | --- | --- |
/// | `tracks`（事件等） | 文档级 | 音轨与事件不随路复制，否则同一段音效会被 N 路各放一次 |
/// | `symbols` | 文档级 | 一份文档一张符号表；`TrackRef` 要求 `TrackId` 文档内唯一 |
/// | `markers` | 文档级 | 标记对齐时间，与哪台机位出画面无关 |
/// | `samples` | 文档级 | 世界对所有机位是同一个 |
/// | `audioRefs` / `overlays` | 文档级 | 成片叠加与参考音轨属于成片，不属于某一台机位 |
/// | 通道 / 路径 / 空间 / 注视 / 避障 / `view` | **每机位一份** | 不同机位绑不同实体、看不同目标、各自布局 |
///
/// ## primaryRigId 用单个可空引用
///
/// 用**一个**可空 `primaryRigId` 代替各机位的 `primary` 布尔值，排除“多台主机位”这种
/// 非法组合（主设计 D22、§3.1）：
///
/// - 为空 ⇒ **不接管玩家相机**，多路画面仍可合成，玩家保持正常控制；
/// - 非空 ⇒ 必须指向一台**启用**的机位（L2 引用完整性 + L3 语义校验）。
///
/// 关闭主机位的编辑操作必须**同时**显式清除或改选主机位，不能留下悬空的 `primaryRigId`。
///
/// ## 机位至少一台
///
/// `rigs` 不可为空：一台机位都没有的文档无法出画面，也无法承载 `primaryRigId`。
/// 该约束在构造期检查，属于纯结构约束。
///
/// ## 播放长度
///
/// 见 [duration]：取启用机位通道、文档轨道、标记、音轨范围与叠加范围的**有限最大结束时间**；
/// **静态布局不延长动画**。它是派生值，不是存储字段。
///
/// @param dataVersion 存档格式版本；顶层必填，新格式从 1 开始
/// @param name        文档显示名
/// @param rigs        有序机位集合，至少一台
/// @param primaryRigId 驱动玩家相机的机位；可为空（表示不接管）
/// @param tracks      文档级有序轨道（事件、特效等扩展轨道）
/// @param symbols     共享变量与自定义函数
/// @param markers     文档级标记，顺序即显示顺序
/// @param samples     世界采样声明
/// @param audioRefs   参考音轨引用（数据在最终音频阶段实现）
/// @param overlays    有序成片叠加元素
@NullMarked
public record CameraAnimation(
        int dataVersion,
        String name,
        List<CameraRig> rigs,
        @Nullable RigId primaryRigId,
        List<AnimationTrack.ExtensionTrack> tracks,
        Symbols symbols,
        List<Marker> markers,
        List<WorldSample> samples,
        List<AudioRef> audioRefs,
        List<OverlayElement> overlays) {

    /// 当前支持的格式版本。版本缺失、较旧或高于本值均明确拒绝，
    /// 不尝试按当前默认字段读取（主设计 §8.1）。
    public static final int CURRENT_DATA_VERSION = 1;

    public CameraAnimation {
        if (rigs.isEmpty()) {
            throw new IllegalArgumentException("CameraAnimation must contain at least one rig");
        }

        if (name.isBlank()) {
            throw new IllegalArgumentException("CameraAnimation name must not be blank");
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）。
        // 元素本身是不可变值，因此只复制容器，不复制元素。
        rigs = List.copyOf(rigs);
        tracks = List.copyOf(tracks);
        markers = List.copyOf(markers);
        samples = List.copyOf(samples);
        audioRefs = List.copyOf(audioRefs);
        overlays = List.copyOf(overlays);
    }

    // region 构造

    /// 新建单机位空文档：一台全屏机位、空符号表、无标记、无采样、无叠加。
    ///
    /// 单机位是**默认形态**，行为与重构前的“一份文档一套相机通道”一致：
    /// 这是保持现有用户体验的基线（主设计 §10 的 R1“单路基线”）。
    public static CameraAnimation create(String name, CameraRig rig) {
        return new CameraAnimation(CURRENT_DATA_VERSION, name, List.of(rig), rig.id(),
                List.of(), Symbols.EMPTY, List.of(), List.of(), List.of(), List.of());
    }

    // endregion

    // region 查询

    /// 按稳定 ID 找机位。
    public Optional<CameraRig> rig(RigId id) {
        for (CameraRig candidate : rigs) {
            if (candidate.id().equals(id)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    /// 主机位；`primaryRigId` 为空或指向不存在的机位时返回空。
    ///
    /// 指向不存在的机位是**非法文档**（L2 引用完整性会在提交前拒绝），
    /// 这里返回空只是让只读展示不炸，不作为合法性判据。
    public Optional<CameraRig> primaryRig() {
        return primaryRigId == null ? Optional.empty() : rig(primaryRigId);
    }

    /// 是否有主机位。为 `false` 时玩家相机不被任何机位驱动，但多路画面仍可合成。
    public boolean hasPrimaryRig() {
        return primaryRigId != null;
    }

    /// 按稳定 ID 查找**任意**轨道：先找机位内通道，再找文档级轨道。
    ///
    /// `TrackId` 在整份文档内唯一（跨机位也唯一），所以这个查找是全文档级的，
    /// 不需要“机位 + 轨道”二元键。`TrackRef` 因此也只需要一个轨道 ID。
    public Optional<AnimationTrack> track(TrackId id) {
        for (CameraRig rig : rigs) {
            Optional<CurveTrack> channel = rig.channelById(id);

            if (channel.isPresent()) {
                return Optional.of(channel.get());
            }
        }

        for (AnimationTrack.ExtensionTrack track : tracks) {
            if (track.id().equals(id)) {
                return Optional.of(track);
            }
        }

        return Optional.empty();
    }

    /// 按稳定 ID 查找曲线轨道（含机位通道与通用曲线）。
    ///
    /// 只读求值与变量读轨道都走这里；返回值仍是**不可变** `CurveTrack`，
    /// 修改要构造新的候选文档（主设计 §4.1：复合操作构造一个完整候选）。
    public Optional<CurveTrack> curveTrack(TrackId id) {
        return track(id).filter(CurveTrack.class::isInstance).map(CurveTrack.class::cast);
    }

    /// 全部曲线轨道：按机位顺序，各机位内按通道顺序。
    public List<CurveTrack> curveTracks() {
        return rigs.stream().flatMap(rig -> rig.channels().stream()).toList();
    }

    /// 启用机位（`view.enabled` 为真），保持文档顺序。
    ///
    /// 播放只对启用机位求值与渲染；**禁用机位不主动求值，但指向它的 `TrackRef` 仍按依赖求值**
    /// （主设计 §7.2）。
    public List<CameraRig> enabledRigs() {
        return rigs.stream().filter(rig -> rig.view().enabled()).toList();
    }

    // endregion

    // region 派生值

    /// 可播放时长：启用机位通道、文档轨道、标记、音轨范围与叠加范围的**有限最大结束时间**。
    ///
    /// **静态布局不延长动画**（主设计 §3.1）：`view` 的矩形与层级不参与时长计算，
    /// 否则动一下画中画位置就会改变动画长度。
    public float duration() {
        float duration = 0;

        for (CameraRig rig : enabledRigs()) {
            duration = Math.max(duration, rig.channelsDuration());
        }

        for (AnimationTrack.ExtensionTrack track : tracks) {
            duration = Math.max(duration, track.duration());
        }

        for (Marker marker : markers) {
            duration = Math.max(duration, marker.time());
        }

        for (AudioRef ref : audioRefs) {
            duration = Math.max(duration, ref.endTime());
        }

        for (OverlayElement overlay : overlays) {
            duration = Math.max(duration, overlay.endTime());
        }

        return duration;
    }

    /// 符号表驱动的曲线查询：供求值层按 `TrackRef` 解析轨道。
    ///
    /// 返回的查找器读**本快照**；版本变化后必须重新绑定，不能继续引用旧文档
    /// （主设计 §3.4：版本变化时重绑定符号表和曲线查询）。
    public TrackLookup trackLookup() {
        return this::curveTrack;
    }

    // endregion

    // region 派生对象

    /// 换文档名。
    public CameraAnimation withName(String newName) {
        return new CameraAnimation(dataVersion, newName, rigs, primaryRigId, tracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    /// 换机位集合。**顺序即显示顺序**，因此重排就是换一个列表。
    ///
    /// `primaryRigId` 的有效性（必须指向启用机位）由 L2/L3 校验负责，
    /// 不在 `with*` 里隐式夹取——静默改作者的主机位选择比报错更糟。
    public CameraAnimation withRigs(List<CameraRig> newRigs) {
        return new CameraAnimation(dataVersion, name, newRigs, primaryRigId, tracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    /// 换主机位；传 `null` 表示不接管玩家相机。
    public CameraAnimation withPrimaryRigId(@Nullable RigId newPrimaryRigId) {
        return new CameraAnimation(dataVersion, name, rigs, newPrimaryRigId, tracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    /// 换文档级轨道集合；顺序即事件分派顺序。
    public CameraAnimation withTracks(List<AnimationTrack.ExtensionTrack> newTracks) {
        return new CameraAnimation(dataVersion, name, rigs, primaryRigId, newTracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    public CameraAnimation withSymbols(Symbols newSymbols) {
        return new CameraAnimation(dataVersion, name, rigs, primaryRigId, tracks, newSymbols,
                markers, samples, audioRefs, overlays);
    }

    public CameraAnimation withMarkers(List<Marker> newMarkers) {
        return new CameraAnimation(dataVersion, name, rigs, primaryRigId, tracks, symbols,
                newMarkers, samples, audioRefs, overlays);
    }

    public CameraAnimation withSamples(List<WorldSample> newSamples) {
        return new CameraAnimation(dataVersion, name, rigs, primaryRigId, tracks, symbols,
                markers, newSamples, audioRefs, overlays);
    }

    public CameraAnimation withAudioRefs(List<AudioRef> newAudioRefs) {
        return new CameraAnimation(dataVersion, name, rigs, primaryRigId, tracks, symbols,
                markers, samples, newAudioRefs, overlays);
    }

    public CameraAnimation withOverlays(List<OverlayElement> newOverlays) {
        return new CameraAnimation(dataVersion, name, rigs, primaryRigId, tracks, symbols,
                markers, samples, audioRefs, newOverlays);
    }

    /// 替换一台机位（按 ID 定位），位置不变，顺序保留。
    ///
    /// 机位不存在时返回自身——调用方应先按 ID 解析；这条路径的“没找到”由 L2 校验报告，
    /// 不在 `with*` 里抛异常，便于构造候选时组合多个改动。
    public CameraAnimation withRig(CameraRig rig) {
        List<CameraRig> replaced = rigs.stream()
                .map(existing -> existing.id().equals(rig.id()) ? rig : existing)
                .toList();
        return withRigs(replaced);
    }

    // endregion
}
