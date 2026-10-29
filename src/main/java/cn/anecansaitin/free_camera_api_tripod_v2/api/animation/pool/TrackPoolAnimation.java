package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AudioRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayElement;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Symbols;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.codec.Codecs;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.CameraSourceProperty;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PlaybackRange;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.PropertyBinding;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.registry.DocumentRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// 全局轨道池文档根（主设计 §1.3，data_version 2）。
///
/// 轨道只存在于本记录的 [tracks] 池中；机位与文档属性只保存绑定。所有可动画属性默认关闭，
/// 机位默认不输出。旧 data_version 文档不构造本模型，由解码边界明确拒绝。
@NullMarked
public record TrackPoolAnimation(
        int dataVersion,
        String name,
        List<AnimationTrack> tracks,
        List<PooledRig> rigs,
        PropertyBinding<CameraSourceProperty> cameraSourceBinding,
        Symbols symbols,
        List<Marker> markers,
        List<WorldSample> samples,
        List<AudioRef> audioRefs,
        List<OverlayElement> overlays,
        PlaybackRange playbackRange) {

    /// 当前全局轨道池格式版本。
    public static final int CURRENT_DATA_VERSION = 2;

    /// 只接受当前版本，旧版本在结构解码阶段返回错误而不是构造后抛异常。
    public static final Codec<Integer> DATA_VERSION_CODEC = Codec.INT.comapFlatMap(
            version -> version == CURRENT_DATA_VERSION
                    ? com.mojang.serialization.DataResult.success(version)
                    : com.mojang.serialization.DataResult.error(() ->
                            "Unsupported track pool data_version " + version
                                    + ", expected " + CURRENT_DATA_VERSION),
            version -> version);

    public TrackPoolAnimation {
        if (dataVersion != CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported track pool data_version " + dataVersion
                            + ", expected " + CURRENT_DATA_VERSION);
        }

        if (name.isBlank()) {
            throw new IllegalArgumentException("TrackPoolAnimation name must not be blank");
        }

        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(rigs, "rigs");
        Objects.requireNonNull(cameraSourceBinding, "cameraSourceBinding");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(markers, "markers");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(audioRefs, "audioRefs");
        Objects.requireNonNull(overlays, "overlays");
        Objects.requireNonNull(playbackRange, "playbackRange");

        tracks = List.copyOf(tracks);
        rigs = List.copyOf(rigs);
        markers = List.copyOf(markers);
        samples = List.copyOf(samples);
        audioRefs = List.copyOf(audioRefs);
        overlays = List.copyOf(overlays);

        if (rigs.isEmpty()) {
            throw new IllegalArgumentException("TrackPoolAnimation requires at least one rig");
        }

        if (!overlays.isEmpty()) {
            throw new IllegalArgumentException(
                    "Overlay content is not yet part of the track pool format");
        }

        Set<TrackId> trackIds = new HashSet<>();
        for (AnimationTrack track : tracks) {
            Objects.requireNonNull(track, "track");

            if (!trackIds.add(track.id())) {
                throw new IllegalArgumentException("Duplicate track id in global pool: " + track.id());
            }
        }

        Set<RigId> rigIds = new HashSet<>();
        for (PooledRig rig : rigs) {
            Objects.requireNonNull(rig, "rig");

            if (!rigIds.add(rig.id())) {
                throw new IllegalArgumentException("Duplicate rig id: " + rig.id());
            }
        }
    }

    /// 创建一个最小文档：一个默认隐藏机位、空轨道池、相机来源关闭。
    public static TrackPoolAnimation create(String name, PooledRig rig) {
        return new TrackPoolAnimation(CURRENT_DATA_VERSION, name, List.of(), List.of(rig),
                PropertyBinding.disabled(), Symbols.EMPTY, List.of(), List.of(), List.of(),
                List.of(), PlaybackRange.AUTO);
    }

    /// 结构 Codec。
    public static MapCodec<TrackPoolAnimation> mapCodec(DocumentRegistries registries) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                DATA_VERSION_CODEC.fieldOf("data_version").forGetter(TrackPoolAnimation::dataVersion),
                Codecs.nonBlankString().fieldOf("name").forGetter(TrackPoolAnimation::name),
                TrackPoolCodec.codec(registries).listOf().fieldOf("tracks")
                        .forGetter(TrackPoolAnimation::tracks),
                PooledRig.codec(registries).listOf().fieldOf("rigs").forGetter(TrackPoolAnimation::rigs),
                PropertyBinding.codec(CameraSourceProperty.CODEC).fieldOf("camera_source_binding")
                        .forGetter(TrackPoolAnimation::cameraSourceBinding),
                Symbols.CODEC.fieldOf("symbols").forGetter(TrackPoolAnimation::symbols),
                Marker.CODEC.listOf().fieldOf("markers").forGetter(TrackPoolAnimation::markers),
                WorldSample.codec(registries).listOf().fieldOf("samples")
                        .forGetter(TrackPoolAnimation::samples),
                AudioRef.CODEC.listOf().fieldOf("audio_refs").forGetter(TrackPoolAnimation::audioRefs),
                PlaybackRange.CODEC.fieldOf("playback_range").forGetter(TrackPoolAnimation::playbackRange)
        ).apply(instance, (dataVersion, name, tracks, rigs, cameraSourceBinding, symbols,
                           markers, samples, audioRefs, playbackRange) ->
                new TrackPoolAnimation(dataVersion, name, tracks, rigs, cameraSourceBinding,
                        symbols, markers, samples, audioRefs, List.of(), playbackRange)));
    }

    /// 独立 Codec。
    public static Codec<TrackPoolAnimation> codec(DocumentRegistries registries) {
        return mapCodec(registries).codec();
    }

    /// 内容自动结束时间：全局轨道、标记、音频引用与叠加的有限最大结束时间。
    ///
    /// 轨道是否绑定属性不影响时长贡献；空轨道不延长。标记按既有规则参与自动推导。
    public float contentEndSeconds() {
        float end = 0f;

        for (AnimationTrack track : tracks) {
            end = Math.max(end, track.duration());
        }

        for (Marker marker : markers) {
            end = Math.max(end, marker.time());
        }

        for (AudioRef audio : audioRefs) {
            end = Math.max(end, audio.offset() + audio.sourceDuration());
        }

        for (OverlayElement overlay : overlays) {
            end = Math.max(end, overlay.endTime());
        }

        return end;
    }

    /// 播放起点：自动模式为 0，手动模式为作者区间起点。
    public float playbackStartSeconds() {
        return playbackRange instanceof PlaybackRange.Manual manual ? manual.start() : 0f;
    }

    /// 播放终点：自动模式取内容结束时间，手动模式取作者区间终点。
    public float playbackEndSeconds() {
        return playbackRange instanceof PlaybackRange.Manual manual
                ? manual.end()
                : contentEndSeconds();
    }

    /// 播放区间长度。
    public float playbackDurationSeconds() {
        return playbackEndSeconds() - playbackStartSeconds();
    }

    public java.util.Optional<AnimationTrack> track(TrackId id) {
        return tracks.stream().filter(track -> track.id().equals(id)).findFirst();
    }

    public java.util.Optional<PooledRig> rig(RigId id) {
        return rigs.stream().filter(rig -> rig.id().equals(id)).findFirst();
    }

    public TrackPoolAnimation withName(String newName) {
        return new TrackPoolAnimation(dataVersion, newName, tracks, rigs, cameraSourceBinding,
                symbols, markers, samples, audioRefs, overlays, playbackRange);
    }

    public TrackPoolAnimation withTracks(List<AnimationTrack> newTracks) {
        return new TrackPoolAnimation(dataVersion, name, newTracks, rigs, cameraSourceBinding,
                symbols, markers, samples, audioRefs, overlays, playbackRange);
    }

    public TrackPoolAnimation withRigs(List<PooledRig> newRigs) {
        return new TrackPoolAnimation(dataVersion, name, tracks, newRigs, cameraSourceBinding,
                symbols, markers, samples, audioRefs, overlays, playbackRange);
    }

    /// 按 ID 替换机位；目标不存在时返回原文档。
    public TrackPoolAnimation withRig(PooledRig replacement) {
        java.util.Objects.requireNonNull(replacement, "replacement");
        List<PooledRig> newRigs = new java.util.ArrayList<>(rigs);

        for (int index = 0; index < newRigs.size(); index++) {
            if (newRigs.get(index).id().equals(replacement.id())) {
                newRigs.set(index, replacement);
                return withRigs(newRigs);
            }
        }

        return this;
    }

    public TrackPoolAnimation withCameraSourceBinding(
            PropertyBinding<CameraSourceProperty> newBinding) {
        return new TrackPoolAnimation(dataVersion, name, tracks, rigs, newBinding,
                symbols, markers, samples, audioRefs, overlays, playbackRange);
    }

    public TrackPoolAnimation withPlaybackRange(PlaybackRange newRange) {
        return new TrackPoolAnimation(dataVersion, name, tracks, rigs, cameraSourceBinding,
                symbols, markers, samples, audioRefs, overlays, newRange);
    }
}
