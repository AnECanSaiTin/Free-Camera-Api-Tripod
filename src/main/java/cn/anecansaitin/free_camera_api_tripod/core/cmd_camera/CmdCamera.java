package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera;

import cn.anecansaitin.free_camera_api_tripod.FreeCameraApiTripod;
import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.edit.CameraEditorModel;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.info.CameraInfo;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer;
import cn.anecansaitin.free_camera_api_tripod.core.editor.CameraScreens;
import cn.anecansaitin.freecameraapi.api.CameraModifier;
import cn.anecansaitin.freecameraapi.api.CameraPlugin;
import cn.anecansaitin.freecameraapi.api.Plugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/// 相机命令插件。
///
/// 只做组装与每帧调度：动画数据由 {@link CameraAnimation} 持有，
/// 播放交给 {@link CameraPlayer}，编辑状态交给 {@link CameraEditorModel}，
/// 展示文本交给 {@link CameraInfo}。
@NullMarked
@Plugin(value = "cmd_camera", modid = FreeCameraApiTripod.MODID)
public class CmdCamera implements CameraPlugin {
    public static CmdCamera INSTANCE;

    private final CameraModifier modifier;
    private final CameraAnimation animation = new CameraAnimation("Camera");
    private final CameraPlayer player = new CameraPlayer(animation);
    private final CameraEditorModel editor = new CameraEditorModel(animation);
    private final CameraInfo info = new CameraInfo(animation, player);
    private final CameraPose pose = new CameraPose();
    private long lastNanos;

    public CmdCamera(CameraModifier modifier) {
        INSTANCE = this;
        this.modifier = modifier
                .enableFov()
                .enablePos()
                .enableGlobalMode()
                .enableRotation();
    }

    @Override
    public void update(float partialTicks) {
        float deltaSeconds = deltaSeconds();

        if (player.playing()) {
            player.tick(deltaSeconds);
        }

        // 主编辑器、路径编辑、世界内查看都属于相机编辑界面，期间相机完全由编辑器驱动。
        // 漏掉其中任何一个，自由视角就只能改数据、看不到画面变化。
        boolean editing = CameraScreens.editing();

        // 编辑器处于自由视角时不跟随时间轴
        if (editing && editor.viewMode() == CameraEditorModel.ViewMode.FREE) {
            pose.set(editor.freePose());
            apply();
            return;
        }

        // 编辑器打开时始终驱动相机，以便在视口中预览播放头所在帧
        if (editing || player.state() != CameraPlayer.State.STOPPED) {
            // 先把位置对齐到玩家相机：直接坐标模式下没有关键帧的轴会沿用这份值，等价于不修改该轴
            syncPosePositionFromPlayer();
            player.evaluatePose(pose);
            apply();
            return;
        }

        modifier.disable();
    }

    /// 把待求值姿态的位置分量预先设为玩家眼睛位置，供「没有关键帧的轴」沿用
    private void syncPosePositionFromPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        Vec3 eye = player.getEyePosition();
        pose.position().set((float) eye.x, (float) eye.y, (float) eye.z);
    }

    private void apply() {
        modifier.enable();

        if (pose.positionValid()) {
            modifier.enablePos().setPos(pose.position());
        } else {
            // 路径为空时没有位置信息，禁用位置分量以保持相机原本的位置
            modifier.disablePos();
        }

        modifier.setRotationYXZ(pose.rotation());

        if (pose.fovValid()) {
            modifier.enableFov().setFov(pose.fov());
        } else {
            // 没有 FOV 关键帧时沿用玩家原本的摄像机 FOV
            modifier.disableFov();
        }
    }

    /// 帧间隔（秒）；首次调用返回 0
    private float deltaSeconds() {
        long now = System.nanoTime();

        if (lastNanos == 0) {
            lastNanos = now;
            return 0f;
        }

        float delta = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        return Mth.clamp(delta, 0f, 0.25f);
    }

    public CameraAnimation animation() {
        return animation;
    }

    public CameraPlayer player() {
        return player;
    }

    public CameraEditorModel editor() {
        return editor;
    }

    public CameraInfo info() {
        return info;
    }
}
