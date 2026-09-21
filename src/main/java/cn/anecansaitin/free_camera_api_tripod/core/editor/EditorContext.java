package cn.anecansaitin.free_camera_api_tripod.core.editor;

import cn.anecansaitin.free_camera_api_tripod.core.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.info.CameraInfo;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer;
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
    private float viewStartTime = -0.25f;
    private boolean snapEnabled = true;
    private float snapStep = 0.1f;
    private float flySpeed = 12f;
    private boolean worldView;

    private @Nullable Component statusMessage;
    private long statusUntil;

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
        this.viewStartTime = viewStartTime;
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

    // region 世界内查看

    /// 世界内查看：收起编辑界面、让世界按原始尺寸铺满屏幕，相机与操作仍由编辑器接管
    public boolean worldView() {
        return worldView;
    }

    public void worldView(boolean worldView) {
        this.worldView = worldView;
    }

    // endregion

    // region 提示信息

    public void notify(Component message) {
        this.statusMessage = message;
        this.statusUntil = System.currentTimeMillis() + 2600;
    }

    public @Nullable Component statusMessage() {
        return System.currentTimeMillis() < statusUntil ? statusMessage : null;
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
