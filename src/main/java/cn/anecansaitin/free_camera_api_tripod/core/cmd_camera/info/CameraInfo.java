package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.info;

import cn.anecansaitin.free_camera_api_tripod.api.animation.track.AnimationTrack;
import cn.anecansaitin.free_camera_api_tripod.core.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.animation.track.TrackTypeRegistry;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CameraPose;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback.CameraPlayer;
import net.minecraft.network.chat.Component;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/// 信息展示：把动画与播放状态整理成可直接显示到界面上的只读文本。
///
/// 编辑器的状态栏、视口叠加信息与 Inspector 摘要都从这里取用。
@NullMarked
public final class CameraInfo {
    private final CameraAnimation animation;
    private final CameraPlayer player;

    public CameraInfo(CameraAnimation animation, CameraPlayer player) {
        this.animation = animation;
        this.player = player;
    }

    public Component animationName() {
        return Component.literal(animation.name());
    }

    public Component stateText() {
        return switch (player.state()) {
            case PLAYING -> Component.translatable("free_camera_api_tripod.editor.state.playing");
            case PAUSED -> Component.translatable("free_camera_api_tripod.editor.state.paused");
            case STOPPED -> Component.translatable("free_camera_api_tripod.editor.state.stopped");
        };
    }

    public Component timeText() {
        return Component.translatable(
                "free_camera_api_tripod.editor.info.time",
                number(player.time(), 2),
                number(animation.duration(), 2)
        );
    }

    public Component progressText() {
        float duration = animation.duration();
        float progress = duration <= 0 ? 0f : player.time() / duration * 100f;
        return Component.literal(number(progress, 1) + "%");
    }

    public Component trackSummary() {
        return Component.translatable(
                "free_camera_api_tripod.editor.info.tracks",
                animation.curveTracks().size(),
                animation.extensionTracks().size()
        );
    }

    public Component pathSummary() {
        return Component.translatable("free_camera_api_tripod.editor.info.path", animation.path().size());
    }

    public Component keyCountText(AnimationTrack track) {
        return Component.translatable("free_camera_api_tripod.editor.info.keys", track.keyCount());
    }

    public Component trackTypeText(AnimationTrack track) {
        return TrackTypeRegistry.label(track.type());
    }

    public Component poseText(CameraPose pose) {
        return Component.translatable(
                "free_camera_api_tripod.editor.info.pose",
                vector(pose.position(), 2),
                vector(pose.rotation(), 1),
                number(pose.fov(), 1)
        );
    }

    private static String vector(Vector3fc vec, int decimals) {
        return number(vec.x(), decimals) + ", " + number(vec.y(), decimals) + ", " + number(vec.z(), decimals);
    }

    private static String number(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
