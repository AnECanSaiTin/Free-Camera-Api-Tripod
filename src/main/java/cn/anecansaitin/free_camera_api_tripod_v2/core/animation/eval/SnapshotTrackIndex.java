package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.AnimationSnapshot;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.TrackIndex;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// 绑定一份快照的只读轨道索引。
///
/// 建立时一次性扫描机位通道、注视 / 活动轨道、相机来源与文档级扩展轨道；之后按
/// [TrackId] 查询不再线性扫描文档。重复 ID 不静默覆盖：冲突 ID 从查询表中移除，
/// 并在 [diagnostics] 中报告，调用方必须把该诊断当作候选 / 绑定失败处理。
///
/// 本类只建立结构索引，不解析公式、不读取世界、不判断机位活动区间。
@NullMarked
public final class SnapshotTrackIndex implements TrackIndex {

    private final AnimationSnapshot snapshot;
    private final Map<TrackId, Entry> entries;
    private final List<Diagnostic> diagnostics;

    private SnapshotTrackIndex(AnimationSnapshot snapshot,
                               Map<TrackId, Entry> entries,
                               List<Diagnostic> diagnostics) {
        this.snapshot = snapshot;
        this.entries = Map.copyOf(entries);
        this.diagnostics = List.copyOf(diagnostics);
    }

    /// 从快照建立索引。
    ///
    /// @param snapshot 已发布快照；索引保留同一引用，供求值上下文做同源检查
    public static SnapshotTrackIndex build(AnimationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        Builder builder = new Builder(snapshot);
        CameraAnimation document = snapshot.document();

        for (CameraRig rig : document.rigs()) {
            for (CurveTrack channel : rig.channels()) {
                builder.add(channel, rig.id());
            }

            builder.add(rig.aim(), rig.id());
            builder.add(rig.view().active(), rig.id());
        }

        builder.add(document.cameraSource(), null);

        for (AnimationTrack.ExtensionTrack track : document.tracks()) {
            builder.add(track, null);
        }

        return builder.finish();
    }

    @Override
    public AnimationSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public Optional<Entry> find(TrackId trackId) {
        Objects.requireNonNull(trackId, "trackId");
        return Optional.ofNullable(entries.get(trackId));
    }

    @Override
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    private static final class Builder {
        private final AnimationSnapshot snapshot;
        private final Map<TrackId, Entry> entries = new LinkedHashMap<>();
        private final Set<TrackId> duplicates = new HashSet<>();
        private final java.util.ArrayList<Diagnostic> diagnostics = new java.util.ArrayList<>();

        private Builder(AnimationSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void add(AnimationTrack track, @Nullable RigId ownerRig) {
            Entry entry = new Entry(track, ownerRig);

            if (duplicates.contains(track.id())) {
                reportDuplicate(track.id());
                return;
            }

            Entry previous = entries.putIfAbsent(track.id(), entry);

            if (previous == null) {
                return;
            }

            // 重复 ID：保留第一个用于定位，但从查询表移除；后续同名也不再放回。
            entries.remove(track.id());
            duplicates.add(track.id());
            reportDuplicate(track.id());
        }

        private void reportDuplicate(TrackId trackId) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.INVALID_EDIT,
                    Severity.ERROR,
                    LocalizedMessage.literal("Duplicate track id " + trackId
                            + "; the track is not addressable through this index"),
                    new DiagnosticScope.Document(snapshot.revision()),
                    new Diagnostic.Location(trackId, "tracks"),
                    null));
        }

        private SnapshotTrackIndex finish() {
            // 防御性检查：任何已记录冲突的 ID 都不得留在查询表中。
            duplicates.forEach(entries::remove);
            return new SnapshotTrackIndex(snapshot, entries, diagnostics);
        }
    }
}
