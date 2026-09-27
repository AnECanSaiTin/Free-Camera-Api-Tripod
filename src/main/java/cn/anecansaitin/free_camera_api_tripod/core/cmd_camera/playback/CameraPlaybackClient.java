package cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.playback;

import cn.anecansaitin.free_camera_api_tripod.api.animation.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationCodec;
import cn.anecansaitin.free_camera_api_tripod.core.animation.io.AnimationFiles;
import cn.anecansaitin.free_camera_api_tripod.core.cmd_camera.CmdCamera;
import cn.anecansaitin.free_camera_api_tripod.core.editor.EditorLang;
import cn.anecansaitin.free_camera_api_tripod.registry.CameraPlaybackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/// 收到服务端的播放指令后，在本地把动画装上并开始播。
///
/// 两种来源在这里合流：本地文件版自己去读玩家游戏目录下的动画文件，存档版直接用包里带来的 JSON。
/// 动画 JSON 自带路径数据，所以需要路径的动画与纯动画走的是同一条路。
@NullMarked
public final class CameraPlaybackClient {
    /// 处理一条播放 / 停止指令
    public static void handle(CameraPlaybackPayload payload) {
        CmdCamera camera = CmdCamera.INSTANCE;

        if (camera == null) {
            return;
        }

        if (payload.kind() == CameraPlaybackPayload.Kind.STOP) {
            camera.player().stop();
            notifyPlayer(EditorLang.t("notify.playback_stopped"));
            return;
        }

        String json = payload.kind() == CameraPlaybackPayload.Kind.LOCAL
                ? AnimationFiles.loadAnimation(payload.name())
                : payload.json();

        if (json == null) {
            notifyPlayer(EditorLang.t("notify.playback_missing", payload.name()));
            return;
        }

        CameraAnimation loaded = AnimationCodec.animationFromJson(json);

        if (loaded == null) {
            notifyPlayer(EditorLang.t("notify.playback_invalid", payload.name()));
            return;
        }

        // 动画实例被播放器与编辑器共同持有，只能原地替换；编辑器开着时相当于直接换了一段动画
        camera.animation().copyFrom(loaded);
        camera.player().play();
        notifyPlayer(EditorLang.t("notify.playback_started", loaded.name()));
    }

    /// 提示打在动作栏上：这条指令多半是别人发起的，编辑器不一定开着，
    /// 编辑器内那一套提示会看不到，这里需要一条玩家一定能看见的反馈
    private static void notifyPlayer(Component message) {
        Minecraft.getInstance().gui.setOverlayMessage(message, false);
    }

    private CameraPlaybackClient() {
    }
}
