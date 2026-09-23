package cn.anecansaitin.free_camera_api_tripod.api.animation.curve;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 曲线的只读视图。
///
/// 播放器、图表绘制这类只读取值的地方拿这个接口即可，不必接触可写曲线，
/// 与 {@link cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe} / {@link cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframec} 的关系一致。
@NullMarked
public interface Curvec {
    /// 该时刻的曲线取值；曲线为空时返回 0
    float evaluate(float time);

    int size();

    /// 第 index 个键的只读视图；越界返回 null
    @Nullable Keyframec key(int index);
}
