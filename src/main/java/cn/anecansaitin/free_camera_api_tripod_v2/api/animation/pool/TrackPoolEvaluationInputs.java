package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.pool;

import org.jspecify.annotations.NullMarked;

/// 轨道池一帧的显式求值输入：文档时间、进度与冻结世界时间。
///
/// 不从这里隐式读取当前机位、当前属性或最终相机输出；所有公式/轨道只消费这些显式值与
/// 属性绑定中的来源。
///
/// @param time      文档时间，秒；有限且非负
/// @param progress  动画进度，`[0, 1]`
/// @param worldTime 冻结世界时间，秒；有限
@NullMarked
public record TrackPoolEvaluationInputs(float time, double progress, double worldTime) {

    public TrackPoolEvaluationInputs {
        if (!Float.isFinite(time) || time < 0f) {
            throw new IllegalArgumentException("Evaluation time must be finite and non-negative");
        }

        if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Evaluation progress must be in [0, 1]: " + progress);
        }

        if (!Double.isFinite(worldTime)) {
            throw new IllegalArgumentException("Evaluation worldTime must be finite");
        }
    }

    /// 只给文档时间的便捷输入：进度 0、世界时间 0。
    public static TrackPoolEvaluationInputs at(float time) {
        return new TrackPoolEvaluationInputs(time, 0.0, 0.0);
    }
}
