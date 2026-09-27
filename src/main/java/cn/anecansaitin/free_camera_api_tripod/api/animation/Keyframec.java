package cn.anecansaitin.free_camera_api_tripod.api.animation;

/// 只读关键帧：时间、取值，以及两端各一条贝塞尔曲柄。
///
/// 曲柄由「长度 + 斜率」两个数描述：长度是相邻帧间隔 1/3 的倍数（1 表示基准长度），
/// 斜率是曲柄相对该键的方向。两者一起决定曲线这段的形状与进出该键的速度
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
}
