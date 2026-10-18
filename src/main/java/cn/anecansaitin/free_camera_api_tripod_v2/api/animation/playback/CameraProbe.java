package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.spatial.WorldPose;
import org.jspecify.annotations.NullMarked;

/// 相机探针：读取宿主**基础**姿态（主设计 §7.5、§6.4）。
///
/// 基础姿态必须来自宿主相机 / 玩家状态，不能把上一帧本插件的输出再次作为基础输入，
/// 否则会形成自反馈并破坏“同帧只捕获一次”的规则。探针不修改相机、不触发事件。
@NullMarked
@FunctionalInterface
public interface CameraProbe {

    /// 当前宿主基础世界姿态。
    WorldPose basePose();
}
