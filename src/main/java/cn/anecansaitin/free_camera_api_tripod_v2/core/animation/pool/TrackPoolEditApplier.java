package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.pool;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RenamableTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.TrackPoolEditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.PooledRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool.TrackPoolAnimation;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 全局轨道池纯编辑操作应用器。
///
/// 只做结构操作并复用 [TrackPoolValidator] 的引用检查；发布、历史与通知由后续 store 承担。
/// 删除仍被属性/来源引用的轨道时明确拒绝，不留下悬空引用。
@NullMarked
public final class TrackPoolEditApplier {

    /// 应用一个操作。
    ///
    /// @param document 原文档
    /// @param op       纯操作
    /// @param revision 校验作用域
    /// @return 新文档
    /// @throws IllegalArgumentException 目标缺失、身份冲突或引用仍被使用时
    public TrackPoolAnimation apply(TrackPoolAnimation document,
                                    TrackPoolEditOp op,
                                    Revision revision) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(revision, "revision");

        TrackPoolAnimation candidate = switch (op) {
            case TrackPoolEditOp.ReplaceDocument replace -> replace.document();
            case TrackPoolEditOp.AddTrack add -> addTrack(document, add.track());
            case TrackPoolEditOp.RemoveTrack remove -> removeTrack(document, remove.trackId());
            case TrackPoolEditOp.RenameTrack rename -> renameTrack(
                    document, rename.trackId(), rename.name());
            case TrackPoolEditOp.ReplaceTrack replace -> replaceTrack(
                    document, replace.trackId(), replace.replacement());
            case TrackPoolEditOp.SetRigOutput set -> setRigOutput(
                    document, set.rigId(), set.output());
            case TrackPoolEditOp.SetRigProperties set -> setRigProperties(
                    document, set.rigId(), set.properties());
            case TrackPoolEditOp.SetCameraSourceBinding set -> document.withCameraSourceBinding(
                    set.binding());
            case TrackPoolEditOp.SetPlaybackRange set -> document.withPlaybackRange(set.range());
            case TrackPoolEditOp.AddRig add -> addRig(document, add.rig());
            case TrackPoolEditOp.RemoveRig remove -> removeRig(document, remove.rigId());
            case TrackPoolEditOp.RenameRig rename -> renameRig(
                    document, rename.rigId(), rename.name());
        };

        List<Diagnostic> diagnostics = new TrackPoolValidator().validate(candidate, revision);

        if (!diagnostics.isEmpty()) {
            throw new IllegalArgumentException(diagnostics.getFirst().message().toString());
        }

        return candidate;
    }

    private static TrackPoolAnimation addTrack(TrackPoolAnimation document, AnimationTrack track) {
        if (document.track(track.id()).isPresent()) {
            throw new IllegalArgumentException("Track id already exists: " + track.id());
        }

        List<AnimationTrack> tracks = new ArrayList<>(document.tracks());
        tracks.add(track);
        return document.withTracks(tracks);
    }

    private static TrackPoolAnimation removeTrack(TrackPoolAnimation document, TrackId trackId) {
        if (document.track(trackId).isEmpty()) {
            throw new IllegalArgumentException("Track does not exist: " + trackId);
        }

        List<AnimationTrack> tracks = new ArrayList<>(document.tracks());
        tracks.removeIf(track -> track.id().equals(trackId));
        return document.withTracks(tracks);
    }

    private static TrackPoolAnimation renameTrack(TrackPoolAnimation document,
                                                  TrackId trackId,
                                                  String name) {
        AnimationTrack track = document.track(trackId)
                .orElseThrow(() -> new IllegalArgumentException("Track does not exist: " + trackId));

        if (!(track instanceof RenamableTrack renamable)) {
            throw new IllegalArgumentException("Track does not support renaming: " + track.type());
        }

        AnimationTrack renamed = Objects.requireNonNull(renamable.withName(name), "withName result");

        if (!trackId.equals(renamed.id())
                || !track.type().equals(renamed.type())
                || renamed.getClass() != track.getClass()
                || !name.equals(renamed.name())) {
            throw new IllegalArgumentException(
                    "RenamableTrack must preserve identity, type and concrete class");
        }

        return replaceTrack(document, trackId, renamed);
    }

    private static TrackPoolAnimation replaceTrack(TrackPoolAnimation document,
                                                   TrackId trackId,
                                                   AnimationTrack replacement) {
        List<AnimationTrack> tracks = new ArrayList<>(document.tracks());
        boolean found = false;

        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).id().equals(trackId)) {
                tracks.set(index, replacement);
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("Track does not exist: " + trackId);
        }

        return document.withTracks(tracks);
    }

    private static TrackPoolAnimation addRig(TrackPoolAnimation document, PooledRig rig) {
        if (document.rig(rig.id()).isPresent()) {
            throw new IllegalArgumentException("Rig id already exists: " + rig.id());
        }

        List<PooledRig> rigs = new ArrayList<>(document.rigs());
        rigs.add(rig);
        return document.withRigs(rigs);
    }

    private static TrackPoolAnimation removeRig(TrackPoolAnimation document,
                                                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId rigId) {
        if (document.rig(rigId).isEmpty()) {
            throw new IllegalArgumentException("Rig does not exist: " + rigId);
        }

        if (document.rigs().size() <= 1) {
            throw new IllegalArgumentException("Track-pool document must keep at least one rig");
        }

        List<PooledRig> rigs = new ArrayList<>(document.rigs());
        rigs.removeIf(rig -> rig.id().equals(rigId));
        return document.withRigs(rigs);
    }

    private static TrackPoolAnimation renameRig(TrackPoolAnimation document,
                                                cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId rigId,
                                                String name) {
        PooledRig rig = document.rig(rigId)
                .orElseThrow(() -> new IllegalArgumentException("Rig does not exist: " + rigId));
        return document.withRig(rig.withName(name));
    }

    private static TrackPoolAnimation setRigOutput(TrackPoolAnimation document,
                                                   cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId rigId,
                                                   cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigOutput output) {
        PooledRig rig = document.rig(rigId)
                .orElseThrow(() -> new IllegalArgumentException("Rig does not exist: " + rigId));
        return document.withRig(rig.withOutput(output));
    }

    private static TrackPoolAnimation setRigProperties(TrackPoolAnimation document,
                                                       cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId rigId,
                                                       cn.anecansaitin.free_camera_api_tripod_v2.api.animation.property.RigProperties properties) {
        PooledRig rig = document.rig(rigId)
                .orElseThrow(() -> new IllegalArgumentException("Rig does not exist: " + rigId));
        return document.withRig(rig.withProperties(properties));
    }
}
