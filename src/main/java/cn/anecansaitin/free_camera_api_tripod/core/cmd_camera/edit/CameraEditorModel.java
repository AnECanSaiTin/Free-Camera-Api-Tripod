package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe;
import cn.anecansaitin.free_camera_api_tripod.api.animation.TrackKey;
import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.animation.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.PathNode;
import cn.anecansaitin.free_camera_api_tripod.core.animation.track.CurveTrack;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CameraPose;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.PathRender;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 编辑器模型：编辑状态与编辑操作，不参与求值。
///
/// 与播放器 {@link cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer}
/// 共用同一个 {@link CameraAnimation}。
@NullMarked
public class CameraEditorModel {
    /// 视口视角模式
    public enum ViewMode {
        /// 显示播放头所在的动画帧
        PREVIEW,
        /// 自由飞行，用于取景与捕获关键帧
        FREE
    }

    private final CameraAnimation animation;
    private boolean open;
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

    public boolean open() {
        return open;
    }

    public void open(boolean open) {
        this.open = open;
    }

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
        int index = track.addKey(time);

        if (index >= 0) {
            selectTrack(track.id());
            selectKey(index);
        }

        return index;
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

    /// 在指定时间把当前相机位置记录为新的路径点。
    ///
    /// 位置通道的取值是沿路径的弧长，因此同时在该时间插入一个取值等于新路径总长的键，
    /// 让相机随时间沿路径前进。
    public void addPathNodeAt(Vector3fc position, float time) {
        path().node(PathNode.catmullRom(new Vector3f(position)));
        selectPathNode(new Selected(path().size() - 1, Selected.Type.NODE));
        CurveTrack positionTrack = animation.track(CameraAnimation.CHANNEL_POSITION);

        if (positionTrack != null) {
            float distance = (float) path().totalLength();
            int index = positionTrack.curve().key(Keyframe.create(time, distance));
            selectTrack(positionTrack.id());
            selectKey(index);
        }

        PathRender.markDirty();
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
        if (track == null) {
            return;
        }

        int index = track.curve().key(Keyframe.create(time, value));
        selectTrack(track.id());
        selectKey(index);
    }

    // endregion
}
