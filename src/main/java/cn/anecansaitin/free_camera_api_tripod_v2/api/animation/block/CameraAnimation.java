package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// 相机动画文档：编辑器与播放器共用的**不可变**创作数据（主设计 §3.1）。
///
/// ```text
/// CameraAnimation
///   dataVersion / name
///   rigs: 有序 CameraRig 集合
///   cameraSource: 文档级来源轨道（Player / Rig），默认 Player
///   tracks: 文档级有序轨道（事件等）
///   symbols / markers / samples / audioRefs / overlays
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
/// | `cameraSource` | 文档级 | 玩家相机每个时刻只有一个来源，属于整份成片（D22） |
/// | `tracks`（事件等） | 文档级 | 音轨与事件不随路复制，否则同一段音效会被 N 路各放一次 |
/// | `symbols` | 文档级 | 一份文档一张符号表；`TrackRef` 要求 `TrackId` 文档内唯一 |
/// | `markers` | 文档级 | 标记对齐时间，与哪台机位出画面无关 |
/// | `samples` | 文档级 | 世界对所有机位是同一个 |
/// | `audioRefs` / `overlays` | 文档级 | 成片叠加与参考音轨属于成片，不属于某一台机位 |
/// | 通道 / 路径 / 空间 / `aim` / `obstacle` / `view` | **每机位一份** | 不同机位绑不同实体、看不同目标、各自布局与活动区间 |
///
/// ## cameraSource 取代固定主机位
///
/// 旧实现用一个可空 `primaryRigId` 指定“哪台机位驱动玩家相机”，等于把来源钉死成
/// 整个文档一个值。主设计 D22 / §3.1.1 取消该字段，改由文档级
/// [cameraSource] 轨道明确指定**每个时刻**的来源：
///
/// - `Player`：不接管玩家相机，活动机位仍按布局合成；
/// - `Rig(RigId)`：引用必须存在，且在其被选择的每个区间活动。
///
/// 验证器合并来源轨道与全部机位活动轨道的阶跃边界逐区间检查；删除机位或关闭其静态
/// 总开关必须在同一候选里修复来源轨道，否则拒绝。**不得自动选择第一台活动机位**
/// （主设计 §3.1.1）。
///
/// ## 状态轨道只持久化一次
///
/// 每机位的注视轨道（`CameraRig.aim`）、活动轨道（`RigView.active`）与本文档的
/// `cameraSource` 都是正式时间轴轨道；`tracks` 通用表里**不再存**一份可独立修改的副本。
/// 统一时间轴目录由 [track]、[curveTracks] 一类查询从所有者派生（主设计 §3.1.1）。
///
/// ## 机位至少一台
///
/// `rigs` 不可为空：一台机位都没有的文档无法出画面。该约束在构造期检查，属于纯结构约束。
///
/// ## 播放长度
///
/// 见 [duration]：取启用机位的姿态 / 注视 / 活动轨道，加上相机来源、文档轨道、标记、
/// 音轨范围与叠加范围的**有限最大结束时间**；**静态布局不延长动画**。它是派生值，
/// 不是存储字段。
///
/// @param dataVersion  存档格式版本；顶层必填，新格式从 1 开始
/// @param name         文档显示名
/// @param rigs         有序机位集合，至少一台
/// @param cameraSource 玩家相机来源轨道；默认 `Player`、无键
/// @param tracks       文档级有序扩展轨道（事件、特效等）
/// @param symbols      共享变量与自定义函数
/// @param markers      文档级标记，顺序即显示顺序
/// @param samples      世界采样声明
/// @param audioRefs    参考音轨引用（数据在最终音频阶段实现）
/// @param overlays     有序成片叠加元素
@NullMarked
public record CameraAnimation(
        int dataVersion,
        String name,
        List<CameraRig> rigs,
        CameraSourceTrack cameraSource,
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

    /// 新建单机位空文档：一台全屏机位、来源 `Player`、空符号表、无标记、无采样、无叠加。
    ///
    /// 单机位是**默认形态**（主设计 §10 的 R1“单路基线”）。来源默认 `Player` 是设计口径：
    /// 新文档不隐式接管玩家相机；作者显式把来源设为该机位，属于一次正常的编辑提交。
    /// 这条口径取代旧实现“新建文档自动把首台机位设为主机位”的隐式选择——
    /// 主设计 §3.1.1 明确不得自动选择机位。
    ///
    /// `cameraSourceTrackId` 由调用方（宿主经 `IdGenerator`）提供：轨道 ID 必须在文档内唯一，
    /// 纯内核不持有 ID 生成所需的随机源或计数器。
    public static CameraAnimation create(String name, CameraRig rig, TrackId cameraSourceTrackId) {
        return new CameraAnimation(CURRENT_DATA_VERSION, name, List.of(rig),
                CameraSourceTrack.player(cameraSourceTrackId), List.of(), Symbols.EMPTY,
                List.of(), List.of(), List.of(), List.of());
    }

    // endregion

    // region 编解码

    /// 文档根的结构编解码（主设计 §8.1）。
    ///
    /// 字段：`data_version / name / rigs / camera_source / tracks / symbols / markers /
    /// samples / audio_refs`，以及**受检的可选** `overlays`。容器只组合成员 Codec，
    /// 自身不复制子对象字段规则。
    ///
    /// - `data_version` **必填**，且必须是严格整数：缺失、小数、溢出、较旧或更高一律拒绝
    ///   （复核 R34 C2 / R36-N1）。
    /// - `tracks` 按 `type` 查冻结的轨道类型注册表；未知类型返回解码错误。
    /// - `overlays` 在 O1–O3 前**双向拒绝**：解码时出现非空 `overlays` 报错，编码含非空
    ///   叠加的文档也报错（复核 R34 C1 / R35）。不能接受后静默丢弃作者数据；空列表、
    ///   `null` 或字段缺失仍然接受（`null` 由 `optionalFieldOf` 视为缺省）。
    /// - `overlays` 作为**正式声明的字段**参与 `keys()`：压缩 Ops（如
    ///   `JsonOps.COMPRESSED`）按声明顺序建立键表，未声明的键会取到错误索引
    ///   （复核 R36-N2）。这里不用 `mapResult` 直接读原始 MapLike。
    public static MapCodec<CameraAnimation> mapCodec(DocumentRegistries registries) {
        MapCodec<RawFields> raw = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codecs.strictInt().fieldOf("data_version").forGetter(RawFields::dataVersion),
                Codecs.nonBlankString().fieldOf("name").forGetter(RawFields::name),
                CameraRig.codec(registries).listOf().fieldOf("rigs").forGetter(RawFields::rigs),
                CameraSourceTrack.CODEC.fieldOf("camera_source").forGetter(RawFields::cameraSource),
                trackCodec(registries).listOf().fieldOf("tracks").forGetter(RawFields::tracks),
                Symbols.CODEC.fieldOf("symbols").forGetter(RawFields::symbols),
                Marker.CODEC.listOf().fieldOf("markers").forGetter(RawFields::markers),
                WorldSample.codec(registries).listOf().fieldOf("samples").forGetter(RawFields::samples),
                AudioRef.CODEC.listOf().fieldOf("audio_refs").forGetter(RawFields::audioRefs),
                Codec.PASSTHROUGH.optionalFieldOf("overlays").forGetter(RawFields::overlays)
        ).apply(instance, RawFields::new));

        return raw.flatXmap(
                fields -> overlaysUnsupported(fields.overlays())
                        ? DataResult.error(CameraAnimation::overlaysUnsupported)
                        : DataResult.success(new CameraAnimation(
                        fields.dataVersion(), fields.name(), fields.rigs(), fields.cameraSource(),
                        fields.tracks(), fields.symbols(), fields.markers(), fields.samples(),
                        fields.audioRefs(), List.of())),
                document -> document.overlays().isEmpty()
                        ? DataResult.success(new RawFields(
                        document.dataVersion(), document.name(), document.rigs(),
                        document.cameraSource(), document.tracks(), document.symbols(),
                        document.markers(), document.samples(), document.audioRefs(),
                        Optional.empty()))
                        : DataResult.error(CameraAnimation::overlaysUnsupported)
        ).validate(document -> document.dataVersion() == CURRENT_DATA_VERSION
                ? DataResult.success(document)
                : DataResult.error(() -> "Unsupported data version: " + document.dataVersion()
                        + ", expected " + CURRENT_DATA_VERSION));
    }

    /// 文档根解码后的原始字段；`overlays` 只用于受检，不进入结果文档。
    private record RawFields(
            int dataVersion,
            String name,
            List<CameraRig> rigs,
            CameraSourceTrack cameraSource,
            List<AnimationTrack.ExtensionTrack> tracks,
            Symbols symbols,
            List<Marker> markers,
            List<WorldSample> samples,
            List<AudioRef> audioRefs,
            Optional<Dynamic<?>> overlays) {
    }

    /// `overlays` 暂不可序列化时的统一错误文案（解码与编码共用）。
    private static String overlaysUnsupported() {
        return "overlays are not serializable yet (O1-O3 pending); "
                + "non-empty overlays are rejected instead of being silently dropped";
    }

    /// `overlays` 是否为**不支持**的内容：非空列表，或不是列表的值。
    ///
    /// 空列表与缺省（含 `null`，由 `optionalFieldOf` 归一为缺省）视为支持。
    private static boolean overlaysUnsupported(Optional<Dynamic<?>> overlays) {
        if (overlays.isEmpty()) {
            return false;
        }

        return overlays.get().asStreamOpt()
                .result()
                .map(stream -> stream.findAny().isPresent())
                .orElse(true);
    }

    /// 独立解析文档时使用的 Codec。
    public static Codec<CameraAnimation> codec(DocumentRegistries registries) {
        return mapCodec(registries).codec();
    }

    /// 扩展轨道的分派 Codec：读 `type`，查注册表，交给该类型的 `MapCodec`。
    ///
    /// 内建轨道（curve / aim / active / camera_source）不经过本表：
    /// 它们是框架的 sealed 分支，按所属位置（机位通道、`CameraRig.aim`、`RigView.active`、
    /// `cameraSource`）直接编解码。因此本表只服务 `AnimationTrack.ExtensionTrack`。
    ///
    /// `TrackType` 的类型参数已收窄到 `AnimationTrack.ExtensionTrack`（复核 R34 C3），
    /// 因此这里不需要、也不允许把任意轨道的 Codec 强转成扩展轨道 Codec。
    private static Codec<AnimationTrack.ExtensionTrack> trackCodec(DocumentRegistries registries) {
        return Identifier.CODEC.<AnimationTrack.ExtensionTrack>partialDispatch(
                "type",
                track -> DataResult.success(track.type()),
                id -> registries.tracks().find(id)
                        .<DataResult<MapCodec<? extends AnimationTrack.ExtensionTrack>>>map(
                                type -> DataResult.success(type.codec()))
                        .orElseGet(() -> DataResult.error(() -> "Unknown track type: " + id)));
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

    /// 本时刻驱动玩家相机的来源。
    ///
    /// **逐时刻给定**，不是全局可空字段：`Player` 表示不接管玩家相机。
    /// 引用合法性与“来源机位在被选择区间活动”由统一校验负责，这里不改写或夹取
    /// （主设计 §3.1.1、§4.2）。
    public CameraSource cameraSourceAt(float time) {
        return cameraSource.at(time);
    }

    /// 按稳定 ID 查找**任意**轨道：机位通道、注视轨道、活动轨道、相机来源轨道、文档级轨道。
    ///
    /// `TrackId` 在整份文档内唯一（跨机位也唯一），所以这个查找是全文档级的，
    /// 不需要“机位 + 轨道”二元键。`TrackRef` 因此也只需要一个轨道 ID。
    public Optional<AnimationTrack> track(TrackId id) {
        for (CameraRig rig : rigs) {
            Optional<CurveTrack> channel = rig.channelById(id);

            if (channel.isPresent()) {
                return Optional.of(channel.get());
            }

            if (rig.aim().id().equals(id)) {
                return Optional.of(rig.aim());
            }

            if (rig.view().active().id().equals(id)) {
                return Optional.of(rig.view().active());
            }
        }

        if (cameraSource.id().equals(id)) {
            return Optional.of(cameraSource);
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
    ///
    /// 注视 / 活动 / 来源是**非数值状态轨道**，因此不会命中本方法；
    /// `TrackRef` 指向它们必须被静态校验拒绝（主设计 §3.1.1）。
    public Optional<CurveTrack> curveTrack(TrackId id) {
        return track(id).filter(CurveTrack.class::isInstance).map(CurveTrack.class::cast);
    }

    /// 全部曲线轨道：按机位顺序，各机位内按通道顺序。
    public List<CurveTrack> curveTracks() {
        return rigs.stream().flatMap(rig -> rig.channels().stream()).toList();
    }

    /// 启用机位（静态总开关 `view.enabled` 为真），保持文档顺序。
    ///
    /// 播放只对启用机位求值与渲染；**禁用机位不主动求值，但指向它的 `TrackRef` 仍按依赖求值**
    /// （主设计 §7.2）。运行活动状态还要叠加 `view.active.at(t)`。
    public List<CameraRig> enabledRigs() {
        return rigs.stream().filter(rig -> rig.view().enabled()).toList();
    }

    // endregion

    // region 派生值

    /// 可播放时长：启用机位的姿态 / 注视 / 活动轨道，加相机来源、文档轨道、标记、
    /// 音轨范围与叠加范围的**有限最大结束时间**。
    ///
    /// **静态布局不延长动画**（主设计 §3.1）：`view` 的矩形与层级不参与时长计算，
    /// 否则动一下画中画位置就会改变动画长度。禁用机位的姿态与状态轨道也不延长时长。
    /// 标记没有 Tick / 指令 / 发声等执行副作用，但按当前设计规则仍参与时长推导
    /// （主设计 §3.1；是否分离播放与编辑范围属另行裁决）。
    ///
    /// 本方法只为遍历启用机位，**不为内部遍历先物化 `enabledRigs()` 列表**
    /// （主设计 §3.4：内部遍历避免派生列表；缓存属绑定层，不在文档对象里做）。
    public float duration() {
        float duration = 0;

        for (CameraRig rig : rigs) {
            if (!rig.view().enabled()) {
                continue;
            }

            duration = Math.max(duration, rig.contentDuration());
        }

        // 来源轨道属于文档：它决定玩家相机何时交给哪台机位，即使所有机位都被停用，
        // 来源键的存在仍说明作者在这段区间上有创作意图。
        duration = Math.max(duration, cameraSource.duration());

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
            // 用派生查询而不是创作数据访问器：未启用元素的 endTime 仍是作者存储的原始终点。
            duration = Math.max(duration, overlay.durationContribution());
        }

        return duration;
    }

    // endregion

    // region 派生对象

    /// 换文档名。
    public CameraAnimation withName(String newName) {
        return new CameraAnimation(dataVersion, newName, rigs, cameraSource, tracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    /// 换机位集合。**顺序即显示顺序**，因此重排就是换一个列表。
    ///
    /// 来源轨道的引用有效性（引用的机位存在且在全部被选区间活动）由 L2/L3 校验负责，
    /// 不在 `with*` 里隐式夹取——静默改作者的来源选择比报错更糟（主设计 §3.1.1）。
    public CameraAnimation withRigs(List<CameraRig> newRigs) {
        return new CameraAnimation(dataVersion, name, newRigs, cameraSource, tracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    /// 换相机来源轨道。引用区间校验同样留给统一校验，不在这里自动改选来源。
    public CameraAnimation withCameraSource(CameraSourceTrack newCameraSource) {
        return new CameraAnimation(dataVersion, name, rigs, newCameraSource, tracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    /// 换文档级扩展轨道集合；顺序即事件分派顺序。
    public CameraAnimation withTracks(List<AnimationTrack.ExtensionTrack> newTracks) {
        return new CameraAnimation(dataVersion, name, rigs, cameraSource, newTracks, symbols,
                markers, samples, audioRefs, overlays);
    }

    public CameraAnimation withSymbols(Symbols newSymbols) {
        return new CameraAnimation(dataVersion, name, rigs, cameraSource, tracks, newSymbols,
                markers, samples, audioRefs, overlays);
    }

    public CameraAnimation withMarkers(List<Marker> newMarkers) {
        return new CameraAnimation(dataVersion, name, rigs, cameraSource, tracks, symbols,
                newMarkers, samples, audioRefs, overlays);
    }

    public CameraAnimation withSamples(List<WorldSample> newSamples) {
        return new CameraAnimation(dataVersion, name, rigs, cameraSource, tracks, symbols,
                markers, newSamples, audioRefs, overlays);
    }

    public CameraAnimation withAudioRefs(List<AudioRef> newAudioRefs) {
        return new CameraAnimation(dataVersion, name, rigs, cameraSource, tracks, symbols,
                markers, samples, newAudioRefs, overlays);
    }

    public CameraAnimation withOverlays(List<OverlayElement> newOverlays) {
        return new CameraAnimation(dataVersion, name, rigs, cameraSource, tracks, symbols,
                markers, samples, audioRefs, newOverlays);
    }

    /// 替换一台机位（按 ID 定位），位置不变，顺序保留。
    ///
    /// 三种结果：
    ///
    /// - **未找到该 ID**：返回自身（`this`）。不静默创建、不抛异常，便于组合候选改动；
    ///   “编辑目标不存在 / 过期”由提交层单独诊断，不能把未变化候选当作目标存在的证明
    ///   （主设计 §4.5）。
    /// - **找到且内容相等**：返回自身。无内容变化不应产生新文档根、新快照或新历史项。
    /// - **真实替换**：保持原顺序，只替换该位置；原文档不被修改，返回派生对象。
    public CameraAnimation withRig(CameraRig rig) {
        for (int i = 0; i < rigs.size(); i++) {
            if (!rigs.get(i).id().equals(rig.id())) {
                continue;
            }

            if (rigs.get(i).equals(rig)) {
                return this;
            }

            List<CameraRig> replaced = new ArrayList<>(rigs);
            replaced.set(i, rig);
            return withRigs(replaced);
        }

        return this;
    }

    // endregion
}
