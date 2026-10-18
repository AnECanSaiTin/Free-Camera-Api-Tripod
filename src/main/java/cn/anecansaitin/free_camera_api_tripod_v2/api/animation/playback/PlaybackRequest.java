package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Objects;

/// 播放请求（主设计 §7.1）。
///
/// 请求通过文档校验后由同一文档存储切换文档；编辑器打开时按整份换档处理。
/// 请求不含分路导出字段；每路覆盖只作用于显式列出的 `RigId`，未知 ID 必须拒绝，
/// 不能把一份覆盖隐式施加到所有机位。
///
/// 速度必须有限且大于零，第一版不支持反向播放。开始时间必须有限、非负且位于可播放范围；
/// 空动画仅接受零时刻。具体范围校验由播放服务与文档校验完成，本 record 只拒绝明显非法值。
///
/// @param animation    要播放的文档
/// @param rigOverrides 按机位的覆盖；空 map 表示全部继承文档默认
/// @param speed        播放速度，有限且 `> 0`
/// @param loop         是否循环
/// @param startTime    开始时间，秒；有限且非负
@NullMarked
public record PlaybackRequest(
        CameraAnimation animation,
        Map<RigId, RigPlaybackOverrides> rigOverrides,
        float speed,
        boolean loop,
        float startTime) {

    public PlaybackRequest {
        Objects.requireNonNull(animation, "animation");

        if (!Float.isFinite(speed) || speed <= 0f) {
            throw new IllegalArgumentException("PlaybackRequest speed must be finite and positive: " + speed);
        }

        if (!Float.isFinite(startTime) || startTime < 0f) {
            throw new IllegalArgumentException(
                    "PlaybackRequest startTime must be finite and non-negative: " + startTime);
        }

        // 防御性复制：不保留调用方容器（主设计 §3.2）
        rigOverrides = Map.copyOf(rigOverrides);
    }
}
