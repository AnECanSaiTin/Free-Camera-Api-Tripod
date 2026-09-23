package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.api.animation.path.Path;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationCodec;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationFiles;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.info.CameraInfo;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer;
import cn.anecansaitin.free_camera_api_tripod.core.editor.widget.ConfirmDialog;
import cn.anecansaitin.freecameraapi.core.ModifierManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/// 编辑器共享上下文：动画数据、播放器、编辑模型，以及时间轴/曲线图共用的视图参数。
public final class EditorContext {
    private final CameraEditorModel editor;
    private final CameraPlayer player;
    private final CameraAnimation animation;
    private final CameraInfo info;

    private float pixelsPerSecond = 80f;
    private float viewStartTime;
    private boolean snapEnabled = true;
    private float snapStep = 0.1f;
    private boolean bezierSymmetric = true;
    private float flySpeed = 12f;

    private @Nullable Component statusMessage;
    private long statusUntil;
    /// 当前的二次确认弹窗；由面板发起，屏幕负责绘制与派发点击
    private @Nullable ConfirmDialog dialog;

    public EditorContext(CameraEditorModel editor, CameraPlayer player, CameraAnimation animation, CameraInfo info) {
        this.editor = editor;
        this.player = player;
        this.animation = animation;
        this.info = info;
    }

    public CameraEditorModel editor() {
        return editor;
    }

    public CameraPlayer player() {
        return player;
    }

    public CameraAnimation animation() {
        return animation;
    }

    public CameraInfo info() {
        return info;
    }

    public float duration() {
        return animation.duration();
    }

    // region 时间轴视图

    public float pixelsPerSecond() {
        return pixelsPerSecond;
    }

    public void pixelsPerSecond(float pixelsPerSecond) {
        this.pixelsPerSecond = Mth.clamp(pixelsPerSecond, 6f, 600f);
    }

    public float viewStartTime() {
        return viewStartTime;
    }

    public void viewStartTime(float viewStartTime) {
        // 时间轴最左端就是 0 秒：拖到 0 之后不再继续往左走，避免出现负时间
        this.viewStartTime = Math.max(0f, viewStartTime);
    }

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void snapEnabled(boolean snapEnabled) {
        this.snapEnabled = snapEnabled;
    }

    public float snapStep() {
        return snapStep;
    }

    /// 贝塞尔控制点是否对称：为 true 时在曲线图里拖动一侧切线，另一侧自动跟随
    public boolean bezierSymmetric() {
        return bezierSymmetric;
    }

    public void bezierSymmetric(boolean bezierSymmetric) {
        this.bezierSymmetric = bezierSymmetric;
    }

    /// 路径距离的显示形式：false 为绝对距离（格），true 为占全程的百分比（0~1）
    public boolean distancePercent() {
        return animation.distanceMode() == CameraAnimation.DistanceMode.PERCENT;
    }

    /// 切换距离口径；动画数据里已有的位置键会一起换算
    public void distancePercent(boolean distancePercent) {
        animation.distanceMode(distancePercent
                ? CameraAnimation.DistanceMode.PERCENT
                : CameraAnimation.DistanceMode.ABSOLUTE);
    }

    /// 时间轴与曲线图共用的主刻度间隔，保证刻线间距不小于约 56 像素
    public float majorTickStep() {
        float density = pixelsPerSecond;
        float[] candidates = {0.05f, 0.1f, 0.2f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 30f, 60f};

        for (float candidate : candidates) {
            if (candidate * density >= 56f) {
                return candidate;
            }
        }

        return 120f;
    }

    public String formatTick(float time, float step) {
        return step >= 1f
                ? Math.round(time) + "s"
                : String.format(java.util.Locale.ROOT, "%.1fs", time);
    }

    public float snapTime(float time) {
        if (!snapEnabled) {
            return Math.max(0f, time);
        }

        return Math.max(0f, Math.round(time / snapStep) * snapStep);
    }

    // endregion

    public float flySpeed() {
        return flySpeed;
    }

    public void flySpeed(float flySpeed) {
        this.flySpeed = Mth.clamp(flySpeed, 1f, 200f);
    }

    // region 提示信息

    public void notify(Component message) {
        this.statusMessage = message;
        this.statusUntil = System.currentTimeMillis() + 2600;
    }

    public @Nullable Component statusMessage() {
        return System.currentTimeMillis() < statusUntil ? statusMessage : null;
    }

    // endregion

    // region 二次确认

    /// 弹出二次确认；用户点「确认」后才执行 action
    public void confirm(Component message, Runnable action) {
        this.dialog = new ConfirmDialog(message, action);
    }

    public @Nullable ConfirmDialog dialog() {
        return dialog;
    }

    public void closeDialog() {
        this.dialog = null;
    }

    // endregion

    // region 路径绑定与运动模式

    /// 选一条路径并绑定：先弹文件管理界面挑 `.path`，选中后再二次确认，确认才替换路径并切到路径模式
    public void chooseAndBindPath() {
        FileBrowserScreen.open(false, AnimationFiles.pathDir(), null, AnimationFiles.PATH_SUFFIX, this::confirmBindPath);
    }

    /// 选好文件后的二次确认；确认才真正绑定
    public void confirmBindPath(java.nio.file.Path file) {
        String json = AnimationFiles.loadPathFrom(file);
        String name = AnimationFiles.stem(file);

        if (json == null || AnimationCodec.pathFromJson(json) == null) {
            notify(EditorLang.t("notify.path_load_failed", name));
            return;
        }

        confirm(EditorLang.t("notify.delete_position_keys_confirm"), () -> bindPath(json, name));
    }

    /// 用已读出的 JSON 绑定路径：替换当前路径、切到路径模式，只重建通道、不补关键帧
    public boolean bindPath(String json, String name) {
        Path loaded = AnimationCodec.pathFromJson(json);

        if (loaded == null) {
            notify(EditorLang.t("notify.path_load_failed", name));
            return false;
        }

        loaded.name(name);
        editor.path(loaded);
        editor.pathReplaced();
        animation.motionMode(CameraAnimation.MotionMode.PATH);
        notify(EditorLang.t("notify.path_bound", name));
        return true;
    }

    /// 切到路径模式：先选路径文件，再二次确认（两步都由 {@link #chooseAndBindPath()} 完成）
    public void switchToPathMode() {
        if (animation.motionMode() == CameraAnimation.MotionMode.PATH) {
            return;
        }

        chooseAndBindPath();
    }

    /// 切到直接坐标模式：会丢掉路径距离关键帧，先二次确认。
    /// 确认后同时解除路径绑定——该模式的位置由坐标关键帧给出，留着旧路径只会让人误以为还绑着。
    public void switchToCoordinateMode() {
        if (animation.motionMode() == CameraAnimation.MotionMode.COORDINATE) {
            return;
        }

        confirm(EditorLang.t("notify.delete_position_keys_confirm"), () -> {
            animation.motionMode(CameraAnimation.MotionMode.COORDINATE);
            editor.path().clear();
            editor.pathReplaced();
        });
    }

    // endregion

    // region 录路径点

    /// 记录一个路径点：把相机当前位置加入路径、在位置通道上补一个关键帧（时间排在上一个键之后 4 秒），
    /// 并把播放头挪到新键上，方便立刻看到刚录的点。
    public void recordPathNode() {
        float keyTime = editor.addPathNodeAt(currentCameraPosition(), player.time());
        player.seek(keyTime);
        notify(EditorLang.t("notify.path_node_added"));
    }

    // endregion

    // region 当前相机

    public Vector3f currentCameraPosition() {
        return new Vector3f(ModifierManager.INSTANCE.pos());
    }

    public Vector3f currentCameraRotation() {
        return new Vector3f(ModifierManager.INSTANCE.rot());
    }

    public float currentCameraFov() {
        return ModifierManager.INSTANCE.fov();
    }

    /// 把自由视角同步到当前相机，便于切到自由视角时不跳变
    public void syncFreePoseFromCamera() {
        editor.syncFreePose(currentCameraPosition(), currentCameraRotation(), currentCameraFov());
    }

    // endregion
}
