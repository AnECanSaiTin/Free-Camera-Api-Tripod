package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 单路渲染结果与运行降级状态（主设计 §7.6）。
///
/// 降级是宿主运行状态：可见、可恢复、**不改写文档**；不得改写 `cameraSource`、`view.enabled`、
/// 活动轨道或矩形曲线。隔帧复用意味着该路画面可能不是同一瞬间，必须暴露**采样身份**
/// （渲染帧序号 + 动画采样时间 + 游戏刻），不能只给游戏刻：同一 tick 可能有多个渲染帧，
/// 暂停世界后定位也不改变游戏刻。
///
/// 呈现了画面（包括复用旧画面）时 [sampledFrame] 必须非空；停路未呈现时才允许为空。
/// 活动切换或来源交接后，旧活动区间的画面必须失效；重新活动必须准备有效新帧，不能先显示
/// 停用前缓存。`sampledFrame` 描述当前实际呈现的画面，不是准备帧的时间。
///
/// @param rigId        机位
/// @param rendered     本帧是否实际呈现（降级停路时为 false）
/// @param width        实际渲染宽度；未呈现可为 0
/// @param height       实际渲染高度；未呈现可为 0
/// @param degradeReason 降级原因；正常时为 null
/// @param sampledFrame 实际呈现画面的采样身份；未呈现时可为 null
@NullMarked
public record RigRenderStatus(
        RigId rigId,
        boolean rendered,
        int width,
        int height,
        @Nullable DegradeReason degradeReason,
        @Nullable SampleIdentity sampledFrame) {

    public RigRenderStatus {
        Objects.requireNonNull(rigId, "rigId");

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "RigRenderStatus dimensions must be non-negative: " + width + "x" + height);
        }

        if (rendered && (width == 0 || height == 0)) {
            throw new IllegalArgumentException("Rendered RigRenderStatus must have positive dimensions");
        }

        if (rendered && sampledFrame == null) {
            throw new IllegalArgumentException(
                    "Rendered RigRenderStatus must carry the displayed sample identity");
        }

        if (degradeReason == DegradeReason.FRAME_SKIPPED && !rendered) {
            throw new IllegalArgumentException("FRAME_SKIPPED means a reused frame is displayed");
        }

        if (degradeReason == DegradeReason.RIG_PAUSED && rendered) {
            throw new IllegalArgumentException("RIG_PAUSED means the rig is not submitted");
        }
    }

    /// 实际呈现画面的采样身份。
    ///
    /// @param renderFrameIndex 渲染帧序号，非负；区分同一游戏刻内的多个渲染帧
    /// @param animationTime    该画面的动画采样时间，秒；有限且非负
    /// @param gameTick         该画面的游戏刻，非负
    public record SampleIdentity(long renderFrameIndex, float animationTime, long gameTick) {

        public SampleIdentity {
            if (renderFrameIndex < 0) {
                throw new IllegalArgumentException(
                        "SampleIdentity renderFrameIndex must be non-negative: " + renderFrameIndex);
            }

            if (!Float.isFinite(animationTime) || animationTime < 0f) {
                throw new IllegalArgumentException(
                        "SampleIdentity animationTime must be finite and non-negative: " + animationTime);
            }

            if (gameTick < 0) {
                throw new IllegalArgumentException("SampleIdentity gameTick must be non-negative: " + gameTick);
            }
        }
    }
}
