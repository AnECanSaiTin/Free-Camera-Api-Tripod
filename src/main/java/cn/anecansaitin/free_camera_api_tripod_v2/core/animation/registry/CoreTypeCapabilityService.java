package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.registry;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ActiveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorResolverRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.AnchorTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleCollectorRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.SampleTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TrackTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeCapability;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeCapabilityService;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.TypeRegistry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.Set;

/// 基于冻结注册表快照的类型能力查询（主设计 §2.2、§6.3、§7.7）。
///
/// 本类只读五张已冻结注册表：common 侧锚点 / 采样 / 扩展轨道类型，以及客户端侧锚点解析器
/// / 采样采集器。查询不会注册类型、不会加载客户端类，也不会读取当前文档或世界。
///
/// ## 三态
///
/// | 查询 | `TYPE_NOT_REGISTERED` | `RUNTIME_IMPLEMENTATION_MISSING` | `AVAILABLE` |
/// | --- | --- | --- | --- |
/// | [anchor] | common 无锚点类型 | 有类型、无解析器 | 两者都有 |
/// | [sample] | common 无采样类型 | 有类型、无采集器 | 两者都有 |
/// | [track] | 非内建且未注册的扩展类型 | 不适用 | 内建类型或已注册扩展类型 |
///
/// 内建轨道由框架固定分支编解码，不进入扩展轨道注册表，因此 [track] 对内建 ID 返回
/// [TypeCapability#available]。能力缺失只影响本环境能否完整播放 / 预览，不使文档成为结构
/// 非法；调用方仍须把“类型不存在”与“实现缺失”分开报告。
///
/// ## 冻结
///
/// 构造时要求五张表都已冻结；否则查询可能看到构造后新增的类型，与“一次装配、只读运行”
/// 的前提不符，直接抛 [IllegalStateException]。
@NullMarked
public final class CoreTypeCapabilityService implements TypeCapabilityService {

    private static final Set<Identifier> BUILT_IN_TRACKS = Set.of(
            CurveTrack.TYPE,
            AimTrack.TYPE,
            ActiveTrack.TYPE,
            CameraSourceTrack.TYPE);

    private final AnchorTypeRegistry anchors;
    private final SampleTypeRegistry samples;
    private final TrackTypeRegistry tracks;
    private final AnchorResolverRegistry anchorResolvers;
    private final SampleCollectorRegistry sampleCollectors;

    /// 从文档注册表与客户端注册表构造。
    ///
    /// @param registries      已冻结的 common 侧三张类型注册表
    /// @param anchorResolvers 已冻结的客户端锚点解析器注册表
    /// @param sampleCollectors 已冻结的客户端采样采集器注册表
    public CoreTypeCapabilityService(DocumentRegistries registries,
                                     AnchorResolverRegistry anchorResolvers,
                                     SampleCollectorRegistry sampleCollectors) {
        this(Objects.requireNonNull(registries, "registries").anchors(),
                registries.samples(), registries.tracks(),
                anchorResolvers, sampleCollectors);
    }

    /// 显式传入五张注册表；构造后不得再注册类型。
    ///
    /// @param anchors          锚点类型注册表
    /// @param samples          采样类型注册表
    /// @param tracks           扩展轨道类型注册表
    /// @param anchorResolvers  锚点解析器注册表
    /// @param sampleCollectors 采样采集器注册表
    public CoreTypeCapabilityService(AnchorTypeRegistry anchors,
                                     SampleTypeRegistry samples,
                                     TrackTypeRegistry tracks,
                                     AnchorResolverRegistry anchorResolvers,
                                     SampleCollectorRegistry sampleCollectors) {
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.samples = Objects.requireNonNull(samples, "samples");
        this.tracks = Objects.requireNonNull(tracks, "tracks");
        this.anchorResolvers = Objects.requireNonNull(anchorResolvers, "anchorResolvers");
        this.sampleCollectors = Objects.requireNonNull(sampleCollectors, "sampleCollectors");

        requireFrozen("anchor type", anchors);
        requireFrozen("sample type", samples);
        requireFrozen("track type", tracks);
        requireFrozen("anchor resolver", anchorResolvers);
        requireFrozen("sample collector", sampleCollectors);
    }

    @Override
    public TypeCapability anchor(Identifier typeId) {
        Objects.requireNonNull(typeId, "typeId");

        if (anchors.find(typeId).isEmpty()) {
            return TypeCapability.typeNotRegistered(typeId);
        }

        return anchorResolvers.find(typeId).isPresent()
                ? TypeCapability.available(typeId)
                : TypeCapability.runtimeImplementationMissing(typeId);
    }

    @Override
    public TypeCapability sample(Identifier typeId) {
        Objects.requireNonNull(typeId, "typeId");

        if (samples.find(typeId).isEmpty()) {
            return TypeCapability.typeNotRegistered(typeId);
        }

        return sampleCollectors.find(typeId).isPresent()
                ? TypeCapability.available(typeId)
                : TypeCapability.runtimeImplementationMissing(typeId);
    }

    @Override
    public TypeCapability track(Identifier typeId) {
        Objects.requireNonNull(typeId, "typeId");

        return BUILT_IN_TRACKS.contains(typeId) || tracks.find(typeId).isPresent()
                ? TypeCapability.available(typeId)
                : TypeCapability.typeNotRegistered(typeId);
    }

    private static void requireFrozen(String label, TypeRegistry<?> registry) {
        if (!registry.frozen()) {
            throw new IllegalStateException(
                    label + " registry must be frozen before capability queries");
        }
    }
}
