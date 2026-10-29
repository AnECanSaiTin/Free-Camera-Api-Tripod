package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.document;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.*;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.EditOp;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Variable;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.AudioRefId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.KeyId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.MarkerId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.OverlayId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.PathNodeId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.SampleId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.path.PathNode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.AimState;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;

import javax.swing.text.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 把纯 [EditOp] 应用到一个不可变文档候选。
///
/// 本类只做结构操作，不做统一文档校验；发布边界由 [CoreAnimationDocumentStore] 负责。
/// 所有操作都返回新对象或原对象，不修改输入文档。目标不存在、身份冲突和明显的
/// 局部约束错误以 [EditApplyException] 拒绝，再由存储层转成结构化诊断。
final class EditOpApplier {

    private EditOpApplier() {
    }

    static CameraAnimation apply(CameraAnimation document, EditOp operation) {
        return switch (operation) {
            case EditOp.Batch(List<EditOp> operations) -> applyBatch(document, operations);
            case EditOp.RenameDocument(String name) -> document.withName(name);
            case EditOp.ReplaceDocument(CameraAnimation replace) -> replace;

            case EditOp.AddRig(CameraRig rig) -> addRig(document, rig);
            case EditOp.RemoveRig(RigId rigId) -> removeRig(document, rigId);
            case EditOp.ReplaceRig(RigId rigId, CameraRig rig) -> replaceRig(document, rigId, rig);
            case EditOp.ReorderRigs(List<RigId> order) -> reorderRigs(document, order);
            case EditOp.SetRigName(RigId rigId, String name) -> document.withRig(requireRig(document, rigId).withName(name));
            case EditOp.SetRigSpace(RigId rigId, Space space) -> document.withRig(requireRig(document, rigId).withSpace(space));
            case EditOp.SetRigMotionMode(RigId rigId, MotionMode mode) -> document.withRig(requireRig(document, rigId).withMotionMode(mode));
            case EditOp.SetRigDistanceMode(RigId rigId, DistanceMode mode) -> document.withRig(requireRig(document, rigId).withDistanceMode(mode));
            case EditOp.SetRigPath(RigId rigId, Path path) -> document.withRig(requireRig(document, rigId).withPath(path));
            case EditOp.SetRigAim(RigId rigId, AimTrack aim) -> document.withRig(requireRig(document, rigId).withAim(aim));
            case EditOp.SetRigObstacle(RigId rigId, ObstacleSettings obstacle) -> document.withRig(requireRig(document, rigId).withObstacle(obstacle));
            case EditOp.SetRigView(RigId rigId, RigView view) -> document.withRig(requireRig(document, rigId).withView(view));
            case EditOp.SetRigEnabled set ->
                    document.withRig(requireRig(document, set.rigId()).withView(
                            requireRig(document, set.rigId()).view().withEnabled(set.enabled())));
            case EditOp.SetRigRect set ->
                    document.withRig(requireRig(document, set.rigId()).withView(
                            requireRig(document, set.rigId()).view().withRect(set.rect())));
            case EditOp.SetRigZOrder set ->
                    document.withRig(requireRig(document, set.rigId()).withView(
                            requireRig(document, set.rigId()).view().withZOrder(set.zOrder())));
            case EditOp.SetRigQuality set ->
                    document.withRig(requireRig(document, set.rigId()).withView(
                            requireRig(document, set.rigId()).view().withQuality(
                                    set.resolutionScale(), set.updateEveryFrames())));

            case EditOp.AddChannel add -> addChannel(document, add.rigId(), add.channel());
            case EditOp.RemoveChannel remove -> removeChannel(document, remove.rigId(), remove.trackId());
            case EditOp.ReplaceChannel replace -> replaceChannel(document, replace.rigId(), replace.channel());

            case EditOp.AddTrack add -> addTrack(document, add.track());
            case EditOp.RemoveTrack remove -> removeTrack(document, remove.trackId());
            case EditOp.ReplaceTrack replace -> replaceTrack(document, replace.trackId(), replace.replacement());
            case EditOp.RenameTrack rename -> renameTrack(document, rename.trackId(), rename.name());
            case EditOp.ReorderTracks reorder -> reorderTracks(document, reorder.order());
            case EditOp.SetCameraSource set -> setCameraSource(document, set.cameraSource());

            case EditOp.AddNumberKey add -> addNumberKey(document, add.trackId(), add.keyframe());
            case EditOp.AddValueKey add -> addValueKey(document, add.trackId(), add.keyframe());
            case EditOp.RemoveKey remove -> removeKey(document, remove.trackId(), remove.keyId());
            case EditOp.MoveKeys move -> moveKeys(document, move.moves());
            case EditOp.SetKeyValue set -> setKeyValue(document, set.trackId(), set.keyId(), set.value());
            case EditOp.SetStateKey set -> setStateKey(document, set.trackId(), set.keyId(), set.value());
            case EditOp.SetKeyHandles set -> setKeyHandles(document, set.trackId(), set.keyId(),
                    set.inSlope(), set.outSlope(), set.inLength(), set.outLength());
            case EditOp.SetKeyInterpolation set ->
                    setKeyInterpolation(document, set.trackId(), set.keyId(), set.interpolation());
            case EditOp.ReplaceKeys replace -> replaceKeys(document, replace.trackId(), replace.keys());
            case EditOp.ReplaceValueKeys replace ->
                    replaceValueKeys(document, replace.trackId(), replace.keys());

            case EditOp.AddVariable add -> addVariable(document, add.variable());
            case EditOp.RemoveVariable remove -> removeVariable(document, remove.name());
            case EditOp.ReplaceVariable replace -> replaceVariable(document, replace.name(), replace.variable());
            case EditOp.AddFunction add -> addFunction(document, add.function());
            case EditOp.RemoveFunction remove -> removeFunction(document, remove.name());
            case EditOp.ReplaceFunction replace -> replaceFunction(document, replace.name(), replace.function());

            case EditOp.AddPathNode add -> addPathNode(document, add.rigId(), add.node());
            case EditOp.RemovePathNode remove -> removePathNode(document, remove.rigId(), remove.nodeId());
            case EditOp.ReplacePathNode replace ->
                    replacePathNode(document, replace.rigId(), replace.nodeId(), replace.node());
            case EditOp.ReorderPathNodes reorder ->
                    reorderPathNodes(document, reorder.rigId(), reorder.order());

            case EditOp.AddMarker add -> addMarker(document, add.marker());
            case EditOp.RemoveMarker remove -> removeMarker(document, remove.markerId());
            case EditOp.ReplaceMarker replace -> replaceMarker(document, replace.markerId(), replace.marker());
            case EditOp.AddSample add -> addSample(document, add.sample());
            case EditOp.RemoveSample remove -> removeSample(document, remove.sampleId());
            case EditOp.ReplaceSample replace -> replaceSample(document, replace.sampleId(), replace.sample());
            case EditOp.AddAudioRef add -> addAudioRef(document, add.audioRef());
            case EditOp.RemoveAudioRef remove -> removeAudioRef(document, remove.audioRefId());
            case EditOp.ReplaceAudioRef replace ->
                    replaceAudioRef(document, replace.audioRefId(), replace.audioRef());
            case EditOp.AddOverlay add -> addOverlay(document, add.overlay());
            case EditOp.RemoveOverlay remove -> removeOverlay(document, remove.overlayId());
            case EditOp.ReplaceOverlay replace ->
                    replaceOverlay(document, replace.overlayId(), replace.overlay());
            case EditOp.ReorderOverlays reorder -> reorderOverlays(document, reorder.order());
        };
    }

    // region 文档 / 机位

    private static CameraAnimation applyBatch(CameraAnimation document, List<EditOp> operations) {
        CameraAnimation candidate = document;

        for (EditOp operation : operations) {
            candidate = apply(candidate, operation);
        }

        return candidate;
    }

    private static CameraRig requireRig(CameraAnimation document, RigId id) {
        return document.rig(id).orElseThrow(() -> target("rig", id));
    }

    private static CameraAnimation addRig(CameraAnimation document, CameraRig rig) {
        if (document.rig(rig.id()).isPresent()) {
            throw invalid("A rig with id " + rig.id() + " already exists");
        }

        List<CameraRig> rigs = new ArrayList<>(document.rigs());
        rigs.add(rig);
        return document.withRigs(rigs);
    }

    private static CameraAnimation removeRig(CameraAnimation document, RigId id) {
        if (document.rigs().size() <= 1) {
            throw invalid("A document must keep at least one rig");
        }

        List<CameraRig> rigs = new ArrayList<>(document.rigs());
        if (!rigs.removeIf(rig -> rig.id().equals(id))) {
            throw target("rig", id);
        }

        return document.withRigs(rigs);
    }

    private static CameraAnimation replaceRig(CameraAnimation document, RigId id, CameraRig replacement) {
        if (!id.equals(replacement.id())) {
            throw invalid("ReplaceRig must preserve the rig id: " + id + " != " + replacement.id());
        }

        requireRig(document, id);
        return document.withRig(replacement);
    }

    private static CameraAnimation reorderRigs(CameraAnimation document, List<RigId> order) {
        if (!sameIds(document.rigs().stream().map(CameraRig::id).toList(), order)) {
            throw invalid("ReorderRigs must contain every existing rig id exactly once");
        }

        Map<RigId, CameraRig> byId = new LinkedHashMap<>();
        for (CameraRig rig : document.rigs()) {
            byId.put(rig.id(), rig);
        }

        return document.withRigs(order.stream().map(byId::get).toList());
    }

    // endregion

    // region 通道 / 文档轨道

    private static CameraAnimation addChannel(CameraAnimation document, RigId rigId, CurveTrack channel) {
        CameraRig rig = requireRig(document, rigId);
        requireFreeTrackId(document, channel.id());

        if (channel.channel() != null && rig.channel(channel.channel()).isPresent()) {
            throw invalid("Rig " + rigId + " already has a channel for " + channel.channel());
        }

        List<CurveTrack> channels = new ArrayList<>(rig.channels());
        channels.add(channel);
        return document.withRig(rig.withChannels(channels));
    }

    private static CameraAnimation removeChannel(CameraAnimation document, RigId rigId, TrackId trackId) {
        CameraRig rig = requireRig(document, rigId);
        List<CurveTrack> channels = new ArrayList<>(rig.channels());

        if (!channels.removeIf(track -> track.id().equals(trackId))) {
            throw target("channel", trackId);
        }

        return document.withRig(rig.withChannels(channels));
    }

    private static CameraAnimation replaceChannel(CameraAnimation document, RigId rigId, CurveTrack channel) {
        CameraRig rig = requireRig(document, rigId);
        List<CurveTrack> channels = new ArrayList<>(rig.channels());
        int index = indexOfTrack(channels, channel.id());

        if (index < 0) {
            throw target("channel", channel.id());
        }

        if (channel.channel() != null) {
            for (int i = 0; i < channels.size(); i++) {
                if (i != index && channels.get(i).channel() == channel.channel()) {
                    throw invalid("Rig " + rigId + " already has a channel for " + channel.channel());
                }
            }
        }

        channels.set(index, channel);
        return document.withRig(rig.withChannels(channels));
    }

    private static CameraAnimation addTrack(CameraAnimation document,
                                            AnimationTrack.ExtensionTrack track) {
        requireFreeTrackId(document, track.id());

        List<AnimationTrack.ExtensionTrack> tracks = new ArrayList<>(document.tracks());
        tracks.add(track);
        return document.withTracks(tracks);
    }

    private static CameraAnimation removeTrack(CameraAnimation document, TrackId trackId) {
        List<AnimationTrack.ExtensionTrack> tracks = new ArrayList<>(document.tracks());

        if (!tracks.removeIf(track -> track.id().equals(trackId))) {
            throw target("document track", trackId);
        }

        return document.withTracks(tracks);
    }

    private static CameraAnimation replaceTrack(CameraAnimation document,
                                                TrackId trackId,
                                                AnimationTrack replacement) {
        if (!(replacement instanceof AnimationTrack.ExtensionTrack extension)) {
            throw invalid("ReplaceTrack only accepts extension tracks");
        }

        if (!trackId.equals(extension.id())) {
            throw invalid("ReplaceTrack must preserve the track id: " + trackId + " != " + extension.id());
        }

        List<AnimationTrack.ExtensionTrack> tracks = new ArrayList<>(document.tracks());
        int index = indexOfTrack(tracks, trackId);

        if (index < 0) {
            throw target("document track", trackId);
        }

        tracks.set(index, extension);
        return document.withTracks(tracks);
    }

    private static CameraAnimation renameTrack(CameraAnimation document, TrackId trackId, String name) {
        AnimationTrack track = document.track(trackId)
                .orElseThrow(() -> target("track", trackId));

        return switch (track) {
            case CurveTrack curve -> replaceCurve(document, curve.withName(name));
            case QuaternionTrack ignored -> throw invalid(
                    "Quaternion tracks are not yet addressable in the legacy per-rig document model");
            case AimTrack aim -> replaceValueTrack(document, aim.withName(name));
            case ActiveTrack active -> replaceValueTrack(document, active.withName(name));
            case CameraSourceTrack source -> replaceValueTrack(document, source.withName(name));
            case AnimationTrack.ExtensionTrack extension -> {
                if (!(extension instanceof RenamableTrack renamable)) {
                    throw invalid("Track type does not support renaming: " + extension.type());
                }

                List<AnimationTrack.ExtensionTrack> tracks = new ArrayList<>(document.tracks());
                int index = indexOfTrack(tracks, trackId);

                if (index < 0) {
                    throw target("document track", trackId);
                }

                AnimationTrack renamed = Objects.requireNonNull(
                        renamable.withName(name), "withName result");

                if (!trackId.equals(renamed.id())
                        || !extension.type().equals(renamed.type())
                        || renamed.getClass() != extension.getClass()) {
                    throw invalid("RenamableTrack must preserve TrackId, type and concrete class: "
                            + trackId + " / " + extension.type());
                }

                if (!(renamed instanceof AnimationTrack.ExtensionTrack newExtension)) {
                    throw invalid("RenamableTrack must return the same extension track family");
                }

                if (name.isBlank() || !name.equals(renamed.name())) {
                    throw invalid("RenamableTrack must apply the requested non-blank name");
                }

                tracks.set(index, newExtension);
                yield document.withTracks(tracks);
            }
        };
    }

    private static CameraAnimation reorderTracks(CameraAnimation document, List<TrackId> order) {
        if (!sameIds(document.tracks().stream().map(AnimationTrack::id).toList(), order)) {
            throw invalid("ReorderTracks must contain every document track id exactly once");
        }

        Map<TrackId, AnimationTrack.ExtensionTrack> byId = new LinkedHashMap<>();
        for (AnimationTrack.ExtensionTrack track : document.tracks()) {
            byId.put(track.id(), track);
        }

        return document.withTracks(order.stream().map(byId::get).toList());
    }

    private static CameraAnimation setCameraSource(CameraAnimation document, CameraSourceTrack source) {
        requireFreeTrackId(document, source.id(), document.cameraSource().id());
        return document.withCameraSource(source);
    }

    // endregion

    // region 关键帧

    private static CameraAnimation addNumberKey(CameraAnimation document, TrackId trackId, Keyframe keyframe) {
        CurveTrack track = requireCurve(document, trackId);

        if (containsCurveKey(track, keyframe.keyId())) {
            throw invalid("A key with id " + keyframe.keyId() + " already exists in " + trackId);
        }

        List<Keyframe> keys = new ArrayList<>(track.curve().keys());
        keys.add(keyframe);
        return replaceCurve(document, track.withCurve(withKeys(track.curve(), keys)));
    }

    private static CameraAnimation addValueKey(CameraAnimation document,
                                               TrackId trackId,
                                               ValueKeyframe<?> keyframe) {
        KeyframeTrack<?> track = requireValueTrack(document, trackId);
        requireAcceptedValue(track, keyframe.value());

        if (containsValueKey(track, keyframe.keyId())) {
            throw invalid("A key with id " + keyframe.keyId() + " already exists in " + trackId);
        }

        return replaceValueTrack(document, addValueKey(track, keyframe));
    }

    private static CameraAnimation removeKey(CameraAnimation document, TrackId trackId, KeyId keyId) {
        if (document.curveTrack(trackId).isPresent()) {
            CurveTrack track = requireCurve(document, trackId);
            List<Keyframe> keys = new ArrayList<>(track.curve().keys());

            if (!keys.removeIf(key -> key.keyId().equals(keyId))) {
                throw target("key", keyId);
            }

            return replaceCurve(document, track.withCurve(withKeys(track.curve(), keys)));
        }

        KeyframeTrack<?> track = requireValueTrack(document, trackId);

        if (!containsValueKey(track, keyId)) {
            throw target("key", keyId);
        }

        return replaceValueTrack(document, removeValueKey(track, keyId));
    }

    private static CameraAnimation moveKeys(CameraAnimation document, List<EditOp.KeyMove> moves) {
        Map<TrackId, LinkedHashMap<KeyId, Float>> grouped = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (EditOp.KeyMove move : moves) {
            String identity = move.trackId() + "/" + move.keyId();

            if (!seen.add(identity)) {
                throw invalid("MoveKeys contains duplicate move for " + identity);
            }

            if (document.curveTrack(move.trackId()).isEmpty()
                    && !isValueTrack(document, move.trackId())) {
                throw target("track", move.trackId());
            }

            grouped.computeIfAbsent(move.trackId(), ignored -> new LinkedHashMap<>())
                    .put(move.keyId(), move.time());
        }

        CameraAnimation candidate = document;

        for (Map.Entry<TrackId, LinkedHashMap<KeyId, Float>> entry : grouped.entrySet()) {
            TrackId trackId = entry.getKey();
            Map<KeyId, Float> times = entry.getValue();

            if (document.curveTrack(trackId).isPresent()) {
                CurveTrack track = requireCurve(candidate, trackId);
                List<Keyframe> keys = new ArrayList<>(track.curve().keys());

                for (KeyId keyId : times.keySet()) {
                    if (indexOfCurveKey(keys, keyId) < 0) {
                        throw target("key", keyId);
                    }
                }

                for (int i = 0; i < keys.size(); i++) {
                    Keyframe key = keys.get(i);
                    Float time = times.get(key.keyId());

                    if (time != null) {
                        keys.set(i, key.withTime(time));
                    }
                }

                candidate = replaceCurve(candidate, track.withCurve(withKeys(track.curve(), keys)));
            } else {
                KeyframeTrack<?> track = requireValueTrack(candidate, trackId);

                for (KeyId keyId : times.keySet()) {
                    if (!containsValueKey(track, keyId)) {
                        throw target("key", keyId);
                    }
                }

                candidate = replaceValueTrack(candidate, moveValueKeys(track, times));
            }
        }

        return candidate;
    }

    private static CameraAnimation setKeyValue(CameraAnimation document,
                                               TrackId trackId,
                                               KeyId keyId,
                                               cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.NumberSource value) {
        CurveTrack track = requireCurve(document, trackId);
        List<Keyframe> keys = new ArrayList<>(track.curve().keys());
        int index = indexOfCurveKey(keys, keyId);

        if (index < 0) {
            throw target("key", keyId);
        }

        keys.set(index, keys.get(index).withValue(value));
        return replaceCurve(document, track.withCurve(withKeys(track.curve(), keys)));
    }

    private static CameraAnimation setStateKey(CameraAnimation document,
                                               TrackId trackId,
                                               KeyId keyId,
                                               Object value) {
        KeyframeTrack<?> track = requireValueTrack(document, trackId);
        requireAcceptedValue(track, value);

        if (!containsValueKey(track, keyId)) {
            throw target("key", keyId);
        }

        return replaceValueTrack(document, setValueKey(track, keyId, value));
    }

    private static CameraAnimation setKeyHandles(CameraAnimation document,
                                                 TrackId trackId,
                                                 KeyId keyId,
                                                 float inSlope,
                                                 float outSlope,
                                                 float inLength,
                                                 float outLength) {
        CurveTrack track = requireCurve(document, trackId);
        List<Keyframe> keys = new ArrayList<>(track.curve().keys());
        int index = indexOfCurveKey(keys, keyId);

        if (index < 0) {
            throw target("key", keyId);
        }

        Keyframe key = keys.get(index);
        keys.set(index, key.withHandles(inSlope, outSlope, inLength, outLength, key.interpolation()));
        return replaceCurve(document, track.withCurve(withKeys(track.curve(), keys)));
    }

    private static CameraAnimation setKeyInterpolation(CameraAnimation document,
                                                       TrackId trackId,
                                                       KeyId keyId,
                                                       Curve.Interpolation interpolation) {
        CurveTrack track = requireCurve(document, trackId);
        List<Keyframe> keys = new ArrayList<>(track.curve().keys());
        int index = indexOfCurveKey(keys, keyId);

        if (index < 0) {
            throw target("key", keyId);
        }

        Keyframe key = keys.get(index);
        keys.set(index, key.withHandles(
                key.inSlope(), key.outSlope(), key.inLength(), key.outLength(), interpolation));
        return replaceCurve(document, track.withCurve(withKeys(track.curve(), keys)));
    }

    private static CameraAnimation replaceKeys(CameraAnimation document,
                                               TrackId trackId,
                                               List<Keyframe> keys) {
        CurveTrack track = requireCurve(document, trackId);
        requireUniqueKeyIds(keys.stream().map(Keyframe::keyId).toList());
        return replaceCurve(document, track.withCurve(withKeys(track.curve(), keys)));
    }

    private static CameraAnimation replaceValueKeys(CameraAnimation document,
                                                    TrackId trackId,
                                                    List<ValueKeyframe<?>> keys) {
        KeyframeTrack<?> track = requireValueTrack(document, trackId);

        for (ValueKeyframe<?> key : keys) {
            requireAcceptedValue(track, key.value());
        }

        requireUniqueKeyIds(keys.stream().map(ValueKeyframe::keyId).toList());
        return replaceValueTrack(document, replaceValueKeys(track, keys));
    }

    // endregion

    // region 符号

    private static CameraAnimation addVariable(CameraAnimation document, Variable variable) {
        if (document.symbols().variableNameTaken(variable.name())) {
            throw invalid("Variable " + variable.name() + " already exists");
        }

        List<Variable> variables = new ArrayList<>(document.symbols().variables());
        variables.add(variable);
        return document.withSymbols(document.symbols().withVariables(variables));
    }

    private static CameraAnimation removeVariable(CameraAnimation document, String name) {
        List<Variable> variables = new ArrayList<>(document.symbols().variables());

        if (!variables.removeIf(variable -> variable.name().equals(name))) {
            throw target("variable", name);
        }

        return document.withSymbols(document.symbols().withVariables(variables));
    }

    private static CameraAnimation replaceVariable(CameraAnimation document, String name, Variable variable) {
        List<Variable> variables = new ArrayList<>(document.symbols().variables());
        int index = -1;

        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).name().equals(name)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw target("variable", name);
        }

        if (!name.equals(variable.name()) && document.symbols().variableNameTaken(variable.name())) {
            throw invalid("Variable " + variable.name() + " already exists");
        }

        variables.set(index, variable);
        return document.withSymbols(document.symbols().withVariables(variables));
    }

    private static CameraAnimation addFunction(CameraAnimation document, CustomFunction function) {
        if (document.symbols().functionNameTaken(function.name())) {
            throw invalid("Function " + function.name() + " already exists");
        }

        List<CustomFunction> functions = new ArrayList<>(document.symbols().functions());
        functions.add(function);
        return document.withSymbols(document.symbols().withFunctions(functions));
    }

    private static CameraAnimation removeFunction(CameraAnimation document, String name) {
        List<CustomFunction> functions = new ArrayList<>(document.symbols().functions());

        if (!functions.removeIf(function -> function.name().equals(name))) {
            throw target("function", name);
        }

        return document.withSymbols(document.symbols().withFunctions(functions));
    }

    private static CameraAnimation replaceFunction(CameraAnimation document,
                                                   String name,
                                                   CustomFunction function) {
        List<CustomFunction> functions = new ArrayList<>(document.symbols().functions());
        int index = -1;

        for (int i = 0; i < functions.size(); i++) {
            if (functions.get(i).name().equals(name)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw target("function", name);
        }

        if (!name.equals(function.name()) && document.symbols().functionNameTaken(function.name())) {
            throw invalid("Function " + function.name() + " already exists");
        }

        functions.set(index, function);
        return document.withSymbols(document.symbols().withFunctions(functions));
    }

    // endregion

    // region 路径

    private static CameraAnimation addPathNode(CameraAnimation document, RigId rigId, PathNode node) {
        CameraRig rig = requireRig(document, rigId);

        if (rig.path().byId(node.id()) != null) {
            throw invalid("Path already contains node " + node.id());
        }

        List<PathNode> nodes = new ArrayList<>(rig.path().nodes());
        nodes.add(node);
        return document.withRig(rig.withPath(rig.path().withNodes(nodes)));
    }

    private static CameraAnimation removePathNode(CameraAnimation document, RigId rigId, PathNodeId nodeId) {
        CameraRig rig = requireRig(document, rigId);
        List<PathNode> nodes = new ArrayList<>(rig.path().nodes());

        if (!nodes.removeIf(node -> node.id().equals(nodeId))) {
            throw target("path node", nodeId);
        }

        return document.withRig(rig.withPath(rig.path().withNodes(nodes)));
    }

    private static CameraAnimation replacePathNode(CameraAnimation document,
                                                   RigId rigId,
                                                   PathNodeId nodeId,
                                                   PathNode node) {
        if (!nodeId.equals(node.id())) {
            throw invalid("ReplacePathNode must preserve the node id: " + nodeId + " != " + node.id());
        }

        CameraRig rig = requireRig(document, rigId);
        List<PathNode> nodes = new ArrayList<>(rig.path().nodes());
        int index = -1;

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(nodeId)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw target("path node", nodeId);
        }

        nodes.set(index, node);
        return document.withRig(rig.withPath(rig.path().withNodes(nodes)));
    }

    private static CameraAnimation reorderPathNodes(CameraAnimation document,
                                                    RigId rigId,
                                                    List<PathNodeId> order) {
        CameraRig rig = requireRig(document, rigId);

        if (!sameIds(rig.path().nodes().stream().map(PathNode::id).toList(), order)) {
            throw invalid("ReorderPathNodes must contain every existing node id exactly once");
        }

        Map<PathNodeId, PathNode> byId = new LinkedHashMap<>();
        for (PathNode node : rig.path().nodes()) {
            byId.put(node.id(), node);
        }

        return document.withRig(rig.withPath(rig.path().withNodes(order.stream().map(byId::get).toList())));
    }

    // endregion

    // region 标记 / 采样 / 音频 / 叠加

    private static CameraAnimation addMarker(CameraAnimation document, Marker marker) {
        if (document.markers().stream().anyMatch(existing -> existing.id().equals(marker.id()))) {
            throw invalid("Marker " + marker.id() + " already exists");
        }

        List<Marker> markers = new ArrayList<>(document.markers());
        markers.add(marker);
        return document.withMarkers(markers);
    }

    private static CameraAnimation removeMarker(CameraAnimation document, MarkerId markerId) {
        List<Marker> markers = new ArrayList<>(document.markers());

        if (!markers.removeIf(marker -> marker.id().equals(markerId))) {
            throw target("marker", markerId);
        }

        return document.withMarkers(markers);
    }

    private static CameraAnimation replaceMarker(CameraAnimation document, MarkerId markerId, Marker marker) {
        if (!markerId.equals(marker.id())) {
            throw invalid("ReplaceMarker must preserve the marker id: " + markerId + " != " + marker.id());
        }

        List<Marker> markers = new ArrayList<>(document.markers());
        int index = -1;

        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).id().equals(markerId)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw target("marker", markerId);
        }

        markers.set(index, marker);
        return document.withMarkers(markers);
    }

    private static CameraAnimation addSample(CameraAnimation document, WorldSample sample) {
        if (document.samples().stream().anyMatch(existing -> existing.id().equals(sample.id()))) {
            throw invalid("Sample " + sample.id() + " already exists");
        }

        List<WorldSample> samples = new ArrayList<>(document.samples());
        samples.add(sample);
        return document.withSamples(samples);
    }

    private static CameraAnimation removeSample(CameraAnimation document, SampleId sampleId) {
        List<WorldSample> samples = new ArrayList<>(document.samples());

        if (!samples.removeIf(sample -> sample.id().equals(sampleId))) {
            throw target("sample", sampleId);
        }

        return document.withSamples(samples);
    }

    private static CameraAnimation replaceSample(CameraAnimation document, SampleId sampleId, WorldSample sample) {
        if (!sampleId.equals(sample.id())) {
            throw invalid("ReplaceSample must preserve the sample id: " + sampleId + " != " + sample.id());
        }

        List<WorldSample> samples = new ArrayList<>(document.samples());
        int index = -1;

        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i).id().equals(sampleId)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw target("sample", sampleId);
        }

        samples.set(index, sample);
        return document.withSamples(samples);
    }

    private static CameraAnimation addAudioRef(CameraAnimation document, AudioRef audioRef) {
        if (document.audioRefs().stream().anyMatch(existing -> existing.id().equals(audioRef.id()))) {
            throw invalid("AudioRef " + audioRef.id() + " already exists");
        }

        List<AudioRef> refs = new ArrayList<>(document.audioRefs());
        refs.add(audioRef);
        return document.withAudioRefs(refs);
    }

    private static CameraAnimation removeAudioRef(CameraAnimation document, AudioRefId audioRefId) {
        List<AudioRef> refs = new ArrayList<>(document.audioRefs());

        if (!refs.removeIf(ref -> ref.id().equals(audioRefId))) {
            throw target("audio ref", audioRefId);
        }

        return document.withAudioRefs(refs);
    }

    private static CameraAnimation replaceAudioRef(CameraAnimation document,
                                                   AudioRefId audioRefId,
                                                   AudioRef audioRef) {
        if (!audioRefId.equals(audioRef.id())) {
            throw invalid("ReplaceAudioRef must preserve the audio ref id");
        }

        List<AudioRef> refs = new ArrayList<>(document.audioRefs());
        int index = -1;

        for (int i = 0; i < refs.size(); i++) {
            if (refs.get(i).id().equals(audioRefId)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw target("audio ref", audioRefId);
        }

        refs.set(index, audioRef);
        return document.withAudioRefs(refs);
    }

    private static CameraAnimation addOverlay(CameraAnimation document, OverlayElement overlay) {
        if (document.overlays().stream().anyMatch(existing -> existing.id().equals(overlay.id()))) {
            throw invalid("Overlay " + overlay.id() + " already exists");
        }

        List<OverlayElement> overlays = new ArrayList<>(document.overlays());
        overlays.add(overlay);
        return document.withOverlays(overlays);
    }

    private static CameraAnimation removeOverlay(CameraAnimation document, OverlayId overlayId) {
        List<OverlayElement> overlays = new ArrayList<>(document.overlays());

        if (!overlays.removeIf(overlay -> overlay.id().equals(overlayId))) {
            throw target("overlay", overlayId);
        }

        return document.withOverlays(overlays);
    }

    private static CameraAnimation replaceOverlay(CameraAnimation document,
                                                  OverlayId overlayId,
                                                  OverlayElement overlay) {
        if (!overlayId.equals(overlay.id())) {
            throw invalid("ReplaceOverlay must preserve the overlay id");
        }

        List<OverlayElement> overlays = new ArrayList<>(document.overlays());
        int index = -1;

        for (int i = 0; i < overlays.size(); i++) {
            if (overlays.get(i).id().equals(overlayId)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw target("overlay", overlayId);
        }

        overlays.set(index, overlay);
        return document.withOverlays(overlays);
    }

    private static CameraAnimation reorderOverlays(CameraAnimation document, List<OverlayId> order) {
        if (!sameIds(document.overlays().stream().map(OverlayElement::id).toList(), order)) {
            throw invalid("ReorderOverlays must contain every existing overlay id exactly once");
        }

        Map<OverlayId, OverlayElement> byId = new LinkedHashMap<>();
        for (OverlayElement overlay : document.overlays()) {
            byId.put(overlay.id(), overlay);
        }

        return document.withOverlays(order.stream().map(byId::get).toList());
    }

    // endregion

    // region 共同工具

    private static CurveTrack requireCurve(CameraAnimation document, TrackId trackId) {
        return document.curveTrack(trackId)
                .orElseThrow(() -> target("curve track", trackId));
    }

    private static KeyframeTrack<?> requireValueTrack(CameraAnimation document, TrackId trackId) {
        for (CameraRig rig : document.rigs()) {
            if (rig.aim().id().equals(trackId)) {
                return rig.aim();
            }

            if (rig.view().active().id().equals(trackId)) {
                return rig.view().active();
            }
        }

        if (document.cameraSource().id().equals(trackId)) {
            return document.cameraSource();
        }

        throw target("state track", trackId);
    }

    private static boolean isValueTrack(CameraAnimation document, TrackId trackId) {
        return document.curveTrack(trackId).isEmpty()
                && (document.cameraSource().id().equals(trackId)
                || document.rigs().stream().anyMatch(rig ->
                rig.aim().id().equals(trackId) || rig.view().active().id().equals(trackId)));
    }

    private static CameraAnimation replaceCurve(CameraAnimation document, CurveTrack replacement) {
        for (CameraRig rig : document.rigs()) {
            List<CurveTrack> channels = new ArrayList<>(rig.channels());
            int index = indexOfTrack(channels, replacement.id());

            if (index >= 0) {
                channels.set(index, replacement);
                return document.withRig(rig.withChannels(channels));
            }
        }

        throw target("curve track", replacement.id());
    }

    private static CameraAnimation replaceValueTrack(CameraAnimation document,
                                                     KeyframeTrack<?> replacement) {
        return switch (replacement) {
            case AimTrack aim -> {
                for (CameraRig rig : document.rigs()) {
                    if (rig.aim().id().equals(aim.id())) {
                        yield document.withRig(rig.withAim(aim));
                    }
                }

                throw target("aim track", aim.id());
            }
            case ActiveTrack active -> {
                for (CameraRig rig : document.rigs()) {
                    if (rig.view().active().id().equals(active.id())) {
                        yield document.withRig(rig.withView(rig.view().withActive(active)));
                    }
                }

                throw target("active track", active.id());
            }
            case CameraSourceTrack source -> {
                if (!document.cameraSource().id().equals(source.id())) {
                    throw target("camera source track", source.id());
                }

                yield document.withCameraSource(source);
            }
        };
    }

    private static Curve withKeys(Curve curve, List<Keyframe> keys) {
        return new Curve(keys, curve.preMode(), curve.postMode());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyframeTrack<?> addValueKey(KeyframeTrack<?> track, ValueKeyframe<?> keyframe) {
        List keys = new ArrayList<>(track.keys());
        keys.add(keyframe);
        return ((KeyframeTrack) track).withKeys(keys);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyframeTrack<?> removeValueKey(KeyframeTrack<?> track, KeyId keyId) {
        List keys = new ArrayList<>(track.keys());
        keys.removeIf(key -> ((ValueKeyframe<?>) key).keyId().equals(keyId));
        return ((KeyframeTrack) track).withKeys(keys);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyframeTrack<?> moveValueKeys(KeyframeTrack<?> track, Map<KeyId, Float> times) {
        List keys = new ArrayList<>(track.keys());
        boolean found = false;

        for (int i = 0; i < keys.size(); i++) {
            ValueKeyframe key = (ValueKeyframe) keys.get(i);
            Float time = times.get(key.keyId());

            if (time != null) {
                keys.set(i, key.withTime(time));
                found = true;
            }
        }

        if (!found) {
            throw target("key", times.keySet().iterator().next());
        }

        return ((KeyframeTrack) track).withKeys(keys);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyframeTrack<?> setValueKey(KeyframeTrack<?> track, KeyId keyId, Object value) {
        List keys = new ArrayList<>(track.keys());

        for (int i = 0; i < keys.size(); i++) {
            ValueKeyframe key = (ValueKeyframe) keys.get(i);

            if (key.keyId().equals(keyId)) {
                keys.set(i, key.withValue(value));
                return ((KeyframeTrack) track).withKeys(keys);
            }
        }

        throw target("key", keyId);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static KeyframeTrack<?> replaceValueKeys(KeyframeTrack<?> track, List<ValueKeyframe<?>> keys) {
        List copy = new ArrayList<>(keys);
        return ((KeyframeTrack) track).withKeys(copy);
    }

    private static void requireAcceptedValue(KeyframeTrack<?> track, Object value) {
        boolean accepted = switch (track) {
            case AimTrack ignored -> value instanceof AimState;
            case ActiveTrack ignored -> value instanceof Boolean;
            case CameraSourceTrack ignored -> value instanceof CameraSource;
        };

        if (!accepted) {
            throw invalid("Value " + value.getClass().getSimpleName()
                    + " is not valid for track type " + track.type());
        }
    }

    private static boolean containsCurveKey(CurveTrack track, KeyId keyId) {
        return indexOfCurveKey(track.curve().keys(), keyId) >= 0;
    }

    private static int indexOfCurveKey(List<Keyframe> keys, KeyId keyId) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).keyId().equals(keyId)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean containsValueKey(KeyframeTrack<?> track, KeyId keyId) {
        for (ValueKeyframe<?> key : track.keys()) {
            if (key.keyId().equals(keyId)) {
                return true;
            }
        }

        return false;
    }

    private static int indexOfTrack(List<? extends AnimationTrack> tracks, TrackId trackId) {
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).id().equals(trackId)) {
                return i;
            }
        }

        return -1;
    }

    private static void requireFreeTrackId(CameraAnimation document, TrackId trackId) {
        if (document.track(trackId).isPresent()) {
            throw invalid("Track id " + trackId + " already exists");
        }
    }

    private static void requireFreeTrackId(CameraAnimation document, TrackId trackId, TrackId allowed) {
        if (!trackId.equals(allowed) && document.track(trackId).isPresent()) {
            throw invalid("Track id " + trackId + " already exists");
        }
    }

    private static void requireUniqueKeyIds(List<KeyId> keyIds) {
        if (new HashSet<>(keyIds).size() != keyIds.size()) {
            throw invalid("Key ids must be unique within a track");
        }
    }

    private static boolean sameIds(List<?> existing, List<?> order) {
        return existing.size() == order.size()
                && new LinkedHashSet<>(existing).equals(new LinkedHashSet<>(order));
    }

    private static EditApplyException target(String kind, Object id) {
        return new EditApplyException(DiagnosticCode.TARGET_NOT_FOUND,
                "No " + kind + " with id " + id);
    }

    private static EditApplyException invalid(String message) {
        return new EditApplyException(DiagnosticCode.INVALID_EDIT, message);
    }

    /// 应用操作时的可报告拒绝；不携带栈跟踪，避免把用户输入错误当程序崩溃传播。
    static final class EditApplyException extends RuntimeException {

        private final DiagnosticCode code;

        EditApplyException(DiagnosticCode code, String message) {
            super(message, null, false, false);
            this.code = Objects.requireNonNull(code, "code");
        }

        DiagnosticCode code() {
            return code;
        }
    }

    // endregion
}
