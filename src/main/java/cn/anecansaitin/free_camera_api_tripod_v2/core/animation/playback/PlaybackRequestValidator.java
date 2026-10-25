package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraRig;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.document.Revision;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Diagnostic;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticCode;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.DiagnosticScope;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.LocalizedMessage;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit.Severity;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.AimOverride;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.PlaybackRequest;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPlaybackOverrides;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.Space;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// 播放请求的静态检查：不读世界、不发布，只检查请求与目标文档的组合。
///
/// 与 [CorePlaybackService] 的时钟检查互补：本类处理开始时间 / 覆盖机位 / 局部锚点 /
/// 固定注视目标等请求形状；文档内容与统一校验仍由文档存储负责。
@NullMarked
public final class PlaybackRequestValidator {

    private PlaybackRequestValidator() {
    }

    /// 检查一次播放请求。
    ///
    /// @param request  请求
    /// @param revision 请求基于的发布序号；用于诊断作用域
    /// @return 错误诊断；空表示请求形状通过
    public static List<Diagnostic> validate(PlaybackRequest request, Revision revision) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(revision, "revision");

        List<Diagnostic> diagnostics = new ArrayList<>();
        CameraAnimation document = request.animation();
        float duration = document.duration();

        if (duration == 0f && request.startTime() != 0f) {
            diagnostics.add(error(revision, null, "Empty animation only accepts startTime 0"));
        } else if (request.startTime() > duration) {
            diagnostics.add(error(revision, null,
                    "startTime " + request.startTime() + " is outside [0, " + duration + "]"));
        }

        for (var entry : request.rigOverrides().entrySet()) {
            RigId rigId = entry.getKey();
            RigPlaybackOverrides overrides = entry.getValue();
            CameraRig rig = document.rig(rigId).orElse(null);

            if (rig == null) {
                diagnostics.add(error(revision, rigId,
                        "Playback override references a missing rig: " + rigId));
                continue;
            }

            if (overrides.aim() instanceof AimOverride.Replace replace
                    && replace.target().mask().z()) {
                diagnostics.add(error(revision, rigId,
                        "Playback aim override must not enable Roll (z mask)"));
            }

            if (rig.space() instanceof Space.Local local
                    && overrides.anchor() == null
                    && local.defaultAnchor() == null) {
                diagnostics.add(error(revision, rigId,
                        "Local rig " + rigId + " requires an anchor from the document or playback request"));
            }
        }

        return List.copyOf(diagnostics);
    }

    private static Diagnostic error(Revision revision, RigId rigId, String message) {
        return new Diagnostic(DiagnosticCode.INVALID_EDIT, Severity.ERROR,
                LocalizedMessage.literal(message),
                new DiagnosticScope.Document(revision),
                new Diagnostic.Location(rigId, "playback"), null);
    }
}
