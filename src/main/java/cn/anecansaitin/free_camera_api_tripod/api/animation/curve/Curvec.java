package cn.anecansaitin.free_camera_api_tripod.api.animation.curve;

import cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframec;
import org.jspecify.annotations.NullMarked;

/// 曲线的只读视图。
///
/// 播放器、图表绘制这类只读取值的地方拿这个接口即可，不必接触可写曲线，
/// 与 {@link cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframe} / {@link cn.anecansaitin.free_camera_api_tripod.api.animation.Keyframec} 的关系一致。
@NullMarked
public interface Curvec {
    /// 该时刻的曲线取值；曲线为空时返回 0。
    ///
    /// 数值一律从 [values] 读：挂了公式的键按公式取值，还是只读固定数值，由读取器决定。
    /// 一次性求值用 `eval.CurveSampler`，它负责把解析与插值接起来
    float evaluate(float time, KeyValues values);

    int size();

    /// 第 index 个键的只读视图；越界抛出 {@link IndexOutOfBoundsException}
    Keyframec key(int index);
}
