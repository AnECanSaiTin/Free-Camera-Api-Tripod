package cn.anecansaitin.free_camera_api_tripod.api.animation;

import cn.anecansaitin.free_camera_api_tripod.api.animation.expression.ValueSource;

/// 只读关键帧：时间、取值，以及两端各一条贝塞尔曲柄。
///
/// 曲柄由「长度 + 斜率」两个数描述：长度是相邻帧间隔 1/3 的倍数（1 表示基准长度），
/// 斜率是曲柄相对该键的方向。两者一起决定曲线这段的形状与进出该键的速度
///
/// 取值、斜率、长度这五个数值各有两套读法：
/// - `value()` 这一组读的是**固定数值**（公式的回退值）：几何计算与界面编辑用它
/// - `valueSource()` 这一组给的是**数值来源本身**：求值与序列化用它，见 [Keyframe] 的说明。
///   求值按 [ValueSource] 走，因此只读视图同样能参与动态求值（见 `eval.ResolvedKeys`）
public interface Keyframec extends TrackKey {
    // get
    float time();

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

    /// 取值的数值来源：固定值或一条公式
    ValueSource valueSource();

    /// 入曲柄斜率的数值来源
    ValueSource inSlopeSource();

    /// 入曲柄长度的数值来源
    ValueSource inLengthSource();

    /// 出曲柄斜率的数值来源
    ValueSource outSlopeSource();

    /// 出曲柄长度的数值来源
    ValueSource outLengthSource();
}
