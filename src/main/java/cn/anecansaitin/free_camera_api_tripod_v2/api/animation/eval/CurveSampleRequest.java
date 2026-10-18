package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.eval;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.RigId;
import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.id.TrackId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 显式曲线采样请求（主设计 §3.4、§6.1）。
///
/// 采样不再依赖“当前活动机位”或采样器实例的隐式绑定：请求同时携带
/// [EvaluationContext]（快照 + 事实 + 轨道查询 + 索引）、轨道身份、机位上下文与采样时间。
/// 上下文由统一工厂绑定同一快照与索引，采样器只能读取该上下文。
///
/// `rigId` 为本次普通曲线 / 符号读取的空间上下文；`TrackRef` 使用被引用轨道所属机位。
/// 只有不读取任何机位空间事实的求值才允许为空。
///
/// @param context    已绑定的求值上下文
/// @param trackId    要采样的轨道 / 曲线
/// @param rigId      普通曲线 / 符号读取的机位上下文；无空间依赖时为空
/// @param sampleTime 采样时间，秒；有限、非负，`-0.0` 规范化为 `0.0`
@NullMarked
public record CurveSampleRequest(
        EvaluationContext context,
        TrackId trackId,
        @Nullable RigId rigId,
        float sampleTime) {

    public CurveSampleRequest {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(trackId, "trackId");

        if (!Float.isFinite(sampleTime) || sampleTime < 0f) {
            throw new IllegalArgumentException(
                    "CurveSampleRequest sampleTime must be finite and non-negative: " + sampleTime);
        }

        // -0.0 规范化；其余有效时间不变（主设计 §4.4）
        if (sampleTime == 0f) {
            sampleTime = 0f;
        }
    }
}
