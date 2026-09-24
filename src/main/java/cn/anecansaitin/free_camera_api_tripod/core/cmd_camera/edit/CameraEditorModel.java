package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.curve.Curve;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.PathNode;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CameraPose;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.PathRender;
import cn.anecansaitin.freecameraapi.core.ModifierManager;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 编辑器模型：编辑状态与编辑操作，不参与求值。
///
/// 与播放器 {@link cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer}
/// 共用同一个 {@link CameraAnimation}。
@NullMarked
public class CameraEditorModel {
    /// 录路径点时，新关键帧与上一个关键帧的默认时间间隔（秒）

    /// 视口视角模式
    public enum ViewMode {
        /// 显示播放头所在的动画帧
        PREVIEW,
        /// 自由飞行，用于取景与捕获关键帧
        FREE
    }

    private final CameraAnimation animation;
    private ViewMode viewMode = ViewMode.PREVIEW;

    private CameraPose freePose = new CameraPose();

    private @Nullable String selectedTrackId;
    private int selectedKeyIndex = -1;
    private Selected selectedPathNode = new Selected(0, Selected.Type.NODE);

    public CameraEditorModel(CameraAnimation animation) {
        this.animation = animation;
    }

    public CameraAnimation animation() {
        return animation;
    }

    public Path path() {
        return animation.path();
    }

    public void path(Path path) {
        animation.path(path);
    }

    // region 视图状态

    public ViewMode viewMode() {
        return viewMode;
    }

    public void viewMode(ViewMode viewMode) {
        this.viewMode = viewMode;
    }

    public CameraPose freePose() {
        return freePose;
    }

    public void freePose(CameraPose pose) {
        this.freePose = pose;
    }

    public void syncFreePose(Vector3fc position, Vector3fc rotation, float fov) {
        freePose.set(position, rotation, fov);
    }

    /// 旋转自由视角（度）
    public void rotateView(float deltaYaw, float deltaPitch) {
        Vector3f rot = freePose.rotation();
        rot.y = Mth.wrapDegrees(rot.y + deltaYaw);
        rot.x = Mth.clamp(rot.x + deltaPitch, -89.9f, 89.9f);
    }

    /// 沿视线前后推拉
    public void dolly(float amount) {
        viewDirection(viewScratch);
        freePose.position().add(viewScratch.mul(amount));
    }

    /// 自由飞行移动：forward 为视线方向，strafe 为右方向，vertical 为世界竖直方向
    public void moveView(float forward, float strafe, float vertical, float speed, float deltaSeconds) {
        viewDirection(viewScratch);
        float fx = viewScratch.x;
        float fy = viewScratch.y;
        float fz = viewScratch.z;

        // 右方向 = normalize(-fz, 0, fx)
        float mx = fx * forward - fz * strafe;
        float my = fy * forward + vertical;
        float mz = fz * forward + fx * strafe;
        float length = Mth.sqrt(mx * mx + my * my + mz * mz);

        if (length < 1.0E-6f) {
            return;
        }

        float scale = speed * deltaSeconds / length;
        freePose.position().add(mx * scale, my * scale, mz * scale);
    }

    private final Vector3f viewScratch = new Vector3f();

    /// 由 YXZ 旋转得到视线方向（与 {@code ClientUtil.playerView} 保持一致）
    public Vector3f viewDirection(Vector3f dest) {
        Vector3fc rot = freePose.rotation();
        float pitch = rot.x() * Mth.DEG_TO_RAD;
        float yaw = -rot.y() * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitch);
        return dest.set(Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch).normalize();
    }

    // endregion

    // region 选择

    public @Nullable String selectedTrackId() {
        return selectedTrackId;
    }

    public void selectTrack(@Nullable String trackId) {
        this.selectedTrackId = trackId;
        this.selectedKeyIndex = -1;
    }

    public @Nullable AnimationTrack selectedTrack() {
        return selectedTrackId == null ? null : animation.trackById(selectedTrackId);
    }

    public int selectedKeyIndex() {
        return selectedKeyIndex;
    }

    public void selectKey(int index) {
        this.selectedKeyIndex = index;
    }

    public @Nullable TrackKey selectedKey() {
        AnimationTrack track = selectedTrack();

        if (track == null || selectedKeyIndex < 0 || selectedKeyIndex >= track.keyCount()) {
            return null;
        }

        return track.key(selectedKeyIndex);
    }

    public Selected selectedPathNode() {
        return selectedPathNode;
    }

    public boolean selectPathNode(Selected selected) {
        if (selected.index() < 0 || selected.index() >= path().size()) {
            return false;
        }

        this.selectedPathNode = selected;
        return true;
    }

    // endregion

    // region 关键帧编辑

    public int addKey(AnimationTrack track, float time) {
        // 直接坐标模式下位置三轴的默认值取相机当前坐标，避免新建的键落在 0 上
        int axis = positionAxis(track.id());

        if (axis >= 0 && track instanceof CurveTrack curveTrack) {
            int index = curveTrack.addKey(time, cameraAxis(axis));

            if (index >= 0) {
                selectTrack(track.id());
                selectKey(index);
            }

            return index;
        }

        int index = track.addKey(time);

        if (index >= 0) {
            selectTrack(track.id());
            selectKey(index);
        }

        return index;
    }

    /// 位置三轴的下标（0/1/2），不是位置轴时返回 -1
    private static int positionAxis(String trackId) {
        return switch (trackId) {
            case CameraAnimation.CHANNEL_POSITION_X -> 0;
            case CameraAnimation.CHANNEL_POSITION_Y -> 1;
            case CameraAnimation.CHANNEL_POSITION_Z -> 2;
            default -> -1;
        };
    }

    /// 相机当前坐标在指定轴上的分量
    private static float cameraAxis(int axis) {
        Vector3fc camera = ModifierManager.INSTANCE.pos();
        return axis == 0 ? camera.x() : axis == 1 ? camera.y() : camera.z();
    }

    public boolean removeKey(AnimationTrack track, int index) {
        if (!track.removeKey(index)) {
            return false;
        }

        if (track.id().equals(selectedTrackId)) {
            selectedKeyIndex = track.keyCount() == 0 ? -1 : Mth.clamp(index, 0, track.keyCount() - 1);
        }

        return true;
    }

    public int moveKey(AnimationTrack track, int index, float newTime) {
        int moved = track.moveKey(index, newTime);

        if (moved >= 0 && track.id().equals(selectedTrackId)) {
            selectedKeyIndex = moved;
        }

        return moved;
    }

    /// 删除当前选中的关键帧
    public boolean removeSelectedKey() {
        AnimationTrack track = selectedTrack();

        if (track == null || selectedKeyIndex < 0) {
            return false;
        }

        return removeKey(track, selectedKeyIndex);
    }

    /// 时间轴多选用的关键帧引用：轨道 + 该轨道内的关键帧下标
    public record KeyRef(AnimationTrack track, int index) {
    }

    /// 批量删除关键帧。
    ///
    /// 逐条轨道把下标从大到小删除，避免删除后下标位移；返回实际删除的数量。
    public int removeKeys(List<KeyRef> keys) {
        Map<AnimationTrack, List<Integer>> byTrack = new LinkedHashMap<>();

        for (KeyRef key : keys) {
            byTrack.computeIfAbsent(key.track(), track -> new ArrayList<>()).add(key.index());
        }

        int removed = 0;

        for (Map.Entry<AnimationTrack, List<Integer>> entry : byTrack.entrySet()) {
            List<Integer> indices = entry.getValue();
            indices.sort(Comparator.reverseOrder());

            for (int index : indices) {
                if (entry.getKey().removeKey(index)) {
                    removed++;
                }
            }
        }

        return removed;
    }

    /// 把多个关键帧整体平移到给定时间（框选后的整体拖动）。
    ///
    /// newTimes 与 keys 一一对应，是每个键的目标绝对时间。逐条轨道先取出这些键的快照，
    /// 再按下标从大到小移除，最后按新时间重新插入，从而避免逐个移动时新旧时间相互碰撞。
    /// 没有底层曲线的轨道不参与平移。
    /// 返回与 keys 同序的新引用；无法平移的位置下标为 -1。
    public List<KeyRef> moveKeys(List<KeyRef> keys, List<Float> newTimes) {
        KeyRef[] result = new KeyRef[keys.size()];
        Map<AnimationTrack, List<Integer>> byTrack = new LinkedHashMap<>();

        for (int i = 0; i < keys.size(); i++) {
            byTrack.computeIfAbsent(keys.get(i).track(), track -> new ArrayList<>()).add(i);
        }

        for (Map.Entry<AnimationTrack, List<Integer>> entry : byTrack.entrySet()) {
            AnimationTrack track = entry.getKey();
            List<Integer> positions = entry.getValue();

            if (!(track instanceof CurveTrack curveTrack)) {
                for (int position : positions) {
                    result[position] = new KeyRef(track, -1);
                }

                continue;
            }

            Curve curve = curveTrack.curve();
            // 快照：键对象、目标时间，以及它们在传入列表中的位置，三者一一对应
            List<Keyframe> snapshot = new ArrayList<>(positions.size());
            List<Float> targets = new ArrayList<>(positions.size());
            List<Integer> slots = new ArrayList<>(positions.size());

            for (int position : positions) {
                Keyframe keyframe = curve.key(keys.get(position).index());

                if (keyframe == null) {
                    result[position] = new KeyRef(track, -1);
                    continue;
                }

                snapshot.add(keyframe);
                targets.add(newTimes.get(position));
                slots.add(position);
            }

            // 先按下标从大到小移除，避免删除后下标位移
            List<Integer> indices = new ArrayList<>(positions);
            indices.sort(Comparator.reverseOrder());

            for (int index : indices) {
                curve.removeKey(index);
            }

            // 再按新时间重新插入
            for (int i = 0; i < snapshot.size(); i++) {
                Keyframe keyframe = snapshot.get(i);
                keyframe.time(Math.max(0f, targets.get(i)));
                result[slots.get(i)] = new KeyRef(track, curve.key(keyframe));
            }
        }

        return List.of(result);
    }

    // endregion

    // region 路径编辑

    public void addPathNode(PathNode node) {
        path().node(node);
        selectPathNode(new Selected(path().size() - 1, Selected.Type.NODE));
        PathRender.markDirty();
    }

    public void insertPathNode(int index, PathNode node) {
        path().insertNode(index, node);
        selectPathNode(new Selected(index, Selected.Type.NODE));
        PathRender.markDirty();
    }

    public boolean removePathNode(int index) {
        if (!path().removeNode(index)) {
            return false;
        }

        if (selectedPathNode.index() >= path().size()) {
            selectPathNode(new Selected(Math.max(0, path().size() - 1), Selected.Type.NODE));
        }

        PathRender.markDirty();
        return true;
    }

    /// 调整路径点顺序：与相邻节点交换位置（offset 为 ±1），选中项跟着节点一起走
    public boolean movePathNode(int index, int offset) {
        int target = index + offset;

        if (!path().moveNode(index, target)) {
            return false;
        }

        selectPathNode(new Selected(target, Selected.Type.NODE));
        PathRender.markDirty();
        return true;
    }

    public boolean updatePathNode(int index, Path.NodeUpdater updater) {
        if (!path().updateNode(index, updater)) {
            return false;
        }

        PathRender.markDirty();
        return true;
    }

    /// 路径整体被替换（例如命令创建新路径）
    public void pathReplaced() {
        PathRender.markDirty();
    }

    /// 把当前相机位置记录为新的路径点，并把选中项切到新点上。
    ///
    /// 只动路径本身，不再顺手往位置通道补关键帧：路径在时间上什么时候走到哪一段，
    /// 由用户在时间轴上自己排
    public void addPathNodeAt(Vector3fc position) {
        // 相机位置异常时不记录：NaN 一旦写进路径点，整条路径的弧长与采样都会失效
        if (!isFinite(position)) {
            return;
        }

        path().node(PathNode.catmullRom(new Vector3f(position)));
        selectPathNode(new Selected(path().size() - 1, Selected.Type.NODE));
        PathRender.markDirty();
    }

    /// 在指定时间把当前相机位置记录到三个坐标通道（直接坐标模式下的取点方式）。
    ///
    /// 与路径模式记录路径点对应：那边记的是路径节点，这边记的是位置的三个分量。
    public void applyRecordedPosition(Vector3fc position, float time) {
        capture(animation.track(CameraAnimation.CHANNEL_POSITION_X), time, position.x());
        capture(animation.track(CameraAnimation.CHANNEL_POSITION_Y), time, position.y());
        capture(animation.track(CameraAnimation.CHANNEL_POSITION_Z), time, position.z());
    }

    /// 把当前相机旋转记录到播放头处
    public void captureRotation(float time, Vector3fc rotation) {
        capture(animation.track(CameraAnimation.CHANNEL_ROTATION_X), time, rotation.x());
        capture(animation.track(CameraAnimation.CHANNEL_ROTATION_Y), time, rotation.y());
        CurveTrack z = animation.track(CameraAnimation.CHANNEL_ROTATION_Z);
        capture(z, time, rotation.z());

        if (z != null) {
            selectTrack(z.id());
        }
    }

    /// 把当前相机视场角记录到播放头处
    public void captureFov(float time, float fov) {
        CurveTrack track = animation.track(CameraAnimation.CHANNEL_FOV);
        capture(track, time, fov);

        if (track != null) {
            selectTrack(track.id());
        }
    }

    private void capture(@Nullable CurveTrack track, float time, float value) {
        // 关键帧的值与时间都必须是有限值，否则求值时会把 NaN 传下去
        if (track == null || !Float.isFinite(value) || !Float.isFinite(time)) {
            return;
        }

        int index = track.curve().key(Keyframe.create(time, value));
        selectTrack(track.id());
        selectKey(index);
    }

    private static boolean isFinite(Vector3fc vec) {
        return Float.isFinite(vec.x()) && Float.isFinite(vec.y()) && Float.isFinite(vec.z());
    }

    // endregion
}
