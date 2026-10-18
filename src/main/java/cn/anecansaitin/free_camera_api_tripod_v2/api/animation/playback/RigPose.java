package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一路已准备好的机位姿态、矩形、层级与作者质量请求（主设计 §7.2、§7.6）。
///
/// 姿态已完成基础补全、参考系合成、注视覆盖与避障位置修正（若启用）；
/// 矩形为本帧已求值的合成矩形。**层级与质量请求也在准备阶段从同一文档快照冻结**：
/// 合成器不得在绘制时重新读取当前 store，否则可能把 S1 的姿态与 S2 的层级混用。
///
/// @param rigId            机位身份
/// @param pose             可交给相机的完整姿态
/// @param rect             本帧合成矩形，相对最终输出画布
/// @param zOrder           作者层级；大的在上，同层按文档顺序 / 稳定顺序
/// @param resolutionScale  作者请求的内部渲染分辨率比例，`(0, 1]`；实际降级另见
///                         [RigRenderStatus]
/// @param updateEveryFrames 作者请求的更新间隔，正整数；实际隔帧复用另见 [RigRenderStatus]
@NullMarked
public record RigPose(
        RigId rigId,
        WorldPose pose,
        RigView.Rect rect,
        int zOrder,
        double resolutionScale,
        int updateEveryFrames) {

    public RigPose {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(rect, "rect");

        if (!Double.isFinite(resolutionScale) || resolutionScale <= 0.0 || resolutionScale > 1.0) {
            throw new IllegalArgumentException(
                    "RigPose resolutionScale must be finite and in (0, 1]: " + resolutionScale);
        }

        if (updateEveryFrames < 1) {
            throw new IllegalArgumentException(
                    "RigPose updateEveryFrames must be >= 1: " + updateEveryFrames);
        }
    }
}
