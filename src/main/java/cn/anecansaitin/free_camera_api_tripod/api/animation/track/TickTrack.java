package cn.anecansaitin.free_camera_api_tripod.api.animation.track;

import org.jspecify.annotations.NullMarked;

/// 随时间推进触发的轨道：播放头走过某段时间时，落在其中的键应当被触发。
///
/// 这是按能力拆出来的可选接口，不属于 {@link AnimationTrack} 的必备成员：
/// 曲线轨道按时间"取值"（走它自己暴露的 {@link CurveTrack#curve()}），
/// 而指令、事件、后处理特效这类键的语义是"经过即触发"。播放器每帧把推进的时间区间交给实现，
/// 由实现自己决定触发哪些键、按什么顺序触发。
///
/// 实现必须保证同一个键只触发一次：区间取左开右闭 {@code (fromTime, toTime]}，
/// 起点不计入，因此相邻两帧的边界键不会重复触发。
@NullMarked
public interface TickTrack {
    /// 播放头从 fromTime 推进到 toTime；调用方保证 {@code fromTime < toTime}
    void advance(float fromTime, float toTime);
}
