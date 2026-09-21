package cn.anecansaitin.free_camera_api_tripod.api.animation;

import org.jspecify.annotations.NullMarked;

/// 时间轴上的一个键。
///
/// 曲线关键帧（{@link Keyframe}）与未来的事件触发器键、特效键都实现该接口，
/// 从而让时间轴与图表编辑器可以统一处理各类轨道。
@NullMarked
public interface TrackKey {
    float time();
}
