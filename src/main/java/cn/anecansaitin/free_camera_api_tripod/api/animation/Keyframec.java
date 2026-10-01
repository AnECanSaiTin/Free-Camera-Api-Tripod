package cn.anecansaitin.free_camera_api_tripod.api.animation;

import org.jspecify.annotations.NullMarked;

/// 只读关键帧：时间、取值，以及两端各一条贝塞尔曲柄。
///
/// 曲柄由「长度 + 斜率」两个数描述：长度是相邻帧间隔 1/3 的倍数（1 表示基准长度），
/// 斜率是曲柄相对该键的方向。两者一起决定曲线这段的形状与进出该键的速度。
///
/// **这里没有也不会有 `NumberSource`**——只读视图不发放可变表示。有一个槽位挂了公式时，
/// [#value] 这一组给的是公式的**回退值**：本接口是给几何、绘制、界面显示用的；
/// 要这一帧真算出来的数，走 `eval.CurveSampler`。
///
/// 五个槽位按 [KeyField] 寻址（见 [#constant(KeyField)]），所以"再加一个可动态字段"
/// 在这里也不必再加一组 getter。
@NullMarked
public sealed interface Keyframec extends TrackKey permits Keyframe {
    float value();

    /// 入曲柄的斜率：作为前一段右端时的进出速度
    float inSlope();

    /// 出曲柄的斜率：作为后一段左端时的进出速度
    float outSlope();

    /// 入曲柄的长度：相邻帧间隔 1/3 的倍数
    float inLength();

    /// 出曲柄的长度：相邻帧间隔 1/3 的倍数
    float outLength();

    EvaluateMode evaluateMode();

    /// 五个槽位里有没有挂公式的（曲线图标蓝点、状态提示用它）。
    ///
    /// 只读契约上保留这一个方法：它是**展示层需要的信息**，而表达它所依赖的"是不是公式"
    /// 判断已经收进了 `NumberSource#isFormula()`，不必泄漏来源对象本身
    boolean hasFormula();

    /// 某个槽位的固定数值（挂了公式时是回退值）
    default float constant(KeyField field) {
        return switch (field) {
            case VALUE -> value();
            case IN_SLOPE -> inSlope();
            case OUT_SLOPE -> outSlope();
            case IN_LENGTH -> inLength();
            case OUT_LENGTH -> outLength();
        };
    }
}
