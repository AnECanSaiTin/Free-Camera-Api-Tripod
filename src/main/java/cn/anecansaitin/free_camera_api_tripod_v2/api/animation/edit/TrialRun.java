package cn.anecansaitin.free_camera_api_tripod_v2.api.animation.edit;

import cn.anecansaitin.free_camera_api_tripod_v2.api.animation.block.CameraAnimation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 试算请求：候选 + 预算（主设计 §4.3）。
///
/// 试算采样关键时刻、区间中点与固定步长；策略可配置但必须有上限。试算不得执行 Tick 轨道或
/// 其他副作用，不触发世界加载；依赖固定世界事实的公式必须在结果中说明所用事实与时间。
///
/// @param candidate        待检查候选
/// @param maxSamples       最大采样点数，正整数
/// @param maxEvaluations   最大求值工作量，正整数
/// @param maxDiagnostics   最大输出诊断数，正整数
/// @param fixedFactsNote   固定世界事实的时间 / 环境说明；无世界依赖时为空
@NullMarked
public record TrialRun(
        CameraAnimation candidate,
        int maxSamples,
        int maxEvaluations,
        int maxDiagnostics,
        @Nullable String fixedFactsNote) {

    public TrialRun {
        Objects.requireNonNull(candidate, "candidate");

        if (maxSamples < 1) {
            throw new IllegalArgumentException("TrialRun maxSamples must be >= 1: " + maxSamples);
        }

        if (maxEvaluations < 1) {
            throw new IllegalArgumentException("TrialRun maxEvaluations must be >= 1: " + maxEvaluations);
        }

        if (maxDiagnostics < 1) {
            throw new IllegalArgumentException("TrialRun maxDiagnostics must be >= 1: " + maxDiagnostics);
        }
    }
}
