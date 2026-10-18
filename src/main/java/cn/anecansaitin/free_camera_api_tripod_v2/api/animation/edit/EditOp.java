package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AimTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.AudioRef;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSourceTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.DistanceMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.Marker;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.MotionMode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.OverlayElement;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.ValueKeyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.WorldSample;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.CustomFunction;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Keyframe;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.NumberSource;
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
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.ObstacleSettings;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;

/// 纯编辑操作全集：机位、布局、模式、空间、轨道、关键帧、符号、路径、标记、采样、音频引用、
/// 叠加元素与整份替换（主设计 §4.1）。
///
/// ## 约束
///
/// - 操作**不得修改输入文档**，也不执行 IO；应用操作返回新候选。
/// - 数值与有类型状态轨道共用关键帧受控入口；值修改由轨道能力分派，跨轨粘贴须值类型与语义兼容，
///   否则整个候选原子拒绝。
/// - 复合修改用 [Batch] 构造一个完整候选，一次校验、一次发布，不得循环提交单对象操作。
/// - 操作只描述“意图”，不携带校验结果；成功 / 拒绝由 [EditOutcome] 与 [EditResult] 表达。
///
/// 取值来源（`NumberSource` / `ValueKeyframe`）不接受 `TrackRef`：轨道读数在变量层，
/// 不进入关键帧字段（主设计 §6.1）。
@NullMarked
public sealed interface EditOp {

    // region 文档

    /// 改文档显示名。
    record RenameDocument(String name) implements EditOp {
        public RenameDocument {
            if (name.isBlank()) {
                throw new IllegalArgumentException("RenameDocument name must not be blank");
            }
        }
    }

    /// 整份替换（第三方 / 数据导入等场景）；仍会经过统一校验与一次发布。
    record ReplaceDocument(CameraAnimation document) implements EditOp {
        public ReplaceDocument {
            Objects.requireNonNull(document, "document");
        }
    }

    // endregion

    // region 机位

    record AddRig(CameraRig rig) implements EditOp {
        public AddRig {
            Objects.requireNonNull(rig, "rig");
        }
    }

    record RemoveRig(RigId rigId) implements EditOp {
        public RemoveRig {
            Objects.requireNonNull(rigId, "rigId");
        }
    }

    record ReplaceRig(RigId rigId, CameraRig rig) implements EditOp {
        public ReplaceRig {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(rig, "rig");
        }
    }

    /// 重排机位顺序；列表必须恰好包含全部机位 ID（由 L2 校验负责）。
    record ReorderRigs(List<RigId> order) implements EditOp {
        public ReorderRigs {
            order = List.copyOf(order);
        }
    }

    record SetRigName(RigId rigId, String name) implements EditOp {
        public SetRigName {
            Objects.requireNonNull(rigId, "rigId");
            if (name.isBlank()) {
                throw new IllegalArgumentException("SetRigName name must not be blank");
            }
        }
    }

    record SetRigSpace(RigId rigId, Space space) implements EditOp {
        public SetRigSpace {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(space, "space");
        }
    }

    record SetRigMotionMode(RigId rigId, MotionMode mode) implements EditOp {
        public SetRigMotionMode {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(mode, "mode");
        }
    }

    record SetRigDistanceMode(RigId rigId, DistanceMode mode) implements EditOp {
        public SetRigDistanceMode {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(mode, "mode");
        }
    }

    record SetRigPath(RigId rigId, Path path) implements EditOp {
        public SetRigPath {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(path, "path");
        }
    }

    record SetRigAim(RigId rigId, AimTrack aim) implements EditOp {
        public SetRigAim {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(aim, "aim");
        }
    }

    record SetRigObstacle(RigId rigId, ObstacleSettings obstacle) implements EditOp {
        public SetRigObstacle {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(obstacle, "obstacle");
        }
    }

    record SetRigView(RigId rigId, RigView view) implements EditOp {
        public SetRigView {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(view, "view");
        }
    }

    record SetRigEnabled(RigId rigId, boolean enabled) implements EditOp {
        public SetRigEnabled {
            Objects.requireNonNull(rigId, "rigId");
        }
    }

    record SetRigRect(RigId rigId, RigView.Rect rect) implements EditOp {
        public SetRigRect {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(rect, "rect");
        }
    }

    record SetRigZOrder(RigId rigId, int zOrder) implements EditOp {
        public SetRigZOrder {
            Objects.requireNonNull(rigId, "rigId");
        }
    }

    record SetRigQuality(RigId rigId, double resolutionScale, int updateEveryFrames) implements EditOp {
        public SetRigQuality {
            Objects.requireNonNull(rigId, "rigId");
        }
    }

    // endregion

    // region 相机通道

    record AddChannel(RigId rigId, CurveTrack channel) implements EditOp {
        public AddChannel {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(channel, "channel");
        }
    }

    record RemoveChannel(RigId rigId, TrackId trackId) implements EditOp {
        public RemoveChannel {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    record ReplaceChannel(RigId rigId, CurveTrack channel) implements EditOp {
        public ReplaceChannel {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(channel, "channel");
        }
    }

    // endregion

    // region 文档级轨道

    record AddTrack(AnimationTrack.ExtensionTrack track) implements EditOp {
        public AddTrack {
            Objects.requireNonNull(track, "track");
        }
    }

    record RemoveTrack(TrackId trackId) implements EditOp {
        public RemoveTrack {
            Objects.requireNonNull(trackId, "trackId");
        }
    }

    /// 第三方专属轨道替换（主设计 §4.1）；替换值仍须通过注册表校验。
    record ReplaceTrack(TrackId trackId, AnimationTrack replacement) implements EditOp {
        public ReplaceTrack {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(replacement, "replacement");
        }
    }

    record RenameTrack(TrackId trackId, String name) implements EditOp {
        public RenameTrack {
            Objects.requireNonNull(trackId, "trackId");
            if (name.isBlank()) {
                throw new IllegalArgumentException("RenameTrack name must not be blank");
            }
        }
    }

    record ReorderTracks(List<TrackId> order) implements EditOp {
        public ReorderTracks {
            order = List.copyOf(order);
        }
    }

    record SetCameraSource(CameraSourceTrack cameraSource) implements EditOp {
        public SetCameraSource {
            Objects.requireNonNull(cameraSource, "cameraSource");
        }
    }

    // endregion

    // region 关键帧

    /// 数值曲线插键。
    record AddNumberKey(TrackId trackId, Keyframe keyframe) implements EditOp {
        public AddNumberKey {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyframe, "keyframe");
        }
    }

    /// 有类型状态轨道插键；值类型必须与轨道匹配，由轨道能力校验。
    record AddValueKey(TrackId trackId, ValueKeyframe<?> keyframe) implements EditOp {
        public AddValueKey {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyframe, "keyframe");
        }
    }

    record RemoveKey(TrackId trackId, KeyId keyId) implements EditOp {
        public RemoveKey {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyId, "keyId");
        }
    }

    /// 批量移动；先计算全部最终位置再检查冲突，避免顺序影响结果（主设计 §4.4）。
    record MoveKeys(List<KeyMove> moves) implements EditOp {
        public MoveKeys {
            moves = List.copyOf(moves);
        }
    }

    record SetKeyValue(TrackId trackId, KeyId keyId, NumberSource value) implements EditOp {
        public SetKeyValue {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyId, "keyId");
            Objects.requireNonNull(value, "value");
        }
    }

    record SetKeyHandles(TrackId trackId, KeyId keyId,
                         float inSlope, float outSlope, float inLength, float outLength) implements EditOp {
        public SetKeyHandles {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyId, "keyId");
        }
    }

    record SetKeyInterpolation(TrackId trackId, KeyId keyId, Curve.Interpolation interpolation) implements EditOp {
        public SetKeyInterpolation {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyId, "keyId");
            Objects.requireNonNull(interpolation, "interpolation");
        }
    }

    /// 整体替换数值曲线的键；冲突仍整体拒绝。
    record ReplaceKeys(TrackId trackId, List<Keyframe> keys) implements EditOp {
        public ReplaceKeys {
            Objects.requireNonNull(trackId, "trackId");
            keys = List.copyOf(keys);
        }
    }

    /// 整体替换状态轨道的键；值类型由轨道能力校验。
    record ReplaceValueKeys(TrackId trackId, List<ValueKeyframe<?>> keys) implements EditOp {
        public ReplaceValueKeys {
            Objects.requireNonNull(trackId, "trackId");
            keys = List.copyOf(keys);
        }
    }

    /// 单键移动：键身份不变，只改时间。
    record KeyMove(TrackId trackId, KeyId keyId, float time) {
        public KeyMove {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(keyId, "keyId");

            if (!Float.isFinite(time) || time < 0f) {
                throw new IllegalArgumentException("KeyMove time must be finite and non-negative: " + time);
            }

            if (time == 0f) {
                time = 0f;
            }
        }
    }

    // endregion

    // region 符号表

    record AddVariable(Variable variable) implements EditOp {
        public AddVariable {
            Objects.requireNonNull(variable, "variable");
        }
    }

    record RemoveVariable(String name) implements EditOp {
        public RemoveVariable {
            if (name.isBlank()) {
                throw new IllegalArgumentException("RemoveVariable name must not be blank");
            }
        }
    }

    record ReplaceVariable(String name, Variable variable) implements EditOp {
        public ReplaceVariable {
            if (name.isBlank()) {
                throw new IllegalArgumentException("ReplaceVariable name must not be blank");
            }

            Objects.requireNonNull(variable, "variable");
        }
    }

    record AddFunction(CustomFunction function) implements EditOp {
        public AddFunction {
            Objects.requireNonNull(function, "function");
        }
    }

    record RemoveFunction(String name) implements EditOp {
        public RemoveFunction {
            if (name.isBlank()) {
                throw new IllegalArgumentException("RemoveFunction name must not be blank");
            }
        }
    }

    record ReplaceFunction(String name, CustomFunction function) implements EditOp {
        public ReplaceFunction {
            if (name.isBlank()) {
                throw new IllegalArgumentException("ReplaceFunction name must not be blank");
            }

            Objects.requireNonNull(function, "function");
        }
    }

    // endregion

    // region 路径

    record AddPathNode(RigId rigId, PathNode node) implements EditOp {
        public AddPathNode {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(node, "node");
        }
    }

    record RemovePathNode(RigId rigId, PathNodeId nodeId) implements EditOp {
        public RemovePathNode {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(nodeId, "nodeId");
        }
    }

    record ReplacePathNode(RigId rigId, PathNodeId nodeId, PathNode node) implements EditOp {
        public ReplacePathNode {
            Objects.requireNonNull(rigId, "rigId");
            Objects.requireNonNull(nodeId, "nodeId");
            Objects.requireNonNull(node, "node");
        }
    }

    record ReorderPathNodes(RigId rigId, List<PathNodeId> order) implements EditOp {
        public ReorderPathNodes {
            Objects.requireNonNull(rigId, "rigId");
            order = List.copyOf(order);
        }
    }

    // endregion

    // region 标记 / 采样 / 音频 / 叠加

    record AddMarker(Marker marker) implements EditOp {
        public AddMarker {
            Objects.requireNonNull(marker, "marker");
        }
    }

    record RemoveMarker(MarkerId markerId) implements EditOp {
        public RemoveMarker {
            Objects.requireNonNull(markerId, "markerId");
        }
    }

    record ReplaceMarker(MarkerId markerId, Marker marker) implements EditOp {
        public ReplaceMarker {
            Objects.requireNonNull(markerId, "markerId");
            Objects.requireNonNull(marker, "marker");
        }
    }

    record AddSample(WorldSample sample) implements EditOp {
        public AddSample {
            Objects.requireNonNull(sample, "sample");
        }
    }

    record RemoveSample(SampleId sampleId) implements EditOp {
        public RemoveSample {
            Objects.requireNonNull(sampleId, "sampleId");
        }
    }

    record ReplaceSample(SampleId sampleId, WorldSample sample) implements EditOp {
        public ReplaceSample {
            Objects.requireNonNull(sampleId, "sampleId");
            Objects.requireNonNull(sample, "sample");
        }
    }

    record AddAudioRef(AudioRef audioRef) implements EditOp {
        public AddAudioRef {
            Objects.requireNonNull(audioRef, "audioRef");
        }
    }

    record RemoveAudioRef(AudioRefId audioRefId) implements EditOp {
        public RemoveAudioRef {
            Objects.requireNonNull(audioRefId, "audioRefId");
        }
    }

    record ReplaceAudioRef(AudioRefId audioRefId, AudioRef audioRef) implements EditOp {
        public ReplaceAudioRef {
            Objects.requireNonNull(audioRefId, "audioRefId");
            Objects.requireNonNull(audioRef, "audioRef");
        }
    }

    record AddOverlay(OverlayElement overlay) implements EditOp {
        public AddOverlay {
            Objects.requireNonNull(overlay, "overlay");
        }
    }

    record RemoveOverlay(OverlayId overlayId) implements EditOp {
        public RemoveOverlay {
            Objects.requireNonNull(overlayId, "overlayId");
        }
    }

    record ReplaceOverlay(OverlayId overlayId, OverlayElement overlay) implements EditOp {
        public ReplaceOverlay {
            Objects.requireNonNull(overlayId, "overlayId");
            Objects.requireNonNull(overlay, "overlay");
        }
    }

    record ReorderOverlays(List<OverlayId> order) implements EditOp {
        public ReorderOverlays {
            order = List.copyOf(order);
        }
    }

    // endregion

    // region 复合

    /// 一个原子复合操作：按列表顺序应用到一个候选，最后统一校验与发布。
    record Batch(List<EditOp> operations) implements EditOp {
        public Batch {
            operations = List.copyOf(operations);
        }
    }

    // endregion
}
