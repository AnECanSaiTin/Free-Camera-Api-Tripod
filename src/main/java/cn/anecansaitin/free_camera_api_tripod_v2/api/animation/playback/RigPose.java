package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.RigView;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 一路已准备好的机位姿态与矩形（主设计 §7.2、§7.6）。
///
/// 姿态已完成基础补全、参考系合成、注视覆盖与避障位置修正（若启用）；
/// 矩形为本帧已求值的合成矩形。任一路失败时整帧不进入执行阶段，不会出现部分新姿态。
///
/// @param rigId 机位身份
/// @param pose  可交给相机的完整姿态
/// @param rect  本帧合成矩形，相对最终输出画布
@NullMarked
public record RigPose(RigId rigId, WorldPose pose, RigView.Rect rect) {

    public RigPose {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(rect, "rect");
    }
}
