package cn.anecansaitin.free_camera_api_tripod.api.animation.curve;

import org.jspecify.annotations.NullMarked;

/// 关键帧五个数值的读取器。
///
/// [Curve] 的插值只读这一组数，**不关心它们是怎么来的**：直接读键上的固定数值
/// （[StaticKeys]），还是先按公式解析一遍（`eval.ResolvedKeys`）。
/// "怎么算出一个数"的知识因此不必传进曲线里，曲线退回成纯数值插值。
///
/// 两个不在读取器里的字段：
/// - **时间**与**插值模式**取自键本身——它们不是可动态的字段，公式按时间求值，时间自己再挂公式只会绕回自己
/// - 读取器只回答"第 index 个键的某个数是多少"，不回答键有多少个——那是 [Curvec#size] 的事
@NullMarked
public interface KeyValues {
    float value(int index);

    float inSlope(int index);

    float inLength(int index);

    float outSlope(int index);

    float outLength(int index);
}
