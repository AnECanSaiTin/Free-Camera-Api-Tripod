package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 单路渲染结果与运行降级状态（主设计 §7.6）。
///
/// 降级是宿主运行状态：可见、可恢复、**不改写文档**；不得改写 `cameraSource`、`view.enabled`、
/// 活动轨道或矩形曲线。隔帧复用意味着该路画面可能不是同一瞬间，必须暴露采样时刻。
///
/// @param rigId             机位
/// @param rendered          本帧是否实际呈现（降级停路时为 false）
/// @param width             实际渲染宽度；未呈现可为 0
/// @param height            实际渲染高度；未呈现可为 0
/// @param degradeReason     降级原因；正常时为 null
/// @param sampledAtGameTick 画面实际采样的游戏刻；未呈现或未知时为 null
@NullMarked
public record RigRenderStatus(
        RigId rigId,
        boolean rendered,
        int width,
        int height,
        @Nullable DegradeReason degradeReason,
        @Nullable Long sampledAtGameTick) {

    public RigRenderStatus {
        Objects.requireNonNull(rigId, "rigId");

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "RigRenderStatus dimensions must be non-negative: " + width + "x" + height);
        }

        if (rendered && (width == 0 || height == 0)) {
            throw new IllegalArgumentException("Rendered RigRenderStatus must have positive dimensions");
        }

        if (sampledAtGameTick != null && sampledAtGameTick < 0) {
            throw new IllegalArgumentException(
                    "RigRenderStatus sampledAtGameTick must be non-negative: " + sampledAtGameTick);
        }
    }
}
