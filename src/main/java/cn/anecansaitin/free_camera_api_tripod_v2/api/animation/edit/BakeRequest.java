package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval.EvaluationContext;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.field.Curve;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/// 烘焙请求：机位 / 轨道、区间、步长、插值策略、最大键数与完整冻结环境（主设计 §4.6）。
///
/// 预览区间不隐式决定烘焙范围；缺输入、UNKNOWN、ERROR 或超预算时不发布。世界 / 非确定依赖
/// 必须提示“固化这份事实 / 这次结果”，不能伪称可复现世界历史。
///
/// @param rigId         目标机位
/// @param trackId       目标轨道
/// @param startTime     起始时间，秒；有限且非负
/// @param endTime       结束时间，秒；有限且大于起始
/// @param stepSeconds   采样步长，秒；有限且为正
/// @param interpolation 生成键的插值策略
/// @param maxKeys       允许生成的最大键数，正整数
/// @param context       冻结的求值上下文（快照 + 事实 + 索引）
@NullMarked
public record BakeRequest(
        RigId rigId,
        TrackId trackId,
        float startTime,
        float endTime,
        float stepSeconds,
        Curve.Interpolation interpolation,
        int maxKeys,
        EvaluationContext context) {

    public BakeRequest {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(interpolation, "interpolation");
        Objects.requireNonNull(context, "context");

        if (!Float.isFinite(startTime) || startTime < 0f
                || !Float.isFinite(endTime) || endTime <= startTime) {
            throw new IllegalArgumentException(
                    "BakeRequest requires finite 0 <= startTime < endTime");
        }

        if (!Float.isFinite(stepSeconds) || stepSeconds <= 0f) {
            throw new IllegalArgumentException(
                    "BakeRequest stepSeconds must be finite and positive: " + stepSeconds);
        }

        if (maxKeys < 1) {
            throw new IllegalArgumentException("BakeRequest maxKeys must be >= 1: " + maxKeys);
        }
    }
}
