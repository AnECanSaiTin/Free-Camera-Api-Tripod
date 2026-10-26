package cn.anecansaitin.free_camera_api_tripod_v2.core.animation.playback;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraSource;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.playback.RigPose;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/// 相机应用端口：把一帧的来源与机位姿态交给宿主相机边界。
///
/// 实现必须在宿主主线程同步完成，不得读取当前 store、不得重新求值文档；异常表示该帧的
/// 相机应用失败，由 [CoreFrameExecutor] 转为 `RENDER_FAILURE`。相机副作用不承诺回滚。
@NullMarked
@FunctionalInterface
public interface CameraFrameSink {

    /// 应用一帧相机状态。
    ///
    /// @param source 播放/编辑来源
    /// @param rigs   已准备机位姿态；顺序与文档机位顺序一致
    void apply(CameraSource source, List<RigPose> rigs);
}
